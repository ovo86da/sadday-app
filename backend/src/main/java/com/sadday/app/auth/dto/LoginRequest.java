package com.sadday.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del primer paso de login: solo username y contraseña.
 *
 * <p>Si el usuario tiene 2FA activo, el servidor responde 202 con un {@code challengeToken}.
 * El cliente usa ese token en POST /v1/auth/mfa/login para completar la autenticación.
 */
public record LoginRequest(

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(max = 100, message = "Nombre de usuario demasiado largo")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 200, message = "Contraseña demasiado larga")
        String password
) {}
