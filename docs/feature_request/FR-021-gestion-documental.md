# FR-021: Módulo de Gestión Documental

**Fecha:** 2026-06-05
**Estado:** ✅ Implementado
**Módulo:** Backend + Frontend Web + Mobile + MCP
**Prioridad:** Alta
**Ramas Git:** `feature/doc-gestion-fase1-schema` … `feature/doc-gestion-fase13-mobile` (mergeadas a `develop`)

---

## 1. Resumen Ejecutivo

Implementar un módulo completo de gestión documental para el Club de Montaña Sadday. El objetivo es capturar consentimientos legales versionados, información médica y de contacto de emergencia de los socios, y aceptaciones de riesgo por actividad, todo con trazabilidad legal completa (IP, user-agent, hash del documento, timestamp del servidor). Se añade un pipeline de completitud de perfil que bloquea inscripciones a salidas hasta que el socio cumpla todos los requisitos.

La propuesta completa de diseño vive en `document_gestion/propuesta_gestion_documental.md`.

---

## 2. Cambios de Esquema (migraciones V11–V17)

| Migración | Contenido |
|-----------|-----------|
| `V11__legal_documents.sql` | Tablas `legal_documents` y `legal_document_acceptances` |
| `V12__emergency_contacts_table.sql` | Tabla `socio_emergency_contacts` + migración de datos inline de `socios` |
| `V13__medical_info.sql` | Tabla `socio_medical_info` + migración de `socios.tipo_sangre` |
| `V14__activity_risk_documents.sql` | Tablas `activity_risk_documents` y `activity_risk_acceptances` |
| `V15__audit_log.sql` | Tabla `audit_log` (append-only) |
| `V16__drop_emergency_contact_columns.sql` | DROP de las 6 columnas `emergency_contact_*` de `socios` |
| `V17__drop_tipo_sangre.sql` | DROP de `socios.tipo_sangre` |

### Tablas nuevas

**`legal_documents`** — Documentos legales versionados. Solo una versión activa por código. SHA-256 calculado en backend.

**`legal_document_acceptances`** — Inmutable. Cada aceptación guarda: socio_id, legal_document_id, document_version, content_hash (recalculado en backend), ip_address (capturada en backend), user_agent, accepted_at (timestamp del servidor). El frontend nunca envía estos campos.

**`socio_emergency_contacts`** — Hasta 2 contactos por socio (orden 1 y 2). Campos: nombre_completo, relacion, celular, direccion.

**`socio_medical_info`** — Una fila por socio (UNIQUE en socio_id). Campos: blood_type (opcional), has_relevant_allergies, allergies_detail, has_relevant_medical_condition, medical_condition_detail, uses_emergency_medication, emergency_medication_detail, additional_notes. Soft-delete con deleted_at.

**`activity_risk_documents`** — Documento de riesgos específico por salida. Una versión activa por salida.

**`activity_risk_acceptances`** — Aceptación de riesgo de una salida. Inmutable.

**`audit_log`** — Eventos sensibles. Append-only. Eventos auditados: LEGAL_DOCUMENT_ACCEPTED, LEGAL_DOCUMENT_CREATED, LEGAL_DOCUMENT_VERSION_CREATED, MEDICAL_INFO_VIEWED, MEDICAL_INFO_UPDATED, EMERGENCY_CONTACT_UPDATED, SOCIO_RETIRED, SENSITIVE_DATA_DELETED, ACTIVITY_RISK_ACCEPTED, ACTIVITY_ENROLLMENT_BLOCKED.

---

## 3. Documentos Legales del Sistema

| Código | Descripción | Etapa |
|--------|-------------|-------|
| `DATA_PROCESSING_POLICY` | Política de Tratamiento de Datos Personales | REGISTRATION |
| `MEDICAL_DATA_CONSENT` | Consentimiento para tratamiento de datos médicos | REGISTRATION |
| `DATA_RETENTION_POLICY` | Política de Conservación y Eliminación de Datos | PROFILE_COMPLETION |
| `LIABILITY_WAIVER` | Declaración de Conocimiento de Riesgos y Descargo | PROFILE_COMPLETION |

`DATA_PROCESSING_POLICY` y `MEDICAL_DATA_CONSENT` se muestran juntos en el registro pero son documentos y aceptaciones **independientes**. El Reglamento Interno no requiere aceptación versionada (sigue como contenido estático).

---

## 4. Backend (Fases 1–8)

### Controladores implementados

