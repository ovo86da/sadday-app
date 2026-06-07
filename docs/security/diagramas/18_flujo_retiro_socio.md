# Diagrama 18 — Flujo de Retiro de Socio y Eliminación de Datos Sensibles

## Flujo Completo: Confirmación + Eliminación + Auditoría

```mermaid
sequenceDiagram
    actor SEC as Secretaria/Admin
    participant FE as Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL

    SEC->>FE: Presiona "Dar de baja al socio"\nen el detalle de Juan Pérez

    FE->>API: GET /api/v1/admin/socios/{id}
    API->>DB: SELECT nombre, apellido FROM socios WHERE id=?
    FE->>API: GET /api/v1/admin/socios/{id}/profile-completion-status
    API-->>FE: Datos del socio + estado del perfil

    Note over FE: Verifica si tiene deuda pendiente
    FE->>API: GET /api/v1/admin/socios/{id}/financial-status
    API-->>FE: { hasDebt: true/false }

    FE-->>SEC: Diálogo de confirmación:\n"Se eliminarán: contactos de emergencia, información médica"\n"Se conservarán: historial salidas, aceptaciones legales, historial financiero"\n[Si tiene deuda: "⚠️ Tiene deuda — se conserva correo y teléfono"]\n\nMotivo (campo obligatorio): _____________\nConfirmación (campo obligatorio):\nEscriba: 'Si, deseo eliminar al socio Juan Pérez'\n_____________\n[Confirmar — deshabilitado hasta que el texto coincida]

    SEC->>FE: Escribe motivo y texto de confirmación
    Note over FE: El botón se habilita cuando\nel texto coincide exactamente
    SEC->>FE: Presiona "Confirmar"

    FE->>API: POST /api/v1/admin/socios/{id}/retire\n{\n  reason: "Renuncia voluntaria",\n  confirmationText: "Si, deseo eliminar al socio Juan Pérez"\n}

    Note over API: Validar confirmationText en el backend también\n(no confiar solo en el frontend)
    API->>API: Comparar confirmationText con\n"Si, deseo eliminar al socio {nombre} {apellido}"\n(case-insensitive)

    alt confirmationText no coincide
        API-->>FE: 400 — texto de confirmación incorrecto
        FE-->>SEC: Error: "El texto de confirmación no coincide"
    else Texto válido
        Note over API: Ejecutar eliminación en transacción

        API->>DB: DELETE FROM socio_emergency_contacts WHERE socio_id=?
        API->>DB: UPDATE socio_medical_info SET deleted_at=NOW() WHERE socio_id=?
        API->>DB: UPDATE socios SET estado_acceso=EX_MEMBER, fecha_salida=NOW()
        API->>DB: UPDATE usuarios_auth SET active=false WHERE socio_id=?
        API->>DB: DELETE FROM refresh_tokens WHERE socio_id=? (revocar sesiones)

        alt Sin deuda pendiente
            API->>DB: UPDATE socios SET correo=null, telefono=null WHERE id=?
        else Con deuda pendiente
            Note over API: Conservar correo y teléfono para gestionar deuda
        end

        API->>DB: INSERT audit_log\n(action=SOCIO_RETIRED,\n actor_user_id=sec_id,\n resource_type=SOCIO,\n resource_id=socio_id,\n metadata={reason: "Renuncia voluntaria"})

        API->>DB: INSERT audit_log\n(action=SENSITIVE_DATA_DELETED,\n actor_user_id=sec_id,\n resource_type=SOCIO,\n resource_id=socio_id,\n metadata={deleted: ["emergency_contacts", "medical_info"]})

        API-->>FE: 200 — { success: true }
        FE-->>SEC: "Juan Pérez ha sido dado de baja del sistema.\nSus datos médicos y contactos de emergencia han sido eliminados."
    end
```

---

## Qué datos persisten después del retiro

```mermaid
flowchart LR
    SOC[("socios\n(Juan Pérez)")]

    subgraph ELIMINADO["❌ Eliminado al retirar"]
        EC["socio_emergency_contacts\n(los 2 contactos)"]
        MI["socio_medical_info\n(datos de salud)"]
        RT["refresh_tokens\n(sesiones activas)"]
        COR["correo / teléfono\n(solo si sin deuda)"]
    end

    subgraph CONSERVADO["✅ Conservado siempre"]
        NOM["nombre, apellido, cedula"]
        HIST["historial salidas\n(salida_participantes)"]
        LEG["aceptaciones legales\n(legal_document_acceptances)"]
        FIN["historial financiero"]
        CAR["cargos/dignidades en salidas"]
        AU["auth desactivada\n(usuarios_auth.active=false)"]
    end

    SOC --> ELIMINADO
    SOC --> CONSERVADO
```

---

## Estado del socio después del retiro

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : Registro completado
    ACTIVE --> EX_MEMBER : POST /admin/socios/{id}/retire\n(con confirmación escrita + motivo)
    EX_MEMBER --> ACTIVE : Admin revierte la baja\n(los datos médicos ya no existen)
    EX_MEMBER --> [*] : Anonimización después de 5 años\n(proceso manual — pendiente automatizar)
```

**Nota**: si un ex-socio es reactivado, deberá completar de nuevo su información médica y contactos de emergencia, ya que fueron eliminados al dar la baja.
