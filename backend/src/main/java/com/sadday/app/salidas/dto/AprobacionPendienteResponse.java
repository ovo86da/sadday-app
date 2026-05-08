package com.sadday.app.salidas.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Información de una inscripción pendiente de aprobación de riesgo,
 * devuelta al Directivo o Jefe de Salida para que puedan decidir.
 */
public record AprobacionPendienteResponse(
        Long participanteId,
        UUID salidaId,
        String salidaNombre,
        LocalDate fechaSalida,

        UUID socioId,
        String socioNombre,
        String socioApellido,

        /** Nivel técnico actual del socio (puede ser null si no tiene nivel asignado). */
        String nivelSocioNombre,

        /** Nivel mínimo requerido por la salida. */
        String nivelMinimoNombre,

        /** true si el Directivo/Admin ya aprobó. */
        boolean aprobadoPorDirectivo,

        /** true si el Jefe de Salida ya aprobó. */
        boolean aprobadoPorJefe
) {}
