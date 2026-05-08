package com.sadday.app.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sadday.app.shared.util.ClientIpExtractor;

import java.io.IOException;
import java.time.Duration;

/**
 * Filtro de rate limiting por IP para endpoints sensibles.
 *
 * <p>Aplica límites distintos según el tipo de endpoint:
 * <ul>
 *   <li>{@code POST /auth/login}            — 10 intentos / 1 minuto por IP</li>
 *   <li>{@code POST /auth/forgot-password}  — 5 intentos / 5 minutos por IP</li>
 *   <li>{@code POST /auth/reset-password}   — 5 intentos / 5 minutos por IP</li>
 *   <li>{@code POST /auth/refresh}          — 60 intentos / 1 minuto por IP</li>
 *   <li>{@code POST /registro/complete}     — 10 intentos / 10 minutos por IP</li>
 *   <li>{@code GET  /registro/token-info}   — 10 intentos / 10 minutos por IP</li>
 * </ul>
 *
 * <p>El almacenamiento usa Caffeine con expiración automática por inactividad
 * ({@code expireAfterAccess}) y tamaño máximo de 10.000 entradas por tipo de
 * endpoint, lo que evita memory leaks por acumulación ilimitada de IPs (SEC-02).
 *
 * <p>En producción multi-instancia, reemplazar con Redis + Bucket4j distribuido.
 *
 * <p>Desactivado en el perfil {@code test} para no interferir con los tests de integración.
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ClientIpExtractor clientIpExtractor;

    /**
     * Máximo de entradas por caché. Previene DoS por agotamiento de memoria
     * si un atacante rota miles de IPs distintas (ej: via Tor).
     */
    private static final long MAX_CACHE_SIZE = 10_000;

    /** Tiempo de inactividad tras el cual se expulsa una entrada de la caché. */
    private static final Duration EVICTION_AFTER_ACCESS = Duration.ofMinutes(30);

    // Buckets por tipo de límite, indexados por IP — con expiración automática
    private final Cache<String, Bucket> loginBuckets    = buildCache();
    private final Cache<String, Bucket> forgotBuckets   = buildCache();
    private final Cache<String, Bucket> resetBuckets    = buildCache();
    private final Cache<String, Bucket> refreshBuckets  = buildCache();
    private final Cache<String, Bucket> registroBuckets = buildCache();
    private final Cache<String, Bucket> tokenInfoBuckets = buildCache();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        String method = request.getMethod();

        String ip = extractIp(request);
        Bucket bucket = null;

        if ("POST".equals(method)) {
            if (path.endsWith("/auth/login")) {
                bucket = loginBuckets.get(ip, k -> buildBucket(10, Duration.ofMinutes(1)));
            } else if (path.endsWith("/auth/forgot-password")) {
                bucket = forgotBuckets.get(ip, k -> buildBucket(5, Duration.ofMinutes(5)));
            } else if (path.endsWith("/auth/reset-password")) {
                bucket = resetBuckets.get(ip, k -> buildBucket(5, Duration.ofMinutes(5)));
            } else if (path.endsWith("/auth/refresh")) {
                bucket = refreshBuckets.get(ip, k -> buildBucket(60, Duration.ofMinutes(1)));
            } else if (path.endsWith("/registro/complete")) {
                bucket = registroBuckets.get(ip, k -> buildBucket(10, Duration.ofMinutes(10)));
            }
        } else if ("GET".equals(method) && path.contains("/registro/token-info")) {
            bucket = tokenInfoBuckets.get(ip, k -> buildBucket(10, Duration.ofMinutes(10)));
        }

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":"error","message":"Demasiados intentos. Espera un momento antes de reintentar."}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static Cache<String, Bucket> buildCache() {
        return Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterAccess(EVICTION_AFTER_ACCESS)
                .build();
    }

    private Bucket buildBucket(int tokens, Duration refillPeriod) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(tokens)
                        .refillGreedy(tokens, refillPeriod)
                        .build())
                .build();
    }

    private String extractIp(HttpServletRequest request) {
        return clientIpExtractor.extractIp(request);
    }
}
