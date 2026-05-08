# FR-002 — Verificación por email al detectar login desde país desconocido

**Fecha de solicitud:** 2026-04-26
**Fecha de implementación:** 2026-04-26
**Estado:** Implementado
**Prioridad:** Alta
**Área:** Autenticación / Seguridad

---

## Contexto y motivación

FR-001 introdujo detección automática de logins desde países no reconocidos. Cuando se detecta un país nuevo **y el socio no tiene 2FA activo**, el backend revoca el token recién creado y lanza un error `ACCOUNT_LOCKED` genérico. El TODO en `AuthService.completarLogin()` señala explícitamente que el flujo de verificación por email nunca fue implementado:

```java
// TODO Fase 4: emitir challenge token de verificación por email
throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
        "Se detectó un inicio de sesión desde un país desconocido...");
```

El resultado es una experiencia de usuario pobre: el socio recibe un error críptico sin ninguna acción posible para desbloquearse. Lo correcto es un flujo de verificación de dos pasos idéntico en estructura al de MFA pero usando un código de 6 dígitos enviado por correo electrónico.

---

## Flujo solicitado

```
POST /auth/login
  ↓ credenciales válidas + país desconocido + sin 2FA
  202 { countryChallengeToken }
  + email con código de 6 dígitos (15 min de validez)
  
Frontend muestra paso "Verificación de ubicación"
  ↓ usuario ingresa código del email
  
POST /auth/country-challenge/verify
  { challengeToken, code }
  ↓ código válido
  200 { accessToken } + Set-Cookie: refresh_token
```

**Usuarios con 2FA activo:** no se ven afectados. Si tienen MFA y están en país nuevo, ya se autentica el segundo factor con el código TOTP — el nivel de seguridad es suficiente. Solo se envía un email de alerta informativo (ya implementado en FR-001 via `sendNewCountryAlert`).

---

## Cambios solicitados

### Backend

#### 1. Tabla `country_challenge_tokens` (V55)

Similar a `mfa_challenge_tokens` pero con un campo `code_hash` para almacenar el hash del código de 6 dígitos enviado por email.

#### 2. Entidad, repositorio y DTOs

- `CountryChallengeToken` — JPA entity
- `CountryChallengeTokenRepository` — con `findByTokenHash()` y `deleteExpired()`
- `CountryChallengeResponse(challengeToken, expiresIn)` — respuesta del paso 1
- `CountryChallengeVerifyRequest(challengeToken, code)` — cuerpo del paso 2

#### 3. Email de verificación

Nuevo método `sendCountryChallengeCode()` en `SecurityAlertMailSender` que envía el código de 6 dígitos. Se llama de forma síncrona (no `@Async`) para garantizar que el email se envió antes de responder al cliente.

> **Nota:** el resto de alertas de seguridad usan `@Async` porque son informativos. Este email es parte del flujo de login — si falla el envío, debe fallar el login con un error claro, no silenciosamente.

#### 4. Cambios en `AuthService`

- `completarLogin()`: cambia su tipo de retorno de `LoginResult` a `LoginStepResult`. El bloque `blockForNewCountry` ahora genera el challenge y retorna `LoginStepResult.CountryRequired` en lugar de lanzar excepción.
- `completeMfaLogin()`: se adapta al nuevo tipo de retorno de `completarLogin()`.
- Nuevo método `completeCountryChallenge()`: valida el token + código y llama a `completarLogin()` para finalizar el login.

#### 5. Nuevo endpoint en `AuthController`

`POST /api/v1/auth/country-challenge/verify` — público, mismo patrón que `/mfa/login`.

#### 6. `LoginStepResult`

Añadir variante `CountryRequired(CountryChallengeResponse challenge)`.

#### 7. `SchedulerService`

Añadir limpieza de `CountryChallengeToken` expirados en `limpiarTokensExpirados()`.

### Frontend

#### `login.tsx`

Añadir tercer paso `"country_challenge"`:
- Detectado cuando la respuesta del login incluye `countryChallengeToken` (vs `challengeToken` para MFA)
- UI similar al paso MFA: input de 6 dígitos centrado, mensaje explicativo de ubicación desconocida
- Submit llama a `POST /v1/auth/country-challenge/verify`

---

## Decisiones técnicas

| Decisión | Alternativa descartada | Razón |
|---|---|---|
| Código numérico de 6 dígitos | Link de un solo uso en el email | Más fácil copiar el código en el mismo browser donde se está haciendo login; links en email pueden bloquear formularios en móvil |
| `sendCountryChallengeCode()` síncrono | `@Async` como el resto de alertas | Si el email falla, el usuario debe saberlo inmediatamente — no tiene otro camino para desbloquear el login |
| 15 min de expiración (vs 5 min MFA) | 5 min | El usuario necesita tiempo para abrir el correo |
| Máximo 5 intentos (vs 3 MFA) | 3 intentos | Los códigos de email son menos propensos a typos que TOTP, y perder el desafío obliga a reiniciar el login completo |
| `completarLogin()` retorna `LoginStepResult` | Mantener `LoginResult` y lanzar excepción específica | Más coherente con el patrón sealed interface ya establecido; evita usar excepciones para control de flujo |

---

## Registro de implementación

### Archivos nuevos

