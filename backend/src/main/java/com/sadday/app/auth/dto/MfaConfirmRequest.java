package com.sadday.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Código TOTP para confirmar la activación o desactivación del 2FA.
 */
public record MfaConfirmRequest(

        @NotBlank(message = "El código MFA es obligatorio")
        @Pattern(regexp = "^\\d{6}$", message = "El código MFA debe ser de 6 dígitos")
        String code
) {}
