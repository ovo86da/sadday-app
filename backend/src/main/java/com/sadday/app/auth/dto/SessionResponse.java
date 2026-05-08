package com.sadday.app.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionResponse(
        UUID        sessionId,
        String      platform,
        String      browser,
        String      os,
        String      city,
        String      country,
        String      ipAddress,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        boolean     isCurrent
) {}
