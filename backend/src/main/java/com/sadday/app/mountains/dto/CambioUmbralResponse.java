package com.sadday.app.mountains.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un cambio de umbrales esperando aprobación, con lo que haría (FR-026).
 *
 * <p>Lleva {@link #escalas()} y no solo los valores propuestos porque quien
 * aprueba necesita ver <strong>qué cambia</strong>: aprobar un umbral de
 * seguridad sin ver qué dice es exactamente el descuido que FR-026 existe para
 * evitar. Cada escala trae el valor vigente, el propuesto, y si eso relaja o
 * endurece.
 */
public record CambioUmbralResponse(
        UUID id,
        String nivelSocioId,
        String nivelSocioNombre,
        String solicitadoPorNombre,
        LocalDateTime solicitadoEn,
        String estado,
        String resueltoPorNombre,
        LocalDateTime resueltoEn,
        String motivoRechazo,

        /** Las siete escalas, cambien o no. Las que no cambian también se ven. */
        List<CambioEscala> escalas,

        /** Si quien consulta puede resolverlo. Lo decide el servidor. */
        boolean puedoAprobar,
        /** Si quien consulta puede cancelarlo: solo quien lo pidió. */
        boolean puedoCancelar
) {
    /**
     * Una escala dentro del cambio.
     *
     * @param sentido {@code IGUAL}, {@code RELAJA} o {@code ENDURECE}. Se
     *                calcula en el servidor y no en el cliente porque depende
     *                del orden de cada escala, que el cliente no conoce.
     */
    public record CambioEscala(
            String escala,
            String valorActual,
            String valorPropuesto,
            String sentido
    ) {}
}
