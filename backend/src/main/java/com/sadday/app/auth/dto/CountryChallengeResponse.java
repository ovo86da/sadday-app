package com.sadday.app.auth.dto;

/**
 * Respuesta del primer paso de login cuando se detecta un país desconocido sin 2FA activo.
 *
 * <p>El cliente debe presentar este {@code challengeToken} junto con el código de 6 dígitos
 * recibido por email en POST /v1/auth/country-challenge/verify para completar la autenticación.
 */
public record CountryChallengeResponse(
        String countryChallengeToken,
        int    expiresIn   // segundos hasta que expira el desafío
) {}
