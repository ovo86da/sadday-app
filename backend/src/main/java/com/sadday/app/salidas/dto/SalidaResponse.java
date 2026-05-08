package com.sadday.app.salidas.dto;

import com.sadday.app.mountains.dto.RutaDocumentoResponse;
import com.sadday.app.salidas.entity.EstadoSalida;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record SalidaResponse(
        UUID id,
        String nombre,
        LocalDate fechaInicio,
        LocalTime horaEncuentroClub,
        LocalDate fechaFin,
        LocalTime horaEstimadaRegresoClub,
        Integer rutaId,
        String rutaNombre,
        String tipoActividad,
        String publicoObjetivoId,
        String publicoObjetivoNombre,
        String formatoSalidaId,
        String formatoSalidaNombre,
        String nivelMinimoRequeridoId,
        String nivelMinimoRequeridoNombre,
        Short capacidadMaxima,
        int totalInscritos,
        boolean inscripcionesCerradas,
        EstadoSalida estado,
        String motivoCancelacion,
        UUID creadoPorId,
        String creadoPorNombreCompleto,
        List<ParticipanteResponse> participantes,
        List<RutaDocumentoResponse> documentosPermiso,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
