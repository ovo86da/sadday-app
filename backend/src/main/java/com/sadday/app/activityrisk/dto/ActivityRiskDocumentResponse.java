package com.sadday.app.activityrisk.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ActivityRiskDocumentResponse(
        UUID          id,
        UUID          activityId,
        Integer       version,
        String        content,
        String        contentHash,
        boolean       active,
        LocalDateTime approvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
