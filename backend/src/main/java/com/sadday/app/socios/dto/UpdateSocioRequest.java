package com.sadday.app.socios.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Petición para actualizar datos personales de un socio.
 *
 * <p>Solo Admin y Secretaria pueden actualizar estos datos.
 * El rol se cambia con endpoint dedicado ({@code PATCH /{id}/rol}).
 */
public record UpdateSocioRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100)
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100)
        String apellido,

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
        String telefono,

        @Size(max = 500)
        String direccion,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser en el pasado")
        LocalDate fechaNacimiento,

        LocalDate fechaIngreso,

        LocalDate fechaSalida,

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
        String emergencyContactDireccion2,

        @NotNull(message = "El tipo de socio es obligatorio")
        Short tipoSocioId,

        /** Null para quitar el nivel técnico asignado. */
        String nivelTecnicoId,

        @NotNull(message = "El estado de habilitación es obligatorio")
        Short estadoHabilitacionId
) {}
