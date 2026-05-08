package com.sadday.app.mountains.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateMountainRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String region,
        @NotNull @Positive Integer altitud,
        @NotBlank @Size(max = 100) String pais
) {}