| Controlador | Endpoints | Acceso |
|-------------|-----------|--------|
| `LegalDocumentController` | GET active, GET by code, POST accept, GET my acceptances | Público + Autenticado |
| `LegalDocumentAdminController` | CRUD + activar versión + ver aceptaciones + pendientes + estado por socio | ADMIN, SECRETARIA |
| `EmergencyContactController` | GET/PUT mis contactos | Socio |
| `EmergencyContactAdminController` | GET/PUT contactos de un socio | ADMIN, SECRETARIA |
| `MedicalInfoController` | GET/PUT mi info médica | Socio |
| `MedicalInfoAdminController` | GET info médica de un socio, GET resumen de emergencia por salida | ADMIN, SECRETARIA, Jefe de Salida asignado |
| `ProfileCompletionController` | GET estado de completitud propio y de un socio | Socio / ADMIN, SECRETARIA |
| `SocioRetirementController` | POST retire (con reason) | ADMIN, SECRETARIA |
| `ActivityRiskDocumentController` | GET/POST risk-document, POST accept | Autenticado / DIRECTIVO, ADMIN |

### Servicios clave

- `LegalDocumentService` — CRUD, activación de versiones, cálculo SHA-256 en backend
- `LegalDocumentAcceptanceService` — registra aceptaciones capturando IP/user-agent/hash del lado del servidor
- `ProfileCompletionService` — valida los 7 requisitos; devuelve strings legibles en español
- `SocioDataRetentionService` — retiro de socio: elimina datos sensibles, conserva datos legítimos
- `AuditLogService` — registra todos los eventos sensibles del módulo
- `ActivityRiskDocumentService` / `ActivityRiskAcceptanceService`

### Validaciones críticas

- No se guardan datos médicos sin `MEDICAL_DATA_CONSENT` vigente y aceptado
- El endpoint de resumen médico de salida valida que el solicitante sea el Jefe de Salida asignado a esa salida específica (o ADMIN/SECRETARIA). Acceso auditado con `MEDICAL_INFO_VIEWED`
- La inscripción a una salida verifica: perfil completo (`canEnrollActivities = true`) + aceptación del documento de riesgo específico de esa salida + cupo disponible
- Hash SHA-256 del contenido se recalcula en backend al registrar cada aceptación — nunca viene del frontend

---

## 5. Frontend Web (Fases 9–12)

### Registro multi-paso (Fase 9)

Wizard de 6 pasos en `/registro-completar`:

1. Consentimientos iniciales: dos checkboxes independientes (DATA_PROCESSING_POLICY + MEDICAL_DATA_CONSENT)
2. Datos personales (nombre, apellido, cédula, teléfono, dirección, fecha nacimiento, contraseña)
3. Contactos de emergencia (2 contactos obligatorios: orden, nombre, relación, celular, dirección)
4. Información médica (tipo de sangre opcional + 3 preguntas booleanas con detalle condicional)
5. Aceptaciones finales: DATA_RETENTION_POLICY + LIABILITY_WAIVER
6. Confirmación y activación de cuenta

Progreso persistido en BD — reanudable si el socio cierra el navegador.

### Perfil del socio (Fase 10 + 12)

- **Tab "Mis documentos"**: documentos activos con Markdown renderizado, checkbox de aceptación, historial de aceptaciones propias
- **Tab "Contactos de emergencia"**: formulario para los 2 contactos
- **Tab "Información médica"**: formulario completo con tipo de sangre, alergias, condición médica, medicación de emergencia
- **Banner en dashboard**: alerta cuando hay documentos obligatorios pendientes de aceptar
- **Panel de requisitos faltantes**: en el diálogo de detalle de salida, cuando el socio no puede inscribirse se muestra la lista exacta de requisitos con botón "Completar ahora" por ítem
- **Aceptación de riesgo inline**: panel `RiskDocAcceptancePanel` dentro del diálogo de salida antes de inscribirse

### Admin (Fase 11 + 12)

- **Tab "Documentos legales"** con 3 sub-vistas:
  - Lista de documentos: código, tipo, stage, versión activa, estado, fecha activación
  - Pendientes de aceptar: acordeón por documento con socios que aún no aceptaron la versión activa
  - Socios bloqueados: socios sin documentos requeridos aceptados
- **Modal de nueva versión**: textarea + preview Markdown en tiempo real; precarga contenido actual; opción de cargar .md
- **Historial de versiones** por código
- **Diálogo de retiro de socio**: muestra qué datos se eliminan y cuáles se conservan; requiere escribir exactamente `Si, deseo eliminar al socio NOMBRE APELLIDO`; botón deshabilitado hasta que el texto coincida
- **Resumen médico para Jefe de Salida**: visible en el diálogo de salida solo si el usuario es el Jefe asignado — muestra blood_type, alergias, medicación de emergencia

