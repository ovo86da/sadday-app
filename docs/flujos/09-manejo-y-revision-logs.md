# 09 — Manejo y Revisión de Logs

Este documento explica dónde se almacenan los logs del sistema, en qué formato están, cómo buscar un error específico y cómo interpretar cada tipo de registro. Está dirigido a quien administra o da soporte técnico a la aplicación.

---

## 1. Arquitectura de logs: tres capas

El sistema genera registros en tres lugares distintos, con propósitos complementarios:

| Capa | Dónde | Para qué sirve |
|------|-------|----------------|
| **Archivos de log** | Disco del servidor en `/app/logs/` | Rastrear errores técnicos, excepciones, flujo de ejecución |
| **Tabla `auditoria`** | Base de datos PostgreSQL | Quién hizo qué, cuándo y sobre qué entidad |
| **Tabla `security_events`** | Base de datos PostgreSQL | Eventos de acceso: login, sesiones, dispositivos nuevos, países |

Ante cualquier incidente se debe consultar las tres capas. Los archivos explican el **qué y el por qué técnico**; las tablas de BD explican el **quién y el cuándo de negocio**.

---

## 2. Archivos de log en disco

### 2.1 Ubicación

Los logs se almacenan en el volumen Docker `sadday-backend_sadday-logs`, montado dentro del contenedor en `/app/logs`. Desde el servidor host se accede con:

```bash
# Ver los archivos de log desde el host (fuera del contenedor):
docker volume inspect sadday-backend_sadday-logs   # muestra el Mountpoint real en el host

# Acceder directamente dentro del contenedor:
docker exec -it sadday-api ls /app/logs
```

Estructura dentro de `/app/logs`:

```
/app/logs/
├── sadday-app.log              ← log principal activo (INFO, WARN)
├── sadday-app-error.log        ← solo errores ERROR activo
├── sadday-app.2026-04-27.gz    ← logs de días anteriores (comprimidos)
└── sadday-app-error.2026-04-27.gz
```

### 2.2 Creación inicial del volumen (solo una vez al instalar)

El volumen de logs es externo a Docker Compose — hay que crearlo manualmente en el servidor **antes del primer despliegue**:

```bash
docker volume create sadday-backend_sadday-logs
```

Los archivos de log dentro del volumen **no se crean manualmente**. Logback los genera automáticamente al arrancar la aplicación por primera vez. Solo necesita que el directorio `/app/logs` exista con permisos correctos (el Dockerfile ya lo garantiza).

### 2.3 Política de retención y rotación

| Archivo | Niveles | Rotación | Retención | Tamaño máximo |
|---------|---------|----------|-----------|---------------|
| `sadday-app.log` | INFO, WARN | Diaria (medianoche UTC) | 30 días | 2 GB total |
| `sadday-app-error.log` | ERROR | Diaria (medianoche UTC) | **90 días** | 500 MB total |

La rotación la hace Logback automáticamente: a medianoche renombra el archivo activo añadiendo la fecha, lo comprime en `.gz` y crea un nuevo archivo vacío. También elimina archivos más viejos que el límite configurado o que superen el tamaño total. No se necesita ningún cron ni script externo.

Al usar un volumen Docker nombrado, los logs **sobreviven a los redespliegues**. Al hacer `docker-compose up` con una nueva imagen, el contenedor anterior se elimina pero el volumen (y todos los logs históricos) se mantienen.

### 2.4 Perfiles de entorno

El sistema tiene tres perfiles con comportamiento distinto:

- **`prod`** (producción): logs en JSON estructurado, escritura asíncrona para no bloquear la app.
- **`local`** (desarrollo): logs en consola con colores, nivel DEBUG para `com.sadday`.
- **`test`** (pruebas): solo WARN en consola, sin archivos.

---

## 3. Formato de los logs

### 3.1 Producción (JSON)

Cada línea es un objeto JSON independiente. Ejemplo de una excepción de negocio:

```json
{
  "@timestamp": "2026-04-28T14:32:10.543Z",
  "@version": "1",
  "message": "BusinessException [code=SALIDA_FULL | POST /v1/salidas/42/inscribirse]: Cupo de la salida completo",
  "logger_name": "c.s.a.shared.exception.GlobalExceptionHandler",
  "thread_name": "http-nio-8080-exec-3",
  "level": "WARN",
  "level_value": 30000,
  "requestId": "f3a2c1d0-8b4e-4f2a-9c3d-1e5f7a9b0c2d",
  "socioId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "juan.perez",
  "app": "sadday-app",
  "env": "prod",
  "stack_trace": "..."
}
```

