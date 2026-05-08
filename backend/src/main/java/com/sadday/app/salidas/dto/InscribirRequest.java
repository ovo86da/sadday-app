package com.sadday.app.salidas.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Solicitud de inscripción a una salida. Si el socioId difiere del usuario autenticado, requiere rol Admin/Secretaria/Directivo. */
public record InscribirRequest(@NotNull UUID socioId) {}
