# Manejo de PII y retención de datos — Sadday App

**Última actualización:** 2026-06-07
**Audiencia:** Administrador del club, desarrolladores, cualquier persona que deba responder ante socios sobre sus datos personales.

Este documento define qué datos personales almacena Sadday App, quién puede acceder a ellos, cuánto tiempo se conservan, qué ocurre al dar de baja a un socio, y cuáles son los derechos de los titulares de los datos.

**Marco legal de referencia:** Ley Orgánica de Protección de Datos Personales del Ecuador (LOPDP, vigente desde mayo 2023).

---

## 1. Inventario de datos personales (PII)

> **Nota importante sobre datos médicos:** El sistema almacena únicamente datos declarativos de salud para uso en emergencias de montaña. **No se solicitan, no se almacenan y no se manejan certificados médicos de ningún tipo** (certificados de aptitud física, certificados de vacunación, historias clínicas, resultados de exámenes médicos ni ningún documento emitido por un profesional de salud). Los datos de salud que sí se recogen son exclusivamente los descritos en la sección 1.2.

### 1.1 Datos del socio — tabla `socios`

| Campo | Dato | Categoría | Obligatorio |
|-------|------|-----------|-------------|
| `nombre` / `apellido` | Nombre completo | PII básica | Sí |
| `cedula` | Número de cédula | PII sensible (identificador único) | Sí |
| `correo` | Correo electrónico | PII básica | Sí |
| `telefono` | Teléfono | PII básica | No |
| `direccion` | Dirección física | PII básica | No |
| `fecha_nacimiento` | Fecha de nacimiento | PII básica | Sí |
| `fecha_ingreso` | Fecha de ingreso al club | Dato de membresía | Sí |
| `fecha_salida` | Fecha de salida del club | Dato de membresía | No |

### 1.2 Datos de salud — tabla `socio_medical_info` (categoría especial LOPDP Art. 23)

Requieren consentimiento explícito y granular (`MEDICAL_DATA_CONSENT`) antes de ser almacenados. Son datos **declarativos de emergencia** — ingresados por el propio socio, sin validación médica ni documentos adjuntos.

| Campo | Dato | Obligatorio para inscripción |
|-------|------|------------------------------|
| `blood_type` | Tipo de sangre | No |
| `has_relevant_allergies` + `allergies_detail` | Alergias relevantes | No (el flag sí, el detalle solo si aplica) |
| `has_relevant_medical_condition` + `medical_condition_detail` | Condición médica relevante | No (ídem) |
| `uses_emergency_medication` + `emergency_medication_detail` | Medicación de emergencia | No (ídem) |

**Lo que el sistema NO almacena:** certificados médicos, historias clínicas, resultados de exámenes, imágenes médicas, diagnósticos emitidos por profesionales de salud ni ningún documento médico de ningún tipo. El campo `additional_notes` es texto libre limitado, sin adjuntos.

**Base legal:** Art. 22 lit. b LOPDP (interés vital del titular en actividades de riesgo) + consentimiento explícito Art. 23.

### 1.3 Contactos de emergencia — tabla `socio_emergency_contacts`

| Campo | Dato | Categoría |
|-------|------|-----------|
| `nombre_completo` | Nombre del contacto | PII de tercero |
| `relacion` | Parentesco o relación | PII de tercero |
| `celular` | Teléfono | PII de tercero |
| `direccion` | Dirección (opcional) | PII de tercero |

Máximo 2 contactos por socio. Son obligatorios para poder inscribirse a salidas.

### 1.4 Datos de autenticación — tabla `usuarios_auth`

| Campo | Dato | Notas |
|-------|------|-------|
| `username` | Nombre de usuario | Elegido por el socio |
| `password_hash` | Hash de contraseña | Argon2id — nunca recuperable en texto plano |
| `totp_secret` | Secreto TOTP 2FA | Cifrado AES-256-GCM en base de datos |
| `last_login` | Último inicio de sesión | |
| `failed_attempts` | Intentos fallidos | Se limpia al hacer login exitoso |

### 1.5 Datos de seguridad y geolocalización — tabla `security_events`

| Campo | Dato | Finalidad |
|-------|------|-----------|
| `ip_address` | Dirección IP del usuario | Detección de accesos anómalos |
| `country_code` | País (código ISO 2) | Country Challenge — detectar login desde país nuevo |
| `city` | Ciudad | Auditoría de seguridad |
| `user_agent` | User agent del dispositivo | Identificación del dispositivo |
| `device_id` | Hash del dispositivo (32 chars) | Rastreo de dispositivos conocidos |
| `metadata` | JSONB — datos adicionales | Variable por tipo de evento |

**Retención actual:** Indefinida — no existe limpieza automática. Ver §4.

### 1.6 Tokens temporales — limpieza automática

Un job (`SchedulerService.limpiarTokensExpirados()`) corre cada hora y elimina todos los registros expirados o usados.

