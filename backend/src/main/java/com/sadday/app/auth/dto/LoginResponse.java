package com.sadday.app.auth.dto;

import java.util.UUID;

/**
 * Payload de respuesta de login/refresh enviado en el body JSON.
 *
 * <p>El refresh token NO se incluye aquí: viaja exclusivamente como cookie
 * {@code HttpOnly; Secure; SameSite=Strict} y el controlador lo extrae de
 * {@link LoginResult#rawRefreshToken()} antes de construir la respuesta.
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
        boolean esJefeMontana
) {
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
                socioId, username, nombre, rol, nivelTecnico, passwordMustChange, inhabilitado, esJefeMontana);
    }
}
