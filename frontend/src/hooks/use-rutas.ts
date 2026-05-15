import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse, PageResponse } from "@/types/socios"
import type { RutaSummary, RutaDetail, CreateRutaRequest, UpdateRutaRequest, EquipoMontana, RutaDocumento } from "@/types/rutas"

const KEY = "rutas"

interface RutaListParams {
  page?: number
  size?: number
  sort?: string
  mountainId?: number
  aprobada?: boolean
  tipoActividad?: string
  q?: string
  nivelMinimoSocioId?: string
  requierePermisos?: boolean
  tieneTrack?: boolean
  longitudKmMin?: number
  longitudKmMax?: number
  desnivelMin?: number
  desnivelMax?: number
  duracionDiasMin?: number
  duracionDiasMax?: number
}

export function useRutasList(params: RutaListParams = {}) {
  const { page = 0, size = 20, sort = "nombre,asc", ...filters } = params
  return useQuery({
    queryKey: [KEY, "list", { page, size, sort, ...filters }],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<RutaSummary>>>("/v1/rutas", {
        params: { page, size, sort, ...filters },
      })
      return data.data
    },
  })
}

export function useRutaDetail(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, "detail", id],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<RutaDetail>>(`/v1/rutas/${id}`)
      return data.data
    },
    enabled: id !== undefined,
  })
}

export function useCreateRuta() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: CreateRutaRequest) => {
      const { data } = await api.post<ApiResponse<RutaDetail>>("/v1/rutas", req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useUpdateRuta(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: UpdateRutaRequest) => {
      const { data } = await api.put<ApiResponse<RutaDetail>>(`/v1/rutas/${id}`, req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useAprobarRuta() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => { await api.patch(`/v1/rutas/${id}/aprobar`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useRutasByMountain(mountainId: number | null) {
  return useQuery({
    queryKey: [KEY, "by-mountain", mountainId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<RutaSummary>>>("/v1/rutas", {
        params: { mountainId, aprobada: true, size: 500, sort: "nombre,asc" },
      })
      return data.data
    },
    enabled: mountainId !== null,
  })
}

/** Rutas aprobadas filtradas por tipo de actividad (ALPINISMO, CICLISMO, ESCALADA, TREKKING). */
export function useRutasByActividad(tipoActividad: string | null) {
  return useQuery({
    queryKey: [KEY, "by-actividad", tipoActividad],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<RutaSummary>>>("/v1/rutas", {
        params: { tipoActividad, aprobada: true, size: 500, sort: "nombre,asc" },
      })
      return data.data
    },
    enabled: !!tipoActividad,
  })
}

export function useDeleteRuta() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => { await api.delete(`/v1/rutas/${id}`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useRutaEquipos() {
  return useQuery({
    queryKey: [KEY, "equipos"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<EquipoMontana[]>>("/v1/rutas/equipos")
      return data.data
    },
    staleTime: 30 * 60 * 1000,
  })
}

// ─── Documentos de permiso ───────────────────────────────────────────────────

export function useRutaDocumentos(rutaId: number | undefined) {
  return useQuery({
    queryKey: [KEY, "documentos", rutaId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<RutaDocumento[]>>(`/v1/rutas/${rutaId}/documentos`)
      return data.data
    },
    enabled: rutaId !== undefined,
  })
}

export function useSubirDocumentoRuta(rutaId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append("file", file)
      const { data } = await api.post<ApiResponse<RutaDocumento>>(
        `/v1/rutas/${rutaId}/documentos`,
        formData,
        { headers: { "Content-Type": "multipart/form-data" } },
      )
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "documentos", rutaId] })
      qc.invalidateQueries({ queryKey: [KEY, "detail", rutaId] })
    },
  })
}

export function useEliminarDocumentoRuta(rutaId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (docId: string) => {
      await api.delete(`/v1/rutas/${rutaId}/documentos/${docId}`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "documentos", rutaId] })
      qc.invalidateQueries({ queryKey: [KEY, "detail", rutaId] })
    },
  })
}
