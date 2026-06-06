package com.sadday.app.activityrisk.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateActivityRiskDocumentRequest(
        @NotBlank(message = "El contenido del documento es obligatorio")
        String content
) {}
