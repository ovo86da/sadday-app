package com.sadday.app.socios.dto;

import java.util.List;

public record CsvSocioImportPreviewResponse(
        int totalFilas,
        List<FilaValida> validas,
        List<FilaError> errores
) {
    public record FilaValida(
            int    fila,
            String cedula,
            String nombre,
            String apellido,
            String correo,
            String telefono,
            String tipoSocio,
            String nivelTecnico
    ) {}

    public record FilaError(
            int    fila,
            String cedula,
            String correo,
            String motivo
    ) {}
}
