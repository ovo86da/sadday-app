package com.sadday.app.mountains.dto;

import java.util.List;

public record MountainLookupsResponse(
        List<EscalaItem> escalasAlpina,
        List<DificultadRocaItem> dificultadesRoca,
        List<DificultadHieloItem> dificultadesHielo,
        List<CompromisoItem> compromisos,
        List<YosemiteItem> yosemiteClases,
        List<SaddayItem> saddayRiesgos,
        List<ClasifSocioItem> clasificacionesSocio,
        List<EquipoItem> equipos,
        List<DificultadSenderismoItem> dificultadesSenderismo
) {
    public record EscalaItem(String id, String grado, String nombre, String descripcion, Short rank) {}
    public record DificultadRocaItem(String id, String uiaa, String francesa, String descripcion, Short rank) {}
    public record DificultadHieloItem(String id, String grado, String descripcion, Short rank) {}
    public record CompromisoItem(String id, String tipo, String descripcion, Short rank) {}
    public record YosemiteItem(String id, String tipo, String descripcion, Short rank) {}
    public record SaddayItem(String id, Short valor, String escala, String descripcion, Short rank) {}
    public record ClasifSocioItem(String id, Short nivel, String nombre, String descripcion) {}
    public record EquipoItem(Integer id, String nombre, String descripcion) {}
    public record DificultadSenderismoItem(String id, String nombre, String descripcion, Short rank) {}
}
