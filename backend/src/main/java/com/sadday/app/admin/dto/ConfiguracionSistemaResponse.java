package com.sadday.app.admin.dto;

import com.sadday.app.shared.entity.ConfiguracionSistema;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConfiguracionSistemaResponse(
        String clave,
        String valor,
        String descripcion,
        UUID   updatedById,
        LocalDateTime updatedAt
) {
    public static ConfiguracionSistemaResponse from(ConfiguracionSistema e) {
        return new ConfiguracionSistemaResponse(
                e.getClave(), e.getValor(), e.getDescripcion(),
                e.getUpdatedById(), e.getUpdatedAt());
    }
}
