package com.sadday.app.estadisticas.dto;

import java.util.List;

/**
 * Respuesta del endpoint de estadísticas del dashboard.
 *
 * <p>Incluye totales globales y el desglose mensual de salidas
 * para el rango de meses solicitado.
 */
public record DashboardEstadisticasResponse(
        int totalSalidas,
        int totalRealizadas,
        int totalCanceladas,
        int totalEnCurso,
        int totalPlanificadas,
        List<SalidaMesItem> salidasPorMes
) {

    /**
     * Conteo de salidas para un mes/año concreto, con desglose por estado.
     *
     * @param anio         año (ej. 2025)
     * @param mes          mes 1–12
     * @param total        total de salidas ese mes
     * @param realizadas   salidas con estado REALIZADA
     * @param canceladas   salidas con estado CANCELADA
     * @param enCurso      salidas con estado EN_CURSO
     * @param planificadas salidas con estado PLANIFICADA
     */
    public record SalidaMesItem(
            int anio,
            int mes,
            int total,
            int realizadas,
            int canceladas,
            int enCurso,
            int planificadas
    ) {}
}
