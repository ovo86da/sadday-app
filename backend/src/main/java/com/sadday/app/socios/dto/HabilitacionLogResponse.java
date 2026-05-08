package com.sadday.app.socios.dto;

import com.sadday.app.socios.entity.SocioHabilitacionLog;

import java.time.OffsetDateTime;

public record HabilitacionLogResponse(
        Long id,
        String estadoAnterior,
        String estadoNuevo,
        String cambiadoPorNombre,
        OffsetDateTime cambiadoEn,
        String fuente,
        String notas
) {
    public static HabilitacionLogResponse from(SocioHabilitacionLog log) {
        return new HabilitacionLogResponse(
                log.getId(),
                log.getEstadoAnterior().getNombre(),
                log.getEstadoNuevo().getNombre(),
                log.getCambiadoPor().getNombre() + " " + log.getCambiadoPor().getApellido(),
                log.getCambiadoEn(),
                log.getFuente(),
                log.getNotas()
        );
    }
}
