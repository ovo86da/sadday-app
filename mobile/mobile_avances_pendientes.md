# Sadday Mobile — Avances y Pendientes

Spec completa: `docs/flutter-mobile-spec.md` | Referencia rápida: `mobile/CLAUDE.md`

---

## Fase 1 — Andamiaje del proyecto ✅ (Sesión 2 — 2026-05-18)

- [x] `flutter create` con package IDs correctos (`com.sadday.app`) + limpiar boilerplate
- [x] `pubspec.yaml` con todos los paquetes de la spec (pub resolvió versiones compatibles — ver pubspec.yaml para versiones exactas)
- [x] Estructura de carpetas `lib/` (feature-first según spec: 14 features + core/)
- [x] Flavors Android: `dev` / `staging` / `prod` en `build.gradle.kts` (IDs: com.sadday.app.dev / .staging / prod)
- [x] Flavors iOS: xcconfig por flavor en `ios/Flutter/` (Dev/Staging/Prod.xcconfig)
- [x] `.env.dev`, `.env.staging`, `.env.prod` + assets en pubspec
- [x] Entry points: `main_dev.dart`, `main_staging.dart`, `main_prod.dart`
- [x] `app.dart` y `router.dart` (stubs listos para Fase 2)
- [x] Seguridad Android: `allowBackup=false`, `network_security_config.xml` (no cleartext), permisos biometría
- [x] Seguridad iOS: `NSFaceIDUsageDescription` en Info.plist
- [x] `flutter analyze` → 0 errores

## Fase 2 — Capa `core/` ✅ (Sesión 2 — 2026-05-18)

- [x] `AppColors` + `AppTheme` + `AppTextStyles` (dark mode, paleta spec, Inter via google_fonts)
- [x] `AppConfig` + `AppLogger` (lee `.env`, silenciado en prod)
- [x] Widgets base (13): `AppButton`, `AppInput`, `AppCard`, `AppBadge`, `AppDialog`, `AppBottomSheet`, `AppTable`, `AppTabs`, `AppPinInput`, `AppAvatar`, `AppStatusBadge`, `AppEmptyState`, `AppLoadingOverlay`
- [x] `SecureStorageService` singleton (wrapper flutter_secure_storage, Keychain/Keystore)
- [x] `AppException` sealed class (Network, Unauthorized, Forbidden, Business, Server)
- [x] Dio client (`dioClientProvider`) + `cookieJarProvider` compartido
- [x] `AuthInterceptor` (Bearer token + refresh proactivo JWT exp-30s)
- [x] `ErrorInterceptor` (401→refresh+retry con mutex, 403, 4xx, 5xx → AppException tipado)
- [x] `RefreshLock` (mutex `synchronized` — MASVS-AUTH)
- [x] `UserModel` + `AuthState` sealed (Unauthenticated, Locked, PendingMfa, PendingCountryChallenge, Authenticated)
- [x] `JwtUtils.isExpiredWithBuffer` (solo verifica exp, sin validar firma)
- [x] `AuthNotifier` AsyncNotifier — auto-login via refresh token al iniciar
- [x] `AuthGuard` + `roleRedirect` para GoRouter
- [x] `router.dart` completo — todas las rutas de la spec con placeholders
- [x] `AppLifecycleObserver` (overlay privacidad iOS en background)
- [x] `InactivityNotifier` Riverpod Notifier (timeout 10 min → `onInactivityTimeout`)
- [x] `MainActivity.kt` con `FLAG_SECURE` (screenshots bloqueados en Android)
- [x] `flutter analyze` → 0 errores

## Fase 3 — Feature `auth` ✅ (Sesión 2 — 2026-05-18)

- [x] Refactor: `cookieJarProvider` → `cookie_jar_provider.dart`, nuevo `authDioProvider` en `auth_dio_provider.dart` (sin ciclos de importación)
- [x] `AuthRepository` + `AuthRemoteDataSource` (login, MFA verify, country challenge, forgot/reset password, complete registration)
- [x] `LoginNotifier` (Riverpod Notifier — estados: idle/loading/error)
- [x] `UnlockNotifier` (biometría via `local_auth`, contador de intentos, logout a los 3 fallos)
- [x] `LoginScreen` — reactive_forms, email/password, toggle visibilidad, error inline
- [x] `MfaScreen` — `AppPinInput` 6 dígitos, auto-submit al completar
- [x] `CountryChallengeScreen` — dropdown países
- [x] `ForgotPasswordScreen` — email + pantalla de éxito post-envío
- [x] `ResetPasswordScreen` — nueva contraseña + confirmación con mustMatch validator
- [x] `UnlockScreen` — biometría, avatar usuario, contador intentos, cerrar sesión
- [x] `CompleteRegistrationScreen` — nombre, apellido, password (registro por invitación)
- [x] `router.dart` actualizado con pantallas reales para todas las rutas de auth
- [x] `flutter analyze` → 0 errores (1 info de deprecación del framework, no afecta build)

