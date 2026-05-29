# Sadday App — Mobile

Aplicación móvil del sistema de gestión del Club de Montaña Sadday (`el-sadday.com`).
Construida con Flutter, corre en Android e iOS desde un único codebase.

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Dart · Flutter 3.32.x (gestionado con fvm) |
| Estado | Riverpod 3 + riverpod_annotation (codegen) |
| Navegación | go_router 17 |
| HTTP | Dio 5 + dio_cookie_manager |
| Almacenamiento seguro | flutter_secure_storage (Keychain iOS / Keystore Android) |
| Formularios | reactive_forms 18 |
| Gráficos | fl_chart 1 |
| PDF | flutter_pdfview + share_plus |
| i18n | flutter_localizations (ES / EN) |
| Seguridad | local_auth (biometría) · flutter_jailbreak_detection · logger silenciado en prod |
| Build | fvm + flavors (dev / staging / prod) |
| Tests | flutter_test · mocktail · integration_test |

---

## Requisitos previos

- [fvm](https://fvm.app) para gestionar la versión de Flutter del proyecto
- Android Studio o Xcode según plataforma destino

```bash
# Instalar fvm (macOS)
brew tap leoafarias/fvm
brew install fvm

# Instalar la versión de Flutter del proyecto
fvm install
fvm use
```

---

## Configuración inicial

### Variables de entorno

El proyecto usa `flutter_dotenv` — cada flavor carga su archivo `.env.*`:

| Archivo | Flavor |
|---------|--------|
| `.env.dev` | Desarrollo local |
| `.env.staging` | Staging |
| `.env.prod` | Producción |

Variables disponibles:

```env
ENV=dev
API_BASE_URL=http://10.0.2.2:8080/api   # emulador Android → host local
APP_NAME=Sadday DEV
```

> Para iOS simulator usar `http://localhost:8080/api` en `API_BASE_URL`.
> Para dispositivo físico usar la IP local de tu máquina.

### Generar código (Riverpod codegen)

```bash
fvm flutter pub run build_runner build --delete-conflicting-outputs
# o en modo watch durante desarrollo:
fvm flutter pub run build_runner watch --delete-conflicting-outputs
```

---

## Ejecución

```bash
# Flavor dev (emulador o dispositivo)
fvm flutter run --flavor dev -t lib/main_dev.dart

# Flavor staging
fvm flutter run --flavor staging -t lib/main_staging.dart

# Flavor prod (solo para verificación — no hace deploy)
fvm flutter run --flavor prod -t lib/main_prod.dart
```

---

## Build de distribución

```bash
# Android APK (dev)
fvm flutter build apk --flavor dev -t lib/main_dev.dart

# Android App Bundle (prod — para Play Store)
fvm flutter build appbundle --flavor prod -t lib/main_prod.dart --release

# iOS (prod — requiere Mac con Xcode)
fvm flutter build ios --flavor prod -t lib/main_prod.dart --release
```

---

## Tests

```bash
# Tests unitarios y de widget
fvm flutter test

# Tests de integración (requiere emulador/dispositivo conectado)
fvm flutter test integration_test/login_flow_test.dart
```

---

## Estructura de directorios

```
lib/
├── app.dart              # MaterialApp raíz, providers, tema
├── router.dart           # Rutas go_router con guards de autenticación
├── main_dev.dart         # Entry point flavor dev
├── main_staging.dart     # Entry point flavor staging
├── main_prod.dart        # Entry point flavor prod
├── core/
│   ├── api/              # Dio client, interceptores, cookie jar
│   │   └── interceptors/
│   │       ├── auth_interceptor.dart   # Bearer token + refresh proactivo con mutex
│   │       └── error_interceptor.dart  # Mapeo de errores HTTP a AppException
│   ├── auth/             # AuthState, AuthNotifier, JwtUtils
│   ├── config/           # AppConfig (dotenv), AppLogger
│   ├── security/         # Jailbreak detection, biometría
│   ├── storage/          # SecureStorage wrapper (Keychain/Keystore)
│   ├── theme/            # Tema Material 3
│   └── widgets/          # Componentes UI reutilizables (AppButton, AppInput…)
├── features/             # Un directorio por módulo de negocio
│   ├── auth/             # Login, 2FA TOTP, Country Challenge, recuperación password
│   ├── dashboard/        # Resumen del socio
│   ├── salidas/          # Salidas, inscripciones, dignidades
│   ├── montanas/         # Catálogo de montañas
│   ├── rutas/            # Rutas multi-actividad
│   ├── planificador/     # Planificador de salidas con recomendaciones
│   ├── informes/         # Informes post-salida
│   ├── actas/            # Actas de reunión
│   ├── socios/           # Gestión de socios (admin)
│   ├── estadisticas/     # Rankings y estadísticas
│   ├── contactos/        # Directorio de contactos
│   ├── notificaciones/   # Alertas y aprobaciones pendientes
│   ├── perfil/           # Perfil del socio, 2FA, cambio de contraseña
│   ├── admin/            # Gestión de usuarios (admin)
│   ├── reglamento/       # Reglamento del club (PDF)
│   └── teoria/           # Material teórico (PDF)
└── l10n/                 # Archivos de localización (ES / EN)
```

Cada feature sigue la misma estructura interna:

```
features/<modulo>/
├── data/           # Repositorios — llamadas Dio al backend
├── domain/
│   └── models/     # Modelos de dominio (fromJson/toJson)
└── presentation/
    ├── providers/  # Riverpod providers / notifiers
    ├── screens/    # Pantallas (Widgets de página completa)
    └── widgets/    # Widgets reutilizables dentro del feature
```

---

## Autenticación

- **Access token**: almacenado en `flutter_secure_storage` (Keychain iOS / Keystore Android con `unlocked` accessibility)
- **Refresh token**: cookie HttpOnly gestionada por `dio_cookie_manager` — persiste entre sesiones en disco mediante `CookieJar`
- **Refresh proactivo**: `AuthInterceptor` detecta tokens próximos a expirar antes de cada petición y hace refresh usando un mutex (`synchronized`) para evitar condiciones de carrera entre isolates/peticiones concurrentes
- **Refresh reactivo**: `ErrorInterceptor` captura 401 y dispara el flujo de refresh
- **2FA TOTP**: pantalla de código de 6 dígitos con `pinput`
- **Country Challenge**: pantalla de código enviado por email cuando se detecta un país nuevo
- **Biometría**: `local_auth` para desbloquear la sesión en lugar de re-ingresar credenciales

---

## Seguridad

- Tokens almacenados en **Keychain / Keystore** (`KeychainAccessibility.unlocked`) — nunca en SharedPreferences ni disco sin cifrar
- **Jailbreak / root detection** al iniciar la app (`flutter_jailbreak_detection`)
- Logger completamente silenciado en flavor `prod` — no se loguean tokens, contraseñas ni PII
- Mutex en `refresh_lock.dart` (`synchronized` package) previene que múltiples peticiones simultáneas roten el mismo refresh token
