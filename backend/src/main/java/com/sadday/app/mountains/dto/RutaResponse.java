package com.sadday.app.mountains.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RutaResponse(
        Integer id,
        String nombre,
        String tipoActividad,
        Integer mountainId,
        String mountainNombre,
        String lugarReferencia,
        String sectorZona,
        BigDecimal longitudKm,
        Integer desnivelM,
        Short duracionDias,
        Short duracionHoras,
        String peligrosNotas,
        Boolean requierePermisos,
        String documentacionUrl,
        String trackUrl,
        String nivelMinimoSocioId,
        String nivelMinimoSocioNombre,
        String estado,
        UUID revisadaPorId,
        LocalDateTime revisadaEn,
        String motivoRechazo,
        UUID propuestaPorId,
        List<ContactoRutaResponse> contactos,
        List<RutaDocumentoResponse> documentosPermiso,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        // Sub-tipo: solo uno será no nulo según tipoActividad
        AlpinismoDetail alpinismo,
        EscaladaDetail escalada,
        TrekkingDetail trekking,
        CiclismoDetail ciclismo,
        IntegralDetail integral
) {

    public record AlpinismoDetail(
            String escalaAlpinaIfasId,
            String escalaAlpinaIfasGrado,
            String dificultadRocaId,
            String dificultadRocaUiaa,
            String dificultadHieloId,
            String dificultadHieloGrado,
            String compromisoId,
            String compromisoTipo,
            String yosemiteId,
            String yosemiteTipo,
            String saddayNivelTecnicoId,
            String saddayNivelTecnicoEscala,
            String saddayNivelFisicoId,
            String saddayNivelFisicoEscala,
            Integer equipoMontanaId,
            String equipoMontanaNombre
    ) {}

    public record EscaladaDetail(
            String dificultadRocaId,
            String dificultadRocaUiaa,
            String tipoEscalada,
            Short numCintas,
            Integer alturaViaM,
            String tipoRoca
    ) {}

    public record TrekkingDetail(
            String dificultadId,
            String dificultadNombre,
            Boolean esCircular,
            Boolean fuentesAgua,
            String tipoTerreno
    ) {}

    public record CiclismoDetail(
            String tipoBicicleta,
            String dificultadTecnica,
            String superficiePredominante,
            BigDecimal ciclabilidadPct
    ) {}

    public record IntegralDetail(
            String dificultadMaxTipo,
            String dificultadMaximaDescripcion,
            String descripcionItinerario,
            List<CumbreItem> cumbres
    ) {
        public record CumbreItem(Short secuencia, Integer mountainId, String mountainNombre, Integer altitud) {}
    }
}
