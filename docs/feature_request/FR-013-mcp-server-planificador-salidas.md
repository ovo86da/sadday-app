# FR-013 — MCP Server: Asistente de planificación de salidas

**Fecha de solicitud:** 2026-05-07
**Fecha de implementación:** —
**Estado:** Pendiente
**Prioridad:** Media
**Área:** Integración / IA / Planificación

---

## Contexto y motivación

El sistema acumula información valiosa para planificar salidas: montañas, rutas con requisitos técnicos, historial de salidas previas, informes post-salida con condiciones reales y costos, y actas de reuniones de socios con decisiones y propuestas. Actualmente esta información solo es consultable navegando manualmente la aplicación web.

**Objetivo:** exponer esta data a un LLM (Claude) mediante un servidor MCP (Model Context Protocol) para que pueda responder preguntas como:
- *"¿Qué rutas de trekking en la Sierra tenemos pendientes que no hayamos hecho en el último año?"*
- *"¿Cuánto costó la última salida al Cotopaxi y cómo estuvo el refugio?"*
- *"Planifica una salida de nivel básico para junio con cupo para 12 personas."*

El MCP server actúa como capa de lectura sobre la API existente. **Nunca escribe datos** — sugiere, el humano ejecuta en la web.

---

## Arquitectura

```
Claude Desktop / Claude Code
         │  MCP protocol (stdio)
         ▼
  MCP Server (Node.js/TypeScript)
    sadday-app/mcp/
         │  HTTP + X-Api-Key header
         ▼
  API REST Spring Boot :8080
         │  JPA
         ▼
     PostgreSQL
```

El MCP server corre como proceso local en la máquina del desarrollador/admin. Se conecta a la API REST usando una **API Key** generada por el propio usuario desde su perfil en la web.

---

## Componentes a construir

### 1. Backend — Gestión de API Keys

#### 1.1 Tabla `api_keys`

```sql
CREATE TABLE api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    socio_id     UUID NOT NULL REFERENCES socios(id) ON DELETE CASCADE,
    nombre       VARCHAR(100) NOT NULL,          -- descripción amigable, ej: "MCP local"
    key_hash     VARCHAR(255) NOT NULL UNIQUE,   -- SHA-256 de la key, nunca el raw
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ,                    -- NULL = sin expiración (no recomendado)
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ                     -- NULL = activa
);
```

**Formato de la key:** `sk-sadday-<32 bytes aleatorios en base64url>` generado con `SecureRandom` — no UUID, que tiene entropía predecible. Ejemplo: `sk-sadday-X7kP2mNqR8vLwA4jHcEuTbFsYd3ZnI6o`.

La key raw solo se muestra una vez al crearla. Después solo existe el hash SHA-256.

**Límite:** máximo 5 keys activas por usuario — evita proliferación de keys olvidadas.

Al intentar crear una sexta key, el backend devuelve:
```json
{
  "success": false,
  "message": "Has alcanzado el límite de 5 API keys activas. Revoca alguna antes de crear una nueva."
}
```
HTTP 422 — nuevo `ErrorCode.API_KEY_LIMIT_REACHED`.

#### 1.2 Endpoints de gestión

| Método | Path | Descripción | Rol mínimo |
|--------|------|-------------|-----------|
| `POST` | `/api/v1/profile/api-keys` | Genera nueva key — devuelve el raw una sola vez | Cualquier autenticado |
| `GET` | `/api/v1/profile/api-keys` | Lista las keys del usuario (sin el raw) | Cualquier autenticado |
| `DELETE` | `/api/v1/profile/api-keys/{id}` | Revoca una key | Dueño de la key |

#### 1.3 `ApiKeyAuthFilter`

Nuevo filtro en la cadena de Spring Security (antes de `JwtAuthFilter`). Intercepta requests con header `X-Api-Key`:

