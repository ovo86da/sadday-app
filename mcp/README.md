# Sadday MCP Server

Servidor [Model Context Protocol](https://modelcontextprotocol.io) para el Club de Montaña Sadday.  
Permite a clientes IA (Claude Desktop, Cursor, Windsurf, VS Code + Copilot, etc.) consultar datos del club mediante herramientas de solo lectura.

## Arquitectura

```
┌─────────────────┐    stdio     ┌───────────────┐   HTTP + API Key   ┌──────────────┐
│  Claude Desktop │ ◄──────────► │  sadday-mcp   │ ────────────────►  │  Backend     │
│  Cursor / Code  │              │  (Node.js)    │  X-Api-Key: sk-…   │  :8080       │
└─────────────────┘              └───────────────┘                    └──────────────┘
```

El servidor MCP **no se ejecuta como un frontend** — no tiene UI ni servidor web.  
El cliente IA lo lanza automáticamente como un proceso hijo, se comunica por `stdin/stdout`,
y le expone las herramientas disponibles. El usuario interactúa con el IA de forma natural
y el IA decide cuándo invocar cada herramienta.

## Stack

| Capa | Tecnología |
|------|-----------|
| Runtime | Node.js 20+ |
| Lenguaje | TypeScript 5 |
| SDK | `@modelcontextprotocol/sdk` ^1.0.0 |
| HTTP Client | Axios |
| Validación | Zod |
| Transporte | stdio (estándar MCP) |

---

## Requisitos previos

1. **Node.js 20+** y **npm** instalados
2. **Backend corriendo** en `http://localhost:8080` (o la URL que configures)
3. **API Key** generada desde la aplicación web (Perfil → API Keys → Generar)

---

## Instalación y compilación

```bash
cd mcp/

# Instalar dependencias
npm install

# Compilar TypeScript → JavaScript
npm run build
# Genera dist/index.js (entry point)

# (Opcional) Modo watch para desarrollo
npm run dev
```

---

## Configuración en clientes IA

El servidor requiere dos variables de entorno:

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `SADDAY_API_URL` | URL base del backend | `http://localhost:8080` |
| `SADDAY_API_KEY` | API Key con prefijo `sk-sadday-` | `sk-sadday-abc123...` |

### Claude Desktop

Editar `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS)  
o `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "sadday": {
      "command": "node",
      "args": ["/ruta/completa/a/sadday-app/mcp/dist/index.js"],
      "env": {
        "SADDAY_API_URL": "http://localhost:8080",
        "SADDAY_API_KEY": "sk-sadday-tu-api-key"
      }
    }
  }
}
```

### Cursor

Editar `.cursor/mcp.json` en la raíz del proyecto o en el home:

```json
{
  "mcpServers": {
    "sadday": {
      "command": "node",
      "args": ["./mcp/dist/index.js"],
      "env": {
        "SADDAY_API_URL": "http://localhost:8080",
        "SADDAY_API_KEY": "sk-sadday-tu-api-key"
      }
    }
  }
}
```

### VS Code + GitHub Copilot

Editar `.vscode/mcp.json`:

```json
{
  "servers": {
    "sadday": {
      "type": "stdio",
      "command": "node",
      "args": ["./mcp/dist/index.js"],
      "env": {
        "SADDAY_API_URL": "http://localhost:8080",
        "SADDAY_API_KEY": "sk-sadday-tu-api-key"
      }
    }
  }
}
```

---

## Herramientas disponibles

El servidor expone **12 herramientas de solo lectura** organizadas en 5 módulos:

### 🏔️ Montañas

| Herramienta | Descripción |
|-------------|-------------|
| `listar_montanas` | Lista montañas con filtros por nombre y región |
| `obtener_detalle_montana` | Detalles completos de una montaña y todas sus rutas |

### 🧭 Rutas

| Herramienta | Descripción |
|-------------|-------------|
| `buscar_rutas` | Busca rutas por tipo de actividad, montaña, nombre o estado |
| `obtener_detalle_ruta` | Detalles técnicos: nivel requerido, distancia, desnivel, peligros |

### 🎒 Salidas

| Herramienta | Descripción |
|-------------|-------------|
| `listar_salidas_proximas` | Salidas planificadas en los próximos N días |
| `obtener_detalle_salida` | Detalle completo: ruta, fecha, cupo, inscritos, dignidades |
| `historial_salidas` | Salidas realizadas (completadas) con filtros por ruta, montaña y fecha |

### 📋 Informes

| Herramienta | Descripción |
|-------------|-------------|
| `buscar_informes` | Busca salidas realizadas que tienen informe post-salida |
| `obtener_detalle_informe` | Informe completo: condiciones reales, costos, contactos, recomendaciones |
| `resumir_informes_ruta` | Resumen agregado: tasa de éxito, costo promedio, condiciones frecuentes |

### 📝 Actas

| Herramienta | Descripción |
|-------------|-------------|
| `buscar_actas_socios` | Busca actas de reuniones de socios (excluye directivos por privacidad) |
| `obtener_detalle_acta` | Contenido completo: actividades, acuerdos, puntos varios, asistentes |

---

## Ejemplos de uso

Una vez configurado, el usuario puede hacer preguntas naturales al IA:

> **"¿Cuándo fue la última salida al Cotopaxi?"**  
> → El IA llama a `listar_montanas(q: "Cotopaxi")` → `historial_salidas(montana_id: 14)`

> **"¿Qué rutas de escalada hay en el Rucu Pichincha?"**  
> → `buscar_rutas(montana_id: 34, tipo_actividad: "escalada")`

> **"¿Cuánto cuesta ir al Chimborazo? ¿Qué nivel necesito?"**  
> → `obtener_detalle_ruta(ruta_id: 57)` + `resumir_informes_ruta(montana_id: 13)`

> **"¿Qué se acordó en la última reunión de socios?"**  
> → `buscar_actas_socios()` → `obtener_detalle_acta(acta_id: "...")`

---

## Autenticación

El MCP se autentica con el backend usando **API Keys**:

- Generadas desde la UI web: **Perfil → API Keys → Generar nueva**
- Formato: `sk-sadday-{base64url de 32 bytes}`
- Solo se almacena el hash SHA-256 en BD — el valor raw se muestra **una sola vez** al crearse
- Scope forzado a **solo lectura** (`SCOPE_readonly`)
- Máximo **5 keys activas** por usuario
- Cada request envía la key en el header `X-Api-Key`

---

## Estructura del proyecto

```
mcp/
├── src/
│   ├── index.ts          # Entry point — registra tools y conecta transporte stdio
│   ├── api-client.ts     # Cliente Axios configurado con API Key
│   └── tools/
│       ├── montanas.ts   # listar_montanas, obtener_detalle_montana
│       ├── rutas.ts      # buscar_rutas, obtener_detalle_ruta
│       ├── salidas.ts    # listar_salidas_proximas, obtener_detalle_salida, historial_salidas
│       ├── informes.ts   # buscar_informes, obtener_detalle_informe, resumir_informes_ruta
│       └── actas.ts      # buscar_actas_socios, obtener_detalle_acta
├── dist/                 # Output compilado (generado por `npm run build`)
├── package.json
└── tsconfig.json
```

---

## Desarrollo

```bash
# Watch mode (recompila al guardar)
npm run dev

# Probar manualmente con variables de entorno
SADDAY_API_URL=http://localhost:8080 \
SADDAY_API_KEY=sk-sadday-tu-key \
node dist/index.js
# (Se quedará esperando input por stdin — es comportamiento normal del transporte stdio)
```

Para debug con un cliente MCP real, usar el [MCP Inspector](https://github.com/modelcontextprotocol/inspector):

```bash
npx @modelcontextprotocol/inspector node dist/index.js
```

---

## Notas de seguridad

- **Solo lectura** — el MCP no puede crear, modificar ni eliminar datos
- **Actas de directivos excluidas** — `buscar_actas_socios` filtra `tipo: "SOCIOS"` por política de privacidad
- **Rate limiting** — hereda el rate limit del backend (por API Key)
- **Sin datos personales** — las herramientas no exponen cédulas, correos ni datos sensibles de socios
