# Seguridad Mobile — Sadday App (Flutter)

**Última actualización:** 2026-05-28
**Audiencia:** Desarrolladores mobile y revisores de seguridad.

Este documento describe los mecanismos de seguridad implementados en la app Flutter, las decisiones de diseño detrás de cada uno, y los gaps conocidos. Toda afirmación está verificada contra el código fuente — no hay suposiciones.

---

## Índice

1. [Stack y entornos](#1-stack-y-entornos)
2. [Almacenamiento de tokens](#2-almacenamiento-de-tokens)
3. [Cliente HTTP e interceptores](#3-cliente-http-e-interceptores)
4. [Autenticación biométrica e inactividad](#4-autenticación-biométrica-e-inactividad)
5. [Protección de pantalla y captura](#5-protección-de-pantalla-y-captura)
6. [Deep links](#6-deep-links)
7. [Logging en producción](#7-logging-en-producción)
8. [Ciclo de vida de sesión (logout)](#8-ciclo-de-vida-de-sesión-logout)
9. [Configuración de red nativa](#9-configuración-de-red-nativa)
10. [Gaps conocidos](#10-gaps-conocidos)
11. [Correspondencia OWASP MASVS](#11-correspondencia-owasp-masvs)

---

## 1. Stack y entornos

### Paquetes relevantes para seguridad

| Paquete | Versión | Uso |
|---------|---------|-----|
| `flutter_secure_storage` | `^10.2.0` | Keychain (iOS) / Keystore (Android) para el refresh token |
| `local_auth` | `^3.0.1` | Autenticación biométrica (Face ID, huella) |
| `dio` | `^5.9.2` | Cliente HTTP con interceptores de auth y error |
| `synchronized` | `^3.4.0+1` | Mutex para serializar refreshes concurrentes |
| `flutter_jailbreak_detection` | `^1.10.0` | Importado pero **no integrado** — ver [§10](#10-gaps-conocidos) |
| `flutter_dotenv` | `^6.0.1` | Carga de variables de entorno por flavor |
| `go_router` | `^17.2.3` | Routing con guards de rol y manejo de deep links |

### Separación de entornos

Tres flavors con entry points y archivos `.env` distintos:

| Flavor | Entry point | API base URL |
|--------|------------|--------------|
| `prod` | `main_prod.dart` | `https://api.el-sadday.com/api` |
| `staging` | `main_staging.dart` | `https://api-staging.el-sadday.com/api` |
| `dev` | `main_dev.dart` | `http://10.0.2.2:8080/api` (emulador Android) |

`AppConfig.isProd` controla logging, retry de providers y otras puertas de comportamiento. La URL de la API nunca está hardcodeada en el código fuente — siempre viene del `.env` correspondiente.

---

## 2. Almacenamiento de tokens

### Refresh token — Keychain / Keystore

Implementado en `lib/core/storage/secure_storage_service.dart`.

```
iOS  → flutter_secure_storage → Keychain
       IOSOptions(accessibility: KeychainAccessibility.unlocked)
       El ítem solo es accesible con el dispositivo desbloqueado.
       Respaldado por el Secure Enclave cuando está disponible.

Android → flutter_secure_storage v10+ → EncryptedSharedPreferences
          Cifrado automático vía Android Keystore System.
          No usa la depreciada Jetpack Security directamente.
```

Las tres operaciones expuestas son `saveRefreshToken()`, `getRefreshToken()` y `deleteRefreshToken()`. El refresh token nunca toca `SharedPreferences` sin cifrar ni ningún archivo en el filesystem.

### Access token — solo en memoria

El access token vive únicamente en el estado de Riverpod (`AuthAuthenticated.accessToken`). No se persiste en disco en ningún escenario. Se descarta al hacer logout, al expirar la sesión por inactividad, o al cerrar la app.

### Datos de usuario (UserModel)

Nombre, rol, nivel técnico y demás campos del socio autenticado se mantienen en memoria como parte del estado `AuthAuthenticated`. No se cachean en disco entre sesiones — se obtienen frescos del JWT en cada login o refresh.

### Cookies

`CookieJar` se instancia al arrancar la app (`lib/core/api/cookie_jar_provider.dart`) y nunca se persiste. El backend, al detectar el header `X-Sadday-Client: mobile`, devuelve los tokens en el body JSON en lugar de en una cookie `HttpOnly`, que es el mecanismo para web. La cookie jar existe para manejar posibles cookies de sesión de endpoints que las usen, pero no almacena tokens de autenticación.

---

## 3. Cliente HTTP e interceptores

### Configuración de Dio

`lib/core/api/dio_client.dart`:

- Base URL inyectada desde `AppConfig.apiBaseUrl` (no hardcodeada).
- Timeouts: 10 s de conexión, 30 s de recepción.
- Header fijo `X-Sadday-Client: mobile` en cada request — le indica al backend que debe responder con JSON en lugar de cookies HttpOnly para el flujo de tokens.

### AuthInterceptor — refresco proactivo

`lib/core/api/interceptors/auth_interceptor.dart`:

Antes de enviar cada request, el interceptor evalúa si el access token está por expirar. Si quedan menos de 30 segundos (`JwtUtils.isExpiredWithBuffer()`), dispara el refresh antes de adjuntar el token al header. Esto evita que un request salga con un token que el servidor va a rechazar por expirado.

```
Request saliente
  │
  ├─ ¿Token expira en < 30 s? → withRefreshLock() → refresh → nuevo token
  │
  └─ Añade Authorization: Bearer <access_token>
```

### ErrorInterceptor — recuperación ante 401

`lib/core/api/interceptors/error_interceptor.dart`:

Cuando un request recibe 401 (token expirado que pasó el buffer), el interceptor:

1. Adquiere el lock de refresh.
2. Llama a `authNotifierProvider.notifier.refresh()`.
3. Si el refresh tiene éxito: reintenta el request original con el nuevo token.
4. Si el refresh falla (token revocado o expirado en servidor): hace logout y rechaza el request con `AuthException`.

### Refresh lock — mutex para concurrencia

`lib/core/api/refresh_lock.dart`:

El backend rota el refresh token en cada uso — emite un par nuevo e invalida el anterior. Si dos requests fallan en 401 simultáneamente y ambos intentan hacer refresh en paralelo, el segundo refresh llegaría con el token ya invalidado por el primero, causando un logout inesperado.

El mutex (`synchronized` package) serializa los refreshes: el segundo espera a que el primero termine y luego comprueba si el token en memoria ya fue renovado antes de volver a llamar al endpoint.

### Flujo de refresh completo

`lib/core/auth/auth_provider.dart`:

```
1. Lee refresh token de Keychain/Keystore
2. POST /v1/auth/refresh  { refreshToken: <raw> }
   Header: X-Sadday-Client: mobile
3. Backend revoca el token presentado y emite par nuevo
4. App guarda nuevo refresh token en Keychain/Keystore   ← primero
5. App actualiza estado en memoria con nuevo access token ← después

El orden en paso 4-5 es deliberado: si la app se cierra entre ambos,
el token persistido es el nuevo (válido). No se pierde sesión.
```

---

## 4. Autenticación biométrica e inactividad

### Timeout de inactividad

`lib/core/security/inactivity_notifier.dart`:

Timeout fijo de **10 minutos** sin interacción. El temporizador se resetea ante cualquier `onTap` u `onPanDown` capturado por el `GestureDetector` raíz en `app.dart`.

Al expirar:
- El estado pasa a `AuthLocked`.
- El access token se borra de memoria.
- El refresh token permanece en Keychain/Keystore — la sesión no se destruye, solo se bloquea.

### Desbloqueo biométrico

`lib/features/auth/presentation/providers/unlock_notifier.dart`:

Al entrar en estado `AuthLocked`, el usuario ve la pantalla de desbloqueo. El flujo:

1. `local_auth.authenticate()` con mensaje localizado: "Usa tu huella o Face ID para acceder a Sadday".
2. Si la autenticación biométrica tiene éxito: se llama a `authNotifierProvider.notifier.refresh()` — se valida el refresh token contra el servidor y se emite un nuevo access token.
3. Si la biométrica falla **3 veces seguidas**: logout completo (access + refresh token descartados).

La biométrica es opcional para el usuario. Si no está habilitada, el estado `AuthLocked` lleva directamente a la pantalla de login para re-autenticar con contraseña.

---

## 5. Protección de pantalla y captura

### Android — FLAG_SECURE

`android/app/src/main/kotlin/com/sadday/app/MainActivity.kt`:

```kotlin
window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
```

Aplicado en `onCreate()`, cubre todas las pantallas de la app:
- Impide capturas de pantalla desde cualquier app o el propio sistema.
- Difumina el contenido en el recents switcher (app switcher).

### iOS — privacy overlay

`lib/app.dart` + `lib/core/security/app_lifecycle_observer.dart`:

Cuando la app pasa a estado `inactive` o `paused` (p. ej. el usuario pulsa el botón home o llega una llamada), se superpone un widget `_PrivacyOverlay` que muestra el logo de Sadday sobre el color de fondo de la app.

Esto evita que el sistema operativo capture una screenshot del contenido real al componer la vista del app switcher.

Este comportamiento es exclusivo de iOS (`if (!Platform.isIOS) return`) porque en Android lo gestiona `FLAG_SECURE`.

---

## 6. Deep links

### Rutas con token en query parameter

| Ruta | Uso |
|------|-----|
| `/reset-password?token=<token>` | Recuperación de contraseña (flujo A del [Flujo 18](../../flujos/18-recuperacion-contrasena.md)) |
| `/registro/completar?token=<token>` | Completar registro desde invitación por email |

Los tokens se pasan al backend via POST — nunca se evalúan en el cliente. La validación (expiración, uso único, asociación al socio) ocurre enteramente en el servidor.

### Ausencia de app-site association

La app no tiene configurados `apple-app-site-association` (iOS) ni `assetlinks.json` (Android). Esto significa que cualquier URL con el esquema correcto puede abrir la app, no solo enlaces provenientes del dominio oficial.

El riesgo es mitigado por el diseño del servidor: los tokens son de un solo uso, expiran en 15 minutos, y solo son válidos para el socio al que fueron emitidos. Un atacante que consiguiera un enlace válido igualmente necesitaría ese token — no puede fabricar uno arbitrario.

---

## 7. Logging en producción

`lib/core/config/app_logger.dart`:

Todos los métodos de logging (`d()`, `i()`, `w()`, `e()`) están protegidos por `if (!AppConfig.isProd)`. En el flavor `prod`, **no se escribe ningún log**.

En ningún punto del código se loguean tokens, contraseñas ni PII. Los mensajes de error en los interceptores son genéricos: `'logout endpoint error'`, `'refresh failed'`.

En `dev` y `staging` los logs están habilitados para debugging. En staging se recomienda tratar los logs con la misma sensibilidad que en producción.

---

## 8. Ciclo de vida de sesión (logout)

`lib/core/auth/auth_provider.dart`:

El logout ejecuta los pasos en este orden:

```
1. POST /v1/auth/logout  { refreshToken: <raw> }
   Authorization: Bearer <access_token>
   → El servidor revoca el refresh token antes de que el cliente lo borre.

2. deleteRefreshToken()   → borra de Keychain/Keystore
3. setBiometricEnabled(false)
4. Estado → AuthUnauthenticated()
```

El orden garantiza que incluso si la app se cierra tras el paso 1, el token en servidor ya está revocado. Si falla el endpoint de logout (sin red), el cliente igualmente limpia el estado local — el token queda en BD hasta que expire por TTL.

### Logout total (logout-all)

Disponible desde la pantalla de perfil. Llama a `POST /v1/auth/logout-all`, que revoca todos los refresh tokens del socio en todos los dispositivos. El flujo local es idéntico al logout simple.

---

## 9. Configuración de red nativa

### Android — network_security_config.xml

**Producción** (`android/app/src/main/res/xml/network_security_config.xml`):
```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
</network-security-config>
```

Solo tráfico HTTPS. Solo CAs del sistema (no se permiten CAs de usuario instaladas manualmente).

**Desarrollo** (`android/app/src/dev/res/xml/network_security_config.xml`):

Igual que producción para dominios reales. Excepción para `10.0.2.2:8080` (backend local en emulador Android) que permite plaintext. Esta excepción solo existe en el flavor `dev`.

### Android — AndroidManifest.xml

- `android:allowBackup="false"` — los datos de la app no se incluyen en backups de Android (Google Drive backup, ADB backup). El refresh token en Keystore no saldría del dispositivo.
- `android:networkSecurityConfig="@xml/network_security_config"` — enlaza la config de red descrita arriba.
- Permisos declarados: `INTERNET`, `USE_BIOMETRIC`, `USE_FINGERPRINT`.

### iOS — Info.plist

- `NSFaceIDUsageDescription` presente — requerido por App Store para usar Face ID.
- No hay entradas `NSAppTransportSecurity` con excepciones — App Transport Security (ATS) activo por defecto, que bloquea HTTP en producción.

---

## 10. Gaps conocidos

### G-01 — Jailbreak/root detection sin integrar

El paquete `flutter_jailbreak_detection ^1.10.0` está declarado en `pubspec.yaml` pero nunca se importa ni se llama en ningún archivo. La app no toma ninguna acción en dispositivos con jailbreak (iOS) o root (Android).

**Riesgo:** En un dispositivo rooteado, el Keystore puede ser accesible para otras apps con privilegios root. El acceso físico al dispositivo con root reduce la protección que ofrece `flutter_secure_storage`.

**Opciones:**
- Integrar la detección en el arranque (`main_prod.dart`) y bloquear la app si detecta jailbreak/root.
- O eliminar la dependencia si se decide no implementarlo (reduce el surface area de dependencias sin usar).

### G-02 — Validación de contraseña inconsistente en reset

La pantalla `ResetPasswordScreen` muestra el hint "mínimo 8 caracteres" pero el backend requiere 12 con mayúscula, minúscula, número y símbolo (ver [Flujo 18](../../flujos/18-recuperacion-contrasena.md)). El usuario ve el error del servidor solo al enviar el formulario.

La pantalla `CompleteRegistrationScreen` tiene la validación correcta (12 caracteres).

**Impacto:** UX degradada — el usuario puede creer que su contraseña de 8 caracteres es válida hasta que el servidor la rechaza.

### G-03 — Sin app-site association para deep links

No existe `apple-app-site-association` ni `assetlinks.json`. Ver [§6](#6-deep-links) para el análisis de riesgo y la mitigación actual.

### G-04 — Certificate pinning: decisión definitiva de no implementar

No se implementa certificate pinning en Dio ni en el `HttpClient` de Dart. Decisión tomada y cerrada.

**Justificación:**
- El backend usa CA pública (Cloudflare) — el OS ya valida la cadena de confianza.
- El pinning requeriría actualizar la app con cada rotación de certificado, añadiendo complejidad operativa sin ganancia real para este perfil de amenaza.
- Para una aplicación de club de montaña, la CA del sistema ofrece protección suficiente contra MITM.

La protección contra ataques en tránsito la proveen: TLS 1.2+ obligatorio, ATS en iOS, `cleartextTrafficPermitted=false` en Android prod, y la validación del certificado por el TrustManager del OS.

---

## 11. Correspondencia OWASP MASVS

| Control MASVS | Requerimiento | Estado |
|---------------|--------------|--------|
| STORAGE-1 | No almacenar datos sensibles en almacenamiento sin cifrar | ✅ Refresh token en Keychain/Keystore; access token solo en memoria |
| STORAGE-2 | No exponer datos sensibles en logs, capturas de pantalla o IPC | ✅ Logs deshabilitados en prod; FLAG_SECURE (Android); privacy overlay (iOS) |
| CRYPTO-1 | Uso de primitivas criptográficas fuertes | ✅ Delegado al OS (Keychain/Keystore); RS256 en JWT (backend) |
| AUTH-1 | Autenticación a través de endpoints seguros | ✅ HTTPS obligatorio; tokens con expiración; refresh rotativo |
| AUTH-2 | Biometría como segundo factor de desbloqueo | ✅ local_auth v3; 3 intentos → logout |
| AUTH-4 | Sesión expira por inactividad | ✅ 10 minutos; gestures reset el timer |
| AUTH-5 | Logout invalida tokens en servidor | ✅ POST /logout antes de limpiar estado local |
| NETWORK-1 | Tráfico cifrado con TLS | ✅ ATS (iOS); cleartext=false (Android prod); HTTPS forzado |
| NETWORK-2 | Verificación del certificado del servidor | ✅ CA del sistema; sin excepciones en prod |
| NETWORK-3 | Certificate pinning | ✅ Decisión definitiva: no implementar — CA pública del sistema es suficiente para este perfil de amenaza (ver G-04) |
| PLATFORM-2 | Prevenir backup de datos sensibles | ✅ `allowBackup="false"` en AndroidManifest |

---

## Relación con otros documentos

| Documento | Relación |
|-----------|----------|
| [`security-architecture.md`](security-architecture.md) | Arquitectura backend: tokens, firma JWT RS256, rate limiting |
| [`authentication-security.md`](authentication-security.md) | TOTP, country challenge, refresh token theft detection |
| [`docs/flujos/17-segundo-factor-mobile.md`](../../flujos/17-segundo-factor-mobile.md) | Flujo completo de 2FA TOTP en mobile |
| [`docs/flujos/18-recuperacion-contrasena.md`](../../flujos/18-recuperacion-contrasena.md) | Flujos de reset de contraseña (deep link, token, validación) |
