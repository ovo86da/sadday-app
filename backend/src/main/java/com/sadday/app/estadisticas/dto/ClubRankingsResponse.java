package com.sadday.app.estadisticas.dto;

import java.util.List;

/**
 * Rankings del club: quiénes han sido más veces jefes de salida,
 * quiénes más han participado, y top por cada tipo de dignidad.
 */
public record ClubRankingsResponse(
        List<SocioRankingItem>              topJefesSalida,
        List<SocioRankingItem>              topParticipaciones,
        List<DignidadRankingItem>           topPorDignidad,
        List<CategoriaEstadisticaItem>      porCategoria,
        List<CategoriaDignidadRankingItem>  rankingsPorCategoria
) {

    /** Top N socios para una dignidad específica. */
    public record DignidadRankingItem(String dignidad, List<SocioRankingItem> top) {}
}