**Nota:** QR setup para habilitar TOTP (2FA desde Perfil) queda para Fase 5 (Mi Perfil).

## Fase 4 — Features principales (todos los roles) ✅ (Sesión 3 — 2026-05-18)

- [x] Bottom Nav Bar + ShellRoute (5 ítems: Inicio, Salidas, Informes, Perfil, Más→Drawer)
- [x] Drawer completo con secciones y guards de rol
- [x] `core/api/paged_response.dart` — PagedResponse<T> genérico
- [x] `core/widgets/app_nav_shell.dart` — AppNavShell + AppDrawer
- [x] `core/widgets/app_paged_list.dart` — AppPagedList<T> (scroll infinito sin dep. en paquete v5)
- [x] `AppColors` + chart1…chart5 | `AppTextStyles` + titleSmall / titleMedium / titleLarge
- [x] Dashboard (KPIs, próximas salidas, cumpleaños, bar chart, pie chart)
- [x] Salidas (listado tabs Todas/Próximas/Mis, detalle + participantes)
- [x] Informes (tabs Pendientes/Todos, detalle con secciones, validación ADMIN/DIRECTIVO)
- [x] Montañas (listado con búsqueda, detalle)
- [x] Rutas (listado con búsqueda, detalle, banner permisos)
- [x] Actas (listado, detalle + asistentes, generar PDF)
- [x] Estadísticas (tabs Resumen/Rankings/Montañas, fl_chart bar+pie, filtros meses/top N)
- [x] Mi Perfil (tabs Datos/Seguridad/API Keys; 2FA QR; sesiones; clipboard 30s)
- [x] Router: ShellRoute envuelve todas las rutas autenticadas
- [x] `flutter analyze` → 0 errores

## Fase 5 — Features con permisos elevados (SECRETARIA / ADMIN / DIRECTIVO) ✅ (Sesión 4 — 2026-05-18)

- [x] Socios (lista paginada con búsqueda y filtros, tabs Lista/Invitaciones, detalle, CRUD, habilitación/inhabilitación, cambio rol/nivel/jefe-montaña, reenviar invitación, emergency reset, eliminar, exportar)
- [x] Crear/editar salidas (FAB para roles privilegiados, bottom sheet con nombre/fechas/nivel/capacidad) → `POST/PUT /v1/salidas`
- [x] Gestionar actas (FAB para ADMIN/SECRETARIA, importar `.md` con preview → confirmar) → `POST /v1/actas/importar`
- [x] Contactos (lista paginada con búsqueda, CRUD completo: crear/editar/eliminar) → `GET/POST/PUT/DELETE /v1/contactos`
- [x] Administración (4 tabs: Configuración/Auditoría/Seguridad/Usuarios; editar config, logs de auditoría paginados con filtros, eventos de seguridad paginados, lista de usuarios con acciones: desbloquear/cerrar-sesión/emergency-reset)
- [x] Router actualizado — placeholders de `/socios`, `/contactos`, `/admin` reemplazados con pantallas reales
- [x] `flutter analyze` → 0 errores

## Fase 6 — Calidad y publicación ✅ (Sesión 5 — 2026-05-18)

- [x] Tests unitarios — 53 tests, 0 fallos (jwt_utils, modelos: Socio/Salida/Contacto, repositories: auth/salidas/socios/contactos)
- [x] Tests de widget — 34 tests, 0 fallos (AppButton, AppBadge, AppInput, AppStatusBadge + SalidaStatusX)
- [x] Bug fix: `SalidaStatusX.fromString` no manejaba guión bajo (EN_CURSO → ENCURSO). Corregido con `.replaceAll('_', '')`
- [x] Tests de integración — `integration_test/login_flow_test.dart` (cold start, validación vacía, forgot password)
- [x] i18n ES/EN: `l10n.yaml` + `lib/l10n/app_es.arb` + `lib/l10n/app_en.arb` → generado con `flutter gen-l10n`
- [x] `LocaleNotifier` (Riverpod Notifier) — lee/escribe en `SharedPreferences` con key `app_locale`
- [x] `app.dart` actualizado con `localizationsDelegates` (AppLocalizations + Global*) + `locale: ref.watch(localeProvider)` + `supportedLocales`
- [x] Android signing: `signingConfigs { release { from env vars } }` en `build.gradle.kts` + `isMinifyEnabled=true` + `isShrinkResources=true` + ProGuard
- [x] `flutter analyze` → 0 errores, 0 warnings propios (19 info pre-existentes + 1 warning pre-existente en widget_test.dart del framework)

