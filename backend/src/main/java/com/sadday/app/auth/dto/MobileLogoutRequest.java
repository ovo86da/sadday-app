package com.sadday.app.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code POST /auth/logout} para clientes mobile ({@code X-Sadday-Client: mobile}).
 *
 * <p>El flujo web revoca el token leyéndolo de la cookie HttpOnly.
 * El flujo mobile lo envía explícitamente para que el backend lo revoque en BD.
 */
public record MobileLogoutRequest(

        @NotBlank(message = "El refresh token es obligatorio")
        String refreshToken

) {}
