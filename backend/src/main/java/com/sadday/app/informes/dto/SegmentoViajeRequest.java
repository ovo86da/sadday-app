package com.sadday.app.informes.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SegmentoViajeRequest(
        @NotBlank @Size(max = 200) String origen,
        @NotBlank @Size(max = 200) String destino,
        @NotNull Boolean alquiloTransporte,
        @Pattern(
            regexp = "^(CAMIONETA|FURGONETA|BUS_MEDIANO|BUS_GRANDE)?$",
            message = "Tipo de transporte inválido"
        ) String tipoTransporte,
        @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal costoIndividual,
        Integer contactoId
) {}
