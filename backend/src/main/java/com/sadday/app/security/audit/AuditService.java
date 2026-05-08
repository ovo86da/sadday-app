package com.sadday.app.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


/**
 * Servicio de auditoría: escribe registros en la tabla {@code auditoria}.
 *
 * <p>Principios de seguridad:
 * <ul>
 *   <li>La tabla {@code auditoria} es append-only: el usuario de la app NO tiene
 *       permisos de UPDATE ni DELETE sobre ella.</li>
 *   <li>Los registros se escriben de forma asíncrona para no bloquear el flujo principal,
 *       pero los errores de auditoría se loguean sin relanzar excepción.</li>
 *   <li>Nunca se loguea información sensible (tokens, contraseñas, TOTP secrets).</li>
 *   <li>Se usa {@link JdbcClient} directamente para evitar que JPA/Hibernate
 *       interfiera con la naturaleza append-only de la tabla.</li>
 *   <li>Se almacena {@code actor_username} (no FK) para poder registrar intentos de
 *       login fallidos de usuarios inexistentes sin violar integridad referencial.</li>
 * </ul>
 *
 * <p>Uso típico desde un servicio:
 * <pre>
 * {@code
 * // Automático via @Auditable en el método
 * @Auditable(accion = "DELETE_SOCIO", entidad = "socios")
 * public void eliminarSocio(UUID id) { ... }
 *
 * // Manual cuando se necesita capturar datos_anteriores / datos_nuevos
 * auditService.registrar(username, "UPDATE_SOCIO", "socios",
 *     socioId, jsonAntes, jsonDespues, ip, ua, "SUCCESS", null);
 * }
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final JdbcClient jdbcClient;

    private static final String INSERT_SQL = """
            INSERT INTO auditoria
                (actor_username, accion, entidad_afectada, entidad_id,
                 datos_anteriores, datos_nuevos, ip_address, user_agent,
                 resultado, detalle)
            VALUES
                (:actorUsername, :accion, :entidad, :entidadId,
                 CAST(:datosAnteriores AS jsonb), CAST(:datosNuevos AS jsonb),
                 :ipAddress, :userAgent,
                 :resultado, :detalle)
            """;

    /**
     * Registra un evento de auditoría de forma asíncrona.
     *
     * <p>Si falla la escritura, se loguea el error pero NO se propaga la excepción,
     * para no interferir con el flujo de negocio principal.
     *
     * @param actorUsername   Username del actor (del SecurityContext o "SYSTEM"); puede ser null
     * @param accion          Código de acción, ej: "DELETE_SOCIO", "LOGIN_FAILED"
     * @param entidad         Tabla/entidad afectada, ej: "socios", "salida"
     * @param entidadId       UUID de la entidad afectada (puede ser null)
     * @param datosAnteriores JSON con snapshot del estado previo (puede ser null)
     * @param datosNuevos     JSON con snapshot del estado posterior (puede ser null)
     * @param ipAddress       IP del cliente (max 45 chars)
     * @param userAgent       User-Agent del cliente
     * @param resultado       "SUCCESS", "FAILED" o "BLOCKED"
     * @param detalle         Mensaje adicional si resultado = "FAILED" (puede ser null)
     */
    @Async
    public void registrar(
            String actorUsername,
            String accion,
            String entidad,
            Object entidadId,
            String datosAnteriores,
            String datosNuevos,
            String ipAddress,
            String userAgent,
            String resultado,
            String detalle
    ) {
        try {
            jdbcClient.sql(INSERT_SQL)
                    .param("actorUsername",    truncate(actorUsername, 100))
                    .param("accion",           truncate(accion, 100))
                    .param("entidad",          truncate(entidad, 100))
                    .param("entidadId",        entidadId != null ? entidadId.toString() : null)
                    .param("datosAnteriores",  datosAnteriores)
                    .param("datosNuevos",      datosNuevos)
                    .param("ipAddress",        truncate(ipAddress, 45))
                    .param("userAgent",        truncate(userAgent, 500))
                    .param("resultado",        resultado)
                    .param("detalle",          truncate(detalle, 1000))
                    .update();

        } catch (Exception e) {
            log.error("Error al registrar evento de auditoría [accion={}, entidad={}]: {}",
                    accion, entidad, e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
