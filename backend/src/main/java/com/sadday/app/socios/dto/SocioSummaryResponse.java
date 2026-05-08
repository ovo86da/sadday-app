package com.sadday.app.socios.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta resumida de un socio para listados paginados.
 */
public record SocioSummaryResponse(
        UUID   id,
        String nombre,
        String apellido,
        String cedula,
        String correo,
        String telefono,
        LocalDate fechaIngreso,
        int    edad,
        String estadoHabilitacion,
        String tipoSocio,
        String nivelTecnico,
        String rolSistema,
        String estadoAcceso,
        /** true si el socio ya completó el registro y tiene credenciales activas. */
        boolean tieneCuenta,
        /** Solo aplica a DIRECTIVO. Puede aprobar/negar inscripciones con nivel insuficiente. */
        boolean esJefeMontana
) {}
