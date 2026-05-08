package com.sadday.app.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApiKeyResponse(
        UUID            id,
        String          nombre,
        OffsetDateTime  createdAt,
        OffsetDateTime  expiresAt,
        OffsetDateTime  lastUsedAt
) {}
