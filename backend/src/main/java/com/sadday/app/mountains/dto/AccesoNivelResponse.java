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
        // Nulos mientras la directiva no los fije (FR-028). Un nulo aqui
        // significa «no configurado», que no es lo mismo que «sin limite».
        String maxSenderismoId,
        String maxSenderismoNombre,
        String maxCiclismoId,
        String maxCiclismoNombre,
        LocalDateTime updatedAt
) {}
