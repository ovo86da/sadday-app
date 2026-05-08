package com.sadday.app.salidas.dto;

import java.util.List;

public record SalidaLookupsResponse(
        List<PublicoObjetivoItem> publicosObjetivo,
        List<FormatoSalidaItem> formatosSalida,
        List<DignidadItem> dignidades,
        List<String> estadosSalida,
        List<String> estadosInscripcion
) {
    public record PublicoObjetivoItem(String id, String nombre) {}
    public record FormatoSalidaItem(String id, String nombre) {}
    public record DignidadItem(Integer id, String nombre, String descripcion) {}
}
