package com.sadday.app.actas.dto;

import java.util.UUID;

public record AsistenteResponse(
        Long id,
        UUID socioId,
        String socioNombre,
        String socioApellido,
        /** Nombre original del .md; null si el asistente fue agregado manualmente con socioId. */
        String nombreRaw
) {}
