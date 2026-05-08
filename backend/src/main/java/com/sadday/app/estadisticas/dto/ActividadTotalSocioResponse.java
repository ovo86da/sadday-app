package com.sadday.app.estadisticas.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Actividad combinada de un socio: reuniones asistidas + salidas participadas. */
public record ActividadTotalSocioResponse(
        UUID   socioId,
        String nombre,
        String apellido,
        int    totalReunionesAsistidas,
        int    totalSalidasParticipadas,
        int    totalCumbresLogradas,
        List<ReunionAsistidaItem> reunionesAsistidas
) {
    public record ReunionAsistidaItem(
            UUID      actaId,
            LocalDate fecha,
            Integer   numeroReunion,
            String    tipoActa,
            String    presidenteNombre
    ) {}
}
