package com.sadday.app.salidas.dto;

import com.sadday.app.mountains.dto.EvaluacionNivelResponse;

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
        boolean aprobadoPorJefe,

        /** true si la salida tiene un Jefe de Salida designado. Si false y aprobadoPorJefe es false, la inscripción no puede completarse. */
        boolean hayJefeSalida,
        /**
         * Por qué esta inscripción está parada, escala por escala (FR-028).
         *
         * <p>Es lo que hace la diferencia entre poder decidir y no poder: «nivel
         * insuficiente» no dice nada, «la ruta pide WI3 y su nivel llega a WI2» sí.
         * Incluye también lo que el sistema no pudo comprobar, que frena igual.
         */
        EvaluacionNivelResponse evaluacionNivel
) {}
