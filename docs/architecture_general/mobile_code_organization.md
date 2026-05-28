# Organización del Código Mobile

Este documento explica la estructura de carpetas de la app mobile de Sadday, los patrones de diseño utilizados y el rol de cada capa. Está orientado a **nuevos desarrolladores** que necesiten entender el proyecto rápidamente.

---

## Patrón de Arquitectura: Feature-first

La app mobile sigue el patrón **"Feature-first"** (también conocido como Package by Feature). Cada módulo de negocio (`auth`, `socios`, `salidas`, etc.) tiene su propia carpeta que agrupa **toda** la lógica relacionada: modelos de dominio, acceso a datos y presentación.

```
# ✅ Feature-first (Sadday Mobile)
lib/features/
├── auth/        ← Login, 2FA, country challenge, recuperación password
│   ├── data/       — Repositorios (llamadas al backend vía Dio)
│   ├── domain/
│   │   └── models/ — Entidades de dominio (fromJson/toJson)
│   └── presentation/
│       ├── providers/ — Riverpod providers y notifiers
│       ├── screens/   — Pantallas completas
│       └── widgets/   — Widgets locales del feature
├── salidas/     ← Todo lo de Salidas está aquí
└── socios/      ← Todo lo de Socios está aquí
```

Este patrón facilita añadir features completas sin tocar el resto del código, y hace obvio dónde buscar cuando hay un bug en un módulo específico.

---

## Estructura de Carpetas Completa

```
mobile/
│
├── lib/
│   ├── app.dart              ← MaterialApp raíz, theme provider, localization
│   ├── router.dart           ← go_router: rutas y guards de autenticación
│   ├── main_dev.dart         ← Entry point del flavor dev
│   ├── main_staging.dart     ← Entry point del flavor staging
│   ├── main_prod.dart        ← Entry point del flavor prod
│   │
│   ├── core/                 ← Infraestructura transversal (sirve a todos los features)
│   │   ├── api/              — Dio client, cookie jar, interceptores HTTP
│   │   ├── auth/             — AuthState, AuthNotifier, JwtUtils
│   │   ├── config/           — AppConfig (dotenv), AppLogger
│   │   ├── security/         — Jailbreak detection, biometría
│   │   ├── storage/          — SecureStorage wrapper (Keychain/Keystore)
│   │   ├── theme/            — Tema Material 3
│   │   └── widgets/          — Componentes UI reutilizables globales
│   │
│   ├── features/             ← Un directorio por módulo de negocio
│   │   ├── auth/
│   │   ├── dashboard/
│   │   ├── salidas/
│   │   ├── montanas/
│   │   ├── rutas/
│   │   ├── planificador/
│   │   ├── informes/
│   │   ├── actas/
│   │   ├── socios/
│   │   ├── estadisticas/
│   │   ├── contactos/
│   │   ├── notificaciones/
│   │   ├── perfil/
│   │   ├── admin/
│   │   ├── reglamento/       — Visualización del reglamento del club (PDF)
│   │   └── teoria/           — Material teórico (PDF)
│   │
│   └── l10n/                 ← Archivos de localización (ES / EN)
│
├── .env.dev                  ← Variables de entorno del flavor dev
├── .env.staging              ← Variables de entorno del flavor staging
├── .env.prod                 ← Variables de entorno del flavor prod
├── pubspec.yaml              ← Dependencias del proyecto
└── test/                     ← Tests unitarios y de widget
    └── integration_test/     ← Tests de integración (requieren dispositivo)
```

---

## Las 3 Capas Internas de Cada Feature

Cada feature sigue la misma estructura interna. Se usa `auth` como ejemplo:

### 1. `data/` — Acceso a Datos

Contiene los **repositorios**: clases que hacen las llamadas HTTP al backend usando Dio.

```dart
// features/auth/data/auth_repository.dart
class AuthRepository {
  final Dio _dio;

  Future<LoginResponse> login(String username, String password) async {
    final response = await _dio.post('/v1/auth/login', data: {
      'username': username,
      'password': password,
    });
    return LoginResponse.fromJson(response.data['data']);
  }
}
```

Los repositorios **no tienen lógica de negocio** — solo formatean la petición y parsean la respuesta.

---

### 2. `domain/models/` — Modelos de Dominio

Son las clases Dart que representan los datos del negocio. Equivalen a los DTOs del backend.

