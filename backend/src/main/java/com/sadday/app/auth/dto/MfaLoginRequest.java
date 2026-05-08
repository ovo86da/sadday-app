package com.sadday.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Segundo paso del login MFA: token de desafío + código TOTP.
 */
public record MfaLoginRequest(

        @NotBlank(message = "El token de desafío es obligatorio")
        @Size(max = 100)
        String challengeToken,

        @NotBlank(message = "El código de autenticación es obligatorio")
        @Pattern(regexp = "^\\d{6}$", message = "El código debe ser de exactamente 6 dígitos")
        String mfaCode
) {}