Ejemplo de un error inesperado (500):

```json
{
  "@timestamp": "2026-04-28T14:35:01.112Z",
  "message": "UnhandledException [GET /v1/estadisticas/resumen]",
  "level": "ERROR",
  "requestId": "a1b2c3d4-...",
  "socioId": "550e8400-...",
  "username": "maria.lopez",
  "stack_trace": "java.lang.NullPointerException: Cannot invoke...\n\tat com.sadday...",
  "app": "sadday-app",
  "env": "prod"
}
```

### 3.2 Campos MDC (siempre presentes en peticiones autenticadas)

Estos campos se inyectan automáticamente en cada línea de log dentro del contexto de una petición HTTP:

| Campo | Descripción | Ejemplo |
|-------|-------------|---------|
| `requestId` | ID único de la petición HTTP. También se devuelve en el header `X-Request-ID` de la respuesta. | `"f3a2c1d0-8b4e-..."` |
| `socioId` | UUID del socio autenticado. Solo presente si hay JWT válido. | `"550e8400-e29b-..."` |
| `username` | Nombre de usuario del socio autenticado. Solo presente si hay JWT válido. | `"juan.perez"` |

> **Tip:** Si un usuario reporta un error en el frontend, el navegador recibe el `X-Request-ID` en la respuesta HTTP. Con ese ID se puede encontrar exactamente esa petición en los logs.

### 3.3 Enmascaramiento automático de datos sensibles

Los valores de los campos `password`, `token`, `secret`, `authorization`, `totp`, y `cedula` se reemplazan automáticamente por `***` antes de escribirse al log. Nunca aparecen en texto plano en los archivos.

### 3.4 Formato local (desarrollo)

```
14:32:10.543 [http-nio-8080-exec-3] WARN  c.s.a.s.e.GlobalExceptionHandler [f3a2c1d0-...|juan.perez] - BusinessException [code=SALIDA_FULL | POST /v1/salidas/42/inscribirse]: Cupo de la salida completo
```

Estructura: `HORA [THREAD] NIVEL CLASE [requestId|username] - MENSAJE`

---

## 4. Tipos de mensajes y qué buscar

### 4.1 Error de negocio conocido (`BusinessException`)

Son errores controlados: cupo lleno, token inválido, socio inhabilitado, etc. El sistema los espera y los maneja.

**Nivel:** `WARN`  
**Patrón de búsqueda:** `BusinessException [code=`

```bash
grep '"level":"WARN"' sadday-app.log | grep 'BusinessException'
```

El campo `code` indica el tipo exacto de error. Tabla de códigos frecuentes:

| Código | Significado |
|--------|-------------|
| `SALIDA_FULL` | La salida no tiene más cupo disponible |
| `SALIDA_NOT_PLANIFICADA` | Se intentó inscribir en una salida que no está en estado PLANIFICADA |
| `ALREADY_INSCRIBED` | El socio ya estaba inscrito en esa salida |
| `TOKEN_INVALID` | El enlace de recuperación de contraseña es inválido o ya fue usado |
| `TOKEN_EXPIRED` | El token JWT expiró |
| `SOCIO_NOT_FOUND` | No se encontró el socio solicitado |
| `SOCIO_INHABILITADO` | El socio está inhabilitado (cuotas pendientes u otro motivo) |
| `ACCESO_SISTEMA_BLOQUEADO` | El usuario está bloqueado por demasiados intentos fallidos |
| `ACCESS_DENIED` | El usuario no tiene el rol requerido para esa operación |
| `INSUFICIENT_LEVEL` | El nivel técnico del socio es menor al requerido por la salida |
| `RUTA_NOT_FOUND` | La ruta solicitada no existe |
| `VALIDATION_ERROR` | Los datos enviados no cumplen las validaciones de negocio |
| `INTERNAL_ERROR` | Error interno no específico |

### 4.2 Error de validación de campos (`@Valid`)

Ocurre cuando el cliente envía datos que no cumplen las reglas del formulario (campo vacío, formato incorrecto, etc.).

**Nivel:** `DEBUG`  
**Patrón de búsqueda:** `ValidationError [`

```bash
grep 'ValidationError' sadday-app.log
```

