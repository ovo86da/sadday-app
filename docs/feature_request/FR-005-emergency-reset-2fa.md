# FR-005 — Reset de emergencia por pérdida de teléfono (2FA)

## Contexto

Cuando un socio pierde su teléfono y tiene 2FA activo, queda bloqueado sin acceso a su cuenta.
La Secretaria o Admin debe poder ejecutar un reset de emergencia sin requerir intervención técnica.

## Comportamiento esperado

### Quién puede ejecutarlo
- Roles: **ADMIN** y **SECRETARIA**
- Solo visible en el perfil de socios que **tienen 2FA activo** (`totpEnabled = true`)
- El admin/secretaria no necesita ingresar su propio 2FA para esto (ya está autenticado)

### Qué hace (las 3 acciones son atómicas en una sola llamada)
1. **Desactiva el 2FA**: `totp_enabled = false`, `totp_secret = null`
2. **Revoca todas las sesiones activas**: invalida todos los refresh tokens del socio
3. **Fuerza reset de contraseña**: marca `password_must_change = true` y envía email con link de restablecimiento

### UX
- Botón "Reset de emergencia" en la pestaña "Cuenta" del `SocioDetailDialog`, visible solo si `cuentaAuth.totpEnabled === true` y el usuario es ADMIN o SECRETARIA
- Al hacer clic abre un `AlertDialog` estándar de confirmación con mensaje explícito de las consecuencias
- Tras confirmar: toast de éxito indicando que se envió el email al correo del socio

---

## Cambios backend

### `AuthService` — nuevo método `emergencyReset(UUID targetSocioId, UUID adminSocioId)`
- `@Transactional`
- Pasos:
  1. Carga `UsuarioAuth` del target
  2. Si no tiene 2FA activo, lanza `VALIDATION_ERROR` (guard de seguridad)
  3. Desactiva 2FA: `totpEnabled = false`, `totpSecret = null`
  4. Revoca sesiones: `refreshTokenRepository.revokeAllBySocioId(targetSocioId, now)`
  5. Marca `passwordMustChange = true`
  6. Guarda `UsuarioAuth`
  7. Obtiene correo del socio via `SocioRepository` / query directa
  8. Llama `passwordResetService.initiate(correo)` para enviar el enlace
  9. Registra en audit log: acción `EMERGENCY_RESET_2FA`, realizadoPor = adminSocioId

### `SocioController` — nuevo endpoint
```
POST /v1/socios/{socioId}/emergency-reset
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
Response: 200 ApiResponse<Void> con mensaje confirmatorio
```

---

## Cambios frontend

### `socio-detail-dialog.tsx`
- Nuevo `useMutation` para `POST /v1/socios/{socioId}/emergency-reset`
- Nuevo estado `emergencyResetOpen: boolean` para el `AlertDialog`
- Botón "Reset de emergencia" con ícono de escudo-advertencia:
  - Visible solo si `isAdminOrSecretaria && cuentaAuth.totpEnabled`
  - Estilo destructive/warning
- `AlertDialog` con texto:
  > ¿Confirmas el reset de emergencia para **{nombre}**?
  > Se desactivará el 2FA, se cerrarán todas sus sesiones activas y se enviará un correo a **{correo}** con un link para crear una nueva contraseña.
- Tras éxito: toast + `refetch` de la cuenta del socio (para que el badge 2FA se actualice a "No configurada")

---

## Seguridad
- El endpoint está protegido por rol — un socio regular nunca puede ejecutarlo sobre otro
- El audit log registra quién hizo el reset y sobre quién, con timestamp
- `password_must_change = true` asegura que aunque el link de reset expire, el socio no pueda entrar con la contraseña anterior sin cambiarla
- No se expone el secret TOTP en ningún paso
