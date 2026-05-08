package com.sadday.app.mountains.dto;

public record ContactoRutaResponse(
        Integer id,
        Integer contactoId,
        String nombre,
        String telefono,
        String correo,
        String tipoContacto,
        Boolean activo
) {}
