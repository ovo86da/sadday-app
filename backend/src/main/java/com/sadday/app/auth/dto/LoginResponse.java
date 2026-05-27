package com.sadday.app.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Payload de respuesta de login/refresh enviado en el body JSON.
 *
 * <p><b>Web ({@code X-Sadday-Client: spa}):</b> el refresh token viaja en una
 * cookie {@code HttpOnly; Secure; SameSite=Strict} — el campo {@code refreshToken}
 * es {@code null} y Jackson lo omite de la respuesta.</p>
 *
 * <p><b>Mobile ({@code X-Sadday-Client: mobile}):</b> no se emite cookie; el campo
 * {@code refreshToken} se incluye en el body JSON para que la app lo guarde en
 * el Keychain (iOS) / Keystore (Android).</p>
 *
 * <p>El constructor canónico se usa en el controller a través de {@link #of} (web)
 * o {@link #withRefreshToken} (mobile).</p>
 */
public record LoginResponse(
        String  accessToken,
        String  tokenType,
        long    expiresIn,          // duración del access token en segundos
        UUID    socioId,
        String  username,
        String  nombre,
        String  rol,
        String  nivelTecnico,       // null si el socio no tiene nivel asignado
        boolean passwordMustChange, // true → redirigir al formulario de cambio de contraseña
        boolean inhabilitado,       // true → socio inhabilitado (puede loguearse pero con restricciones)
        boolean esJefeMontana,
        @JsonInclude(JsonInclude.Include.NON_NULL) String refreshToken  // null para web; populated para mobile
) {
    /**
     * Factory para el flujo web: {@code refreshToken} es {@code null} (se omite del JSON).
     */
    public static LoginResponse of(
            String  accessToken,
            long    expiresIn,
            UUID    socioId,
            String  username,
            String  nombre,
            String  rol,
            String  nivelTecnico,
            boolean passwordMustChange,
            boolean inhabilitado,
            boolean esJefeMontana) {

        return new LoginResponse(accessToken, "Bearer", expiresIn,
                socioId, username, nombre, rol, nivelTecnico,
                passwordMustChange, inhabilitado, esJefeMontana,
                null);
    }

    /**
     * Devuelve una copia de este response con el {@code refreshToken} incluido
     * (para el flujo mobile).
     */
    public LoginResponse withRefreshToken(String token) {
        return new LoginResponse(accessToken, tokenType, expiresIn, socioId, username,
                nombre, rol, nivelTecnico, passwordMustChange, inhabilitado,
                esJefeMontana, token);
    }
}
