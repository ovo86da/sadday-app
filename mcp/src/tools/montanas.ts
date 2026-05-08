import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { apiClient } from "../api-client.js";

export function registerMontanasTools(server: McpServer): void {

  server.tool(
    "listar_montanas",
    `Lista las montañas del club con filtros opcionales.
     Usar cuando el usuario quiera saber qué montañas existen, buscar por nombre o región,
     o necesite el ID de una montaña para usarlo en otras consultas.
     Devuelve id, nombre, altitud, región y tipos de actividad disponibles.`,
    {
      q: z.string().optional()
        .describe("Búsqueda por nombre de montaña (parcial). Ej: 'Cotopaxi', 'Chim'."),
      region: z.string().optional()
        .describe("Filtrar por región geográfica. Ej: 'Sierra Norte', 'Sierra Centro'."),
    },
    async ({ q, region }) => {
      const params: Record<string, string> = {};
      if (q) params.q = q;
      if (region) params.region = region;

      const { data } = await apiClient.get("/api/v1/mountains", { params });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "obtener_detalle_montana",
    `Obtiene los detalles completos de una montaña: altitud, región, descripción y todas sus rutas.
     Usar cuando el usuario quiera información completa de una montaña específica,
     o para ver qué rutas tiene disponibles.
     Requiere el ID numérico — obtenerlo con listar_montanas si solo se tiene el nombre.`,
    {
      montana_id: z.number().int().positive()
        .describe("ID numérico de la montaña. Obtenerlo con listar_montanas si solo se tiene el nombre."),
    },
    async ({ montana_id }) => {
      const { data } = await apiClient.get(`/api/v1/mountains/${montana_id}`);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
