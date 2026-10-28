package com.sadday.app.mountains.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Los umbrales propuestos para un nivel.
 *
 * <p>Las siete primeras son obligatorias: la matriz se escribió para alpinismo
 * y siempre han estado ahí. Las dos últimas llegaron con FR-028 y son
 * <strong>opcionales</strong>, porque hasta que la directiva las rellene no hay
 * valor que mandar — y obligar a inventarse uno sería peor que dejarlo vacío.
 */
public record UpdateAccesoNivelRequest(
        @NotBlank String maxIfasId,
        @NotBlank String maxRocaId,
        @NotBlank String maxHieloId,
        @NotBlank String maxCompromisoId,
        @NotBlank String maxYosemiteId,
        @NotBlank String maxSaddayTecnicoId,
        @NotBlank String maxSaddayFisicoId,
        String maxSenderismoId,
        String maxCiclismoId
) {}
