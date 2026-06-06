package com.sadday.app.activityrisk.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ActivityRiskAcceptanceResponse(
        UUID          id,
        UUID          socioId,
        UUID          activityId,
        UUID          riskDocumentId,
        Integer       documentVersion,
        LocalDateTime acceptedAt
) {}
