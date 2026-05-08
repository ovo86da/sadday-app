package com.sadday.app.salidas.entity;

/**
 * Estado de inscripción de un socio a una salida. Mapeado al ENUM PostgreSQL {@code estado_inscripcion}.
 *
 * <ul>
 *   <li>{@link #PENDIENTE_APROBACION} — inscripción creada pero el socio no cumple el nivel mínimo.
 *       Requiere aprobación explícita de un Directivo Y del Jefe de Salida para pasar a INSCRITO.</li>
 *   <li>{@link #INSCRITO}   — inscripción activa y válida.</li>
 *   <li>{@link #CONFIRMADO} — presencia confirmada (post-salida).</li>
 *   <li>{@link #NO_FUE}     — el socio no asistió (marcado en el informe).</li>
 *   <li>{@link #CANCELADO}  — el socio canceló su inscripción.</li>
 *   <li>{@link #NEGADO}     — la inscripción fue negada por Directivo o Jefe de Salida.</li>
 * </ul>
 */
public enum EstadoInscripcion {
    PENDIENTE_APROBACION,
    INSCRITO,
    CONFIRMADO,
    NO_FUE,
    CANCELADO,
    NEGADO
}
