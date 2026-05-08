package com.sadday.app.mountains.dto;

import java.time.LocalDateTime;

public record MountainResponse(
        Integer id,
        String nombre,
        String region,
        Integer altitud,
        String pais,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
