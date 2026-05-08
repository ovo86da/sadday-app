# Diagrama 09 — Gestión de Nivel Técnico y Control de Acceso a Rutas

## Estructura de Clasificación Técnica

```mermaid
flowchart LR
    subgraph ESCALA["Escala ClasificacionSocio (campo nivel: entero)"]
        direction TB
        T1["T1 — Principiante\nnivel = 1"]
        T2["T2 — Básico\nnivel = 2"]
        T3["T3 — Iniciado\nnivel = 3"]
        T4["T4 — Intermedio\nnivel = 4"]
        T5["T5"]
        T6["T6"]
        T7["T7"]
        T8["T8"]
        T9["T9"]
        T10["T10 — Experto\nnivel = 10"]
        T1 --> T2 --> T3 --> T4 --> T5 --> T6 --> T7 --> T8 --> T9 --> T10
    end

    subgraph RUTA["Salida / Ruta"]
        NMIN["nivelMinimoRequerido\n(ClasificacionSocio FK)"]
    end

    subgraph SOCIO["Socio"]
        NSOC["nivelTecnico\n(ClasificacionSocio FK)\nnull = sin nivel asignado"]
    end

    NSOC -->|"nivel_socio >= nivel_minimo\n→ INSCRITO directo"| OK["✅ Inscripción inmediata"]
    NSOC -->|"nivel_socio < nivel_minimo\nO nivelTecnico = null"| PEND["⚠️ PENDIENTE_APROBACION\n(requiere Directivo + Jefe)"]
    NMIN --> OK
    NMIN --> PEND

    style OK fill:#ccffcc,stroke:#006600
    style PEND fill:#fff3cc,stroke:#cc9900
```

---

## Flujo: Asignación / Actualización de Nivel Técnico

```mermaid
sequenceDiagram
    actor STAFF as Admin / Secretaria / Directivo
    participant API as Spring Boot API
    participant SVC as SocioService
    participant DB as PostgreSQL

    STAFF->>API: PATCH /api/v1/socios/{id}/nivel-tecnico\n{nivelTecnicoId: "uuid-del-nivel"}\n(requiere rol Admin, Secretaria o Directivo)

    API->>SVC: actualizarNivelTecnico(id, request)
    Note over SVC: @Auditable(accion="UPDATE_NIVEL_TECNICO",\nentidad="socios", idArgName="id")

    SVC->>DB: SELECT socios WHERE id = ?
    alt Socio no encontrado
        SVC-->>API: 404 "Socio no encontrado"
    end

    SVC->>DB: SELECT clasificacion_socio WHERE id = nivelTecnicoId
    alt Nivel no encontrado
        SVC-->>API: 404 "Nivel técnico no encontrado"
    end

    SVC->>DB: UPDATE socios SET nivel_tecnico_id = ? WHERE id = ?
    SVC->>DB: INSERT INTO auditoria\n(UPDATE_NIVEL_TECNICO, entidad=socios,\nentidad_id=socioId)

    API-->>STAFF: 200 SocioResponse con nuevo nivel
    Note over STAFF,DB: Cambio efectivo inmediatamente:\nla próxima inscripción del socio<br/>usará el nivel actualizado.
```

---

## Flujo: Consulta de Nivel por el Propio Socio

```mermaid
sequenceDiagram
    actor S as Socio
    participant API as Spring Boot API
    participant DB as PostgreSQL

    S->>API: GET /api/v1/socios/me\n(requiere Access Token)

    API->>DB: SELECT socios JOIN clasificacion_socio\nWHERE socios.id = socioId_del_jwt
    DB-->>API: datos del socio incluyendo nivelTecnico

    API-->>S: 200 {\n  id, nombre, apellido, ...\n  nivelTecnico: {\n    id: "uuid",\n    nivel: 4,\n    nombre: "T4 — Intermedio",\n    descripcion: "..."\n  }\n}
    Note over S: El socio puede ver su nivel.<br/>No puede modificarlo.
```

---

## Flujo: Validación de Nivel en Inscripción a Salida

```mermaid
flowchart TD
    START(["POST /api/v1/salidas/{id}/inscripciones\n{socioId}"])

    START --> Q1{"¿La salida tiene\nnivelMinimoRequerido?"}
    Q1 -->|"No — salida libre"| INSCRITO["✅ EstadoInscripcion = INSCRITO"]
    Q1 -->|"Sí"| Q2{"¿El socio tiene\nnivelTecnico asignado?"}

    Q2 -->|"No (nivelTecnico = null)"| PEND
    Q2 -->|"Sí"| Q3{"nivelSocio.nivel\n>=\nnivelMinimo.nivel ?"}

    Q3 -->|"Sí — nivel suficiente"| INSCRITO
    Q3 -->|"No — nivel insuficiente"| PEND["⚠️ EstadoInscripcion = PENDIENTE_APROBACION\nSe notifica a Directivo y Jefe de Salida"]

    INSCRITO --> DB1["INSERT salida_participantes\n(estadoInscripcion=INSCRITO)\nINSERT auditoria (INSCRIPCION_OK)"]
    PEND --> DB2["INSERT salida_participantes\n(estadoInscripcion=PENDIENTE_APROBACION)\nINSERT auditoria (INSCRIPCION_PENDIENTE)"]

    style INSCRITO fill:#ccffcc,stroke:#006600
    style PEND fill:#fff3cc,stroke:#cc9900
```

---

## Control de Acceso por Rol — Nivel Técnico

```mermaid
flowchart LR
    subgraph ROLES["Roles"]
        ADMIN["👤 Admin"]
        SEC["🗂️ Secretaria"]
        DIR["📋 Directivo"]
        SOC["⛰️ Socio"]
    end

    subgraph OPS["Operaciones"]
        VER_PROPIO["GET /socios/me\n(ver propio nivel)"]
        VER_OTRO["GET /socios/{id}\n(ver nivel de cualquier socio)"]
        ACTUALIZAR["PATCH /socios/{id}/nivel-tecnico\n(asignar o cambiar nivel)"]
        LOOKUPS["GET /socios/lookups\n(catálogo de niveles disponibles)"]
    end

    ADMIN -->|"✅"| VER_PROPIO & VER_OTRO & ACTUALIZAR & LOOKUPS
    SEC   -->|"✅"| VER_PROPIO & VER_OTRO & ACTUALIZAR & LOOKUPS
    DIR   -->|"✅"| VER_PROPIO & VER_OTRO & ACTUALIZAR & LOOKUPS
    SOC   -->|"✅"| VER_PROPIO & LOOKUPS
    SOC   -->|"❌ 403"| VER_OTRO & ACTUALIZAR

    style ACTUALIZAR fill:#fff3cc,stroke:#cc9900
```
