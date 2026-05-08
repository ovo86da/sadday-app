package com.sadday.app.actas.dto;

import com.sadday.app.actas.entity.TipoActa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateActaRequest(
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
        UUID secretariaReunionId,
        /** IDs de socios a registrar como asistentes (opcional, se pueden agregar luego). */
        @Size(max = 500) List<UUID> asistentesIds,
        /** IDs de informes de salida a vincular (opcional). */
        @Size(max = 100) List<UUID> informesIds
) {}
