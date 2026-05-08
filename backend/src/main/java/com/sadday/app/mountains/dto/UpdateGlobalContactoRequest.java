package com.sadday.app.mountains.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGlobalContactoRequest(
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 20) String telefono,
        @Size(max = 255) String correo,
        String notas
) {}
