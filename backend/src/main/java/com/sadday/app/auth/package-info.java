/**
 * Módulo de autenticación y gestión de sesiones.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Login con email + contraseña (+ TOTP opcional)</li>
 *   <li>Registro de nuevos socios (flujo de verificación de email)</li>
 *   <li>Refresh y revocación de tokens</li>
 *   <li>Reset de contraseña (por usuario y por Admin)</li>
 *   <li>Gestión de TOTP 2FA (activar, desactivar, verificar)</li>
 * </ul>
 *
 * <p>Endpoints bajo: {@code /api/v1/auth/**} y {@code /api/v1/registro/**}
 */
package com.sadday.app.auth;
