# Seguridad del Módulo de Gestión Documental

**Última actualización:** 2026-06-07
**Audiencia:** Desarrolladores, auditores de seguridad, responsables de cumplimiento legal.

---

## 1. Modelo de amenaza

El módulo de gestión documental maneja dos tipos de datos con requisitos de seguridad distintos:

**Datos de alta sensibilidad (categoría especial — LOPDP Art. 23):**
- Información médica del socio (`socio_medical_info`)
- Consentimiento médico (`MEDICAL_DATA_CONSENT` y sus aceptaciones)

**Datos de trazabilidad legal (evidencia):**
- Aceptaciones de documentos (`legal_document_acceptances`, `activity_risk_acceptances`)
- Log de auditoría (`audit_log`)

Las amenazas principales son:
1. Falsificación de aceptaciones (alguien dice haber aceptado algo que no leyó)
2. Acceso no autorizado a datos médicos
3. Repudio de una aceptación ("yo nunca acepté ese documento")
4. Exposición de datos médicos en logs o respuestas de API generales

---

## 2. Cadena de evidencia legal

### 2.1 Cómo se registra una aceptación

Cuando un socio acepta un documento legal, el frontend envía **únicamente**:
```json
{ "documentId": "uuid", "documentVersion": 3 }
```

El backend captura y calcula todo lo demás:

```java
String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                    .orElse(request.getRemoteAddr());
String userAgent = request.getHeader("User-Agent");
String contentHash = sha256(legalDocument.getContent()); // desde BD, no del cliente
Instant acceptedAt = Instant.now(); // clock del servidor
```

**Por qué el frontend no puede enviar el hash**: si el cliente calculara el hash, podría enviar el hash de un documento diferente al que realmente se mostró. El backend recalcula el hash del contenido almacenado en BD, garantizando que el hash registrado corresponde exactamente al texto que estaba activo en ese momento.

### 2.2 Inmutabilidad de aceptaciones

Las tablas `legal_document_acceptances` y `activity_risk_acceptances` son **append-only**:
- Sin UPDATE
- Sin DELETE
- Sin soft delete

No hay mecanismo en el código que permita modificar una aceptación después de crearse. Esta restricción es intencional: las aceptaciones son evidencia legal equivalente a una firma electrónica.

### 2.3 Versionado de documentos

Un documento nunca se sobreescribe. Cada cambio crea una nueva versión:
- `UNIQUE(code, version)` en `legal_documents`
- La versión anterior mantiene su contenido intacto (incluso después de desactivarse)
- Las aceptaciones antiguas referencian la versión específica que fue aceptada

Esto permite demostrar retrospectivamente qué texto exacto aceptó un socio en una fecha determinada.

---

## 3. Protección de datos médicos

### 3.1 Aislamiento en tabla separada

Los datos médicos viven en `socio_medical_info`, separados de la tabla principal `socios`. Esto garantiza que:
- Un `SELECT *` sobre `socios` no expone datos médicos
- Los endpoints de listado de socios nunca incluyen campos médicos
- El acceso a datos médicos requiere un endpoint dedicado con validación explícita de roles

### 3.2 Control de acceso por rol

| Endpoint | Acceso permitido |
|----------|-----------------|
| `GET /v1/me/medical-info` | El propio socio |
| `PUT /v1/me/medical-info` | El propio socio |
| `GET /v1/admin/socios/{id}/medical-info` (info completa) | ADMIN, SECRETARIA |
| `GET /v1/salidas/{id}/participantes/medical-summary` (resumen) | Jefe de Salida asignado a ESA salida, ADMIN, SECRETARIA |

El endpoint de resumen médico para el Jefe de Salida devuelve **solo 3 campos**:
- `blood_type`
- `allergies_detail` (solo si `has_relevant_allergies = true`)
- `emergency_medication_detail` (solo si `uses_emergency_medication = true`)

El backend valida que el socio que consulta tenga `es_jefe_salida = true` para la salida específica consultada — no basta con ser Jefe de Salida de otra salida.

### 3.3 Auditoría de acceso a datos médicos

