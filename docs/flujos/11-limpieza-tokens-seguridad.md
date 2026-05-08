# Flujo 11 — Limpieza automática de tokens de seguridad

## ¿Qué es este flujo?

El sistema genera tokens temporales para operaciones sensibles: confirmar un correo, restablecer contraseña, autenticación de dos factores, etc. Estos tokens tienen una vida corta y después de usarse o expirar ya no sirven para nada. Si se acumulan indefinidamente en la base de datos representan un riesgo de seguridad innecesario y desperdicio de espacio.

Un proceso automático los elimina periódicamente sin que nadie tenga que intervenir.

---

## ¿Qué tokens se limpian?

| Tabla | Propósito | Vida útil |
|---|---|---|
| `mfa_challenge_tokens` | Código de 6 dígitos para 2FA al hacer login | 5 minutos |
| `country_challenge_tokens` | Verificación de país inusual al hacer login | Corta |
| `refresh_tokens` | Mantiene la sesión activa sin re-loguear | Configurable |
| `password_reset_tokens` | Enlace de restablecimiento de contraseña | 1 hora |
| `email_verification_tokens` | Enlace de invitación/registro de nuevos socios | 72 horas |

---

## Cuándo corre la limpieza

La limpieza ocurre en dos momentos:

1. **Al arrancar el servidor** — inmediatamente cuando el backend inicia, antes de atender cualquier petición.
2. **Cada hora en punto** — a las 00:00, 01:00, 02:00, etc., de forma continua mientras el servidor esté corriendo.

Esto garantiza que en producción los tokens viejos no vivan más de 1 hora extra luego de expirar, y que en desarrollo (donde el servidor se reinicia seguido) la limpieza ocurra de todas formas al arrancar.

---

## Qué se elimina exactamente

Se elimina un token si cumple **cualquiera** de estas condiciones:

- **Expirado**: su fecha de vencimiento ya pasó.
- **Usado**: ya fue consumido (el socio completó el registro, restableció la contraseña, etc.).

Un token que no ha sido usado y todavía no ha expirado no se toca.

---

## Por qué es importante (seguridad)

Eliminar tokens vencidos o usados es una práctica estándar recomendada por OWASP y NIST SP 800-63B por dos razones:

- **Reduce la superficie de ataque**: si alguien obtuviera acceso a la base de datos, solo encontraría tokens que ya no funcionan.
- **Previene reutilización**: un token usado que permanece en BD podría (en implementaciones con bugs) ser reutilizado. Al eliminarlo se cierra esa posibilidad por completo.

---

## Dónde está implementado

| Componente | Archivo |
|---|---|
| Job principal | `scheduler/SchedulerService.java` → `limpiarTokensExpirados()` y `limpiarTokensAlArrancar()` |
| Query de borrado (email tokens) | `auth/repository/EmailVerificationTokenRepository.java` → `deleteExpiredAndUsed()` |
| Activación del scheduler | `SaddayAppApplication.java` → `@EnableScheduling` |

---

## Flujo de invitaciones pendientes en la UI

La pantalla **Socios → Invitaciones** muestra las invitaciones enviadas que aún no fueron aceptadas. Mientras el token existe en base de datos, aparece en la lista con estado `Pendiente` o `Expirado` (si pasaron las 72 horas pero el scheduler aún no corrió).

Una vez que el scheduler limpia el token expirado, desaparece automáticamente de la lista. Si se necesita eliminarlo antes de que el scheduler corra, la secretaria o el admin pueden hacerlo manualmente con el botón de eliminar en esa misma vista.
