# Diagrama 10 — Ciclo de Vida de Salidas y Estados de Inscripción

## Máquina de Estados — Salida

```mermaid
stateDiagram-v2
    [*] --> PLANIFICADA : POST /salidas\n(Admin / Secretaria / Directivo)

    PLANIFICADA --> EN_CURSO : Scheduler 00:02\nfechaInicio <= hoy
    PLANIFICADA --> REALIZADA : Scheduler 00:02\nfechaFin < hoy\n(salida muy corta sin EN_CURSO)
    PLANIFICADA --> CANCELADA : PATCH /{id}/estado\n(Admin / Directivo)

    EN_CURSO --> REALIZADA : Scheduler 00:02\nfechaFin < hoy
    EN_CURSO --> CANCELADA : PATCH /{id}/estado\n(Admin / Directivo)

    REALIZADA --> [*]
    CANCELADA --> [*]

    note right of PLANIFICADA
        Admite inscripciones.
        Puede editarse con PUT.
        Puede eliminarse con DELETE (Admin/Directivo).
    end note

    note right of EN_CURSO
        La salida está en progreso.
        No admite nuevas inscripciones.
        Jefe puede gestionar dignidades.
    end note

    note right of REALIZADA
        Estado final normal.
        Se puede crear/editar informe.
        Directivo puede validar el informe.
    end note

    note right of CANCELADA
        Estado final de cancelación.
        No genera informe.
    end note
```

---

## Scheduler — Transiciones Automáticas y Promoción de Juveniles

```mermaid
flowchart TD
    subgraph STARTUP["Al arrancar la aplicación (@EventListener ApplicationReadyEvent)"]
        S0["doActualizarEstadoSalidas()\n(sincroniza estados de salidas con fechas pasadas)"]
    end

    subgraph NOCHE["Tareas nocturnas (@Scheduled cron)"]
        CRON1["00:01 — promoverJuvenilesMayoresDeEdad()"]
        CRON2["00:02 — actualizarEstadoSalidasDiario()"]
    end

    subgraph LOGICA_SALIDAS["doActualizarEstadoSalidas()"]
        Q1{"¿Salidas PLANIFICADAS\ncon fechaInicio <= hoy?"}
        A1["UPDATE salida SET estado = EN_CURSO\npara cada una"]
        Q2{"¿Salidas PLANIFICADAS\no EN_CURSO con fechaFin < hoy?"}
        A2["UPDATE salida SET estado = REALIZADA\npara cada una"]
        FIN["Log: N salidas actualizadas\no 'ningún cambio necesario'"]

        Q1 -->|"Sí"| A1
        Q1 -->|"No"| Q2
        A1 --> Q2
        Q2 -->|"Sí"| A2
        Q2 -->|"No"| FIN
        A2 --> FIN
    end

    subgraph LOGICA_JUVENILES["promoverJuvenilesMayoresDeEdad()"]
        QJ{"¿Socios tipo=Juvenil\ncon fechaNacimiento <= hoy-18años?"}
        AJ["UPDATE socios SET tipo_socio = 'Socio Activo'\npara cada uno"]
        FINJ["Log: N socios promovidos"]

        QJ -->|"Sí"| AJ
        QJ -->|"No"| FINJ
        AJ --> FINJ
    end

    STARTUP --> LOGICA_SALIDAS
    CRON1 --> LOGICA_JUVENILES
    CRON2 --> LOGICA_SALIDAS
```

---

## Máquina de Estados — Inscripción de Participante

```mermaid
stateDiagram-v2
    [*] --> INSCRITO : Nivel suficiente\nPOST /salidas/{id}/inscripciones

    [*] --> PENDIENTE_APROBACION : Nivel insuficiente\no nivelTecnico = null

    PENDIENTE_APROBACION --> INSCRITO : Directivo aprueba\nY Jefe de Salida aprueba\n(ambas firmas requeridas)
    PENDIENTE_APROBACION --> NEGADO : Cualquiera de los dos niega\nPATCH /inscripciones/{id}/aprobacion-riesgo\n{aprobar: false}
    PENDIENTE_APROBACION --> CANCELADO : El socio cancela\nDELETE /inscripciones/{participanteId}

    INSCRITO --> CANCELADO : DELETE /inscripciones/{participanteId}\n(el socio cancela o Admin/Directivo)
    INSCRITO --> CONFIRMADO : PATCH /inscripciones/{id}/estado\n(Admin / Secretaria / Directivo / Jefe)\nestado = CONFIRMADO
    INSCRITO --> NO_FUE : PATCH /inscripciones/{id}/estado\nestado = NO_FUE (no asistió)

    CONFIRMADO --> [*]
    NEGADO --> [*]
    CANCELADO --> [*]
    NO_FUE --> [*]

    note right of PENDIENTE_APROBACION
        Notifica a Directivo y Jefe asignado.
        GET /salidas/aprobaciones-pendientes
        muestra las pendientes del usuario autenticado.
    end note

    note right of INSCRITO
        Puede recibir dignidades
        (Jefe / Admin / Secretaria / Directivo).
    end note
```

