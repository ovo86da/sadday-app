import { create } from "zustand"

export interface User {
  socioId: string
  username: string
  nombre: string
  rol: string
  nivelTecnico: string | null
  inhabilitado: boolean
  esJefeMontana: boolean
}

interface AuthState {
  accessToken: string | null
  user: User | null
  isAuthenticated: boolean

  setAuth: (payload: { accessToken: string; user: User }) => void
  clearAuth: () => void
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
  user: null,
  isAuthenticated: false,

  setAuth: ({ accessToken, user }) =>
    set({ accessToken, user, isAuthenticated: true }),

  clearAuth: () =>
    set({ accessToken: null, user: null, isAuthenticated: false }),
}))
