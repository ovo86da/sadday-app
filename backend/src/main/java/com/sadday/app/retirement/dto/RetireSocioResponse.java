package com.sadday.app.retirement.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RetireSocioResponse(
        UUID          socioId,
        String        nombre,
        String        apellido,
        LocalDate     fechaSalida,
        boolean       hasPendingDebt,
        List<String>  actionsPerformed,
        LocalDateTime retiredAt
) {}
