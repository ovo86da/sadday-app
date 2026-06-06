package com.sadday.app.legal.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LegalDocumentAcceptanceResponse(
        UUID id,
        UUID documentId,
        String documentCode,
        String documentTitle,
        Integer documentVersion,
        LocalDateTime acceptedAt,
        boolean accepted
) {}
