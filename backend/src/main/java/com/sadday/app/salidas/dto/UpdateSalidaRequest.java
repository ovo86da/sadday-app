package com.sadday.app.salidas.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateSalidaRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalTime horaEncuentroClub,
        @NotNull LocalDate fechaFin,
        LocalTime horaEstimadaRegresoClub,
        Integer rutaId,
        String tipoActividad,
        String publicoObjetivoId,
        String formatoSalidaId,
        String nivelMinimoRequeridoId,
        @Min(1) Short capacidadMaxima
) {}
