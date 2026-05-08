# Arquitectura de seguridad — Sadday App

**Última actualización:** 2026-04-26
**Stack:** Spring Boot 3.x / Java 21 · React 19 / Vite · PostgreSQL 16 · AWS S3/Lightsail · Cloudflare

---

## Índice

1. [Infraestructura de red y cadena de confianza](#1-infraestructura-de-red-y-cadena-de-confianza)
2. [Autenticación — flujos y tokens](#2-autenticación--flujos-y-tokens)
3. [Almacenamiento de credenciales y secretos](#3-almacenamiento-de-credenciales-y-secretos)
4. [Autorización en dos capas](#4-autorización-en-dos-capas)
5. [Rate limiting y protección de endpoints](#5-rate-limiting-y-protección-de-endpoints)
6. [HTTP Security headers](#6-http-security-headers)
7. [Auditoría append-only](#7-auditoría-append-only)
8. [Seguridad del frontend](#8-seguridad-del-frontend)
9. [Configuración por entorno y gestión de secretos](#9-configuración-por-entorno-y-gestión-de-secretos)
10. [Pipeline CI/CD — supply chain security](#10-pipeline-cicd--supply-chain-security)

---

## 1. Infraestructura de red y cadena de confianza

### 1.1 Topología en producción

```
Usuario (browser / app móvil)
        │
        │  HTTPS (TLS 1.2+, certificado gestionado por Cloudflare)
        ▼
  ┌─────────────┐
  │  Cloudflare │  WAF, DDoS, TLS termination
  │  (edge)     │  Añade CF-Connecting-IP = IP real del usuario
  └─────────────┘
        │
        │  HTTPS → Nginx en AWS Lightsail (misma VM)
        ▼
  ┌─────────────────────────────────────────────────────┐
  │  AWS Lightsail — Ubuntu 24.04                       │
  │                                                     │
  │  ┌──────────┐    proxy_pass http://127.0.0.1:8080   │
  │  │  Nginx   │ ─────────────────────────────────►    │
  │  │  :443    │                                        │
  │  └──────────┘    ┌────────────────────────────────┐ │
  │                  │  Spring Boot API                │ │
  │                  │  127.0.0.1:8080 (solo loopback) │ │
  │                  └────────────────────────────────┘ │
  │                                                     │
  │                  ┌────────────────────────────────┐ │
  │                  │  PostgreSQL  (sin puerto ext.)  │ │
  │                  └────────────────────────────────┘ │
  └─────────────────────────────────────────────────────┘
```

**Puntos clave:**

- El backend escucha exclusivamente en `127.0.0.1:8080` (`docker-compose.prod.yml`, línea 48: `"127.0.0.1:8080:8080"`). Ningún puerto está accesible desde internet directamente.
- PostgreSQL no expone su puerto al exterior en producción (`docker-compose.prod.yml`, línea 19: `ports: []`).
- Nginx termina TLS y hace `proxy_pass` a loopback.

### 1.2 Cadena de confianza para la IP del cliente

Esta topología crea una cadena de responsabilidad en tres niveles:

```
Nivel 1 — Cloudflare:
  - Recibe la IP real del usuario del socket TCP.
  - Inyecta el header: CF-Connecting-IP: <IP real del usuario>
  - Cloudflare garantiza que este header es la IP real y no puede ser falsificado
    por el usuario si Nginx rechaza tráfico que no provenga de rangos de Cloudflare.

Nivel 2 — Nginx:
  - SOLO acepta tráfico entrante desde las IPs publicadas de Cloudflare.
    Configuración Nginx recomendada:
        # Rangos IPv4 de Cloudflare (https://www.cloudflare.com/ips-v4/)
        allow 103.21.244.0/22;
        allow 103.22.200.0/22;
        allow 162.158.0.0/15;
        ... (resto de rangos Cloudflare)
        deny all;
  - Reenvía CF-Connecting-IP al backend como header.
  - Desde la perspectiva del backend, el remoteAddr de Nginx siempre es 127.0.0.1.

Nivel 3 — Backend (Spring Boot):
  - Solo confía en CF-Connecting-IP / X-Forwarded-For si getRemoteAddr() == 127.0.0.1.
  - Si la petición no llega por loopback (alguien conectó directamente al puerto),
    ignora los headers y usa getRemoteAddr() como IP del cliente.
  - Configuración: sadday.security.trusted-proxy-cidrs = [127.0.0.1/32, ::1/128]
```

### 1.3 Por qué los CIDRs de Cloudflare NO están en el backend

Este es un detalle no obvio que merece explicación explícita.

**Escenario incorrecto (lo que NO se hace):**
```yaml
# INCORRECTO para esta arquitectura
trusted-proxy-cidrs:
  - "103.21.244.0/22"   # Cloudflare
  - "162.158.0.0/15"    # Cloudflare
  ...
```
Si el backend confiara en los CIDRs de Cloudflare, un atacante que consiguiera conectarse directamente al puerto 8080 desde una IP de Cloudflare (Cloudflare tiene IPs públicas y compartidas) podría enviar `CF-Connecting-IP: 8.8.8.8` y el backend lo aceptaría como IP real, eludiendo el rate limiting y falseando la auditoría.

**Escenario correcto (lo que sí se hace):**
La validación de que el tráfico proviene de Cloudflare la hace Nginx a nivel de red, antes de que la petición llegue a Java. El backend solo necesita saber que el vecino inmediato (Nginx en la misma máquina) es confiable: `127.0.0.1`.

```
Responsabilidad de cada capa:
  Cloudflare → garantiza la IP real del usuario (header CF-Connecting-IP)
  Nginx      → garantiza que el tráfico venga de Cloudflare (allowlist de CIDRs)
  Backend    → garantiza que el tráfico venga de Nginx (remoteAddr == 127.0.0.1)
```

**Implementación** (`ClientIpExtractor.java`):
```java
public String extractIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();

    if (!isTrustedProxy(remoteAddr)) {
        // No viene de Nginx: ignorar todos los headers — usar IP directa
        return remoteAddr;
    }

    // Solo aquí se confía en el header inyectado por Cloudflare via Nginx
    String cfIp = request.getHeader("CF-Connecting-IP");
    if (isValidIp(cfIp)) return cfIp;

    // Fallback a X-Forwarded-For si no hay CF-Connecting-IP
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null) {
        String first = xff.split(",")[0].strip();
        if (isValidIp(first)) return first;
    }

    return remoteAddr;
}
```

La IP extraída se usa en: rate limiting (`RateLimitFilter`), auditoría (`AuditService`) y gestión de sesiones (`AuthService`).

---

## 2. Autenticación — flujos y tokens

### 2.1 Estrategia de tokens

El sistema usa dos tipos de tokens con responsabilidades distintas:

| Token | Tipo | Transporte | Almacenamiento cliente | Vida útil | Almacenamiento servidor |
|-------|------|-----------|------------------------|-----------|------------------------|
| Access token | JWT firmado RS256 | `Authorization: Bearer` header | Memoria JS (no localStorage) | 15 min | Solo en tránsito — sin estado en BD |
| Refresh token | UUID opaco (32 bytes aleatorios) | Cookie `HttpOnly; Secure; SameSite=Strict` | Cookie (inaccesible a JS) | 30 días | Hash SHA-256 en tabla `refresh_tokens` |

**Por qué esta separación:**
- El access token viaja en cada request y debe ser verificable sin BD → JWT firmado.
- El refresh token solo se usa para renovar sesión → opaco, almacenado como hash, invalidable individualmente.
- Nunca se almacena ningún token en `localStorage` para prevenir robo via XSS.

### 2.2 Firma JWT (RS256)

Implementación en `JwtConfig.java` y `JwtService.java`:

- Algoritmo: **RS256** (RSA con SHA-256). La firma usa clave privada; la verificación usa clave pública.
- Las claves se cargan desde archivos PEM en el filesystem, nunca hardcodeadas.
  - En local: `classpath:keys/private.pem` / `classpath:keys/public.pem`
  - En prod: `file:${JWT_PRIVATE_KEY_PATH}` — ruta del servidor pasada como variable de entorno.
- `JwtEncoder` y `JwtDecoder` están separados: el encoder (solo backend) usa ambas claves; el decoder usa solo la pública.
- Claims incluidos en el access token: `sub` (username), `socio_id`, `rol`, `nombre`, `iat`, `exp`, `iss`.
- No se incluyen datos sensibles: sin cédula, sin email, sin TOTP secrets.

### 2.3 Refresh tokens rotativos y detección de robo

Implementación en `AuthService.java`:

```
Flujo de rotación:
  1. Cliente envía refresh token actual (cookie HttpOnly).
  2. Backend calcula SHA-256(token) y busca en BD.
  3. Si está válido: revoca el token actual, emite uno nuevo, devuelve nuevo access token.
  4. La cookie se remplaza con el nuevo refresh token.

Detección de robo (AuthService.java:238-243):
  Si se presenta un token ya revocado → alguien lo robó y lo está usando.
  Acción: revocar TODOS los refresh tokens del socio (cierre de todas las sesiones).
```

Solo se almacena `hash = SHA-256(rawToken)` en la columna `token_hash`. El token en claro nunca toca la BD.

### 2.4 Flujo de login con MFA opcional

```
Login sin 2FA:
  POST /auth/login → valida username + Argon2id(password) → emite access + refresh → 200 OK

Login con 2FA activo:
  POST /auth/login → valida username + password
                   → genera MfaChallengeToken (32 bytes aleatorios, hash en BD, TTL 5 min, max 3 intentos)
                   → devuelve 202 + rawChallengeToken
  POST /auth/mfa/login → valida challengeToken + código TOTP de 6 dígitos
                       → si correcto: marca challenge como used=true (un solo uso)
                       → emite access + refresh → 200 OK
```

El challenge token también se almacena como hash. El límite de 3 intentos por challenge previene fuerza bruta del TOTP sin necesitar otro bloqueo de cuenta.

### 2.5 Bloqueo por intentos fallidos

```
authService.handleFailedAttempt():
  failedAttempts++
  Si failedAttempts >= maxLoginAttempts (default: 3):
    loginBlocked = true
    blockedUntil = now + lockoutDurationHours (default: 24 h)

Auto-desbloqueo: se verifica blockedUntil en cada intento sin intervención del admin.
Mismo mensaje de error para "usuario no existe" y "contraseña incorrecta" → previene enumeración.
```

---

## 3. Almacenamiento de credenciales y secretos

### 3.1 Contraseñas — Argon2id

Configuración en `SecurityConfig.java:172`:

```java
new Argon2PasswordEncoder(
    16,      // saltLength: 16 bytes
    32,      // hashLength: 32 bytes (256 bits)
    1,       // parallelism: 1 hilo
    19_456,  // memory: 19 MB (19 * 1024 KB)
    2        // iterations: 2 pasadas
)
```

Parámetros alineados con las recomendaciones OWASP para 2026. El hash resultante incluye el salt, el algoritmo y los parámetros codificados en el formato PHC string, por lo que es autocontenido.

### 3.2 TOTP secrets — AES-256-GCM

Implementación en `TotpService.java`:

Los secrets TOTP no se almacenan en texto claro. Antes de guardarse en BD pasan por:

```
1. Generación:    SecureRandom.nextBytes(20) → 20 bytes de entropía real
2. Cifrado:
   - IV:          SecureRandom.nextBytes(12) → 12 bytes aleatorios (nuevo IV por cada cifrado)
   - Algoritmo:   AES/GCM/NoPadding con tag de autenticación de 128 bits
   - Clave:       AES-256 (32 bytes) cargada de TOTP_ENCRYPTION_KEY al arranque
3. Almacenamiento en BD: Base64(IV || ciphertext || GCM-auth-tag)

Verificación:
   - Se descifra el secret con la clave AES.
   - Se computa TOTP RFC 6238 para ventana de ±1 paso (±30 segundos de tolerancia).
   - Se acepta si coincide con alguno de los 3 valores de la ventana.
```

El GCM tag garantiza integridad: si alguien modifica el ciphertext en BD, el descifrado falla.

### 3.3 Reset de contraseña — tokens de un solo uso

```
Generación:  SecureRandom → 32 bytes → token opaco
Hash:        SHA-256(token) almacenado en BD
TTL:         15 minutos (configurable con PWD_RESET_EXPIRY)
Límite:      3 solicitudes por IP por cada 15 minutos (rate limiter)
Invalidación: se destruye al usarse (un solo uso) o al expirar
```

### 3.4 Claves RSA para JWT

```
Generación:  scripts/generate-keys.sh → openssl genrsa + openssl rsa (2048 bits mínimo)
Local:       classpath:keys/  (en .gitignore — NO se commitean)
Producción:  filesystem del servidor, ruta pasada como JWT_PRIVATE_KEY_PATH
Separación:  JwtEncoder tiene privada+pública; JwtDecoder solo tiene pública
```

---

## 4. Autorización en dos capas

El acceso se verifica en dos momentos independientes para mayor defensa en profundidad.

### Capa 1 — SecurityFilterChain (por URL)

Configurado en `SecurityConfig.java`:

```
Público (sin token):
  POST /api/v1/auth/login
  POST /api/v1/auth/refresh
  POST /api/v1/auth/forgot-password
  POST /api/v1/auth/reset-password
  GET/POST /api/v1/registro/**
  GET /actuator/health

Autenticado (cualquier rol):
  POST /api/v1/auth/logout
  POST /api/v1/auth/logout-all
  POST /api/v1/auth/mfa/**

Solo Admin, Secretaria o Directivo:
  /api/v1/admin/**

Swagger/OpenAPI:
  /swagger-ui/**, /v3/api-docs/**
  → Libre en local/staging, solo ADMIN o SECRETARIA en prod

Resto:
  .anyRequest().authenticated() — todo lo demás requiere token válido
```

### Capa 2 — @PreAuthorize (por método)

Anotaciones en los controladores y servicios para verificar el rol con mayor granularidad. Ejemplo: desbloquear cuenta está restringido a `ROLE_ADMIN` aunque el endpoint `/admin/**` ya permita SECRETARIA y DIRECTIVO.

### JwtAuthFilter

El filtro (`JwtAuthFilter.java`) extrae el Bearer token, lo valida con `JwtService.isTokenValid()`, construye un `UsernamePasswordAuthenticationToken` con el authority `ROLE_<ROL>` y un `SaddayAuthDetails` con `socioId` y `rol`. Esto evita consultas a BD en cada request autenticado.

Orden de filtros en la cadena: `RateLimitFilter → JwtAuthFilter → UsernamePasswordAuthenticationFilter`.

---

## 5. Rate limiting y protección de endpoints

### 5.1 Configuración por endpoint

Implementado en `RateLimitFilter.java` con Bucket4j + caché Caffeine:

| Endpoint | Límite | Ventana |
|----------|--------|---------|
| `POST /auth/login` | 10 intentos | 1 minuto |
| `POST /auth/forgot-password` | 5 intentos | 5 minutos |
| `POST /auth/reset-password` | 5 intentos | 5 minutos |
| `POST /auth/refresh` | 60 intentos | 1 minuto |
| `POST /registro/complete` | 10 intentos | 10 minutos |
| `GET /registro/token-info` | 10 intentos | 10 minutos |

### 5.2 Clave de rate limiting: IP validada

La IP usada como clave del bucket es la devuelta por `ClientIpExtractor.extractIp()` — es decir, la IP real del usuario (obtenida de `CF-Connecting-IP` cuando el request viene por Nginx, o `getRemoteAddr()` directamente en cualquier otro caso). Esto garantiza que el rate limiting aplica al usuario real y no al proxy.

### 5.3 Protección de memoria contra DDoS de IPs

```java
private static final long MAX_CACHE_SIZE = 10_000;       // máx 10.000 IPs por tipo de endpoint
private static final Duration EVICTION_AFTER_ACCESS = Duration.ofMinutes(30);
```

Si un atacante rota 10.001 IPs distintas, Caffeine empieza a evict entradas antiguas. El límite de memoria es acotado; no hay crecimiento ilimitado.

### 5.4 Bloqueo de cuenta vs. rate limit por IP

Son mecanismos complementarios con responsabilidades distintas:

- **Rate limit por IP** → mitiga ataques de volumen desde una IP concreta (bruteforce directo).
- **Bloqueo de cuenta** → mitiga ataques distribuidos donde cada IP hace pocos intentos contra la misma cuenta.

Ambos deben estar activos para cubrir los dos vectores.

---

## 6. HTTP Security headers

### 6.1 Backend (API Spring Boot)

Configurado en `SecurityConfig.java`:

| Header | Valor | Propósito |
|--------|-------|-----------|
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` | API pura — no sirve HTML. Bloquea todo excepto lo explícito. Previene que el browser interprete respuestas JSON como HTML. |
| `X-Content-Type-Options` | `nosniff` | Previene MIME-type sniffing |
| `X-Frame-Options` | `DENY` | Previene clickjacking |
| `Referrer-Policy` | `no-referrer` | No envía `Referer` en ningún request |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Solo en prod. Fuerza HTTPS por 1 año. |

Error handling hardening (`application.yml`):
```yaml
server.error:
  include-stacktrace: never   # No revelar stack traces al cliente
  include-message:   never    # No revelar mensajes de excepción internos
  include-binding-errors: never
```

### 6.2 Frontend (Nginx del contenedor React)

Configurado en `frontend/nginx.conf`:

| Header | Valor |
|--------|-------|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; worker-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` |

`style-src 'unsafe-inline'` es necesario porque Radix UI, Recharts y Sonner inyectan estilos inline para posicionamiento dinámico de overlays. Los scripts sí están restringidos a `'self'`.

Los headers se declaran en dos bloques en nginx.conf porque Nginx anula la herencia en bloques `location` hijos — están duplicados explícitamente en el bloque de assets estáticos.

---

## 7. Auditoría append-only

### 7.1 Principios

Implementado en `AuditService.java`:

- La tabla `auditoria` en PostgreSQL NO tiene permisos de `UPDATE` ni `DELETE` para el usuario de la aplicación — es físicamente append-only.
- Los registros se escriben de forma **asíncrona** (`@Async`) para no bloquear el flujo de negocio.
- Si falla la escritura de auditoría, se loguea el error pero no se propaga excepción (el negocio no se interrumpe por un fallo de auditoría).
- `actor_username` es un string libre (no FK a usuarios) para poder registrar intentos de login de usernames inexistentes.

### 7.2 Qué se registra

Eventos críticos mediante `@Auditable` en servicios o llamada manual a `auditService.registrar()`:

| Acción | Disparador |
|--------|-----------|
| `LOGIN_SUCCESS` / `LOGIN_FAILED` / `LOGIN_BLOCKED` | Cada intento de login |
| `LOGIN_MFA_CHALLENGE` / `LOGIN_MFA_FAILED` | Flujo 2FA |
| `LOGIN_SESSION_CONFLICT` | Intento de doble sesión en la misma plataforma |
| `REFRESH_TOKEN_STOLEN` | Reutilización de refresh token revocado |
| `LOGOUT` / `LOGOUT_ALL` | Cierre de sesión |
| `DELETE_SOCIO` / `UPDATE_SOCIO` / `CAMBIAR_ROL_SOCIO` | Modificaciones de socios |
| `CHANGE_PASSWORD` / `RESET_PASSWORD` | Cambios de contraseña |
| `MFA_SETUP` / `MFA_DISABLED` | Cambios en 2FA |

### 7.3 Campos registrados

```sql
actor_username   -- quien ejecutó la acción (o username intentado en login fallido)
accion           -- código de acción ("LOGIN_FAILED", "DELETE_SOCIO", ...)
entidad_afectada -- tabla afectada ("socios", "usuarios_auth", ...)
entidad_id       -- UUID de la entidad (si aplica)
datos_anteriores -- JSON snapshot del estado previo (si aplica)
datos_nuevos     -- JSON snapshot del estado posterior (si aplica)
ip_address       -- IP real del cliente (via ClientIpExtractor)
user_agent       -- User-Agent del cliente
resultado        -- "SUCCESS", "FAILED", "BLOCKED", "PENDING"
detalle          -- mensaje adicional (solo en fallos)
created_at       -- timestamp automático del servidor
```

---

## 8. Seguridad del frontend

### 8.1 Almacenamiento de tokens

```
Access token:  memoria JS (variable en auth-store.ts) — nunca en localStorage ni cookies
Refresh token: cookie HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth
```

La cookie de refresh token usa `Path=/api/v1/auth` para que el browser solo la envíe a ese path específico. `SameSite=Strict` impide que se envíe en requests cross-site. `HttpOnly` impide que JavaScript la lea (protección frente a XSS).

### 8.2 Renovación automática del access token

En `frontend/src/lib/api.ts`, el interceptor de respuesta de Axios:

1. Detecta un 401 en cualquier request.
2. Si no hay ya un refresh en curso, llama a `POST /api/v1/auth/refresh` (la cookie se envía automáticamente).
3. Mientras espera el refresh, encola los requests fallidos.
4. Al completarse el refresh, reintenta los requests encolados con el nuevo access token.
5. Si el refresh también falla, limpia el estado local y redirige a `/login`.

### 8.3 Sin `dangerouslySetInnerHTML`, `eval` ni sinks peligrosos

El código React no usa `dangerouslySetInnerHTML`, `eval()`, `document.write()`, `innerHTML` ni `insertAdjacentHTML()`. Toda la renderización pasa por el virtual DOM de React, que escapa automáticamente.

---

## 9. Configuración por entorno y gestión de secretos

### 9.1 Perfiles de Spring Boot

| Perfil | Uso | TLS | Cookie Secure | TOTP Key | Detalles Actuator |
|--------|-----|-----|--------------|----------|-------------------|
| `local` | Desarrollo | No | `false` | Default (32 bytes 0, solo dev) | DEBUG logs |
| `qa` | Staging / testing | No | `false` | Variable de entorno | `show-details: always` (interno) |
| `prod` | Producción | Sí (via Cloudflare+Nginx) | `true` | Variable de entorno (obligatoria) | `show-details: never` |

### 9.2 Variables de entorno sensibles en producción

Valores que deben estar definidos como variables de entorno o Docker secrets en prod (la app no arranca si faltan):

| Variable | Descripción |
|----------|-------------|
| `TOTP_ENCRYPTION_KEY` | Base64 de 32 bytes aleatorios para AES-256. Generar: `openssl rand -base64 32` |
| `JWT_PRIVATE_KEY_PATH` | Ruta al archivo PEM de la clave privada RSA |
| `JWT_PUBLIC_KEY_PATH` | Ruta al archivo PEM de la clave pública RSA |
| `DB_PASSWORD` | Contraseña de PostgreSQL |
| `ADMIN_INITIAL_PASSWORD` | Contraseña inicial del admin (sin default en prod) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Credenciales SMTP de Amazon SES |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | Credenciales AWS S3 |

### 9.3 Archivos explícitamente excluidos del repositorio

```gitignore
*.pem, *.key, *.p12, *.jks   — claves criptográficas
.env, .env.*                  — variables de entorno locales
frontend/.env.local           — configuración local del frontend
```

---

## 10. Pipeline CI/CD — supply chain security

### 10.1 Visión general

```
Push a main/develop
        │
        ├── ci.yml (en paralelo)
        │     ├── Backend: mvn verify (tests integración con Testcontainers + PostgreSQL real)
        │     ├── Frontend: tsc --noEmit + vitest + pnpm build
        │     ├── SonarCloud: análisis de calidad + cobertura JaCoCo
        │     └── Semgrep SAST: p/java, p/spring, p/react, p/owasp-top-ten, p/secrets
        │
        └── deploy.yml (en paralelo)
              ├── Backend:  Build image → Trivy scan → Push GHCR → cosign sign → SBOM CycloneDX
              ├── Frontend: Build image → Trivy scan → Push GHCR → cosign sign → SBOM CycloneDX
              ├── Verificar firma cosign antes de deploy
              └── Deploy (SSH + docker compose, imagen por digest)

security.yml (scheduled, cada lunes 07:00 UTC + push a main):
  ├── Snyk — dependencias Maven (backend)
  ├── Snyk — dependencias npm/pnpm (frontend)
  └── OWASP Dependency Check (base NVD, falla en CVSS ≥ 9)
```

### 10.2 Firma de imágenes con cosign (supply chain)

```bash
# Firma keyless via OIDC de GitHub Actions (deploy.yml:117)
cosign sign --yes "${IMAGE}@${DIGEST}"

# Verificación antes de cada deploy (deploy.yml:382)
cosign verify \
  --certificate-identity="https://github.com/REPO/.github/workflows/deploy.yml@refs/heads/main" \
  --certificate-oidc-issuer="https://token.actions.githubusercontent.com" \
  "${IMAGE}@${DIGEST}"
```

El deploy usa el digest verificado (no el tag `latest`): `docker pull IMAGE@sha256:...`. Esto garantiza que la imagen desplegada es exactamente la firmada por el pipeline, no una imagen que alguien pudo haber subido al registry bajo el mismo tag.

### 10.3 SBOM (Software Bill of Materials)

Por cada imagen Docker se genera un SBOM en formato CycloneDX JSON y se adjunta como attestation cosign al digest de la imagen en GHCR. Esto permite auditar qué dependencias exactas están en cada versión desplegada.

### 10.4 Escaneo de vulnerabilidades

| Herramienta | Qué escanea | Cuándo | Bloquea |
|------------|-------------|--------|---------|
| Trivy | Imagen Docker (OS + libs) | En cada push a main/develop | Sí — `exit-code: 1` en CRITICAL/HIGH |
| Semgrep | Código fuente (SAST) | En cada push | No — resultados vía SARIF |
| Snyk | Dependencias Maven y npm | Semanal + push a main | No — resultados vía SARIF |
| OWASP DC | Dependencias Maven vs NVD | Semanal + push a main | No (log) salvo CVSS ≥ 9 |

Trivy bloquea el deploy si encuentra vulnerabilidades CRITICAL o HIGH en la imagen. El resto son herramientas de visibilidad que publican en la pestaña Security > Code Scanning de GitHub.

### 10.5 Tests de integración con base de datos real

Los tests de backend usan Testcontainers para levantar un PostgreSQL real durante CI. No se usan mocks de base de datos. Esto garantiza que las migraciones Flyway son correctas y que las queries JPA funcionan contra PostgreSQL real.

---

## Apéndice — Decisiones de diseño destacadas

### Por qué JWT RS256 y no HS256

RS256 usa un par de claves asimétricas. Si en el futuro se añadieran microservicios o sistemas externos que necesitaran verificar tokens, solo necesitarían la clave pública — sin compartir ningún secreto. HS256 requeriría compartir la misma clave secreta entre todos los verificadores.

### Por qué el refresh token es opaco y no un JWT

Un JWT de refresh sería auto-contenido y no requeriría BD para verificarse, lo que parece más eficiente. Pero también significa que no se puede revocar individualmente antes de que expire (solo se puede mantener una blocklist). Un token opaco almacenado como hash en BD permite revocación inmediata, rotación verificable y detección de robo.

### Por qué Argon2id y no bcrypt o scrypt

Argon2id es el ganador de la Password Hashing Competition (2015) y es la recomendación actual de OWASP, NIST SP 800-63B e IETF RFC 9106. Combina resistencia a ataques de GPU (como Argon2d) y resistencia a ataques de canal lateral (como Argon2i). bcrypt no escala bien en uso de memoria; scrypt tiene un parámetro de memoria pero no el componente de resistencia a canal lateral de Argon2id.

### Por qué auditoría asíncrona y no síncrona

Si la auditoría fuera síncrona y la BD de auditoría estuviera lenta o saturada, todas las operaciones del sistema se degradarían. Al ser asíncrona, un fallo de auditoría no interrumpe el servicio. La contrapartida es que en un crash extremo del proceso podría perderse el registro de un evento. Esta es una compensación aceptable dado que la auditoría es para forensia, no para control transaccional.

### Por qué el backend se enlaza a 127.0.0.1 y no a 0.0.0.0

Si se enlazara a `0.0.0.0:8080`, el puerto estaría accesible directamente desde internet si el firewall de Lightsail estuviera mal configurado. Al enlazarse solo a loopback, aunque el firewall falle o sea mal configurado, nadie puede conectar al backend directamente — es una segunda barrera de defensa independiente de la configuración de red.
