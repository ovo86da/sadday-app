package com.sadday.app.socios.dto;

import java.util.UUID;

/**
 * Respuesta mínima de socio (solo identidad) para búsquedas accesibles a todos
 * los usuarios autenticados (ej. Jefe de Salida añadiendo participantes).
 * No expone datos personales sensibles.
 */
public record SocioMinimalResponse(
        UUID   id,
        String nombre,
        String apellido
) {}
