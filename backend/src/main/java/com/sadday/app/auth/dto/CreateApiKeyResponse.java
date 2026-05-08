package com.sadday.app.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Respuesta al crear una API Key — incluye el raw una sola vez. */
public record CreateApiKeyResponse(
        UUID            id,
        String          nombre,
        String          key,
        OffsetDateTime  createdAt,
        OffsetDateTime  expiresAt
) {}
