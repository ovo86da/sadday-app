package com.sadday.app.admin.dto;

import java.time.LocalDate;
import java.util.List;

public record AuditoriaFiltroRequest(
        String       actorUsername,
        String       accion,
        List<String> omitirAcciones,
        String       resultado,
        String       entidadAfectada,
        String       entidadId,
        LocalDate    fechaDesde,
        LocalDate    fechaHasta
) {}
