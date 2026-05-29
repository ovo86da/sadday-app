# Manejo de PII y retención de datos — Sadday App

**Última actualización:** 2026-05-28
**Audiencia:** Administrador del club, desarrolladores, cualquier persona que deba responder ante socios sobre sus datos personales.

Este documento define qué datos personales almacena Sadday App, quién puede acceder a ellos, cuánto tiempo se conservan, qué ocurre al dar de baja a un socio, y cuáles son los derechos de los titulares de los datos.

**Marco legal de referencia:** Ley Orgánica de Protección de Datos Personales del Ecuador (LOPDP, vigente desde mayo 2023).

---

## 1. Inventario de datos personales (PII)

### 1.1 Datos del socio — tabla `socios`

| Campo | Dato | Categoría | Obligatorio |
|-------|------|-----------|-------------|
| `nombre` / `apellido` | Nombre completo | PII básica | Sí |
| `cedula` | Número de cédula | PII sensible (identificador único) | Sí |
| `correo` | Correo electrónico | PII básica | Sí |
| `telefono` | Teléfono | PII básica | No |
| `direccion` | Dirección física | PII básica | No |
| `fecha_nacimiento` | Fecha de nacimiento | PII básica | Sí |
| `tipo_sangre` | Tipo de sangre | **Dato de salud — categoría especial** | No |
| `emergency_contact_name` / `_name2` | Nombre contacto de emergencia | PII de tercero | No |
| `emergency_contact_phone` / `_phone2` | Teléfono contacto de emergencia | PII de tercero | No |
| `emergency_contact_direccion` / `_direccion2` | Dirección contacto de emergencia | PII de tercero | No |
| `fecha_ingreso` | Fecha de ingreso al club | Dato de membresía | Sí |
| `fecha_salida` | Fecha de salida del club | Dato de membresía | No |

**Nota sobre tipo de sangre:** La LOPDP clasifica datos de salud como categoría especial — requieren consentimiento explícito adicional y protección reforzada. Su almacenamiento está justificado por razones de seguridad en actividades de montañismo de alto riesgo (Art. 22 lit. b LOPDP: interés vital del titular).

### 1.2 Datos de autenticación — tabla `usuarios_auth`

| Campo | Dato | Notas |
|-------|------|-------|
| `username` | Nombre de usuario | Elegido por el socio |
| `password_hash` | Hash de contraseña | Argon2id — nunca recuperable en texto plano |
| `totp_secret` | Secreto TOTP 2FA | Cifrado AES-256-GCM en base de datos |
| `last_login` | Último inicio de sesión | |
| `failed_attempts` | Intentos fallidos | Se limpia al hacer login exitoso |

### 1.3 Datos de seguridad y geolocalización — tabla `security_events`

| Campo | Dato | Finalidad |
|-------|------|-----------|
| `ip_address` | Dirección IP del usuario | Detección de accesos anómalos |
| `country_code` | País (código ISO 2) | Country Challenge — detectar login desde país nuevo |
| `city` | Ciudad | Auditoría de seguridad |
| `user_agent` | User agent del dispositivo | Identificación del dispositivo |
| `device_id` | Hash del dispositivo (32 chars) | Rastreo de dispositivos conocidos |
| `metadata` | JSONB — datos adicionales | Variable por tipo de evento |

**Retención actual:** Indefinida (sin política de limpieza automática). Ver §4.

### 1.4 Tokens temporales (limpieza automática cada hora)

| Tabla | Dato PII | Retención |
|-------|----------|-----------|
| `email_verification_tokens` | cedula, correo, nombre, apellido | 72 horas o hasta usar |
| `password_reset_tokens` | socio_id (referencia) | 15 minutos o hasta usar |
| `refresh_tokens` | socio_id, device_id, ip | 30 días o hasta revocar |
| `country_challenge_tokens` | ip_address, user_agent | 5 minutos o hasta usar |
| `mfa_challenge_tokens` | socio_id | 5 minutos o hasta usar |

Un job automático (`SchedulerService.limpiarTokensExpirados()`) corre cada hora y elimina todos los registros expirados o usados.

### 1.5 Datos de contactos de ruta — tabla `contactos`

Personas externas al club (encargados de sectores de montaña). Datos almacenados: nombre, teléfono, correo. No son socios — estos datos se gestionan exclusivamente por el administrador.

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

## 4. Retención de datos

### Política vigente