| Tabla | Dato PII | Retención |
|-------|----------|-----------|
| `email_verification_tokens` | cedula, correo, nombre, apellido | 72 horas o hasta usar |
| `password_reset_tokens` | socio_id (referencia) | 15 minutos o hasta usar |
| `refresh_tokens` | socio_id, device_id, ip | 30 días o hasta revocar |
| `country_challenge_tokens` | ip_address, user_agent | 5 minutos o hasta usar |
| `mfa_challenge_tokens` | socio_id | 5 minutos o hasta usar |

### 1.7 Datos de contactos de ruta — tabla `contactos`

Personas externas al club (encargados de sectores de montaña). Datos almacenados: nombre, teléfono, correo. No son socios — gestionados exclusivamente por el administrador.

---

## 2. Quién puede ver qué

| Dato | SOCIO (propio) | SECRETARIA | DIRECTIVO | ADMIN |
|------|----------------|------------|-----------|-------|
| Sus propios datos de perfil | ✓ Ver y editar | ✓ Ver y editar | ✓ Ver | ✓ Ver y editar |
| Datos de otros socios (básicos) | Lista (nombre, apellido) | ✓ Completo | ✓ Completo | ✓ Completo |
| Cédula de otros socios | ✗ | ✓ | ✓ | ✓ |
| Tipo de sangre de otros socios | ✗ | ✓ | ✓ | ✓ |
| Contactos de emergencia | Solo los propios | ✓ | ✓ | ✓ |
| Dirección de otros socios | ✗ | ✓ | ✓ | ✓ |
| Exportar CSV/PDF completo | ✗ | ✓ | ✓ | ✓ |
| Security events (IP, país) | ✗ | ✓ | ✗ | ✓ |
| Historial de auditoría | ✗ | ✓ | ✗ | ✓ |
| Datos de EX_MEMBER | ✗ | ✓ | ✓ | ✓ |

**Toda exportación queda registrada en la tabla `auditoria`** con: quién exportó, qué campos, filtros aplicados, IP y timestamp.

---

## 3. Finalidades del tratamiento de datos

| Finalidad | Datos usados | Base legal |
|-----------|-------------|------------|
| Gestión de membresía del club | Nombre, cédula, correo, fecha ingreso | Ejecución de relación contractual (Art. 14 LOPDP) |
| Comunicaciones del club (emails) | Correo, nombre | Ejecución de relación contractual |
| Seguridad en salidas de montaña | Tipo de sangre, teléfono, contactos de emergencia | Interés vital del titular (Art. 22 lit. b LOPDP) + Consentimiento |
| Control de acceso a la plataforma | Username, password hash, 2FA | Ejecución de relación contractual |
| Detección de accesos fraudulentos | IP, país, user agent, device_id | Interés legítimo del responsable (Art. 22 lit. f LOPDP) |
| Recordatorios de cumpleaños | Fecha de nacimiento, nombre | Ejecución de relación contractual |
| Estadísticas internas del club | Datos anonimizados o agregados | Interés legítimo |

---

## 4. Política de retención de datos

### Estado actual

| Categoría | Retención actual | Estado |
|-----------|-----------------|--------|
| Datos de socio activo (`socios`) | Mientras es socio + indefinida tras salida | ⚠️ Sin política definida |
| Datos de EX_MEMBER | Indefinida — no se anonimiza al salir | ⚠️ Pendiente |
| Security events (IP, país, ciudad) | **Indefinida** — sin limpieza automática | ⚠️ Pendiente |
| Refresh tokens | 30 días (automático) | ✓ |
| Tokens temporales (MFA, reset, email) | 5 min – 72 horas (automático) | ✓ |
| Registros de auditoría | Indefinida | ⚠️ Sin política |

### Política objetivo

| Categoría | Retención objetivo |
|-----------|-------------------|
| Datos de socio activo | Duración de la membresía activa |
| Datos de EX_MEMBER | 5 años desde `fecha_salida` (obligaciones contables/legales), luego anonimizar |
| Security events | 12 meses desde la fecha del evento |
| Registros de auditoría | 5 años |
| Historial de salidas (participaciones) | Conservar en forma agregada (conteo) — desvincular de PII tras anonimización |

---

## 5. Baja de socio — estado EX_MEMBER

### Comportamiento actual del sistema

Cuando un admin o secretaria cambia el estado a `EX_MEMBER`:

1. `estado_acceso` pasa a `EX_MEMBER` → el socio no puede iniciar sesión
2. Todos los refresh tokens activos se revocan → sesiones activas cerradas inmediatamente
3. El cambio queda registrado en `auditoria`
4. **Todos los datos personales permanecen sin cambios en la BD** — nombre, cédula, correo, tipo de sangre, dirección, contactos de emergencia
5. El socio puede ser reactivado por un admin en cualquier momento (por eso se conservan los datos)

### Política de datos tras la baja

