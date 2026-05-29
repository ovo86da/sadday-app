# Diagrama 12 — Cadena de Filtros Spring Security

## Orden de Ejecución

```mermaid
flowchart TD
    REQ["📥 HTTP Request"]

    subgraph CHAIN["Spring Security Filter Chain — orden de ejecución"]
        direction TB

        F1["① RateLimitFilter\n(@Profile !test)\nBucket4j + Caffeine en memoria"]
        F2["② ApiKeyAuthFilter\nHeader: X-Api-Key"]
        F3["③ JwtAuthFilter\nHeader: Authorization: Bearer"]
        F4["④ UsernamePasswordAuthenticationFilter\n(built-in — no se usa directamente;\nes el ancla de orden de los filtros custom)"]
        F5["⑤ Spring Security Authorization\n(.authorizeHttpRequests)\nEvalúa reglas de acceso por URL"]
        F6["⑥ @PreAuthorize\n(en Controller / Service)\nSegunda capa de autorización por método"]

        F1 --> F2 --> F3 --> F4 --> F5 --> F6
    end

    REQ --> F1
    F6 --> CTRL["🎯 Controller / Service"]
```

---

## ① RateLimitFilter — Límites por Endpoint y por IP

Solo actúa sobre los endpoints listados. El resto de requests pasa sin consumir ningún token.

```mermaid
flowchart TD
    IN["Request entrante"] --> MATCH{"¿El endpoint\nestá en la lista?"}

    MATCH -->|"No"| PASS["✅ Pasar al siguiente filtro\n(sin consumir tokens)"]

    MATCH -->|"POST /auth/login"| B1["Bucket: 10 req / 1 min por IP"]
    MATCH -->|"POST /auth/forgot-password"| B2["Bucket: 5 req / 5 min por IP"]
    MATCH -->|"POST /auth/reset-password"| B3["Bucket: 5 req / 5 min por IP"]
    MATCH -->|"POST /auth/refresh"| B4["Bucket: 60 req / 1 min por IP"]
    MATCH -->|"POST /registro/complete"| B5["Bucket: 10 req / 10 min por IP"]
    MATCH -->|"GET /registro/token-info"| B6["Bucket: 10 req / 10 min por IP"]

    B1 & B2 & B3 & B4 & B5 & B6 --> CONSUME{"tryConsume(1)\n¿Token disponible?"}

    CONSUME -->|"Sí"| PASS
    CONSUME -->|"No"| R429["❌ 429 Too Many Requests\n{\"message\": \"Demasiados intentos...\"}"]

    style R429 fill:#ffcccc,stroke:#cc0000
    style PASS fill:#ccffcc,stroke:#006600
```

**Implementación:** Caffeine cache (max 10.000 IPs por tipo de endpoint, expiración tras 30 min de inactividad). En producción multi-instancia debe reemplazarse con Redis + Bucket4j distribuido.

---

## ② ApiKeyAuthFilter — Autenticación por X-Api-Key

```mermaid
flowchart TD
    IN2["Request con o sin X-Api-Key"] --> CHECK_HEADER{"Header\nX-Api-Key\npresente?"}

    CHECK_HEADER -->|"No"| SKIP["✅ Pasar al siguiente filtro\n(JwtAuthFilter tomará el relevo)"]

    CHECK_HEADER -->|"Sí"| CHECK_TLS{"¿Perfil prod?\n¿X-Forwarded-Proto\n!= https?"}
    CHECK_TLS -->|"Sí (no HTTPS en prod)"| R400["❌ 400 Bad Request\n'API Key solo permitida sobre HTTPS'"]

    CHECK_TLS -->|"No (OK)"| CHECK_METHOD{"¿Método HTTP\nes POST / PUT /\nPATCH / DELETE?"}
    CHECK_METHOD -->|"Sí"| R403["❌ 403 Forbidden\n'Las API Keys no permiten\noperaciones de escritura'"]

    CHECK_METHOD -->|"No (GET / HEAD / OPTIONS)"| HASH["SHA-256(rawKey)\n→ buscar en tabla api_keys"]

    HASH --> FOUND{"¿Encontrado?\n¿No revocada?\n¿No expirada?"}
    FOUND -->|"No"| R401["❌ 401 Unauthorized\n'API Key inválida o revocada'"]

    FOUND -->|"Sí"| LOAD["Cargar Socio del api_key.socioId\nObtener rol_sistema del socio"]
    LOAD --> INJECT["Inyectar en SecurityContext:\n• ROLE_{rol_sistema}\n• SCOPE_readonly\n• SaddayAuthDetails(socioId, rol)"]
    INJECT --> TOUCH["touchLastUsedAt(apiKey.id)\n(UPDATE síncrono — índice en id)"]
    TOUCH --> CONTINUE["✅ Continuar filter chain\n(JwtAuthFilter omitirá — context ya está setteado)"]

    style R400 fill:#ffcccc,stroke:#cc0000
    style R401 fill:#ffcccc,stroke:#cc0000
    style R403 fill:#ffcccc,stroke:#cc0000
    style CONTINUE fill:#ccffcc,stroke:#006600
```

