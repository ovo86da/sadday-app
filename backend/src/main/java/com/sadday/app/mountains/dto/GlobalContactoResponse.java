package com.sadday.app.mountains.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GlobalContactoResponse(
        Integer id,
        String nombre,
        String telefono,
        String correo,
        String notas,
        List<String> tiposContacto,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
