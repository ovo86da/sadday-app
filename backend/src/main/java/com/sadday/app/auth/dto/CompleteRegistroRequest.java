package com.sadday.app.auth.dto;

import com.sadday.app.shared.validation.StrongPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de la petición para completar el registro inicial.
 *
 * <p>Cuando el token corresponde a un <b>pre-registro</b> (socioId == null en el token),
 * el socio debe incluir todos sus datos personales (nombre, apellido, fechaNacimiento son
 * obligatorios; el resto son opcionales).
 *
 * <p>Cuando el token corresponde a un socio ya existente (flujo legacy / reenvío),
 * solo son necesarios el token, username y password.
 */
public record CompleteRegistroRequest(

        @NotBlank(message = "El token es obligatorio")
        String token,

        // ── Datos personales (requeridos solo en pre-registro) ──────────────

        @Size(max = 100)
        String nombre,

        @Size(max = 100)
        String apellido,

        LocalDate fechaNacimiento,

        @Size(max = 500)
        String direccion,

        // ── Credenciales (siempre requeridas) ───────────────────────────────

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 4, max = 100, message = "El nombre de usuario debe tener entre 4 y 100 caracteres")
        @Pattern(
                regexp = "^[a-z0-9._-]+$",
                message = "El nombre de usuario solo puede contener letras minúsculas, números, puntos, guiones y guiones bajos"
        )
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 12, max = 200, message = "La contraseña debe tener al menos 12 caracteres")
        @StrongPassword
        String password,

        @NotBlank(message = "La confirmación de contraseña es obligatoria")
        String confirmPassword,

        // ── Wizard: datos opcionales del flujo de 6 pasos ──────────────────

        /** IDs de los documentos legales activos que el socio aceptó en el wizard. */
        List<UUID> documentIdsToAccept,

        /** Hasta 2 contactos de emergencia (orden asignado por posición en la lista). */
        @Valid
        List<WizardContactoDto> contactosEmergencia,

        /** Información médica opcional recopilada en el wizard. */
        @Valid
        WizardMedicalInfoDto informacionMedica
) {}
