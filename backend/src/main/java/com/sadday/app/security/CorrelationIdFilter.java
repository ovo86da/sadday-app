package com.sadday.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filtro que asigna un ID único a cada petición HTTP y lo propaga via MDC.
 *
 * <p>Comportamiento:
 * <ul>
 *   <li>Si la petición trae el header {@code X-Request-ID}, lo reutiliza
 *       siempre que cumpla el formato permitido (alfanumérico + separadores,
 *       máximo 64 caracteres). En caso contrario, genera un UUID v4.</li>
 *   <li>El ID se inyecta en el MDC bajo la clave {@value #MDC_KEY}, por lo que
 *       aparece automáticamente en cada línea de log de esa petición.</li>
 *   <li>El ID se devuelve en el header {@code X-Request-ID} de la respuesta,
 *       permitiendo al cliente o al equipo de soporte correlacionar errores.</li>
 *   <li>El MDC se limpia al finalizar la petición (evita fugas en thread pools).</li>
 * </ul>
 *
 * <p>Seguridad (SEC-03): el header externo se valida para prevenir:
 * <ul>
 *   <li>Log injection (caracteres especiales o saltos de línea en logs)</li>
 *   <li>Inflación de almacenamiento (valores extremadamente largos)</li>
 * </ul>
 *
 * <p>Uso en investigación de errores:
 * <pre>
 *   # Buscar todos los logs de una petición específica:
 *   grep '"requestId":"abc-123"' /app/logs/sadday-app.log
 * </pre>
 */
@Component
@Order(Integer.MIN_VALUE)   // Primer filtro de la cadena — antes de Spring Security
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY           = "requestId";

    private static final int MAX_REQUEST_ID_LENGTH = 64;

    /** Solo letras, dígitos, guiones, guiones bajos y puntos. */
    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!isValidRequestId(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static boolean isValidRequestId(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_REQUEST_ID_LENGTH
                && SAFE_ID.matcher(value).matches();
    }
}

