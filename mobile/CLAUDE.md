# Sadday App — Mobile (Flutter) — Referencia de contexto

Spec completa: `docs/flutter-mobile-spec.md`
Backend API base: `https://api.el-sadday.com/api` (prod) / `https://api-dev.el-sadday.com/api` (dev)
Headers obligatorios en toda request autenticada: `Authorization: Bearer {token}` + `X-Sadday-Client: mobile`

---

## Stack — versiones exactas (no cambiar sin revisar spec §2 y §18)

```yaml
dio: ^5.9.2
dio_cookie_manager: ^4.0.0
cookie_jar: ^4.0.8
flutter_secure_storage: ^10.2.0
local_auth: ^3.0.1
flutter_riverpod: ^3.3.1
riverpod_annotation: ^3.3.1
go_router: ^17.2.3
reactive_forms: ^18.0.0
fl_chart: ^0.70.0
qr_flutter: ^4.2.0
pinput: ^5.0.0
awesome_snackbar_content: ^0.2.0
lucide_icons_flutter: ^1.0.0
flutter_pdfview: ^1.3.2
share_plus: ^10.0.0
path_provider: ^2.1.5
file_picker: ^8.1.7
intl: ^0.20.2
infinite_scroll_pagination: ^5.0.0
flutter_dotenv: ^5.2.1
google_fonts: ^6.2.1
shared_preferences: ^2.3.4
connectivity_plus: ^6.1.3
logger: ^2.5.0
synchronized: ^3.3.0
flutter_jailbreak_detection: ^1.9.0
```

---

## Arquitectura

**Patrón:** Feature-first + Riverpod (AsyncNotifier / StateNotifier)

```
lib/
├── main_dev.dart / main_staging.dart / main_prod.dart
├── app.dart                  # MaterialApp.router + ProviderScope
├── core/
│   ├── api/                  # Dio, interceptores, AppException, endpoints
│   ├── auth/                 # AuthService, AuthProvider, AuthGuard, JwtUtils
│   ├── config/               # AppConfig (lee .env vía flutter_dotenv)
│   ├── security/             # AppLifecycleObserver, InactivityNotifier
│   ├── storage/              # SecureStorageService (wrapper flutter_secure_storage)
│   ├── theme/                # AppColors, AppTheme, AppTextStyles
│   └── widgets/              # Componentes base (AppButton, AppCard, AppInput…)
├── features/
│   └── {feature}/
│       ├── data/             # Repository + RemoteDataSource (Dio)
│       ├── domain/models/    # Modelos tipados (freezed o manual)
│       └── presentation/
│           ├── providers/    # Riverpod providers
│           ├── screens/      # Pantallas completas
│           └── widgets/      # Widgets propios del feature
└── router.dart               # GoRouter + guards de rol
```

**Features:** auth, dashboard, socios, montanas, rutas, salidas, informes, actas, estadisticas, planificador, contactos, admin, perfil

---

## Seguridad — reglas NO negociables

### Tokens
| Dato | Dónde | Nunca en |
|---|---|---|
| Access token | Solo memoria (AuthProvider Riverpod) | Disco, logs, SharedPrefs |
| Refresh token | `flutter_secure_storage` (Keychain/Keystore) | SharedPrefs, archivos planos |
| Flag biométrico | `flutter_secure_storage` | SharedPrefs |
| PII del usuario | Solo memoria (Riverpod) | Disco |

### Refresh token — mutex obligatorio (MASVS-AUTH / MITRE T1557)
Usar `synchronized` para evitar race condition cuando múltiples requests fallan 401 simultáneamente.
El backend rota el refresh token en cada refresh y revoca el anterior — dos refreshes paralelos pueden revocar toda la sesión.
Ver implementación en `lib/core/api/refresh_lock.dart`.

### Validación JWT local (MASVS-AUTH-2)
Antes de cada request, verificar `exp` del JWT localmente (buffer de 30s). Solo para decidir refresh proactivo — NO validar firma en cliente.