Incluye el método HTTP, el endpoint y los campos que fallaron:

```
ValidationError [POST /v1/socios/registrar]: {nombre=El nombre es requerido, email=Formato de email inválido}
```

### 4.3 Acceso denegado (`@PreAuthorize`)

Se registra cuando un usuario autenticado intenta ejecutar una operación que su rol no permite.

**Nivel:** `INFO`  
**Patrón de búsqueda:** `AccessDenied [`

```bash
grep 'AccessDenied' sadday-app.log
```

```
AccessDenied [DELETE /v1/socios/123]: Access Denied
```

Útil para detectar si alguien está intentando acceder a funciones de administrador.

### 4.4 Error inesperado (500)

Cualquier excepción no controlada: bugs, errores de base de datos, NullPointerException, etc.

**Nivel:** `ERROR`  
**Patrón de búsqueda:** `UnhandledException [`

```bash
grep 'UnhandledException' sadday-app-error.log
```

Este tipo de error siempre incluye el stack trace completo en el campo `stack_trace` (producción) o impreso directamente (local). Investigar estos errores tiene prioridad alta.

### 4.5 Eventos de seguridad (log de archivo)

Eventos críticos de seguridad que también se escriben en log además de la BD:

| Mensaje en log | Nivel | Qué significa |
|----------------|-------|---------------|
| `Posible robo de refresh token — revocando todos los tokens` | WARN | Se detectó reutilización de un token de sesión ya usado |
| `Cuenta bloqueada por N intentos fallidos` | WARN | Un usuario fue bloqueado por credenciales incorrectas repetidas |
| `Nuevo país XX para socio=... sin MFA — login bloqueado` | WARN | Login desde país desconocido sin segundo factor |
| `Nuevo dispositivo detectado para socio=...` | INFO | Login desde un dispositivo nunca antes visto |
| `Rate limit de reset password superado` | WARN | Más de 3 solicitudes de recuperación en 15 minutos |

```bash
# Ver todos los eventos de seguridad relevantes del día:
grep -E '"level":"WARN"' sadday-app.log | grep -E 'token|bloqueado|país|dispositivo|rate limit'
```

---

## 5. Cómo buscar un error específico

### 5.1 Buscar por `requestId` (el método más directo)

Cuando un usuario reporta un error, el frontend recibe el `X-Request-ID` en la respuesta HTTP. Con ese ID se recuperan todos los logs de esa petición:

```bash
# En producción (JSON):
grep '"f3a2c1d0-8b4e-4f2a-9c3d-1e5f7a9b0c2d"' /app/logs/sadday-app.log

# Con jq para verlo formateado:
grep '"f3a2c1d0-8b4e-..."' /app/logs/sadday-app.log | jq .
```

Si el frontend no capturó el ID, se puede obtener de los encabezados de respuesta en las herramientas de desarrollo del navegador (pestaña Red/Network → respuesta → cabecera `X-Request-ID`).

### 5.2 Buscar todos los errores de un usuario

```bash
# Por username:
grep '"username":"juan.perez"' /app/logs/sadday-app.log | grep '"level":"ERROR"\|"level":"WARN"'

# Por socioId (UUID):
grep '"socioId":"550e8400-e29b-41d4-a716-446655440000"' /app/logs/sadday-app.log
```

### 5.3 Ver solo los errores del día de hoy

```bash
# Todos los ERROR del log de errores:
cat /app/logs/sadday-app-error.log | jq 'select(.level == "ERROR")' 

# Con fecha específica:
grep '"2026-04-28"' /app/logs/sadday-app-error.log | jq '{timestamp: .["@timestamp"], message, username, socioId}'
```

### 5.4 Ver errores en archivos comprimidos (días anteriores)

```bash
# Los archivos históricos están comprimidos con gzip:
zgrep '"level":"ERROR"' /app/logs/sadday-app-error.2026-04-27.gz

# Con jq sobre un archivo comprimido:
zcat /app/logs/sadday-app-error.2026-04-27.gz | jq 'select(.level == "ERROR") | {timestamp: .["@timestamp"], message}'
```

### 5.5 Buscar un tipo de BusinessException específico

```bash
# Todos los intentos de inscripción fallidos por cupo:
grep 'SALIDA_FULL' /app/logs/sadday-app.log | jq '{timestamp: .["@timestamp"], username, message}'

# Todos los tokens inválidos de reset de contraseña:
grep 'TOKEN_INVALID' /app/logs/sadday-app.log
```

