import { useQuery } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"

// ─── Types ────────────────────────────────────────────────────────────────────

import type { TipoActividad } from "@/types/rutas"

export interface RecomendacionResponse {
  // Datos generales de la ruta
  rutaId: number
  rutaNombre: string
  tipoActividad: TipoActividad
  mountainNombre: string | null
  sectorZona: string | null
  requierePermisos: boolean
  trackUrl: string | null

  // Alpinismo
  saddayNivelTecnicoEscala: string | null
  saddayNivelFisicoEscala: string | null
  escalaAlpinaIfasGrado: string | null
  dificultadRocaUiaa: string | null
  dificultadHieloGrado: string | null
  equipoMontanaId: number | null
  equipoMontanaNombre: string | null

  // Escalada
  escaladaDificultadRocaUiaa: string | null
  escaladaTipoEscalada: string | null
  escaladaNumCintas: number | null
  escaladaAlturaViaM: number | null
  escaladaTipoRoca: string | null

  // Trekking
  trekkingDificultadNombre: string | null
  trekkingEsCircular: boolean | null
  trekkingFuentesAgua: boolean | null
  trekkingTipoTerreno: string | null

  // Ciclismo
  ciclismoTipoBicicleta: string | null
  ciclismoDificultadTecnica: string | null
  ciclismoSuperficiePredominante: string | null

  // Estadísticas históricas
  totalSalidasPrevias: number
  datosInsuficientes: boolean
  tasaExitoPct: number | null
  horaSalidaPromedioClub: string | null
  pctAlquiloTransporte: number | null
  costoPromedioTransporte: number | null
  pctContratoGuia: number | null
  costoPromedioGuia: number | null
  costoTotalPromedio: number | null
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useRecomendacion(rutaId: number | null) {
  return useQuery({
    queryKey: ["planificador", "ruta", rutaId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<RecomendacionResponse>>(
        `/v1/planificador/ruta/${rutaId}`,
      )
      return data.data
    },
    enabled: rutaId !== null,
    staleTime: 5 * 60 * 1000,
  })
}