```dart
// features/auth/domain/models/login_response.dart
class LoginResponse {
  final String accessToken;
  final UserInfo user;
  final bool requiresMfa;

  const LoginResponse({
    required this.accessToken,
    required this.user,
    required this.requiresMfa,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) => LoginResponse(
    accessToken: json['accessToken'] as String,
    user: UserInfo.fromJson(json['user']),
    requiresMfa: json['requiresMfa'] as bool? ?? false,
  );
}
```

---

### 3. `presentation/` — UI y Estado

#### `providers/` — Estado con Riverpod

Los providers son el equivalente a los `hooks` del frontend React. Manejan el estado de la UI y coordinan las llamadas al repositorio.

```dart
// features/auth/presentation/providers/auth_notifier.dart
@riverpod
class AuthNotifier extends _$AuthNotifier {
  @override
  FutureOr<AuthState> build() => AuthState.unauthenticated();

  Future<void> login(String username, String password) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repo = ref.read(authRepositoryProvider);
      final response = await repo.login(username, password);
      return AuthState.authenticated(response.user);
    });
  }
}
```

#### `screens/` — Pantallas Completas

Una pantalla es un Widget que ocupa toda la pantalla. Lee el estado del provider y lo renderiza.

```dart
// features/auth/presentation/screens/login_screen.dart
class LoginScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authNotifierProvider);

    return authState.when(
      loading: () => const CircularProgressIndicator(),
      error: (e, _) => ErrorMessage(e.toString()),
      data: (state) => LoginForm(onSubmit: (u, p) =>
        ref.read(authNotifierProvider.notifier).login(u, p)),
    );
  }
}
```

#### `widgets/` — Widgets Locales

Widgets reutilizables **dentro del feature** (no se usan en otros módulos). Por ejemplo, un `TotpCodeInput` que solo aparece en el flujo de 2FA.

---

## `core/` — Infraestructura Transversal

### `api/`

El cliente HTTP de la app. Equivale a `lib/api.ts` del frontend.

```
core/api/
├── dio_client.dart           ← Instancia Dio configurada (baseURL, timeouts, interceptores)
├── auth_dio_provider.dart    ← Provider Riverpod del cliente Dio autenticado
├── cookie_jar_provider.dart  ← Cookie jar persistido en disco (refresh token HttpOnly)
├── refresh_lock.dart         ← Mutex (synchronized package) para refresh concurrente
├── app_exception.dart        ← Modelo de error unificado (mapea errores Dio → AppException)
├── paged_response.dart       ← Wrapper genérico para respuestas paginadas
└── interceptors/
    ├── auth_interceptor.dart ← Añade Bearer token; refresh proactivo con mutex si JWT expira pronto
    └── error_interceptor.dart← Captura 401 y dispara refresh reactivo; mapea errores HTTP
```

**Flujo de una petición:**

```
Screen (Widget)
  │
  ▼
Provider (Riverpod)     ← Lee/escribe estado, llama al repositorio
  │
  ▼
Repository              ← Construye la petición y parsea la respuesta
  │
  ▼
Dio (auth_interceptor)  ← Inyecta Bearer token; refresca si está próximo a expirar
  │
  ▼
Backend API             ← Responde con JSON
```

### `auth/`

Gestiona el estado de sesión global. Contiene:

- **`AuthState`** — Sealed class: `AuthUnauthenticated`, `AuthAuthenticated(accessToken, user)`, `AuthLoading`
- **`AuthNotifier`** — Provider que maneja login, logout y refresh del token
- **`JwtUtils`** — Parsea el JWT y calcula si está próximo a expirar (`isExpiredWithBuffer`)

El access token vive **solo en memoria** (en el `AuthNotifier`), nunca en disco. El refresh token es una cookie HttpOnly que gestiona el `CookieJar` de Dio.

### `storage/`

Wrapper sobre `flutter_secure_storage` para guardar datos sensibles en:
- **Keychain** (iOS) con accesibilidad `unlocked`
- **Keystore** (Android)

Se usa para persistir el access token entre arranques de la app (el `AuthNotifier` lo lee al iniciar).

### `config/`

- **`AppConfig`** — Lee variables de entorno con `flutter_dotenv` (`API_BASE_URL`, `ENV`, `APP_NAME`)
- **`AppLogger`** — Wrapper de `logger` completamente silenciado en el flavor `prod`

### `security/`

- **Jailbreak / Root detection** — Se ejecuta al arrancar la app; si detecta un dispositivo comprometido, muestra advertencia
- **Biometría** — Usa `local_auth` para desbloquear la sesión sin re-ingresar credenciales

