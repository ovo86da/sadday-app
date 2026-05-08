package com.sadday.app.estadisticas.dto;

public record RutaRankingItem(
        int    rutaId,
        String nombre,
        String tipoActividad,
        String mountainNombre,
        int    totalSalidas,
        int    totalParticipantes
) {}
