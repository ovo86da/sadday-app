package com.sadday.app.actas.dto;

import com.sadday.app.actas.entity.TipoActa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record UpdateActaRequest(
        @NotNull TipoActa tipoActa,
        Integer numeroReunion,
        @NotNull LocalDate fecha,
        @NotNull LocalTime hora,
        LocalTime horaFin,
        @Size(max = 200) String lugar,
        @Size(max = 5000) String actividadesRealizadasDesc,
        @Size(max = 5000) String actividadesPorRealizar,
        @Size(max = 5000) String acuerdos,
        @Size(max = 2000) String varios,
        @Size(max = 2000) String observaciones,
        UUID presidenteReunionId,
        UUID secretariaReunionId
) {}
