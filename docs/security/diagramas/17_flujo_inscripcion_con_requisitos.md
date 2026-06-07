# Diagrama 17 — Inscripción a Salida con Validación de Perfil Completo y Riesgo

## Flujo Completo: Verificación de Requisitos + Aceptación de Riesgo + Inscripción

```mermaid
sequenceDiagram
    actor SOC as Socio
    participant FE as Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL

    SOC->>FE: Abre detalle de una salida

    Note over FE: Consulta estado del socio y requisitos de la salida en paralelo

    par Verificar perfil
        FE->>API: GET /api/v1/me/profile-completion-status
        API->>DB: Consultar 7 requisitos:\n1. Datos básicos completos\n2. 2 contactos emergencia\n3. Info médica presente\n4. MEDICAL_DATA_CONSENT vigente\n5. DATA_RETENTION_POLICY vigente\n6. LIABILITY_WAIVER vigente\n7. Sin docs con nueva versión sin aceptar
        DB-->>API: Estado de cada requisito
        API-->>FE: { canEnrollActivities, missingRequirements[], pendingDocuments[] }
    and Obtener documento de riesgo
        FE->>API: GET /api/v1/salidas/{id}/risk-document
        API->>DB: SELECT * FROM activity_risk_documents\nWHERE activity_id=? AND active=true
        DB-->>API: { id, content, version } o null
        API-->>FE: Documento de riesgo (si existe)
    end

    alt canEnrollActivities = false
        FE-->>SOC: Panel de requisitos faltantes\n[req faltante 1 — botón "Completar ahora"]\n[req faltante 2 — botón "Completar ahora"]\n[req cumplido ✓]\n[req cumplido ✓]

        Note over SOC,FE: El socio completa los requisitos faltantes\n(navega a perfil, acepta documentos, etc.)

        FE->>API: GET /api/v1/me/profile-completion-status (re-verifica)
        API-->>FE: { canEnrollActivities: true }
        FE-->>SOC: Panel actualizado — todos los requisitos cumplidos
    end

    alt Salida tiene documento de riesgo activo
        FE-->>SOC: Muestra contenido Markdown del documento de riesgo\n+ checkbox "He leído y acepto los riesgos de esta salida"

        Note over SOC: El botón "Inscribirse" está deshabilitado\nhasta que el checkbox esté marcado

        SOC->>FE: Marca el checkbox de riesgos
        FE-->>SOC: Botón "Inscribirse" se habilita
    else Sin documento de riesgo
        FE-->>SOC: Botón "Inscribirse" visible directamente
    end

    SOC->>FE: Presiona "Inscribirse"

    alt Tiene documento de riesgo que aceptar
        FE->>API: POST /api/v1/salidas/{id}/risk-document/accept\n{ activityRiskDocumentId, documentVersion }
        Note over API: Captura IP, user-agent\nRecalcula hash desde BD
        API->>DB: INSERT activity_risk_acceptances\n(socio_id, activity_id, doc_id, hash, ip, user_agent, accepted_at=NOW())
        API->>DB: INSERT audit_log (ACTIVITY_RISK_ACCEPTED)
        API-->>FE: 200 — riesgo aceptado
    end

    FE->>API: POST /api/v1/salidas/{id}/inscribirse

    Note over API: Validaciones finales del backend (no confiar solo en el frontend)
    API->>DB: Verificar: salida en estado PLANIFICADA
    API->>DB: Verificar: cupo disponible
    API->>DB: Verificar: canEnrollActivities = true (re-check server-side)
    API->>DB: Verificar: riesgo aceptado (si aplica)
    API->>DB: Verificar: nivel técnico del socio (flujo existente)

    alt Alguna validación falla
        API-->>FE: 409 / 403 con motivo específico
        FE-->>SOC: Mensaje de error con la razón
    else Todo OK
        API->>DB: INSERT salida_participantes\n(socio_id, salida_id, estado=INSCRITO)
        API->>DB: INSERT audit_log (INSCRIPCION_OK)
        API-->>FE: 201 — inscripción exitosa
        FE-->>SOC: "Te has inscrito a la salida ✓"
    end
```

---

## Evaluación del perfil completo (lógica del backend)

```mermaid
flowchart TD
    START(["ProfileCompletionService\n.getStatus(socioId)"]) --> R1

    R1{"Datos básicos completos?\nnombre, apellido, cedula,\ntelefono, direccion"} -->|No| FAIL1["❌ Debe completar datos personales"]
    R1 -->|Sí| R2

    R2{"COUNT(emergency_contacts) = 2?"} -->|No| FAIL2["❌ Debe registrar 2 contactos de emergencia"]
    R2 -->|Sí| R3

    R3{"EXISTS socio_medical_info\nWHERE socio_id=? AND deleted_at IS NULL"} -->|No| FAIL3["❌ Debe completar información médica"]
    R3 -->|Sí| R4

    R4{"MEDICAL_DATA_CONSENT\naceptado en versión activa?"} -->|No| FAIL4["❌ Debe aceptar el consentimiento médico vigente"]
    R4 -->|Sí| R5

    R5{"DATA_RETENTION_POLICY\naceptada en versión activa?"} -->|No| FAIL5["❌ Debe aceptar la política de retención de datos vigente"]
    R5 -->|Sí| R6

    R6{"LIABILITY_WAIVER\naceptado en versión activa?"} -->|No| FAIL6["❌ Debe aceptar el descargo de responsabilidad vigente"]
    R6 -->|Sí| R7

    R7{"Documentos obligatorios\ncon nueva versión sin aceptar?"} -->|Sí| FAIL7["❌ Debe aceptar documentos actualizados"]
    R7 -->|No| SUCCESS["✅ canEnrollActivities = true"]

    FAIL1 & FAIL2 & FAIL3 & FAIL4 & FAIL5 & FAIL6 & FAIL7 --> RESULT["Devolver lista de\nmissingRequirements[]"]
```
