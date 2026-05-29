# Diagrama 14 — Flujo de Refresh Token Mobile y Mutex de Concurrencia

## Arquitectura de Interceptores

La app Flutter usa dos interceptores Dio en serie. Cada uno tiene un rol distinto:

```mermaid
flowchart LR
    APP["App Flutter\nRiverpod"]

    subgraph DIO["Cliente Dio"]
        AI["① AuthInterceptor\nonRequest — proactivo\nAgrega Bearer token\nRefresh si expira en < 30s"]
        EI["② ErrorInterceptor\nonError — reactivo\nReintentar request si 401"]
    end

    LOCK["🔒 refresh_lock.dart\nMutex global\n(package:synchronized)"]
    API["⚙️ Spring Boot API"]

    APP -->|"dio.get('/socios')"| AI
    AI -->|"+ Authorization: Bearer <token>"| API
    API -->|"200 OK / 401"| EI
    EI -->|"Retry con nuevo token si 401"| API

    AI <-->|"withRefreshLock()"| LOCK
    EI <-->|"withRefreshLock()"| LOCK
```

---

## Flujo Proactivo — AuthInterceptor (onRequest)

Se ejecuta **antes** de cada request. Si el access token expira en los próximos 30 segundos, hace el refresh antes de enviar la petición.

```mermaid
sequenceDiagram
    participant APP as App (Riverpod)
    participant AI as AuthInterceptor
    participant LOCK as refresh_lock (mutex)
    participant NOTIFIER as AuthNotifier
    participant API as Spring Boot API
    participant KS as SecureStorage\n(Keychain/Keystore)

    APP->>AI: dio.get('/api/v1/socios')

    AI->>AI: auth = ref.read(authNotifierProvider)
    AI->>AI: JwtUtils.isExpiredWithBuffer(accessToken, bufferSeconds: 30)

    alt Token válido — no expira en los próximos 30s
        AI->>AI: options.headers['Authorization'] = 'Bearer {accessToken}'
        AI->>API: GET /api/v1/socios\nAuthorization: Bearer {accessToken}
    end

    alt Token expira en < 30s o ya expirado
        AI->>LOCK: withRefreshLock(() => notifier.refresh())
        Note over LOCK: El mutex bloquea si otro refresh\nya está en curso — espera su resultado
        LOCK->>NOTIFIER: refresh()
        NOTIFIER->>KS: leer refreshToken
        NOTIFIER->>API: POST /api/v1/auth/refresh\nX-Sadday-Client: mobile\nbody: {refreshToken}
        API-->>NOTIFIER: 200 {accessToken, refreshToken}
        NOTIFIER->>KS: guardar nuevo refreshToken\n(Keychain primero, luego memoria)
        NOTIFIER->>NOTIFIER: actualizar estado Riverpod\ncon nuevo accessToken (en memoria)
        LOCK-->>AI: refresh completado
        AI->>AI: options.headers['Authorization'] = 'Bearer {nuevoAccessToken}'
        AI->>API: GET /api/v1/socios\nAuthorization: Bearer {nuevoAccessToken}
    end

    API-->>APP: 200 {socios: [...]}
```

---

## Flujo Reactivo — ErrorInterceptor (onError)

Se ejecuta **después** de recibir una respuesta de error. Si recibe un 401, intenta refrescar el token y reintentar el request original.

```mermaid
sequenceDiagram
    participant APP as App (Riverpod)
    participant EI as ErrorInterceptor
    participant LOCK as refresh_lock (mutex)
    participant NOTIFIER as AuthNotifier
    participant API as Spring Boot API
    participant KS as SecureStorage

    APP->>API: GET /api/v1/socios\nAuthorization: Bearer {tokenExpirado}
    API-->>EI: 401 Unauthorized

    EI->>LOCK: withRefreshLock(() => notifier.refresh())
    Note over LOCK: El mutex garantiza que solo un\nrefresh se ejecuta a la vez aunque\nvariosrequests fallen con 401 simultáneamente

    LOCK->>NOTIFIER: refresh()
    NOTIFIER->>KS: leer refreshToken
    NOTIFIER->>API: POST /api/v1/auth/refresh\nX-Sadday-Client: mobile\nbody: {refreshToken}

    alt Refresh exitoso
        API-->>NOTIFIER: 200 {accessToken, refreshToken}
        NOTIFIER->>KS: guardar nuevo refreshToken
        NOTIFIER->>NOTIFIER: nuevo accessToken en memoria
        LOCK-->>EI: true (refresh OK)

        EI->>EI: Recuperar requestOptions original
        EI->>EI: options.headers['Authorization'] = 'Bearer {nuevoAccessToken}'
        EI->>API: Retry: GET /api/v1/socios\nAuthorization: Bearer {nuevoAccessToken}
        API-->>APP: 200 {socios: [...]}
    end

    alt Refresh fallido (refreshToken expirado, revocado o red caída)
        API-->>NOTIFIER: 401 / error de red
        LOCK-->>EI: false (refresh fallido)
        EI->>NOTIFIER: logout()
        NOTIFIER->>KS: eliminar refreshToken de SecureStorage
        NOTIFIER->>NOTIFIER: estado → AuthUnauthenticated
        EI-->>APP: UnauthorizedException → go_router redirige a /login
    end
```

