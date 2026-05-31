package com.sadday.app.mountains.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarRutaRequest(
        @NotBlank @Size(max = 500) String motivo
) {}
