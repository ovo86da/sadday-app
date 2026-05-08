import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse, PageResponse } from "@/types/socios"
import type {
  SalidaLookupsResponse,
  SalidaSummary,
  SalidaDetail,
  CreateSalidaRequest,
  UpdateSalidaRequest,
  CambiarEstadoSalidaRequest,
  EstadoSalida,
  AprobacionPendiente,
  EliminarSalidaRequest,
  CancelarSalidaRequest,
} from "@/types/salidas"

const KEY = "salidas"

export function useSalidaLookups() {
  return useQuery({
    queryKey: [KEY, "lookups"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<SalidaLookupsResponse>>("/v1/salidas/lookups")
      return data.data
    },
    staleTime: 30 * 60 * 1000,
  })
}

interface SalidaListParams {
  page?: number
  size?: number
  sort?: string
  estado?: EstadoSalida
  fechaInicio?: string
  q?: string
  rutaId?: number
}

export function useSalidasList(params: SalidaListParams = {}) {
  const { page = 0, size = 20, sort = "fechaInicio,desc", ...filters } = params
  return useQuery({
    queryKey: [KEY, "list", { page, size, sort, ...filters }],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<SalidaSummary>>>("/v1/salidas", {
        params: { page, size, sort, ...filters },
      })
      return data.data
    },
  })
}

export function useSalidaDetail(id: string | undefined) {
  return useQuery({
    queryKey: [KEY, "detail", id],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<SalidaDetail>>(`/v1/salidas/${id}`)
      return data.data
    },
    enabled: !!id,
  })
}

export function useCreateSalida() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: CreateSalidaRequest) => {
      const { data } = await api.post<ApiResponse<SalidaDetail>>("/v1/salidas", req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useUpdateSalida(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: UpdateSalidaRequest) => {
      const { data } = await api.put<ApiResponse<SalidaDetail>>(`/v1/salidas/${id}`, req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useCambiarEstadoSalida() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, request }: { id: string; request: CambiarEstadoSalidaRequest }) => {
      await api.patch(`/v1/salidas/${id}/estado`, request)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useDesignarJefeSalida(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (participanteId: number) => {
      const { data } = await api.patch<ApiResponse<unknown>>(
        `/v1/salidas/${salidaId}/inscripciones/${participanteId}/jefe`,
      )
      return data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] })
      qc.invalidateQueries({ queryKey: ["alertas-sin-jefe"] })
    },
  })
}

export function useAgregarDignidad(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ participanteId, dignidadId }: { participanteId: number; dignidadId: number }) => {
      const { data } = await api.post<ApiResponse<unknown>>(
        `/v1/salidas/${salidaId}/inscripciones/${participanteId}/dignidades`,
        { dignidadId },
      )
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] }),
  })
}

export function useEliminarDignidad(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ participanteId, dignidadAsignadaId }: { participanteId: number; dignidadAsignadaId: number }) => {
      await api.delete(`/v1/salidas/${salidaId}/inscripciones/${participanteId}/dignidades/${dignidadAsignadaId}`)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] }),
  })
}

export function useSalidasByRuta(rutaId: number | null) {
  return useQuery({
    queryKey: [KEY, "by-ruta", rutaId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<SalidaSummary>>>("/v1/salidas", {
        params: { rutaId, size: 100, sort: "fechaInicio,desc" },
      })
      return data.data
    },
    enabled: rutaId !== null,
  })
}

export function useDeleteSalida() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, motivo }: { id: string; motivo: string }) => {
      await api.delete(`/v1/salidas/${id}`, { data: { motivo } satisfies EliminarSalidaRequest })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useCancelarSalida() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, motivo }: { id: string; motivo: string }) => {
      const { data } = await api.patch<ApiResponse<SalidaDetail>>(
        `/v1/salidas/${id}/cancelar`,
        { motivo } satisfies CancelarSalidaRequest,
      )
      return data.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useInscribirSalida(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (socioId: string) => {
      const { data } = await api.post<ApiResponse<{ id: number }>>(
        `/v1/salidas/${salidaId}/inscripciones`,
        { socioId },
      )
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] })
      qc.invalidateQueries({ queryKey: [KEY, "list"] })
    },
  })
}

export function useCambiarEstadoInscripcion(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ participanteId, estadoInscripcion }: { participanteId: number; estadoInscripcion: string }) => {
      const { data } = await api.patch<ApiResponse<unknown>>(
        `/v1/salidas/${salidaId}/inscripciones/${participanteId}/estado`,
        { estadoInscripcion },
      )
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] }),
  })
}

export function useDecidirRiesgo(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({
      participanteId,
      aprobar,
      motivo,
    }: {
      participanteId: number
      aprobar: boolean
      motivo: string
    }) => {
      const { data } = await api.patch<ApiResponse<unknown>>(
        `/v1/salidas/${salidaId}/inscripciones/${participanteId}/aprobacion-riesgo`,
        { aprobar, motivo },
      )
      return data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] })
      qc.invalidateQueries({ queryKey: ["aprobaciones-pendientes"] })
    },
  })
}

export function useRevocarAprobacion(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (participanteId: number) => {
      const { data } = await api.delete<ApiResponse<unknown>>(
        `/v1/salidas/${salidaId}/inscripciones/${participanteId}/aprobacion-riesgo`,
      )
      return data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] })
      qc.invalidateQueries({ queryKey: ["aprobaciones-pendientes"] })
    },
  })
}

export function useAprobacionesPendientes(enabled = true) {
  return useQuery({
    queryKey: ["aprobaciones-pendientes"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<AprobacionPendiente[]>>(
        "/v1/salidas/aprobaciones-pendientes",
      )
      return data.data
    },
    enabled,
    staleTime: 0,
    refetchOnMount: true,
    refetchOnWindowFocus: true,
  })
}

export interface AlertaSinJefe {
  salidaId: string
  salidaNombre: string
  fechaSalida: string
  jefeAbandonoNombre: string
}

export function useAlertasSinJefe(enabled = true) {
  return useQuery({
    queryKey: ["alertas-sin-jefe"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<AlertaSinJefe[]>>(
        "/v1/salidas/alertas-sin-jefe",
      )
      return data.data
    },
    enabled,
    staleTime: 0,
    refetchOnMount: true,
    refetchOnWindowFocus: true,
  })
}

export interface SolapamientoItem {
  id: string
  nombre: string
  fechaInicio: string
  fechaFin: string
}

export async function verificarSolapamiento(
  fechaInicio: string,
  fechaFin: string,
  excludeId?: string,
): Promise<SolapamientoItem[]> {
  const params = new URLSearchParams({ fechaInicio, fechaFin })
  if (excludeId) params.set("excludeId", excludeId)
  const { data } = await api.get<ApiResponse<SolapamientoItem[]>>(
    `/v1/salidas/solapamiento?${params}`,
  )
  return data.data
}

export function useToggleCerrarInscripciones(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const { data } = await api.patch<ApiResponse<boolean>>(
        `/v1/salidas/${salidaId}/cerrar-inscripciones`,
      )
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] })
      qc.invalidateQueries({ queryKey: [KEY, "list"] })
    },
  })
}

export function useCancelarInscripcion(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (participanteId: number) => {
      await api.delete(`/v1/salidas/${salidaId}/inscripciones/${participanteId}`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "detail", salidaId] })
      qc.invalidateQueries({ queryKey: [KEY, "list"] })
      qc.invalidateQueries({ queryKey: ["alertas-sin-jefe"] })
    },
  })
}
