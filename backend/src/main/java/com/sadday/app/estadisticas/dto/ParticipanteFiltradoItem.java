package com.sadday.app.estadisticas.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Socio resultante de una búsqueda avanzada de participantes,
 * con sus estadísticas dentro del contexto del filtro aplicado.
 */
public record ParticipanteFiltradoItem(
        UUID        socioId,
        String      nombre,
        String      apellido,
        String      nivelTecnico,
        int         totalParticipaciones,
        int         vecesJefeSalida,
        List<String> dignidades,
        LocalDate   ultimaParticipacion
) {}
