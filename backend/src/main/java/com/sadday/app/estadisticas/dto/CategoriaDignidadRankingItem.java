package com.sadday.app.estadisticas.dto;

import java.util.List;

public record CategoriaDignidadRankingItem(
        String                 tipoActividad,
        String                 dignidad,
        List<SocioRankingItem> top
) {}
