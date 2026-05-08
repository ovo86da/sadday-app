package com.sadday.app.estadisticas.dto;

import java.util.List;
import java.util.UUID;

public record SocioHistorialResponse(
        UUID socioId,
        String nombre,
        String apellido,
        /** Total de participaciones no canceladas. */
        int totalParticipaciones,
        /** Salidas con informe donde seRealizo = true. */
        int totalCumbresLogradas,
        /** Veces que fue Jefe de Salida. */
        int vecesJefeSalida,
        List<SalidaHistorialItem> historial,
        /** Conteo de dignidades agrupadas por nombre (para gráficas). */
        List<DignidadConteoItem> conteosDignidades
) {}