1. Extrae la key del header
2. Calcula SHA-256
3. Busca en `api_keys` por hash — verifica que no esté revocada ni expirada
4. Carga el `socio` asociado y construye el `Authentication` con sus roles, **forzando scope de solo lectura** independientemente del rol del usuario
5. Actualiza `last_used_at` de forma asíncrona (no bloquea el request)
6. Registra en tabla `auditoria` con acción `API_KEY_USED` — igual que los eventos de seguridad existentes

Si no hay header `X-Api-Key`, el filtro no hace nada y pasa al siguiente (JwtAuthFilter). No son excluyentes.

Los endpoints de gestión de keys (`/api/v1/profile/api-keys`) quedan excluidos de la autenticación por API key (solo JWT). El endpoint `DELETE /{id}` devuelve `404` si la key no pertenece al usuario autenticado — evita enumerar keys de otros usuarios.

**Solo HTTPS en producción:** el `ApiKeyAuthFilter` verifica el header `X-Forwarded-Proto` en perfil `prod` y rechaza con 400 si la request llega sin TLS — la key no debe viajar en claro.

---

### 2. Frontend — Sección "API Keys" en perfil

En la página de perfil del usuario, nueva sección:

- Lista de keys activas: nombre, fecha de creación, último uso
- Botón "Nueva API Key" → modal con nombre → muestra el raw una sola vez con botón de copiar y advertencia
- Botón "Revocar" por key individual

---

### 3. MCP Server — `mcp/`

Nuevo directorio en el monorepo:

```
mcp/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts           # entry point — registra tools y arranca el servidor
│   ├── api-client.ts      # cliente Axios con X-Api-Key en header
│   └── tools/
│       ├── montanas.ts
│       ├── rutas.ts
│       ├── salidas.ts
│       ├── informes.ts
│       └── actas.ts
└── README.md
```

**Dependencias:**
- `@modelcontextprotocol/sdk` — SDK oficial de Anthropic
- `axios` — cliente HTTP
- `zod` — validación de parámetros de cada tool

#### Tools expuestos

##### Montañas

| Tool | Descripción | Parámetros |
|------|-------------|------------|
| `listar_montanas` | Lista montañas con filtros opcionales | `region?`, `altitud_min?`, `altitud_max?`, `tipo_actividad?` |
| `obtener_detalle_montana` | Detalles completos de una montaña y sus rutas | `montana_id` |

##### Rutas

| Tool | Descripción | Parámetros |
|------|-------------|------------|
| `buscar_rutas` | Busca rutas por criterios de planificación | `montana_id?`, `tipo?` (alpinismo/escalada/trekking/ciclismo), `nivel_tecnico_max?`, `aprobada?` |
| `obtener_detalle_ruta` | Detalles completos: dificultad, distancia, desnivel, nivel requerido, contactos | `ruta_id` |

##### Salidas

| Tool | Descripción | Parámetros |
|------|-------------|------------|
| `listar_salidas_proximas` | Salidas planificadas con plazas disponibles | `dias_adelante?` (default 90), `con_plazas_disponibles?` |
| `obtener_detalle_salida` | Detalles de una salida: ruta, fecha, cupo, estado, dignidades | `salida_id` |
| `historial_salidas` | Salidas completadas por montaña o ruta | `montana_id?`, `ruta_id?`, `desde?`, `hasta?` |

##### Informes post-salida

| Tool | Descripción | Parámetros |
|------|-------------|------------|
| `buscar_informes` | Busca informes por ruta, montaña o período | `ruta_id?`, `montana_id?`, `desde?`, `hasta?` |
| `obtener_detalle_informe` | Condiciones reales, costos, contactos usados, logro de cumbre, recomendaciones | `informe_id` |
| `resumir_informes_ruta` | Agrega datos de múltiples informes: tasa de éxito, costo promedio, incidencias | `ruta_id` |

##### Actas de reunión de socios