| Archivo | Descripción |
|---|---|
| `db/migration/V55__create_country_challenge_tokens.sql` | Tabla `country_challenge_tokens` |
| `auth/entity/CountryChallengeToken.java` | JPA entity |
| `auth/repository/CountryChallengeTokenRepository.java` | Repositorio con `findByTokenHash()` y `deleteExpired()` |
| `auth/dto/CountryChallengeResponse.java` | DTO respuesta paso 1: `{ challengeToken, expiresIn }` |
| `auth/dto/CountryChallengeVerifyRequest.java` | DTO request paso 2: `{ challengeToken, code }` |

### Archivos modificados

| Archivo | Cambio |
|---|---|
| `auth/dto/LoginStepResult.java` | Añadida variante `CountryRequired(CountryChallengeResponse)` |
| `auth/service/SecurityAlertMailSender.java` | Añadido `sendCountryChallengeCode()` síncrono |
| `auth/service/AuthService.java` | Ver detalle abajo |
| `auth/controller/AuthController.java` | Switch de `login()` con caso `CountryRequired`; nuevo endpoint `POST /country-challenge/verify` |
| `scheduler/SchedulerService.java` | `CountryChallengeTokenRepository` + limpieza en `limpiarTokensExpirados()` |
| `frontend/src/pages/login.tsx` | Paso `country_challenge` con icono `Globe`, detección via campo `countryChallengeToken` |

---

### Schema de `country_challenge_tokens`

```sql
id          UUID        PRIMARY KEY DEFAULT gen_random_uuid()
socio_id    UUID        NOT NULL
token_hash  VARCHAR(64) NOT NULL UNIQUE   -- SHA-256 del challengeToken enviado al frontend
code_hash   VARCHAR(64) NOT NULL          -- SHA-256 del código de 6 dígitos enviado por email
ip_address  VARCHAR(45)
user_agent  TEXT
expires_at  TIMESTAMP   NOT NULL          -- 15 minutos desde la creación
used        BOOLEAN     NOT NULL DEFAULT FALSE
attempts    SMALLINT    NOT NULL DEFAULT 0
created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
```

---

### Detalle de cambios en `AuthService`

**`completarLogin()` — tipo de retorno**

Cambia de `private LoginResult` a `private LoginStepResult`. Todos sus callers se actualizan:

- `login()`: ya no envuelve en `LoginStepResult.Completed(...)` — devuelve el resultado directamente.
- `completeMfaLogin()`: recibe `LoginStepResult`, extrae `Completed.result()` vía `instanceof`. Los usuarios con MFA nunca llegan a `CountryRequired` porque `applyLoginRules()` no bloquea cuando `hasMfa = true`.
- `completeCountryChallenge()`: mismo patrón que `completeMfaLogin()`.

**Bloque `blockForNewCountry` (antes el TODO)**

```java
if (blockForNewCountry) {
    // 1. Revocar el RT recién creado — el login queda pausado
    rt.setRevoked(true); refreshTokenRepository.save(rt);

    // 2. Generar challengeToken (UUID aleatorio) y código de 6 dígitos
    String rawChallenge = generateSecureToken();
    String code = String.format("%06d", secureRandom.nextInt(1_000_000));

    // 3. Guardar en BD con hashes SHA-256 (nunca en texto plano)
    CountryChallengeToken saved = CountryChallengeToken.builder()
        .socioId(usuario.getSocioId())
        .tokenHash(hashToken(rawChallenge))
        .codeHash(hashToken(code))
        .expiresAt(LocalDateTime.now().plusSeconds(900))
        ...build();
    countryChallengeTokenRepository.save(saved);

    // 4. Enviar código por email de forma SÍNCRONA
    alertMailSender.sendCountryChallengeCode(correo, nombre, code, country, city, browser);

    // 5. Retornar 202 con el challengeToken (sin acceso al código)
    return new LoginStepResult.CountryRequired(
        new CountryChallengeResponse(rawChallenge, 900));
}
```

**`completeCountryChallenge()` — flujo de verificación**

1. Busca el challenge por `hashToken(request.challengeToken())`.
2. Valida que no esté expirado ni usado (`isValid()`).
3. Valida que no se hayan superado 5 intentos.
4. Compara `hashToken(request.code())` contra `challenge.getCodeHash()`.
5. Si incorrecto: incrementa `attempts`, guarda, lanza `TOKEN_INVALID` con intentos restantes.
6. Si correcto: marca como `used`, llama a `completarLogin(usuario, ip, userAgent)`.

**Por qué `completarLogin()` no vuelve a bloquear tras la verificación**

Cuando se llama `completeCountryChallenge()` y este llama `completarLogin()`, el flujo interno vuelve a llamar `applyLoginRules()`. En ese punto `existsKnownCountry()` devuelve `true` porque durante el primer intento (el que generó el challenge) ya se registró un evento `NEW_COUNTRY_LOGIN` para ese `socio_id` + `country_code`. Como el query filtra por `event_type IN ('LOGIN_SUCCESS', 'NEW_COUNTRY_LOGIN')`, la presencia del `NEW_COUNTRY_LOGIN` es suficiente para considerar el país como conocido. No hay doble bloqueo.

---

### Distinción frontend entre MFA y country challenge

Ambos retornan HTTP 202. El frontend distingue por el campo presente en `res.data`:

| Campo presente | Acción |
|---|---|
| `challengeToken` | Paso MFA — POST `/v1/auth/mfa/login` |
| `countryChallengeToken` | Paso país — POST `/v1/auth/country-challenge/verify` |
| ninguno | Login completado — guardar auth y navegar |

Los nombres de campo son distintos por diseño para que la detección sea explícita y no dependa del código HTTP ni de flags adicionales.
