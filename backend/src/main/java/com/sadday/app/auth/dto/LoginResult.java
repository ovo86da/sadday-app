package com.sadday.app.auth.dto;

/**
 * Resultado interno de login/refresh que agrupa el payload del body
 * y el refresh token crudo (que va a la cookie HttpOnly, nunca al JSON).
 *
 * <p>Solo circula entre {@code AuthService} y {@code AuthController}.
 * Jamás se serializa directamente.
 */
public record LoginResult(
        LoginResponse response,
        String rawRefreshToken
) {}
