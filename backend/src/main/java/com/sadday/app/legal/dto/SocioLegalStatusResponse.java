package com.sadday.app.legal.dto;

import java.util.List;
import java.util.UUID;

public record SocioLegalStatusResponse(
        UUID socioId,
        boolean allRequiredDocumentsAccepted,
        List<DocumentStatusDto> documents
) {
    public record DocumentStatusDto(
            UUID documentId,
            String code,
            String title,
            String requiredStage,
            boolean accepted,
            Integer acceptedVersion,
            Integer activeVersion,
            boolean needsReacceptance
    ) {}
}
