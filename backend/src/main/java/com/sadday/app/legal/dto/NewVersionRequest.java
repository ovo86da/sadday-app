package com.sadday.app.legal.dto;

import jakarta.validation.constraints.NotBlank;

public record NewVersionRequest(@NotBlank String content) {}
