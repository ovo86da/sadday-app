package com.sadday.app.mountains.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RutaSummaryResponse(
        Integer id,
        String nombre,
        String tipoActividad,
        Integer mountainId,
        String mountainNombre,
        String lugarReferencia,
        String sectorZona,
        BigDecimal longitudKm,
        Integer desnivelM,
        Short duracionDias,
        Short duracionHoras,
        Boolean requierePermisos,
        String trackUrl,
        String nivelMinimoSocioId,
        String nivelMinimoSocioNombre,
        Boolean aprobada,
        UUID propuestaPorId,
        LocalDateTime createdAt,

        // Resumen de dificultad según tipo (texto legible para la lista)
        String dificultadResumen
) {}
