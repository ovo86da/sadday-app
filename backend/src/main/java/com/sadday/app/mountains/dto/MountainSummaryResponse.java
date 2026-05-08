package com.sadday.app.mountains.dto;

public record MountainSummaryResponse(
        Integer id,
        String nombre,
        String region,
        Integer altitud,
        String pais
) {}