---

## 6. Mobile Flutter (Fase 13)

- Wizard de registro de 6 pasos equivalente al web
- Banner de documentos pendientes con navegación directa a aceptarlos
- **Tab "Mis documentos"** en perfil: lista de documentos con content Markdown renderizado, checkbox de aceptación
- **Tab "Contactos de emergencia"** en perfil: formulario para los 2 contactos
- **Tab "Información médica"** en perfil: formulario completo
- **Panel de requisitos faltantes** en pantalla de detalle de salida: lista con botones "Completar ahora" que navegan directamente a la sección correspondiente
- **Bottom sheet de aceptación de riesgo** antes de inscribirse a una salida
- Para ADMIN/SECRETARIA: pantalla de gestión de documentos legales (Tab A: documentos + versiones; Tab B: aceptaciones + pendientes), editor de texto con preview, activación de versiones
- Para ADMIN/SECRETARIA: bottom sheet de confirmación de retiro con campo de texto de confirmación escrita

---

## 7. MCP (Model Context Protocol)

Herramientas de solo lectura añadidas al servidor MCP:

- `get_legal_documents` — lista documentos activos y sus versiones
- `get_profile_completion_status` — estado de completitud de un socio por ID
- `get_audit_log` — consulta el log de auditoría por actor, tipo de evento o recurso

---

## 8. Criterios de Aceptación (todos cumplidos)

- [x] Usuario no puede registrarse sin aceptar DATA_PROCESSING_POLICY y MEDICAL_DATA_CONSENT (independientes)
- [x] Aceptaciones guardan versión, hash, IP, user-agent y timestamp del servidor — ninguno de estos datos viene del frontend
- [x] Los documentos legales son versionados; nunca se sobreescribe un documento aceptado
- [x] Si hay nueva versión activa de documento obligatorio, el socio debe volver a aceptarla
- [x] Información médica en tabla separada (socio_medical_info)
- [x] Contactos de emergencia en tabla separada (socio_emergency_contacts)
- [x] No se guardan datos médicos sin MEDICAL_DATA_CONSENT vigente
- [x] Socio necesita 2 contactos de emergencia e información médica respondida para quedar habilitado
- [x] Jefe de Salida ve solo el resumen médico de emergencia (blood_type, alergias, medicación) de los inscritos en su salida, con auditoría
- [x] Al retiro: se eliminan datos médicos y contactos de emergencia
- [x] Al retiro con deuda: datos médicos y contactos de emergencia se eliminan igualmente
- [x] Socio no puede inscribirse si tiene documentos obligatorios pendientes
- [x] Inscripción a salida requiere aceptar el documento de riesgo específico de esa salida
- [x] ADMIN y SECRETARIA pueden crear documentos, activar versiones, ver aceptaciones, ver socios bloqueados (web y mobile)
- [x] Cuando un socio no puede inscribirse, se muestra la lista exacta de requisitos faltantes con botón "Completar ahora" (web y mobile)
- [x] Eliminación de socio requiere escribir exactamente `Si, deseo eliminar al socio NOMBRE APELLIDO`
- [x] Datos médicos no aparecen en listados generales ni en logs
- [x] Se auditan todos los accesos y cambios sobre información sensible

---

## 9. Seguridad y Cumplimiento Legal

- **LOPDP Ecuador (Art. 23)**: datos de salud son categoría especial — consentimiento explícito, específico y granular (dos checkboxes independientes para DATA_PROCESSING_POLICY y MEDICAL_DATA_CONSENT)
- **Cadena de evidencia**: cada aceptación guarda hash SHA-256 del contenido exacto aceptado + IP + user-agent + timestamp del servidor — trazabilidad legal completa
- **Inmutabilidad**: `legal_document_acceptances` y `activity_risk_acceptances` son append-only (nunca UPDATE ni DELETE)
- **Acceso restringido**: datos médicos protegidos por `@PreAuthorize`; el resumen para Jefe de Salida es el mínimo necesario (blood_type, alergias, medicación de emergencia únicamente)
- **Auditoría**: `audit_log` append-only registra todos los accesos y cambios sobre datos sensibles

Ver detalles en:
- `docs/security/architecture/legal-document-security.md`
- `docs/security/architecture/pii-data-retention.md` (actualizado)
