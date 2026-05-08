# FR-004 — Vista de invitaciones pendientes de aceptación

**Fecha de solicitud:** 2026-04-27
**Fecha de implementación:** 2026-04-27
**Estado:** Implementado (ver [Registro de implementación](#registro-de-implementación))
**Prioridad:** Media
**Área:** Socios / Gestión de invitaciones

---

## Contexto y motivación

Cuando la Secretaria envía una invitación de pre-registro (flujo mínimo: cédula + correo + teléfono), el sistema crea un `EmailVerificationToken` en base de datos pero **no crea todavía el registro en la tabla `socios`**. El socio invitado solo aparece en la lista de socios una vez que completa el formulario de registro.

Esto deja a la Secretaria sin visibilidad sobre:
- A quiénes se les envió invitación
- Si el plazo de aceptación (72 h por defecto) expiró sin que el socio completara el registro
- Si necesita reenviar la invitación

El endpoint de reenvío existente (`POST /v1/socios/{id}/reenviar-invitacion`) usa `socioId`, que no existe para invitaciones de pre-registro (flujo nuevo), por lo que tampoco es posible reenviar desde la UI actual.

---

## Cambios solicitados

### 1. Nuevo endpoint: listar invitaciones pendientes

**Endpoint:** `GET /v1/socios/invitaciones`
**Autorización:** Admin, Secretaria

Devuelve todos los `EmailVerificationToken` donde:
- `socioId IS NULL` (flujo pre-registro — el socio aún no existe en BD)
- `used = false` (el socio no ha completado el registro)

El campo `estado` se calcula en el servicio:
- `PENDIENTE` → `expiresAt >= now()`
- `EXPIRADO` → `expiresAt < now()`

**Respuesta `InvitacionPendienteResponse`:**

| Campo           | Tipo            | Descripción                                          |
|-----------------|-----------------|------------------------------------------------------|
| `id`            | UUID            | ID del token (usado para reenviar)                  |
| `cedula`        | String          | Cédula proporcionada por la Secretaria              |
| `correo`        | String          | Correo al que se envió la invitación                |
| `telefono`      | String / null   | Teléfono opcional                                   |
| `nombre`        | String / null   | Solo si vino de CSV import                          |
| `apellido`      | String / null   | Solo si vino de CSV import                          |
| `fromCsvImport` | boolean         | Indica si el token fue generado desde importación   |
| `creadoEn`      | LocalDateTime   | Fecha y hora de envío de la invitación              |
| `expiresAt`     | LocalDateTime   | Fecha y hora de expiración del token                |
| `estado`        | String          | `"PENDIENTE"` o `"EXPIRADO"`                        |

---

### 2. Nuevo endpoint: reenviar invitación de pre-registro

**Endpoint:** `POST /v1/socios/invitaciones/{tokenId}/reenviar`
**Autorización:** Admin, Secretaria

Recibe el `id` del `EmailVerificationToken` (no un `socioId`). Comportamiento:
1. Busca el token por ID.
2. Verifica que sea pre-registro (`socioId IS NULL`) y que no esté usado (`used = false`).
3. Invalida todos los tokens anteriores para el mismo correo.
4. Crea un nuevo token con los mismos datos (cedula, correo, telefono, nombre, apellido, tipo, nivel).
5. Envía el email de invitación al correo del token.

---

### 3. Nueva pestaña en la UI de Socios

Se agrega un sistema de pestañas en `socios-page.tsx`:

- **Pestaña "Socios"** — lista existente de socios registrados (sin cambios).
- **Pestaña "Invitaciones"** — nueva tabla con invitaciones pendientes.

**Tabla de invitaciones muestra:**
- Cédula
- Correo
- Teléfono (si disponible)
- Nombre + Apellido (si vino de CSV)
- Enviado (fecha relativa)
- Expira (fecha + hora)
- Estado (`PENDIENTE` en verde / `EXPIRADO` en rojo)
- Botón **Reenviar** por fila (llama al endpoint nuevo)

---

## Archivos afectados

### Backend

| Archivo | Cambio |
|---------|--------|
| `EmailVerificationTokenRepository.java` | Nuevo método: `findBySocioIdIsNullAndUsedFalseOrderByCreatedAtDesc()` |
| `EmailVerificationService.java` | Nuevos métodos: `listarInvitacionesPendientes()`, `reenviarInvitacionPreRegistro(UUID tokenId)` |
| `socios/dto/InvitacionPendienteResponse.java` | Nuevo DTO record |
| `SocioController.java` | Dos nuevos endpoints: `GET /invitaciones` y `POST /invitaciones/{tokenId}/reenviar` |

### Frontend

| Archivo | Cambio |
|---------|--------|
| `types/socios.ts` | Nuevo tipo `InvitacionPendiente` |
| `hooks/use-socios.ts` | Nuevos hooks: `useInvitaciones()`, `useReenviarInvitacionPendiente()` |
| `pages/socios/invitaciones-tab.tsx` | Nuevo componente con la tabla de invitaciones |
| `pages/socios/socios-page.tsx` | Agrega tabs Socios / Invitaciones |

---

## Notas de diseño

- Los tokens expirados son visibles mientras no los elimine el scheduler (`deleteExpiredAndUsed`). Una vez eliminados por el scheduler, ya no aparecen. Esto es aceptable para el caso de uso: la Secretaria revisará la lista dentro del mismo ciclo de limpieza.
- No se agrega migración de BD porque la tabla `email_verification_tokens` ya tiene todos los campos necesarios.
- El reenvío NO verifica duplicados en `socios` (a diferencia de `sendMinimalInvitation`), porque el candidato ya tiene una invitación previa válida y todavía no se registró.

---

## Registro de implementación

- Backend: nuevo DTO, query en repository, dos métodos en `EmailVerificationService`, dos endpoints en `SocioController`.
- Frontend: nuevo tipo, dos hooks, nuevo componente `InvitacionesTab`, tabs en `SociosPage`.
