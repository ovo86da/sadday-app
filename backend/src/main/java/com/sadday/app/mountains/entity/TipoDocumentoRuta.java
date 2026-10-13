package com.sadday.app.mountains.entity;

/**
 * Tipo de un documento adjunto a una ruta.
 *
 * <p>Se almacena como texto ({@code VARCHAR(20)}) y no como enum nativo de
 * PostgreSQL, igual que {@code rutas.estado}: añadir un tipo nuevo no exige
 * entonces tocar la base de datos.
 */
public enum TipoDocumentoRuta {

    /** Plantilla oficial en blanco que hay que presentar para acceder. Cambia con el tiempo. */
    PERMISO,

    /** PDF con el mapa de la ruta. */
    MAPA,

    /** Fichero {@code .gpx} con el track. La aplicación no lo interpreta: lo guarda y lo sirve. */
    TRACK_GPX,

    /** Cualquier otro adjunto. */
    OTRO
}
