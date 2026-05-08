package com.sadday.app.actas.dto;

import com.sadday.app.actas.entity.TipoActa;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ActaResponse(
        UUID id,
        TipoActa tipoActa,
        Integer numeroReunion,
        LocalDate fecha,
        LocalTime hora,
        LocalTime horaFin,
        String lugar,
        String actividadesRealizadasDesc,
        String actividadesPorRealizar,
        String acuerdos,
        String varios,
        String observaciones,
        UUID presidenteReunionId,
        String presidenteReunionNombre,
        UUID secretariaReunionId,
        String secretariaReunionNombre,
        List<AsistenteResponse> asistentes,
        List<InformeLinkResponse> informes,
        UUID documentoId,
        String documentoFilename,
        UUID creadaPorId,
        String creadaPorNombre,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
