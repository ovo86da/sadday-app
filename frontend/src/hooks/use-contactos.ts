import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse, PageResponse } from "@/types/socios"
import type { Contacto, CreateContactoRequest, UpdateContactoRequest } from "@/types/contactos"

const KEY = "contactos"

export function useBuscarContactos(q: string) {
  return useQuery({
    queryKey: [KEY, "buscar", q],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<Contacto[]>>("/v1/contactos/buscar", {
        params: { q },
      })
      return data.data
    },
    enabled: q.length >= 2,
    staleTime: 30_000,
  })
}

export function useContactosList(params: { q?: string; page?: number; size?: number } = {}) {
  const { q = "", page = 0, size = 20 } = params
  return useQuery({
    queryKey: [KEY, "list", page, size, q],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<Contacto>>>("/v1/contactos", {
        params: { page, size, ...(q ? { q } : {}) },
      })
      return data.data
    },
  })
}

export function useCreateContacto() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: CreateContactoRequest) => {
      const { data } = await api.post<ApiResponse<Contacto>>("/v1/contactos", req)
      return data.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useUpdateContacto() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, data: req }: { id: number; data: UpdateContactoRequest }) => {
      const { data } = await api.put<ApiResponse<Contacto>>(`/v1/contactos/${id}`, req)
      return data.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}

export function useDeleteContacto() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/v1/contactos/${id}`)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [KEY] }),
  })
}