Cada acceso al resumen médico de una salida genera un evento en `audit_log`:

```json
{
  "action": "MEDICAL_INFO_VIEWED",
  "actorUserId": "uuid-del-jefe-salida",
  "resourceType": "SALIDA",
  "resourceId": "uuid-de-la-salida",
  "ipAddress": "...",
  "userAgent": "...",
  "metadataJson": {
    "participanteIds": ["uuid1", "uuid2", ...]
  }
}
```

### 3.4 Datos médicos en logs del sistema

Los datos médicos **no deben aparecer en logs**. Las reglas en el código:
- Ningún `toString()` de entidades médicas incluye los campos de detalle
- Los mappers de logging excluyen `socio_medical_info`
- Los datos del cuerpo de request/response de endpoints médicos no se loggean en el nivel DEBUG

### 3.5 Eliminación al retiro del socio

Al ejecutar `POST /v1/admin/socios/{id}/retire`:
1. `DELETE FROM socio_emergency_contacts WHERE socio_id = ?`
2. `UPDATE socio_medical_info SET deleted_at = NOW() WHERE socio_id = ?`

La información médica usa soft-delete con `deleted_at` para dar un margen de recuperación ante errores. El hard delete definitivo se puede ejecutar pasado un período de gracia. Los contactos de emergencia se eliminan directamente.

**Esta eliminación se ejecuta independientemente de si el socio tiene deuda pendiente.** Solo se conserva lo mínimo necesario para gestionar la deuda (nombre, cédula, correo, teléfono, registros financieros).

---

## 4. Requisito de consentimiento previo a datos médicos

El backend valida en el guardado de `socio_medical_info` que exista una aceptación vigente de `MEDICAL_DATA_CONSENT`:

```java
// En SocioMedicalInfoService.save()
boolean hasConsent = acceptanceRepository.existsValidAcceptance(
    socioId,
    "MEDICAL_DATA_CONSENT",
    legalDocumentRepository.findActiveByCode("MEDICAL_DATA_CONSENT").getVersion()
);
if (!hasConsent) {
    throw new InsufficientConsentException("MEDICAL_DATA_CONSENT requerido");
}
```

Esto impide que datos médicos sean guardados sin el consentimiento explícito del socio, cumpliendo con el Art. 23 de la LOPDP.

---

## 5. Cumplimiento LOPDP

| Requisito LOPDP | Implementación |
|-----------------|---------------|
| Consentimiento explícito y granular para datos de salud (Art. 23) | Checkbox independiente para `MEDICAL_DATA_CONSENT`, separado de la política general |
| Información del tratamiento al momento del consentimiento | El documento `MEDICAL_DATA_CONSENT` describe exactamente para qué se usan los datos médicos |
| Derecho de eliminación (Art. 15) | `retireSocio()` elimina datos médicos y contactos de emergencia |
| Conservación mínima necesaria | Solo se conservan datos médicos mientras el socio está activo |
| Trazabilidad del consentimiento | Cada aceptación guarda versión del documento, hash, IP, user-agent y timestamp |
| Datos de salud en categoría especial | Tabla separada, acceso restringido, auditoría en todos los accesos |

### Gaps pendientes respecto a la política PII general

Los siguientes puntos siguen siendo deuda técnica (documentados en `pii-data-retention.md`):
- Sin limpieza automática de `security_events` tras 12 meses
- Sin anonimización automática de EX_MEMBER tras 5 años
- Sin endpoint de portabilidad de datos para el titular

---

## 6. Relación con otros documentos de seguridad

- **Arquitectura general**: `docs/security/architecture/security-architecture.md`
- **PII y retención**: `docs/security/architecture/pii-data-retention.md`
- **Seguridad mobile**: `docs/security/architecture/mobile-security.md`
- **Flujo de registro**: `docs/flujos/24-registro-multistep.md`
- **Retiro de socio**: `docs/flujos/27-retiro-de-socio.md`
- **Diagramas de flujo**: `docs/security/diagramas/15_flujo_registro_documental.md` y siguientes