### `widgets/`

Componentes UI reutilizables en **cualquier feature**:
- `AppButton` — Botón estándar con variantes (primary, outlined, destructive) y estado de carga
- `AppInput` — Campo de texto con etiqueta, hint y manejo de errores
- `AppStatusBadge` — Badge de estado con colores semánticos
- `AppBadge` — Chip genérico para etiquetas

---

## Flavors (Entornos)

La app tiene 3 flavors que comparten el mismo codebase pero se comportan distinto:

| Flavor | Entry point | API URL | App name | Logger |
|--------|-------------|---------|----------|--------|
| `dev` | `main_dev.dart` | `10.0.2.2:8080/api` | Sadday DEV | Activo |
| `staging` | `main_staging.dart` | `api-staging.el-sadday.com` | Sadday STAGING | Activo |
| `prod` | `main_prod.dart` | `api.el-sadday.com` | Sadday | Silenciado |

Variables de entorno por archivo `.env.{flavor}`:

```env
ENV=dev
API_BASE_URL=http://10.0.2.2:8080/api
APP_NAME=Sadday DEV
```

Comandos de build:

```bash
# Desarrollo
fvm flutter run --flavor dev -t lib/main_dev.dart

# Producción (Android)
fvm flutter build appbundle --flavor prod -t lib/main_prod.dart --release
```

---

## Autenticación — Flujo Completo

### Login estándar

```
LoginScreen → AuthNotifier.login()
  → AuthRepository.login(username, password)
  → POST /v1/auth/login
  → Backend devuelve accessToken + Set-Cookie: refreshToken (HttpOnly)
  → AuthNotifier guarda accessToken en memoria
  → CookieJar persiste la cookie en disco automáticamente
  → go_router redirige a /dashboard
```

### Refresh proactivo (antes de que expire)

```
auth_interceptor.onRequest()
  → JwtUtils.isExpiredWithBuffer(accessToken)  ← ¿expira en < 60 seg?
  → SI: adquiere mutex withRefreshLock()
    → AuthNotifier.refresh()
    → POST /v1/auth/refresh  (cookie se envía automáticamente)
    → Nuevo accessToken → AuthNotifier lo guarda
  → Inyecta el token fresco en el header Authorization
```

El **mutex** (`synchronized` package) garantiza que si hay 5 peticiones concurrentes al mismo tiempo y todas detectan que el token está próximo a expirar, solo **una** hace el refresh. Las otras esperan y usan el resultado.

### 2FA y Country Challenge

Si el login devuelve `requiresMfa: true` o `requiresCountryChallenge: true`, `router.dart` redirige a la pantalla correspondiente antes de completar la autenticación.

---

## Seguridad

| Aspecto | Implementación |
|---------|---------------|
| Access token | Memoria (AuthNotifier) + flutter_secure_storage entre sesiones |
| Refresh token | Cookie HttpOnly (gestionada por CookieJar de Dio) |
| Almacenamiento seguro | Keychain (iOS, `unlocked`) / Keystore (Android) |
| Refresh concurrente | Mutex `synchronized` en `refresh_lock.dart` |
| Integridad del dispositivo | flutter_jailbreak_detection al arrancar |
| Biometría | local_auth para desbloqueo de sesión |
| Logs | AppLogger silenciado completamente en flavor `prod` |

---

## Comparación Backend / Frontend Web / Mobile

| Concepto | Backend (Java) | Frontend (React) | Mobile (Flutter) |
|---|---|---|---|
| Organización | Package by Feature | Package by Layer | Feature-first |
| Lógica de negocio | `service/` | `pages/` | `presentation/providers/` |
| Acceso a datos | `repository/` | `hooks/` | `data/` (repositorios) |
| Contratos de datos | `dto/` (Java records) | `types/` (TypeScript) | `domain/models/` (Dart) |
| Cliente HTTP | Spring RestTemplate / WebClient | Axios (`lib/api.ts`) | Dio (`core/api/`) |
| Estado global | — (stateless) | Zustand (`stores/`) | Riverpod (`AuthNotifier`) |
| Componentes visuales | — | `components/` | `core/widgets/` |
| Enrutamiento | `@RequestMapping` | React Router | go_router |
| Seguridad HTTP | Spring Security filters | Axios interceptors | Dio interceptors |
| Tests unitarios | JUnit 5 + Mockito | Vitest + Testing Library | flutter_test + mocktail |
| Tests E2E | Testcontainers | Playwright | integration_test |
