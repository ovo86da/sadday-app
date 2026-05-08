import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type {
  ApiResponse,
  PageResponse,
  LookupsResponse,
  SocioSummary,
  SocioDetail,
  CreateSocioRequest,
  UpdateSocioRequest,
  UpdateRolRequest,
  CuotaResponse,
  CreateCuotaRequest,
  HabilitacionLogEntry,
  CsvHabilitacionResult,
  CsvFilaValida,
  CsvImportPreviewResponse,
  CsvImportResultResponse,
  InvitacionPendiente,
} from "@/types/socios"

const SOCIOS_KEY = "socios"

// ─── Queries ─────────────────────────────────────────

export function useLookups() {
  return useQuery({
    queryKey: [SOCIOS_KEY, "lookups"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<LookupsResponse>>("/v1/socios/lookups")
      return data.data
    },
    staleTime: 30 * 60 * 1000,   // 30 min — datos de catálogo cambian poco
  })
}

interface SocioListParams {
  page?: number
  size?: number
  sort?: string
  rolId?: number
  estadoId?: number
  tipoId?: number
  q?: string
}

export function useSociosList(params: SocioListParams = {}) {
  const { page = 0, size = 20, sort = "apellido,asc", ...filters } = params

  return useQuery({
    queryKey: [SOCIOS_KEY, "list", { page, size, sort, ...filters }],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<SocioSummary>>>("/v1/socios", {
        params: { page, size, sort, ...filters },
      })
      return data.data
    },
  })
}

export function useSocioDetail(id: string | undefined) {
  return useQuery({
    queryKey: [SOCIOS_KEY, "detail", id],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<SocioDetail>>(`/v1/socios/${id}`)
      return data.data
    },
    enabled: !!id,
  })
}

// ─── Mutations ───────────────────────────────────────

export function useCreateSocio() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (request: CreateSocioRequest) => {
      await api.post("/v1/socios", request)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "list"] })
    },
  })
}

export function useUpdateSocio(id: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (request: UpdateSocioRequest) => {
      const { data } = await api.put<ApiResponse<SocioDetail>>(`/v1/socios/${id}`, request)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY] })
    },
  })
}

export function useHabilitarSocio() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (id: string) => {
      await api.patch(`/v1/socios/${id}/habilitar`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY] })
    },
  })
}

export function useInhabilitarSocio() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (id: string) => {
      await api.patch(`/v1/socios/${id}/inhabilitar`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY] })
    },
  })
}

export function useCambiarRol() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ id, request }: { id: string; request: UpdateRolRequest }) => {
      await api.patch(`/v1/socios/${id}/rol`, request)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY] })
    },
  })
}

export function useActualizarNivelTecnico(socioId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (nivelTecnicoId: string | null) => {
      const { data } = await api.patch<ApiResponse<SocioDetail>>(
        `/v1/socios/${socioId}/nivel-tecnico`,
        { nivelTecnicoId },
      )
      return data.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "detail", socioId] })
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "list"] })
    },
  })
}

export function useDeleteSocio() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/v1/socios/${id}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "list"] })
    },
  })
}

// ─── Cuotas ──────────────────────────────────────────

export function useCuotas(socioId: string | undefined) {
  return useQuery({
    queryKey: [SOCIOS_KEY, "cuotas", socioId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<CuotaResponse[]>>(`/v1/socios/${socioId}/cuotas`)
      return data.data
    },
    enabled: !!socioId,
  })
}

export function useRegistrarCuota(socioId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (request: CreateCuotaRequest) => {
      const { data } = await api.post<ApiResponse<CuotaResponse>>(
        `/v1/socios/${socioId}/cuotas`,
        request,
      )
      return data.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "cuotas", socioId] })
    },
  })
}

export function useEliminarCuota(socioId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (cuotaId: number) => {
      await api.delete(`/v1/socios/${socioId}/cuotas/${cuotaId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "cuotas", socioId] })
    },
  })
}

export function useReenviarInvitacion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (socioId: string) => {
      await api.post(`/v1/socios/${socioId}/reenviar-invitacion`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "list"] })
    },
  })
}

export function useSetJefeMontana() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, valor }: { id: string; valor: boolean }) => {
      const { data } = await api.patch<ApiResponse<SocioDetail>>(
        `/v1/socios/${id}/jefe-montana`,
        null,
        { params: { valor } },
      )
      return data.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY] })
    },
  })
}

// ─── Habilitación log ─────────────────────────────────

export function useHabilitacionLog(socioId: string | undefined) {
  return useQuery({
    queryKey: [SOCIOS_KEY, "habilitacion-log", socioId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<HabilitacionLogEntry[]>>(
        `/v1/socios/${socioId}/habilitacion-log`
      )
      return data.data
    },
    enabled: !!socioId,
  })
}

// ─── CSV Habilitación masiva ──────────────────────────

export function useUploadCsvHabilitacion() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({ file }: { file: File }) => {
      const formData = new FormData()
      formData.append("file", file)
      const { data } = await api.post<ApiResponse<CsvHabilitacionResult>>(
        "/v1/socios/habilitacion/csv",
        formData,
        { headers: { "Content-Type": "multipart/form-data" } }
      )
      return data.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "list"] })
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "habilitacion-log"] })
    },
  })
}

// ─── CSV Import de socios existentes ─────────────────

export function useCsvImportPreview() {
  return useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append("file", file)
      const { data } = await api.post<ApiResponse<CsvImportPreviewResponse>>(
        "/v1/socios/importar/preview",
        formData,
        { headers: { "Content-Type": "multipart/form-data" } }
      )
      return data.data
    },
  })
}

export function useCsvImportConfirmar() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (filas: CsvFilaValida[]) => {
      const { data } = await api.post<ApiResponse<CsvImportResultResponse>>(
        "/v1/socios/importar/confirmar",
        filas
      )
      return data.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "list"] })
    },
  })
}

// ─── Invitaciones pendientes ──────────────────────────

export function useInvitaciones() {
  return useQuery({
    queryKey: [SOCIOS_KEY, "invitaciones"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<InvitacionPendiente[]>>("/v1/socios/invitaciones")
      return data.data
    },
  })
}

export function useReenviarInvitacionPendiente() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (tokenId: string) => {
      await api.post(`/v1/socios/invitaciones/${tokenId}/reenviar`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "invitaciones"] })
    },
  })
}

export function useEliminarInvitacion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (tokenId: string) => {
      await api.delete(`/v1/socios/invitaciones/${tokenId}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [SOCIOS_KEY, "invitaciones"] })
    },
  })
}
