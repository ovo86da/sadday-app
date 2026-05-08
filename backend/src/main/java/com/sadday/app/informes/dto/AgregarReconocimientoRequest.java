package com.sadday.app.informes.dto;

import com.sadday.app.informes.entity.TipoReconocimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AgregarReconocimientoRequest(
        @NotNull UUID socioId,
        @NotNull TipoReconocimiento tipo,
        @NotBlank @Size(max = 2000, message = "El motivo no puede superar 2000 caracteres") String motivo
) {}
