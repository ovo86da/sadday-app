package com.sadday.app.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConfigRequest(
        @NotBlank @Size(max = 500) String valor
) {}
