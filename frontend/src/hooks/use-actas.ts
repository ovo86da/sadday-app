import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse, PageResponse } from "@/types/socios"
import type {
  ActaSummary,
  ActaDetail,
  CreateActaRequest,
  UpdateActaRequest,
  AsistenteResponse,
  InformeLinkResponse,
  ActaImportPreview,
  ActaImportConfirmRequest,
} from "@/types/actas"

const KEY = "actas"

interface ActaListParams {
  page?: number
  size?: number
  q?: string
  tipo?: "SOCIOS" | "DIRECTIVA"
}

export function useActasList(params: ActaListParams = {}) {
  const { page = 0, size = 20, ...filters } = params
  return useQuery({
    queryKey: [KEY, "list", { page, size, ...filters }],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<ActaSummary>>>("/v1/actas", {
        params: { page, size, ...filters },
      })
      return data.data
    },
  })
}

export function useActaDetail(id: string | undefined) {
  return useQuery({
    queryKey: [KEY, "detail", id],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<ActaDetail>>(`/v1/actas/${id}`)
      return data.data
    },
    enabled: !!id,
  })
}

export function useCreateActa() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: CreateActaRequest) => {
      const { data } = await api.post<ApiResponse<ActaDetail>>("/v1/actas", req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useUpdateActa(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: UpdateActaRequest) => {
      const { data } = await api.put<ApiResponse<ActaDetail>>(`/v1/actas/${id}`, req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useDeleteActa() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => { await api.delete(`/v1/actas/${id}`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useAgregarAsistente(actaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (socioId: string) => {
      const { data } = await api.post<ApiResponse<AsistenteResponse>>(`/v1/actas/${actaId}/asistentes`, { socioId })
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "detail", actaId] }),
  })
}

export function useEliminarAsistente(actaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (asistenteId: number) => { await api.delete(`/v1/actas/${actaId}/asistentes/${asistenteId}`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "detail", actaId] }),
  })
}

export function useAgregarInformeActa(actaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (informeId: string) => {
      const { data } = await api.post<ApiResponse<InformeLinkResponse>>(`/v1/actas/${actaId}/informes`, { informeId })
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "detail", actaId] }),
  })
}

export function useEliminarInformeActa(actaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (linkId: number) => { await api.delete(`/v1/actas/${actaId}/informes/${linkId}`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "detail", actaId] }),
  })
}

export function useImportarActaPreview() {
  return useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append("file", file)
      const { data } = await api.post<ApiResponse<ActaImportPreview>>(
        "/v1/actas/importar",
        formData,
        { headers: { "Content-Type": "multipart/form-data" } },
      )
      return data.data
    },
  })
}

export function useConfirmarImportActa() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: ActaImportConfirmRequest) => {
      const { data } = await api.post<ApiResponse<ActaDetail>>("/v1/actas/importar/confirmar", req)
      return data.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useGenerarPdfActa(actaId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (): Promise<{ blob: Blob; filename: string }> => {
      // Paso 1: generar y subir a MinIO (retorna JSON)
      await api.post(`/v1/actas/${actaId}/pdf`)
      // Paso 2: descargar los bytes ya generados
      const response = await api.get(`/v1/actas/${actaId}/pdf`, { responseType: "blob" })
      const disposition: string = response.headers["content-disposition"] ?? ""
      const match = disposition.match(/filename="?([^"]+)"?/)
      const filename = match?.[1] ?? `acta-${actaId}.pdf`
      return { blob: response.data as Blob, filename }
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "detail", actaId] }),
  })
}
