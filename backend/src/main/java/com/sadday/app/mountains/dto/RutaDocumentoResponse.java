package com.sadday.app.mountains.dto;

import com.sadday.app.mountains.entity.TipoDocumentoRuta;

import java.time.LocalDateTime;
import java.util.UUID;

public record RutaDocumentoResponse(
        UUID   id,
        TipoDocumentoRuta tipo,
        String filename,
        String contentType,
        Long   sizeBytes,
        UUID   subidoPorId,
        String subidoPorNombre,
        LocalDateTime createdAt,

        /** Cuántos socios avalan este fichero. */
        long   confirmaciones,

        /** Si quien consulta ya lo confirmó; sirve para pintar el botón. */
        boolean confirmadoPorMi,

        /** Si quien consulta puede borrarlo: lo subió él, o es de la directiva. */
        boolean puedoEliminar
) {}
