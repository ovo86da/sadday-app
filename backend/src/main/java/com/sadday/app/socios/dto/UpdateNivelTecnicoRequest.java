package com.sadday.app.socios.dto;

import jakarta.validation.constraints.Size;

/**
 * Petición para actualizar el nivel técnico de un socio.
 * nivelTecnicoId puede ser null para quitar el nivel asignado.
 * Admin, Secretaria y Directivo pueden usar este endpoint.
 */
public record UpdateNivelTecnicoRequest(
        @Size(max = 50)
        String nivelTecnicoId
) {}
