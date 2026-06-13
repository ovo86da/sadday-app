package com.sadday.app.auth.dto;

/**
 * Respuesta al consultar el tipo de token de invitación antes de mostrar el formulario.
 *
 * @param requiresPersonalData {@code true} si el socio aún no existe en BD y debe
 *                             completar sus datos personales.
 * @param fromCsvImport        {@code true} si el token viene de una importación CSV.
 */
public record TokenInfoResponse(
        boolean requiresPersonalData,
        boolean fromCsvImport
) {}
