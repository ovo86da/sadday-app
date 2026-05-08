package com.sadday.app.actas.dto;

import com.sadday.app.actas.entity.TipoActa;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record ActaSummaryResponse(
        UUID id,
        TipoActa tipoActa,
        Integer numeroReunion,
        LocalDate fecha,
        LocalTime hora,
        LocalTime horaFin,
        String lugar,
        int totalAsistentes,
        UUID presidenteReunionId,
        String presidenteReunionNombre,
        UUID creadaPorId,
        String creadaPorNombre,
        LocalDateTime createdAt
) {}
