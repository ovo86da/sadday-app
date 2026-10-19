package com.sadday.app.mountains.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RutaEnlaceRequest(

        @NotBlank(message = "La etiqueta es obligatoria")
        @Size(max = 100, message = "La etiqueta no puede exceder 100 caracteres")
        String etiqueta,

        @NotBlank(message = "La URL es obligatoria")
        @Size(max = 2000, message = "La URL no puede exceder 2000 caracteres")
        // Solo http y https: evita javascript:, data: y file:, que un enlace
        // pintado en la ficha de la ruta convertiría en un vector de ataque.
        @Pattern(regexp = "^https?://.+", message = "La URL debe empezar por http:// o https://")
        String url,

        Short orden
) {}
