package com.sadday.app.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sadday.app.shared.util.ClientIpExtractor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aspecto de auditoría para registrar acciones críticas en la tabla {@code auditoria}.
 *
 * <p>Uso: anotar el método de servicio con {@link Auditable}:
 * <pre>
 * {@code @Auditable(accion = "DELETE_SOCIO", entidad = "socios")}
 * public void eliminarSocio(UUID id) { ... }
 * </pre>
 *
 * <p>El aspecto captura:
 * <ul>
 *   <li>Quién lo hizo (socio_id desde el SecurityContext)</li>
 *   <li>Qué acción</li>
 *   <li>Si fue exitoso o falló</li>
 *   <li>IP y user-agent del request actual</li>
 * </ul>
 *
 * <p>Nota: los snapshots de datos (antes/después) se inyectan manualmente
 * desde el servicio usando {@link AuditService} directamente cuando se necesita
 * mayor granularidad.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService       auditService;
    private final ClientIpExtractor  clientIpExtractor;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        String ipAddress  = extractIp();
        String userAgent  = extractUserAgent();
        String username   = extractUsername();

        try {
            Object result = pjp.proceed();
            Object entidadId = extractEntidadId(pjp, auditable, result);
            String detalle = auditable.detalle().isBlank() ? null : auditable.detalle();
            auditService.registrar(
                    username,
                    auditable.accion(),
                    auditable.entidad(),
                    entidadId,
                    null,
                    null,
                    ipAddress,
                    userAgent,
                    "SUCCESS",
                    detalle
            );
            return result;

        } catch (Exception e) {
            Object entidadId = extractEntidadId(pjp, auditable, null);
            auditService.registrar(
                    username,
                    auditable.accion(),
                    auditable.entidad(),
                    entidadId,
                    null,
                    null,
                    ipAddress,
                    userAgent,
                    "FAILED",
                    e.getMessage()
            );
            throw e;
        }
    }

    /**
     * Extrae el ID de la entidad afectada según la configuración de {@link Auditable}.
     * <ul>
     *   <li>Si {@code idArgName} está definido, busca ese parámetro en la firma del método.</li>
     *   <li>Si {@code idFromReturn} es true, intenta llamar {@code id()} o {@code getId()} en el resultado.</li>
     * </ul>
     */
    private Object extractEntidadId(ProceedingJoinPoint pjp, Auditable auditable, Object result) {
        if (!auditable.idArgName().isBlank()) {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            String[] paramNames = sig.getParameterNames();
            Object[] args = pjp.getArgs();
            for (int i = 0; i < paramNames.length; i++) {
                if (paramNames[i].equals(auditable.idArgName())) {
                    return args[i];
                }
            }
        }
        if (auditable.idFromReturn() && result != null) {
            try {
                try {
                    return result.getClass().getMethod("id").invoke(result);
                } catch (NoSuchMethodException e) {
                    return result.getClass().getMethod("getId").invoke(result);
                }
            } catch (Exception ignored) {
                log.trace("No se pudo extraer el id de la entidad: {}", ignored.getMessage(), ignored);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------

    private String extractUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "SYSTEM";
    }

    private String extractIp() {
        return clientIpExtractor.extractIpFromContext();
    }

    private String extractUserAgent() {
        return clientIpExtractor.extractUserAgentFromContext();
    }

    // =========================================================================
    // Anotación @Auditable
    // =========================================================================

    /**
     * Marca un método de servicio para ser auditado automáticamente.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Auditable {
        /** Nombre de la acción, ej: "DELETE_SOCIO", "UPDATE_RUTA". */
        String accion();
        /** Entidad afectada, ej: "socios", "rutas". Opcional. */
        String entidad() default "";
        /**
         * Nombre del parámetro del método cuyo valor se usará como {@code entidad_id}.
         * Ej: {@code idArgName = "id"} tomará el argumento llamado "id".
         */
        String idArgName() default "";
        /**
         * Si {@code true}, intenta extraer el id del valor de retorno llamando
         * {@code id()} (record) o {@code getId()} (bean). Ignorado si el método es void.
         */
        boolean idFromReturn() default false;
        /**
         * Descripción legible en español de la acción para mostrar en el log de auditoría.
         * Si está vacío, el campo detalle queda null en registros exitosos.
         */
        String detalle() default "";
    }
}
