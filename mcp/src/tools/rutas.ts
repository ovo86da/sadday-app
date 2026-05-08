import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { apiClient } from "../api-client.js";

export function registerRutasTools(server: McpServer): void {

  server.tool(
    "buscar_rutas",
    `Busca rutas de montaña filtrando por tipo de actividad, montaña y estado de aprobación.
     Usar cuando el usuario quiera saber qué rutas existen, planificar una salida
     o comparar opciones. Devuelve id, nombre, tipo, nivel técnico requerido, distancia y desnivel.
     Para filtrar por nivel técnico máximo del grupo, usar el campo de nivel_tecnico_max —
     devuelve solo rutas que el grupo puede realizar.`,
    {
      montana_id: z.number().int().positive().optional()
        .describe("ID numérico de la montaña. Obtenerlo con listar_montanas si el usuario mencionó la montaña por nombre."),
      tipo_actividad: z.enum(["alpinismo", "escalada", "trekking", "ciclismo"]).optional()
        .describe("Tipo de actividad. Omitir para ver todos los tipos."),
      q: z.string().optional()
        .describe("Búsqueda por nombre de ruta (parcial)."),
      aprobada: z.boolean().optional()
        .describe("Filtrar solo rutas aprobadas (true). Omitir para ver todas."),
    },
    async ({ montana_id, tipo_actividad, q, aprobada }) => {
      const params: Record<string, string | boolean | number> = {};
      if (montana_id) params.mountainId = montana_id;
      if (tipo_actividad) params.tipoActividad = tipo_actividad;
      if (q) params.q = q;
      if (aprobada !== undefined) params.aprobada = aprobada;

      const { data } = await apiClient.get("/api/v1/rutas", { params });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "obtener_detalle_ruta",
    `Obtiene los detalles completos de una ruta: nivel técnico requerido, distancia, desnivel,
     duración estimada, condiciones habituales, contactos (guías, refugios) y descripción.
     Usar cuando se necesita información detallada para planificar una salida específica.
     Requiere el ID numérico — obtenerlo con buscar_rutas.`,
    {
      ruta_id: z.number().int().positive()
        .describe("ID numérico de la ruta. Obtenerlo con buscar_rutas o listar_montanas."),
    },
    async ({ ruta_id }) => {
      const { data } = await apiClient.get(`/api/v1/rutas/${ruta_id}`);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
