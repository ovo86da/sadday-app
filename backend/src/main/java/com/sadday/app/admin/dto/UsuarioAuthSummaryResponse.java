package com.sadday.app.admin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resumen de un usuario de autenticación para el módulo Admin.
 *
 * <p>Incluye datos de {@code usuarios_auth} y datos básicos de identificación de {@code socios}
 * (nombre, apellido, correo). No expone {@code password_hash} ni {@code totp_secret}.
 */
public record UsuarioAuthSummaryResponse(
        UUID          socioId,
        String        username,
        boolean       totpEnabled,
        short         failedAttempts,
        boolean       loginBlocked,
        LocalDateTime blockedUntil,
        LocalDateTime lastLogin,
        LocalDateTime createdAt,
        // Datos del socio asociado
        String        nombre,
        String        apellido,
        String        correo,
        /** Código del estado de acceso, ej: "ACTIVE", "BLOCKED". */
        String        estadoAcceso,
        /** Nombre legible del estado de acceso, ej: "Activo", "Bloqueado". */
        String        estadoAccesoNombre
) {}
