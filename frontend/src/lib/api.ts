import axios from "axios"
import { useAuthStore } from "@/stores/auth-store"
import {
  acquireRefreshLock,
  releaseRefreshLock,
  broadcastRefreshDone,
  broadcastRefreshFailed,
  waitForRefreshResult,
} from "@/lib/auth-broadcast"

/**
 * Instancia de Axios para todas las peticiones al backend.
 *
 * - `baseURL`: usa el proxy de Vite en dev, o la misma URL en prod.
 * - `withCredentials`: envía la cookie HttpOnly de refresh token automáticamente.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
    // Requerido por el backend en /auth/refresh para prevenir CSRF.
    // Un formulario o fetch cross-origin no puede setear headers custom sin
    // pasar por un preflight CORS, que ya está restringido al origen del frontend.
    "X-Sadday-Client": "spa",
  },
})

// ─── Request interceptor: añade Bearer token ──────────────────────────
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ─── Response interceptor: auto-refresh en 401 ───────────────────────
//
// isRefreshing / failedQueue: coordinan peticiones concurrentes DENTRO
// de la misma tab (patrón estándar).
// acquireRefreshLock / waitForRefreshResult: coordinan ENTRE tabs via
// localStorage + BroadcastChannel para evitar que dos tabs roten el
// mismo refresh token simultáneamente (A9).
let isRefreshing = false
let failedQueue: Array<{
  resolve: (value?: unknown) => void
  reject: (reason?: unknown) => void
}> = []

const processQueue = (error: unknown | null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve()
  })
  failedQueue = []
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // Solo intentar refresh si es 401 y no es un endpoint de autenticación
    if (
      error.response?.status !== 401 ||
      originalRequest._retry ||
      originalRequest.url?.includes("/auth/refresh") ||
      originalRequest.url?.includes("/auth/login") ||
      originalRequest.url?.includes("/auth/mfa/login") ||
      originalRequest.url?.includes("/auth/country-challenge")
    ) {
      return Promise.reject(error)
    }

    // Si ya estamos refrescando en esta tab, encolar la petición
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then(() => api(originalRequest))
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      // Verificar si otra tab ya está refrescando (cross-tab lock)
      if (!acquireRefreshLock()) {
        const result = await waitForRefreshResult()
        if (result) {
          useAuthStore.getState().setAuth({ accessToken: result.accessToken, user: result.user })
          processQueue(null)
          return api(originalRequest)
        }
        // Timeout o fallo en otra tab — limpiar sesión
        processQueue(new Error("cross-tab refresh failed"))
        useAuthStore.getState().clearAuth()
        window.location.href = "/login"
        return Promise.reject(error)
      }

      // Esta tab tiene el lock — hacer el refresh
      try {
        const { data } = await api.post("/v1/auth/refresh")
        const d = data.data
        const user = {
          socioId: d.socioId,
          username: d.username,
          nombre: d.nombre,
          rol: d.rol,
          nivelTecnico: d.nivelTecnico ?? null,
          inhabilitado: d.inhabilitado ?? false,
          esJefeMontana: d.esJefeMontana ?? false,
        }
        useAuthStore.getState().setAuth({ accessToken: d.accessToken, user })
        broadcastRefreshDone(d.accessToken, user)
        processQueue(null)
        return api(originalRequest)
      } catch (refreshError) {
        broadcastRefreshFailed()
        processQueue(refreshError)
        useAuthStore.getState().clearAuth()
        window.location.href = "/login"
        return Promise.reject(refreshError)
      } finally {
        releaseRefreshLock()
      }
    } finally {
      isRefreshing = false
    }
  }
)

export default api
