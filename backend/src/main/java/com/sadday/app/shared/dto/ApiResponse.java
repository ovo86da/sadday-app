package com.sadday.app.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

/**
 * Wrapper estándar para todas las respuestas de la API.
 *
 * <p>Estructura:
 * <pre>
 * {
 *   "success": true,
 *   "message": "Operación exitosa",
 *   "data": { ... },         // null si no hay datos
 *   "timestamp": "2026-03-06T12:00:00Z"
 * }
 * </pre>
 *
 * @param <T> tipo del campo data
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    /** Respuesta exitosa con datos. */
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /** Respuesta exitosa con datos y mensaje. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /** Respuesta exitosa sin datos (ej. creación, actualización). */
    public static <T> ApiResponse<T> ok(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /** Respuesta de error. */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /** Respuesta de error con datos adicionales (ej. errores de validación por campo). */
    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
}
