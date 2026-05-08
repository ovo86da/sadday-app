/**
 * Módulo de gestión de actas de reunión.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>CRUD de actas (Admin/Secretaria/Directivo)</li>
 *   <li>Registro de asistentes</li>
 *   <li>Vinculación de informes de salida tratados en reunión</li>
 *   <li>Búsqueda full-text en contenido de actas (tsvector + GIN)</li>
 *   <li>Generación y almacenamiento de PDF firmado en S3</li>
 * </ul>
 *
 * <p>Endpoints bajo: {@code /api/v1/actas/**}
 */
package com.sadday.app.actas;
