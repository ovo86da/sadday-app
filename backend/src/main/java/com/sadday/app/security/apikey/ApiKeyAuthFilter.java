package com.sadday.app.security.apikey;

import com.sadday.app.auth.entity.ApiKey;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.auth.service.ApiKeyService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.socios.repository.SocioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticación por API Key para el MCP server.
 *
 * <p>Intercepta requests con header {@code X-Api-Key} y construye
 * el contexto de seguridad con {@code SCOPE_readonly}.
 * Rechaza POST/PUT/PATCH/DELETE directamente con 403 — las API Keys son solo lectura.
 *
 * <p>En perfil prod, rechaza requests sin TLS (X-Forwarded-Proto != https).
 * Si no hay header {@code X-Api-Key}, pasa al siguiente filtro sin hacer nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    private final ApiKeyService         apiKeyService;
    private final SocioRepository       socioRepository;
    private final UsuarioAuthRepository usuarioAuthRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         filterChain
    ) throws ServletException, IOException {

        final String rawKey = request.getHeader(API_KEY_HEADER);

        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // En prod rechazar si no viene por HTTPS
        if ("prod".equalsIgnoreCase(activeProfile)) {
            String proto = request.getHeader("X-Forwarded-Proto");
            if (!"https".equalsIgnoreCase(proto)) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        "API Key solo permitida sobre HTTPS");
                return;
            }
        }

        // API Keys son solo lectura
        String method = request.getMethod();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "Las API Keys no permiten operaciones de escritura");
            return;
        }

        try {
            var optKey = apiKeyService.findActiveByRawKey(rawKey);

            if (optKey.isEmpty()) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "API Key inválida o revocada");
                return;
            }

            ApiKey apiKey = optKey.get();

            var optSocio = socioRepository.findById(apiKey.getSocioId());
            if (optSocio.isEmpty()) {
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "API Key inválida");
                return;
            }

            var socio = optSocio.get();
            String username = usuarioAuthRepository.findBySocioId(socio.getId())
                    .map(u -> u.getUsername())
                    .orElse(socio.getCorreo());

            var auth = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    List.of(
                            new SimpleGrantedAuthority("ROLE_" + socio.getRolSistema().getNombre().toUpperCase()),
                            new SimpleGrantedAuthority("SCOPE_readonly")
                    )
            );
            auth.setDetails(new SaddayAuthDetails(socio.getId(), socio.getRolSistema().getNombre()));
            SecurityContextHolder.getContext().setAuthentication(auth);

            MDC.put("socioId",  socio.getId().toString());
            MDC.put("username", username);

            // Actualizar lastUsedAt de forma síncrona (UPDATE indexado — latencia mínima)
            apiKeyService.touchLastUsedAt(apiKey.getId());

            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.remove("socioId");
                MDC.remove("username");
            }

        } catch (Exception e) {
            log.error("Error en ApiKeyAuthFilter: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error interno del servidor");
        }
    }

    private void writeJson(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
