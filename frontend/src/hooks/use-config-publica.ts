import { useQuery } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"

/** Apps sugeridas al descargar un track, si la clave no está configurada. */
const APPS_POR_DEFECTO = "GaiaGPS, CalTopo, Komoot, Wikiloc"

/**
 * Configuración que cualquier socio puede leer.
 *
 * <p>Vive en la base y no en el código para que añadir una app nueva no exija
 * desplegar el frontend ni publicar una versión de la app móvil.
 */
export function useConfigPublica() {
  return useQuery({
    queryKey: ["config", "publicas"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<Record<string, string>>>("/v1/config/publicas")
      return data.data
    },
    staleTime: 1000 * 60 * 60,  // cambia muy poco; una hora de caché sobra
  })
}

/** Lista de apps para el aviso del track, con un valor por defecto si falta la clave. */
export function useAppsRecomendadas(): string {
  const { data } = useConfigPublica()
  return data?.APPS_RECOMENDADAS_GPX?.trim() || APPS_POR_DEFECTO
}
