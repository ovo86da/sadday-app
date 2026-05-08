package com.sadday.app.estadisticas.dto;

import java.time.LocalDate;

public record MontanaPeriodoItem(
        int       mountainId,
        String    nombre,
        String    region,
        int       altitud,
        int       totalSalidas,
        int       totalParticipantes,
        LocalDate primeraFecha,
        LocalDate ultimaFecha
) {}
