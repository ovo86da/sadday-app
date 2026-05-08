package com.sadday.app.notificaciones.dto;

import java.time.LocalDate;
import java.util.List;

public record CumpleanosResponse(
        LocalDate fecha,
        int total,
        List<CumpleanosItem> cumpleanos
) {}
