package com.sadday.app.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filtro JWT: extrae y valida el Bearer token en cada request.
 *
 * <p>Si el token es válido, construye el {@code Authentication} con el rol del usuario
 * y adjunta un {@link SaddayAuthDetails} con el {@code socioId} y el rol
 * para su uso en controladores y servicios sin consultas adicionales a la BD.
 *
 * <p>Si no hay token o es inválido, simplemente no setea autenticación
 * y deja que Spring Security devuelva 401.
 *
 * <p>Nota de seguridad: nunca se loguea el contenido del token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(BEARER_PREFIX.length());
        boolean mdcSet = false;

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                if (jwtService.isTokenValid(token)) {
                    String username = jwtService.extractUsername(token);
                    String rol      = jwtService.extractRol(token);
                    UUID   socioId  = jwtService.extractSocioId(token);

                    // hasRole/hasAnyRole comparan contra ROLE_<UPPERCASE>, así que normalizamos
                    var authToken = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()))
                    );
                    authToken.setDetails(new SaddayAuthDetails(socioId, rol));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    MDC.put("socioId",  socioId.toString());
                    MDC.put("username", username);
                    mdcSet = true;
                }
            }
        } catch (Exception e) {
            log.debug("Token JWT inválido o expirado: {}", e.getMessage(), e);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (mdcSet) {
                MDC.remove("socioId");
                MDC.remove("username");
            }
        }
    }
}
