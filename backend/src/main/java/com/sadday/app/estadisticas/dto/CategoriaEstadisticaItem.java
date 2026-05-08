package com.sadday.app.estadisticas.dto;

public record CategoriaEstadisticaItem(
        String tipoActividad,
        int    totalSalidas,
        int    totalParticipantes
) {}
