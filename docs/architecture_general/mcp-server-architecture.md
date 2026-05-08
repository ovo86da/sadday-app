# Arquitectura del MCP Server — Sadday App

Este documento describe el diseño técnico del servidor MCP (Model Context Protocol) que permite conectar un LLM (Claude, Cursor) con los datos del club para asistir en la planificación de salidas.

---

## ¿Qué es MCP?

Model Context Protocol es un protocolo abierto de Anthropic que define cómo un LLM se comunica con fuentes de datos externas. El LLM no accede directamente a la base de datos — en cambio, llama **tools** (funciones definidas en el servidor MCP) que hacen las consultas por él y devuelven resultados en texto.

---

## Posición en la arquitectura general

```
┌─────────────────────────────────┐
│   Laptop del usuario            │
│                                 │
│  Claude Desktop / Cursor        │
│       │   MCP (stdio)           │
│       ▼                         │
│  mcp/dist/index.js              │
│  (proceso Node.js local)        │
│       │   HTTPS + X-Api-Key     │
└───────┼─────────────────────────┘
        │
        ▼
┌───────────────────────┐
│  AWS Lightsail        │
│  API REST :443        │
│  Spring Boot          │
│       │               │
│  PostgreSQL           │
└───────────────────────┘
```

El MCP server corre **en la máquina del usuario**, no en el servidor. Lightsail solo recibe peticiones HTTP normales con `X-Api-Key` — no sabe que vienen de un MCP.

---

## Transporte: stdio

A diferencia de un servidor web, el MCP server no escucha en ningún puerto. Claude Desktop lo **lanza como subproceso** al iniciar y se comunica por `stdin`/`stdout` usando JSON-RPC:

```
Claude Desktop
  ├── spawns → node mcp/dist/index.js
  ├── stdin  → { "method": "tools/call", "params": { "name": "buscar_rutas", ... } }
  └── stdout ← { "result": { "content": [{ "type": "text", "text": "..." }] } }
```

Cuando Claude Desktop se cierra, el proceso muere. No hay estado persistente entre sesiones.

---

## Estructura de archivos

```
mcp/
├── package.json          # dependencias: @modelcontextprotocol/sdk, axios, zod
├── tsconfig.json         # compilación TypeScript → dist/
├── src/
│   ├── index.ts          # entry point
│   ├── api-client.ts     # cliente HTTP compartido
│   └── tools/
│       ├── montanas.ts   # tools de montañas
│       ├── rutas.ts      # tools de rutas
│       ├── salidas.ts    # tools de salidas e historial
│       ├── informes.ts   # tools de informes post-salida
│       └── actas.ts      # tools de actas de socios
└── README.md
```

### `index.ts` — entry point

Registra todos los tools y conecta el servidor al transporte stdio:

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { registerMontanasTools } from "./tools/montanas.js";
import { registerRutasTools } from "./tools/rutas.js";
// ...

const server = new McpServer({
  name: "sadday",
  version: "1.0.0",
  description:
    `Sistema de gestión del Club de Montaña Sadday. Contiene información sobre
     montañas y rutas del Ecuador, salidas planificadas y completadas, informes
     post-salida con condiciones reales, costos y recomendaciones, y actas de
     reuniones de socios. Usar para planificar salidas, consultar historial y
     analizar condiciones de rutas específicas.`
});

registerMontanasTools(server);
registerRutasTools(server);
// ...

await server.connect(new StdioServerTransport());
```

### `api-client.ts` — cliente HTTP compartido

Todos los tools usan este cliente. Lee las credenciales de variables de entorno:

```typescript
import axios from "axios";

export const apiClient = axios.create({
  baseURL: process.env.SADDAY_API_URL,
  headers: {
    "X-Api-Key": process.env.SADDAY_API_KEY,
    "Content-Type": "application/json"
  },
  timeout: 10_000
});
```

### `tools/rutas.ts` — ejemplo de implementación

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { apiClient } from "../api-client.js";

export function registerRutasTools(server: McpServer) {
  server.tool(
    "buscar_rutas",

    // Descripción: lo que Claude lee para decidir cuándo usar este tool
    `Busca rutas de montaña filtrando por tipo de actividad, nivel técnico y montaña.
     Usar cuando el usuario quiera saber qué rutas existen, planificar una salida,
     o comparar opciones por dificultad. Devuelve nombre, tipo, nivel técnico
     requerido, distancia y desnivel.`,

    // Esquema de parámetros: describe cada campo para que Claude sepa qué enviar
    {
      tipo: z.enum(["alpinismo", "escalada", "trekking", "ciclismo"])
        .optional()
        .describe("Tipo de actividad. Omitir para todos los tipos."),

      nivel_tecnico_max: z.number().int().min(1).max(5)
        .optional()
        .describe("Nivel técnico máximo del grupo (1=básico … 5=experto). " +
                  "Filtra rutas que el grupo puede hacer según su nivel."),

      montana_id: z.string().uuid()
        .optional()
        .describe("UUID de la montaña. Obtenerlo con listar_montanas " +
                  "si el usuario mencionó la montaña por nombre.")
    },

    // Handler: llama la API y devuelve texto al LLM
    async (params) => {
      const { data } = await apiClient.get("/api/v1/mountains/routes", { params });
      return {
        content: [{ type: "text", text: JSON.stringify(data, null, 2) }]
      };
    }
  );
}
```

---

## El rol de las descripciones

