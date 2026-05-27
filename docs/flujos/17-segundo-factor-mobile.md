# Flujo 17 — Segundo Factor de Autenticación (2FA) en Mobile

## Contexto

El sistema usa **TOTP** (Time-based One-Time Password, RFC 6238): cada 30 segundos la app autenticadora del usuario genera un código de 6 dígitos derivado de un secreto compartido. El servidor verifica ese código en el momento del login o al confirmar acciones sensibles.

---

## Flujo de activación (setup)

### Problema de UX en mobile

Mostrar solo un QR en la pantalla del teléfono no sirve: el usuario necesita **otra** cámara para escanearlo, pero la app autenticadora está en el **mismo** dispositivo. La solución es el deep link `otpauth://`.

### Secuencia

```
Usuario toca "Activar 2FA"
       │
       ▼
POST /v1/auth/mfa/setup
       │  Respuesta: { otpAuthUri, base32Secret }
       │
       ▼
_MfaSetupSheet (bottom sheet)
  ├─ Paso 1: botón "Abrir en app de autenticación"
  │          → launchUrl(otpauth://totp/Sadday:user?secret=…)
  │          → El SO abre Google Authenticator / Authy
  │          → La cuenta queda importada automáticamente
  │
  ├─ Paso 2: código manual (base32Secret) copiable
  │          → Fallback si ninguna app maneja otpauth://
  │          → El usuario lo pega manualmente en su autenticadora
  │
  ├─ QR (colapsable)
  │          → Solo útil si el usuario tiene tablet + teléfono separados
  │          → Se activa tocando "Ver QR (para activar desde otro dispositivo)"
  │
  └─ Paso 3: campo de 6 dígitos + botón "Confirmar y activar"
             → POST /v1/auth/mfa/confirm { code }
             → Si correcto: totpEnabled = true en BD
             → Si incorrecto: error inline, el usuario reintenta
```

### Origen del secreto y momento exacto de activación

**El secreto lo genera el backend**, no el cliente. `TotpService.generateSecret()` produce 20 bytes criptográficamente aleatorios en el servidor. El móvil solo recibe el `base32Secret` para mostrárselo al usuario — nunca lo calcula ni lo almacena.

El 2FA **no se activa en el `setup`**. El secreto se guarda en BD desde el paso 1, pero `totpEnabled` permanece en `false` hasta que el usuario confirma con un código válido. Estado por paso:

| Paso | `totpSecret` en BD | `totpEnabled` | ¿MFA activo en login? |
|---|---|---|---|
| Antes del setup | `null` | `false` | No |
| Después de `POST /mfa/setup` | cifrado (AES-256-GCM) | `false` | **No** |
| Código incorrecto en confirm | cifrado | `false` | **No** |
| Código correcto en confirm | cifrado | **`true`** | **Sí** |

El secreto se guarda en el paso 1 porque el servidor necesita tenerlo disponible para poder verificar el código TOTP que el usuario ingresa en el paso 2. Pasar el secreto de vuelta desde el cliente sería menos seguro. Este patrón (guardar pero no habilitar hasta confirmar) es el estándar en implementaciones TOTP.

Si el usuario abandona el proceso después del `setup` sin confirmar, el secreto queda en BD pero `totpEnabled = false` — el login sigue funcionando solo con contraseña. Al intentar el setup de nuevo, el secreto pendiente se sobreescribe con uno nuevo.

### Por qué el deep link es el flujo principal

| Método | Compatible con mismo dispositivo | Fricción |
|---|---|---|
| Escanear QR | No | Alta |
| Deep link `otpauth://` | Sí | Mínima |
| Código manual | Sí | Media |

El deep link abre directamente la app autenticadora con todos los datos pre-cargados. Si el dispositivo no tiene ninguna app que maneje `otpauth://`, se muestra un SnackBar indicando instalar Google Authenticator o Authy.

---

## Flujo de desactivación

