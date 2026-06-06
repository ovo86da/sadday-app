package com.sadday.app.legal.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LegalDocumentSummaryResponse(
        UUID id,
        String code,
        String title,
        String documentType,
        String requiredStage,
        Integer version,
        boolean active,
        boolean required,
        LocalDateTime approvedAt,
        LocalDateTime createdAt
) {}
