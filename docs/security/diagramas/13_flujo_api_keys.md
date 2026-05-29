# Diagrama 13 — Flujo de API Keys (MCP Server)

## Propósito y Contexto

Las API Keys permiten que el MCP server acceda a la API de Sadday en nombre de un socio, sin exponer credenciales de usuario. Son de solo lectura — ninguna operación de escritura está permitida con este mecanismo.

```mermaid
flowchart LR
    MCP["🤖 MCP Server\n(Claude Desktop)"]
    API["⚙️ Sadday API\nSpring Boot"]
    DB[("🗄️ PostgreSQL\ntabla api_keys")]

    MCP -->|"GET /api/v1/...\nX-Api-Key: sk-sadday-<base64>"| API
    API -->|"SHA-256(rawKey)\nSELECT WHERE key_hash=?"| DB
    DB -->|"ApiKey + socio_id"| API
    API -->|"200 respuesta filtrada por rol"| MCP
```

---

## Flujo Completo: Request Autenticado con API Key

```mermaid
sequenceDiagram
    actor MCP as MCP Server
    participant FILTER as ApiKeyAuthFilter
    participant SVC as ApiKeyService
    participant DB as PostgreSQL
    participant CTRL as Controller

    MCP->>FILTER: GET /api/v1/socios\nX-Api-Key: sk-sadday-<base64url-32bytes>

    Note over FILTER: ① ¿Header X-Api-Key presente?

    alt No hay header X-Api-Key
        FILTER->>FILTER: Pasar al siguiente filtro\n(JwtAuthFilter tomará el relevo)
    end

    Note over FILTER: ② ¿Método de escritura?

    alt POST / PUT / PATCH / DELETE
        FILTER-->>MCP: 403 "Las API Keys no permiten operaciones de escritura"
    end

    Note over FILTER: ③ ¿HTTPS en producción?

    alt Perfil prod + X-Forwarded-Proto != https
        FILTER-->>MCP: 400 "API Key solo permitida sobre HTTPS"
    end

    FILTER->>SVC: findActiveByRawKey(rawKey)
    SVC->>SVC: keyHash = SHA-256(rawKey)
    SVC->>DB: SELECT * FROM api_keys\nWHERE key_hash = ?\nAND revoked = false\nAND expires_at > NOW()

    alt Key no encontrada / revocada / expirada
        FILTER-->>MCP: 401 "API Key inválida o revocada"
    end

    DB-->>SVC: ApiKey(socioId, nombre, lastUsedAt)
    SVC-->>FILTER: Optional<ApiKey>

    FILTER->>DB: SELECT socios WHERE id = socioId
    DB-->>FILTER: Socio(id, rolSistema)

    FILTER->>FILTER: Inyectar SecurityContext:\n• ROLE_{rolSistema}  (ej: ROLE_SOCIO)\n• SCOPE_readonly\n• SaddayAuthDetails(socioId)

    FILTER->>SVC: touchLastUsedAt(apiKey.id)
    SVC->>DB: UPDATE api_keys SET last_used_at = NOW()\nWHERE id = ?
    Note over SVC,DB: UPDATE síncrono — índice sobre id\nLatencia < 1ms

    FILTER->>CTRL: Continuar filter chain
    Note over CTRL: @PreAuthorize se evalúa normalmente.\nSCOPE_readonly disponible para restricciones adicionales.
    CTRL-->>MCP: 200 respuesta filtrada por rol
```

---

## Gestión de API Keys (CRUD desde la app web)

