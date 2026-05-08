package com.sadday.app.estadisticas.dto;

import java.util.List;

public record RankingMontanaRutaResponse(
        List<MontanaRankingItem> topMontanasMasSalidas,
        List<MontanaRankingItem> topMontanasMenosSalidas,
        List<RutaRankingItem>    topRutasMasSalidas,
        List<RutaRankingItem>    topRutasMenosSalidas,
        List<RutaRankingItem>    topRutasMasParticipantes
) {}