---

## Flujo de Aprobación de Riesgo (Doble Firma)

```mermaid
sequenceDiagram
    actor S as Socio (nivel insuficiente)
    actor DIR as Directivo / Admin
    actor JEFE as Jefe de Salida
    participant API as Spring Boot API
    participant DB as PostgreSQL

    S->>API: POST /api/v1/salidas/{id}/inscripciones\n{socioId}
    Note over API: nivelSocio.nivel < nivelMinimo.nivel
    API->>DB: INSERT salida_participantes\n(estadoInscripcion = PENDIENTE_APROBACION,\nriesgoAprobadoPorDirectivo = null,\nriesgoAprobadoPorJefe = null)
    API-->>S: 201 {estado: "PENDIENTE_APROBACION"}

    Note over DIR,JEFE: Ambos pueden ver las pendientes en:\nGET /api/v1/salidas/aprobaciones-pendientes

    DIR->>API: PATCH /api/v1/salidas/{id}/inscripciones/{participanteId}/aprobacion-riesgo\n{aprobar: true}
    API->>DB: UPDATE SET riesgoAprobadoPorDirectivo = dirId
    Note over DB: riesgoAprobadoPorJefe aún null → sigue PENDIENTE

    JEFE->>API: PATCH /api/v1/salidas/{id}/inscripciones/{participanteId}/aprobacion-riesgo\n{aprobar: true}
    Note over API: Verifica: es_jefe_salida = true para esta salida
    API->>DB: UPDATE SET riesgoAprobadoPorJefe = jefeId

    Note over API: Ambas aprobaciones presentes → promover a INSCRITO
    API->>DB: UPDATE SET estadoInscripcion = INSCRITO
    API->>DB: INSERT INTO auditoria (RIESGO_APROBADO_AMBOS)
    API-->>JEFE: 200 {estado: "INSCRITO"}

    Note over DIR,JEFE: Si cualquiera niega:
    DIR->>API: PATCH .../aprobacion-riesgo {aprobar: false}
    API->>DB: UPDATE SET estadoInscripcion = NEGADO
    API->>DB: INSERT INTO auditoria (RIESGO_NEGADO)
    API-->>DIR: 200 {estado: "NEGADO"}

    Note over S: El socio queda NEGADO.\nNo puede re-inscribirse automáticamente.
```

---

## Control de Acceso por Rol — Operaciones de Salida

```mermaid
flowchart TD
    subgraph CREAR["Crear / Editar salida"]
        C1["POST /salidas\nPUT /salidas/{id}"]
        C2["Admin / Secretaria / Directivo"]
        C1 --- C2
    end

    subgraph ESTADO["Cambiar estado manual"]
        E1["PATCH /salidas/{id}/estado"]
        E2["Admin / Directivo"]
        E1 --- E2
    end

    subgraph INSCRIBIR["Inscribir participantes"]
        I1["POST /salidas/{id}/inscripciones"]
        I2["Cualquier autenticado\n(solo se puede inscribir a sí mismo\nsalvo Admin/Secretaria/Directivo)"]
        I1 --- I2
    end

    subgraph RIESGO["Aprobar/negar riesgo"]
        R1["PATCH /inscripciones/{id}/aprobacion-riesgo"]
        R2["Directivo/Admin\nO Jefe de Salida de esa salida"]
        R1 --- R2
    end

    subgraph JEFE["Designar Jefe de Salida"]
        J1["PATCH /inscripciones/{id}/jefe"]
        J2["Admin / Secretaria / Directivo"]
        J1 --- J2
    end

    subgraph CERRAR["Abrir/cerrar inscripciones"]
        CL1["PATCH /salidas/{id}/cerrar-inscripciones"]
        CL2["Jefe de Salida\nO Admin / Directivo"]
        CL1 --- CL2
    end

    style RIESGO fill:#fff3cc,stroke:#cc9900
    style ESTADO fill:#fff3cc,stroke:#cc9900
```
