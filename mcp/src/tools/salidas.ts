import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { apiClient } from "../api-client.js";

export function registerSalidasTools(server: McpServer): void {

  server.tool(
    "listar_salidas_proximas",
    `Lista las salidas planificadas en los próximos días, con estado y plazas disponibles.
     Usar cuando el usuario pregunta por próximas salidas, qué actividades hay programadas,
     o cuándo es la próxima salida a una ruta específica.
     Devuelve fecha, ruta, cupo total, inscriptos y estado.`,
    {
      dias_adelante: z.number().int().min(1).max(365).default(90)
        .describe("Cuántos días hacia adelante buscar (máximo 365). Por defecto: 90."),
      ruta_id: z.number().int().positive().optional()
        .describe("Filtrar por ruta específica. ID numérico — obtenerlo con buscar_rutas."),
      q: z.string().optional()
        .describe("Búsqueda libre por nombre de salida."),
    },
    async ({ dias_adelante, ruta_id, q }) => {
      const fechaInicio = new Date().toISOString().split("T")[0];
      const fechaFin = new Date(Date.now() + dias_adelante * 86_400_000)
        .toISOString().split("T")[0];

      const params: Record<string, string | number> = {
        estado: "PLANIFICADA",
        fechaInicio,
        fechaFin,
      };
      if (ruta_id) params.rutaId = ruta_id;
      if (q) params.q = q;

      const { data } = await apiClient.get("/api/v1/salidas", { params });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "obtener_detalle_salida",
    `Obtiene los detalles completos de una salida: ruta, fecha, cupo, estado,
     inscritos, dignidades (jefe, médico, etc.) y notas.
     Usar para profundizar en una salida encontrada con listar_salidas_proximas
     o historial_salidas.`,
    {
      salida_id: z.string().uuid()
        .describe("UUID de la salida. Obtenerlo con listar_salidas_proximas o historial_salidas."),
    },
    async ({ salida_id }) => {
      const { data } = await apiClient.get(`/api/v1/salidas/${salida_id}`);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  server.tool(
    "historial_salidas",
    `Lista las salidas completadas (realizadas) del club, con filtros opcionales.
     Usar cuando el usuario quiera saber cuándo fue la última vez que fueron a una montaña o ruta,
     ver el historial de actividades, o analizar frecuencia de salidas.
     Devuelve fecha, ruta, cantidad de participantes, si hubo informe, y estado final.`,
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

      // Si se filtra por montaña, obtener sus rutas primero
      if (montana_id && !ruta_id) {
        const mountainData = await apiClient.get(`/api/v1/mountains/${montana_id}`);
        const text = `Estadísticas de salidas a la montaña:\n${JSON.stringify(
          mountainData.data, null, 2
        )}\n\nSalidas realizadas:\n`;
        const { data } = await apiClient.get("/api/v1/salidas", { params });
        return { content: [{ type: "text", text: text + JSON.stringify(data, null, 2) }] };
      }

      const { data } = await apiClient.get("/api/v1/salidas", { params });
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );
}
