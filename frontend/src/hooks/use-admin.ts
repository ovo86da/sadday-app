import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse, PageResponse } from "@/types/socios"
import type { AuditoriaEntry, ConfiguracionSistema, SecurityEventEntry, UsuarioAuthSummary } from "@/types/admin"

const KEY = "admin"

// ─── Configuración del sistema ────────────────────────────────────────────────

export function useConfiguracion() {
  return useQuery({
    queryKey: [KEY, "config"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<ConfiguracionSistema[]>>("/v1/admin/config")
      return data.data
    },
  })
}

export function useActualizarConfig() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ clave, valor }: { clave: string; valor: string }) => {
      const { data } = await api.patch<ApiResponse<ConfiguracionSistema>>(
        `/v1/admin/config/${clave}`,
        { valor },
      )
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [KEY, "config"] })
    },
  })
}

// ─── Auditoría ────────────────────────────────────────────────────────────────

export interface AuditoriaParams {
  page?: number
  size?: number
  actorUsername?: string
  accion?: string
  omitirAccion?: string[]
  resultado?: string
  entidadAfectada?: string
  entidadId?: string
  fechaDesde?: string
  fechaHasta?: string
}

export function useAuditoria(params: AuditoriaParams = {}) {
  const { page = 0, size = 30, omitirAccion, ...filters } = params
  return useQuery({
    queryKey: [KEY, "auditoria", { page, size, omitirAccion, ...filters }],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<AuditoriaEntry>>>(
        "/v1/admin/auditoria",
        {
          params: { page, size, sort: "createdAt,desc", ...filters },
          // Spring acepta params repetidos: omitirAccion=A&omitirAccion=B
          paramsSerializer: (p) => {
            const base = new URLSearchParams(p).toString()
            const omit = (omitirAccion ?? [])
              .map((a) => `omitirAccion=${encodeURIComponent(a)}`)
              .join("&")
            return omit ? `${base}&${omit}` : base
          },
        },
      )
      return data.data
    },
  })
}

// ─── Eventos de seguridad ─────────────────────────────────────────────────────

export interface SecurityEventsParams {
  page?: number
  size?: number
  username?: string
  eventType?: string
  ipAddress?: string
  fechaDesde?: string
  fechaHasta?: string
}

export function useSecurityEvents(params: SecurityEventsParams = {}) {
  const { page = 0, size = 30, ...filters } = params
  return useQuery({
    queryKey: [KEY, "security-events", { page, size, ...filters }],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<SecurityEventEntry>>>(
        "/v1/admin/security-events",
        { params: { page, size, sort: "createdAt,desc", ...filters } },
      )
      return data.data
    },
  })
}

// ─── Usuarios Auth ────────────────────────────────────────────────────────────

export function useUsuariosAuth() {
  return useQuery({
    queryKey: [KEY, "usuarios-auth"],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<UsuarioAuthSummary[]>>(
        "/v1/admin/usuarios-auth",
      )
      return data.data
    },
  })
}

export function useUsuarioAuthBySocio(socioId: string | undefined) {
  return useQuery({
    queryKey: [KEY, "usuarios-auth", socioId],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<UsuarioAuthSummary | null>>(
        `/v1/admin/usuarios-auth/${socioId}`,
      )
      return data.data
    },
    enabled: !!socioId,
  })
}

export function useDesbloquearUsuario() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (socioId: string) => {
      await api.post(`/v1/admin/usuarios-auth/${socioId}/desbloquear`)
    },
    onSuccess: (_data, socioId) => {
      qc.invalidateQueries({ queryKey: [KEY, "usuarios-auth"] })
      qc.invalidateQueries({ queryKey: [KEY, "usuarios-auth", socioId] })
    },
  })
}

export function useCambiarEstadoAcceso() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ socioId, codigo }: { socioId: string; codigo: string }) => {
      await api.patch(`/v1/admin/usuarios-auth/${socioId}/estado-acceso`, null, { params: { codigo } })
    },
    onSuccess: (_data, { socioId }) => {
      qc.invalidateQueries({ queryKey: [KEY, "usuarios-auth"] })
      qc.invalidateQueries({ queryKey: [KEY, "usuarios-auth", socioId] })
    },
  })
}

export function useForzarCierreSesion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (socioId: string) => {
      await api.post(`/v1/admin/usuarios-auth/${socioId}/cerrar-sesion`)
    },
    onSuccess: (_data, socioId) => {
      qc.invalidateQueries({ queryKey: [KEY, "usuarios-auth"] })
      qc.invalidateQueries({ queryKey: [KEY, "usuarios-auth", socioId] })
    },
  })
}

export function useEmergencyReset() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (socioId: string) => {
      await api.post(`/v1/socios/${socioId}/emergency-reset`)
    },
    onSuccess: (_data, socioId) => {
      qc.invalidateQueries({ queryKey: [KEY, "usuarios-auth", socioId] })
    },
  })
}
