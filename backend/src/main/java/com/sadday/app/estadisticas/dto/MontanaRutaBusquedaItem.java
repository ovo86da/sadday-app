package com.sadday.app.estadisticas.dto;

public record MontanaRutaBusquedaItem(
        int    id,
        String nombre,
        String tipo,
        String tipoActividad,
        String mountainNombre,
        int    totalSalidas,
        int    totalParticipantes
) {}
