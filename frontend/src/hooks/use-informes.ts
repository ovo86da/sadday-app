import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"
import type {
  InformeResponse,
  InformePendienteItem,
  CreateInformeRequest,
  UpdateInformeRequest,
  AgregarReconocimientoRequest,
  ReconocimientoResponse,
} from "@/types/informes"

const KEY = "informes"
const PENDIENTES_KEY = "informes-pendientes-jefe"

export function useInformesPendientesJefe(enabled = true) {
  return useQuery({
    queryKey: [PENDIENTES_KEY],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<InformePendienteItem[]>>("/v1/informes/pendientes-jefe")
      return data.data
    },
    enabled,
    staleTime: 60_000,
  })
}

export function useInforme(salidaId: string | undefined) {
  return useQuery({
    queryKey: [KEY, salidaId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<InformeResponse>>(`/v1/informes/${salidaId}`)
      return data.data
    },
    enabled: !!salidaId,
    retry: false, // 404 is expected when no informe exists yet
  })
}

export function useCreateInforme(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: CreateInformeRequest) => {
      const { data } = await api.post<ApiResponse<InformeResponse>>(`/v1/informes/${salidaId}`, req)
      return data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, salidaId] })
      qc.invalidateQueries({ queryKey: [PENDIENTES_KEY] })
    },
  })
}

export function useUpdateInforme(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: UpdateInformeRequest) => {
      const { data } = await api.put<ApiResponse<InformeResponse>>(`/v1/informes/${salidaId}`, req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, salidaId] }),
  })
}

export function useValidarInforme(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => { await api.patch(`/v1/informes/${salidaId}/validar`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, salidaId] }),
  })
}

export function useAgregarReconocimiento(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: AgregarReconocimientoRequest) => {
      const { data } = await api.post<ApiResponse<ReconocimientoResponse>>(`/v1/informes/${salidaId}/reconocimientos`, req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, salidaId] }),
  })
}

export function useEliminarReconocimiento(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (reconocimientoId: number) => {
      await api.delete(`/v1/informes/${salidaId}/reconocimientos/${reconocimientoId}`)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, salidaId] }),
  })
}

export function useGenerarPdfInforme(salidaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (): Promise<{ blob: Blob; filename: string }> => {
      const response = await api.post(`/v1/informes/${salidaId}/pdf`, null, { responseType: "blob" })
      const disposition: string = response.headers["content-disposition"] ?? ""
      const match = disposition.match(/filename="?([^"]+)"?/)
      const filename = match?.[1] ?? `informe-${salidaId}.pdf`
      return { blob: response.data as Blob, filename }
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, salidaId] }),
  })
}
