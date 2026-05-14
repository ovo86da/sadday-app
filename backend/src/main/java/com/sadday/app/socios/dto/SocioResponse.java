package com.sadday.app.socios.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Respuesta completa con todos los datos de un socio.
 * Usada en GET /socios/{id} y como respuesta tras crear o actualizar.
 */
public record SocioResponse(
        UUID   id,
        String nombre,
        String apellido,
        String cedula,
        String correo,
        String telefono,
        String direccion,
        LocalDate fechaNacimiento,
        LocalDate fechaIngreso,
        LocalDate fechaSalida,
        String tipoSangre,
        int    edad,
        int    antiguedadAnios,

        // Contacto de emergencia 1
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactDireccion,

        // Contacto de emergencia 2
        String emergencyContactName2,
        String emergencyContactPhone2,
        String emergencyContactDireccion2,

        // Lookup values (IDs + nombres)
        Short  estadoHabilitacionId,
        String estadoHabilitacion,

        Short  tipoSocioId,
        String tipoSocio,

        String nivelTecnicoId,
        String nivelTecnico,

        Short  rolSistemaId,
        String rolSistema,

        Short  estadoAccesoId,
        String estadoAcceso,

        /** Solo aplica a DIRECTIVO. Puede aprobar/negar inscripciones con nivel insuficiente. */
        boolean esJefeMontana,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