Las descripciones son la parte más importante del MCP. Claude las lee al conectarse y las usa para decidir **cuándo** llamar cada tool, **en qué orden**, y **qué parámetros** enviar. Un tool mal descrito nunca se llama, o se llama con datos incorrectos.

### Descripción del servidor (contexto general)

Le dice a Claude para qué sirve el MCP completo. Se lee una vez al conectarse.

### Descripción del tool (cuándo usarlo)

Debe responder: ¿en qué situación debo llamar este tool? ¿Qué información devuelve?

```
❌ Malo:  "Busca rutas"
✅ Bueno: "Busca rutas de montaña filtrando por tipo de actividad, nivel técnico
           y montaña. Usar cuando el usuario quiera planificar una salida o
           comparar opciones por dificultad."
```

### Descripción de parámetros (qué enviar)

Debe responder: ¿qué valor pongo aquí? ¿De dónde lo obtengo? ¿Qué pasa si no lo pongo?

```
❌ Malo:  montana_id: z.string()
✅ Bueno: montana_id: z.string().uuid()
            .describe("UUID de la montaña. Obtenerlo con listar_montanas
                       si el usuario mencionó la montaña por nombre.")
```

### Cadenas de tools

Las descripciones también guían a Claude a encadenar tools cuando es necesario:

```
Usuario: "¿Qué rutas de trekking hay en el Chimborazo?"

Claude:
  1. llama listar_montanas({ nombre: "Chimborazo" }) → obtiene montana_id
  2. llama buscar_rutas({ tipo: "trekking", montana_id: "..." }) → obtiene rutas
  3. redacta la respuesta
```

Esto funciona porque la descripción de `buscar_rutas` dice explícitamente: *"Obtener el montana_id con listar_montanas si el usuario mencionó la montaña por nombre."*

---

## Flujo completo de una consulta

```
Usuario escribe en Claude:
"¿Cuánto costó en promedio la última salida al Cotopaxi y cómo estuvo el refugio?"
        │
        ▼
Claude analiza los tools disponibles
        │
        ├─ 1. listar_montanas({ nombre: "Cotopaxi" })
        │       stdin → proceso MCP → GET /api/v1/mountains?nombre=Cotopaxi
        │       stdout ← { id: "abc-123", nombre: "Cotopaxi", ... }
        │
        ├─ 2. historial_salidas({ montana_id: "abc-123" })
        │       stdin → proceso MCP → GET /api/v1/salidas?montana_id=abc-123
        │       stdout ← [{ id: "salida-789", fecha: "2025-09-14", ... }]
        │
        └─ 3. obtener_detalle_informe({ informe_id: "inf-456" })
                stdin → proceso MCP → GET /api/v1/informes/inf-456
                stdout ← { costo_total: 180, costo_por_persona: 45,
                            condiciones: "Refugio con calefacción funcionando,
                            buenas condiciones de nieve...", ... }
        │
        ▼
Claude redacta:
"La última salida al Cotopaxi fue el 14 de septiembre de 2025.
 El costo total fue $180 ($45 por persona). Según el informe,
 el refugio estaba en buenas condiciones con calefacción
 funcionando y..."
```

---

## Autenticación

El MCP server se autentica contra la API usando una **API Key** generada por el usuario desde su perfil en la web. La key viaja en el header `X-Api-Key` en cada request.

El backend valida la key en el `ApiKeyAuthFilter`:
- Calcula SHA-256 de la key recibida
- Busca en tabla `api_keys` por hash
- Verifica que no esté revocada ni expirada
- Construye el contexto de seguridad con `SCOPE_readonly` — solo GET permitido, aunque el MCP intentara un POST

Ver detalles en [FR-013](../feature_request/FR-013-mcp-server-planificador-salidas.md) y [secrets-management.md](../security/architecture/secrets-management.md).

---

## Tools disponibles

| Tool | Módulo | Descripción |
|------|--------|-------------|
| `listar_montanas` | montanas | Lista montañas con filtros de región, altitud y tipo de actividad |
| `obtener_detalle_montana` | montanas | Detalles completos de una montaña y sus rutas |
| `buscar_rutas` | rutas | Rutas por montaña, tipo, nivel técnico requerido |
| `obtener_detalle_ruta` | rutas | Dificultad, distancia, desnivel, nivel requerido, contactos |
| `listar_salidas_proximas` | salidas | Salidas planificadas con plazas disponibles |
| `obtener_detalle_salida` | salidas | Ruta, fecha, cupo, estado, dignidades |
| `historial_salidas` | salidas | Salidas completadas por montaña o ruta |
| `buscar_informes` | informes | Informes por ruta, montaña o período |
| `obtener_detalle_informe` | informes | Condiciones, costos, contactos, cumbre, recomendaciones |
| `resumir_informes_ruta` | informes | Agrega: tasa de éxito, costo promedio, incidencias de una ruta |
| `buscar_actas_socios` | actas | Actas de reuniones de socios con búsqueda full-text |
| `obtener_detalle_acta` | actas | Contenido, acuerdos, número de asistentes |

> Las actas de directivos están excluidas a nivel de backend — el filtro fuerza `tipo = REUNION_SOCIOS`.

---

## Clientes compatibles

| Cliente | Transporte | Config |
|---------|-----------|--------|
| Claude Desktop | stdio | `~/.config/claude/claude_desktop_config.json` |
| Claude Code | stdio | `claude mcp add` en terminal |
| Cursor (v0.43+) | stdio | `~/.cursor/mcp.json` o `.cursor/mcp.json` por proyecto |

Todos usan el mismo binario compilado — no hay versiones distintas por cliente.
