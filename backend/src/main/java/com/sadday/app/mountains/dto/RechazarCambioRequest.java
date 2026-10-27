package com.sadday.app.mountains.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo del rechazo, obligatorio.
 *
 * <p>Un rechazo sin motivo no le dice nada a quien pidió el cambio, y lo deja
 * sin saber si tiene que corregir algo o insistir. La base lo exige también
 * ({@code chk_motivo_rechazo} en V22), de modo que no depende de que el cliente
 * lo mande.
 */
public record RechazarCambioRequest(
        @NotBlank @Size(max = 1000) String motivo
) {}
