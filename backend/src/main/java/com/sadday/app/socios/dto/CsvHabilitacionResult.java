package com.sadday.app.socios.dto;

import java.util.List;

public record CsvHabilitacionResult(
        int procesados,
        int habilitados,
        int deshabilitados,
        int sinCambio,
        List<FilaError> errores,
        String csvKey
) {
    public record FilaError(int fila, String nombre, String cedula, String motivo) {}
}
