package com.sadday.app.emergencycontacts.dto;

import jakarta.validation.constraints.*;

public record EmergencyContactRequest(

        @NotNull(message = "El orden es obligatorio (1 o 2)")
        @Min(value = 1, message = "El orden debe ser 1 o 2")
        @Max(value = 2, message = "El orden debe ser 1 o 2")
        Short orden,

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 200)
        String nombreCompleto,

        @NotBlank(message = "La relación es obligatoria")
        @Size(max = 100)
        String relacion,

        @Pattern(regexp = "^[0-9]{0,15}$", message = "El celular debe contener solo dígitos (máximo 15)")
        @Size(max = 15)
        String celular,

        @Size(max = 500)
        String direccion
) {}
