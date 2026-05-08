package com.sadday.app.estadisticas.dto;

import java.util.List;

/**
 * Estadísticas agregadas del club: distribución de socios por nivel técnico,
 * tipo de socio, estado de habilitación y dignidades más frecuentes en salidas.
 */
public record ClubEstadisticasResponse(
        int totalSocios,
        int habilitados,
        int inhabilitados,
        List<NivelTecnicoItem>  porNivelTecnico,
        List<TipoSocioItem>     porTipoSocio,
        List<DignidadGlobalItem> topDignidades
) {

    public record NivelTecnicoItem(String nombre, int total, double porcentaje) {}

    public record TipoSocioItem(String nombre, int total) {}

    /** Dignidad con totales de asignaciones y socios únicos que la han tenido. */
    public record DignidadGlobalItem(String nombre, long asignaciones, long sociosUnicos) {}
}
