package com.sadday.app.socios.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Respuesta para un registro de cuota del historial de pagos de un socio.
 */
public record CuotaResponse(
        Long          id,
        BigDecimal    valor,
        LocalDate     fecha,
        String        estado,
        String        registradoPorNombre,
        LocalDateTime createdAt
) {}