### 5.6 Ver los últimos N errores en tiempo real

```bash
# Seguir el log de errores en tiempo real:
tail -f /app/logs/sadday-app-error.log | jq '{timestamp: .["@timestamp"], level, message, username}'

# Solo los últimos 50 errores:
tail -n 50 /app/logs/sadday-app-error.log | jq .
```

---

## 6. Tabla `auditoria` en base de datos

Registra **todas las acciones de negocio** realizadas por usuarios: quién creó, modificó o eliminó qué, con snapshot del estado antes y después.

### 6.1 Estructura de la tabla

| Columna | Tipo | Descripción |
|---------|------|-------------|
| `id` | UUID | Identificador único del registro |
| `created_at` | TIMESTAMPTZ | Cuándo ocurrió la acción |
| `actor_username` | VARCHAR | Username del usuario que realizó la acción (o `SYSTEM`) |
| `accion` | VARCHAR | Código de la acción: `CREATE_SOCIO`, `DELETE_SALIDA`, `LOGIN_FAILED`, etc. |
| `entidad_afectada` | VARCHAR | Tabla o entidad involucrada: `socios`, `salida`, `participantes`, etc. |
| `entidad_id` | VARCHAR | ID de la entidad afectada (UUID o numérico según entidad) |
| `datos_anteriores` | JSONB | Snapshot del estado previo (solo para UPDATE/DELETE) |
| `datos_nuevos` | JSONB | Snapshot del estado nuevo (para CREATE/UPDATE) |
| `ip_address` | VARCHAR | IP del cliente |
| `user_agent` | VARCHAR | Navegador/dispositivo del cliente |
| `resultado` | VARCHAR | `SUCCESS`, `FAILED` o `BLOCKED` |
| `detalle` | VARCHAR | Mensaje adicional cuando resultado = `FAILED` |

> **Importante:** Esta tabla es solo de escritura (`append-only`). El usuario de la aplicación no tiene permisos de `UPDATE` ni `DELETE` sobre ella. Los registros no se pueden modificar ni eliminar.

### 6.2 Consultas útiles

```sql
-- ¿Quién modificó un socio específico y cuándo?
SELECT created_at, actor_username, accion, datos_anteriores, datos_nuevos
FROM auditoria
WHERE entidad_afectada = 'socios'
  AND entidad_id = '550e8400-e29b-41d4-a716-446655440000'
ORDER BY created_at DESC;

-- ¿Qué hizo un usuario en las últimas 24 horas?
SELECT created_at, accion, entidad_afectada, entidad_id, resultado
FROM auditoria
WHERE actor_username = 'juan.perez'
  AND created_at >= NOW() - INTERVAL '24 hours'
ORDER BY created_at DESC;

-- ¿Cuántos intentos de login fallido hubo hoy?
SELECT actor_username, COUNT(*) as intentos, MAX(created_at) as ultimo_intento
FROM auditoria
WHERE accion = 'LOGIN_FAILED'
  AND created_at >= CURRENT_DATE
GROUP BY actor_username
ORDER BY intentos DESC;

-- Historial completo de cambios en una salida:
SELECT created_at, actor_username, accion, datos_anteriores, datos_nuevos, resultado
FROM auditoria
WHERE entidad_afectada = 'salida'
  AND entidad_id = '42'
ORDER BY created_at;

-- Ver qué cambió en un UPDATE (comparar antes y después):
SELECT
  created_at,
  actor_username,
  datos_anteriores,
  datos_nuevos
FROM auditoria
WHERE entidad_afectada = 'socios'
  AND accion = 'UPDATE_SOCIO'
  AND entidad_id = '550e8400-...'
ORDER BY created_at DESC
LIMIT 5;
```

---

## 7. Tabla `security_events` en base de datos

Registra **eventos relacionados con la seguridad de acceso**: logins exitosos y fallidos, dispositivos nuevos, países nuevos, tokens robados, cambios de contraseña, etc. Con geolocalización de la IP.

### 7.1 Estructura de la tabla

