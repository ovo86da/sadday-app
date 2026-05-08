package com.sadday.app.actas.dto;

import com.sadday.app.actas.entity.TipoActa;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Preview del acta parseada desde un .md.
 * Se devuelve al cliente para revisión antes de confirmar la importación.
 * Los campos de personas pueden tener {@code resuelto=false}; en ese caso
 * la secretaria debe elegir un candidato o dejar en null.
 */
public record ActaImportPreviewResponse(
        TipoActa tipoActa,
        Integer numeroReunion,
        LocalDate fecha,
        LocalTime hora,
        LocalTime horaFin,
        String lugar,
        PersonaImportDto presidenteReunion,
        PersonaImportDto secretariaReunion,
        List<AsistenteImportDto> asistentes,
        String actividadesRealizadasDesc,
        String actividadesPorRealizar,
        String acuerdos,
        String varios,
        String observaciones,
        /** true si todos los nombres se resolvieron automáticamente; false si requiere revisión. */
        boolean listaParaConfirmar
) {}
