package com.sadday.app.mountains.dto;

import com.sadday.app.mountains.entity.TipoActividad;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Request para crear o proponer una ruta.
 *
 * <p>Los campos comunes aplican a todos los tipos de actividad.
 * Los campos específicos (alpinismo, escalada, trekking, ciclismo) se validan
 * en el servicio según el valor de {@code tipoActividad}.
 *
 * <ul>
 *   <li>ALPINISMO  → requiere: escalaAlpinaIfasId, dificultadRocaId, dificultadHieloId,
 *                               compromisoId, yosemiteId, saddayNivelTecnicoId, saddayNivelFisicoId</li>
 *   <li>ESCALADA   → requiere: dificultadRocaId, tipoEscalada</li>
 *   <li>TREKKING   → requiere: dificultadSenderismoId</li>
 *   <li>CICLISMO   → requiere: tipoBicicleta</li>
 * </ul>
 */
public record CreateRutaRequest(

        // ── Campos comunes ──────────────────────────────────────────────────
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

        // ── Alpinismo ───────────────────────────────────────────────────────
        String escalaAlpinaIfasId,
        String dificultadRocaId,        // también en Escalada
        String dificultadHieloId,
        String compromisoId,
        String yosemiteId,
        String saddayNivelTecnicoId,
        String saddayNivelFisicoId,
        Integer equipoMontanaId,

        // ── Escalada ────────────────────────────────────────────────────────
        /** DEPORTIVA | TRADICIONAL | MIXTA | BOULDER */
        String tipoEscalada,
        Short numCintas,
        Integer alturaViaM,
        String tipoRoca,

        // ── Trekking ────────────────────────────────────────────────────────
        String dificultadSenderismoId,
        Boolean esCircular,
        Boolean fuentesAgua,
        String tipoTerreno,

        // ── Ciclismo ────────────────────────────────────────────────────────
        /** RIGIDA | DOBLE_SUSPENSION | ENDURO | GRAVEL | RUTA */
        String tipoBicicleta,
        /** S0 | S1 | S2 | S3 | S4 */
        String dificultadTecnicaCiclismo,
        String superficiePredominante,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal ciclabilidadPct

) {}
