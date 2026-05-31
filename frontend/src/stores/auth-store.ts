import { create } from "zustand"

export interface User {
  socioId: string
  username: string
  nombre: string
  rol: string
  nivelTecnico: string | null
  inhabilitado: boolean
  esJefeMontana: boolean
  esPresidenta: boolean
  esJefeSalidaActivo: boolean
}

interface AuthState {
  accessToken: string | null
  /** Timestamp en ms (Date.now()) en que expira el access token. null si no hay sesión. */
  tokenExpiresAt: number | null
  user: User | null
  isAuthenticated: boolean

  setAuth: (payload: { accessToken: string; user: User }) => void
  clearAuth: () => void
}

/** Extrae el campo `exp` del payload JWT (sin verificar firma). */
function getJwtExpiry(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]))
    return typeof payload.exp === "number" ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

/**
 * Store de autenticación con Zustand.
 *
 * - El access token se guarda solo en memoria (nunca en localStorage).
 * - El refresh token lo maneja el browser como cookie HttpOnly.
 * - Al hacer refresh (POST /auth/refresh), se actualiza el accessToken aquí.
 * - Al cerrar pestaña, el access token se pierde. Al recargar, la app
 *   intenta un refresh automático al montar.
 */
export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  tokenExpiresAt: null,
  user: null,
  isAuthenticated: false,

  setAuth: ({ accessToken, user }) =>
    set({ accessToken, tokenExpiresAt: getJwtExpiry(accessToken), user, isAuthenticated: true }),

  clearAuth: () =>
    set({ accessToken: null, tokenExpiresAt: null, user: null, isAuthenticated: false }),
}))