| Columna | Tipo | Descripción |
|---------|------|-------------|
| `id` | UUID | Identificador único |
| `created_at` | TIMESTAMPTZ | Cuándo ocurrió |
| `socio_id` | UUID | Socio involucrado (puede ser null para logins fallidos con usuario desconocido) |
| `username` | VARCHAR | Username involucrado |
| `event_type` | VARCHAR | Tipo de evento (ver tabla abajo) |
| `ip_address` | VARCHAR | IP del cliente |
| `country_code` | VARCHAR | Código de país detectado por geolocalización |
| `city` | VARCHAR | Ciudad detectada |
| `user_agent` | VARCHAR | Navegador/dispositivo |
| `device_id` | VARCHAR | Huella del dispositivo (hash del User-Agent) |
| `session_id` | UUID | ID de la sesión/refresh token asociada |
| `metadata` | JSONB | Datos adicionales según el tipo de evento |

### 7.2 Tipos de evento

| `event_type` | Descripción |
|--------------|-------------|
| `LOGIN_SUCCESS` | Login exitoso |
| `LOGIN_FAILED` | Credenciales incorrectas |
| `LOGIN_BLOCKED` | Login rechazado porque la cuenta está bloqueada |
| `NEW_DEVICE_LOGIN` | Login desde un dispositivo nunca antes visto (envía email de alerta al usuario) |
| `NEW_COUNTRY_LOGIN` | Login desde un país nuevo (con MFA activo) |
| `COUNTRY_CHALLENGE_ISSUED` | Login bloqueado por país nuevo sin MFA — se envió reto de verificación |
| `PASSWORD_CHANGED` | El usuario cambió su contraseña |
| `REFRESH_TOKEN_REUSED` | Se detectó reutilización de un token ya invalidado (posible robo de sesión) |
| `SESSION_REVOKED` | Una sesión específica fue cerrada |
| `SESSION_REVOKED_ALL` | Todas las sesiones del usuario fueron cerradas |
| `SUSPICIOUS_ACTIVITY_REPORTED` | El usuario reportó actividad sospechosa |
| `MFA_ENABLED` | El usuario activó autenticación de dos factores |
| `MFA_DISABLED` | El usuario desactivó autenticación de dos factores |

### 7.3 Consultas útiles

```sql
-- Historial de accesos de un socio:
SELECT created_at, event_type, ip_address, country_code, city, user_agent
FROM security_events
WHERE username = 'juan.perez'
ORDER BY created_at DESC
LIMIT 20;

-- ¿Desde dónde se conectó un usuario en los últimos 30 días?
SELECT DISTINCT country_code, city, ip_address, COUNT(*) as veces
FROM security_events
WHERE username = 'juan.perez'
  AND event_type = 'LOGIN_SUCCESS'
  AND created_at >= NOW() - INTERVAL '30 days'
GROUP BY country_code, city, ip_address
ORDER BY veces DESC;

-- Logins fallidos recientes (posible ataque de fuerza bruta):
SELECT username, ip_address, COUNT(*) as intentos, MAX(created_at) as ultimo
FROM security_events
WHERE event_type = 'LOGIN_FAILED'
  AND created_at >= NOW() - INTERVAL '1 hour'
GROUP BY username, ip_address
HAVING COUNT(*) >= 3
ORDER BY intentos DESC;

-- Tokens robados o reutilizados:
SELECT created_at, username, ip_address, country_code, metadata
FROM security_events
WHERE event_type = 'REFRESH_TOKEN_REUSED'
ORDER BY created_at DESC;

-- Cuentas bloqueadas hoy:
SELECT created_at, username, ip_address, country_code
FROM security_events
WHERE event_type = 'LOGIN_BLOCKED'
  AND created_at >= CURRENT_DATE
ORDER BY created_at DESC;
```

---

## 8. Receta de investigación por tipo de incidente

### Incidente: "Un usuario dice que le salió un error"

1. Pedir al usuario (o al equipo de soporte) el `X-Request-ID` de la respuesta fallida.
2. Buscar en el log principal:
   ```bash
   grep '"<requestId>"' /app/logs/sadday-app.log | jq .
   ```
3. Si es ERROR, el `stack_trace` indica exactamente dónde falló en el código.
4. Si es WARN con `BusinessException`, el campo `code` indica el error de negocio.

---

### Incidente: "Un usuario no puede entrar al sistema"

1. Buscar eventos de login en `security_events`:
   ```sql
   SELECT event_type, created_at, ip_address, country_code, metadata
   FROM security_events
   WHERE username = 'el.usuario'
   ORDER BY created_at DESC LIMIT 10;
   ```
