package com.sadday.app.estadisticas.dto;

import java.time.LocalDate;

public record RutaPeriodoItem(
        int       rutaId,
        String    nombre,
        String    tipoActividad,
        String    mountainNombre,
        int       totalSalidas,
        int       totalParticipantes,
        LocalDate primeraFecha,
        LocalDate ultimaFecha
) {}
