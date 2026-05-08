package com.sadday.app.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SecurityEventResponse(
        UUID           id,
        String         username,
        String         nombre,
        String         eventType,
        String         ipAddress,
        String         countryCode,
        String         city,
        String         browser,
        String         os,
        OffsetDateTime createdAt,
        String         metadata
) {}
