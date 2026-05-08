# Diagrama 02 — Flujos de Autenticación

## Flujo 1: Login (con y sin 2FA)

```mermaid
sequenceDiagram
    actor U as Usuario
    participant CF as Cloudflare WAF
    participant API as Spring Boot API
    participant DB as PostgreSQL

    U->>CF: POST /api/v1/auth/login {username, password}
    Note over CF: WAF: rate limit global, IPs bloqueadas

    CF->>API: POST /api/v1/auth/login
    Note over API: Rate limit por IP (Bucket4j in-memory)

    API->>DB: SELECT usuarios_auth WHERE username = ?
    DB-->>API: registro o null

    alt Usuario no existe
        API-->>U: 401 "Credenciales incorrectas"
        Note over API: Mismo mensaje que password incorrecta<br/>⚠️ Previene enumeración de usuarios
    end

    API->>API: Argon2id.verify(password, hash)
    Note over API: ⚠️ Si falla: incrementar failed_attempts

    alt failed_attempts >= MAX_INTENTOS_LOGIN (config)
        API->>DB: UPDATE SET login_blocked=true, blocked_until=NOW()+Xh
        API->>DB: INSERT INTO auditoria (LOGIN_BLOCKED)
        API-->>U: 429 "Cuenta bloqueada"
    end

    alt Password incorrecta (bajo umbral)
        API->>DB: UPDATE SET failed_attempts = failed_attempts + 1
        API->>DB: INSERT INTO auditoria (LOGIN_FAILED)
        API-->>U: 401 "Credenciales incorrectas"
    end

    alt Login correcto, 2FA habilitado, sin mfaCode en request
        API->>DB: INSERT INTO auditoria (MFA_REQUIRED)
        API-->>U: 401 {code: "MFA_REQUIRED"}<br/>(no se emite ningún token aún)
        Note over U: El cliente muestra campo TOTP.<br/>Reenvía el mismo POST con mfaCode.

        U->>API: POST /api/v1/auth/login {username, password, mfaCode}
        API->>API: Descifrar totp_secret (AES-256-GCM)<br/>TOTP.verify(mfaCode, secret)
        Note over API: Ventana ±30s, anti-replay por timestamp

        alt TOTP inválido
            API->>DB: INSERT INTO auditoria (LOGIN_FAILED_MFA)
            API-->>U: 401 "Código 2FA inválido"
        end
    end

    Note over API: Login correcto (con o sin 2FA)
    API->>DB: UPDATE SET failed_attempts=0, last_login=NOW()
    API->>API: Generar Access Token (JWT RS256, 15 min)<br/>Claims: sub=socioId, rol, nombre, nivelTecnico
    API->>API: Generar Refresh Token (UUID aleatorio, 30 días)
    API->>DB: INSERT refresh_tokens(SHA256(token), ip, ua, expires_at)
    API->>DB: INSERT INTO auditoria (LOGIN_SUCCESS)

    API-->>U: 200 body: {accessToken, tokenType, expiresIn,<br/>socioId, username, nombre, rol,<br/>nivelTecnico, passwordMustChange}
    Note over U,API: Set-Cookie: refresh_token=...<br/>HttpOnly, Secure, SameSite=Strict<br/>Path=/api/v1/auth, Max-Age=2592000<br/>⚠️ El refresh token NUNCA va en el body JSON
    Note over U: Si passwordMustChange=true:<br/>redirigir a /change-password
```

---

## Flujo 2: Refresh de Access Token (Rotación)

```mermaid
sequenceDiagram
    actor U as Usuario
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Note over U,API: El browser envía la cookie refresh_token automáticamente<br/>(no requiere ningún código en el cliente)
    U->>API: POST /api/v1/auth/refresh<br/>(sin body — refresh token llega como cookie HttpOnly)

    API->>API: Leer refresh_token desde @CookieValue
    alt Cookie ausente o vacía
        API-->>U: 401 "Token de sesión ausente o inválido"
    end

    API->>API: hash_sha256(refreshToken)
    API->>DB: SELECT * FROM refresh_tokens WHERE token_hash = ?
    DB-->>API: registro

    alt Token no encontrado
        API-->>U: 401 "Token inválido"
    end

    alt Token revocado (ya fue usado — posible robo)
        Note over API: ⚠️ Robo de token detectado<br/>Revocar TODOS los tokens del usuario
        API->>DB: UPDATE refresh_tokens SET revoked=true WHERE socio_id = ?
        API->>DB: INSERT INTO auditoria (TOKEN_THEFT_DETECTED)
        API-->>U: 401 "Sesión inválida. Inicie sesión nuevamente"
    end

    alt Token expirado
        API->>DB: UPDATE SET revoked=true WHERE id = ?
        API-->>U: 401 "Sesión expirada"
    end

    Note over API: Token válido — Rotación
    API->>DB: UPDATE SET revoked=true, revoked_at=NOW() WHERE id = ?
    API->>API: Generar nuevo Access Token (15 min)
    API->>API: Generar nuevo Refresh Token (UUID)
    API->>DB: INSERT refresh_tokens(SHA256(nuevo_token), ip, ua, expires_at)
    API->>DB: INSERT INTO auditoria (TOKEN_ROTATED)

    API-->>U: 200 body: {accessToken, ..., passwordMustChange}
    Note over U,API: Set-Cookie: refresh_token=<nuevo_token><br/>HttpOnly, Secure, SameSite=Strict
```

