package com.sadday.app.estadisticas.dto;

import com.sadday.app.salidas.entity.EstadoInscripcion;
import com.sadday.app.salidas.entity.EstadoSalida;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record SalidaHistorialItem(
        Long participanteId,
        UUID salidaId,
        String salidaNombre,
        LocalDate fecha,
        String mountainNombre,
        Integer mountainAltitud,
        String rutaNombre,
        EstadoInscripcion estadoInscripcion,
        EstadoSalida estadoSalida,
        boolean esJefeSalida,
        /** Null si la salida aún no tiene informe generado. */
        Boolean seRealizo,
        /** Hora de encuentro en el club, para pre-llenar el informe. */
        LocalTime horaEncuentroClub,
        List<String> dignidades,
        /** true si el Directivo/Admin ya aprobó el riesgo de inscripción (solo relevante en PENDIENTE_APROBACION). */
        boolean directivoAprobado,
        /** true si el Jefe de Salida ya aprobó el riesgo de inscripción (solo relevante en PENDIENTE_APROBACION). */
        boolean jefeAprobado
) {}
