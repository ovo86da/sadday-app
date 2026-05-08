# FR-001 — Gestión de sesiones múltiples y eventos de seguridad

**Fecha de solicitud:** 2026-04-26
**Fecha de implementación:** 2026-04-26
**Estado:** Implementado (ver [Registro de implementación](#registro-de-implementación))
**Prioridad:** Alta
**Área:** Autenticación / Seguridad

---

## Contexto y motivación

El sistema actual tiene un bloqueo de sesión única por plataforma (`existsActiveBySocioIdAndPlatform`) que rechaza el login si ya hay una sesión activa en la misma plataforma. Esto genera problemas de usabilidad: si la sesión anterior no cerró correctamente (pérdida de conexión, cierre forzado del browser), el usuario queda bloqueado hasta que el token expire o un admin intervenga.

El enfoque correcto no es prohibir múltiples sesiones sino **darle control al usuario sobre sus propias sesiones** y registrar eventos de seguridad para detectar actividad anómala.

---

## Cambios solicitados

### 1. Eliminar el bloqueo de sesión única

**Archivo afectado:** `AuthService.java` — método `completarLogin()`

Eliminar el bloque:
```java
if (refreshTokenRepository.existsActiveBySocioIdAndPlatform(...)) {
    throw new BusinessException(ErrorCode.SESSION_ALREADY_ACTIVE);
}
```

Y el método en `RefreshTokenRepository`:
```java
boolean existsActiveBySocioIdAndPlatform(...)
```

El error `SESSION_ALREADY_ACTIVE` y `LOGIN_SESSION_CONFLICT` también pueden eliminarse de `ErrorCode`.

#### 1.1 Workaround actual de secretaria — `forzarCierreSesion`

Antes de este cambio, el flujo de desbloqueo manual era:

1. El socio intenta iniciar sesión y recibe `SESSION_ALREADY_ACTIVE` (sesión activa en la misma plataforma).
2. El socio contacta a la secretaria.
3. La secretaria llama a `POST /api/v1/admin/usuarios-auth/{socioId}/cerrar-sesion` desde el panel de administración.
4. El backend ejecuta `revokeAllBySocioId(socioId)` → marca todos los **refresh tokens** del socio como revocados (`revoked = true`, `revokedAt = now()`).
5. El socio puede iniciar sesión nuevamente.

**Importante — qué se revoca y qué no:**

- Se revocan los **refresh tokens** (registros en la tabla `refresh_tokens`). Son los únicos tokens que el backend puede invalidar porque están almacenados en BD.
- Los **access tokens** (JWT RS256, 15 min) son **stateless** — el backend no los almacena, no puede revocarlos. Si el socio tiene un access token válido en memoria, sigue funcionando hasta que expire. En la práctica esto es irrelevante porque el bloqueo ocurría al hacer login (antes de emitir nuevos tokens).

**Por qué este workaround desaparece con FR-001:**

Al eliminar el bloque `existsActiveBySocioIdAndPlatform`, el sistema simplemente permite el nuevo login aunque existan sesiones anteriores activas. El usuario nunca queda bloqueado por una sesión huérfana, y la secretaria no necesita intervenir.

**Por qué se mantiene `POST /admin/usuarios-auth/{socioId}/cerrar-sesion`:**

El endpoint no se elimina. Pasa de ser un workaround para un bug de usabilidad a ser una herramienta administrativa legítima para casos como:

- Cuenta comprometida reportada por el socio o detectada por el equipo.
- Cierre forzado por baja del socio o suspensión de cuenta.
- Requerimiento de cumplimiento o auditoría interna.
- Actividad sospechosa identificada por un administrador.

El endpoint existente (`@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")`) no requiere cambios funcionales — solo cambia su propósito percibido.

---

### 2. Gestión de sesiones activas (UI + API)

El usuario puede ver y cerrar sus propias sesiones desde la configuración de su cuenta.

#### Vista esperada en UI

```
Sesiones activas (3 dispositivos)

● Esta sesión
  Chrome — Quito, Ecuador
  Hace 2 minutos

  Firefox — IP desconocida
  Hace 3 días                           [Cerrar]

  iPhone — Guayaquil, Ecuador
  Hace 1 semana                         [Cerrar]

                              [Cerrar todas las demás]
```

#### Nuevos endpoints

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/v1/auth/sessions` | Lista las sesiones activas del usuario autenticado |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | Revoca una sesión específica por ID |
| `DELETE` | `/api/v1/auth/sessions` (ya existe como `/logout-all`) | Revoca todas las sesiones |

> `/logout-all` ya existe. Se puede reutilizar o agregar una variante que excluya la sesión actual (`/sessions/others`).

#### DTO de respuesta de sesión

```json
{
  "sessionId": "uuid",
  "platform": "WEB",
  "browser": "Chrome 124",
  "os": "Windows 11",
  "city": "Quito",
  "country": "EC",
  "ipAddress": "190.x.x.x",
  "createdAt": "2026-04-20T14:30:00",
  "lastUsedAt": "2026-04-26T13:00:00",
  "isCurrent": true
}
```

**Campo `isCurrent`:** la sesión activa se identifica comparando el hash del refresh token de la petición actual con los tokens listados.

**Campo `lastUsedAt`:** requiere agregar la columna `last_used_at` a `refresh_tokens` y actualizarla en cada rotación de token en `AuthService.refresh()`.

---

### 3. Invalidación selectiva (revocación)

Escenarios que ya revocan todas las sesiones (sin cambios necesarios):
- **Cambio de contraseña** → `authService.changePassword()` ya llama `revokeAllBySocioId()`
- **Detección de robo de refresh token** → `authService.refresh()` ya llama `revokeAllBySocioId()`

Escenario nuevo a implementar:
- **El usuario reporta actividad sospechosa** → nuevo endpoint `POST /api/v1/auth/report-suspicious` que revoca todas las sesiones y registra el evento `SUSPICIOUS_ACTIVITY_REPORTED`.

---

### 4. Nueva tabla: `security_events`

Tabla dedicada a eventos de seguridad, separada de `auditoria` (que registra cambios de datos de negocio). `security_events` es para actividad de autenticación y señales de riesgo.

#### Esquema propuesto

```sql
CREATE TABLE security_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    socio_id        UUID REFERENCES socios(id) ON DELETE SET NULL,
    username        VARCHAR(100),        -- para logins fallidos donde el socio_id es desconocido
    event_type      VARCHAR(50)  NOT NULL,
    ip_address      VARCHAR(45),
    country_code    CHAR(2),             -- ISO 3166-1 alpha-2 (EC, US, AR, ...)
    city            VARCHAR(100),
    user_agent      TEXT,
    device_id       VARCHAR(64),         -- hash del fingerprint del dispositivo
    session_id      UUID REFERENCES refresh_tokens(id) ON DELETE SET NULL,
    metadata        JSONB,               -- datos adicionales según el tipo de evento
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_events_socio_id    ON security_events (socio_id, created_at DESC);
CREATE INDEX idx_security_events_event_type  ON security_events (event_type, created_at DESC);
CREATE INDEX idx_security_events_ip          ON security_events (ip_address, created_at DESC);
```

#### Tipos de evento (`event_type`)

| Evento | Descripción |
|--------|-------------|
| `LOGIN_SUCCESS` | Login exitoso |
| `LOGIN_FAILED` | Contraseña incorrecta o usuario no existe |
| `LOGIN_BLOCKED` | Login rechazado por cuenta bloqueada o estado inactivo |
| `NEW_DEVICE_LOGIN` | Login desde un `device_id` no visto antes para este socio |
| `NEW_COUNTRY_LOGIN` | Login desde un país no visto antes para este socio |
| `PASSWORD_CHANGED` | Cambio de contraseña (invalida todas las sesiones) |
| `REFRESH_TOKEN_REUSED` | Token de refresco revocado usado de nuevo → posible robo |
| `SESSION_REVOKED` | Sesión cerrada manualmente por el usuario |
| `SESSION_REVOKED_ALL` | Cierre de todas las sesiones |
| `SUSPICIOUS_ACTIVITY_REPORTED` | El usuario marcó actividad sospechosa |
| `MFA_ENABLED` / `MFA_DISABLED` | Cambios en 2FA |

#### Campo `device_id`

Fingerprint del dispositivo construido como `SHA-256(user_agent + platform)` truncado a 16 bytes en hex (32 chars). No identifica al usuario, solo al binomio dispositivo+browser. Permite detectar cuándo un socio inicia sesión desde un dispositivo nuevo.

#### Campo `metadata` (ejemplos por tipo de evento)

```json
// NEW_DEVICE_LOGIN
{ "device_id": "a3f4...", "is_first_login": false }

// NEW_COUNTRY_LOGIN
{ "previous_country": "EC", "new_country": "RU" }

// REFRESH_TOKEN_REUSED
{ "revoked_token_id": "uuid", "sessions_revoked": 3 }

// SESSION_REVOKED
{ "revoked_session_id": "uuid", "revoked_by": "user" }
```

---

### 5. Geolocalización de IP

Para poblar `country_code` y `city` en `security_events`.

#### Opción recomendada: MaxMind GeoLite2 (embedded)

Base de datos local, sin llamadas a APIs externas, sin latencia de red, sin costo por volumen.

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.maxmind.geoip2</groupId>
    <artifactId>geoip2</artifactId>
    <version>4.x</version>
</dependency>
```

La base de datos `GeoLite2-City.mmdb` se descarga de MaxMind (requiere cuenta gratuita), se incluye como recurso en el JAR o se monta como volumen en Docker.

```java
@Component
public class GeoIpService {
    private final DatabaseReader reader;

    public GeoLocation lookup(String ipAddress) {
        // Devuelve country_code + city, o null si es IP privada/desconocida
    }
}
```

**Fallback:** si la IP es privada (`127.0.0.1`, `10.x.x.x`, `192.168.x.x`) o la lookup falla, guardar `country_code = null`, `city = null` — nunca lanzar excepción.

**Actualización de la BD:** MaxMind actualiza GeoLite2 dos veces por semana. Se puede automatizar en el pipeline CI o con un cron semanal.

---

### 6. Reglas de seguridad automáticas

Lógica a implementar en `SecurityEventService` al registrar un `LOGIN_SUCCESS`:

#### Regla 1 — Nuevo dispositivo

```
Si device_id no aparece en security_events del socio con event_type IN ('LOGIN_SUCCESS', 'NEW_DEVICE_LOGIN'):
  → registrar NEW_DEVICE_LOGIN
  → enviar email de alerta: "Nuevo inicio de sesión desde [browser] en [ciudad, país]"
  → si el socio tiene MFA activo: ya está protegido (pasó por TOTP)
  → si NO tiene MFA: incluir en el email el enlace para activar 2FA
```

#### Regla 2 — Nuevo país

```
Si country_code no aparece en los últimos 90 días de security_events del socio:
  → registrar NEW_COUNTRY_LOGIN
  → si el socio NO tiene MFA activo: bloquear el login y exigir verificación por email
    (similar al flujo de MFA: emitir un challenge token, enviar email con código)
  → si SÍ tiene MFA activo: ya pasó por TOTP, solo registrar evento
```

#### Regla 3 — Muchos fallos de login (ya existe)

Ya implementado en `AuthService.handleFailedAttempt()`:
- 3 intentos fallidos → `loginBlocked = true`, `blockedUntil = now + 24h`
- Auto-desbloqueo al expirar `blockedUntil`

Mejora sugerida: registrar también en `security_events` con `event_type = LOGIN_FAILED` además de en `auditoria`, para poder hacer agregaciones por IP y detectar ataques distribuidos.

#### Regla 4 — Refresh token reutilizado (ya existe, mejorar logging)

Ya implementado en `AuthService.refresh()`: detecta token revocado → revoca todas las sesiones.

Mejora: registrar en `security_events` con `event_type = REFRESH_TOKEN_REUSED` y `metadata` con el número de sesiones revocadas.

---

## Estado actual del código relevante

| Componente | Archivo | Estado |
|-----------|---------|--------|
| Bloqueo sesión única | `AuthService.java:199-205` | Eliminar |
| `existsActiveBySocioIdAndPlatform` | `RefreshTokenRepository.java:29-35` | Eliminar |
| `RefreshToken` entity | `RefreshToken.java` | Agregar `lastUsedAt`, `deviceId` |
| `revokeAllBySocioId` | `RefreshTokenRepository.java:19-21` | Ya existe, reutilizar |
| Logout simple | `AuthController.java:180` | Ya existe |
| Logout all | `AuthController.java:196` | Ya existe, reutilizar |
| `PlatformDetector` | `PlatformDetector.java` | Extender o reemplazar con parser de UA completo |
| Auditoría `LOGIN_SUCCESS` | `AuditService.java` | Se mantiene, agregar también a `security_events` |
| `@EnableAsync` | `SaddayAppApplication.java` | Ya activo, reutilizar para emails de alerta |

---

## Tareas de implementación

### Fase 1 — Base de datos y eliminación del bloqueo
- [x] Migración Flyway `V51`: agregar `last_used_at` y `device_id` a `refresh_tokens`
- [x] Migración Flyway `V52`: crear tabla `security_events` con índices
- [x] Eliminar bloqueo de sesión única en `AuthService.completarLogin()`
- [x] Eliminar `existsActiveBySocioIdAndPlatform` de `RefreshTokenRepository`

### Fase 2 — Geolocalización
- [x] Agregar dependencia `geoip2 4.2.0` en `pom.xml`
- [x] Crear `GeoIpService` con lookup y fallback para IPs privadas
- [x] Integrar en `SecurityEventService` y en `AuthService.listSessions()`

### Fase 3 — Registro de eventos de seguridad
- [x] Crear entity `SecurityEvent` y `SecurityEventRepository`
- [x] Crear `SecurityEventService` con método `record(...)` y `applyLoginRules(...)`
- [x] Integrar en `AuthService`: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGIN_BLOCKED`, `REFRESH_TOKEN_REUSED`, `PASSWORD_CHANGED`, `SESSION_REVOKED`, `SESSION_REVOKED_ALL`, `SUSPICIOUS_ACTIVITY_REPORTED`
- [x] Calcular `device_id = SHA-256(userAgent + platform)[:32]`
- [x] Actualizar `last_used_at` en `RefreshToken` en cada rotación

### Fase 4 — Reglas automáticas
- [x] Regla nuevo dispositivo: detección + email de alerta asíncrono (`SecurityAlertMailSender`)
- [x] Regla nuevo país: detección + bloqueo condicional si no tiene MFA
- [x] Emails de alerta implementados en texto plano (sin template HTML)
- [ ] **Pendiente:** verificación por email para nuevo país sin MFA (challenge token + código, similar a flujo MFA)

### Fase 5 — API de gestión de sesiones
- [x] `GET /api/v1/auth/sessions` — listar sesiones activas con geolocalización
- [x] `DELETE /api/v1/auth/sessions/{sessionId}` — revocar sesión por ID
- [x] `DELETE /api/v1/auth/sessions/others` — cerrar todas las demás (variante añadida)
- [x] `POST /api/v1/auth/report-suspicious` — reportar actividad sospechosa

### Fase 6 — Frontend
- [x] Sección "Sesiones activas" en página de perfil (`perfil.tsx`)
- [x] Card de sesión con: icono dispositivo, browser, OS, ciudad/país, IP, fecha relativa, botón cerrar
- [x] Badge "Esta sesión" en la sesión actual
- [x] Botón "Cerrar todas las demás"
- [x] Botón "Reportar actividad sospechosa"
- [x] Toast de confirmación al cerrar una sesión

---

## Consideraciones técnicas adicionales

### Parser de User-Agent
Para mostrar "Chrome 124 — Windows 11" en lugar del UA crudo, usar una librería de parsing:
```xml
<dependency>
    <groupId>ua-parser</groupId>
    <artifactId>uap-java</artifactId>
</dependency>
```
O la alternativa `yauaa` (Yet Another UserAgent Analyzer), más mantenida.

### Privacidad (LOPDP Ecuador)
- `country_code` y `city` son datos de geolocalización. Cubriertos por la política de privacidad del sistema.
- `device_id` es un hash — no contiene PII directamente.
- `ip_address` ya se almacena en `auditoria` y `refresh_tokens`, no es nuevo.
- Los `security_events` deben tener retención definida (sugerido: 90 días para eventos normales, 1 año para eventos críticos como `REFRESH_TOKEN_REUSED`).

### Relación con `auditoria`
`security_events` no reemplaza a `auditoria`. Son complementarios:
- `auditoria`: cambios de datos de negocio (quién modificó qué entidad)
- `security_events`: señales de seguridad de autenticación (para detección de anomalías y alertas)

### Multi-instancia
`SecurityEventService` puede ser stateless. Las reglas de detección (nuevo dispositivo, nuevo país) hacen queries a la BD — funcionan correctamente en múltiples instancias sin Redis ni estado compartido.

---

## Registro de implementación

**Fecha:** 2026-04-26

### Archivos creados

| Archivo | Descripción |
|---------|-------------|
| `db/migration/V51__add_refresh_token_fields.sql` | Agrega `last_used_at TIMESTAMPTZ` y `device_id VARCHAR(32)` a `refresh_tokens` |
| `db/migration/V52__create_security_events.sql` | Crea tabla `security_events` con índices en `socio_id`, `event_type`, `ip_address` |
| `auth/entity/SecurityEvent.java` | Entidad JPA mapeada a `security_events`; campo `metadata` usa `@JdbcTypeCode(SqlTypes.JSON)` para JSONB |
| `auth/repository/SecurityEventRepository.java` | Queries `existsKnownDevice()` y `existsKnownCountry()` usadas por las reglas automáticas |
| `auth/service/GeoIpService.java` | Lookup MaxMind GeoLite2 con `@PostConstruct`; falla silenciosamente si el archivo `.mmdb` no está configurado |
| `auth/service/SecurityEventService.java` | `record()` con `Propagation.REQUIRES_NEW`; `applyLoginRules()` ejecuta reglas de nuevo dispositivo y nuevo país; `parseUa()` detecta browser/OS con regex |
| `auth/service/SecurityAlertMailSender.java` | Envía alertas de seguridad `@Async` (mismo patrón que `PasswordResetMailSender`) |
| `auth/dto/SessionResponse.java` | Record con `sessionId`, `platform`, `browser`, `os`, `city`, `country`, `ipAddress`, `createdAt`, `lastUsedAt`, `isCurrent` |

### Archivos modificados

| Archivo | Cambios |
|---------|---------|
| `pom.xml` | Agregada dependencia `com.maxmind.geoip2:geoip2:4.2.0` |
| `application.yml` | Agregada propiedad `sadday.geo.db-path` (vacío por defecto → geolocalización deshabilitada en dev) |
| `auth/entity/RefreshToken.java` | Campos `lastUsedAt` y `deviceId` |
| `auth/repository/RefreshTokenRepository.java` | Eliminado `existsActiveBySocioIdAndPlatform`; agregados `findActiveBySocioId()` y `revokeAllBySocioIdExcept()` |
| `auth/repository/UsuarioAuthRepository.java` | Agregado `s.correo` a la query nativa de `findSocioAuthView` |
| `auth/dto/SocioAuthView.java` | Agregado método `getCorreo()` a la interfaz de proyección |
| `auth/service/AuthService.java` | Eliminado bloque de sesión única; integrados eventos de seguridad en todos los flujos de login/logout/refresh; nuevos métodos `listSessions()`, `revokeSession()`, `revokeOtherSessionsByHash()`, `reportSuspiciousActivity()`; `saveRefreshToken()` ahora devuelve la entidad y acepta `deviceId`; nuevo `computeDeviceId()` |
| `auth/controller/AuthController.java` | Cuatro nuevos endpoints de sesiones: `GET /sessions`, `DELETE /sessions/{sessionId}`, `DELETE /sessions/others`, `POST /report-suspicious` |
| `shared/exception/ErrorCode.java` | Eliminado `SESSION_ALREADY_ACTIVE` |
| `frontend/src/pages/perfil.tsx` | Reemplazado el botón "Cerrar todas las sesiones" por el componente `SessionsSection` con lista completa de sesiones activas |

### Decisiones de implementación

#### Parser de User-Agent — sin librería externa
Se optó por un parser regex propio en `SecurityEventService.parseUa()` en lugar de `yauaa` o `uap-java`. Detecta familia de browser (Chrome, Firefox, Safari, Edge, Opera, IE) y OS (Windows, macOS, Android, iOS, Linux). No incluye versiones. Razón: yauaa añade ~50 MB de dependencias transitivas; para el uso actual (mostrar "Chrome — Windows" en la UI) la precisión extra no justifica el coste.

#### Geolocalización habilitada por configuración
`GeoIpService` arranca sin base de datos si `sadday.geo.db-path` está vacío o el archivo no existe. En local/dev no hay geolocalización; en producción se monta el archivo `GeoLite2-City.mmdb` via variable de entorno `GEOIP_DB_PATH`. La base de datos no se incluye en el repositorio (MaxMind requiere registro gratuito para descargarla).

#### `device_id` — VARCHAR(32) no VARCHAR(64)
La tabla `security_events` usa `VARCHAR(32)` consistente con la definición funcional del FR ("16 bytes en hex = 32 chars"). El esquema SQL del FR decía `VARCHAR(64)` por error — corregido en la migración.

#### `record()` con `REQUIRES_NEW`
Los eventos de seguridad se persisten en una transacción independiente. Si la transacción del llamador (ej. un login que falla por nuevo país) hace rollback, el evento de seguridad igual se guarda.

#### Regla 2 — Nuevo país: bloqueo parcial
El bloqueo está implementado: si el socio no tiene MFA y el país es nuevo, se revoca el token recién creado y se lanza `ACCOUNT_LOCKED` con mensaje descriptivo. Lo que **no** está implementado aún es el flujo completo de verificación por email (challenge token + código de un solo uso). Hay un `// TODO Fase 4` en `AuthService.completarLogin()` marcando el punto de extensión.

#### `DELETE /sessions/others` — endpoint añadido
El FR mencionaba esta variante como opcional. Se implementó para mejorar la UX: el usuario puede cerrar todas las demás sesiones sin cerrar la actual, sin necesidad de usar el botón global "Cerrar todas".

#### `isCurrent` en `GET /sessions`
Se identifica leyendo el refresh token de la cookie `HttpOnly` con `@CookieValue` en el endpoint. El hash del token se compara contra los `tokenHash` almacenados en BD. Esto requiere que el cliente envíe la cookie en la petición (comportamiento estándar con `withCredentials: true` en Axios).

### Pendiente

| Item | Prioridad | Descripción |
|------|-----------|-------------|
| Challenge token por email para nuevo país | Media | Implementar el flujo completo: emitir token de verificación por email, endpoint para confirmar el código, completar el login tras verificación. Punto de entrada: `// TODO Fase 4` en `AuthService.completarLogin()` |
| Retención de `security_events` | Baja | Definir política (90 días normales / 1 año críticos) y agregar job de limpieza similar al scheduler de `deleteExpiredAndRevoked()` en `RefreshToken` |
| `MFA_ENABLED` / `MFA_DISABLED` en eventos | Baja | Registrar en `security_events` cuando el usuario activa o desactiva el 2FA desde `AuthService.confirmMfa()` y `disableMfa()` |
