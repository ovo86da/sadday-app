/**
 * Módulo de gestión de informes de salida.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Creación y edición de informe post-salida (Jefe de Salida)</li>
 *   <li>Validación/aprobación del informe (Directivo/Jefe de Montaña)</li>
 *   <li>Registro de reconocimientos: AMONESTADO / DESTACADO por participante</li>
 *   <li>Generación y almacenamiento de PDF en S3 (URL pre-firmada, 15 min)</li>
 * </ul>
 *
 * <p>Endpoints bajo: {@code /api/v1/informes/**}
 */
package com.sadday.app.informes;
