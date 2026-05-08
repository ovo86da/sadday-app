package com.sadday.app.actas.dto;

import java.util.UUID;

/** Candidato sugerido cuando un nombre del .md no puede resolverse de forma única. */
public record CandidatoSocioDto(UUID socioId, String nombre, String apellido) {}
