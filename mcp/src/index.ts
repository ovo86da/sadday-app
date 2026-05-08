import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

import { registerMontanasTools } from "./tools/montanas.js";
import { registerRutasTools } from "./tools/rutas.js";
import { registerSalidasTools } from "./tools/salidas.js";
import { registerInformesTools } from "./tools/informes.js";
import { registerActasTools } from "./tools/actas.js";

const server = new McpServer({
  name: "sadday",
  version: "1.0.0",
  description: `Sistema de gestión del Club de Montaña Sadday (Ecuador).
Contiene:
- Montañas y rutas del Ecuador con niveles técnicos y detalles de acceso
- Salidas planificadas y completadas del club
- Informes post-salida con condiciones reales, costos y recomendaciones
- Actas de reuniones de socios con acuerdos y propuestas

Útil para: planificar salidas, consultar historial, analizar condiciones de rutas,
revisar costos anteriores y conocer decisiones del club.`,
});

registerMontanasTools(server);
registerRutasTools(server);
registerSalidasTools(server);
registerInformesTools(server);
registerActasTools(server);

await server.connect(new StdioServerTransport());
