package com.sadday.app.socios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos mínimos que la Secretaria proporciona para iniciar el registro de un nuevo socio.
 * El socio completa sus datos personales y credenciales al activar su cuenta con el link recibido.
 */
public record CreateSocioRequest(

        @NotBlank(message = "La cédula es obligatoria")
        @Pattern(regexp = "^[0-9]{10}$", message = "La cédula debe tener exactamente 10 dígitos numéricos")
        String cedula,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Pattern(
                regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$",
                message = "El correo debe tener un formato válido (ej: nombre@dominio.com)"
        )
        @Size(max = 255)
        String correo,

        @Pattern(regexp = "^[0-9]{0,15}$", message = "El teléfono debe contener solo dígitos (máximo 15)")
        @Size(max = 15)
        String telefono
) {}
