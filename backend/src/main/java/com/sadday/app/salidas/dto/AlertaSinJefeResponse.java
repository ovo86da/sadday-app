package com.sadday.app.salidas.dto;

import java.time.LocalDate;

public record AlertaSinJefeResponse(
        String salidaId,
        String salidaNombre,
        LocalDate fechaSalida,
        String jefeAbandonoNombre
) {}
