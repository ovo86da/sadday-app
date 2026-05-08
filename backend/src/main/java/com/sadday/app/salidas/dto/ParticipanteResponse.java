package com.sadday.app.salidas.dto;

import com.sadday.app.salidas.entity.EstadoInscripcion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de un participante inscrito en una salida.
 *
 * <p>Los campos de nivel ({@code nivelSocioId}, {@code nivelSocioNombre},
 * {@code nivelMinimoRequeridoNombre}, {@code nivelInsuficiente}) permiten al
 * frontend renderizar advertencias y el banner de aprobación de riesgo.
 *
 * <p>Cuando {@code estadoInscripcion == PENDIENTE_APROBACION}, los campos
 * {@code riesgoAprobadoPorDirectivo} y {@code riesgoAprobadoPorJefe} indican
 * qué aprobaciones ya están completas. Cuando ambos tienen valor, el estado
 * pasa automáticamente a INSCRITO.
 */
public record ParticipanteResponse(
        Long id,
        UUID socioId,
        String socioNombre,
        String socioApellido,
        EstadoInscripcion estadoInscripcion,
        Boolean esJefeSalida,
        List<DignidadAsignadaResponse> dignidades,

        // Nivel técnico del socio
        String nivelSocioId,
        String nivelSocioNombre,

        // Nivel mínimo requerido por la salida (null si la salida no tiene mínimo)
        String nivelMinimoRequeridoId,
        String nivelMinimoRequeridoNombre,

        // true cuando nivel del socio < nivel mínimo requerido
        boolean nivelInsuficiente,

        // Aprobación de riesgo (solo relevante cuando nivelInsuficiente = true)
        UUID riesgoAprobadoPorDirectivo,
        String riesgoAprobadoPorDirectivoNombre,
        UUID riesgoAprobadoPorJefe,
        String riesgoAprobadoPorJefeNombre,
        LocalDateTime riesgoAprobadoEn,

        /** Motivo del Directivo/Admin al aprobar o negar (null si aún no ha decidido). */
        String motivoDirectivo,

        /** Motivo del Jefe de Salida al aprobar o negar (null si aún no ha decidido). */
        String motivoJefe,

        LocalDateTime createdAt,

        // Datos de contacto (para exportación)
        String cedula,
        String telefono,
        Integer edad
) {}
