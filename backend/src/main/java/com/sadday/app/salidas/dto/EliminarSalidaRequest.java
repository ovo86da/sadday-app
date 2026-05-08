package com.sadday.app.salidas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EliminarSalidaRequest(
        @NotBlank @Size(max = 500) String motivo
) {}