Solo se exponen actas de tipo **Reunión de Socios** — las actas de directivos quedan excluidas porque pueden contener decisiones internas sensibles (finanzas, sanciones, conflictos).

| Tool | Descripción | Parámetros |
|------|-------------|------------|
| `buscar_actas_socios` | Busca actas de reuniones de socios | `desde?`, `hasta?`, `texto?` (búsqueda full-text) |
| `obtener_detalle_acta` | Contenido, acuerdos, número de asistentes | `acta_id` |

Información útil de las actas para planificación:
- Propuestas de salidas discutidas en asamblea
- Acuerdos sobre destinos o calendarios
- Observaciones de seguridad discutidas colectivamente
- Decisiones sobre cuotas o costos de actividades

---

## Configuración del MCP en Claude Desktop / Claude Code

```json
{
  "mcpServers": {
    "sadday": {
      "command": "node",
      "args": ["/ruta/sadday-app/mcp/dist/index.js"],
      "env": {
        "SADDAY_API_URL": "https://app.el-sadday.com",
        "SADDAY_API_KEY": "sk-sadday-xxxxxxxxxxxxxxxx"
      }
    }
  }
}
```

---

## Despliegue

El MCP server **no se despliega en ningún servidor**. Corre en modo `stdio` — Claude Desktop lo lanza como subproceso en la máquina local del usuario y se comunica por stdin/stdout.

```
Laptop del usuario
  └── Claude Desktop / Claude Code
        └── spawns → mcp/dist/index.js  (Node.js local)
                        └── HTTPS → https://app.el-sadday.com/api/v1/...
```

Lightsail solo recibe requests HTTP normales con `X-Api-Key` — no sabe ni le importa que vienen de un MCP.

**Clientes compatibles:** Claude Desktop, Claude Code y Cursor (v0.43+). Todos usan el mismo formato de config, solo cambia la ubicación del archivo:

| Cliente | Archivo de config |
|---------|------------------|
| Claude Desktop | `~/.config/claude/claude_desktop_config.json` |
| Claude Code | configurado via `claude mcp add` en terminal |
| Cursor | `~/.cursor/mcp.json` (global) o `.cursor/mcp.json` (por proyecto) |

**Setup por usuario (una sola vez):**
1. Tener Node.js instalado en la laptop
2. Desde el repo: `cd mcp && npm install && npm run build`
3. Generar una API key desde el perfil en la web
4. Agregar la config al archivo correspondiente al cliente que use, con la URL de producción y la key

**¿Cuándo sí requeriría un servidor?**
Solo si se quisiera usar desde Claude.ai web, que requiere transporte HTTP/SSE. Para el uso previsto (admin y directivos desde sus laptops con Claude Desktop/Code), el modo stdio es suficiente y no añade infraestructura.

---

## Consideraciones de seguridad y privacidad

| Aspecto | Decisión |
|---------|----------|
| **Read-only forzado en backend** | El `ApiKeyAuthFilter` construye el `Authentication` con authority `SCOPE_readonly`. El `SecurityConfig` deniega todo método que no sea `GET`/`HEAD`/`OPTIONS` cuando el contexto de seguridad tiene ese scope — aunque el MCP intente un POST, el backend lo rechaza con 403 |
| **Entropía real** | Keys generadas con `SecureRandom` (32 bytes) — no UUID predecible |
| **API Keys revocables** | El usuario revoca desde la web; también se revocan automáticamente si el socio es deshabilitado |
| **Scope mínimo** | La key no hereda permisos de escritura aunque el usuario sea Admin |
| **Sin PII** | No se expone PII de inscritos en salidas (nombres, contactos, cédulas) |
| **Actas de directivos** | Excluidas a nivel de filtro en el backend — no configurable desde el MCP |
| **Key storage** | Solo hash SHA-256 en BD — el raw nunca persiste ni aparece en logs |
| **Auditoría completa** | Uso de key registrado en tabla `auditoria` (acción `API_KEY_USED`) — no solo `last_used_at` |
| **Anti-enumeración** | DELETE de key ajena devuelve 404, no 403 |
| **Límite de keys** | Máximo 5 keys activas por usuario — error 422 con mensaje claro al exceder |
| **Expiración** | Campo `expires_at` disponible — recomendado 1 año para keys de MCP local |
| **TLS en prod** | El filtro rechaza requests sin HTTPS en perfil `prod` |
| **Secreto en config MCP** | La key va en variable de entorno (`SADDAY_API_KEY`), nunca hardcodeada en el JSON de config ni en el repo |

