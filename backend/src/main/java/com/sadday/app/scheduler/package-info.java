/**
 * Módulo de tareas programadas ({@code @Scheduled}).
 *
 * <p>Jobs incluidos:
 * <ul>
 *   <li>{@code TipoSocioScheduler} — Recalcula tipo JUVENIL/ADULTO
 *       diariamente basado en {@code fecha_nacimiento}</li>
 *   <li>{@code SalidaEstadoScheduler} — Transiciona salidas de
 *       PLANIFICADA → EN_PROGRESO → REALIZADA según fechas</li>
 *   <li>{@code TokenCleanupScheduler} — Elimina refresh tokens y
 *       tokens de verificación expirados</li>
 * </ul>
 *
 * <p>Todos los jobs se registran en la tabla {@code auditoria} con usuario "SYSTEM".
 */
package com.sadday.app.scheduler;