### Screen masking
- Android: `FLAG_SECURE` en `MainActivity.kt` — previene screenshots y oculta contenido en app switcher
- iOS: overlay por código en `AppLifecycleObserver` al pasar a `inactive`/`paused`

### Campos sensibles
`TextField(obscureText: true, enableSuggestions: false, autocorrect: false)` en: contraseña, PIN MFA, country challenge, cédula.

### Inactividad (10 min)
`InactivityNotifier` (Riverpod provider) recibe callback `onTimeout`. Resetear en cada gesto del usuario. Al timeout: limpiar access token de memoria → navegar a `/unlock`.

### HTTPS
- Android: `network_security_config.xml` con `cleartextTrafficPermitted="false"`
- iOS: ATS habilitado por defecto — no agregar `NSAllowsArbitraryLoads`

### Deep links — sanitizar tokens (MASVS-PLATFORM / MITRE T1444)
Validar token con regex `^[a-zA-Z0-9\-]{36,128}$` antes de procesar. Si inválido → `/login`.

### Jailbreak / root
`flutter_jailbreak_detection` al iniciar. Para esta app: mostrar advertencia, NO bloquear acceso.

### Logs
`AppLogger` silenciado en prod (`AppConfig.isProd`). Nunca loguear tokens, contraseñas ni PII.

### Android backup
`android:allowBackup="false"` en `AndroidManifest.xml`.

### Logout
1. `POST /v1/auth/logout` (aunque falle, continuar)
2. Limpiar AuthProvider (memoria)
3. `secureStorage.delete('refresh_token')` + `delete('biometric_enabled')`
4. Limpiar archivos temporales (PDFs descargados)
5. `router.go('/login')`

### Clipboard
Limpiar 30s después de copiar datos sensibles (API keys, tokens MFA).

### Build release
```bash
flutter build appbundle --flavor prod -t lib/main_prod.dart --release --obfuscate --split-debug-info=build/debug-info/
flutter build ipa --flavor prod -t lib/main_prod.dart --release --obfuscate --split-debug-info=build/debug-info/
```
Guardar `split-debug-info/` para desofuscar stack traces en producción.

---

## Diseño — tokens de color (dark mode por defecto)

```dart
background  = Color(0xFF111111)   foreground  = Color(0xFFFAFAFA)
card        = Color(0xFF111111)   primary     = Color(0xFF6B7FD4)  // azul-violeta
secondary   = Color(0xFF3A3A3A)   destructive = Color(0xFFE5534B)  // rojo
border      = Color(0xFF3A3A3A)   mutedFg     = Color(0xFFAAAAAA)
sidebar     = Color(0xFF1C1C1C)

// Estados de salida
PLANIFICADA = Color(0xFFD4A84B)   EN_CURSO  = Color(0xFF6B7FD4)
REALIZADA   = Color(0xFF4CAF8A)   CANCELADA = Color(0xFFE5534B)
```

Fuente: **Inter** (`google_fonts`). Radios: sm=6, md=8, lg=10, xl=14.

Componentes base en `lib/core/widgets/`:
`AppButton`, `AppInput`, `AppCard`, `AppBadge`, `AppDialog`, `AppBottomSheet`,
`AppTable`, `AppTabs`, `AppPinInput`, `AppAvatar`, `AppStatusBadge`, `AppEmptyState`, `AppLoadingOverlay`

---

## Navegación

**Bottom Nav Bar (5 ítems):** Dashboard · Salidas · Informes · Mi Perfil · Más (abre Drawer)

**Rutas públicas:** `/login`, `/forgot-password`, `/reset-password`, `/registro/completar`

**Guard de auth:** si no autenticado → `/login`. Si autenticado en `/login` → `/dashboard`.
**Guard de rol:** rutas restringidas devuelven `/403` si el rol no tiene acceso.

---

## Permisos por rol (resumen)

