package com.sadday.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WizardContactoDto(
        @NotBlank(message = "El nombre del contacto es obligatorio")
        @Size(max = 200)
        String nombreCompleto,

        @NotBlank(message = "La relación es obligatoria")
        @Size(max = 100)
        String relacion,

        @Size(max = 20)
        String celular,

        @Size(max = 500)
        String direccion
) {}
