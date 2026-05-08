# Diagrama 04 — Flujo de Inscripción a Salida con Validación de Nivel

## Flujo Principal de Inscripción

```mermaid
sequenceDiagram
    actor S as Socio
    actor DIR as Directivo
    actor JEFE as Jefe de Salida
    participant API as Spring Boot API
    participant DB as PostgreSQL

    S->>API: POST /api/v1/salidas/{salida_id}/inscribirse
    Note over API: Verificar: JWT válido, rol Socio o superior

    API->>DB: SELECT salida WHERE id = salida_id
    DB-->>API: datos de salida (estado, ruta_id, nivel_minimo, capacidad)

    alt Salida no está PLANIFICADA
        API-->>S: 409 "La salida no admite inscripciones"
    end

    alt Capacidad máxima alcanzada
        API-->>S: 409 "Salida sin cupos disponibles"
    end

    API->>DB: SELECT estado_habilitacion FROM socios WHERE id = socio_id
    alt Socio inhabilitado Y config BLOQUEAR_INSCRIPCION_INHABILITADOS = true
        API-->>S: 403 "No puedes inscribirte. Contacta a un Directivo"
    end

    API->>DB: SELECT nivel_tecnico_id FROM socios WHERE id = socio_id
    API->>DB: SELECT acceso_ruta_por_nivel WHERE nivel_socio_id = nivel_tecnico_id
    API->>DB: SELECT rutas WHERE id = ruta_id (obtener los 6 valores de dificultad)

    Note over API: Comparar cada dificultad de la ruta<br/>con el máximo del nivel del socio (via ranks)
    API->>API: Evaluar los 6 ejes de dificultad

    alt Todos los ejes dentro del límite (verde)
        API->>DB: INSERT salida_participantes\n(estado=INSCRITO, es_jefe_salida=false)
        API->>DB: INSERT INTO auditoria (INSCRIPCION_OK)
        API-->>S: 201 "Inscripción exitosa"
    end

    alt Algún eje supera el límite del nivel (bloqueado)
        API-->>S: 200 {bloqueado: true, ejes_excedidos: [...]}
        Note over S: Socio ve qué dificultades no cumple.<br/>No puede auto-inscribirse.

        Note over DIR,JEFE: Se notifica al Directivo y<br/>Jefe de Salida asignado (si existe)

        DIR->>API: POST /api/v1/salidas/{salida_id}/aprobar-riesgo\n{socio_id, aprobacion: true}
        Note over API: Verificar: JWT válido, rol Directivo
        API->>DB: UPDATE salida_participantes\nSET riesgo_aprobado_por_directivo = dir_id,\nriesgo_aprobado_en = NOW()\nWHERE socio_id = ? AND salida_id = ?
        API->>DB: INSERT INTO auditoria (RIESGO_APROBADO_DIRECTIVO)

        JEFE->>API: POST /api/v1/salidas/{salida_id}/aprobar-riesgo\n{socio_id, aprobacion: true}
        Note over API: Verificar: es_jefe_salida = true\npara ESTA salida_id específica
        API->>DB: UPDATE salida_participantes\nSET riesgo_aprobado_por_jefe = jefe_id
        API->>DB: INSERT INTO auditoria (RIESGO_APROBADO_JEFE)

        Note over API,DB: Solo cuando AMBAS aprobaciones existen:<br/>crear inscripción definitiva
        API->>DB: Verificar ambas aprobaciones presentes
        API->>DB: INSERT salida_participantes\n(estado=INSCRITO, con ambos aprobadores)
        API-->>S: Notificación "Inscripción aprobada"
    end
```

---

## Flujo: Asignación de Jefe de Salida y Dignidades

```mermaid
sequenceDiagram
    actor DIR as Directivo
    actor JEFE as Jefe de Salida
    actor S as Socio participante
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Note over DIR: Con la lista de inscritos lista,<br/>el Directivo designa al Jefe de Salida

    DIR->>API: PATCH /api/v1/salidas/{salida_id}/jefe\n{socio_id: jefe_elegido_id}
    Note over API: Verificar: rol Directivo<br/>El socio elegido debe estar INSCRITO en esa salida

    API->>DB: UPDATE salida_participantes\nSET es_jefe_salida = true\nWHERE salida_id = ? AND socio_id = ?
    API->>DB: INSERT INTO auditoria\n(ASIGNAR_JEFE_SALIDA, entidad=salida_id)
    API-->>DIR: 200 "Jefe de Salida asignado"

    Note over JEFE: El Jefe de Salida ahora puede<br/>asignar dignidades a los participantes

    JEFE->>API: POST /api/v1/salidas/{salida_id}/participantes/{socio_id}/dignidades\n{dignidad_ids: [1, 6]}
    Note over API: Verificar: es_jefe_salida = true\npara esta salida_id exacta\nSalida en estado PLANIFICADA o EN_CURSO

    API->>DB: SELECT id FROM salida_participantes\nWHERE salida_id = ? AND socio_id = ?
    API->>DB: INSERT salida_participante_dignidades\n(participante_id, dignidad_id) × N
    API->>DB: INSERT INTO auditoria (ASIGNAR_DIGNIDAD)
    API-->>JEFE: 201 "Dignidades asignadas"

    Note over JEFE: El Jefe de Salida también puede<br/>cancelar la salida si es necesario

    JEFE->>API: PATCH /api/v1/salidas/{salida_id}\n{estado: CANCELADA, motivo: "..."}
    Note over API: Verificar: es_jefe_salida = true<br/>O rol Directivo / Secretaria

    API->>DB: UPDATE salida SET estado = CANCELADA
    API->>DB: INSERT INTO auditoria (CANCELAR_SALIDA)
    API-->>JEFE: 200 "Salida cancelada"
```

---

## Flujo: Transición de Estado de Salida (Scheduler)

```mermaid
flowchart TD
    START(["⏰ Spring @Scheduled\nEjecuta cada hora"])

    START --> Q1{"¿Salidas PLANIFICADAS\ncon fecha_inicio <= NOW()?"}
    Q1 -->|Sí| A1["UPDATE salida SET estado = EN_CURSO"]
    Q1 -->|No| Q2

    A1 --> Q2{"¿Salidas EN_CURSO\ncon fecha_fin < NOW()?"}
    Q2 -->|Sí| A2["UPDATE salida SET estado = REALIZADA"]
    Q2 -->|No| Q3

    A2 --> Q3{"¿Socios con\ntipo_socio = Juvenil\nque cumplen 18 hoy?"}
    Q3 -->|Sí| A3["UPDATE socios SET tipo_socio_id = Socio Activo\nINSERT auditoria (AUTO_TRANSICION_JUVENIL)"]
    Q3 -->|No| END_NODE(["✅ Fin del ciclo"])

    A3 --> END_NODE
```