---

## ③ JwtAuthFilter — Autenticación por Bearer Token

```mermaid
flowchart TD
    IN3["Request"] --> CHECK_CTX{"¿SecurityContext\nya tiene autenticación?\n(ApiKeyAuthFilter ya actuó)"}

    CHECK_CTX -->|"Sí"| SKIP3["✅ Pasar al siguiente filtro\n(no procesar JWT — ya hay auth)"]

    CHECK_CTX -->|"No"| CHECK_HDR{"Header Authorization\nBearer <token>\npresente?"}
    CHECK_HDR -->|"No"| SKIP3

    CHECK_HDR -->|"Sí"| VERIFY["Verificar firma JWT\nRS256, RSA-4096\nClave pública desde /opt/sadday/keys/public.pem"]

    VERIFY --> VALID{"¿Firma válida\ny no expirado?"}
    VALID -->|"No"| R401B["❌ 401 (manejado por\nexceptionHandling)"]

    VALID -->|"Sí"| CLAIMS["Extraer claims:\nsub=socioId, rol, nombre"]
    CLAIMS --> SET["Inyectar en SecurityContext:\n• ROLE_{rol}\n• SaddayAuthDetails(socioId, rol, nombre)"]
    SET --> CONT["✅ Continuar filter chain"]

    style R401B fill:#ffcccc,stroke:#cc0000
    style CONT fill:#ccffcc,stroke:#006600
```

---

## Reglas de Autorización por URL (Spring Security)

```mermaid
flowchart LR
    subgraph PUBLIC["🟢 Público — sin autenticación"]
        P1["POST /api/v1/auth/login"]
        P2["POST /api/v1/auth/refresh"]
        P3["POST /api/v1/auth/forgot-password"]
        P4["POST /api/v1/auth/reset-password"]
        P5["POST /api/v1/auth/mfa/login"]
        P6["POST /api/v1/auth/country-challenge/verify"]
        P7["/api/v1/registro/**"]
        P8["GET /actuator/health"]
        P9["GET /actuator/info\n(solo non-prod)"]
    end

    subgraph AUTH_REQUIRED["🟡 Autenticado — cualquier rol"]
        A1["POST /api/v1/auth/logout"]
        A2["POST /api/v1/auth/logout-all"]
        A3["/api/v1/auth/mfa/setup|confirm|DELETE"]
        A4["GET /swagger-ui/**, /v3/api-docs/**\n(non-prod) | ADMIN/SECRETARIA (prod)"]
        A5["Cualquier otro endpoint autenticado"]
    end

    subgraph ADMIN_ONLY["🔴 Solo ADMIN o SECRETARIA"]
        AD1["/api/v1/admin/**"]
        NOTE["⚠️ Algunos sub-endpoints de /admin\nrequieren solo ADMIN vía @PreAuthorize"]
    end

    subgraph DENIED["⛔ Siempre denegado"]
        D1["/actuator/**\n(excepto /health e /info)"]
    end
```

---

## Resumen del Contexto de Seguridad según método de autenticación

| Autenticación | Authorities inyectadas | Puede escribir (POST/PUT/PATCH/DELETE) |
|---|---|---|
| JWT válido | `ROLE_{rol}` | ✓ (según @PreAuthorize) |
| API Key válida | `ROLE_{rol}` + `SCOPE_readonly` | ✗ (403 en el filtro) |
| Sin autenticación en endpoint público | Ninguna | Solo endpoints `permitAll()` |
| Sin autenticación en endpoint protegido | — | ✗ 401 |
