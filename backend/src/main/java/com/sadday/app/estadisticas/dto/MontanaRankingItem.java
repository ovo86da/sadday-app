package com.sadday.app.estadisticas.dto;

public record MontanaRankingItem(
        int    mountainId,
        String nombre,
        String region,
        int    altitud,
        int    totalSalidas
) {}
