package com.sadday.app.legal.dto;

import java.util.List;
import java.util.UUID;

public record PendingAcceptancesResponse(
        UUID documentId,
        String documentCode,
        String documentTitle,
        Integer activeVersion,
        List<SocioPendienteDto> sociosPendientes
) {
    public record SocioPendienteDto(UUID id, String nombre, String apellido, String cedula, String correo) {}
}
