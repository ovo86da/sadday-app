package com.sadday.app.planificador.dto;

import com.sadday.app.mountains.entity.TipoActividad;

/**
 * Recomendaciones para planificar una salida a una ruta concreta.
 * Combina datos fijos de la ruta con estadísticas agregadas del historial de informes.
 */
public record RecomendacionResponse(

        // ── Datos de la ruta ─────────────────────────────────────────────────
        Integer       rutaId,
        String        rutaNombre,
        TipoActividad tipoActividad,
        String        mountainNombre,
        String        sectorZona,
        Boolean       requierePermisos,
        String        trackUrl,

        // ── Alpinismo ────────────────────────────────────────────────────────
        String  saddayNivelTecnicoEscala,
        String  saddayNivelFisicoEscala,
        String  escalaAlpinaIfasGrado,
        String  dificultadRocaUiaa,
        String  dificultadHieloGrado,
        Integer equipoMontanaId,
        String  equipoMontanaNombre,

        // ── Escalada ────────────────────────────────────────────────────────
        /** También poblado para Alpinismo (dificultad roca). */
        String  escaladaDificultadRocaUiaa,
        String  escaladaTipoEscalada,
        Integer escaladaNumCintas,
        Integer escaladaAlturaViaM,
        String  escaladaTipoRoca,

        // ── Trekking ────────────────────────────────────────────────────────
        String  trekkingDificultadNombre,
        Boolean trekkingEsCircular,
        Boolean trekkingFuentesAgua,
        String  trekkingTipoTerreno,

        // ── Ciclismo ────────────────────────────────────────────────────────
        String  ciclismoTipoBicicleta,
        String  ciclismoDificultadTecnica,
        String  ciclismoSuperficiePredominante,

        // ── Estadísticas históricas ──────────────────────────────────────────
        /** Total de salidas realizadas a esta ruta con informe registrado. */
        int     totalSalidasPrevias,
        /** true si totalSalidasPrevias < 3 (datos insuficientes para estadísticas fiables). */
        boolean datosInsuficientes,
        /** % de salidas en que seRealizo=true. null si sin datos. */
        Double  tasaExitoPct,
        /** Hora promedio de salida del club, formato "HH:mm". null si sin datos. */
        String  horaSalidaPromedioClub,
        /** % de salidas que alquilaron transporte externo. null si sin datos. */
        Double  pctAlquiloTransporte,
        /** Costo promedio de transporte (solo salidas que alquilaron). null si sin datos. */
        Double  costoPromedioTransporte,
        /** % de salidas que contrataron guía externo. null si sin datos. */
        Double  pctContratoGuia,
        /** Costo promedio de guía (solo salidas que contrataron). null si sin datos. */
        Double  costoPromedioGuia,
        /** Presupuesto total promedio (costoTransporte + costoGuia). null si sin datos. */
        Double  costoTotalPromedio
) {}
