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
        int    edad,
        int    antiguedadAnios,

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

        /** Presidenta del club. Solo una persona activa a la vez. */
        boolean esPresidenta,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
