package com.sadday.app.mountains.dto;

import java.util.List;

/**
 * Qué dice la matriz de acceso sobre un socio y una ruta concretos.
 *
 * <p>No devuelve un sí/no a secas porque un sí/no no sirve para decidir. Quien
 * aprueba necesita saber <b>qué</b> escala no da y por cuánto, y si hay algo que
 * el sistema no ha podido comprobar.
 *
 * @param apto      true solo si no hay nada que exceda y nada sin comprobar.
 * @param hallazgos una entrada por escala problemática. Vacío si todo da.
 */
public record EvaluacionNivelResponse(
        boolean apto,
        List<Hallazgo> hallazgos
) {

    /** Por qué una escala concreta impide (o no permite confirmar) la inscripción. */
    public record Hallazgo(
            /** Nombre de la escala: «Hielo», «Senderismo»… o la razón si no hay escala. */
            String escala,
            /** Lo que exige la ruta. Null si no se sabe. */
            String exigeRuta,
            /** El techo que permite el nivel del socio. Null si no está fijado. */
            String permiteNivel,
            /** {@code EXCEDE} o {@code SIN_COMPROBAR}. */
            String tipo,
            /** Frase lista para mostrar, construida en el servidor. */
            String detalle
    ) {}

    public static final String EXCEDE        = "EXCEDE";
    public static final String SIN_COMPROBAR = "SIN_COMPROBAR";

    /** True si algo excede el nivel: la ruta es más difícil de lo que ese nivel permite. */
    public boolean algoExcede() {
        return hallazgos.stream().anyMatch(h -> EXCEDE.equals(h.tipo()));
    }

    /** True si algo no se pudo comprobar. Cuenta como freno, nunca como visto bueno. */
    public boolean algoSinComprobar() {
        return hallazgos.stream().anyMatch(h -> SIN_COMPROBAR.equals(h.tipo()));
    }
}
