package com.sadday.app.salidas.dto;

import com.sadday.app.salidas.entity.EstadoSalida;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoSalidaRequest(@NotNull EstadoSalida estado) {}