| Acción | SOCIO | DIRECTIVO | SECRETARIA | ADMIN |
|---|:---:|:---:|:---:|:---:|
| Ver salidas / montañas / informes / actas / estadísticas | ✅ | ✅ | ✅ | ✅ |
| Crear/editar salidas | ❌ | ✅ | ✅ | ✅ |
| Ver lista socios | ❌ | ✅ | ✅ | ✅ |
| Crear/editar socios | ❌ | ❌ | ✅ | ✅ |
| Eliminar socios | ❌ | ❌ | ❌ | ✅ |
| Gestionar actas | ❌ | ❌ | ✅ | ✅ |
| Validar informes | ❌ | ✅ | ❌ | ✅ |
| Administración | ❌ | ✅ | ✅ | ✅ |
| Contactos | ❌ | ❌ | ✅ | ✅ |

DIRECTIVO: solo estados habilitación no restrictivos (Habilitado, Vitalicio).
Estados restrictivos (Inhabilitado, Licencia, Re-inscripción): solo ADMIN / SECRETARIA.
ADMIN nunca puede ser bloqueado. No se puede bloquear a la única SECRETARIA activa.

---

## Autenticación — flujos clave

### Login
`POST /v1/auth/login` → 200 (éxito) | 202+challengeToken (MFA) | 202+countryChallengeToken (country)
- Access token → solo AuthProvider (memoria)
- Refresh token → `flutter_secure_storage` vía interceptor que captura `Set-Cookie`

### Biometría
Desbloqueo rápido (no reemplaza login inicial). Flag `biometric_enabled` en SecureStorage.
Al abrir app: si flag activo → pantalla `/unlock` → `local_auth.authenticate()` → `POST /v1/auth/refresh`.
Fallo 3 veces consecutivas → forzar login completo.

### Cookies (dio_cookie_manager)
Usar `SecureCookieJar` + `CookieManager` en Dio. El refresh token viaja como cookie HttpOnly.
NO inyectar manualmente el header `Cookie`.

---

## Ambientes y flavors

| Ambiente | App ID Android | Bundle ID iOS | Backend |
|---|---|---|---|
| DEV | `com.sadday.app.dev` | `com.sadday.app.dev` | `https://api-dev.el-sadday.com/api` |
| Staging | `com.sadday.app.staging` | `com.sadday.app.staging` | `https://api-staging.el-sadday.com/api` |
| Prod | `com.sadday.app` | `com.sadday.app` | `https://api.el-sadday.com/api` |

Entry points: `main_dev.dart`, `main_staging.dart`, `main_prod.dart` — cada uno carga su `.env`.
Correr: `flutter run --flavor dev -t lib/main_dev.dart`

---

## PDF y CSV

- **Ver PDF in-app:** `flutter_pdfview`
- **Compartir PDF/CSV:** `share_plus` → share sheet del SO (WhatsApp, email, Archivos, Drive)
- **Importar CSV/MD:** `file_picker` → multipart Dio
- Guardar en `getTemporaryDirectory()` antes de abrir o compartir
- Limpiar archivos temporales en logout

---

## i18n

Idiomas: **ES (base) y EN**. Archivos ARB en `lib/l10n/`. Idioma por defecto: español.
Preferencia guardada en `SharedPreferences` (no SecureStorage).
Textos UI siempre vía `AppLocalizations.of(context)!` — nunca strings hardcodeados en widgets.
Mensajes de error del backend: mostrar tal cual (ya vienen en español desde el servidor).

---

## Manejo de errores — jerarquía tipada

```dart
sealed class AppException  // lib/core/api/app_exception.dart
  NetworkException          // sin conexión
  UnauthorizedException     // 401 — sesión expirada
  ForbiddenException        // 403 — sin permisos
  BusinessException(msg)    // 4xx con mensaje del backend
  ServerException           // 5xx
```

Transformar HTTP status en excepciones tipadas en el interceptor de Dio. Nunca `catch (_)` genérico sin relanzar.

---

## Testing

| Tipo | Herramienta | Cobertura objetivo |
|---|---|---|
| Unit | `flutter_test` + `mocktail` | 70% en repositories/services/providers |
| Widget | `flutter_test` + `golden_toolkit` | Pantallas y componentes base |
| Integration | `integration_test` | Flujos críticos (login → dashboard) |
| Golden | `golden_toolkit` | Design system — regresiones visuales |
