package com.sadday.app.informes.dto;

import java.time.LocalDate;
import java.util.UUID;

public record InformePendienteResponse(
        UUID salidaId,
        String salidaNombre,
        LocalDate fechaFin
) {}