```
Usuario toca "Desactivar 2FA"
       │
       ▼
_MfaDisableSheet (bottom sheet)
  └─ Campo de 6 dígitos del autenticador actual
       │
       ▼
DELETE /v1/auth/mfa { code }
       │
       ├─ Válido → totpEnabled = false, totpSecret = null
       └─ Inválido → error inline, el usuario reintenta
```

La desactivación requiere el código actual para evitar que alguien con acceso físico al teléfono desbloqueado desactive 2FA sin saber la contraseña.

---

## Flujo de login con 2FA activo

```
POST /v1/auth/login { username, password }
       │
       ├─ Sin 2FA → HTTP 200 → accessToken + refresh_token cookie
       │
       └─ Con 2FA → HTTP 202 → { challengeToken, expiresIn: 300 }
              │
              ▼
       Pantalla /mfa (login_screen.dart, step "mfa")
              │
              ▼
       POST /v1/auth/mfa/login { challengeToken, mfaCode }
              │
              ├─ Válido → HTTP 200 → accessToken + refresh_token cookie
              └─ Inválido → error (max 3 intentos por challengeToken)
```

El `challengeToken` expira en **5 minutos** y es de **un solo uso**. Después de 3 intentos fallidos se bloquea ese token y el usuario debe volver a ingresar con contraseña.

---

## Recuperación de emergencia

Solo disponible para roles `ADMIN` y `SECRETARIA`:

```
POST /v1/socios/{socioId}/emergency-reset
```

Efecto:
- Desactiva 2FA del socio objetivo
- Revoca **todas** sus sesiones activas
- Establece `passwordMustChange = true`
- El usuario recibe un enlace de restablecimiento de contraseña por email

Caso de uso: socio perdió su teléfono y no puede acceder a la app autenticadora.

---

## Detalles técnicos del backend

| Aspecto | Valor |
|---|---|
| Algoritmo | TOTP / HMAC-SHA1 (RFC 6238) |
| Período | 30 segundos |
| Dígitos | 6 |
| Tolerancia | ±1 ventana (±30 s de drift de reloj) |
| Secreto almacenado | AES-256-GCM, clave desde `TOTP_ENCRYPTION_KEY` |
| challengeToken | Hash SHA-256 en BD, raw al cliente |
| Expiración challenge | 300 segundos (5 minutos) |
| Intentos máximos | 3 por challenge token |

El secreto **nunca** viaja en el JWT ni en ninguna respuesta después del setup inicial.

---

## URI del deep link

Formato generado por el backend (`TotpService.buildOtpAuthUri`):

```
otpauth://totp/Sadday:{username}?secret={base32}&issuer=Sadday&digits=6&period=30
```

Compatible con: Google Authenticator, Authy, Microsoft Authenticator, 1Password, Bitwarden.

Para que Android pueda resolver este esquema, se declara en `android/app/src/main/AndroidManifest.xml`:

```xml
<queries>
  <intent>
    <action android:name="android.intent.action.VIEW"/>
    <data android:scheme="otpauth"/>
  </intent>
</queries>
```

---

## Archivos relevantes

| Archivo | Rol |
|---|---|
| `mobile/lib/features/perfil/presentation/screens/perfil_screen.dart` | UI: `_MfaSetupSheet`, `_MfaDisableSheet` |
| `mobile/lib/features/perfil/data/perfil_remote_data_source.dart` | Llamadas API de MFA |
| `mobile/lib/features/perfil/data/perfil_repository.dart` | Capa repositorio |
| `mobile/lib/features/auth/presentation/screens/login_screen.dart` | Pantalla de challenge TOTP en login |
| `backend/.../auth/service/TotpService.java` | Generación y verificación TOTP |
| `backend/.../auth/service/AuthService.java` | Setup, confirm, disable, challenge |
| `backend/.../auth/controller/AuthController.java` | Endpoints REST de MFA |
