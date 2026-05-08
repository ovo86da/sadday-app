package com.sadday.app.actas.dto;

import java.util.List;
import java.util.UUID;

/**
 * Representa un asistente durante el flujo de importación desde .md.
 *
 * <ul>
 *   <li>{@code resuelto=true}: se encontró un único socio → {@code socioId} está presente.</li>
 *   <li>{@code resuelto=false}: 0 o varios candidatos → la secretaria debe seleccionar
 *       {@code socioId} manualmente, o dejarlo null para guardar solo {@code nombreRaw}.</li>
 * </ul>
 */
public record AsistenteImportDto(
        String nombreRaw,
        boolean resuelto,
        UUID socioId,
        String socioNombre,
        String socioApellido,
        List<CandidatoSocioDto> candidatos
) {}
