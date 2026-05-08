package com.sadday.app.shared.util;

import com.sadday.app.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Extrae la IP real del cliente de una petición HTTP.
 *
 * <p>Prioridad de extracción (solo cuando {@code remoteAddr} es un proxy confiable):
 * <ol>
 *   <li>{@code CF-Connecting-IP} — IP real inyectada por Cloudflare.</li>
 *   <li>{@code X-Forwarded-For} — primer valor de la cadena.</li>
 *   <li>{@code getRemoteAddr()} — IP directa de la conexión TCP (fallback siempre).</li>
 * </ol>
 *
 * <p>Los headers {@code CF-Connecting-IP} y {@code X-Forwarded-For} son controlables
 * por el cliente y solo se leen si {@code getRemoteAddr()} pertenece a un CIDR de la
 * allowlist {@code sadday.security.trusted-proxy-cidrs}. Si la conexión llega directamente
 * al backend (sin pasar por el proxy confiable), se ignoran esos headers y se devuelve
 * {@code getRemoteAddr()} para evitar IP spoofing en logs, auditoría y rate limiting.
 */
@Component
public class ClientIpExtractor {

    private static final Pattern IP_FORMAT = Pattern.compile("^[0-9a-fA-F.:]+$");
    private static final int MAX_IP_LENGTH = 45;

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public ClientIpExtractor(SecurityProperties securityProperties) {
        this.trustedProxyMatchers = securityProperties.getTrustedProxyCidrs().stream()
                .map(IpAddressMatcher::new)
                .toList();
    }

    /**
     * Extrae la IP real del cliente de una petición HTTP.
     *
     * @param request la petición HTTP actual
     * @return la IP del cliente
     */
    public String extractIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (!isTrustedProxy(remoteAddr)) {
            // Conexión directa sin proxy confiable: ignorar headers spoofables
            return remoteAddr;
        }

        String cfIp = request.getHeader("CF-Connecting-IP");
        if (isValidIp(cfIp)) {
            return cfIp;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String firstIp = xForwardedFor.split(",")[0].strip();
            if (isValidIp(firstIp)) {
                return firstIp;
            }
        }

        return remoteAddr;
    }

    /**
     * Extrae la IP real del cliente desde el {@link RequestContextHolder}.
     * Útil en contextos donde no se tiene acceso directo al {@code HttpServletRequest}
     * (ej: aspectos, servicios).
     *
     * @return la IP del cliente, o {@code "UNKNOWN"} si no hay request activo
     */
    public String extractIpFromContext() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return extractIp(attrs.getRequest());
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * Extrae el User-Agent del request actual desde el {@link RequestContextHolder}.
     *
     * @return el User-Agent, o {@code null} si no hay request activo
     */
    public String extractUserAgentFromContext() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest().getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) return false;
        return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddr));
    }

    private static boolean isValidIp(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_IP_LENGTH
                && IP_FORMAT.matcher(value).matches();
    }
}
