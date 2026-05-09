package com.sadday.app.admin.service;

import com.sadday.app.admin.dto.AuditoriaEntryResponse;
import com.sadday.app.admin.dto.AuditoriaFiltroRequest;
import com.sadday.app.admin.dto.SecurityEventResponse;
import com.sadday.app.admin.dto.UsuarioAuthSummaryResponse;
import com.sadday.app.auth.repository.RefreshTokenRepository;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.auth.service.SecurityEventService;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio del módulo Admin.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Consultar el log de auditoría (solo lectura, tabla append-only).</li>
 *   <li>Listar usuarios de autenticación con su estado (bloqueado / activo).</li>
 *   <li>Desbloquear cuentas bloqueadas por intentos fallidos de login.</li>
 * </ul>
 *
 * <p>Acceso restringido a roles ADMIN y SECRETARIA (validado en {@code SecurityConfig} y
 * con {@code @PreAuthorize} adicional en el controlador para operaciones de escritura).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final JdbcClient             jdbcClient;
    private final UsuarioAuthRepository  usuarioAuthRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService           auditService;
    private final SecurityEventService   securityEventService;
    private final com.sadday.app.socios.repository.SocioRepository        socioRepository;
    private final com.sadday.app.socios.repository.EstadoAccesoRepository estadoAccesoRepository;

    private static final String COL_CREATED_AT  = "created_at";
    private static final String COL_USERNAME    = "username";
    private static final String RESULTADO_OK    = "SUCCESS";
    private static final String ESTADO_ACTIVO   = "ACTIVE";

    // =========================================================================
    // Log de auditoría (solo lectura)
    // =========================================================================

    /**
     * Devuelve el log de auditoría paginado con filtros opcionales.
     *
     * @param actorUsername   Filtro parcial (ILIKE) sobre el campo actor_username
     * @param accion          Filtro parcial (ILIKE) sobre el campo accion
     * @param resultado       Filtro exacto: "SUCCESS", "FAILED" o "BLOCKED"
     * @param entidadAfectada Filtro parcial (ILIKE) sobre el campo entidad_afectada
     * @param fechaDesde      Fecha de inicio (inclusive)
     * @param fechaHasta      Fecha de fin (inclusive)
     * @param pageable        Paginación (Spring Data)
     */
    @SuppressWarnings("java:S2077") // SQL dinámico seguro: estructura hardcodeada, valores via parámetros nombrados
    @Transactional(readOnly = true)
    public Page<AuditoriaEntryResponse> getAuditoria(AuditoriaFiltroRequest filtro, Pageable pageable) {
        var where  = new StringBuilder(" WHERE 1=1");
        var params = new HashMap<String, Object>();

        if (StringUtils.hasText(filtro.actorUsername())) {
            where.append(" AND actor_username ILIKE :actorUsername");
            params.put("actorUsername", "%" + filtro.actorUsername().trim() + "%");
        }
        if (StringUtils.hasText(filtro.accion())) {
            where.append(" AND accion ILIKE :accion");
            params.put("accion", "%" + filtro.accion().trim() + "%");
        }
        if (filtro.omitirAcciones() != null) {
            List<String> validas = filtro.omitirAcciones().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
            for (int i = 0; i < validas.size(); i++) {
                String key = "omitir" + i;
                where.append(" AND accion != :").append(key);
                params.put(key, validas.get(i));
            }
        }
        if (StringUtils.hasText(filtro.resultado())) {
            where.append(" AND resultado = :resultado");
            params.put("resultado", filtro.resultado().trim().toUpperCase());
        }
        if (StringUtils.hasText(filtro.entidadAfectada())) {
            where.append(" AND entidad_afectada ILIKE :entidadAfectada");
            params.put("entidadAfectada", "%" + filtro.entidadAfectada().trim() + "%");
        }
        if (StringUtils.hasText(filtro.entidadId())) {
            where.append(" AND entidad_id = :entidadId");
            params.put("entidadId", filtro.entidadId().trim());
        }
        if (filtro.fechaDesde() != null) {
            where.append(" AND created_at >= :fechaDesde");
            params.put("fechaDesde", filtro.fechaDesde().atStartOfDay());
        }
        if (filtro.fechaHasta() != null) {
            where.append(" AND created_at < :fechaHasta");
            params.put("fechaHasta", filtro.fechaHasta().plusDays(1).atStartOfDay());
        }

        String base = """
                FROM auditoria a
                LEFT JOIN usuarios_auth ua_actor ON ua_actor.username = a.actor_username
                LEFT JOIN socios s_actor         ON s_actor.id = ua_actor.socio_id
                LEFT JOIN socios s_entidad        ON (
                    a.entidad_afectada IN ('socios', 'usuarios_auth')
                    AND a.entidad_id IS NOT NULL
                    AND s_entidad.id = CASE
                        WHEN a.entidad_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                        THEN a.entidad_id::uuid
                    END
                )
                """ + where;

        long total = Objects.requireNonNullElse(
                jdbcClient.sql("SELECT COUNT(*) " + base).params(params).query(Long.class).single(),
                0L);

        Map<String, Object> pageParams = new HashMap<>(params);
        pageParams.put("limit",  pageable.getPageSize());
        pageParams.put("offset", pageable.getOffset());

        List<AuditoriaEntryResponse> content = jdbcClient
                .sql("""
                        SELECT a.id, a.actor_username,
                               s_actor.nombre  || ' ' || s_actor.apellido  AS actor_nombre,
                               a.accion, a.entidad_afectada, a.entidad_id,
                               s_entidad.nombre || ' ' || s_entidad.apellido AS entidad_nombre,
                               a.datos_anteriores::text, a.datos_nuevos::text, a.ip_address,
                               a.resultado, a.detalle, a.created_at
                        """ + base + " " + """
                        ORDER BY a.created_at DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .params(pageParams)
                .query((rs, rowNum) -> new AuditoriaEntryResponse(
                        rs.getLong("id"),
                        rs.getString("actor_username"),
                        rs.getString("actor_nombre"),
                        rs.getString("accion"),
                        rs.getString("entidad_afectada"),
                        rs.getString("entidad_id"),
                        rs.getString("entidad_nombre"),
                        rs.getString("datos_anteriores"),
                        rs.getString("datos_nuevos"),
                        rs.getString("ip_address"),
                        rs.getString("resultado"),
                        rs.getString("detalle"),
                        rs.getObject(COL_CREATED_AT, LocalDateTime.class)
                ))
                .list();

        return new PageImpl<>(content, pageable, total);
    }

    // =========================================================================
    // Gestión de usuarios de autenticación
    // =========================================================================

    /**
     * Lista todos los usuarios de autenticación con datos básicos del socio asociado.
     * Los bloqueados aparecen primero.
     */
    @Transactional(readOnly = true)
    public List<UsuarioAuthSummaryResponse> getUsuariosAuth() {
        return jdbcClient
                .sql("""
                        SELECT ua.socio_id, ua.username, ua.totp_enabled,
                               ua.failed_attempts, ua.login_blocked, ua.blocked_until,
                               ua.last_login, ua.created_at,
                               s.nombre, s.apellido, s.correo,
                               ea.codigo AS estado_acceso, ea.nombre AS estado_acceso_nombre
                        FROM   usuarios_auth ua
                        JOIN   socios s  ON s.id             = ua.socio_id
                        JOIN   estado_acceso ea ON ea.id     = s.estado_acceso_id
                        ORDER  BY CASE ea.codigo
                                      WHEN 'ACTIVE' THEN 3
                                      WHEN 'PENDING_REGISTER' THEN 2
                                      ELSE 1
                                  END ASC,
                                  s.apellido, s.nombre
                        """)
                .query((rs, rowNum) -> new UsuarioAuthSummaryResponse(
                        rs.getObject("socio_id", UUID.class),
                        rs.getString(COL_USERNAME),
                        rs.getBoolean("totp_enabled"),
                        rs.getShort("failed_attempts"),
                        rs.getBoolean("login_blocked"),
                        rs.getObject("blocked_until", LocalDateTime.class),
                        rs.getObject("last_login", LocalDateTime.class),
                        rs.getObject(COL_CREATED_AT, LocalDateTime.class),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("correo"),
                        rs.getString("estado_acceso"),
                        rs.getString("estado_acceso_nombre")
                ))
                .list();
    }

    /**
     * Devuelve el estado de la cuenta de autenticación de un socio específico.
     *
     * @param socioId UUID del socio
     * @return {@link UsuarioAuthSummaryResponse} con el estado actual, o {@code null} si no tiene cuenta
     */
    @Transactional(readOnly = true)
    public Optional<UsuarioAuthSummaryResponse> getUsuarioAuthBySocio(UUID socioId) {
        return jdbcClient
                .sql("""
                        SELECT ua.socio_id, ua.username, ua.totp_enabled,
                               ua.failed_attempts, ua.login_blocked, ua.blocked_until,
                               ua.last_login, ua.created_at,
                               s.nombre, s.apellido, s.correo,
                               ea.codigo AS estado_acceso, ea.nombre AS estado_acceso_nombre
                        FROM   usuarios_auth ua
                        JOIN   socios s  ON s.id             = ua.socio_id
                        JOIN   estado_acceso ea ON ea.id     = s.estado_acceso_id
                        WHERE  ua.socio_id = :socioId
                        """)
                .param("socioId", socioId)
                .query((rs, rowNum) -> new UsuarioAuthSummaryResponse(
                        rs.getObject("socio_id", UUID.class),
                        rs.getString(COL_USERNAME),
                        rs.getBoolean("totp_enabled"),
                        rs.getShort("failed_attempts"),
                        rs.getBoolean("login_blocked"),
                        rs.getObject("blocked_until", LocalDateTime.class),
                        rs.getObject("last_login", LocalDateTime.class),
                        rs.getObject(COL_CREATED_AT, LocalDateTime.class),
                        rs.getString("nombre"),
                        rs.getString("apellido"),
                        rs.getString("correo"),
                        rs.getString("estado_acceso"),
                        rs.getString("estado_acceso_nombre")
                ))
                .optional();
    }

    /**
     * Desbloquea la cuenta de un socio (reseta {@code login_blocked}, {@code failed_attempts}
     * y {@code blocked_until}).
     *
     * @param socioId       UUID del socio cuya cuenta se desbloquea
     * @param actorUsername Username del administrador que realiza la acción (para auditoría)
     */
    @Transactional
    public void desbloquearUsuario(UUID socioId, String actorUsername) {
        var usuario = usuarioAuthRepository.findBySocioId(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        if (!usuario.isLoginBlocked() && usuario.getFailedAttempts() == 0) {
            // No está bloqueado; no hay nada que hacer
            return;
        }

        usuario.setLoginBlocked(false);
        usuario.setFailedAttempts((short) 0);
        usuario.setBlockedUntil(null);
        usuarioAuthRepository.save(usuario);

        log.info("Admin [{}] desbloqueó la cuenta del socio [{}] (username={})",
                actorUsername, socioId, usuario.getUsername());

        auditService.registrar(
                actorUsername,
                "DESBLOQUEAR_USUARIO",
                "usuarios_auth",
                socioId,
                null,
                null,
                null,
                null,
                RESULTADO_OK,
                "Cuenta desbloqueada manualmente por administrador"
        );
    }

    /**
     * Cambia el estado de acceso al sistema de un socio.
     *
     * <p>Protecciones:
     * <ul>
     *   <li>El rol ADMIN no puede ser bloqueado/deshabilitado bajo ningún concepto.</li>
     *   <li>Si el socio es SECRETARIA, debe quedar al menos una secretaria ACTIVE tras el cambio.</li>
     *   <li>Si el nuevo estado no es ACTIVE, se revocan todos los tokens (desconexión inmediata).</li>
     * </ul>
     */
    @Transactional
    public void cambiarEstadoAcceso(UUID socioId, String nuevoCodigo, String actorUsername) {
        var socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        String rolNombre = socio.getRolSistema().getNombre();

        // Protección: el rol Admin no puede ser bloqueado ni deshabilitado
        if ("Admin".equalsIgnoreCase(rolNombre) && !ESTADO_ACTIVO.equals(nuevoCodigo)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El rol Admin no puede ser bloqueado ni deshabilitado. " +
                    "Cambia el rol antes de restringir el acceso.");
        }

        // Protección: si es secretaria y se le quita ACTIVE, verificar que quede al menos una activa
        if ("Secretaria".equalsIgnoreCase(rolNombre) && !ESTADO_ACTIVO.equals(nuevoCodigo)) {
            long activasRestantes = socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo(
                    "Secretaria", ESTADO_ACTIVO);
            // activasRestantes incluye la actual — si hay solo 1, no se puede quitar
            if (activasRestantes <= 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "No se puede bloquear a la única secretaria activa. " +
                        "Asigna otra secretaria primero.");
            }
        }

        var nuevoEstado = estadoAccesoRepository.findByCodigo(nuevoCodigo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Estado de acceso no encontrado: " + nuevoCodigo));

        String codigoAnterior = socio.getEstadoAcceso().getCodigo();
        socio.setEstadoAcceso(nuevoEstado);
        socioRepository.save(socio);

        // Si el acceso deja de ser ACTIVE, revocar tokens para desconexión inmediata
        int revocados = 0;
        if (!ESTADO_ACTIVO.equals(nuevoCodigo)) {
            revocados = refreshTokenRepository.revokeAllBySocioId(socioId, LocalDateTime.now());
        }

        log.info("[{}] cambió estado_acceso de socio [{}]: {} → {} (tokens revocados={})",
                actorUsername, socioId, codigoAnterior, nuevoCodigo, revocados);

        auditService.registrar(
                actorUsername, "CAMBIAR_ESTADO_ACCESO", "socios",
                socioId,
                "{\"estadoAnterior\":\"" + codigoAnterior + "\"}",
                "{\"estadoNuevo\":\"" + nuevoCodigo + "\",\"tokensRevocados\":" + revocados + "}",
                null, null, RESULTADO_OK,
                "Estado de acceso cambiado de " + codigoAnterior + " a " + nuevoCodigo
        );
    }

    @Transactional
    public void forzarCierreSesion(UUID socioId, String actorUsername) {
        var usuario = usuarioAuthRepository.findBySocioId(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        int revocados = refreshTokenRepository.revokeAllBySocioId(socioId, LocalDateTime.now());

        log.info("Admin [{}] forzó cierre de sesión del socio [{}] (username={}, tokens revocados={})",
                actorUsername, socioId, usuario.getUsername(), revocados);

        auditService.registrar(
                actorUsername,
                "FORZAR_CIERRE_SESION",
                "usuarios_auth",
                socioId,
                null,
                "{\"tokensRevocados\":" + revocados + "}",
                null,
                null,
                RESULTADO_OK,
                "Cierre de sesión forzado por administrador"
        );
    }

    // =========================================================================
    // Eventos de seguridad (solo lectura)
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<SecurityEventResponse> getSecurityEvents(
            String    username,
            String    eventType,
            String    ipAddress,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Pageable  pageable
    ) {
        var where  = new StringBuilder(" WHERE 1=1");
        var params = new HashMap<String, Object>();

        if (StringUtils.hasText(username)) {
            where.append(" AND se.username ILIKE :username");
            params.put(COL_USERNAME, "%" + username.trim() + "%");
        }
        if (StringUtils.hasText(eventType)) {
            where.append(" AND se.event_type = :eventType");
            params.put("eventType", eventType.trim().toUpperCase());
        }
        if (StringUtils.hasText(ipAddress)) {
            where.append(" AND se.ip_address ILIKE :ipAddress");
            params.put("ipAddress", "%" + ipAddress.trim() + "%");
        }
        if (fechaDesde != null) {
            where.append(" AND se.created_at >= :fechaDesde");
            params.put("fechaDesde", fechaDesde.atStartOfDay());
        }
        if (fechaHasta != null) {
            where.append(" AND se.created_at < :fechaHasta");
            params.put("fechaHasta", fechaHasta.plusDays(1).atStartOfDay());
        }

        String base = """
                FROM security_events se
                LEFT JOIN socios s ON s.id = se.socio_id
                """ + where;

        long total = Objects.requireNonNullElse(
                jdbcClient.sql("SELECT COUNT(*) " + base).params(params).query(Long.class).single(),
                0L);

        Map<String, Object> pageParams = new HashMap<>(params);
        pageParams.put("limit",  pageable.getPageSize());
        pageParams.put("offset", pageable.getOffset());

        List<SecurityEventResponse> content = jdbcClient
                .sql("""
                        SELECT se.id, se.username,
                               s.nombre || ' ' || s.apellido AS nombre_completo,
                               se.event_type, se.ip_address, se.country_code, se.city,
                               se.user_agent, se.created_at, se.metadata::text AS metadata
                        """ + base + " " + """
                        ORDER BY se.created_at DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .params(pageParams)
                .query((rs, rowNum) -> {
                    String[] parsed = securityEventService.parseUa(rs.getString("user_agent"));
                    return new SecurityEventResponse(
                            rs.getObject("id", UUID.class),
                            rs.getString(COL_USERNAME),
                            rs.getString("nombre_completo"),
                            rs.getString("event_type"),
                            rs.getString("ip_address"),
                            rs.getString("country_code"),
                            rs.getString("city"),
                            parsed[0],
                            parsed[1],
                            rs.getObject(COL_CREATED_AT, OffsetDateTime.class),
                            rs.getString("metadata")
                    );
                })
                .list();

        return new PageImpl<>(content, pageable, total);
    }
}
