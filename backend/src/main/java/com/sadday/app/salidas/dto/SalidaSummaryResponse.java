package com.sadday.app.salidas.dto;

import com.sadday.app.salidas.entity.EstadoSalida;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record SalidaSummaryResponse(
        UUID id,
        String nombre,
        LocalDate fechaInicio,
        LocalTime horaEncuentroClub,
        LocalDate fechaFin,
        String rutaNombre,
        String tipoActividad,
        String publicoObjetivoNombre,
        String formatoSalidaNombre,
        String nivelMinimoNombre,
        Short capacidadMaxima,
        int totalInscritos,
        boolean inscripcionesCerradas,
        EstadoSalida estado,
        String motivoCancelacion,
        boolean tieneInforme,
        LocalDateTime createdAt
) {}
