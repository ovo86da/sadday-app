package com.sadday.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CountryChallengeVerifyRequest(
        @NotBlank String challengeToken,
        @NotBlank @Size(min = 6, max = 6) @Pattern(regexp = "\\d{6}") String code
) {}
