package com.sadday.app.estadisticas.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SalidaPeriodoItem(
        UUID      salidaId,
        String    nombre,
        LocalDate fecha,
        String    tipoActividad,
        String    mountainNombre,
        String    rutaNombre,
        String    estado,
        int       totalParticipantes,
        Boolean   seRealizo
) {}
