# Diagrama 08 — Ciclo password_must_change

## Visión General del Ciclo

```mermaid
flowchart LR
    A["AdminBootstrap\n(primer arranque prod/staging)\nINSERT usuarios_auth\npassword_must_change = true"]

    B["Login exitoso\nLoginResponse {\n  ...\n  passwordMustChange: true\n}"]

    C["Frontend detecta flag\nRedirige a /change-password\n(bloquea resto de la app)"]

    D["POST /auth/change-password\n{passwordActual, nuevaPassword}"]

    E["AuthService.changePassword()\nSET password_must_change = false\nRevocar todos los refresh tokens"]

    F["Login normal\npasswordMustChange: false\nAcceso completo a la app"]

    A -->|"Primer login\ndel admin"| B
    B --> C
    C --> D
    D -->|"Contraseña válida"| E
    E -->|"Re-login con nueva contraseña"| F

    style A fill:#fff3cc,stroke:#cc9900
    style C fill:#ffcccc,stroke:#cc0000
    style F fill:#ccffcc,stroke:#006600
```

---

## Flujo Detallado: Primer Login del Admin (AdminBootstrap)

```mermaid
sequenceDiagram
    participant BOOT as AdminBootstrap\n(@Profile prod/staging)
    participant DB as PostgreSQL
    participant LOG as Logger

    Note over BOOT: Se ejecuta al arrancar la aplicación\n(implements CommandLineRunner)

    BOOT->>DB: SELECT COUNT(*) FROM usuarios_auth
    DB-->>BOOT: count

    alt Existen usuarios (count > 0)
        BOOT->>LOG: INFO "ya existen N usuario(s) — omitiendo"
        Note over BOOT: No hace nada más
    end

    Note over BOOT: Primera vez — tabla vacía

    BOOT->>DB: SELECT id FROM roles_sistema WHERE nombre = 'Admin'
    BOOT->>DB: SELECT id FROM tipo_socio_club WHERE nombre = 'Socio Activo'
    BOOT->>DB: SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'

    BOOT->>DB: INSERT INTO socios\n(id=UUID, nombre='Admin', apellido='Sistema',\ncedula='0000000000', correo='admin@sadday.local',\nrol=Admin, estado=Habilitado)

    BOOT->>DB: INSERT INTO usuarios_auth\n(socio_id, username=adminUsername,\npassword_hash=Argon2id(adminInitialPassword),\npassword_must_change = TRUE)

    BOOT->>LOG: WARN "╔══ ADMIN INICIAL CREADO — CAMBIA LA CONTRASEÑA YA ══╗"
    BOOT->>LOG: WARN "║  Usuario: {adminUsername}"
    BOOT->>LOG: WARN "╚════════════════════════════════════════════════════╝"

    Note over BOOT: adminInitialPassword se lee de la variable\nde entorno SADDAY_ADMIN_INITIAL_PASSWORD\n(nunca en el código fuente)
```

---

## Flujo Detallado: Login con password_must_change = true

```mermaid
sequenceDiagram
    actor ADM as Admin (primer login)
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant FE as Frontend React

    ADM->>API: POST /api/v1/auth/login\n{username: "admin", password: "Admin123!"}

    API->>DB: SELECT usuarios_auth WHERE username = 'admin'
    API->>API: Argon2id.verify(password, hash) — OK
    API->>DB: UPDATE SET failed_attempts=0, last_login=NOW()
    API->>API: Generar Access Token (JWT, 15 min)
    API->>API: Generar Refresh Token
    API->>DB: INSERT refresh_tokens(...)
    API->>DB: INSERT INTO auditoria (LOGIN_SUCCESS)

    API-->>FE: 200 {\n  accessToken: "eyJ...",\n  tokenType: "Bearer",\n  expiresIn: 900,\n  socioId: "uuid...",\n  username: "admin",\n  nombre: "Admin Sistema",\n  rol: "Admin",\n  nivelTecnico: null,\n  passwordMustChange: true   ← flag\n}
    Note over API,FE: Set-Cookie: refresh_token=...; HttpOnly; Secure

    FE->>FE: Guardar accessToken en memoria
    FE->>FE: Verificar passwordMustChange === true

    alt passwordMustChange === true
        FE->>FE: Redirigir a /change-password\n⚠️ Bloquear navegación a otras rutas\nhasta que se cambie la contraseña
        Note over FE: El access token es válido para hacer\nla petición de cambio de contraseña.\nPero la UI no debe permitir otras acciones.
    end
```

