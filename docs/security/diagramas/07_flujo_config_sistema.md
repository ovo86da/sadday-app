# Diagrama 07 — Gestión de Configuración del Sistema

## Parámetros de Configuración y su Impacto de Seguridad

```mermaid
flowchart TD
    subgraph TABLE["Tabla configuracion_sistema"]
        direction LR
        P1["MAX_INTENTOS_LOGIN\n→ Umbral de bloqueo de cuenta"]
        P2["BLOQUEAR_INSCRIPCION_INHABILITADOS\n→ Permite/bloquea inscripción\nde socios inhabilitados"]
        P3["TIEMPO_BLOQUEO_CUENTA_HORAS\n→ Duración del bloqueo tras exceder intentos"]
        P4["... otros parámetros operativos"]
    end

    subgraph IMPACT["Impacto de un cambio no auditado"]
        I1["MAX_INTENTOS_LOGIN = 9999\n→ Brute force sin bloqueo"]
        I2["BLOQUEAR_INSCRIPCION_INHABILITADOS = false\n→ Socios expulsados se inscriben a salidas"]
        I3["TIEMPO_BLOQUEO_CUENTA_HORAS = 0\n→ Bloqueo efectivamente anulado"]
    end

    P1 -.->|"si se cambia a valor alto"| I1
    P2 -.->|"si se cambia a false"| I2
    P3 -.->|"si se cambia a 0"| I3

    style I1 fill:#ffcccc,stroke:#cc0000
    style I2 fill:#ffcccc,stroke:#cc0000
    style I3 fill:#ffcccc,stroke:#cc0000
```

---

## Flujo: Consulta de Configuración (Admin / Secretaria)

```mermaid
sequenceDiagram
    actor U as Admin / Secretaria
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Note over U: Listar todos los parámetros
    U->>API: GET /api/v1/admin/config\n(requiere rol Admin o Secretaria)
    API->>DB: SELECT * FROM configuracion_sistema
    DB-->>API: lista de parámetros
    API-->>U: 200 [{clave, valor, descripcion, updatedAt, updatedBy}]

    Note over U: Consultar un parámetro específico
    U->>API: GET /api/v1/admin/config/{clave}\n(requiere rol Admin o Secretaria)
    API->>DB: SELECT * FROM configuracion_sistema WHERE clave = ?
    alt Clave no encontrada
        API-->>U: 404 "Configuración no encontrada: {clave}"
    end
    API-->>U: 200 {clave, valor, descripcion, updatedAt, updatedBy}
```

---

## Flujo: Actualización de Configuración (solo Admin) — Auditado

```mermaid
sequenceDiagram
    actor ADM as Admin
    participant API as Spring Boot API
    participant SVC as ConfiguracionSistemaService
    participant AUDIT as AuditService
    participant DB as PostgreSQL

    ADM->>API: PATCH /api/v1/admin/config/{clave}\n{valor: "nuevo_valor"}\n(requiere rol Admin — @PreAuthorize("hasRole('ADMIN')"))

    API->>SVC: actualizar(clave, request, authentication)
    Note over SVC: @Transactional + @Auditable(accion="UPDATE_CONFIG")

    SVC->>DB: SELECT * FROM configuracion_sistema WHERE clave = ?
    alt Clave no encontrada
        SVC-->>API: BusinessException NOT_FOUND
        API-->>ADM: 404 "Configuración no encontrada: {clave}"
    end

    SVC->>SVC: valorAnterior = config.getValor()

    Note over SVC,AUDIT: Registro de auditoría con snapshot antes/después

    SVC->>AUDIT: registrar(\nactor=admin.username,\naccion="UPDATE_CONFIG",\nentidad="configuracion_sistema",\nentidadId=clave,\nantes={"clave":"...", "valor":"valorAnterior"},\ndespues={"clave":"...", "valor":"valorNuevo"},\nresultado="SUCCESS"\n)
    AUDIT->>DB: INSERT INTO auditoria\n(actor, accion, entidad, entidad_id,\nantes_json, despues_json, resultado, created_at)
    Note over DB: ⚠️ La tabla auditoria es append-only.\nEl usuario de BD no tiene DELETE sobre auditoria.

    SVC->>DB: UPDATE configuracion_sistema SET\nvalor = nuevoValor,\nupdated_by_id = socioId,\nupdated_at = NOW()\nWHERE clave = ?

    SVC-->>API: ConfiguracionSistemaResponse
    API-->>ADM: 200 {clave, valor, descripcion, updatedAt, updatedBy}

    Note over ADM,DB: El cambio queda registrado en auditoria con:<br/>• Quién cambió (username + socioId)<br/>• Qué cambió (clave)<br/>• Valor anterior y nuevo (JSON snapshot)<br/>• Timestamp exacto
```

---

## Control de Acceso por Rol

```mermaid
flowchart LR
    subgraph ROLES["Roles del sistema"]
        ADMIN["👤 Admin"]
        SECRETARIA["🗂️ Secretaria"]
        DIRECTIVO["📋 Directivo"]
        SOCIO["⛰️ Socio"]
    end

    subgraph ENDPOINTS["Endpoints /admin/config"]
        GET_ALL["GET /admin/config\n(listar todos)"]
        GET_ONE["GET /admin/config/{clave}\n(ver uno)"]
        PATCH["PATCH /admin/config/{clave}\n(modificar)"]
    end

    ADMIN -->|"✅ Permitido"| GET_ALL
    ADMIN -->|"✅ Permitido"| GET_ONE
    ADMIN -->|"✅ Permitido\n@PreAuthorize('hasRole(ADMIN)')"| PATCH

    SECRETARIA -->|"✅ Permitido\n(solo lectura)"| GET_ALL
    SECRETARIA -->|"✅ Permitido\n(solo lectura)"| GET_ONE
    SECRETARIA -->|"❌ 403 Forbidden"| PATCH

    DIRECTIVO -->|"❌ 403 Forbidden"| GET_ALL
    DIRECTIVO -->|"❌ 403 Forbidden"| GET_ONE
    DIRECTIVO -->|"❌ 403 Forbidden"| PATCH

    SOCIO -->|"❌ 403 Forbidden"| GET_ALL

    style PATCH fill:#fff3cc,stroke:#cc9900
```

---

## Registro en Auditoría — Formato del Snapshot

```mermaid
flowchart TD
    CHANGE["PATCH /admin/config/MAX_INTENTOS_LOGIN\n{valor: '5'}"]

    CHANGE --> SNAP["Snapshot generado por ConfiguracionSistemaService"]

    SNAP --> ANTES["antes_json:\n{\"clave\": \"MAX_INTENTOS_LOGIN\",\n\"valor\": \"3\"}"]
    SNAP --> DESPUES["despues_json:\n{\"clave\": \"MAX_INTENTOS_LOGIN\",\n\"valor\": \"5\"}"]

    ANTES & DESPUES --> ROW["INSERT auditoria {\n  actor: 'admin',\n  accion: 'UPDATE_CONFIG',\n  entidad: 'configuracion_sistema',\n  entidad_id: 'MAX_INTENTOS_LOGIN',\n  antes_json: '{...}',\n  despues_json: '{...}',\n  resultado: 'SUCCESS',\n  created_at: '2026-04-17T18:30:00'\n}"]
```
