package com.sadday.app.mountains.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RutaDocumentoResponse(
        UUID   id,
        String filename,
        String contentType,
        Long   sizeBytes,
        String subidoPorNombre,
        LocalDateTime createdAt
) {}
