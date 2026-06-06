package com.sadday.app.legal.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LegalDocumentAcceptanceAdminResponse(
        UUID id,
        UUID socioId,
        String socioNombreCompleto,
        String socioCedula,
        String documentCode,
        Integer documentVersion,
        LocalDateTime acceptedAt,
        String ipAddress
) {}
