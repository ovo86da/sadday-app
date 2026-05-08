package com.sadday.app.salidas.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Información básica de una salida que se solapa en fechas con la que se está creando/editando.
 */
public record SolapamientoResponse(
        UUID id,
        String nombre,
        LocalDate fechaInicio,
        LocalDate fechaFin
) {}
