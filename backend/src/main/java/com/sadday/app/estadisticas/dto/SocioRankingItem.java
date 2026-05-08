package com.sadday.app.estadisticas.dto;

import java.util.UUID;

/**
 * Entrada en un ranking de socios (por participaciones, jefaturas, dignidades, etc.).
 */
public record SocioRankingItem(
        UUID   socioId,
        String nombre,
        String apellido,
        String nivelTecnico,
        int    total
) {}
