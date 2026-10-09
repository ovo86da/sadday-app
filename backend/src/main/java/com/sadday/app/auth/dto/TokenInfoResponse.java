package com.sadday.app.auth.dto;

/**
 * Respuesta al consultar el tipo de token de invitación antes de mostrar el formulario.
 *
 * @param requiresPersonalData {@code true} si el socio aún no existe en BD y debe
 *                             completar sus datos personales (flujo manual).
 *                             {@code false} si el socio ya existe (flujo CSV/legacy):
 *                             el formulario solo pide usuario y contraseña.
 */
public record TokenInfoResponse(
        boolean requiresPersonalData
) {}
