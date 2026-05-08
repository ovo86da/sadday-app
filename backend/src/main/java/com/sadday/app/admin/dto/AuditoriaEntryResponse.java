package com.sadday.app.admin.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de un registro del log de auditoría.
 *
 * <p>Los campos {@code datosAnteriores} y {@code datosNuevos} se devuelven como
 * String (JSON serializado) para permitir al frontend mostrarlo como texto o parsearlo.
 */
public record AuditoriaEntryResponse(
        Long          id,
        String        actorUsername,
        String        actorNombre,
        String        accion,
        String        entidadAfectada,
        String        entidadId,
        String        entidadNombre,
        String        datosAnteriores,
        String        datosNuevos,
        String        ipAddress,
        String        resultado,
        String        detalle,
        LocalDateTime createdAt
) {}