La transición a EX_MEMBER inicia un período de retención de **5 años** sobre los datos del socio. Este plazo cubre posibles obligaciones contables, disputas o reactivaciones solicitadas por el propio ex-socio.

**Durante los 5 años posteriores a `fecha_salida`:**
- Los datos permanecen en la tabla `socios` sin cambios
- El ex-socio no puede iniciar sesión (`EX_MEMBER` bloquea el acceso)
- Los datos son visibles para SECRETARIA, DIRECTIVO y ADMIN
- Los datos pueden incluirse en exportaciones CSV/PDF si se filtra por `estadoId=EX_MEMBER`

**Al cumplirse los 5 años (anonimización):**
- Los campos PII identificables deben reemplazarse con `[ANONIMIZADO]` o `null`
- Campos a anonimizar: `nombre`, `apellido`, `cedula`, `correo`, `telefono`, `direccion`, `tipo_sangre`, todos los campos de contacto de emergencia
- Campos a conservar: `fecha_ingreso`, `fecha_salida`, `tipo_socio_id` — para estadísticas históricas del club
- La anonimización debe quedar registrada en `auditoria`

**Si el ex-socio solicita eliminación anticipada de sus datos:**
El titular tiene derecho de solicitar el borrado antes del plazo de 5 años (Art. 15 LOPDP). En ese caso, aplicar la anonimización inmediatamente. Actualmente este proceso es manual — ver §6.

---

## 6. Derechos de los titulares (LOPDP)

Los socios tienen los siguientes derechos sobre sus datos personales:

| Derecho | Descripción | Cómo ejercerlo actualmente |
|---------|-------------|---------------------------|
| **Acceso** | Conocer qué datos tiene el club sobre él | Ver perfil en la app. Datos completos: contactar al administrador |
| **Rectificación** | Corregir datos inexactos | Editar perfil propio en la app; para campos bloqueados: contactar al administrador |
| **Eliminación / Olvido** | Solicitar borrado de datos | Contactar al administrador — proceso manual |
| **Portabilidad** | Recibir sus datos en formato portable | No implementado — contactar al administrador |
| **Oposición** | Oponerse a cierto tratamiento | Contactar al administrador |
| **Limitación** | Suspender el procesamiento de sus datos temporalmente | Contactar al administrador |

**Canal de contacto:** El administrador del club atiende solicitudes al email registrado en `ADMIN_ALERT_EMAIL`.

---

## 7. Gaps pendientes (feature requests)

Los siguientes gaps requieren cambios en código — se documentan aquí como referencia para los tickets correspondientes, no como parte del alcance actual.

| ID | Gap | Severidad | Estado |
|----|-----|-----------|--------|
| G-01 | Sin mecanismo automatizado de anonimización para EX_MEMBER tras 5 años | Alta | ⚠️ Pendiente |
| G-02 | `security_events` sin limpieza automática — IP/país/ciudad acumulados indefinidamente | Alta | ⚠️ Pendiente |
| G-03 | Sin pantalla de consentimiento en el registro (`CompleteRegistrationScreen`) | Alta | ✅ Resuelto — FR-021 implementa registro multi-paso con consentimientos versionados |
| G-04 | Sin registro del consentimiento en base de datos (`consent_accepted_at`, `consent_version`) | Alta | ✅ Resuelto — tabla `legal_document_acceptances` con trazabilidad completa (hash, IP, user-agent, timestamp) |
| G-05 | Sin endpoint de portabilidad de datos para el titular | Media | ⚠️ Pendiente |
| G-06 | Sin endpoint de eliminación/anonimización por solicitud del titular | Media | ⚠️ Pendiente — `retire` endpoint cubre la eliminación de datos sensibles, pero no la anonimización completa por solicitud del titular |
| G-07 | Registros de auditoría sin política de retención automática | Baja | ⚠️ Pendiente |

### Actualización de inventario (FR-021 — 2026-06-07)

Con la implementación del módulo de gestión documental, el inventario de PII en sección 1.1 fue actualizado:

- Los 6 campos `emergency_contact_*` fueron migrados de `socios` a la tabla `socio_emergency_contacts` (V12 + V16)
- El campo `tipo_sangre` fue migrado de `socios` a `socio_medical_info.blood_type` (V13 + V17)
- La tabla `socio_medical_info` almacena datos de categoría especial (LOPDP Art. 23): alergias, condición médica, medicación de emergencia

Ver el inventario actualizado en `docs/db/esquema_bdd.md` y la documentación de seguridad del módulo en `docs/security/architecture/legal-document-security.md`.

---

## Referencias

- Marco de seguridad general: `docs/security/architecture/security-architecture.md`
- Seguridad mobile: `docs/security/architecture/mobile-security.md`
- Flujo de alta de socios: `docs/flujos/01-alta-socios.md`
- LOPDP Ecuador: Registro Oficial Suplemento 459 de 26 de mayo de 2021
