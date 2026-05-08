package com.sadday.app.estadisticas.dto;


import java.util.List;

/** Estadísticas y rankings de asistencia a reuniones. */
public record ReunionesRankingResponse(
        int totalReuniones,
        double promedioAsistentesGlobal,
        List<SocioRankingItem>           topAsistentes,
        List<SocioRankingItem>           menosAsistentes,
        List<ReunionAsistenciaMesItem>   asistenciaPorMes
) {}