```mermaid
sequenceDiagram
    actor S as Socio (autenticado)
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Note over S: Crear una nueva API Key
    S->>API: POST /api/v1/profile/api-keys\n{nombre: "Claude Desktop"}\n(requiere JWT — cualquier rol)

    API->>DB: SELECT COUNT(*) FROM api_keys\nWHERE socio_id = ? AND revoked = false AND expires_at > NOW()
    alt >= 5 keys activas
        API-->>S: 409 "Has alcanzado el límite de 5 API Keys activas"
    end

    API->>API: rawKey = "sk-sadday-" + Base64URL(32 random bytes)
    API->>API: keyHash = SHA-256(rawKey)
    API->>DB: INSERT api_keys\n(socio_id, key_hash, nombre,\nexpires_at = NOW() + 1 año,\nrevoked = false)

    API-->>S: 201 {\n  id, nombre, keyPrefix: "sk-sadday-",\n  rawKey: "sk-sadday-...",\n  expiresAt\n}
    Note over S: ⚠️ rawKey se muestra UNA SOLA VEZ\nEl socio debe guardarla ahora — no hay forma\nde recuperarla después (solo se almacena el hash)

    Note over S: Listar keys activas
    S->>API: GET /api/v1/profile/api-keys
    API->>DB: SELECT api_keys WHERE socio_id = ?\nAND revoked = false AND expires_at > NOW()
    API-->>S: [{id, nombre, keyPrefix, lastUsedAt, expiresAt}]\n(sin exponer rawKey ni key_hash)

    Note over S: Revocar una key
    S->>API: DELETE /api/v1/profile/api-keys/{keyId}
    API->>DB: SELECT api_keys WHERE id = ? AND socio_id = ?
    alt Key no pertenece al socio
        API-->>S: 403 Forbidden
    end
    API->>DB: UPDATE api_keys SET revoked = true WHERE id = ?
    API-->>S: 204 No Content
    Note over S: A partir de este momento cualquier request\ncon esa key recibe 401 inmediatamente
```

---

## Formato y Ciclo de Vida de la Key

```mermaid
flowchart TD
    subgraph FORMAT["Formato de la API Key"]
        PREFIX["sk-sadday-"]
        BODY["Base64URL(32 bytes aleatorios)\n≈ 43 caracteres"]
        FULL["sk-sadday-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"]
        PREFIX --> BODY --> FULL
    end

    subgraph STORAGE["Lo que se almacena en BD"]
        RAW["rawKey — NUNCA persiste"]
        HASH["key_hash = SHA-256(rawKey)\n32 bytes / 64 hex chars"]
        RAW -.->|"descartado tras responder"| HASH
    end

    subgraph LIFECYCLE["Ciclo de vida"]
        CREATED["Creada\n(activa, expira en 1 año)"]
        USED["En uso\nlastUsedAt actualizado\nen cada request"]
        EXPIRED["Expirada\n(expires_at < NOW())\nNo revocada pero inactiva"]
        REVOKED["Revocada\n(revoked = true)\nIgnorada por el filtro"]

        CREATED -->|"Uso normal"| USED
        USED -->|"1 año desde creación"| EXPIRED
        USED -->|"DELETE /profile/api-keys/{id}"| REVOKED
        CREATED -->|"DELETE /profile/api-keys/{id}"| REVOKED
    end
```

---

## Control de Acceso — Qué puede y qué no puede hacer una API Key

```mermaid
flowchart TD
    KEY["Request con X-Api-Key válida\nAuthorities: ROLE_SOCIO + SCOPE_readonly"]

    KEY --> CAN["✅ Puede"]
    KEY --> CANNOT["❌ No puede"]

    subgraph CAN_LIST["Operaciones permitidas (GET / HEAD)"]
        C1["GET /api/v1/socios/me\n(perfil propio)"]
        C2["GET /api/v1/salidas\n(lista de salidas)"]
        C3["GET /api/v1/mountains/**\n(montañas y rutas)"]
        C4["GET /api/v1/estadisticas\n(estadísticas del club)"]
        C5["Cualquier endpoint GET\naccesible para el rol del socio"]
    end

    subgraph CANNOT_LIST["Operaciones bloqueadas"]
        D1["POST / PUT / PATCH / DELETE\n→ 403 en el filtro (antes de llegar al controller)"]
        D2["Endpoints de /admin/**\n→ 403 (ROLE_SOCIO no tiene acceso)"]
        D3["Endpoints que requieren otro rol\n→ 403 por @PreAuthorize"]
    end

    CAN --> CAN_LIST
    CANNOT --> CANNOT_LIST

    style CAN fill:#ccffcc,stroke:#006600
    style CANNOT fill:#ffcccc,stroke:#cc0000
```