| Categoría | Retención actual | Estado |
|-----------|-----------------|--------|
| Datos de socio activo (`socios`) | Mientras es socio + indefinida tras salida | ⚠️ Sin política definida |
| Datos de EX_MEMBER | Indefinida — no se anonimiza al salir | ⚠️ Gap |
| Security events (IP, país, ciudad) | **Indefinida** — sin limpieza automática | ⚠️ Gap crítico |
| Refresh tokens | 30 días (automático) | ✓ |
| Tokens temporales (MFA, reset, email) | 5 min – 72 horas (automático) | ✓ |
| Registros de auditoría | Indefinida | ⚠️ Sin política |

### Política recomendada (a implementar)

| Categoría | Retención recomendada | Acción requerida |
|-----------|----------------------|-----------------|
| Datos de socio activo | Mientras dure la membresía activa | Ninguna mientras está activo |
| Datos de EX_MEMBER | 5 años después de `fecha_salida` (obligaciones contables/legales), luego anonimizar | Implementar job de anonimización |
| Security events | 12 meses desde la fecha del evento | Implementar job de limpieza mensual |
| Registros de auditoría | 5 años (recomendado para trazabilidad) | Implementar limpieza automática |
| Historial de salidas con socio | Conservar agregado (número de salidas) — desvincular de PII | Anonimización parcial al salir |

---

## 5. Qué ocurre al dar de baja a un socio (estado EX_MEMBER)

### Comportamiento actual

Cuando un admin/secretaria ejecuta el cambio de estado a `EX_MEMBER`:

1. `estado_acceso` se cambia a `EX_MEMBER` → el socio no puede iniciar sesión
2. Todos los refresh tokens activos se revocan → sesiones activas cerradas inmediatamente
3. El cambio queda registrado en `auditoria`
4. **Todos los datos personales permanecen sin cambios en la BD** — nombre, cédula, correo, tipo de sangre, dirección, contactos de emergencia, etc.
5. El socio puede ser reactivado por un admin en cualquier momento

### Qué debería ocurrir (gaps a implementar)

#### Gap 1 — Sin mecanismo de borrado/anonimización
No existe endpoint ni proceso para eliminar o anonimizar los datos de un EX_MEMBER. Si un ex-socio solicita la eliminación de sus datos (derecho reconocido por la LOPDP), no hay forma de atenderlo sin intervención manual en la BD.

**Implementación recomendada:**
- Endpoint `POST /api/v1/admin/socios/{id}/anonimizar` (solo ADMIN)
- Acción: reemplazar campos PII con valores neutros (`[ANONIMIZADO]`, null) manteniendo el registro de membresía para estadísticas
- Campos a anonimizar: nombre, apellido, cedula, correo, telefono, direccion, tipo_sangre, todos los campos de contacto de emergencia
- Campos a conservar: fecha_ingreso, fecha_salida, tipo_socio (para estadísticas históricas del club)
- Registro de la anonimización en `auditoria`

#### Gap 2 — Security events sin limpieza
Los eventos de seguridad (IP, país, ciudad, user agent) se acumulan indefinidamente. Para un socio que lleva años en el club esto puede representar un historial completo de ubicaciones geográficas de sus logins.

**Implementación recomendada:**
- Job mensual que elimine `security_events` con `created_at < NOW() - INTERVAL '12 months'`
- Alternativamente: conservar solo el conteo por tipo de evento, no los registros individuales, después de 90 días

#### Gap 3 — Sin endpoint de "portabilidad" (derecho LOPDP)
La LOPDP reconoce el derecho a recibir los propios datos en formato portable. Actualmente no existe un endpoint que permita al socio descargar todos sus propios datos.

---

## 6. Derechos de los titulares (LOPDP)

Los socios tienen los siguientes derechos sobre sus datos personales:

| Derecho | Descripción | Cómo ejercerlo actualmente |
|---------|-------------|---------------------------|
| **Acceso** | Conocer qué datos tiene el club sobre él | Ver perfil en la app. Para datos completos: contactar al administrador |
| **Rectificación** | Corregir datos inexactos | Editar perfil propio en la app; para campos bloqueados: contactar administrador |
| **Eliminación / Olvido** | Solicitar borrado de datos al salir del club | Actualmente: contactar al administrador (proceso manual) |
| **Portabilidad** | Recibir sus datos en formato legible/portable | Actualmente: no implementado — contactar al administrador |
| **Oposición** | Oponerse a cierto tratamiento (ej. estadísticas) | Actualmente: contactar al administrador |
| **Limitación** | Solicitar que sus datos no sean procesados temporalmente | Actualmente: contactar al administrador |

