package com.sadday.app.audit;

import com.sadday.app.security.jwt.SaddayAuthDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Escribe eventos sensibles en la tabla append-only {@code audit_log}.
 *
 * <p>El actor se resuelve automáticamente desde el SecurityContext del hilo llamante.
 * La escritura es síncrona pero envuelta en try-catch: un fallo de auditoría nunca
 * interrumpe el flujo de negocio principal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAuditService {

    private final JdbcClient jdbcClient;

    private static final String INSERT_SQL = """
            INSERT INTO audit_log
                (actor_user_id, action, resource_type, resource_id,
                 ip_address, user_agent, metadata_json)
            VALUES
                (:actorUserId, :action, :resourceType, :resourceId,
                 :ipAddress, :userAgent, CAST(:metadataJson AS jsonb))
            """;

    public void log(AuditAction action, String resourceType, UUID resourceId) {
        log(action, resourceType, resourceId, null, null, null);
    }

    public void log(AuditAction action, String resourceType, UUID resourceId,
                    String ip, String ua, Map<String, Object> metadata) {
        try {
            UUID actorId = resolveActorId();
            String metadataJson = toJson(metadata);
            jdbcClient.sql(INSERT_SQL)
                    .param("actorUserId",   actorId)
                    .param("action",        action.name())
                    .param("resourceType",  resourceType)
                    .param("resourceId",    resourceId)
                    .param("ipAddress",     ip)
                    .param("userAgent",     ua)
                    .param("metadataJson",  metadataJson)
                    .update();
        } catch (Exception e) {
            log.error("Error al escribir audit_log [action={}, resource={}/{}]: {}",
                    action, resourceType, resourceId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private UUID resolveActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof SaddayAuthDetails details) {
            return details.socioId();
        }
        return null;
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : metadata.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) {
                sb.append("\"").append(s.replace("\"", "\\\"")).append("\"");
            } else {
                sb.append(v);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
