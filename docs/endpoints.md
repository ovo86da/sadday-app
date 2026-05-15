# Endpoints — Sadday App API

**Base URL:** `http://localhost:8080` (local) · `https://app.sadday.com` (prod)  
**Prefijo global:** `/api/v1/`  
**Autenticación:** Bearer token en header `Authorization: Bearer <token>`  
**Total:** 145 endpoints · 15 controladores

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
15. [Resumen por rol](#15-resumen-por-rol)

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

## 15. Resumen por rol

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
