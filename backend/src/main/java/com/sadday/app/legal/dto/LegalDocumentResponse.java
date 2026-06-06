package com.sadday.app.legal.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LegalDocumentResponse(
        UUID id,
        String code,
        String title,
        String description,
        String documentType,
        String requiredStage,
        Integer version,
        String content,
        String contentHash,
        boolean active,
        boolean required,
        boolean requiresReacceptanceOnNewVersion,
        LocalDateTime approvedAt,
        String approvedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