---

## Flujo 3: Logout

```mermaid
sequenceDiagram
    actor U as Usuario
    participant API as Spring Boot API
    participant DB as PostgreSQL

    U->>API: POST /api/v1/auth/logout<br/>(requiere Access Token en Authorization header)<br/>(refresh token llega automáticamente como cookie)

    API->>API: hash_sha256(cookie refresh_token)
    API->>DB: UPDATE refresh_tokens SET revoked=true WHERE token_hash = ?
    API->>DB: INSERT INTO auditoria (LOGOUT)

    API-->>U: 200<br/>Set-Cookie: refresh_token=, Max-Age=0, HttpOnly, Secure
    Note over U: Browser elimina la cookie.<br/>Cliente elimina el access token de memoria.
    Note over U: ⚠️ El access token aún válido expira en ≤15 min.<br/>No existe invalidación inmediata de access tokens.
```

---

## Flujo 4: Logout en todos los dispositivos

```mermaid
sequenceDiagram
    actor U as Usuario
    participant API as Spring Boot API
    participant DB as PostgreSQL

    U->>API: POST /api/v1/auth/logout-all<br/>(requiere Access Token válido)

    API->>DB: UPDATE refresh_tokens SET revoked=true<br/>WHERE socio_id = ? AND revoked = false
    API->>DB: INSERT INTO auditoria (LOGOUT_ALL)

    API-->>U: 200<br/>Set-Cookie: refresh_token=, Max-Age=0
    Note over U: Todos los otros dispositivos quedarán<br/>sin sesión en el próximo refresh.
```

---

## Flujo 5: Verificación de Access Token en cada Request

```mermaid
sequenceDiagram
    actor U as Usuario
    participant API as Spring Boot API
    participant SEC as Spring Security Filter
    participant DB as PostgreSQL

    U->>API: GET /api/v1/cualquier-endpoint<br/>Authorization: Bearer {accessToken}

    API->>SEC: Intercepta request (JwtAuthFilter)
    SEC->>SEC: Verificar firma JWT (RS256, RSA-4096)

    alt Firma inválida
        SEC-->>U: 401 Unauthorized
    end

    alt Token expirado
        SEC-->>U: 401 Token expirado
    end

    SEC->>SEC: Extraer claims (socioId, rol, nombre)
    Note over SEC: ⚠️ Para operaciones críticas no confiar<br/>solo en claims JWT — pueden ser stale

    alt Operación crítica (cambio de rol, desbloqueo, etc.)
        SEC->>DB: SELECT rol_sistema_id FROM socios WHERE id = ?
        Note over SEC: Validar rol actual desde DB<br/>por si fue cambiado desde que se emitió el token
    end

    SEC->>SEC: Verificar permisos según rol + recurso (@PreAuthorize)
    SEC-->>API: Request autorizado con SecurityContext
    API-->>U: Respuesta del endpoint
```

---

## Flujo 6: Configuración 2FA (Setup / Confirm / Disable)

```mermaid
sequenceDiagram
    actor U as Usuario
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Note over U: Setup — usuario autenticado inicia configuración 2FA
    U->>API: POST /api/v1/auth/mfa/setup<br/>(requiere Access Token)
    API->>API: Generar secret TOTP (aleatorio, 20 bytes)
    API->>DB: UPDATE usuarios_auth SET totp_secret=AES256(secret),<br/>totp_enabled=false (pendiente confirmación)
    API-->>U: 200 {otpauthUri, qrCodeBase64}
    Note over U: Usuario escanea QR con app TOTP<br/>(Google Authenticator, Authy, etc.)

    Note over U: Confirm — usuario verifica con el primer código
    U->>API: POST /api/v1/auth/mfa/confirm {code}<br/>(requiere Access Token)
    API->>API: Descifrar totp_secret, TOTP.verify(code)
    alt Código inválido
        API-->>U: 400 "Código inválido"
    end
    API->>DB: UPDATE usuarios_auth SET totp_enabled=true
    API->>DB: INSERT INTO auditoria (MFA_ENABLED)
    API-->>U: 200 "2FA activado correctamente"

    Note over U: Disable — usuario desactiva 2FA
    U->>API: DELETE /api/v1/auth/mfa {code}<br/>(requiere Access Token + código TOTP válido)
    API->>API: Descifrar totp_secret, TOTP.verify(code)
    alt Código inválido
        API-->>U: 400 "Código inválido"
    end
    API->>DB: UPDATE usuarios_auth SET totp_secret=null, totp_enabled=false
    API->>DB: INSERT INTO auditoria (MFA_DISABLED)
    API-->>U: 200 "2FA desactivado"
```
