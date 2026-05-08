package com.sadday.app.auth.dto;

/**
 * Respuesta al consultar el tipo de token de invitación antes de mostrar el formulario.
 *
 * @param requiresPersonalData {@code true} si el socio aún no existe en BD y debe
 *                             completar sus datos personales.
 * @param fromCsvImport        {@code true} si el token viene de una importación CSV.
 *                             En este caso nombre/apellido ya están pre-cargados y el
 *                             formulario solo pide los datos faltantes (fecha nacimiento,
 *                             dirección, contacto de emergencia).
 * @param prefilledNombre      Nombre pre-cargado (solo cuando fromCsvImport=true).
 * @param prefilledApellido    Apellido pre-cargado (solo cuando fromCsvImport=true).
 * @param prefilledTipoSocio   Tipo de socio pre-cargado (solo cuando fromCsvImport=true).
 * @param prefilledNivelTecnico Nivel técnico pre-cargado (solo cuando fromCsvImport=true).
 */
public record TokenInfoResponse(
        boolean requiresPersonalData,
        boolean fromCsvImport,
        String prefilledNombre,
        String prefilledApellido,
        String prefilledTipoSocio,
        String prefilledNivelTecnico
) {}
