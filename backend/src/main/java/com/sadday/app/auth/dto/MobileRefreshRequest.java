package com.sadday.app.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code POST /auth/refresh} para clientes mobile ({@code X-Sadday-Client: mobile}).
 *
 * <p>El flujo web usa la cookie HttpOnly automáticamente; el flujo mobile envía el
 * refresh token explícitamente en el body JSON para poder almacenarlo en el
 * Keychain (iOS) / Keystore (Android) del dispositivo.
 */
public record MobileRefreshRequest(

        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken

) {}
