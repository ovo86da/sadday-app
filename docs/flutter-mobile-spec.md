# Sadday App — Especificación Flutter Mobile (iOS & Android)

## Índice

1. [Visión general](#1-visión-general)
2. [Stack tecnológico](#2-stack-tecnológico)
3. [Sistema de diseño](#3-sistema-de-diseño)
4. [Arquitectura y estructura de carpetas](#4-arquitectura-y-estructura-de-carpetas)
5. [Autenticación y sesión](#5-autenticación-y-sesión)
6. [Navegación y estructura de pantallas](#6-navegación-y-estructura-de-pantallas)
7. [Pantallas — especificación completa](#7-pantallas--especificación-completa) *(18 pantallas + API Keys en Perfil)*
8. [API — referencia completa de endpoints](#8-api--referencia-completa-de-endpoints)
9. [Estado global](#9-estado-global)
10. [Permisos por rol](#10-permisos-por-rol)
11. [Consideraciones mobile-específicas](#11-consideraciones-mobile-específicas)
12. [Seguridad](#12-seguridad)
13. [Requisitos adicionales de seguridad (OWASP MASVS 2.x / MITRE ATT&CK)](#13-requisitos-adicionales-de-seguridad-owasp-masvs-2x--mitre-attck)
14. [Consideraciones de clean code](#14-consideraciones-de-clean-code)

---

## 1. Visión general

Aplicación mobile **Club Sadday** para iOS y Android desarrollada en Flutter.  
Replica toda la funcionalidad del frontend web (React) adaptada a mobile, con la adición de **autenticación biométrica** (huella dactilar / Face ID según el dispositivo).

**Principios:**
- Dark mode por defecto (igual que el web)
- Misma paleta de colores que el web
- Mismo backend REST — no se requieren cambios en el API
- Biometría como mecanismo de desbloqueo rápido (no reemplaza el login inicial)
- UI adaptada a mobile: navegación por bottom nav bar, drawers, sheets

---

## 2. Stack tecnológico

| Categoría | Paquete | Versión mínima | Propósito |
|---|---|---|---|
| **HTTP** | `dio` | ^6.0.x | Cliente HTTP, interceptores |
| **Cookies** | `dio_cookie_manager` + `cookie_jar` | ^4.x / ^5.x | Persistir refresh token (cookie HttpOnly) |
| **Secure storage** | `flutter_secure_storage` | ^10.x.x | Guardar access token y flag biométrico |
| **Biometría** | `local_auth` | ^2.5.x | Huella / Face ID |
| **Estado** | `flutter_riverpod` | ^3.x.x | Estado global reactivo (v3 estable) |
| **Router** | `go_router` | ^16.x.x | Navegación declarativa con guards |
| **Formularios** | `reactive_forms` | ^18.x | Validación reactiva |
| **Caché de datos** | `riverpod` AsyncNotifier | — | Equivalente a React Query |
| **Gráficos** | `fl_chart` | ^0.70.x | Bar charts, pie charts (estadísticas) |
| **QR** | `qr_flutter` | ^4.2.x | Mostrar QR para setup MFA |
| **OTP input** | `pinput` | ^5.0.x | Input de 6 dígitos para MFA/challenge |
| **Notificaciones** | `awesome_snackbar_content` | ^0.2.x | Toasts equivalentes a `sonner` |
| **Íconos** | `lucide_icons_flutter` | ^1.x | Mismo set de íconos que el web (Official) |
| **PDF viewer** | `syncfusion_flutter_pdfviewer` | ^29.x | Ver PDFs de informes/actas (v29 - 2026) |
| **Fecha/hora** | `intl` | ^0.20.x | Formateo localizado |
| **Infinite scroll** | `infinite_scroll_pagination` | ^5.x | Tablas paginadas |
| **Env** | `flutter_dotenv` | ^5.2.x | Variables de entorno (base URL) |

---

## 3. Sistema de diseño

### 3.1 Dark mode (por defecto)

La app debe iniciarse siempre en dark mode. Implementar soporte para light mode como opcional en ajustes del perfil.

### 3.2 Paleta de colores

Traducción directa de los tokens CSS del web (formato OKLCH → HEX aproximado):

```dart
// lib/core/theme/app_colors.dart

class AppColors {
  // Dark mode (por defecto)
  static const background     = Color(0xFF111111); // oklch(0.145 0 0)
  static const foreground     = Color(0xFFFAFAFA); // oklch(0.985 0 0)
  static const card           = Color(0xFF111111);
  static const cardForeground = Color(0xFFFAFAFA);
  static const primary        = Color(0xFF6B7FD4); // oklch(0.60 0.18 250) — azul-violeta
  static const primaryFg      = Color(0xFF111111);
  static const secondary      = Color(0xFF3A3A3A); // oklch(0.269 0 0)
  static const secondaryFg    = Color(0xFFFAFAFA);
  static const muted          = Color(0xFF3A3A3A);
  static const mutedFg        = Color(0xFFAAAAAA); // oklch(0.708 0 0)
  static const accent         = Color(0xFF3A3A3A);
  static const destructive    = Color(0xFFE5534B); // oklch(0.577 0.245 27.325) — rojo
  static const border         = Color(0xFF3A3A3A); // oklch(0.269 0 0)
  static const input          = Color(0xFF3A3A3A);
  static const ring           = Color(0xFF6B7FD4);
  static const sidebar        = Color(0xFF1C1C1C); // oklch(0.18 0 0)

  // Charts
  static const chart1 = Color(0xFF6B7FD4); // azul-violeta
  static const chart2 = Color(0xFF4CAF8A); // verde
  static const chart3 = Color(0xFFD4A84B); // ámbar
  static const chart4 = Color(0xFF9B6BD4); // violeta
  static const chart5 = Color(0xFFD46B6B); // rojo-cálido

  // Estados de salida
  static const estadoPlanificada = Color(0xFFD4A84B); // amber
  static const estadoEnCurso     = Color(0xFF6B7FD4); // primary
  static const estadoRealizada   = Color(0xFF4CAF8A); // emerald
  static const estadoCancelada   = Color(0xFFE5534B); // destructive
}
```

### 3.3 Tipografía

Fuente: **Inter** (igual que el web).

```dart
// pubspec.yaml — agregar Google Fonts
// google_fonts: ^6.x

TextTheme buildTextTheme() => GoogleFonts.interTextTheme(ThemeData.dark().textTheme);
```

### 3.4 Bordes y radios

```dart
static const double radiusSm = 6.0;  // radius - 4px
static const double radiusMd = 8.0;  // radius - 2px
static const double radiusLg = 10.0; // radius (0.625rem)
static const double radiusXl = 14.0; // radius + 4px
```

### 3.5 Componentes base reutilizables

Implementar como widgets propios en `lib/core/widgets/`:

- `AppButton` — variantes: primary, secondary, destructive, outline, ghost
- `AppInput` — con label, error, suffix/prefix icons
- `AppCard` — rounded-xl, border, bg card
- `AppBadge` — variantes: default, secondary, destructive, outline
- `AppDialog` — modal genérico con título, cuerpo y acciones
- `AppBottomSheet` — sheet deslizable desde abajo
- `AppTable` — lista paginada con infinite scroll
- `AppTabs` — tabs horizontal
- `AppPinInput` — 6 dígitos para MFA/challenge (usar `pinput`)
- `AppAvatar` — avatar con iniciales o foto
- `AppStatusBadge` — badge de estado con color semántico
- `AppEmptyState` — ilustración + mensaje cuando no hay datos
- `AppLoadingOverlay` — indicador de carga global

---

## 4. Arquitectura y estructura de carpetas

Arquitectura: **Feature-first** con Riverpod como capa de estado.

```
lib/
├── main.dart
├── app.dart                        # MaterialApp + GoRouter + Providers
├── core/
│   ├── api/
│   │   ├── api_client.dart         # Configuración Dio + interceptores
│   │   ├── api_error.dart          # Manejo de errores
│   │   └── endpoints.dart          # Constantes de URLs
│   ├── auth/
│   │   ├── auth_service.dart       # Login, refresh, logout, biometría
│   │   ├── auth_provider.dart      # Riverpod provider del estado auth
│   │   └── auth_guard.dart         # GoRouter redirect guard
│   ├── storage/
│   │   └── secure_storage.dart     # Wrapper de flutter_secure_storage
│   ├── theme/
│   │   ├── app_colors.dart
│   │   ├── app_theme.dart
│   │   └── app_text_styles.dart
│   └── widgets/                    # Componentes reutilizables
├── features/
│   ├── auth/
│   │   ├── login/
│   │   ├── forgot_password/
│   │   ├── reset_password/
│   │   └── registro/
│   ├── dashboard/
│   ├── socios/
│   ├── montanas/
│   ├── rutas/
│   ├── salidas/
│   ├── informes/
│   ├── actas/
│   ├── estadisticas/
│   ├── planificador/
│   ├── contactos/
│   ├── admin/
│   └── perfil/
└── router.dart
```

Cada feature sigue la estructura:

```
features/salidas/
├── data/
│   ├── salidas_repository.dart     # Llamadas a la API
│   └── salidas_remote_datasource.dart
├── domain/
│   └── models/                     # Modelos tipados
├── presentation/
│   ├── providers/                  # Riverpod AsyncNotifier/StateNotifier
│   ├── screens/                    # Pantallas completas
│   └── widgets/                    # Widgets propios del feature
```

---

## 5. Autenticación y sesión

### 5.1 Estrategia de tokens en mobile

| Token | Dónde se guarda | Por qué |
|---|---|---|
| **Access token** | Solo en memoria (Riverpod) | Vida corta (~15 min). No persistir en disco: si el token no fue revocado aún y alguien extrae el almacenamiento, tendría acceso válido. Se pierde al cerrar la app — correcto. |
| **Refresh token** | `flutter_secure_storage` | Larga duración, necesita sobrevivir reinicios. SecureStorage usa Keychain (iOS) / Keystore (Android), cifrado a nivel hardware. |
| **Flag biométrico** | `flutter_secure_storage` | Dato de configuración de seguridad, debe estar protegido. |

**Importante:** NO usar `PersistCookieJar` + `FileStorage` para el refresh token. `FileStorage` guarda archivos de texto plano sin cifrar. En su lugar, interceptar la cookie `Set-Cookie` del backend y almacenar el valor en `flutter_secure_storage`, reinyectándola manualmente en cada request.

```dart
// lib/core/api/api_client.dart

final dio = Dio(BaseOptions(
  baseUrl: Env.apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
    'X-Sadday-Client': 'mobile',
  },
));

// Interceptor que inyecta el refresh token como cookie en cada request
dio.interceptors.add(InterceptorsWrapper(
  onRequest: (options, handler) async {
    final refreshToken = await secureStorage.read('refresh_token');
    if (refreshToken != null) {
      options.headers['Cookie'] = 'refresh_token=$refreshToken';
    }
    final accessToken = authState.accessToken; // solo desde memoria
    if (accessToken != null) {
      options.headers['Authorization'] = 'Bearer $accessToken';
    }
    handler.next(options);
  },
  onResponse: (response, handler) async {
    // Capturar Set-Cookie del backend y guardar en SecureStorage
    final setCookie = response.headers['set-cookie'];
    if (setCookie != null) {
      final tokenValue = _parseRefreshTokenFromCookie(setCookie);
      if (tokenValue != null) {
        await secureStorage.write('refresh_token', tokenValue);
      }
    }
    handler.next(response);
  },
));
```

El **access token** vive exclusivamente en el provider de Riverpod en memoria, nunca se escribe en disco.

### 5.2 Flujo de login completo

```
Usuario ingresa usuario + contraseña
        │
        ▼
POST /v1/auth/login
        │
   ┌────┴─────────────────────────┐
   │ 200 OK                        │ 202 + challengeToken     │ 202 + countryChallengeToken
   ▼                               ▼                           ▼
Login exitoso               Pantalla MFA               Pantalla Country Challenge
Guardar accessToken         (código TOTP)              (código por email, 15 min)
SOLO en AuthProvider        POST /auth/mfa/login        POST /auth/country-challenge/verify
(memoria)                          │                           │
Guardar refreshToken               └───────────────────────────┘
en SecureStorage                           │ 200 OK
                                           ▼
                                   Guardar accessToken en memoria
                                   Guardar refreshToken en SecureStorage
                                   Ir a Dashboard
```

### 5.3 Autenticación biométrica

La biometría **no reemplaza** el login inicial. Funciona como un "desbloqueo rápido" cuando hay una sesión válida guardada.

**Flujo de activación (primera vez):**
1. Login normal exitoso → app pregunta "¿Habilitar acceso con huella/Face ID?"
2. Usuario acepta → se guarda flag `biometric_enabled = true` en `SecureStorage`
3. El refresh token ya está persistido en `flutter_secure_storage`

**Flujo de desbloqueo biométrico:**
```
App en foreground después de background/cierre
        │
        ▼
¿biometric_enabled = true?
        │ Sí
        ▼
Mostrar pantalla de desbloqueo biométrico
        │
        ▼
local_auth.authenticate(localizedReason: "Desbloquear Sadday")
        │
   ┌────┴────┐
   │ Éxito   │ Fallo/cancelar
   ▼         ▼
POST /v1/auth/refresh    Mostrar login normal
(cookies automáticas)
        │
   ┌────┴────┐
   │ 200 OK  │ 401 (expirado)
   ▼         ▼
Nuevo        Limpiar sesión
accessToken  → Login normal
en memoria
```

**Implementación:**

```dart
// lib/core/auth/auth_service.dart

Future<bool> tryBiometricUnlock() async {
  final isBiometricEnabled = await secureStorage.read('biometric_enabled') == 'true';
  if (!isBiometricEnabled) return false;

  final canAuthenticate = await localAuth.canCheckBiometrics;
  if (!canAuthenticate) return false;

  final authenticated = await localAuth.authenticate(
    localizedReason: 'Desbloquear Sadday App',
    options: const AuthenticationOptions(biometricOnly: false),
  );
  if (!authenticated) return false;

  // Usar refresh token (cookie) para obtener nuevo access token
  return await refreshSession();
}
```

**Consideraciones:**
- En iOS: solicitar permisos `NSFaceIDUsageDescription` en Info.plist
- En Android: agregar `USE_BIOMETRIC` y `USE_FINGERPRINT` en AndroidManifest.xml
- Si el dispositivo no soporta biometría: no mostrar la opción
- Si el refresh token expiró: forzar login completo y desactivar biometría hasta que re-logueen

### 5.4 Auto-refresh de access token

El interceptor de Dio maneja el 401 automáticamente, igual que el web:

```dart
// En el interceptor de errores de Dio
if (error.response?.statusCode == 401 && !isRetry) {
  final refreshed = await authService.refreshSession();
  if (refreshed) {
    // Reintentar la request original con el nuevo token
    return dio.fetch(options..headers['Authorization'] = 'Bearer $newToken');
  } else {
    // Refresh falló → limpiar sesión → ir a login
    ref.read(authProvider.notifier).clearAuth();
    router.go('/login');
  }
}
```

### 5.5 Validaciones de contraseña

Reglas (idénticas al web):
- Mínimo 12 caracteres
- Al menos 1 mayúscula
- Al menos 1 minúscula
- Al menos 1 número
- Al menos 1 símbolo

Mostrar requisitos en tiempo real con checkmarks mientras el usuario escribe.

### 5.6 Headers requeridos

```dart
'X-Sadday-Client': 'mobile'    // Para que el backend identifique la plataforma
'Authorization': 'Bearer $accessToken'  // En todas las requests autenticadas
```

---

## 6. Navegación y estructura de pantallas

### 6.1 Rutas públicas (sin autenticación)

| Ruta | Pantalla |
|---|---|
| `/login` | LoginScreen |
| `/forgot-password` | ForgotPasswordScreen |
| `/reset-password` | ResetPasswordScreen |
| `/registro/completar` | RegistroCompletarScreen |

### 6.2 Estructura de navegación autenticada

En mobile se usa **Bottom Navigation Bar** (5 destinos máximo) + **Drawer** para el resto:

**Bottom Nav Bar:**
1. Dashboard
2. Salidas
3. Informes
4. Mi Perfil
5. Más (abre Drawer completo)

**Drawer completo (igual al sidebar web):**

```
General
  └── Dashboard

Club
  ├── Socios              [ADMIN, SECRETARIA, DIRECTIVO]
  ├── Montañas
  ├── Rutas
  ├── Salidas
  ├── Notificaciones      [ADMIN, SECRETARIA, DIRECTIVO]
  └── Niveles de acceso

Análisis
  ├── Planificador
  └── Estadísticas

Documentos
  ├── Informes
  └── Actas

Sistema
  ├── Mi Perfil
  ├── Contactos           [ADMIN, SECRETARIA]
  └── Administración      [ADMIN, SECRETARIA, DIRECTIVO]
```

### 6.3 Guards de navegación

```dart
// router.dart — redirect en GoRouter

redirect: (context, state) {
  final isAuthenticated = ref.read(authProvider).isAuthenticated;
  final isPublicRoute = ['/login', '/forgot-password', ...].contains(state.uri.path);

  if (!isAuthenticated && !isPublicRoute) return '/login';
  if (isAuthenticated && state.uri.path == '/login') return '/dashboard';

  // Guard de rol
  final userRole = ref.read(authProvider).user?.rol;
  if (state.uri.path == '/socios' && !['ADMIN','SECRETARIA','DIRECTIVO'].contains(userRole)) {
    return '/403';
  }
  return null;
}
```

---

## 7. Pantallas — especificación completa

---

### 7.1 Login

**Ruta:** `/login`  
**Acceso:** público

**Descripción:** Formulario multi-paso. El paso activo cambia según la respuesta del backend.

**Paso 1 — Credenciales:**
- Campo: nombre de usuario (min 4, max 100 chars)
- Campo: contraseña (min 12, con ícono toggle mostrar/ocultar)
- Botón: Ingresar → `POST /v1/auth/login`
- Link: "¿Olvidaste tu contraseña?" → `/forgot-password`

**Paso 2 — MFA (si el backend responde 202 + `challengeToken`):**
- Instrucción: "Ingresa el código de tu app autenticadora"
- `AppPinInput` de 6 dígitos (autosubmit al completar)
- Botón: Verificar → `POST /v1/auth/mfa/login`

**Paso 3 — Country Challenge (si el backend responde 202 + `countryChallengeToken`):**
- Instrucción: "Detectamos un acceso desde un país nuevo. Ingresa el código enviado a tu correo."
- `AppPinInput` de 6 dígitos
- Contador regresivo de 15 minutos
- Botón: Verificar → `POST /v1/auth/country-challenge/verify`

**Post-login exitoso:**
- Si `biometric_enabled` no está seteado: mostrar bottom sheet "¿Habilitar acceso biométrico?"
- Navegar a `/dashboard`

---

### 7.2 Recuperación de contraseña

**Ruta:** `/forgot-password`  
**Acceso:** público

- Campo: correo electrónico
- Botón: Enviar → `POST /v1/auth/forgot-password`
- Mensaje de éxito: "Si el correo existe, recibirás un enlace" (nunca revelar si existe)

---

### 7.3 Reset de contraseña

**Ruta:** `/reset-password`  
**Acceso:** público (requiere `?token=` en query params o deep link)

- Campo: nueva contraseña
- Campo: confirmar contraseña
- Indicadores visuales de requisitos en tiempo real (checkmarks animados)
- Botón: Cambiar contraseña → `POST /v1/auth/reset-password`
- Post-éxito: navegar a `/login`

---

### 7.4 Completar registro / Activación de cuenta

**Ruta:** `/registro/completar`  
**Acceso:** público (requiere `?token=` en query params o deep link)

Primero consultar `GET /v1/registro/token-info?token=...` para determinar el tipo:

- **Tipo legacy:** solo campos de credenciales (usuario + contraseña)
- **Tipo pre-registro:** credenciales + datos personales (teléfono, sangre, dirección, contactos emergencia)
- **Tipo CSV import:** credenciales + nombre/apellido readonly (ya precargados)

Botón: Activar cuenta → `POST /v1/registro/complete`

---

### 7.5 Dashboard

**Ruta:** `/dashboard`  
**Acceso:** todos los autenticados

**Layout:** scroll vertical con tarjetas apiladas.

**Tarjeta 1 — Bienvenida:**
- "Bienvenido, {nombre}"
- Rol y nivel técnico del usuario

**Tarjeta 2 — KPIs (3 chips horizontales):**
- Total socios activos → `GET /v1/socios?size=1` (usar totalElements)
- Salidas planificadas → `GET /v1/salidas?estado=PLANIFICADA`
- Salidas completadas → `GET /v1/salidas?estado=REALIZADA`

**Tarjeta 3 — Mis próximas salidas como jefe** (solo si tiene salidas a jefear):
- Lista de salidas donde es jefe de salida
- Chip de alerta si hay aprobaciones pendientes
- → `GET /v1/salidas/alertas-sin-jefe`

**Tarjeta 4 — Aprobaciones pendientes** (ADMIN, SECRETARIA, DIRECTIVO):
- Lista de participantes que requieren aprobación de riesgo
- → `GET /v1/salidas/aprobaciones-pendientes`

**Tarjeta 5 — Próximas salidas (5):**
- Lista de las próximas 5 salidas planificadas
- Tap → detalle de salida

**Tarjeta 6 — Cumpleaños hoy:**
- Lista de socios que cumplen años
- → `GET /v1/notificaciones/cumpleanos`

**Tarjeta 7 — Distribución de socios (Pie chart):**
- Por nivel técnico
- → `GET /v1/estadisticas/dashboard`

**Tarjeta 8 — Salidas por mes (Bar chart):**
- Filtros: últimos 6 / 12 / 24 meses
- → `GET /v1/estadisticas/dashboard?meses=12`

---

### 7.6 Mi Perfil

**Ruta:** `/perfil`  
**Acceso:** todos los autenticados

**Sección 1 — Datos personales:**
- Nombre completo (readonly)
- Cédula (readonly)
- Correo (editable)
- Teléfono (editable)
- Tipo de sangre (editable)
- Dirección (editable)
- Rol y tipo de socio (readonly)
- Estado habilitación (readonly, con badge de color)
- Nivel técnico (readonly)

**Sección 2 — Contactos de emergencia:**
- Lista de contactos (nombre, teléfono, relación)
- Añadir / editar / eliminar

Endpoint: `GET /v1/socios/me` → `PATCH /v1/socios/me`

**Sección 3 — Seguridad:**

*Cambiar contraseña:*
- Contraseña actual + nueva contraseña + confirmar
- Si tiene 2FA: después de validar contraseña, pide código TOTP
- → `POST /v1/auth/change-password/verify` + `POST /v1/auth/change-password`

*Autenticación de dos factores:*
- Si deshabilitado: botón "Activar 2FA" → QR + campo código TOTP para confirmar
  - → `POST /v1/auth/mfa/setup` → muestra QR con `qr_flutter`
  - → `POST /v1/auth/mfa/confirm`
- Si habilitado: botón "Desactivar 2FA" → pide código TOTP actual
  - → `DELETE /v1/auth/mfa`
- → `GET /v1/auth/mfa/status`

*Autenticación biométrica:*
- Toggle "Acceso con huella/Face ID"
- Al activar: solicitar biometría para confirmar, luego guardar flag en SecureStorage
- Al desactivar: limpiar flag

**Sección 4 — Sesiones activas:**
- Lista de dispositivos/sesiones con: plataforma (WEB/MOBILE), IP, última actividad
- Botón "Cerrar" en cada sesión → `DELETE /v1/auth/sessions/{id}`
- Botón "Cerrar todas las demás" → `DELETE /v1/auth/sessions/others`
- Botón "Reportar actividad sospechosa" → `POST /v1/auth/report-suspicious`
- → `GET /v1/auth/sessions`

**Sección 5 — Historial de salidas:**
- Lista de salidas en las que participó
- Visible dentro del perfil

**Botón Cerrar sesión** (al final o en el header):
- → `POST /v1/auth/logout`
- Limpiar SecureStorage + cookieJar + AuthProvider
- Navegar a `/login`

---

### 7.7 Socios

**Ruta:** `/socios`  
**Acceso:** ADMIN, SECRETARIA, DIRECTIVO

**Pantalla principal — Lista de socios:**
- Buscador (por nombre/cédula)
- Filtros: rol, estado habilitación, tipo socio
- Lista paginada con infinite scroll
- Cada ítem: avatar con iniciales, nombre, rol (badge), estado (badge color)
- Tap → pantalla de detalle del socio
- FAB (floating action button): "Nuevo socio" (ADMIN, SECRETARIA)
- → `GET /v1/socios` (paginado)

**Pestaña: Invitaciones pendientes:**
- Lista de invitaciones sin activar
- Acciones: reenviar invitación, eliminar invitación
- → `GET /v1/socios/invitaciones`

**Pantalla de detalle / edición del socio:**
- Todos los campos del socio (nombre, cédula, correo, teléfono, dirección, sangre, nivel, rol)
- Historial de habilitación
- Cuotas: lista + agregar pago + eliminar
- Acciones (según rol del actor):
  - Habilitar / Inhabilitar → `PATCH /socios/{id}/habilitar|inhabilitar`
  - Cambiar rol → `PATCH /socios/{id}/rol`
  - Cambiar nivel técnico → `PATCH /socios/{id}/nivel-tecnico`
  - Toggle Jefe de Montaña → `PATCH /socios/{id}/jefe-montana`
  - Reenviar invitación → `POST /socios/{id}/reenviar-invitacion`
  - Emergency reset → `POST /socios/{id}/emergency-reset`
  - Eliminar → `DELETE /socios/{id}` (con confirmación)

**Bottom sheet — Crear / Editar socio:**
- Campos: nombre, apellido, cédula, correo, teléfono, nivel técnico, tipo socio, fecha nacimiento
- → `POST /v1/socios` | `PUT /v1/socios/{id}`

**Exportar socios (ADMIN, SECRETARIA, DIRECTIVO):**
- Botón "Exportar" con diálogo de opciones: tipo (CSV / lista PDF / hoja de firmas PDF), campos a incluir y su orden, filtros (tipo socio, estado habilitación, excluir admin)
- → `GET /v1/socios/exportar/csv` | `GET /v1/socios/exportar/pdf` | `GET /v1/socios/exportar/pdf/firmas`
- El PDF lista acepta máximo 6 columnas; la hoja de firmas es formato fijo (cédula, apellido, nombre, firma)

**Importación CSV:**
- Opción en el menú → seleccionar archivo CSV
- Preview de los datos antes de confirmar
- → `POST /v1/socios/importar/preview` → `POST /v1/socios/importar/confirmar`

---

### 7.8 Montañas

**Ruta:** `/montanas`  
**Acceso:** todos los autenticados

- Lista de montañas con buscador
- Cada ítem: nombre, altitud, país, número de rutas
- Tap → detalle de montaña (niveles de acceso por clasificación)
- FAB: nueva montaña (ADMIN, SECRETARIA)
- → `GET /v1/mountains`

**Detalle / Edición de montaña:**
- Nombre, descripción, altitud, coordenadas, país
- Tabla de acceso por nivel (qué clasificaciones pueden hacer cumbre)
- Acciones: editar, eliminar
- → `GET /v1/mountains/{id}`, `PUT /v1/mountains/{id}`, `DELETE /v1/mountains/{id}`

---

### 7.9 Rutas

**Ruta:** `/rutas`  
**Acceso:** todos los autenticados

- Lista paginada de rutas
- Cada ítem: nombre, montaña, dificultad, distancia, altitud máxima
- Tap → detalle (documentos/permisos requeridos, descripción técnica)
- FAB: nueva ruta (ADMIN, SECRETARIA, DIRECTIVO)
- → `GET /v1/rutas`

**Detalle / Edición de ruta:**
- Todos los campos de la ruta (nombre, tipo actividad, montaña, sector, longitud, desnivel, duración, nivel mínimo, escalas técnicas)
- Botón Aprobar / Rechazar (ADMIN, DIRECTIVO)
- → `GET /v1/rutas/{id}`, `PUT /v1/rutas/{id}`, `PATCH /v1/rutas/{id}/aprobar`, `DELETE /v1/rutas/{id}`

**Documentos de permiso (cuando `requierePermisos = true`):**
- Banner ámbar: "Esta ruta requiere documentación de acceso"
- Lista de documentos subidos (nombre, tipo, fecha)
- Botón descargar cada documento → `GET /v1/rutas/{id}/documentos/{docId}/descargar`
- FAB/botón subir documento (ADMIN, SECRETARIA, DIRECTIVO): seleccionar PDF/Word/Excel vía `file_picker`, máx 10 MB → `POST /v1/rutas/{id}/documentos` (multipart)
- Eliminar documento (ADMIN, SECRETARIA, DIRECTIVO) → `DELETE /v1/rutas/{id}/documentos/{docId}`
- → `GET /v1/rutas/{id}/documentos`

**Contactos vinculados a la ruta:**
- Lista: nombre, tipo contacto (GUIA / TRANSPORTE / REFUGIO / OTRO), teléfono
- Vincular contacto: selector con búsqueda `GET /v1/contactos/buscar?q=...` + tipo contacto → `POST /v1/rutas/{id}/contactos`
- Desvincular → `DELETE /v1/rutas/{id}/contactos/{contactoRutaId}`
- (ADMIN, SECRETARIA, DIRECTIVO)
- → `GET /v1/rutas/{id}/contactos`

---

### 7.10 Salidas

**Ruta:** `/salidas`  
**Acceso:** todos los autenticados (con vistas distintas por rol)

**Vista para ADMIN / SECRETARIA / DIRECTIVO:**

*Tabs:*
1. **Todas** — tabla completa con filtros (estado, fecha, búsqueda)
2. **Próximas** — estado PLANIFICADA / EN_CURSO
3. **Anteriores** — estado REALIZADA / CANCELADA
4. **Mis salidas** — donde el usuario es participante

*FAB:* Nueva salida

**Vista para SOCIO:**

*Tabs:*
1. **Próximas** — salidas disponibles para inscribirse
2. **Mis salidas** — salidas en las que está inscrito
3. **Anteriores** — historial

**Tarjeta de salida (en lista):**
- Nombre, fecha, montaña/ruta
- Badge de estado (PLANIFICADA / EN_CURSO / REALIZADA / CANCELADA)
- Nivel mínimo requerido
- Capacidad: X/Y inscritos
- Botón rápido: inscribirse (si socio y no inscrito)

**Pantalla de detalle de salida:**
- Todos los datos de la salida
- Lista de participantes con estado (INSCRITO / CONFIRMADO / NO_FUE / CANCELADO)
- Jefe de salida marcado con badge
- Dignidades de cada participante
- Aprobaciones de riesgo pendientes

*Acciones según rol:*
- Socio: inscribirse / cancelar inscripción
- Admin/Secretaria/Directivo:
  - Cambiar estado (PLANIFICADA → EN_CURSO → REALIZADA)
  - Cancelar salida (con motivo)
  - Eliminar salida (con motivo)
  - Cerrar/abrir inscripciones
  - Gestionar participantes (cambiar estado, designar jefe, agregar dignidades)
  - Aprobar/rechazar inscripciones con nivel insuficiente

**Bottom sheet — Crear / Editar salida:**
- Nombre, fecha inicio/fin, hora encuentro, hora estimada regreso
- Ruta (selector con búsqueda)
- Público objetivo, formato de salida
- Nivel mínimo requerido
- Capacidad máxima
- → `POST /v1/salidas` | `PUT /v1/salidas/{id}`

---

### 7.11 Notificaciones / Aprobaciones

**Ruta:** `/notificaciones`  
**Acceso:** ADMIN, SECRETARIA, DIRECTIVO

- Lista de participantes con nivel insuficiente que requieren aprobación
- Cada ítem: nombre del socio, salida, nivel del socio vs nivel requerido
- Acciones: Aprobar / Rechazar (con campo de motivo al rechazar)
- → `GET /v1/salidas/aprobaciones-pendientes`
- → `PATCH /v1/salidas/{id}/inscripciones/{id}/aprobacion-riesgo`

---

### 7.12 Niveles de acceso

**Ruta:** `/acceso-nivel`  
**Acceso:** todos los autenticados

- Matriz de montañas vs niveles de clasificación
- Toggle para cada combinación
- → `GET /v1/mountains/acceso-por-nivel`
- → `PUT /v1/mountains/acceso-por-nivel/{nivelSocioId}`

En mobile mostrar como lista expandible (accordeon) de montañas → niveles como chips seleccionables.

---

### 7.13 Informes

**Ruta:** `/informes`  
**Acceso:** todos los autenticados (con acciones según rol)

**Tabs:**
1. **Pendientes** — salidas REALIZADA sin informe completado (solo jefes de salida y admins)
2. **Todos** — historial completo de informes

**Lista:**
- Nombre de salida, fecha, estado del informe (pendiente / completado / validado)
- Si pendiente y es el jefe: badge de alerta

**Pantalla de detalle / creación del informe:**

*Sección horarios reales:*
- Hora salida del club, hora llegada montaña, hora cumbre, hora inicio descenso, hora llegada autos, hora regreso club
- Toggle "¿Se realizó?" y "¿Lograron la cumbre?"

*Sección condiciones:*
- Condiciones meteorológicas (texto)
- Crónica (texto largo)
- Observaciones (texto)
- Comentarios varios (texto)

*Sección transporte (segmentos de viaje):*
- Lista de tramos (origen → destino, tipo transporte, costo individual)
- Añadir / editar / eliminar tramos

*Sección alojamiento:*
- Toggle "¿Alquiló refugio?" + nombre + costo + contacto
- Toggle "¿Acampó?" + nombre + costo + contacto

*Sección guía externo:*
- Toggle "¿Alquiló guía?" + costo + contacto

*Sección autos:*
- Dónde se dejaron los autos (selector: parqueadero/calle/etc.) + descripción + link ubicación + costo parqueadero

*Sección costos:*
- Costo total del viaje
- Costo por persona

*Sección reconocimientos:*
- Lista de participantes destacados / amonestados
- Añadir: seleccionar participante, tipo (DESTACADO/AMONESTADO), motivo

*Validación del informe (DIRECTIVO / ADMIN):*
- Botón "Validar informe" → confirma con dialog
- → `PATCH /v1/informes/{salidaId}/validar`

*PDF:*
- Botón "Generar PDF" → `POST /v1/informes/{salidaId}/pdf`
- Botón "Descargar PDF" → `GET /v1/informes/{salidaId}/pdf` → abrir en visor PDF

Endpoints:
- `GET /v1/informes/pendientes-jefe`
- `GET /v1/informes/{salidaId}`
- `POST /v1/informes/{salidaId}`
- `PUT /v1/informes/{salidaId}`
- `PATCH /v1/informes/{salidaId}/validar`
- `POST /v1/informes/{salidaId}/reconocimientos`
- `DELETE /v1/informes/{salidaId}/reconocimientos/{id}`

---

### 7.14 Actas

**Ruta:** `/actas`  
**Acceso:** ADMIN, SECRETARIA (gestión), resto puede ver

- Lista paginada de actas de reunión
- Cada ítem: número, fecha, tipo (SOCIOS / DIRECTIVA), estado PDF
- Tap → detalle del acta
- FAB: nueva acta (ADMIN, SECRETARIA)

**Detalle del acta:**
- Fecha, tipo, número, descripción
- Lista de asistentes (con avatares)
  - Añadir/remover asistentes (ADMIN, SECRETARIA)
- Lista de informes de salida vinculados
  - Añadir/remover informes (ADMIN, SECRETARIA)
- Botones: Generar PDF, Descargar PDF (si ya existe)

**CSV import:**
- Seleccionar archivo Markdown (.md) con el contenido del acta
- Preview → confirmar
- → `POST /v1/actas/importar` → `POST /v1/actas/importar/confirmar`

---

### 7.15 Estadísticas

**Ruta:** `/estadisticas`  
**Acceso:** todos los autenticados

**Tabs:**

**Tab 1 — Dashboard / Resumen general:**
- KPI cards: total salidas, socios activos, cumbres logradas, tasa de éxito
- Bar chart: salidas por mes (filtros: últimos 6 / 12 / 24 meses)
- Pie chart: distribución de socios por nivel técnico
- Pie chart: salidas por tipo de actividad (ALPINISMO / ESCALADA / TREKKING / CICLISMO)
- → `GET /v1/estadisticas/dashboard?meses=12`
- → `GET /v1/estadisticas/club`

**Tab 2 — Estadísticas por período:**
- Selector de rango de fechas (fechaDesde / fechaHasta)
- Salidas del período: count, realizadas, canceladas
- Montañas visitadas en el período: lista con count de ascensos
- Rutas usadas en el período: lista con count de veces
- → `GET /v1/estadisticas/periodo/salidas`
- → `GET /v1/estadisticas/periodo/montanas`
- → `GET /v1/estadisticas/periodo/rutas`

**Tab 3 — Rankings:**
- Ranking de socios más activos (más participaciones)
- Ranking de socios con más cumbres
- Ranking de asistencia a reuniones
- Selector top N (10 / 20 / 50)
- → `GET /v1/estadisticas/rankings?top=10`
- → `GET /v1/estadisticas/reuniones/rankings?top=10&meses=12`

**Tab 4 — Montañas y rutas:**
- Ranking de montañas más visitadas (bar chart)
- Ranking de rutas más usadas
- → `GET /v1/estadisticas/ranking-montana-ruta`

**Tab 5 — Búsqueda avanzada de participantes:**
- Filtros: montaña, ruta, dignidad, nivel técnico, búsqueda por nombre
- Tabla de resultados: socio, salida, montaña, dignidad
- → `GET /v1/estadisticas/participantes`
- → `GET /v1/estadisticas/montana-ruta/buscar`

**Tab 6 — Historial de socio** (tap desde lista de socios o búsqueda):
- Estadísticas personales: total participaciones, cumbres logradas, veces jefe de salida
- Conteo por dignidad (guía, escoba, cronista…)
- Historial de salidas con resultado (cumbre / no cumbre / cancelada)
- → `GET /v1/estadisticas/socios/{socioId}`
- → `GET /v1/estadisticas/socios/{socioId}/actividad-total`

---

### 7.16 Planificador

**Ruta:** `/planificador`  
**Acceso:** todos los autenticados

El planificador evalúa una ruta específica y devuelve quién puede ir y qué equipo necesitan.

**Flujo de uso:**
1. Selector de ruta (búsqueda por nombre/montaña) → `GET /v1/rutas?q=...`
2. Al seleccionar la ruta → consultar `GET /v1/planificador/ruta/{rutaId}`
3. Mostrar resultado:
   - **Socios aptos:** lista de socios con nivel suficiente y habilitados, con su nivel técnico y clasificaciones
   - **Advertencias:** socios con nivel insuficiente (pueden ser aprobados con aprobación de riesgo)
   - **Equipo recomendado:** lista de equipos necesarios para la ruta
   - **Socios inhabilitados:** socios que no pueden inscribirse por inhabilitación
4. Desde la lista de socios aptos: acceso rápido a inscribirlo en una salida futura

---

### 7.17 Contactos

**Ruta:** `/contactos`  
**Acceso:** ADMIN, SECRETARIA

- Lista de contactos externos (empresas de transporte, guías, refugios)
- Buscador por nombre/teléfono
- CRUD: crear, editar, eliminar
- → `GET /v1/contactos`, `POST /v1/contactos`, `PUT /v1/contactos/{id}`, `DELETE /v1/contactos/{id}`

---

### 7.18 Administración

**Ruta:** `/admin`  
**Acceso:** ADMIN, SECRETARIA, DIRECTIVO

**Tabs:**

*1. Configuración del sistema:*
- Parámetros editables del sistema
- → `GET /v1/admin/config`, `PATCH /v1/admin/config`

*2. Auditoría:*
- Log de cambios en datos (quién, qué, cuándo)
- Filtros por entidad, actor, fecha
- → `GET /v1/admin/auditoria`

*3. Eventos de seguridad:*
- Log de login, dispositivos nuevos, países nuevos
- → `GET /v1/admin/security-events`

*4. Usuarios y acceso:*
- Lista de usuarios con su estado de acceso (ACTIVE / BLOCKED / EX_MEMBER / etc.)
- Acciones:
  - Cambiar estado de acceso → `PATCH /v1/admin/usuarios-auth/{id}/estado-acceso`
  - Desbloquear → `POST /v1/admin/usuarios-auth/{id}/desbloquear`
  - Cerrar sesión → `POST /v1/admin/usuarios-auth/{id}/cerrar-sesion`
  - Emergency reset → `POST /v1/socios/{id}/emergency-reset`
- → `GET /v1/admin/usuarios-auth`

### 7.19 API Keys (Perfil)

Las API keys se gestionan desde la sección de Perfil, no como pantalla independiente. Agregar una nueva pestaña "API Keys" dentro de `/perfil`.

**Acceso:** todos los autenticados (excepto usuarios con token readonly)

**Pestaña API Keys en Perfil:**
- Lista de API keys activas: nombre, fecha creación, último uso
- Botón "Crear API key": pide un nombre descriptivo → `POST /v1/profile/api-keys`
  - Al crear: mostrar el token generado **una sola vez** en un dialog con opción de copiar
  - Advertencia: "Guarda este token ahora, no podrás verlo de nuevo"
- Botón "Revocar" en cada key (con confirmación) → `DELETE /v1/profile/api-keys/{id}`
- → `GET /v1/profile/api-keys`

---

## 8. API — referencia completa de endpoints

**Base URL:** configurar en `.env` → `API_BASE_URL=https://api.sadday.club/api`

Todos los endpoints autenticados requieren:
```
Authorization: Bearer {accessToken}
X-Sadday-Client: mobile
```

### Autenticación
| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/v1/auth/login` | Login con credenciales |
| POST | `/v1/auth/mfa/login` | Verificar código TOTP |
| POST | `/v1/auth/country-challenge/verify` | Verificar código de país |
| POST | `/v1/auth/refresh` | Renovar access token (usa cookie) |
| POST | `/v1/auth/logout` | Cerrar sesión en el dispositivo actual |
| POST | `/v1/auth/logout-all` | Cerrar sesión en todos los dispositivos |
| POST | `/v1/auth/forgot-password` | Solicitar reset |
| POST | `/v1/auth/reset-password` | Confirmar reset con token |
| POST | `/v1/auth/mfa/setup` | Generar QR para 2FA |
| POST | `/v1/auth/mfa/confirm` | Confirmar activación 2FA |
| DELETE | `/v1/auth/mfa` | Deshabilitar 2FA |
| GET | `/v1/auth/mfa/status` | Estado del 2FA |
| POST | `/v1/auth/change-password/verify` | Preflight cambio contraseña |
| POST | `/v1/auth/change-password` | Cambiar contraseña |
| GET | `/v1/auth/sessions` | Listar sesiones activas |
| DELETE | `/v1/auth/sessions/{id}` | Cerrar sesión específica |
| DELETE | `/v1/auth/sessions/others` | Cerrar todas las demás sesiones |
| POST | `/v1/auth/report-suspicious` | Reportar actividad sospechosa |

### Registro
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/registro/token-info` | Info del token de activación |
| POST | `/v1/registro/complete` | Completar registro |

### Socios
| Método | Endpoint | Descripción | Roles |
|---|---|---|---|
| GET | `/v1/socios` | Listar (paginado, filtrable) | ADMIN, SECRETARIA, DIRECTIVO |
| GET | `/v1/socios/{id}` | Detalle completo | ADMIN, SECRETARIA, DIRECTIVO |
| GET | `/v1/socios/me` | Mi perfil | Autenticado |
| GET | `/v1/socios/buscar` | Búsqueda mínima por nombre (para inscripciones) | Autenticado |
| POST | `/v1/socios` | Iniciar registro (invitación) | ADMIN, SECRETARIA |
| PUT | `/v1/socios/{id}` | Editar datos personales | ADMIN, SECRETARIA |
| PATCH | `/v1/socios/me` | Actualizar mi perfil | Autenticado |
| PATCH | `/v1/socios/{id}/habilitar` | Habilitar | ADMIN, SECRETARIA, DIRECTIVO |
| PATCH | `/v1/socios/{id}/inhabilitar` | Inhabilitar | ADMIN, SECRETARIA, DIRECTIVO |
| GET | `/v1/socios/{id}/habilitacion-log` | Historial de habilitación | ADMIN, SECRETARIA, DIRECTIVO |
| PATCH | `/v1/socios/{id}/rol` | Cambiar rol | ADMIN, SECRETARIA |
| PATCH | `/v1/socios/{id}/nivel-tecnico` | Cambiar nivel técnico | ADMIN, SECRETARIA, DIRECTIVO |
| PATCH | `/v1/socios/{id}/jefe-montana` | Toggle jefe de montaña | ADMIN, SECRETARIA |
| DELETE | `/v1/socios/{id}` | Eliminar (hard delete) | ADMIN |
| POST | `/v1/socios/{id}/reenviar-invitacion` | Reenviar invitación de activación | ADMIN, SECRETARIA |
| POST | `/v1/socios/{id}/emergency-reset` | Reset de emergencia (2FA/dispositivo perdido) | ADMIN, SECRETARIA |
| GET | `/v1/socios/lookups` | Catálogos (tipos, estados, roles, clasificaciones) | Autenticado |
| GET | `/v1/socios/{id}/cuotas` | Historial de cuotas | ADMIN, SECRETARIA, DIRECTIVO |
| POST | `/v1/socios/{id}/cuotas` | Registrar pago de cuota | ADMIN, SECRETARIA |
| DELETE | `/v1/socios/{id}/cuotas/{cuotaId}` | Eliminar registro de cuota | ADMIN, SECRETARIA |
| POST | `/v1/socios/habilitacion/csv` | Habilitación/inhabilitación masiva por CSV | ADMIN, SECRETARIA, DIRECTIVO |
| POST | `/v1/socios/importar/preview` | Preview import CSV de socios | ADMIN, SECRETARIA |
| POST | `/v1/socios/importar/confirmar` | Confirmar import CSV de socios | ADMIN, SECRETARIA |
| GET | `/v1/socios/invitaciones` | Invitaciones pendientes | ADMIN, SECRETARIA |
| POST | `/v1/socios/invitaciones/{id}/reenviar` | Reenviar invitación pendiente | ADMIN, SECRETARIA |
| DELETE | `/v1/socios/invitaciones/{id}` | Eliminar invitación pendiente | ADMIN, SECRETARIA |
| GET | `/v1/socios/exportar/csv` | Exportar lista de socios en CSV | ADMIN, SECRETARIA, DIRECTIVO |
| GET | `/v1/socios/exportar/pdf` | Exportar lista de socios en PDF (máx. 6 columnas) | ADMIN, SECRETARIA, DIRECTIVO |
| GET | `/v1/socios/exportar/pdf/firmas` | Exportar hoja de firmas en PDF | ADMIN, SECRETARIA, DIRECTIVO |

**Query params de exportación:** `fields=apellido,nombre,...` (orden = posición), `tipoId`, `estadoId`, `excludeAdmin` (default: true), `q`

### Salidas
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/salidas` | Listar (paginado, filtrable) |
| GET | `/v1/salidas/{id}` | Detalle + participantes |
| POST | `/v1/salidas` | Crear |
| PUT | `/v1/salidas/{id}` | Editar |
| PATCH | `/v1/salidas/{id}/estado` | Cambiar estado |
| PATCH | `/v1/salidas/{id}/cancelar` | Cancelar con motivo |
| DELETE | `/v1/salidas/{id}` | Eliminar con motivo |
| GET | `/v1/salidas/lookups` | Catálogos |
| GET | `/v1/salidas/solapamiento` | Detectar solapamiento de fechas |
| POST | `/v1/salidas/{id}/inscripciones` | Inscribirse |
| DELETE | `/v1/salidas/{id}/inscripciones/{iId}` | Cancelar inscripción |
| PATCH | `/v1/salidas/{id}/inscripciones/{iId}/estado` | Cambiar estado inscripción |
| PATCH | `/v1/salidas/{id}/inscripciones/{iId}/jefe` | Designar jefe de salida |
| PATCH | `/v1/salidas/{id}/inscripciones/{iId}/aprobacion-riesgo` | Aprobar riesgo |
| DELETE | `/v1/salidas/{id}/inscripciones/{iId}/aprobacion-riesgo` | Revocar aprobación |
| POST | `/v1/salidas/{id}/inscripciones/{iId}/dignidades` | Agregar dignidad |
| DELETE | `/v1/salidas/{id}/inscripciones/{iId}/dignidades/{dId}` | Remover dignidad |
| PATCH | `/v1/salidas/{id}/cerrar-inscripciones` | Toggle cierre inscripciones |
| GET | `/v1/salidas/aprobaciones-pendientes` | Aprobaciones pendientes |
| GET | `/v1/salidas/alertas-sin-jefe` | Salidas sin jefe |

### Montañas
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/mountains` | Listar |
| GET | `/v1/mountains/{id}` | Detalle |
| POST | `/v1/mountains` | Crear |
| PUT | `/v1/mountains/{id}` | Editar |
| DELETE | `/v1/mountains/{id}` | Eliminar |
| GET | `/v1/mountains/lookups` | Catálogos (escalas, dificultades, etc.) |
| GET | `/v1/mountains/acceso-por-nivel` | Umbrales de acceso por nivel técnico |
| PUT | `/v1/mountains/acceso-por-nivel/{nivelSocioId}` | Actualizar umbral de acceso por nivel |

### Rutas
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/rutas` | Listar |
| GET | `/v1/rutas/{id}` | Detalle |
| POST | `/v1/rutas` | Crear |
| PUT | `/v1/rutas/{id}` | Editar |
| PATCH | `/v1/rutas/{id}/aprobar` | Aprobar ruta propuesta |
| DELETE | `/v1/rutas/{id}` | Eliminar |
| GET | `/v1/rutas/equipos` | Tipos de equipo disponibles |
| GET | `/v1/rutas/{id}/documentos` | Listar documentos de permiso |
| POST | `/v1/rutas/{id}/documentos` | Subir documento (multipart, máx 10 MB) |
| DELETE | `/v1/rutas/{id}/documentos/{docId}` | Eliminar documento |
| GET | `/v1/rutas/{id}/documentos/{docId}/descargar` | Descargar documento |
| GET | `/v1/rutas/{id}/contactos` | Listar contactos vinculados |
| POST | `/v1/rutas/{id}/contactos` | Vincular contacto a ruta |
| DELETE | `/v1/rutas/{id}/contactos/{cId}` | Desvincular contacto |

### Informes
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/informes/pendientes-jefe` | Pendientes del jefe |
| GET | `/v1/informes/{salidaId}` | Obtener informe (null si no existe) |
| POST | `/v1/informes/{salidaId}` | Crear informe |
| PUT | `/v1/informes/{salidaId}` | Editar informe |
| PATCH | `/v1/informes/{salidaId}/validar` | Validar informe |
| POST | `/v1/informes/{salidaId}/reconocimientos` | Agregar reconocimiento |
| DELETE | `/v1/informes/{salidaId}/reconocimientos/{id}` | Eliminar reconocimiento |
| POST | `/v1/informes/{salidaId}/pdf` | Generar PDF |
| GET | `/v1/informes/{salidaId}/pdf` | Descargar PDF |

**Nota sobre GET informe:** cuando `data` es null en la respuesta, significa que la salida existe pero el informe aún no fue creado. Mostrar estado "Pendiente de creación" en lugar de error.

### Actas
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/actas` | Listar |
| GET | `/v1/actas/{id}` | Detalle |
| POST | `/v1/actas` | Crear |
| PUT | `/v1/actas/{id}` | Editar |
| DELETE | `/v1/actas/{id}` | Eliminar |
| POST | `/v1/actas/{id}/asistentes` | Agregar asistente |
| DELETE | `/v1/actas/{id}/asistentes/{aId}` | Remover asistente |
| POST | `/v1/actas/{id}/informes` | Vincular informe de salida |
| DELETE | `/v1/actas/{id}/informes/{iId}` | Desvincular informe |
| POST | `/v1/actas/importar` | Importar acta desde Markdown (preview) |
| POST | `/v1/actas/importar/confirmar` | Confirmar importación desde Markdown |
| POST | `/v1/actas/{id}/pdf` | Generar PDF y guardar en S3 |
| GET | `/v1/actas/{id}/pdf` | Descargar PDF |

### Contactos
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/contactos` | Listar |
| GET | `/v1/contactos/buscar` | Buscar por nombre/teléfono |
| POST | `/v1/contactos` | Crear |
| PUT | `/v1/contactos/{id}` | Editar |
| DELETE | `/v1/contactos/{id}` | Eliminar |

### Admin
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/admin/config` | Listar todos los parámetros de configuración |
| GET | `/v1/admin/config/{clave}` | Obtener parámetro de configuración por clave |
| PATCH | `/v1/admin/config/{clave}` | Actualizar parámetro de configuración |
| GET | `/v1/admin/auditoria` | Log de auditoría (paginado, filtrable) |
| GET | `/v1/admin/security-events` | Eventos de seguridad (paginado, filtrable) |
| GET | `/v1/admin/usuarios-auth` | Listar cuentas de autenticación |
| GET | `/v1/admin/usuarios-auth/{id}` | Detalle de cuenta de autenticación |
| POST | `/v1/admin/usuarios-auth/{id}/desbloquear` | Desbloquear cuenta |
| PATCH | `/v1/admin/usuarios-auth/{id}/estado-acceso` | Cambiar estado de acceso |
| POST | `/v1/admin/usuarios-auth/{id}/cerrar-sesion` | Forzar cierre de sesión |
| POST | `/v1/admin/diagnostico/geoip` | Forzar verificación de frescura de base GeoIP |

### Estadísticas
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/estadisticas/dashboard` | KPIs generales del dashboard |
| GET | `/v1/estadisticas/club` | Estadísticas globales del club |
| GET | `/v1/estadisticas/socios/{socioId}` | Historial de un socio |
| GET | `/v1/estadisticas/socios/{socioId}/actividad-total` | Totales acumulados del socio |
| GET | `/v1/estadisticas/mountains/{mountainId}` | Estadísticas de una montaña |
| GET | `/v1/estadisticas/rankings` | Rankings de participación |
| GET | `/v1/estadisticas/reuniones/rankings` | Ranking de asistencia a reuniones |
| GET | `/v1/estadisticas/ranking-montana-ruta` | Montañas y rutas más frecuentadas |
| GET | `/v1/estadisticas/montana-ruta/buscar` | Búsqueda de estadísticas por montaña/ruta |
| GET | `/v1/estadisticas/participantes` | Búsqueda avanzada de participantes |
| GET | `/v1/estadisticas/periodo/salidas` | Salidas en un período |
| GET | `/v1/estadisticas/periodo/montanas` | Montañas visitadas en un período |
| GET | `/v1/estadisticas/periodo/rutas` | Rutas usadas en un período |

### Planificador y notificaciones
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/planificador/ruta/{rutaId}` | Socios aptos + equipo recomendado para una ruta |
| GET | `/v1/notificaciones/cumpleanos` | Socios con cumpleaños hoy |

### API Keys
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/v1/profile/api-keys` | Listar mis API keys |
| POST | `/v1/profile/api-keys` | Crear nueva API key |
| DELETE | `/v1/profile/api-keys/{id}` | Revocar API key |

### Formato de respuesta estándar

```json
{
  "success": true,
  "message": "Mensaje opcional",
  "data": { ... },
  "timestamp": "2026-01-01T00:00:00Z"
}
```

- `success: false` → mostrar `message` como error (toast o inline)
- `data: null` con `success: true` → condición válida (ej: informe no creado aún)
- HTTP 4xx → error del cliente, mostrar `message`
- HTTP 5xx → error del servidor, mostrar mensaje genérico

---

## 9. Estado global

### 9.1 AuthProvider (Riverpod)

```dart
// lib/core/auth/auth_provider.dart

@riverpod
class Auth extends _$Auth {
  @override
  AuthState build() => const AuthState.unauthenticated();

  Future<void> setAuth(String accessToken, User user) async {
    // Access token only in memory — never write to disk (see §5.1)
    state = AuthState.authenticated(accessToken: accessToken, user: user);
  }

  Future<void> clearAuth() async {
    await secureStorage.delete('refresh_token');
    await secureStorage.delete('biometric_enabled');
    state = const AuthState.unauthenticated();
  }
}

class AuthState {
  final String? accessToken;
  final User? user;
  final bool isAuthenticated;
  // ...
}

class User {
  final String socioId;
  final String username;
  final String nombre;
  final String rol;
  final String? nivelTecnico;
  final bool inhabilitado;
  final bool esJefeMontana;
}
```

### 9.2 Inicialización de sesión al arrancar

Al iniciar la app, intentar restaurar la sesión con el refresh token (si la cookie persiste):

```dart
// lib/app.dart — en initState o en un FutureProvider

Future<void> initApp() async {
  // 1. Verificar si hay biometría habilitada
  final biometricEnabled = await secureStorage.read('biometric_enabled') == 'true';

  if (biometricEnabled) {
    // Mostrar pantalla de desbloqueo biométrico
    router.go('/unlock');
  } else {
    // Intentar refresh silencioso (cookie persiste entre sesiones)
    final refreshed = await authService.refreshSession();
    if (refreshed) {
      router.go('/dashboard');
    } else {
      router.go('/login');
    }
  }
}
```

### 9.3 Providers de datos (equivalente a React Query)

```dart
// Ejemplo para salidas
@riverpod
Future<PaginatedResult<Salida>> salidas(
  SalidasRef ref, {
  required int page,
  String? estado,
  String? busqueda,
}) async {
  return ref.watch(salidasRepositoryProvider).listar(
    page: page,
    estado: estado,
    busqueda: busqueda,
  );
}
```

---

## 10. Permisos por rol

| Pantalla / Acción | SOCIO | DIRECTIVO | SECRETARIA | ADMIN |
|---|:---:|:---:|:---:|:---:|
| Dashboard | ✅ | ✅ | ✅ | ✅ |
| Salidas — ver | ✅ | ✅ | ✅ | ✅ |
| Salidas — crear/editar | ❌ | ✅ | ✅ | ✅ |
| Salidas — inscribirse | ✅ | ✅ | ✅ | ✅ |
| Salidas — aprobar riesgo | ❌ | ✅ | ✅ | ✅ |
| Socios — ver lista | ❌ | ✅ | ✅ | ✅ |
| Socios — crear/editar | ❌ | ❌ | ✅ | ✅ |
| Socios — eliminar | ❌ | ❌ | ❌ | ✅ |
| Socios — cambiar rol | ❌ | ❌ | ✅ | ✅ |
| Socios — habilitar/inhabilitar | ❌ | ✅ | ✅ | ✅ |
| Socios — exportar (CSV/PDF) | ❌ | ✅ | ✅ | ✅ |
| Montañas — ver | ✅ | ✅ | ✅ | ✅ |
| Montañas — crear/editar | ❌ | ❌ | ✅ | ✅ |
| Informes — ver | ✅ | ✅ | ✅ | ✅ |
| Informes — crear/editar | ✅* | ✅ | ✅ | ✅ |
| Informes — validar (firma) | ❌ | ✅ | ❌ | ✅ |
| Actas — ver | ✅ | ✅ | ✅ | ✅ |
| Actas — gestionar | ❌ | ❌ | ✅ | ✅ |
| Administración | ❌ | ✅ | ✅ | ✅ |
| Contactos | ❌ | ❌ | ✅ | ✅ |
| Notificaciones | ❌ | ✅ | ✅ | ✅ |

*Solo si es Jefe de Salida de esa salida específica.

---

## 11. Consideraciones mobile-específicas

### 11.1 Biometría — configuración nativa

**iOS — Info.plist:**
```xml
<key>NSFaceIDUsageDescription</key>
<string>Usa Face ID para acceder rápidamente a Sadday App</string>
```

**Android — AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC"/>
<uses-permission android:name="android.permission.USE_FINGERPRINT"/>
```

### 11.2 Deep links para activación de cuenta y reset de contraseña

El backend envía emails con links del tipo:
- `https://app.sadday.club/registro/completar?token=xxx`
- `https://app.sadday.club/reset-password?token=xxx`

Configurar deep links / App Links (Android) y Universal Links (iOS) para que estos links abran la app directamente.

**Android — AndroidManifest.xml:**
```xml
<intent-filter android:autoVerify="true">
  <action android:name="android.intent.action.VIEW"/>
  <category android:name="android.intent.category.BROWSABLE"/>
  <data android:scheme="https" android:host="app.sadday.club"/>
</intent-filter>
```

**iOS — Entitlements:**
```xml
<key>com.apple.developer.associated-domains</key>
<array>
  <string>applinks:app.sadday.club</string>
</array>
```

### 11.3 Manejo de PDFs

Para descargar PDFs de informes/actas:
1. `GET /v1/informes/{id}/pdf` → bytes del PDF
2. Guardar en directorio temporal de la app
3. Abrir con `syncfusion_flutter_pdfviewer` (visor in-app) o con `open_filex` (app externa)

### 11.4 CSV import en mobile

Para las funciones de importación CSV (socios, actas):
- Usar `file_picker` para seleccionar archivos CSV del dispositivo
- Multipart form upload con Dio

### 11.5 Pantalla de desbloqueo biométrico

Pantalla intermedia que aparece cuando la app vuelve al foreground después de X minutos en background:

```dart
class UnlockScreen extends StatelessWidget {
  // Mostrar logo + botón "Desbloquear con huella/Face ID"
  // Si falla 3 veces consecutivas: forzar login completo
  // Opción: "Usar contraseña" → ir a login normal
}
```

### 11.6 Notificaciones push (futuro)

Infraestructura preparada para push notifications (FCM / APNs) para:
- Alertas de nuevo dispositivo / país
- Aprobaciones pendientes de riesgo
- Salidas próximas (recordatorio)

No implementar en MVP, pero dejar la arquitectura preparada con un servicio `PushNotificationService`.

### 11.7 Offline / conectividad

- Mostrar banner cuando no hay conexión
- Usar `connectivity_plus` para detectar estado de red
- No implementar cache offline en MVP, solo manejo de errores de red con mensajes claros

### 11.8 Refresh token — persistencia entre reinicios

El refresh token se guarda en `flutter_secure_storage` (Keychain/Keystore). Al reiniciar la app, se recupera y se inyecta como cookie en el primer request a `/auth/refresh`. Si el refresh es exitoso, la sesión se restaura sin re-login.

Al cerrar sesión explícitamente → `secureStorage.deleteAll()` elimina todos los datos persistidos.

### 11.9 Timeout y reintentos

```dart
BaseOptions(
  connectTimeout: const Duration(seconds: 15),
  receiveTimeout: const Duration(seconds: 30),
)
```

Reintentar automáticamente una vez en errores de red (no en 4xx/5xx).

---

## 11b. Estados de acceso y habilitación

La tabla de permisos por rol describe qué puede hacer cada usuario **una vez dentro de la app**. El acceso a la app en sí está controlado por dos campos independientes del rol.

---

### Campo 1 — `estado_acceso` (¿puede loguear?)

Controla si el usuario puede iniciar sesión. Al cambiarlo a cualquier valor distinto de `ACTIVE`, el backend **revoca todas las sesiones activas inmediatamente**.

| Valor | Significado | Puede loguear |
|---|---|---|
| `ACTIVE` | Acceso normal | ✅ |
| `BLOCKED` | Bloqueado por sanción, reversible | ❌ |
| `EX_MEMBER` | Ex-miembro del club | ❌ |
| `DISABLED` | Deshabilitado técnicamente | ❌ |
| `PENDING_REGISTER` | Invitado, aún no completó registro | ❌ |

**¿Quién puede cambiarlo?** `ADMIN` y `SECRETARIA`
→ `PATCH /api/v1/admin/usuarios-auth/{socioId}/estado-acceso?codigo=BLOCKED`

**Restricciones del backend:**
- El rol `ADMIN` **nunca puede ser bloqueado ni deshabilitado** — el código lo impide explícitamente. Para restringir a un Admin hay que cambiarle el rol primero.
- No se puede bloquear a la **única SECRETARIA activa** — debe haber al menos una con `ACTIVE` en todo momento.

**En la app mobile:** si al hacer refresh el backend responde con error de acceso bloqueado, limpiar sesión completamente y mostrar mensaje: *"Tu acceso ha sido suspendido. Contactá a un administrador."*

---

### Campo 2 — `estado_habilitacion` (¿puede participar en salidas?)

Controla la elegibilidad del socio para inscribirse en salidas. **No impide el login** — el usuario entra a la app normalmente pero no puede inscribirse.

| Valor | Significado | Puede inscribirse en salidas |
|---|---|---|
| `Habilitado` | Estado normal | ✅ |
| `Inhabilitado` | Sancionado o con deuda | ❌ |
| `Socio Vitalicio` | Miembro honorario | ✅ |

**¿Quién puede cambiarlo?** `ADMIN`, `SECRETARIA` y `DIRECTIVO`
→ `PATCH /api/v1/socios/{id}/inhabilitar`
→ `PATCH /api/v1/socios/{id}/habilitar`

**Restricción del backend:** no se puede inhabilitar a un socio con rol `ADMIN` o `SECRETARIA` desde este endpoint.

---

### Resumen — quién controla qué

| Acción | SOCIO | DIRECTIVO | SECRETARIA | ADMIN |
|---|:---:|:---:|:---:|:---:|
| Cambiar `estado_acceso` (bloquear/desbloquear login) | ❌ | ❌ | ✅ | ✅ |
| Cambiar `estado_habilitacion` (habilitar/inhabilitar para salidas) | ❌ | ✅ | ✅ | ✅ |

---

## 12. Seguridad

### 12.1 Almacenamiento de datos sensibles

| Dato | Almacenamiento | Justificación |
|---|---|---|
| Access token | Solo memoria (Riverpod) | Vida corta, no debe sobrevivir al cierre de app |
| Refresh token | `flutter_secure_storage` | Cifrado con Keychain/Keystore a nivel hardware |
| Flag biométrico | `flutter_secure_storage` | Dato de configuración de seguridad |
| Datos del usuario | Solo memoria (Riverpod) | No persistir PII en disco innecesariamente |
| URLs / config | `flutter_dotenv` (archivo `.env`) | No hardcodear en código fuente |

**Nunca almacenar en:**
- `SharedPreferences` / `NSUserDefaults` — no cifrados
- `localStorage` equivalente — accesible sin cifrado
- Logs de la app en producción

### 12.2 Certificate pinning

Implementar con el paquete `dio_pinning` o manualmente con `SecurityContext`. Previene ataques MITM donde un proxy intercepta el tráfico con un certificado falso.

```dart
// lib/core/api/api_client.dart

SecurityContext buildSecurityContext() {
  final context = SecurityContext.defaultContext;
  // Cargar el certificado público del servidor (bundleado en assets)
  final cert = File('assets/certs/sadday_cert.pem').readAsBytesSync();
  context.setTrustedCertificatesBytes(cert);
  return context;
}

final httpClient = HttpClient(context: buildSecurityContext());
final dio = Dio()
  ..httpClientAdapter = IOHttpClientAdapter(createHttpClient: () => httpClient);
```

**Consideraciones:**
- Bundlear el certificado en `assets/certs/` (no incluir en control de versiones si es producción real)
- Planificar rotación: cuando el certificado vence, la app debe tener un mecanismo de actualización (o usar SPKI pinning que es más tolerante a renovaciones)
- En desarrollo: permitir omitir pinning con una variable de entorno `SKIP_CERT_PINNING=true`

### 12.3 Screen masking en background

Cuando la app pasa a background, iOS/Android capturan un screenshot para el app switcher. Enmascarar la UI para evitar exponer datos del usuario.

```dart
// lib/core/security/app_lifecycle_observer.dart

class AppLifecycleObserver extends WidgetsBindingObserver {
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive || state == AppLifecycleState.paused) {
      // Mostrar overlay de bloqueo (logo o pantalla en negro)
      _showSecureOverlay();
    } else if (state == AppLifecycleState.resumed) {
      _hideSecureOverlay();
      _checkInactivityTimeout(); // Ver sección 12.6
    }
  }
}
```

**iOS — Info.plist:**
No requiere configuración adicional si se maneja por código. Alternativa: usar `FlutterSecureTextEntry` para campos específicos.

**Android — MainActivity.kt:**
```kotlin
window.setFlags(
  WindowManager.LayoutParams.FLAG_SECURE,
  WindowManager.LayoutParams.FLAG_SECURE
)
```
`FLAG_SECURE` también previene capturas de pantalla del usuario dentro de la app.

### 12.4 Campos sensibles — deshabilitar cache del teclado

El teclado de Android guarda historial para autocompletado. En campos sensibles esto es una filtración.

```dart
// Aplicar en todos los campos de contraseña, PIN, y datos sensibles

TextField(
  obscureText: true,
  enableSuggestions: false,     // deshabilita sugerencias del teclado
  autocorrect: false,           // deshabilita corrección automática
  keyboardType: TextInputType.visiblePassword, // evita emoji/sugerencias
)
```

Campos que requieren esto:
- Contraseña (login, registro, cambio de contraseña)
- PIN de MFA (6 dígitos)
- Código de country challenge
- Cédula de identidad

### 12.5 Tráfico solo HTTPS — bloquear cleartext

**Android — `android/app/src/main/res/xml/network_security_config.xml`:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <base-config cleartextTrafficPermitted="false">
    <trust-anchors>
      <certificates src="system"/>
    </trust-anchors>
  </base-config>
</network-security-config>
```

**iOS:** Por defecto App Transport Security (ATS) ya bloquea HTTP. Asegurarse de no tener `NSAllowsArbitraryLoads: true` en `Info.plist`.

### 12.6 Timeout de sesión por inactividad

Si el usuario deja la app abierta sin usarla, disparar el desbloqueo biométrico después de X minutos.

```dart
// lib/core/security/inactivity_timer.dart

class InactivityTimer {
  static const _timeoutMinutes = 10;
  Timer? _timer;

  void reset() {
    _timer?.cancel();
    _timer = Timer(
      const Duration(minutes: _timeoutMinutes),
      _onTimeout,
    );
  }

  void _onTimeout() {
    // Limpiar access token de memoria (no el refresh token)
    ref.read(authProvider.notifier).clearAccessToken();
    // Navegar a pantalla de desbloqueo
    router.go('/unlock');
  }
}
```

Resetear el timer en cada interacción del usuario (tap, scroll, input). Usar `Listener` en el widget raíz para capturar cualquier gesto.

### 12.7 Ofuscación de código en releases

```bash
# Build con ofuscación habilitada
flutter build apk --release --obfuscate --split-debug-info=build/debug-info/
flutter build ipa --release --obfuscate --split-debug-info=build/debug-info/
```

Guardar los archivos de `split-debug-info/` para poder desofuscar stack traces en producción (usar Firebase Crashlytics o similar).

### 12.8 Logs — no exponer datos sensibles

```dart
// lib/core/utils/logger.dart

class AppLogger {
  static final _logger = Logger();

  static void info(String message) => _logger.i(message);
  static void error(String message, [Object? error]) => _logger.e(message, error: error);

  // NUNCA llamar a estos métodos con tokens, contraseñas o PII
  // ❌ AppLogger.info('Token: $accessToken')
  // ❌ AppLogger.info('Password: $password')
  // ✅ AppLogger.info('Login exitoso para usuario ${user.username}')
}
```

En modo release, deshabilitar logs completamente:
```dart
if (kReleaseMode) Logger.level = Level.nothing;
```

### 12.9 Protección contra root / jailbreak

Detectar dispositivos comprometidos donde las garantías de seguridad del sistema operativo no aplican (un atacante con root puede extraer `flutter_secure_storage`).

Usar el paquete `flutter_jailbreak_detection`:

```dart
// Al iniciar la app
final isJailbroken = await FlutterJailbreakDetection.jailbroken;
final isDeveloperMode = await FlutterJailbreakDetection.developerMode; // Android

if (isJailbroken) {
  // Mostrar advertencia y opcionalmente bloquear acceso
  showDialog(
    context: context,
    builder: (_) => AlertDialog(
      title: const Text('Dispositivo comprometido'),
      content: const Text(
        'Se detectó que este dispositivo tiene acceso root o jailbreak. '
        'Por seguridad, algunas funciones pueden estar limitadas.'
      ),
    ),
  );
  // Para un club deportivo: advertir pero no bloquear (decisión de negocio)
}
```

**Nota de negocio:** para esta app, mostrar advertencia pero no bloquear el acceso. Bloqueo total es apropiado para apps bancarias/financieras, no necesariamente para un club deportivo.

### 12.10 Validación de inputs en el cliente

Aunque el backend valida todo, el cliente debe validar antes de enviar para mejorar la UX y reducir requests inválidos:

| Campo | Regla |
|---|---|
| Usuario | 4–100 caracteres |
| Contraseña | Min 12 chars, 1 mayúscula, 1 minúscula, 1 número, 1 símbolo |
| Correo | Formato válido de email |
| Cédula | Solo números, longitud según país |
| Teléfono | Solo números y `+`, longitud razonable |
| PIN MFA | Exactamente 6 dígitos numéricos |

### 12.11 Logout seguro

El logout debe limpiar todo sin dejar rastros:

```dart
Future<void> logout() async {
  // 1. Notificar al backend (revoca el refresh token en servidor)
  try {
    await api.post('/v1/auth/logout');
  } catch (_) {
    // Si falla el request, igual limpiar localmente
  }

  // 2. Limpiar memoria
  ref.read(authProvider.notifier).clearAuth();

  // 3. Limpiar SecureStorage
  await secureStorage.delete('refresh_token');
  await secureStorage.delete('biometric_enabled');
  // NO eliminar preferencias de UI (tema, idioma) — son datos no sensibles

  // 4. Navegar a login
  router.go('/login');
}
```

### 12.12 Checklist de seguridad pre-release

Antes de publicar en App Store / Play Store verificar:

- [ ] Certificate pinning habilitado y probado
- [ ] `FLAG_SECURE` en Android habilitado
- [ ] `NSAllowsArbitraryLoads` ausente o `false` en iOS
- [ ] `network_security_config.xml` con `cleartextTrafficPermitted="false"`
- [ ] Build con `--obfuscate` y `split-debug-info` guardado
- [ ] Logs deshabilitados en release (`kReleaseMode` check)
- [ ] Ningún token/contraseña en variables de entorno commiteadas al repo
- [ ] Timeout de inactividad probado (10 minutos)
- [ ] Screen masking probado en iOS y Android
- [ ] Flujo de logout limpia SecureStorage completamente
- [ ] Campos sensibles con `enableSuggestions: false`

---

## 13. Requisitos adicionales de seguridad (OWASP MASVS 2.x / MITRE ATT&CK)

> Los siguientes requisitos se identificaron durante una revisión contra OWASP MASVS 2.x y MITRE ATT&CK Mobile. Deben implementarse junto con los controles de la sección 12.

### 13.1 Mutex en el interceptor de refresh — evitar race condition

**OWASP:** MASVS-AUTH | **MITRE:** T1557 (Adversary-in-the-Middle)

Si múltiples requests fallan con 401 simultáneamente, se dispararían múltiples llamadas a `/auth/refresh`. El backend rota el refresh token en cada refresh y revoca el anterior — si se ejecutan en paralelo, el backend puede interpretar el segundo como robo de token y revocar **todas** las sesiones del usuario.

**Implementación requerida:**

```dart
// lib/core/api/refresh_lock.dart

import 'package:synchronized/synchronized.dart';

final _refreshLock = Lock();
String? _lastExpiredToken;

Future<bool> refreshWithLock(AuthService authService, String expiredToken) async {
  return _refreshLock.synchronized(() async {
    // Si otro request ya refrescó el token, no repetir
    if (_lastExpiredToken == expiredToken) return true;
    
    final success = await authService.refreshSession();
    if (success) _lastExpiredToken = expiredToken;
    return success;
  });
}
```

En el interceptor de errores (§5.4), reemplazar la llamada directa a `refreshSession()` con `refreshWithLock()`.

### 13.2 Validación local de expiración del JWT

**OWASP:** MASVS-AUTH-2

Antes de enviar cada request, verificar localmente si el access token ya expiró. Esto evita 401 innecesarios y reduce latencia para el usuario.

```dart
// lib/core/auth/jwt_utils.dart

bool isTokenExpired(String jwt) {
  try {
    final parts = jwt.split('.');
    if (parts.length != 3) return true;
    
    final payload = json.decode(
      utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
    );
    final exp = DateTime.fromMillisecondsSinceEpoch(payload['exp'] * 1000);
    // 30 segundos de buffer para compensar diferencia de reloj
    return DateTime.now().isAfter(exp.subtract(const Duration(seconds: 30)));
  } catch (_) {
    return true; // Si no se puede decodificar, tratar como expirado
  }
}
```

**Importante:** NO validar la firma del JWT en el cliente — eso es responsabilidad del backend. Solo verificar la expiración para decidir si hacer refresh proactivo.

### 13.3 Sanitización de deep links

**OWASP:** MASVS-PLATFORM | **MITRE:** T1444 (Masquerade as Legitimate Application)

Los deep links de activación de cuenta (`/registro/completar?token=xxx`) y reset de contraseña (`/reset-password?token=xxx`) reciben un token por URL. Este token debe validarse antes de procesarse.

```dart
// lib/core/utils/deep_link_validator.dart

class DeepLinkValidator {
  static final _tokenPattern = RegExp(r'^[a-zA-Z0-9\-]{36,128}$');

  /// Valida y sanitiza un token recibido por deep link.
  /// Retorna null si el token es inválido.
  static String? sanitizeToken(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    final trimmed = raw.trim();
    if (!_tokenPattern.hasMatch(trimmed)) return null;
    return trimmed;
  }
}
```

**Reglas:**
- Si el token no pasa la validación → navegar a `/login` con mensaje genérico
- No mostrar el token en la UI — puede ser fotografiado
- Limpiar el token de la URL/intent después de procesarlo
- Limitar a 3 intentos de envío con un token inválido antes de bloquear la pantalla

### 13.4 Detección de debugger y frameworks de hooking

**OWASP:** MASVS-RESILIENCE | **MITRE:** T1407, T1404

Complementa la detección de root/jailbreak (§12.9) con detección de:
- Debuggers (Frida, lldb, ADB debugging)
- Frameworks de hooking (Xposed, Substrate)
- Repackaging del APK/IPA

Usar el paquete `freerasp` (gratuito, mantenido por Talsec):

```dart
// lib/core/security/rasp_config.dart

import 'package:freerasp/freerasp.dart';

Future<void> initRasp() async {
  final config = TalsecConfig(
    androidConfig: AndroidConfig(
      packageName: 'com.sadday.app',
      signingCertHashes: ['HASH_DE_TU_CERTIFICADO'],
    ),
    iosConfig: IOSConfig(
      bundleIds: ['com.sadday.app'],
      teamId: 'TU_TEAM_ID',
    ),
    watcherMail: 'security@sadday.club',
  );

  final callback = ThreatCallback(
    onDebuggerDetected: () => _showSecurityWarning('Debugger detectado'),
    onHookDetected: () => _showSecurityWarning('Hooking detectado'),
    onTamperDetected: () => _showSecurityWarning('App modificada'),
    onRootDetected: () => _showSecurityWarning('Dispositivo con root/jailbreak'),
  );

  await Talsec.instance.start(config, callback);
}
```

**Nota de negocio:** para un club deportivo, mostrar advertencia pero NO bloquear el acceso. El bloqueo total es apropiado para apps financieras.

### 13.5 Deshabilitar backup de Android

**OWASP:** MASVS-STORAGE | **MITRE:** T1409 (Access Stored Application Data)

Android Auto Backup guarda datos de la app en la nube del usuario. Aunque `flutter_secure_storage` usa Keystore (no se incluye en backups), otros archivos podrían filtrarse.

**AndroidManifest.xml:**
```xml
<application
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules">
```

**`android/app/src/main/res/xml/data_extraction_rules.xml`** (Android 12+):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
  <cloud-backup>
    <exclude domain="root" />
    <exclude domain="sharedpref" />
    <exclude domain="database" />
  </cloud-backup>
</data-extraction-rules>
```

### 13.6 Política de retención de datos locales

**OWASP:** MASVS-PRIVACY

Definir explícitamente qué datos se retienen y cuándo se eliminan:

| Dato | Retención | Limpieza |
|---|---|---|
| Cache HTTP de Dio | Deshabilitado — no cachear responses | N/A |
| PDFs descargados (informes/actas) | Directorio temporal | Al cerrar sesión + al iniciar la app |
| Clipboard (API keys, tokens MFA) | No controlable | Limpiar clipboard 30s después de copiar |
| Logs de debug | Solo en modo debug | Deshabilitados en release (§12.8) |

**Implementación:**

```dart
// En logout() — agregar limpieza de archivos temporales
Future<void> _cleanTempFiles() async {
  final tempDir = await getTemporaryDirectory();
  if (tempDir.existsSync()) {
    tempDir.listSync().forEach((f) => f.deleteSync(recursive: true));
  }
}

// Después de copiar un dato sensible al clipboard
Future<void> copyAndClear(String value) async {
  await Clipboard.setData(ClipboardData(text: value));
  Future.delayed(const Duration(seconds: 30), () {
    Clipboard.setData(const ClipboardData(text: ''));
  });
}
```

### 13.7 Versiones mínimas del sistema operativo

Para garantizar las propiedades de seguridad de `flutter_secure_storage` (Keystore hardware-backed) y biometría:

| Plataforma | Versión mínima | Justificación |
|---|---|---|
| **Android** | API 26 (8.0 Oreo) | Keystore hardware-backed confiable. StrongBox opcional. |
| **iOS** | 14.0 | Secure Enclave + biometría moderna + ATS enforced. |

Configurar en:
- `android/app/build.gradle`: `minSdkVersion 26`
- Xcode: Deployment Target = 14.0

### 13.8 Header User-Agent descriptivo

El backend (`PlatformDetector.java`) detecta la plataforma vía User-Agent para la tabla de sesiones activas. Configurar un User-Agent descriptivo:

```dart
// En api_client.dart, agregar a los headers:
'User-Agent': 'SaddayApp/${appVersion} (Flutter; ${Platform.operatingSystem} ${Platform.operatingSystemVersion})',
```

Esto permite que la sección "Sesiones activas" del perfil muestre información útil (ej: "SaddayApp/1.0.0 (Flutter; android 14)") en lugar de "Desconocido".

---

## 14. Consideraciones de clean code

### 14.1 Convenciones de naming

El código Dart debe seguir las convenciones del ecosistema Flutter:
- **Código fuente:** todo en inglés (variables, clases, métodos, comentarios técnicos)
- **Textos de UI:** en español (labels, mensajes, placeholders)
- **Nombres de archivo:** snake_case (ej: `auth_service.dart`, `login_screen.dart`)
- **Clases:** PascalCase
- **Variables/métodos:** camelCase

### 14.2 Manejo de errores tipado

Definir una jerarquía de excepciones para evitar `catch (_)` genéricos:

```dart
// lib/core/api/app_exception.dart

sealed class AppException implements Exception {
  final String message;
  const AppException(this.message);
}

class NetworkException extends AppException {
  const NetworkException([super.message = 'Error de conexión. Verifica tu red.']);
}

class UnauthorizedException extends AppException {
  const UnauthorizedException([super.message = 'Sesión expirada.']);
}

class ForbiddenException extends AppException {
  const ForbiddenException([super.message = 'No tienes permisos para esta acción.']);
}

class BusinessException extends AppException {
  const BusinessException(super.message);
}

class ServerException extends AppException {
  const ServerException([super.message = 'Error del servidor. Intenta más tarde.']);
}
```

Usar en el interceptor de Dio para transformar HTTP status codes en excepciones tipadas.

### 14.3 Estrategia de testing

| Tipo | Objetivo | Herramienta |
|---|---|---|
| **Unit tests** | Repositories, services, providers | `flutter_test` + `mocktail` |
| **Widget tests** | Pantallas y componentes | `flutter_test` + `golden_toolkit` |
| **Integration tests** | Flujos completos (login → dashboard) | `integration_test` |
| **Golden tests** | Design system — detectar regresiones visuales | `golden_toolkit` |

Cobertura mínima objetivo: **70% en código de negocio** (repositories, services, providers).

