package com.sadday.app.actas.entity;

/**
 * Tipo de acta de reunión.
 *
 * <ul>
 *   <li>{@code DIRECTIVA} — Reunión de la directiva. Visible para Admin, Secretaria y Directivos.</li>
 *   <li>{@code SOCIOS}    — Reunión de socios. Visible para todos los socios autenticados.</li>
 * </ul>
 */
public enum TipoActa {
    DIRECTIVA,
    SOCIOS
}
