import { describe, it, expect, beforeEach } from "vitest"
import { useAuthStore } from "@/stores/auth-store"

const mockUser = {
  socioId: "uuid-1234",
  username: "juan.perez",
  nombre: "Juan Pérez",
  rol: "SOCIO",
  nivelTecnico: null,
}

describe("useAuthStore", () => {
  beforeEach(() => {
    // Resetear el store antes de cada test
    useAuthStore.getState().clearAuth()
  })

  it("estado inicial es no autenticado", () => {
    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(false)
    expect(state.accessToken).toBeNull()
    expect(state.user).toBeNull()
  })

  it("setAuth guarda token, usuario y marca isAuthenticated", () => {
    useAuthStore.getState().setAuth({ accessToken: "token-abc", user: mockUser })

    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(true)
    expect(state.accessToken).toBe("token-abc")
    expect(state.user).toEqual(mockUser)
  })

  it("clearAuth limpia todo el estado", () => {
    useAuthStore.getState().setAuth({ accessToken: "token-abc", user: mockUser })
    useAuthStore.getState().clearAuth()

    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(false)
    expect(state.accessToken).toBeNull()
    expect(state.user).toBeNull()
  })

  it("setAuth sobreescribe un token previo", () => {
    useAuthStore.getState().setAuth({ accessToken: "token-viejo", user: mockUser })
    useAuthStore.getState().setAuth({ accessToken: "token-nuevo", user: mockUser })

    expect(useAuthStore.getState().accessToken).toBe("token-nuevo")
  })

  it("setAuth funciona para distintos roles", () => {
    const roles = ["ADMIN", "SECRETARIA", "DIRECTIVO", "SOCIO"]
    for (const rol of roles) {
      useAuthStore.getState().setAuth({ accessToken: "t", user: { ...mockUser, rol } })
      expect(useAuthStore.getState().user?.rol).toBe(rol)
    }
  })
})
