# Endpoints — Sadday App API

**Base URL:** `http://localhost:8080` (local) · `https://app.el-sadday.com` (prod)  
**Prefijo global:** `/api/v1/`  
**Autenticación:** Bearer token en header `Authorization: Bearer <token>`  
**Total:** 186 endpoints · 22 controladores

---

## Índice

1. [Auth](#1-auth)
2. [Registro (invitación)](#2-registro)
3. [Socios](#3-socios)
4. [Montañas](#4-montañas)
5. [Rutas](#5-rutas)
6. [Contactos](#6-contactos)
7. [Salidas](#7-salidas)
8. [Informes](#8-informes)
9. [Actas](#9-actas)
10. [Estadísticas](#10-estadísticas)
11. [Planificador](#11-planificador)
12. [Notificaciones](#12-notificaciones)
13. [Admin](#13-admin)
14. [API Keys (Perfil)](#14-api-keys)
15. [Documentos Legales](#15-documentos-legales)
16. [Contactos de Emergencia](#16-contactos-de-emergencia)
17. [Información Médica](#17-información-médica)
18. [Completitud de Perfil](#18-completitud-de-perfil)
19. [Documentos de Riesgo por Actividad](#19-documentos-de-riesgo-por-actividad)
20. [Retiro de Socio](#20-retiro-de-socio)
21. [Resumen por rol](#21-resumen-por-rol)

---

## Convenciones

| Símbolo | Significado |
|---|---|
| 🔓 | Público — sin autenticación |
| 🔒 | Requiere token válido (cualquier rol) |
| 👤 | Solo Admin |
| 👥 | Admin · Secretaria |
| 🏔 | Admin · Secretaria · Directivo |
| ⛰ | Admin · Directivo |
| 📋 | Solo Secretaria |

---

## 1. Auth

`/api/v1/auth/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/login` | 🔓 | Login con usuario y contraseña. Devuelve `accessToken` + cookie `refreshToken`. Puede incluir challenge de país si la IP es inusual. |
| POST | `/country-challenge/verify` | 🔓 | Verifica el código enviado por email en el flujo de challenge por país desconocido. |
| POST | `/mfa/login` | 🔓 | Segunda fase del login cuando el socio tiene MFA activo. Recibe el código TOTP. |
| POST | `/refresh` | 🔓 | Renueva el access token usando la cookie `refreshToken`. Requiere header `X-Sadday-Client: spa`. |
| POST | `/forgot-password` | 🔓 | Envía email con link de recuperación de contraseña. |
| POST | `/reset-password` | 🔓 | Establece nueva contraseña usando el token del email. |
| POST | `/change-password/verify` | 🔒 | Verifica la contraseña actual antes de cambiarla (paso previo). |
| POST | `/change-password` | 🔒 | Cambia la contraseña del usuario autenticado. |
| POST | `/logout` | 🔒 | Invalida el refresh token actual. |
| POST | `/logout-all` | 🔒 | Invalida todos los refresh tokens del usuario. |
| GET | `/sessions` | 🔒 | Lista las sesiones activas del usuario (refresh tokens vigentes). |
| DELETE | `/sessions/{sessionId}` | 🔒 | Cierra una sesión específica por ID. |
| DELETE | `/sessions/others` | 🔒 | Cierra todas las sesiones excepto la actual. |
| POST | `/report-suspicious` | 🔒 | Reporta actividad sospechosa detectada por el cliente. |
| GET | `/mfa/status` | 🔒 | Consulta si el usuario tiene MFA activo. |
| POST | `/mfa/setup` | 🔒 | Inicia la configuración de MFA (TOTP). Devuelve QR y secret. |
| POST | `/mfa/confirm` | 🔒 | Confirma y activa MFA con el código TOTP. |
| DELETE | `/mfa` | 🔒 | Desactiva MFA (requiere código TOTP para confirmar). |

**Body — `POST /login`**
```json
{ "username": "admin", "password": "Admin123!" }
```

**Body — `POST /mfa/login`**
```json
{ "tempToken": "...", "code": "123456" }
```

**Body — `POST /forgot-password`**
```json
{ "email": "socio@sadday.com" }
```

**Body — `POST /reset-password`**
```json
{ "token": "...", "newPassword": "NuevaClave123!" }
```

**Body — `POST /change-password`**
```json
{ "currentPassword": "actual", "newPassword": "nueva" }
```

**Body — `POST /mfa/confirm` / `DELETE /mfa`**
```json
{ "code": "123456" }
```

---

## 2. Registro

`/api/v1/registro/...`

Flujo de incorporación de socios: la Secretaria crea al socio, el sistema envía un email con un token de invitación, y el socio completa su registro.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/token-info?token=...` | 🔓 | Valida el token de invitación y devuelve los datos pre-cargados del socio. |
| POST | `/complete` | 🔓 | Completa el registro: establece username, contraseña y datos personales faltantes. |

**Body — `POST /complete`**
```json
{
  "token": "...",
  "username": "juan.perez",
  "password": "MiClave123!",
  "telefono": "0991234567"
}
```

---

## 3. Socios

`/api/v1/socios/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/lookups` | 🔒 | Catálogos para formularios (roles, tipos de socio, estados de habilitación, niveles técnicos). |
| GET | `/me` | 🔒 | Perfil del usuario autenticado. |
| PATCH | `/me` | 🔒 | Actualiza datos propios (teléfono, correo, etc.). |
| GET | `/buscar?q=...` | 🔒 | Búsqueda rápida de socios por nombre/apellido (para autocompletar). |
| GET | `/` | 🏔 | Lista todos los socios con filtros opcionales. |
| GET | `/{id}` | 🏔 | Obtiene un socio por ID. |
| POST | `/` | 👥 | Crea un nuevo socio y envía email de invitación. |
| PUT | `/{id}` | 👥 | Actualiza todos los datos de un socio, incluyendo `estadoHabilitacionId`. ADMIN/SECRETARIA pueden asignar cualquier estado; DIRECTIVO solo puede asignar estados no restrictivos (Habilitado, Socio Vitalicio). |
| DELETE | `/{id}` | 👤 | Elimina un socio (solo si no tiene datos asociados). |
| PATCH | `/{id}/habilitar` | 🏔 | Fuerza el estado de habilitación a Habilitado. |
| PATCH | `/{id}/inhabilitar` | 👥 | Fuerza el estado de habilitación a Inhabilitado (estado restrictivo — requiere ADMIN o SECRETARIA). |
| GET | `/{id}/habilitacion-log` | 🏔 | Historial de cambios de estado de habilitación del socio. |
| POST | `/habilitacion/csv` | 🏔 | Cambia el estado de habilitación de socios en masa por CSV. Acepta: `Habilitado`, `Inhabilitado`, `Vitalicio`, `Licencia`, `Re-inscripcion`. Los estados restrictivos (Inhabilitado, Licencia, Re-inscripción) solo los puede aplicar ADMIN o SECRETARIA. |
| POST | `/importar/preview` | 👥 | Preview de importación de socios desde CSV (muestra filas a importar, errores). |
| POST | `/importar/confirmar` | 👥 | Confirma la importación de socios y envía invitaciones por email. |
| PATCH | `/{id}/nivel-tecnico` | 🏔 | Actualiza el nivel técnico del socio. |
| PATCH | `/{id}/jefe-montana` | 👥 | Activa o desactiva el flag Jefe de Montaña (solo para Directivos). |
| PATCH | `/{id}/rol` | 👤 | Cambia el rol del socio. |
| POST | `/{id}/reenviar-invitacion` | 👥 | Reenvía el email de invitación de registro. |
| GET | `/invitaciones` | 👥 | Lista todas las invitaciones pendientes de registro. |
| POST | `/invitaciones/{tokenId}/reenviar` | 👥 | Reenvía una invitación específica por token. |
| DELETE | `/invitaciones/{tokenId}` | 👥 | Cancela una invitación pendiente. |
| POST | `/{socioId}/emergency-reset` | 👥 | Reset de emergencia del TOTP (para socios que perdieron el teléfono). |
| GET | `/{id}/cuotas` | 🏔 | Lista las cuotas registradas del socio. |
| POST | `/{id}/cuotas` | 👥 | Registra el pago de una cuota. |
| DELETE | `/{id}/cuotas/{cuotaId}` | 👥 | Elimina un registro de cuota. |

**Exportar socios** (`/api/v1/socios/exportar/...`):

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/csv` | 🏔 | Descarga la lista de socios filtrada como CSV. |
| GET | `/pdf` | 🏔 | Descarga la lista de socios filtrada como PDF. |
| GET | `/pdf/firmas` | 🏔 | Descarga el PDF de lista de firmas (socios con espacio para firma). |

**Query params — `GET /`**
```
?rolId=1&estadoId=2&tipoId=3&q=juan
```

**Body — `POST /`**
```json
{
  "nombre": "Juan",
  "apellido": "Pérez",
  "cedula": "1723456789",
  "correo": "juan@email.com",
  "telefono": "0991234567",
  "fechaNacimiento": "1990-05-15",
  "fechaIngreso": "2024-01-10",
  "rolId": 4,
  "tipoSocioId": 1,
  "estadoHabilitacionId": 1
}
```

---

## 4. Montañas

`/api/v1/mountains/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/lookups` | 🔒 | Catálogos de escalas (IFAS, UIAA, WI, compromiso, Yosemite, Sadday). |
| GET | `/acceso-por-nivel` | 🔒 | Tabla de umbrales de acceso máximo por nivel de socio. |
| PUT | `/acceso-por-nivel/{nivelSocioId}` | 🏔 | Actualiza los umbrales de acceso para un nivel de socio. |
| GET | `/` | 🔒 | Lista montañas con filtros opcionales. |
| POST | `/` | 🏔 | Registra una nueva montaña. |
| GET | `/{id}` | 🔒 | Obtiene una montaña por ID. |
| PUT | `/{id}` | 🏔 | Actualiza datos de una montaña. |
| DELETE | `/{id}` | 🏔 | Elimina una montaña (solo si no tiene rutas asociadas). |

---

## 5. Rutas

`/api/v1/rutas/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/` | 🔒 | Lista rutas con filtros avanzados. |
| POST | `/` | 🔒 | Propone una nueva ruta (queda pendiente de aprobación). |
| GET | `/{id}` | 🔒 | Obtiene detalle completo de una ruta. |
| PUT | `/{id}` | 🏔 | Actualiza una ruta. |
| PATCH | `/{id}/aprobar` | ⛰ | Aprueba una ruta propuesta. |
| DELETE | `/{id}` | 🏔 | Elimina una ruta. |
| GET | `/equipos` | 🔒 | Lista los tipos de equipo de montaña disponibles. |
| GET | `/{id}/documentos` | 🔒 | Lista los documentos de permiso subidos a la ruta. |
| POST | `/{id}/documentos` | 🏔 | Sube un documento de permiso (PDF/Word/Excel, máx 10 MB) a S3. |
| DELETE | `/{id}/documentos/{docId}` | 🏔 | Elimina un documento de permiso. |
| GET | `/{id}/documentos/{docId}/descargar` | 🔒 | Descarga un documento de permiso desde S3. |
| GET | `/{id}/contactos` | 🔒 | Lista los contactos vinculados a una ruta. |
| POST | `/{id}/contactos` | 🏔 | Vincula un contacto global a una ruta. |
| DELETE | `/{id}/contactos/{contactoRutaId}` | 🏔 | Desvincula un contacto de la ruta. |

**Query params — `GET /`**

Todos los parámetros son opcionales y combinables entre sí.

| Parámetro | Tipo | Restricción | Descripción |
|---|---|---|---|
| `q` | string | máx 100 chars | Búsqueda parcial en nombre, sector y lugar de referencia. |
| `tipoActividad` | enum | — | `ALPINISMO` · `ESCALADA` · `TREKKING` · `CICLISMO` |
| `aprobada` | boolean | — | `true` = aprobadas, `false` = pendientes. |
| `mountainId` | integer | positivo | Filtra por montaña. |
| `nivelMinimoSocioId` | string | — | ID de clasificación de socio (ej. `BASICO`, `MEDIO`, `AVANZADO`). |
| `requierePermisos` | boolean | — | `true` = requiere permisos, `false` = sin permisos. |
| `tieneTrack` | boolean | — | `true` = tiene track GPS, `false` = sin track. |
| `longitudKmMin` | decimal | 0–9999 | Longitud mínima en km. |
| `longitudKmMax` | decimal | 0–9999 | Longitud máxima en km. |
| `desnivelMin` | integer | 0–9999 | Desnivel positivo mínimo en metros. |
| `desnivelMax` | integer | 0–9999 | Desnivel positivo máximo en metros. |
| `duracionDiasMin` | integer | 1–365 | Duración mínima en días. |
| `duracionDiasMax` | integer | 1–365 | Duración máxima en días. |
| `page` / `size` / `sort` | — | — | Paginación estándar Spring (default: `size=20, sort=nombre`). |

Ejemplo:
```
?tipoActividad=TREKKING&aprobada=true&longitudKmMax=15&desnivelMin=500&tieneTrack=true
```

---

## 6. Contactos

`/api/v1/contactos/...`

Contactos globales reutilizables (guías, transporte, refugios) que se vinculan a rutas.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/buscar?q=...` | 🔒 | Búsqueda rápida para autocompletar. |
| GET | `/` | 👥 | Lista todos los contactos. |
| GET | `/{id}` | 🔒 | Obtiene un contacto por ID. |
| POST | `/` | 🔒 | Crea un nuevo contacto global. |
| PUT | `/{id}` | 👥 | Actualiza un contacto. |
| DELETE | `/{id}` | 👤 | Elimina un contacto. |

---

## 7. Salidas

`/api/v1/salidas/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/lookups` | 🔒 | Catálogos: `publicosObjetivo`, `formatosSalida`, dignidades, estados de salida/inscripción. |
| GET | `/solapamiento` | 🏔 | Verifica si hay salidas solapadas en un rango de fechas. |
| GET | `/aprobaciones-pendientes` | 🔒 | Lista inscripciones pendientes de aprobación de riesgo para el jefe. |
| GET | `/alertas-sin-jefe` | 🏔 | Lista salidas próximas sin jefe de salida asignado. |
| GET | `/` | 🔒 | Lista salidas con filtros. |
| POST | `/` | 🏔 | Crea una nueva salida. |
| GET | `/{id}` | 🔒 | Obtiene detalle de una salida con lista de participantes. |
| PUT | `/{id}` | 🏔 | Actualiza datos de una salida. |
| PATCH | `/{id}/estado` | ⛰ | Cambia el estado de la salida (PLANIFICADA → EN_CURSO → REALIZADA). |
| PATCH | `/{id}/cancelar` | 🏔 | Cancela una salida. |
| DELETE | `/{id}` | 🏔 | Elimina una salida (requiere campo `motivo`). |
| PATCH | `/{id}/cerrar-inscripciones` | 🔒 | Abre o cierra las inscripciones de la salida (toggle). |
| POST | `/{id}/inscripciones` | 🔒 | Inscribe a un socio en la salida. |
| DELETE | `/{id}/inscripciones/{participanteId}` | 🔒 | Cancela una inscripción. |
| PATCH | `/{id}/inscripciones/{participanteId}/estado` | 🔒 | Cambia el estado de una inscripción. |
| PATCH | `/{id}/inscripciones/{participanteId}/aprobacion-riesgo` | 🔒 | Aprueba o rechaza a un participante fuera de nivel. |
| DELETE | `/{id}/inscripciones/{participanteId}/aprobacion-riesgo` | 🔒 | Revoca una aprobación de riesgo. |
| PATCH | `/{id}/inscripciones/{participanteId}/jefe` | 🏔 | Designa al jefe de salida. |
| POST | `/{id}/inscripciones/{participanteId}/dignidades` | 🏔 | Asigna una dignidad (guía, escoba, cronista…) a un participante. |
| DELETE | `/{id}/inscripciones/{participanteId}/dignidades/{dignidadId}` | 🏔 | Elimina una dignidad asignada. |

**Body — `DELETE /{id}`**
```json
{ "motivo": "Condiciones climáticas adversas" }
```

**Body — `PATCH /{id}/inscripciones/{pid}/aprobacion-riesgo`**
```json
{ "aprobado": true, "motivo": "Socio con experiencia demostrada en hielo." }
```

---

## 8. Informes

`/api/v1/informes/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/pendientes-jefe` | 🔒 | Lista salidas realizadas sin informe donde el usuario es jefe de salida. |
| GET | `/{salidaId}` | 🔒 | Obtiene el informe de una salida. Retorna `data: null` si no existe aún. |
| POST | `/{salidaId}` | 🔒 | Crea el informe de salida (jefe de salida o staff privilegiado). |
| PUT | `/{salidaId}` | 🔒 | Actualiza el informe (solo antes de validar). |
| PATCH | `/{salidaId}/validar` | ⛰ | Valida el informe. Lo bloquea para edición posterior. |
| POST | `/{salidaId}/reconocimientos` | 🔒 | Agrega un reconocimiento/logro a un participante. |
| DELETE | `/{salidaId}/reconocimientos/{id}` | 🔒 | Elimina un reconocimiento. |
| POST | `/{salidaId}/pdf` | 🏔 | Genera el PDF del informe y lo sube a S3. Solo disponible tras validación. |
| GET | `/{salidaId}/pdf` | 🔒 | Descarga o devuelve URL firmada del PDF del informe. |

---

## 9. Actas

`/api/v1/actas/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/` | 🔒 | Lista actas con búsqueda opcional. |
| POST | `/` | 👥 | Crea una nueva acta de reunión. |
| GET | `/{id}` | 🔒 | Obtiene detalle completo de un acta. |
| PUT | `/{id}` | 👥 | Actualiza un acta. |
| DELETE | `/{id}` | 👥 | Elimina un acta. |
| POST | `/{id}/asistentes` | 👥 | Agrega un asistente al acta. |
| DELETE | `/{id}/asistentes/{asistenteId}` | 👥 | Elimina un asistente del acta. |
| POST | `/{id}/informes` | 👥 | Vincula un informe de salida al acta. |
| DELETE | `/{id}/informes/{linkId}` | 👥 | Desvincula un informe del acta. |
| POST | `/{id}/pdf` | 👥 | Genera el PDF del acta y lo sube a S3. |
| GET | `/{id}/pdf` | 🔒 | Descarga o devuelve URL firmada del PDF del acta. |
| POST | `/importar` | 📋 | Preview de importación de actas desde archivo Markdown. |
| POST | `/importar/confirmar` | 📋 | Confirma y persiste la importación de actas. |

---

## 10. Estadísticas

`/api/v1/estadisticas/...`

Todos de solo lectura, requieren autenticación.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/dashboard` | 🔒 | KPIs generales del dashboard (salidas, socios activos, cumbres). |
| GET | `/club` | 🔒 | Estadísticas globales del club. |
| GET | `/socios/{socioId}` | 🔒 | Historial completo de participación de un socio. |
| GET | `/socios/{socioId}/actividad-total` | 🔒 | Resumen total acumulado (cumbres, km, desnivel). |
| GET | `/mountains/{mountainId}` | 🔒 | Estadísticas de una montaña (ascensos, tasa de éxito, rutas). |
| GET | `/rankings` | 🔒 | Rankings de participación (más activos, más cumbres…). |
| GET | `/reuniones/rankings` | 🔒 | Ranking de asistencia a reuniones. |
| GET | `/ranking-montana-ruta` | 🔒 | Ranking de montañas y rutas más frecuentadas. |
| GET | `/montana-ruta/buscar` | 🔒 | Búsqueda de estadísticas por montaña/ruta con filtros. |
| GET | `/participantes` | 🔒 | Búsqueda avanzada de participantes con filtros. |
| GET | `/periodo/salidas` | 🔒 | Estadísticas de salidas en un período de tiempo. |
| GET | `/periodo/montanas` | 🔒 | Estadísticas de montañas en un período de tiempo. |
| GET | `/periodo/rutas` | 🔒 | Estadísticas de rutas en un período de tiempo. |

**Query params — `GET /participantes`**
```
?mountainId=1&rutaId=5&dignidadId=1&nivelTecnicoId=SO004&q=juan
```

**Query params comunes de período**
```
?fechaDesde=2025-01-01&fechaHasta=2025-12-31
```

---

## 11. Planificador

`/api/v1/planificador/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/ruta/{rutaId}` | 🔒 | Genera recomendación de equipo, socios aptos y advertencias para una ruta. |

---

## 12. Notificaciones

`/api/v1/notificaciones/...`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/cumpleanos` | 🔒 | Lista socios que cumplen años hoy (para banner en la app). |

---

## 13. Admin

`/api/v1/admin/...`  
Protección global a nivel de ruta en Spring Security — requiere rol Admin o Secretaria como mínimo.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/auditoria` | 👥 | Log de auditoría con filtros. |
| GET | `/security-events` | 👥 | Eventos de seguridad (intentos fallidos, bloqueos, IPs sospechosas). |
| GET | `/usuarios-auth` | 👥 | Lista todos los usuarios del sistema con estado de cuenta. |
| GET | `/usuarios-auth/{socioId}` | 👥 | Obtiene el usuario de autenticación de un socio. |
| POST | `/usuarios-auth/{socioId}/desbloquear` | 👤 | Desbloquea una cuenta bloqueada por exceso de intentos fallidos. |
| PATCH | `/usuarios-auth/{socioId}/estado-acceso` | 👥 | Cambia el estado de acceso de un usuario (ACTIVE, SUSPENDED…). |
| POST | `/usuarios-auth/{socioId}/cerrar-sesion` | 👥 | Cierra forzosamente todas las sesiones de un usuario. |
| GET | `/config` | 👥 | Lista todos los parámetros de configuración del sistema. |
| GET | `/config/{clave}` | 👥 | Obtiene un parámetro de configuración por clave. |
| PATCH | `/config/{clave}` | 👥 | Actualiza el valor de un parámetro de configuración (auditado). |
| POST | `/diagnostico/geoip` | 👤 | Fuerza una actualización/diagnóstico de la base de datos GeoIP. |

**Query params — `GET /auditoria`**
```
?actorUsername=admin&accion=CREATE&omitirAccion=LOGIN
&resultado=SUCCESS&entidadAfectada=SOCIO&entidadId=uuid
&fechaDesde=2025-01-01&fechaHasta=2025-12-31
```

---

## 14. API Keys

`/api/v1/profile/api-keys`

Permite a los usuarios gestionar sus propias API keys (para integraciones, MCP, etc.).

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/` | 🔒 | Lista las API keys del usuario autenticado. |
| POST | `/` | 🔒 | Crea una nueva API key. |
| DELETE | `/{id}` | 🔒 | Revoca una API key. |

> Las API keys con scope `readonly` no pueden gestionar otras API keys.

---

## 15. Documentos Legales

`/api/v1/legal-documents/...` · `/api/v1/me/...` · `/api/v1/admin/legal-documents/...`

Gestión de documentos legales versionados (Markdown) y registro de aceptaciones electrónicas con trazabilidad legal (hash, IP, user-agent, timestamp). Ver [Flujo 23](docs/flujos/23-gestion-documental.md).

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/legal-documents/active` | 🔓 | Documentos activos. Query param opcional: `?stage=REGISTRATION\|PROFILE_COMPLETION\|ACTIVITY_ENROLLMENT` |
| GET | `/legal-documents/{code}/active` | 🔓 | Documento activo por código (ej: `MEDICAL_DATA_CONSENT`) |
| GET | `/legal-documents/{id}` | 🔒 | Documento legal por UUID |
| POST | `/legal-documents/{id}/accept` | 🔒 | Registrar aceptación. IP y user-agent capturados en servidor. |
| GET | `/me/legal-acceptances` | 🔒 | Mis aceptaciones de documentos legales |
| GET | `/admin/legal-documents` | 👥 | Listar todos los documentos con todas sus versiones |
| POST | `/admin/legal-documents` | 👥 | Crear documento legal nuevo (versión 1, inactivo) |
| POST | `/admin/legal-documents/{id}/new-version` | 👥 | Publicar nueva versión de un documento (queda inactiva hasta activarla) |
| PATCH | `/admin/legal-documents/{id}/activate` | 👤 | Activar versión (solo ADMIN). Desactiva versiones anteriores automáticamente. |
| GET | `/admin/legal-documents/{id}/acceptances` | 👥 | Aceptaciones de un documento específico |
| GET | `/admin/legal-documents/pending-acceptances` | 👥 | Por cada documento requerido activo: socios que aún no lo han aceptado |
| GET | `/admin/socios/{id}/legal-status` | 👥 | Estado legal de un socio: qué documentos aceptó y cuáles le faltan |
| GET | `/admin/socios/blocked-for-activities` | 👥 | Socios bloqueados para inscripción por documentos pendientes |

**Body — `POST /admin/legal-documents`**
```json
{
  "code": "LIABILITY_WAIVER",
  "title": "Descargo de Responsabilidad",
  "description": "Acepto los riesgos inherentes al montañismo",
  "documentType": "WAIVER",
  "requiredStage": "PROFILE_COMPLETION",
  "content": "# Descargo...\n\nContenido en Markdown.",
  "required": true,
  "requiresReacceptanceOnNewVersion": true
}
```

> **Códigos de documentos activos:** `DATA_PROCESSING_POLICY`, `MEDICAL_DATA_CONSENT`, `DATA_RETENTION_POLICY`, `LIABILITY_WAIVER`

---

## 16. Contactos de Emergencia

`/api/v1/me/emergency-contacts` · `/api/v1/admin/socios/{id}/emergency-contacts`

Máximo 2 contactos por socio. Obligatorios para que `canEnrollActivities = true`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/me/emergency-contacts` | 🔒 | Mis contactos de emergencia |
| PUT | `/me/emergency-contacts` | 🔒 | Crear/actualizar mis contactos (reemplaza los anteriores, máx 2) |
| GET | `/admin/socios/{id}/emergency-contacts` | 👥 | Contactos de emergencia de un socio |
| PUT | `/admin/socios/{id}/emergency-contacts` | 👥 | Crear/actualizar contactos de un socio (reemplaza los anteriores, máx 2) |

**Body — `PUT /me/emergency-contacts`**
```json
{
  "contactos": [
    {
      "orden": 1,
      "nombreCompleto": "María Torres",
      "relacion": "Madre",
      "celular": "0987654321",
      "direccion": "Av. Principal 123"
    },
    {
      "orden": 2,
      "nombreCompleto": "Carlos Torres",
      "relacion": "Padre",
      "celular": "0912345678",
      "direccion": null
    }
  ]
}
```

---

## 17. Información Médica

`/api/v1/me/medical-info` · `/api/v1/admin/socios/{id}/medical-info` · `/api/v1/admin/socios/{id}/medical-summary`

Datos declarativos de salud para emergencias. No se almacenan certificados médicos. Requiere `MEDICAL_DATA_CONSENT` aceptado antes de guardar.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/me/medical-info` | 🔒 | Mi información médica completa |
| PUT | `/me/medical-info` | 🔒 | Crear/actualizar mi información médica |
| GET | `/admin/socios/{id}/medical-info` | 👥 | Información médica completa de un socio |
| PUT | `/admin/socios/{id}/medical-info` | 👥 | Actualizar información médica de un socio |
| GET | `/admin/socios/{id}/medical-summary` | 🏔 | Resumen de emergencia (3 campos: tipo_sangre, alergias, medicación). Solo para Jefe de Salida asignado a la salida del socio, Secretaria y Admin. Acceso auditado en `audit_log`. |

**Body — `PUT /me/medical-info`**
```json
{
  "bloodType": "O+",
  "hasRelevantAllergies": true,
  "allergiesDetail": "Penicilina",
  "hasRelevantMedicalCondition": false,
  "medicalConditionDetail": null,
  "usesEmergencyMedication": false,
  "emergencyMedicationDetail": null,
  "additionalNotes": null
}
```

> Valores válidos para `bloodType`: `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-` o `null`.

---

## 18. Completitud de Perfil

`/api/v1/me/profile-completion-status` · `/api/v1/admin/socios/{id}/profile-completion-status`

Verifica los 7 requisitos para que un socio pueda inscribirse a salidas. Ver [Flujo 25](docs/flujos/25-perfil-completo.md).

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/me/profile-completion-status` | 🔒 | Mi estado de completitud de perfil |
| GET | `/admin/socios/{id}/profile-completion-status` | 👥 | Estado de completitud de un socio |

**Respuesta**
```json
{
  "profileComplete": false,
  "canEnrollActivities": false,
  "missingRequirements": [
    "Debe registrar 2 contactos de emergencia",
    "Debe aceptar el descargo de responsabilidad vigente"
  ],
  "pendingDocuments": [
    { "code": "LIABILITY_WAIVER", "title": "Descargo de Responsabilidad" }
  ]
}
```

---

## 19. Documentos de Riesgo por Actividad

`/api/v1/salidas/{id}/risk-document` · `/api/v1/admin/salidas/{id}/risk-document`

Documento de riesgo específico por salida. El socio debe aceptarlo antes de inscribirse si la salida lo requiere. Ver [Flujo 26](docs/flujos/26-riesgo-por-actividad.md).

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/salidas/{id}/risk-document` | 🔒 | Documento de riesgo activo de una salida (null si no tiene) |
| POST | `/salidas/{id}/risk-document/accept` | 🔒 | Aceptar el documento de riesgo. IP y user-agent capturados en servidor. |
| POST | `/admin/salidas/{id}/risk-document` | ⛰ | Crear documento de riesgo para una salida (ADMIN o DIRECTIVO) |

**Body — `POST /admin/salidas/{id}/risk-document`**
```json
{ "content": "# Riesgos de la ruta\n\nContenido en Markdown..." }
```

---

## 20. Retiro de Socio

`/api/v1/admin/socios/{id}/retire`

Baja formal de un socio: cambia estado a `EX_MEMBER`, elimina datos sensibles (contactos de emergencia, información médica) y revoca sesiones. Requiere texto de confirmación exacto validado en el servidor. Ver [Flujo 27](docs/flujos/27-retiro-de-socio.md).

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/admin/socios/{id}/retire` | 👥 | Dar de baja a un socio con eliminación de datos sensibles |

**Body**
```json
{
  "reason": "Renuncia voluntaria"
}
```

> El frontend también envía `confirmationText` que el usuario debe escribir manualmente (`"Si, deseo eliminar al socio NOMBRE APELLIDO"`), pero ese campo se valida en el frontend antes del envío. El campo `reason` es el que persiste en el `audit_log`.

---

## 21. Resumen por rol

| Recurso | Socio | Directivo | Secretaria | Admin |
|---|---|---|---|---|
| Auth (login, password, MFA, sesiones) | ✅ | ✅ | ✅ | ✅ |
| Ver salidas, rutas, montañas, informes, actas | ✅ | ✅ | ✅ | ✅ |
| Inscribirse en salidas | ✅ | ✅ | ✅ | ✅ |
| Ver estadísticas | ✅ | ✅ | ✅ | ✅ |
| Proponer rutas | ✅ | ✅ | ✅ | ✅ |
| Aprobar rutas | ❌ | ✅ | ❌ | ✅ |
| Crear y editar salidas | ❌ | ✅ | ✅ | ✅ |
| Gestionar socios (crear, editar) | ❌ | Solo nivel técnico y estados no restrictivos | ✅ | ✅ |
| Importar socios CSV | ❌ | ❌ | ✅ | ✅ |
| Exportar socios (CSV / PDF / PDF firmas) | ❌ | ✅ | ✅ | ✅ |
| Asignar Jefe de Montaña | ❌ | ❌ | ✅ | ✅ |
| Crear y editar actas | ❌ | ❌ | ✅ | ✅ |
| Importar actas Markdown | ❌ | ❌ | ✅ | ❌ |
| Generar PDFs | ❌ | ✅ | ✅ | ✅ |
| Validar informes | ❌ | ✅ | ❌ | ✅ |
| Auditoría y security events | ❌ | ❌ | Ver | ✅ |
| Cambiar roles de socios | ❌ | ❌ | ❌ | ✅ |
| Desbloquear cuentas | ❌ | ❌ | ❌ | ✅ |
| Diagnóstico GeoIP | ❌ | ❌ | ❌ | ✅ |
| API Keys propias | ✅ | ✅ | ✅ | ✅ |
| **FR-021 — Gestión Documental** | | | | |
| Ver documentos legales activos (público) | ✅ | ✅ | ✅ | ✅ |
| Aceptar documentos legales | ✅ | ✅ | ✅ | ✅ |
| Ver mis aceptaciones y estado de completitud | ✅ | ✅ | ✅ | ✅ |
| Ver / editar mis contactos de emergencia | ✅ | ✅ | ✅ | ✅ |
| Ver / editar mi información médica | ✅ | ✅ | ✅ | ✅ |
| Aceptar documento de riesgo por salida | ✅ | ✅ | ✅ | ✅ |
| Ver contactos / info médica de otros socios | ❌ | ❌ | ✅ | ✅ |
| Resumen médico de emergencia | ❌ | Jefe de Salida asignado | ✅ | ✅ |
| Editar info médica / contactos de otros socios | ❌ | ❌ | ✅ | ✅ |
| Ver socios bloqueados, estado legal de socios | ❌ | ❌ | ✅ | ✅ |
| Crear documentos legales, publicar versiones | ❌ | ❌ | ✅ | ✅ |
| Activar versión de documento legal | ❌ | ❌ | ❌ | ✅ |
| Crear documento de riesgo de salida | ❌ | ✅ | ❌ | ✅ |
| Dar de baja a un socio (`retire`) | ❌ | ❌ | ✅ | ✅ |
