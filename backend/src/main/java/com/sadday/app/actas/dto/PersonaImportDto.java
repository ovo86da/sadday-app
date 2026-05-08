package com.sadday.app.actas.dto;

import java.util.List;
import java.util.UUID;

/**
 * Representa una persona con rol en el acta (presidente o secretaria de reunión)
 * durante el flujo de importación.
 */
public record PersonaImportDto(
        String nombreRaw,
        boolean resuelto,
        UUID socioId,
        String socioNombre,
        String socioApellido,
        List<CandidatoSocioDto> candidatos
) {}