---

## Archivos a crear / modificar

### Backend

| Archivo | Tipo | Descripción |
|---------|------|-------------|
| `db/migration/V3__api_keys.sql` | Nuevo | Tabla `api_keys` |
| `auth/entity/ApiKey.java` | Nuevo | Entidad JPA |
| `auth/repository/ApiKeyRepository.java` | Nuevo | Repositorio |
| `auth/service/ApiKeyService.java` | Nuevo | Lógica: crear, listar, revocar |
| `auth/controller/ApiKeyController.java` | Nuevo | Endpoints REST |
| `security/ApiKeyAuthFilter.java` | Nuevo | Filtro de autenticación por key |
| `config/SecurityConfig.java` | Modificar | Registrar `ApiKeyAuthFilter` en la cadena |
| `mountains/controller/` | Modificar | Agregar endpoint de resumen para MCP si hace falta |
| `actas/controller/` | Modificar | Filtrar por tipo al exponer — solo `REUNION_SOCIOS` |

### Frontend

| Archivo | Tipo | Descripción |
|---------|------|-------------|
| `src/features/profile/ApiKeysSection.tsx` | Nuevo | Lista y gestión de keys |
| `src/features/profile/CreateApiKeyModal.tsx` | Nuevo | Modal creación con display one-time |
| `src/api/profile.ts` | Modificar | Endpoints de api-keys |

### MCP Server (nuevo)

| Archivo | Descripción |
|---------|-------------|
| `mcp/package.json` | Dependencias: `@modelcontextprotocol/sdk`, `axios`, `zod` |
| `mcp/src/index.ts` | Entry point — registra tools |
| `mcp/src/api-client.ts` | Cliente HTTP con auth |
| `mcp/src/tools/montanas.ts` | Tools de montañas |
| `mcp/src/tools/rutas.ts` | Tools de rutas |
| `mcp/src/tools/salidas.ts` | Tools de salidas |
| `mcp/src/tools/informes.ts` | Tools de informes |
| `mcp/src/tools/actas.ts` | Tools de actas (socios únicamente) |
| `mcp/README.md` | Setup e instrucciones de configuración |

---

## Variables de entorno (MCP server)

| Variable | Descripción |
|----------|-------------|
| `SADDAY_API_URL` | URL base de la API (`http://localhost:8080` en local) |
| `SADDAY_API_KEY` | API Key generada desde el perfil del usuario |

---

## Notas de diseño

- **Descripción de tools es crítica:** el 80% de que el LLM use correctamente cada tool depende de tener descripciones precisas con ejemplos de cuándo usarla. Esto requiere iteración.
- **Sin endpoints nuevos en la API por ahora:** los endpoints existentes de montañas, rutas, salidas, informes y actas son suficientes para una primera versión funcional.
- **Modo stdio vs HTTP:** el servidor corre en modo `stdio` para integrarse con Claude Desktop y Claude Code localmente. No requiere despliegue.
- **Actas de directivos excluidas:** se implementa a nivel del `ApiKeyAuthFilter` o del endpoint de actas con un filtro fijo por tipo — no queda en manos de la config del MCP.
- **Futuro — tools de escritura:** inscribir a un socio en una salida, crear una salida propuesta. Requieren confirmación explícita del usuario y son Fase 2.