**Canal de contacto para ejercer derechos:** El administrador del club responde solicitudes enviadas al email registrado en `ADMIN_ALERT_EMAIL`.

---

## 7. Pantalla de consentimiento en el registro

La pantalla de consentimiento debe mostrarse en `CompleteRegistrationScreen` (Flutter) **antes** de que el socio pueda enviar el formulario. El socio debe aceptar activamente (checkbox, no preseleccionado) antes de crear su cuenta.

### Qué debe incluir la pantalla

#### Bloque 1 — Responsable del tratamiento
```
Responsable: Club Andino Sadday
Contacto: [email del administrador]
```

#### Bloque 2 — Datos que se recopilan
```
Al crear tu cuenta, el club almacena:
• Nombre, apellido y cédula de identidad
• Correo electrónico y teléfono
• Fecha de nacimiento
• Dirección (opcional)
• Tipo de sangre (opcional — solo para emergencias en salidas de montaña)
• Datos de contactos de emergencia (opcionales)
• Registros técnicos de acceso: dirección IP, país de conexión y tipo de dispositivo
```

#### Bloque 3 — Finalidad
```
Tus datos se usan para:
• Gestionar tu membresía en el club
• Enviarte comunicaciones del club
• Garantizar tu seguridad en salidas de montaña (tipo de sangre y contactos de emergencia)
• Proteger tu cuenta de accesos no autorizados
```

#### Bloque 4 — Retención
```
Conservamos tus datos mientras seas socio activo del club.
Al darte de baja, tus datos se conservan por hasta 5 años por
obligaciones legales, tras lo cual son eliminados o anonimizados.
Puedes solicitar la eliminación anticipada de tus datos en cualquier momento.
```

#### Bloque 5 — Tus derechos
```
Tienes derecho a:
• Acceder a tus datos personales
• Corregir información inexacta
• Solicitar la eliminación de tus datos
• Recibir tus datos en formato portable
• Oponerte a ciertos usos de tus datos

Para ejercer estos derechos, contacta al administrador del club.
```

#### Bloque 6 — Consentimiento especial para tipo de sangre
Si el formulario de registro incluye el campo tipo de sangre, agregar un segundo checkbox separado:
```
[ ] Autorizo al club a almacenar mi tipo de sangre para ser compartido
    con servicios de emergencia en caso de accidente durante actividades del club.
    (Este campo es opcional — puedes registrarte sin proporcionarlo.)
```

#### Checkbox obligatorio
```
[_] He leído y acepto el tratamiento de mis datos personales
    según lo descrito arriba.
```

El botón "Crear cuenta" debe estar **deshabilitado** hasta que el checkbox esté marcado.

### Consideraciones de implementación

- El consentimiento debe quedar registrado: guardar `consent_accepted_at TIMESTAMP` y `consent_version VARCHAR` en `usuarios_auth` o en una tabla separada `consentimientos`
- Si en el futuro cambia la política (nueva finalidad, nuevos datos), notificar a socios existentes y solicitar nuevo consentimiento
- Para socios ya registrados antes de implementar esta pantalla: enviar notificación por email informando la política y dando opción de oposición

---

## 8. Gaps priorizados

| ID | Gap | Severidad | Esfuerzo |
|----|-----|-----------|---------|
| G-01 | Sin mecanismo de anonimización para EX_MEMBER | Alta | Media |
| G-02 | Security events sin retención limitada (IP/país acumulados indefinidamente) | Alta | Baja |
| G-03 | Sin registro de consentimiento en base de datos | Alta | Baja |
| G-04 | Sin pantalla de consentimiento en registro | Alta | Media |
| G-05 | Sin endpoint de portabilidad de datos (derecho LOPDP) | Media | Media |
| G-06 | Sin endpoint de eliminación de datos por solicitud del titular | Media | Alta |
| G-07 | Registros de auditoría sin política de retención | Baja | Baja |

---

## Referencias

- Marco de seguridad general: `docs/security/architecture/security-architecture.md`
- Rotación de secretos: `docs/security/architecture/secret-rotation.md`
- Seguridad mobile: `docs/security/architecture/mobile-security.md`
- Flujo de alta de socios: `docs/flujos/01-alta-socios.md`
- LOPDP Ecuador: Registro Oficial Suplemento 459 de 26 de mayo de 2021
