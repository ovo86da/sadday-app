package com.sadday.app.mountains.dto;

public record RutaEnlaceResponse(
        Long   id,
        String etiqueta,
        String url,
        Short  orden
) {}
