/**
 * Módulo de gestión de salidas (excursiones) del club.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>CRUD de salidas (Admin/Directivo)</li>
 *   <li>Inscripción de socios (validación de nivel y cuotas)</li>
 *   <li>Aprobación de riesgo por Directivo y Jefe de Salida</li>
 *   <li>Confirmación de asistencia y gestión de estado de inscripción</li>
 *   <li>Asignación de dignidades por participante</li>
 *   <li>Transiciones de estado via {@code @Scheduled} (scheduler)</li>
 * </ul>
 *
 * <p>Endpoints bajo: {@code /api/v1/salidas/**}
 */
package com.sadday.app.salidas;
