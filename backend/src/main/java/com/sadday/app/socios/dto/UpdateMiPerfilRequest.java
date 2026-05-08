package com.sadday.app.socios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Petición para que un socio actualice su propia información de contacto.
 *
 * <p>Campos editables por el propio socio: correo, teléfono, dirección, tipo de sangre
 * y ambos contactos de emergencia. Los campos de identidad (nombre, cédula, fechas)
 * solo pueden ser modificados por Admin/Secretaria.
 */
public record UpdateMiPerfilRequest(

        @Email(message = "El correo debe tener un formato válido")
        @Pattern(
                regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$",
                message = "El correo debe tener un formato válido (ej: nombre@dominio.com)"
        )
        @Size(max = 255)
        String correo,

        @Pattern(regexp = "^[0-9]{0,15}$", message = "El teléfono debe contener solo dígitos (máximo 15)")
        @Size(max = 15)
        String telefono,

        @Size(max = 500)
        String direccion,

        @Pattern(regexp = "^(A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-)?$", message = "Tipo de sangre inválido")
        String tipoSangre,

        @Size(max = 200)
        String emergencyContactName,
        @Pattern(regexp = "^[0-9]{0,15}$", message = "El teléfono de emergencia debe contener solo dígitos (máximo 15)")
        @Size(max = 15)
        String emergencyContactPhone,
        @Size(max = 500)
        String emergencyContactDireccion,

        @Size(max = 200)
        String emergencyContactName2,
        @Pattern(regexp = "^[0-9]{0,15}$", message = "El teléfono de emergencia 2 debe contener solo dígitos (máximo 15)")
        @Size(max = 15)
        String emergencyContactPhone2,
        @Size(max = 500)
        String emergencyContactDireccion2
) {}