---

## Flujo Detallado: Cambio de Contraseña y Limpieza del Flag

```mermaid
sequenceDiagram
    actor ADM as Admin
    participant FE as Frontend React
    participant API as Spring Boot API
    participant DB as PostgreSQL

    ADM->>FE: Completa formulario:\n{passwordActual: "Admin123!",\nnuevaPassword: "M1NuevaClave#2026",\nconfirmarPassword: "M1NuevaClave#2026"}

    FE->>API: POST /api/v1/auth/change-password\n{passwordActual, nuevaPassword}\nAuthorization: Bearer {accessToken}

    API->>DB: SELECT usuarios_auth WHERE socio_id = ?
    API->>API: Argon2id.verify(passwordActual, hash)

    alt Contraseña actual incorrecta
        API-->>FE: 400 "Contraseña actual incorrecta"
        FE->>FE: Mostrar error en formulario
    end

    API->>API: Validar política: min 10 chars,\nmayús, minús, número, especial

    alt Política no cumplida
        API-->>FE: 400 "La contraseña no cumple los requisitos"
    end

    API->>API: nuevo_hash = Argon2id.hash(nuevaPassword)

    API->>DB: UPDATE usuarios_auth SET\npassword_hash = nuevo_hash,\npassword_must_change = FALSE   ← limpia el flag
    Note over DB: El flag queda en false permanentemente\nhasta que se active manualmente de nuevo

    API->>DB: UPDATE refresh_tokens SET revoked=true\nWHERE socio_id = ?\n(invalida TODAS las sesiones activas\nen otros dispositivos)

    API->>DB: INSERT INTO auditoria (PASSWORD_CHANGED)

    API-->>FE: 200 "Contraseña actualizada correctamente."

    FE->>FE: Eliminar accessToken de memoria
    FE->>FE: Redirigir a /login
    Note over FE: El usuario debe hacer login de nuevo\ncon la nueva contraseña.\nEn el nuevo login passwordMustChange = false\n→ acceso completo a la app.
```

---

## Otros Eventos que Activan password_must_change

```mermaid
flowchart TD
    subgraph ACTIVA["Eventos que ponen password_must_change = true"]
        E1["AdminBootstrap\n(primer arranque)"]
        E2["Futuro: Admin fuerza\ncambio al siguiente login\n(no implementado aún)"]
    end

    subgraph LIMPIA["Eventos que ponen password_must_change = false"]
        C1["POST /auth/change-password\n(cambio con contraseña actual)"]
        C2["POST /auth/reset-password\n(reset por token de email)"]
    end

    subgraph PROPAGA["Donde se propaga el flag al cliente"]
        P1["POST /auth/login\n→ LoginResponse.passwordMustChange"]
        P2["POST /auth/refresh\n→ LoginResponse.passwordMustChange"]
    end

    E1 & E2 -->|"SET password_must_change = true"| DB[("usuarios_auth")]
    C1 & C2 -->|"SET password_must_change = false"| DB
    DB -->|"isPasswordMustChange()"| P1 & P2

    Note1["⚠️ El refresh también devuelve el flag\npara que el cliente lo re-verifique\nen cada renovación de token"]
    P2 --- Note1

    style E2 fill:#f0f0f0,stroke:#999999,stroke-dasharray: 5 5
```
