package com.sadday.app.informes.dto;

import com.sadday.app.informes.entity.TipoReconocimiento;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReconocimientoResponse(
        Long id,
        UUID socioId,
        String socioNombre,
        String socioApellido,
        TipoReconocimiento tipo,
        String motivo,
        UUID registradoPorId,
        String registradoPorNombre,
        LocalDateTime createdAt
) {}