2. Si `event_type = LOGIN_BLOCKED` → la cuenta está bloqueada. Ver cuándo ocurrió y desde dónde.
3. Si `event_type = COUNTRY_CHALLENGE_ISSUED` → entró desde un país nuevo sin 2FA.
4. Buscar en `auditoria` si hubo algún cambio reciente al usuario:
   ```sql
   SELECT * FROM auditoria WHERE entidad_afectada = 'socios' AND actor_username = 'el.usuario' ORDER BY created_at DESC LIMIT 5;
   ```

---

### Incidente: "Alguien modificó datos que no debía"

1. Buscar en `auditoria` por entidad y rango de fechas:
   ```sql
   SELECT created_at, actor_username, accion, datos_anteriores, datos_nuevos, ip_address
   FROM auditoria
   WHERE entidad_afectada = '<tabla>'
     AND entidad_id = '<id>'
   ORDER BY created_at DESC;
   ```
2. Cruzar el `actor_username` con `security_events` para confirmar que era esa persona desde esa IP.
3. Verificar en los archivos de log si hubo `AccessDenied` por ese usuario alrededor de esa hora (intentos previos fallidos).

---

### Incidente: "La app está lenta o hay errores frecuentes"

1. Ver la frecuencia de errores en los últimos minutos:
   ```bash
   tail -f /app/logs/sadday-app-error.log | jq '{ts: .["@timestamp"], msg: .message}'
   ```
2. Si aparece el mismo `message` repetidamente, es un error sistémico.
3. Ver si hay errores de base de datos en el stack trace: buscar `PSQLException`, `HikariPool`, `DataAccessException`.
4. Revisar si el error afecta a un endpoint específico (`"message"` siempre incluye el método y path).

---

### Incidente: "Sospecha de acceso no autorizado a una cuenta"

1. Revisar `security_events` para la cuenta:
   ```sql
   SELECT event_type, created_at, ip_address, country_code, city, device_id
   FROM security_events
   WHERE username = 'el.usuario'
     AND created_at >= NOW() - INTERVAL '7 days'
   ORDER BY created_at;
   ```
2. Buscar `REFRESH_TOKEN_REUSED`, `NEW_COUNTRY_LOGIN`, o `NEW_DEVICE_LOGIN` inesperados.
3. Comparar IPs y países con los que el usuario habitualmente usa.
4. Si se confirma acceso no autorizado: revocar todas las sesiones desde la interfaz de admin, y forzar cambio de contraseña.

---

## 9. Referencia rápida de comandos

```bash
# Ver errores en tiempo real
tail -f /app/logs/sadday-app-error.log | jq '{ts: .["@timestamp"], msg: .message, user: .username}'

# Buscar por requestId
grep '"<ID>"' /app/logs/sadday-app.log | jq .

# Buscar todos los errores de hoy de un usuario
grep '"username":"<usuario>"' /app/logs/sadday-app.log | grep '"level":"ERROR"' | jq .

# Contar errores por tipo en el log de hoy
grep '"level":"ERROR"' /app/logs/sadday-app-error.log | jq -r '.message' | sed 's/\[.*//' | sort | uniq -c | sort -rn

# Ver últimos 20 errores
tail -n 20 /app/logs/sadday-app-error.log | jq '{ts: .["@timestamp"], level, msg: .message, trace: .stack_trace[0:200]}'

# Buscar en archivos históricos comprimidos
zgrep '"username":"juan.perez"' /app/logs/sadday-app.2026-04-27.gz | jq .

# Ver todos los AccessDenied del día
grep 'AccessDenied' /app/logs/sadday-app.log | jq '{ts: .["@timestamp"], username, message}'
```

---

## 10. Notas importantes

- **Los logs nunca contienen contraseñas, tokens ni cédulas.** Son enmascarados automáticamente antes de escribirse.
- **El archivo `sadday-app-error.log` es el primero que revisar** ante cualquier incidente técnico: solo contiene `ERROR` y se guarda por 90 días.
- **La tabla `auditoria` no se puede modificar.** Es el registro de verdad inmutable de qué pasó en el sistema.
- **Los campos MDC (`requestId`, `socioId`, `username`) están en cada línea de log** de una petición autenticada. Cualquiera de los tres permite filtrar exactamente las líneas relevantes.
- **Las peticiones sin autenticación** (login, recuperar contraseña) solo tienen `requestId`, no tienen `socioId` ni `username` en el MDC.
