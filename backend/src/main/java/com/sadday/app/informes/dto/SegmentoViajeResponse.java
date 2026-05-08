package com.sadday.app.informes.dto;

import java.math.BigDecimal;

public record SegmentoViajeResponse(
        Long id,
        Short orden,
        String origen,
        String destino,
        Boolean alquiloTransporte,
        String tipoTransporte,
        BigDecimal costoIndividual,
        Integer contactoId,
        String contactoNombre,
        String contactoTelefono
) {}
