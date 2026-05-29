# Flujo 22 — Gestión de API Keys

## ¿Para qué sirven las API Keys?

Las API Keys permiten que el **asistente de IA (MCP server)** se autentique contra el backend para hacer consultas de solo lectura. Son el mecanismo que usa Claude Desktop u otro cliente MCP para acceder a datos del sistema sin depender de una sesión de usuario interactiva.

Ver [Flujo 14 — Asistente de IA (MCP)](./14-asistente-ia-mcp.md) para el contexto completo del asistente.

---

## ¿Quién puede crear API Keys?

Cualquier socio autenticado puede crear sus propias API Keys desde su perfil en la web. No hay restricción por rol: Admin, Secretaria, Directivo y Socio tienen el mismo acceso a esta función.

> La key que crea un Directivo autentica al MCP con los permisos de ese Directivo. La key que crea un Socio autentica al MCP con los permisos de ese Socio.

---

## Datos de una API Key

| Campo | Descripción |
|-------|-------------|
| Nombre | Texto libre (hasta 100 caracteres) para identificar la key — ej. "MCP Claude Desktop" |
| Key (raw) | Valor opaco `sk-sadday-<base64>` — se muestra **una sola vez** al crear |
| Hash | SHA-256 del raw — lo único que se persiste en la base de datos |
| Fecha de creación | Automática |
| Fecha de expiración | 1 año desde la creación |
| Último uso | Actualizado automáticamente en cada request autenticado |
| Revocada en | Fecha de revocación (si aplica) |

---

## Flujo: crear una API Key

```
Socio → Perfil → API Keys (Asistente MCP) → "Nueva key"
  │
  ├── Ingresa un nombre descriptivo
  └── El sistema genera la key y la muestra una sola vez
```

```mermaid
sequenceDiagram
    actor S as Socio
    participant App as Sistema

    S->>App: POST /v1/profile/api-keys { nombre }
    App->>App: Verifica límite (máx. 5 activas)
    App->>App: Genera 32 bytes aleatorios → "sk-sadday-<base64>"
    App->>App: Guarda SHA-256(raw) en BD; raw nunca se persiste
    App-->>S: Devuelve el raw una sola vez + metadatos
    Note over S: El socio debe copiar la key ahora.<br/>No hay forma de recuperarla después.
```

Si el socio ya tiene 5 keys activas, el sistema responde con error 422 antes de generar nada.

---

## Flujo: usar la API Key (MCP server)

El cliente MCP incluye la key en el header `X-Api-Key` de cada request:

```
GET /v1/salidas
X-Api-Key: sk-sadday-AbCdEfGh...
```

El filtro de seguridad (`ApiKeyAuthFilter`) intercepta el request antes que el filtro JWT y:

1. Verifica que la key exista, no esté revocada y no haya expirado.
2. Bloquea cualquier request con método `POST`, `PUT`, `PATCH` o `DELETE` — responde 403.
3. Construye el contexto de seguridad con el rol del socio propietario + `SCOPE_readonly`.
4. Actualiza `last_used_at` de la key.
5. Pasa el request al resto de la cadena de filtros.

En producción, si el request no llega por HTTPS (`X-Forwarded-Proto: https` ausente o diferente), el filtro rechaza con 400.

---

## Flujo: revocar una API Key

Desde **Perfil → API Keys**, el socio puede revocar cualquiera de sus keys activas. Al revocar:

- Se registra `revoked_at` con la fecha actual (revocación lógica — el registro permanece en BD).
- La key deja de funcionar de inmediato en el siguiente request.
- La acción es irreversible — no hay forma de "desrevocar".

```
DELETE /v1/profile/api-keys/{id}
Authorization: Bearer <jwt_del_socio>
```

Si se intenta revocar una key que pertenece a otro socio, el sistema responde 404 (evita enumerar keys ajenas).

---

## Límites y seguridad

| Aspecto | Detalle |
|---------|---------|
| Máximo de keys activas | 5 por socio |
| Expiración automática | 1 año desde la creación |
| Alcance (scope) | Solo lectura — escrituras bloqueadas a nivel de filtro (403) |
| Formato del raw | `sk-sadday-` + Base64 URL-safe sin padding (32 bytes) |
| Almacenamiento | SHA-256 hex en BD; el raw nunca se guarda |
| Visibilidad del raw | Solo al momento de creación — no recuperable después |
| HTTPS (prod) | Requests sin TLS rechazados con 400 |
| Enumeración | Revocar key ajena devuelve 404, no 403 |
| lastUsedAt | Actualizado en cada request autenticado con la key |

---

## Endpoints

Todos bajo `/v1/profile/api-keys`. Requieren sesión JWT activa (no aceptan autenticación por API Key para evitar recursividad).

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/v1/profile/api-keys` | Crea una nueva key. Devuelve el raw una sola vez. |
| `GET` | `/v1/profile/api-keys` | Lista las keys activas del socio autenticado (sin raw). |
| `DELETE` | `/v1/profile/api-keys/{id}` | Revoca la key indicada. |

---

## Tabla de permisos

| Acción | Admin | Secretaria | Directivo | Socio |
|--------|:-----:|:----------:|:---------:|:-----:|
| Crear API Key | ✅ | ✅ | ✅ | ✅ |
| Ver sus propias keys | ✅ | ✅ | ✅ | ✅ |
| Revocar sus propias keys | ✅ | ✅ | ✅ | ✅ |
| Ver o revocar keys de otro socio | ❌ | ❌ | ❌ | ❌ |

No existe gestión de keys ajenas — cada socio solo opera sobre las suyas.

---

## Web

La sección **"API Keys (Asistente MCP)"** está disponible en la pantalla de Perfil para todos los roles. Muestra:

- Listado de keys activas con nombre, fecha de creación y último uso.
- Botón "Nueva key" (deshabilitado si ya hay 5 activas).
- Botón de revocar por key.

Al crear una key, se muestra un diálogo con el raw y un botón de copia. El diálogo advierte explícitamente que el valor no se puede recuperar después de cerrarlo.

## Mobile

No hay gestión de API Keys en la app móvil. El caso de uso (conectar el asistente MCP desde una herramienta de escritorio) no aplica en contexto mobile.
