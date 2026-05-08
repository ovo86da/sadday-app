/**
 * Módulo del portal de administración.
 *
 * <p>Acceso restringido a roles ADMIN y SECRETARIA.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Gestión de usuarios: desbloquear cuentas, resetear contraseñas</li>
 *   <li>Consulta del log de auditoría (solo lectura)</li>
 *   <li>Configuración del sistema (parámetros globales)</li>
 *   <li>Reportes y estadísticas del club</li>
 * </ul>
 *
 * <p>Endpoints bajo: {@code /api/v1/admin/**}
 * Requiere rol ADMIN o SECRETARIA (configurado en {@code SecurityConfig}).
 */
package com.sadday.app.admin;