---

## Sesiones

### Sesión 1 — 2026-05-18
- Revisión del proyecto completo (backend ✅, frontend ✅, mcp ✅)
- Definición del plan de implementación mobile en 6 fases
- Creación de rama `feature/mobile-scaffold`
- Creado este archivo `mobile_avances_pendientes.md`
- Flutter instalado durante la sesión — pendiente confirmar path para que Claude lo detecte en su subshell

### Sesión 2 — 2026-05-18
- Flutter confirmado en `/Users/david/fvm/default/bin/flutter` (v3.44.0 / Dart 3.12.0)
- **Fases 1, 2 y 3 completadas al 100%** — ver checklists arriba
- Nota versiones: algunas versiones de la spec no existían en pub.dev (riverpod_annotation resolvió en 4.0.2, fl_chart en 1.2.0, etc.) — ver `pubspec.yaml` para versiones reales
- iOS SPM warnings: `flutter_jailbreak_detection` y `file_picker` no tienen soporte SPM todavía (solo warnings, no bloquea build)

### Sesión 3 — 2026-05-18
- **Fase 4 completada al 100%** — ver checklist arriba
- `infinite_scroll_pagination` v5 tiene API rota incompatible con docs v4. Se implementó `AppPagedList<T>` propio (scroll infinito con ScrollController) para evitar la dependencia rota.
- `AppColors.chart1…chart5` y `AppTextStyles.titleSmall/Medium/Large` añadidos (faltaban en Fase 2)
- ShellRoute + AppNavShell + AppDrawer: navegación completa con bottom nav de 5 ítems y drawer lateral con guards de rol
- **Siguiente sesión:** Fase 5 — features con permisos elevados (Socios, crear/editar salidas, informes, actas, Contactos, Administración)

### Sesión 4 — 2026-05-18
- **Fase 5 completada al 100%** — ver checklist arriba
- Socios: lista con filtros multi-criterio (rol, estado, tipo), detalle completo con historial de habilitación y cuotas, CRUD + acciones de administración
- SalidaFormSheet: bottom sheet con fecha picker nativo + nivel dropdown + capacidad
- Actas: FAB con import .md via `file_picker`, preview antes de confirmar
- Contactos: lista paginada + buscador + CRUD completo con bottom sheet
- Admin: 4 tabs — config editable (alertDialog), auditoría paginada con filtros, security events paginados, usuarios-auth con acciones
- Router: `/socios/:id`, `/contactos`, `/admin` conectados a pantallas reales
- API del proyecto — correcciones de nombres de parámetros respecto a spec (AppInput usa `hint`/`label` no `hintText`/`labelText`; AppAvatar usa `name` no `initials`; AppButton usa `loading` no `isLoading`; showAppDialog no AppDialog.show)
- **Siguiente sesión:** Fase 6 — tests unitarios (70%), golden tests, build release con ofuscación, signing, i18n ES/EN

### Sesión 5 — 2026-05-18
- **Fase 6 completada al 100%** — ver checklist arriba
- 87 tests totales (53 unit + 34 widget), todos en verde
- Bug encontrado y corregido por los tests: `SalidaStatusX.fromString('EN_CURSO')` retornaba `planificada` (fallab porque `enCurso.name.toUpperCase()` = `ENCURSO` ≠ `EN_CURSO`). Fix: strip underscores antes de comparar.
- i18n: 27 claves en ES y EN. `LocaleNotifier` persiste preferencia en SharedPreferences. `AppLocalizations.delegate` registrado en `MaterialApp.router`.
- Android signing: usa 4 variables de entorno (`ANDROID_KEYSTORE_PATH`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) — nunca commitear keystore al repo.
- Build release: `flutter build appbundle --flavor prod -t lib/main_prod.dart --release --obfuscate --split-debug-info=build/debug-info/`
- Integración test en `integration_test/login_flow_test.dart` (requiere emulador/dispositivo para correr)
