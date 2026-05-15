import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { apiClient } from "../api-client.js";

export function registerRutasTools(server: McpServer): void {

  server.tool(
    "buscar_rutas",
    `Busca rutas de montaña con múltiples filtros combinables.
     Usar cuando el usuario quiera saber qué rutas existen, planificar una salida o comparar opciones.
     Devuelve id, nombre, tipo, nivel mínimo requerido, distancia, desnivel y duración.
     Ejemplos de uso:
     - "rutas de escalada aprobadas sin permiso" → tipo_actividad=escalada, aprobada=true, requiere_permisos=false
     - "rutas cortas de trekking con track GPS" → tipo_actividad=trekking, longitud_km_max=10, tiene_track=true
     - "rutas de alpinismo de más de 1000 m de desnivel" → tipo_actividad=alpinismo, desnivel_min=1000`,
    {
      montana_id: z.number().int().positive().optional()
        .describe("ID numérico de la montaña. Obtenerlo con listar_montanas si el usuario mencionó la montaña por nombre."),
      tipo_actividad: z.enum(["alpinismo", "escalada", "trekking", "ciclismo"]).optional()
        .describe("Tipo de actividad. Omitir para ver todos los tipos."),
      q: z.string().max(100).optional()
        .describe("Búsqueda por nombre de ruta, sector o lugar de referencia (parcial)."),
      aprobada: z.boolean().optional()
        .describe("true = solo rutas aprobadas para salidas. false = solo pendientes de aprobación. Omitir para ver todas."),
      nivel_minimo_socio_id: z.string().optional()
        .describe("ID del nivel mínimo de socio requerido (ej. 'BASICO', 'MEDIO', 'AVANZADO'). Filtra rutas que exigen exactamente ese nivel."),
      requiere_permisos: z.boolean().optional()
        .describe("true = solo rutas que requieren permisos. false = solo rutas sin permisos. Omitir para ver todas."),
      tiene_track: z.boolean().optional()
        .describe("true = solo rutas con track GPS disponible. false = solo sin track. Omitir para ver todas."),
      longitud_km_min: z.number().min(0).max(9999).optional()
        .describe("Longitud mínima de la ruta en kilómetros (ej. 5 para rutas de al menos 5 km)."),
      longitud_km_max: z.number().min(0).max(9999).optional()
        .describe("Longitud máxima de la ruta en kilómetros (ej. 20 para rutas de hasta 20 km)."),
      desnivel_min: z.number().int().min(0).max(9999).optional()
        .describe("Desnivel positivo mínimo en metros (ej. 500 para rutas de al menos 500 m de desnivel)."),
      desnivel_max: z.number().int().min(0).max(9999).optional()
        .describe("Desnivel positivo máximo en metros (ej. 1500 para rutas de máximo 1500 m de desnivel)."),
      duracion_dias_min: z.number().int().min(1).max(365).optional()
        .describe("Duración mínima en días completos (ej. 2 para expediciones de al menos 2 días)."),
      duracion_dias_max: z.number().int().min(1).max(365).optional()
        .describe("Duración máxima en días completos (ej. 3 para rutas de máximo 3 días)."),
    },
    async ({
      montana_id, tipo_actividad, q, aprobada,
      nivel_minimo_socio_id, requiere_permisos, tiene_track,
      longitud_km_min, longitud_km_max,
      desnivel_min, desnivel_max,
      duracion_dias_min, duracion_dias_max,
    }) => {
      const params: Record<string, string | boolean | number> = {};
      if (montana_id !== undefined)            params.mountainId          = montana_id;
      if (tipo_actividad)                      params.tipoActividad       = tipo_actividad.toUpperCase();
      if (q)                                   params.q                   = q;
      if (aprobada !== undefined)              params.aprobada            = aprobada;
      if (nivel_minimo_socio_id)               params.nivelMinimoSocioId  = nivel_minimo_socio_id;
      if (requiere_permisos !== undefined)     params.requierePermisos    = requiere_permisos;
      if (tiene_track !== undefined)           params.tieneTrack          = tiene_track;
      if (longitud_km_min !== undefined)       params.longitudKmMin       = longitud_km_min;
      if (longitud_km_max !== undefined)       params.longitudKmMax       = longitud_km_max;
      if (desnivel_min !== undefined)          params.desnivelMin         = desnivel_min;
      if (desnivel_max !== undefined)          params.desnivelMax         = desnivel_max;
      if (duracion_dias_min !== undefined)     params.duracionDiasMin     = duracion_dias_min;
      if (duracion_dias_max !== undefined)     params.duracionDiasMax     = duracion_dias_max;

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
