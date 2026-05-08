package com.sadday.app.mountains.dto;

import com.sadday.app.mountains.entity.TipoActividad;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Misma estructura que CreateRutaRequest — todos los campos son actualizables. */
public record UpdateRutaRequest(

        @NotBlank @Size(max = 200) String nombre,
        @NotNull TipoActividad tipoActividad,
        Integer mountainId,
        @Size(max = 200) String lugarReferencia,
        @Size(max = 200) String sectorZona,
        @DecimalMin("0.01") BigDecimal longitudKm,
        @Min(1) Integer desnivelM,
        @Min(1) Short duracionDias,
        @Min(1) Short duracionHoras,
        @Size(max = 2000) String peligrosNotas,
        @NotNull Boolean requierePermisos,
        @Size(max = 500) String documentacionUrl,
        @Size(max = 2000) String trackUrl,
        String nivelMinimoSocioId,

        String escalaAlpinaIfasId,
        String dificultadRocaId,
        String dificultadHieloId,
        String compromisoId,
        String yosemiteId,
        String saddayNivelTecnicoId,
        String saddayNivelFisicoId,
        Integer equipoMontanaId,

        String tipoEscalada,
        Short numCintas,
        Integer alturaViaM,
        String tipoRoca,

        String dificultadSenderismoId,
        Boolean esCircular,
        Boolean fuentesAgua,
        String tipoTerreno,

        String tipoBicicleta,
        String dificultadTecnicaCiclismo,
        String superficiePredominante,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal ciclabilidadPct

) {}
