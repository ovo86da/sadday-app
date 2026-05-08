package com.sadday.app.mountains.dto;

public record ContactoResponse(
        Integer id,
        String nombre,
        String telefono,
        String correo,
        String direccion,
        Integer tipoContactoId,
        String tipoContactoNombre
) {}
