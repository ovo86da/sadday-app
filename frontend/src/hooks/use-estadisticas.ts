import { useQuery } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"

// ─── Club stats & rankings types ──────────────────────────────────────────────

export interface NivelTecnicoItem  { nombre: string; total: number; porcentaje: number }
export interface TipoSocioItem     { nombre: string; total: number }
export interface DignidadGlobalItem { nombre: string; asignaciones: number; sociosUnicos: number }

export interface ClubEstadisticasResponse {
  totalSocios: number
  habilitados: number
  inhabilitados: number
  porNivelTecnico: NivelTecnicoItem[]
  porTipoSocio: TipoSocioItem[]
  topDignidades: DignidadGlobalItem[]
}

export interface SocioRankingItem {
  socioId: string
  nombre: string
  apellido: string
  nivelTecnico: string | null
  total: number
}

export interface DignidadRankingItem {
  dignidad: string
  top: SocioRankingItem[]
}

export interface CategoriaEstadisticaItem {
  tipoActividad: string
  totalSalidas: number
  totalParticipantes: number
}

export interface CategoriaDignidadRankingItem {
  tipoActividad: string
  dignidad: string
  top: SocioRankingItem[]
}

export interface ClubRankingsResponse {
  topJefesSalida: SocioRankingItem[]
  topParticipaciones: SocioRankingItem[]
  topPorDignidad: DignidadRankingItem[]
  porCategoria: CategoriaEstadisticaItem[]
  rankingsPorCategoria: CategoriaDignidadRankingItem[]
}

export interface ParticipanteFiltradoItem {
  socioId: string
  nombre: string
  apellido: string
  nivelTecnico: string | null
  totalParticipaciones: number
  vecesJefeSalida: number
  dignidades: string[]
  ultimaParticipacion: string | null
}

export interface ParticipantesFilters {
  tipoActividad?: string
  mountainId?: number
  rutaId?: number
  dignidadId?: number
  nivelTecnicoId?: string
  q?: string
}

// ─── Types ────────────────────────────────────────────────────────────────────

export interface SalidaHistorialItem {
  participanteId: number
  salidaId: string
  salidaNombre: string
  fecha: string
  mountainNombre: string
  mountainAltitud: number
  rutaNombre: string
  estadoInscripcion: string
  estadoSalida: string
  esJefeSalida: boolean
  seRealizo: boolean | null
  horaEncuentroClub: string | null
  dignidades: string[]
  directivoAprobado: boolean
  jefeAprobado: boolean
}

export interface SocioHistorialResponse {
  socioId: string
  nombre: string
  apellido: string
  totalParticipaciones: number
  totalCumbresLogradas: number
  vecesJefeSalida: number
  historial: SalidaHistorialItem[]
}

export interface DashboardEstadisticasResponse {
  totalSalidas: number
  totalRealizadas: number
  totalCanceladas: number
  totalEnCurso: number
  totalPlanificadas: number
  salidasPorMes: Array<{
    anio: number
    mes: number
    total: number
    realizadas: number
    canceladas: number
    enCurso: number
    planificadas: number
  }>
}

export interface MountainEstadisticaResponse {
  mountainId: number
  nombre: string
  region: string
  altitud: number
  totalSalidas: number
  salidasRealizadas: number
  ultimaSalida: string | null
  rutas: Array<{
    rutaId: number
    rutaNombre: string
    totalSalidas: number
  }>
}

// ─── Hooks ────────────────────────────────────────────────────────────────────

