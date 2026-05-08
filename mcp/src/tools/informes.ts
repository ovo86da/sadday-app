import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { apiClient } from "../api-client.js";

export function registerInformesTools(server: McpServer): void {

  server.tool(
    "buscar_informes",
    `Busca salidas realizadas que tienen informe post-salida, filtrando por ruta, montaña o período.
     Usar para encontrar informes disponibles antes de consultar su detalle.
     Combinar con obtener_detalle_informe para ver condiciones reales, costos y recomendaciones.
     Devuelve lista de salidas completadas con sus IDs para consultar el informe de cada una.`,
    {
      ruta_id: z.number().int().positive().optional()
        .describe("Filtrar por ruta específica. ID numérico — obtenerlo con buscar_rutas."),
      montana_id: z.number().int().positive().optional()
        .describe("Filtrar por montaña (todas sus rutas). ID numérico — obtenerlo con listar_montanas."),
      desde: z.string().optional()
        .describe("Fecha de inicio del rango (YYYY-MM-DD). Ej: '2025-01-01'."),
      hasta: z.string().optional()
        .describe("Fecha de fin del rango (YYYY-MM-DD). Ej: '2025-12-31'."),
    },
    async ({ ruta_id, montana_id, desde, hasta }) => {
      const params: Record<string, string | number> = { estado: "REALIZADA" };
      if (ruta_id) params.rutaId = ruta_id;
      if (desde) params.fechaInicio = desde;
      if (hasta) params.fechaFin = hasta;

      if (montana_id && !ruta_id) {
        // Obtener estadísticas de la montaña junto con salidas realizadas
        const [salidaRes, statsRes] = await Promise.all([
          apiClient.get("/api/v1/salidas", { params }),
          apiClient.get(`/api/v1/estadisticas/mountains/${montana_id}`),
        ]);
        const result = {
          estadisticas: statsRes.data,
          salidas_con_informe: salidaRes.data,
        };
        return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
      }

      const { data } = await apiClient.get("/api/v1/salidas", { params });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "obtener_detalle_informe",
    `Obtiene el informe post-salida completo de una salida realizada.
     Incluye: condiciones reales de la ruta (nieve, refugio, acceso),
     si se logró la cumbre, costo total y por persona, contactos usados
     (guías, transportistas, refugios), notas de seguridad y recomendaciones para futuras salidas.
     Usar después de historial_salidas o buscar_informes para profundizar en una salida específica.`,
    {
      salida_id: z.string().uuid()
        .describe("UUID de la salida realizada. Obtenerlo con historial_salidas o buscar_informes."),
    },
    async ({ salida_id }) => {
      const { data } = await apiClient.get(`/api/v1/informes/${salida_id}`);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "resumir_informes_ruta",
    `Resume los informes históricos de una ruta o montaña: tasa de éxito (cumbre),
     costo promedio por persona, condiciones más frecuentes e incidencias.
     Usar para planificación cuando se necesita una visión agregada del historial
     en lugar de revisar informe por informe.
     Requiere el ID de montaña para obtener estadísticas consolidadas.`,
    {
      montana_id: z.number().int().positive()
        .describe("ID numérico de la montaña. Obtenerlo con listar_montanas."),
    },
    async ({ montana_id }) => {
      const { data } = await apiClient.get(`/api/v1/estadisticas/mountains/${montana_id}`);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
