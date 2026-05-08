package com.sadday.app.salidas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición para decidir sobre el riesgo de una inscripción PENDIENTE_APROBACION.
 *
 * @param aprobar {@code true} para aprobar, {@code false} para negar.
 * @param motivo  Razón obligatoria (tanto para aprobación como para negación).
 */
public record DecidirRiesgoRequest(
        boolean aprobar,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo
) {}