---

## El Mutex — Por Qué es Necesario

Sin mutex, 3 requests paralelos que reciben 401 simultáneamente dispararían 3 refresh en paralelo. El backend rota el refresh token en cada refresh: el segundo refresh usa un token ya inválido (el primero lo rotó) y el backend lo detecta como robo de token, revocando **todas las sesiones** del usuario.

```mermaid
sequenceDiagram
    participant R1 as Request 1
    participant R2 as Request 2
    participant R3 as Request 3
    participant LOCK as refresh_lock
    participant API as Spring Boot API

    Note over R1,R3: ❌ SIN MUTEX — lo que pasaría

    R1->>API: POST /auth/refresh {token_A}
    R2->>API: POST /auth/refresh {token_A}
    R3->>API: POST /auth/refresh {token_A}

    API-->>R1: 200 {accessToken, refreshToken: token_B}\n(token_A queda revocado)
    API-->>R2: 401 REFRESH_TOKEN_REUSED ← token_A ya revocado\n⚠️ Backend detecta posible robo → REVOCA TODO
    API-->>R3: 401 REFRESH_TOKEN_REUSED

    Note over R1,R3: ✅ CON MUTEX — comportamiento real

    R1->>LOCK: withRefreshLock() → adquiere lock
    R2->>LOCK: withRefreshLock() → espera (bloqueado)
    R3->>LOCK: withRefreshLock() → espera (bloqueado)

    LOCK->>API: POST /auth/refresh {token_A}
    API-->>LOCK: 200 {accessToken_new, refreshToken: token_B}
    LOCK-->>R1: lock liberado — token_B guardado en SecureStorage

    R2->>LOCK: withRefreshLock() → adquiere lock\nLee nuevo estado Riverpod: accessToken_new ya válido
    LOCK-->>R2: lock liberado sin llamar a la API\n(token ya fresco)

    R3->>LOCK: withRefreshLock() → adquiere lock\nIdem — token_new todavía válido
    LOCK-->>R3: lock liberado

    R1->>API: Retry con accessToken_new → 200 ✅
    R2->>API: Retry con accessToken_new → 200 ✅
    R3->>API: Retry con accessToken_new → 200 ✅
```

---

## Orden de Guardado del Token — Crash Safety

El orden importa para evitar pérdida de sesión si la app se cierra entre operaciones:

```mermaid
flowchart TD
    GOT["API devuelve {accessToken, refreshToken}"]

    GOT --> S1["① Guardar refreshToken\nen SecureStorage\n(Keychain iOS / Keystore Android)\noperación persistente"]

    S1 --> S2["② Actualizar estado Riverpod\ncon accessToken\n(solo en memoria)"]

    S2 --> DONE["✅ Refresh completado"]

    subgraph WHY["¿Por qué este orden?"]
        W1["Si la app crashea entre ① y ②:\nel refreshToken nuevo está guardado\nen Keychain → al reabrir la app\npuede hacer refresh desde el estado persitido"]
        W2["Si fuera al revés (Riverpod primero):\nel accessToken en memoria se pierde en el crash\ny el refreshToken viejo ya fue invalidado por el backend\n→ el usuario pierde la sesión"]
    end
```

---

## Referencia de Archivos

| Archivo | Rol |
|---|---|
| `mobile/lib/core/api/refresh_lock.dart` | Mutex global (`Lock` de `package:synchronized`) |
| `mobile/lib/core/api/interceptors/auth_interceptor.dart` | Interceptor proactivo — refresh antes de que expire |
| `mobile/lib/core/api/interceptors/error_interceptor.dart` | Interceptor reactivo — retry tras 401 |
| `mobile/lib/core/auth/auth_provider.dart` | `AuthNotifier.refresh()` — llama al backend y actualiza estado |
| `mobile/lib/core/auth/jwt_utils.dart` | `isExpiredWithBuffer(token, bufferSeconds: 30)` |
