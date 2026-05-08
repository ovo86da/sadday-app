import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse, PageResponse } from "@/types/socios"
import type {
  MountainSummary,
  MountainDetail,
  CreateMountainRequest,
  UpdateMountainRequest,
  AccesoNivelResponse,
  UpdateAccesoNivelRequest,
  MountainLookups,
} from "@/types/mountains"

const KEY = "mountains"

interface MountainListParams {
  page?: number
  size?: number
  sort?: string
  q?: string
  region?: string
}

export function useMountainsList(params: MountainListParams = {}) {
  const { page = 0, size = 20, sort = "nombre,asc", ...filters } = params
  return useQuery({
    queryKey: [KEY, "list", { page, size, sort, ...filters }],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<MountainSummary>>>("/v1/mountains", {
        params: { page, size, sort, ...filters },
      })
      return data.data
    },
  })
}

export function useMountainDetail(id: number | undefined) {
  return useQuery({
    queryKey: [KEY, "detail", id],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<MountainDetail>>(`/v1/mountains/${id}`)
      return data.data
    },
    enabled: id !== undefined,
  })
}

export function useCreateMountain() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: CreateMountainRequest) => {
      const { data } = await api.post<ApiResponse<MountainDetail>>("/v1/mountains", req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useUpdateMountain(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: UpdateMountainRequest) => {
      const { data } = await api.put<ApiResponse<MountainDetail>>(`/v1/mountains/${id}`, req)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useDeleteMountain() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/v1/mountains/${id}`)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "list"] }),
  })
}

export function useMountainLookups() {
  return useQuery({
    queryKey: [KEY, "lookups"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<MountainLookups>>("/v1/mountains/lookups")
      return data.data
    },
    staleTime: 30 * 60 * 1000, // lookups rarely change
  })
}

export function useAccesoPorNivel() {
  return useQuery({
    queryKey: [KEY, "acceso-por-nivel"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<AccesoNivelResponse[]>>("/v1/mountains/acceso-por-nivel")
      return data.data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useUpdateAccesoNivel() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ nivelSocioId, req }: { nivelSocioId: string; req: UpdateAccesoNivelRequest }) => {
      const { data } = await api.put<ApiResponse<AccesoNivelResponse>>(
        `/v1/mountains/acceso-por-nivel/${nivelSocioId}`,
        req,
      )
      return data.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY, "acceso-por-nivel"] }),
  })
}
