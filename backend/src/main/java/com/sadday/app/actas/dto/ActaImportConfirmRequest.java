package com.sadday.app.actas.dto;

import com.sadday.app.actas.entity.TipoActa;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Request enviada por el cliente para confirmar y persistir el acta importada.
 * Es el preview devuelto con las resoluciones manuales aplicadas.
 */
public record ActaImportConfirmRequest(
        @NotNull TipoActa tipoActa,
        Integer numeroReunion,
        @NotNull LocalDate fecha,
        @NotNull LocalTime hora,
        LocalTime horaFin,
        String lugar,
        /** ID del socio que presidió la reunión (null si no se pudo resolver). */
        UUID presidenteReunionId,
        /** Nombre raw del presidente, siempre presente. */
        String presidenteReunionNombreRaw,
        /** ID del socio que actuó de secretaria (null si no se pudo resolver). */
        UUID secretariaReunionId,
        /** Nombre raw de la secretaria, siempre presente. */
        String secretariaReunionNombreRaw,
        /** Lista de asistentes con sus resoluciones finales. */
        @NotNull List<AsistenteConfirmDto> asistentes,
        String actividadesRealizadasDesc,
        String actividadesPorRealizar,
        String acuerdos,
        String varios,
        String observaciones
) {
    /** Un asistente con su resolución final. */
    public record AsistenteConfirmDto(
            /** Nombre original del .md. */
            String nombreRaw,
            /** ID del socio resuelto; null si no se pudo o no se quiso resolver. */
            UUID socioId
    ) {}
}