export function useDashboardEstadisticas(meses = 12) {
  return useQuery({
    queryKey: ["estadisticas", "dashboard", meses],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<DashboardEstadisticasResponse>>(
        "/v1/estadisticas/dashboard",
        { params: { meses } },
      )
      return data.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useHistorialSocio(socioId: string | undefined) {
  return useQuery({
    queryKey: ["estadisticas", "socio", socioId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<SocioHistorialResponse>>(
        `/v1/estadisticas/socios/${socioId}`,
      )
      return data.data
    },
    enabled: !!socioId,
    staleTime: 5 * 60 * 1000,
  })
}

export function useEstadisticasMountain(mountainId: number | undefined) {
  return useQuery({
    queryKey: ["estadisticas", "mountain", mountainId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<MountainEstadisticaResponse>>(
        `/v1/estadisticas/mountains/${mountainId}`,
      )
      return data.data
    },
    enabled: !!mountainId,
    staleTime: 5 * 60 * 1000,
  })
}

export function useClubEstadisticas() {
  return useQuery({
    queryKey: ["estadisticas", "club"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<ClubEstadisticasResponse>>(
        "/v1/estadisticas/club",
      )
      return data.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useClubRankings(top = 10) {
  return useQuery({
    queryKey: ["estadisticas", "rankings", top],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<ClubRankingsResponse>>(
        "/v1/estadisticas/rankings",
        { params: { top } },
      )
      return data.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

// ─── Reuniones stats types ─────────────────────────────────────────────────────

export interface ReunionAsistenciaMesItem {
  anio: number
  mes: number
  totalReuniones: number
  totalAsistencias: number
  promedioAsistentes: number
}

export interface ReunionesRankingResponse {
  totalReuniones: number
  promedioAsistentesGlobal: number
  topAsistentes: SocioRankingItem[]
  menosAsistentes: SocioRankingItem[]
  asistenciaPorMes: ReunionAsistenciaMesItem[]
}

export interface ActividadTotalSocioResponse {
  socioId: string
  nombre: string
  apellido: string
  totalReunionesAsistidas: number
  totalSalidasParticipadas: number
  totalCumbresLogradas: number
  reunionesAsistidas: Array<{
    actaId: string
    fecha: string
    numeroReunion: number | null
    tipoActa: string
    presidenteNombre: string | null
  }>
}

// ─── Reuniones hooks ──────────────────────────────────────────────────────────

export function useRankingReuniones(top = 10, meses = 12) {
  return useQuery({
    queryKey: ["estadisticas", "reuniones", "rankings", top, meses],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<ReunionesRankingResponse>>(
        "/v1/estadisticas/reuniones/rankings",
        { params: { top, meses } },
      )
      return data.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useActividadTotalSocio(socioId: string | undefined) {
  return useQuery({
    queryKey: ["estadisticas", "actividad-total", socioId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<ActividadTotalSocioResponse>>(
        `/v1/estadisticas/socios/${socioId}/actividad-total`,
      )
      return data.data
    },
    enabled: !!socioId,
    staleTime: 5 * 60 * 1000,
  })
}

// ─── Ranking Montaña & Rutas types ───────────────────────────────────────────

export interface MontanaRankingItem {
  mountainId: number
  nombre: string
  region: string
  altitud: number
  totalSalidas: number
}

export interface RutaRankingItem {
  rutaId: number
  nombre: string
  tipoActividad: string
  mountainNombre: string | null
  totalSalidas: number
  totalParticipantes: number
}

export interface RankingMontanaRutaResponse {
  topMontanasMasSalidas: MontanaRankingItem[]
  topMontanasMenosSalidas: MontanaRankingItem[]
  topRutasMasSalidas: RutaRankingItem[]
  topRutasMenosSalidas: RutaRankingItem[]
  topRutasMasParticipantes: RutaRankingItem[]
}

export interface MontanaRutaBusquedaItem {
  id: number
  nombre: string
  tipo: "MONTANA" | "RUTA"
  tipoActividad: string | null
  mountainNombre: string | null
  totalSalidas: number
  totalParticipantes: number
}

export function useRankingMontanaRuta(top = 10) {
  return useQuery({
    queryKey: ["estadisticas", "ranking-montana-ruta", top],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<RankingMontanaRutaResponse>>(
        "/v1/estadisticas/ranking-montana-ruta",
        { params: { top } },
      )
      return data.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useBuscarMontanaRuta(
  tipo: string,
  q: string,
  sinSalidas: boolean,
  enabled: boolean,
) {
  return useQuery({
    queryKey: ["estadisticas", "montana-ruta-busqueda", tipo, q, sinSalidas],
    queryFn: async () => {
      const params: Record<string, unknown> = { tipo, sinSalidas }
      if (q) params.q = q
      const { data } = await api.get<ApiResponse<MontanaRutaBusquedaItem[]>>(
        "/v1/estadisticas/montana-ruta/buscar",
        { params },
      )
      return data.data
    },
    enabled,
    staleTime: 2 * 60 * 1000,
  })
}

// ─── Actividad por período types ─────────────────────────────────────────────

export interface SalidaPeriodoItem {
  salidaId: string
  nombre: string
  fecha: string
  tipoActividad: string | null
  mountainNombre: string | null
  rutaNombre: string | null
  estado: string
  totalParticipantes: number
  seRealizo: boolean | null
}

export interface MontanaPeriodoItem {
  mountainId: number
  nombre: string
  region: string
  altitud: number
  totalSalidas: number
  totalParticipantes: number
  primeraFecha: string | null
  ultimaFecha: string | null
}

export interface RutaPeriodoItem {
  rutaId: number
  nombre: string
  tipoActividad: string | null
  mountainNombre: string | null
  totalSalidas: number
  totalParticipantes: number
  primeraFecha: string | null
  ultimaFecha: string | null
}

export type TipoPeriodoBusqueda = "salidas" | "montanas" | "rutas"

export interface PeriodoBusquedaFilters {
  tipo: TipoPeriodoBusqueda
  fechaDesde: string
  fechaHasta: string
  tipoActividad?: string
}

// ─── Actividad por período hooks ──────────────────────────────────────────────

export function useActividadPorPeriodo(filters: PeriodoBusquedaFilters, enabled = true) {
  return useQuery({
    queryKey: ["estadisticas", "periodo", filters],
    queryFn: async () => {
      const params: Record<string, unknown> = {
        fechaDesde: filters.fechaDesde,
        fechaHasta: filters.fechaHasta,
      }
      if (filters.tipoActividad) params.tipoActividad = filters.tipoActividad

      if (filters.tipo === "salidas") {
        const { data } = await api.get<ApiResponse<SalidaPeriodoItem[]>>(
          "/v1/estadisticas/periodo/salidas", { params },
        )
        return { tipo: "salidas" as const, items: data.data }
      }
      if (filters.tipo === "montanas") {
        const { data } = await api.get<ApiResponse<MontanaPeriodoItem[]>>(
          "/v1/estadisticas/periodo/montanas", { params },
        )
        return { tipo: "montanas" as const, items: data.data }
      }
      const { data } = await api.get<ApiResponse<RutaPeriodoItem[]>>(
        "/v1/estadisticas/periodo/rutas", { params },
      )
      return { tipo: "rutas" as const, items: data.data }
    },
    enabled,
    staleTime: 2 * 60 * 1000,
  })
}

export function useParticipantesFiltrados(filters: ParticipantesFilters, enabled = true) {
  return useQuery({
    queryKey: ["estadisticas", "participantes", filters],
    queryFn: async () => {
      const params: Record<string, unknown> = {}
      if (filters.tipoActividad)          params.tipoActividad  = filters.tipoActividad
      if (filters.mountainId != null)    params.mountainId     = filters.mountainId
      if (filters.rutaId != null)        params.rutaId         = filters.rutaId
      if (filters.dignidadId != null)    params.dignidadId     = filters.dignidadId
      if (filters.nivelTecnicoId != null) params.nivelTecnicoId = filters.nivelTecnicoId
      if (filters.q)                     params.q              = filters.q
      const { data } = await api.get<ApiResponse<ParticipanteFiltradoItem[]>>(
        "/v1/estadisticas/participantes",
        { params },
      )
      return data.data
    },
    enabled,
    staleTime: 2 * 60 * 1000,
  })
}
