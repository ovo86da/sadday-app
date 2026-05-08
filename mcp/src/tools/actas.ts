import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { apiClient } from "../api-client.js";

export function registerActasTools(server: McpServer): void {

  server.tool(
    "buscar_actas_socios",
    `Busca actas de reuniones de socios del club con búsqueda de texto completo.
     Solo muestra actas de tipo SOCIOS — las actas de directivos están excluidas por política de privacidad.
     Útil para encontrar acuerdos sobre salidas, calendarios, cuotas o decisiones colectivas.
     Devuelve fecha, número de asistentes y resumen para identificar qué actas revisar en detalle.`,
    {
      q: z.string().optional()
        .describe("Búsqueda de texto en el contenido del acta. Ej: 'Cotopaxi julio calendario salidas'."),
      desde: z.string().optional()
        .describe("Fecha de inicio del rango (YYYY-MM-DD). Ej: '2025-01-01'."),
      hasta: z.string().optional()
        .describe("Fecha de fin del rango (YYYY-MM-DD). Ej: '2025-12-31'."),
    },
    async ({ q, desde, hasta }) => {
      const params: Record<string, string> = { tipo: "SOCIOS" };
      if (q) params.q = q;
      if (desde) params.desde = desde;
      if (hasta) params.hasta = hasta;

      const { data } = await apiClient.get("/api/v1/actas", { params });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "obtener_detalle_acta",
    `Obtiene el contenido completo de un acta de reunión de socios.
     Incluye: actividades realizadas, actividades por realizar, acuerdos tomados,
     puntos varios, observaciones y lista de asistentes.
     Usar después de buscar_actas_socios para leer el contenido completo
     de un acta específica y ver los acuerdos o discusiones sobre salidas.`,
    {
      acta_id: z.string().uuid()
        .describe("UUID del acta. Obtenerlo con buscar_actas_socios."),
    },
    async ({ acta_id }) => {
      const { data } = await apiClient.get(`/api/v1/actas/${acta_id}`);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
