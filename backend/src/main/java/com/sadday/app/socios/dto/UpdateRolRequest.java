package com.sadday.app.socios.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Petición para cambiar el rol del sistema de un socio.
 * Solo Admin y Secretaria pueden cambiar roles.
 */
public record UpdateRolRequest(
        @NotNull(message = "El ID del rol es obligatorio")
        Short rolSistemaId
) {}
