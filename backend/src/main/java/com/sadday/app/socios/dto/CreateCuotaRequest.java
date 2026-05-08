package com.sadday.app.socios.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo de la petición para registrar un pago/cuota en el historial de un socio.
 */
public record CreateCuotaRequest(

        @NotNull(message = "El valor es obligatorio")
        @Positive(message = "El valor debe ser mayor a 0")
        BigDecimal valor,

        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha,

        @NotBlank(message = "El estado es obligatorio")
        @Pattern(regexp = "PAGADO|PENDIENTE", message = "El estado debe ser PAGADO o PENDIENTE")
        String estado
) {}
