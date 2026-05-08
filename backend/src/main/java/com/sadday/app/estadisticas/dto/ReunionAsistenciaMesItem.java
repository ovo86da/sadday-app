package com.sadday.app.estadisticas.dto;

/** Asistencia promedio a reuniones en un mes determinado. */
public record ReunionAsistenciaMesItem(
        int anio,
        int mes,
        int totalReuniones,
        int totalAsistencias,
        double promedioAsistentes
) {}
