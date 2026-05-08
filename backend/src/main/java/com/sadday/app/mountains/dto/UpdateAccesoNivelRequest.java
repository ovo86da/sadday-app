package com.sadday.app.mountains.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccesoNivelRequest(
        @NotBlank String maxIfasId,
        @NotBlank String maxRocaId,
        @NotBlank String maxHieloId,
        @NotBlank String maxCompromisoId,
        @NotBlank String maxYosemiteId,
        @NotBlank String maxSaddayTecnicoId,
        @NotBlank String maxSaddayFisicoId
) {}
