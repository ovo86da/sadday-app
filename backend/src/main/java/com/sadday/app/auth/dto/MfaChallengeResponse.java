package com.sadday.app.auth.dto;

/**
 * Respuesta del primer paso de login cuando el usuario tiene 2FA activo.
 *
 * <p>El cliente debe presentar este {@code challengeToken} junto con el código TOTP
 * en POST /v1/auth/mfa/login para completar la autenticación.
 */
public record MfaChallengeResponse(
        String challengeToken,
        int    expiresIn   // segundos hasta que expira el desafío
) {}
