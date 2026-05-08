package com.sadday.app.estadisticas.dto;

import java.time.LocalDate;
import java.util.List;

public record MountainEstadisticaResponse(
        int mountainId,
        String nombre,
        String region,
        int altitud,
        int totalSalidas,
        int salidasRealizadas,
        /** Null si no se ha realizado ninguna salida. */
        LocalDate ultimaSalida,
        List<RutaEstadisticaItem> rutas
) {
    public record RutaEstadisticaItem(int rutaId, String rutaNombre, int totalSalidas) {}
}
