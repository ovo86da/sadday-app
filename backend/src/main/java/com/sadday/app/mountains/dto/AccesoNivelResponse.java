package com.sadday.app.mountains.dto;

import java.time.LocalDateTime;

public record AccesoNivelResponse(
        Short id,
        String nivelSocioId,
        String nivelSocioNombre,
        String maxIfasId,
        String maxIfasGrado,
        String maxRocaId,
        String maxRocaUiaa,
        String maxHieloId,
        String maxHieloGrado,
        String maxCompromisoId,
        String maxCompromisoTipo,
        String maxYosemiteId,
        String maxYosemiteTipo,
        String maxSaddayTecnicoId,
        String maxSaddayTecnicoEscala,
        String maxSaddayFisicoId,
        String maxSaddayFisicoEscala,
        LocalDateTime updatedAt
) {}
