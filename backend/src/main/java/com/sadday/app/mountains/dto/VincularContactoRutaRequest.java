package com.sadday.app.mountains.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VincularContactoRutaRequest(
        @NotNull Integer contactoId,
        @NotBlank String tipoContacto  // GUIA, TRANSPORTE, REFUGIO, ALMUERZO
) {}
