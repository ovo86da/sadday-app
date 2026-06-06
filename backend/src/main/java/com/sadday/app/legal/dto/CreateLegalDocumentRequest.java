package com.sadday.app.legal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateLegalDocumentRequest(
        @NotBlank String code,
        @NotBlank String title,
        String description,
        @NotBlank String documentType,
        @NotBlank
        @Pattern(regexp = "REGISTRATION|PROFILE_COMPLETION|ACTIVITY_ENROLLMENT",
                 message = "requiredStage debe ser REGISTRATION, PROFILE_COMPLETION o ACTIVITY_ENROLLMENT")
        String requiredStage,
        @NotBlank String content,
        boolean required,
        boolean requiresReacceptanceOnNewVersion
) {}
