package com.sadday.app.salidas.dto;

import com.sadday.app.salidas.entity.EstadoInscripcion;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoInscripcionRequest(@NotNull EstadoInscripcion estadoInscripcion) {}
