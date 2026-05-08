package com.sadday.app.socios.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InvitacionPendienteResponse(
        UUID id,
        String cedula,
        String correo,
        String telefono,
        String nombre,
        String apellido,
        boolean fromCsvImport,
        LocalDateTime creadoEn,
        LocalDateTime expiresAt,
        String estado
) {}
