import { describe, it, expect, beforeEach, afterEach, vi } from "vitest"
import MockAdapter from "axios-mock-adapter"
import { useAuthStore } from "@/stores/auth-store"

// Importar api DESPUÉS de configurar mocks para que el módulo se inicialice limpio
import api from "@/lib/api"

const mock = new MockAdapter(api)

const mockUser = {
  socioId: "uuid-1234",
  username: "juan.perez",
  nombre: "Juan Pérez",
  rol: "SOCIO",
  nivelTecnico: null,
  inhabilitado: false,
  esJefeMontana: false,
}

describe("API — request interceptor", () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
    mock.reset()
  })

  it("no añade Authorization header si no hay token", async () => {
    let capturedHeaders: Record<string, string> = {}
    mock.onGet("/v1/test").reply((config) => {
      capturedHeaders = config.headers as Record<string, string>
      return [200, {}]
    })

    await api.get("/v1/test")
    expect(capturedHeaders["Authorization"]).toBeUndefined()
  })

  it("añade Authorization: Bearer <token> cuando hay accessToken", async () => {
    useAuthStore.getState().setAuth({ accessToken: "my-token-xyz", user: mockUser })

    let capturedHeaders: Record<string, string> = {}
    mock.onGet("/v1/test").reply((config) => {
      capturedHeaders = config.headers as Record<string, string>
      return [200, {}]
    })

    await api.get("/v1/test")
    expect(capturedHeaders["Authorization"]).toBe("Bearer my-token-xyz")
  })
})

describe("API — response interceptor (auto-refresh 401)", () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
    mock.reset()
    // Limpiar el flag isRefreshing entre tests (es estado de módulo)
    vi.restoreAllMocks()
  })

  afterEach(() => {
    mock.reset()
  })

  it("respuestas exitosas pasan sin modificación", async () => {
    mock.onGet("/v1/recurso").reply(200, { data: { id: 1 } })

    const res = await api.get("/v1/recurso")
    expect(res.status).toBe(200)
    expect(res.data.data.id).toBe(1)
  })

  it("errores que no son 401 se rechazan directamente", async () => {
    mock.onGet("/v1/recurso").reply(404, { message: "No encontrado" })

    await expect(api.get("/v1/recurso")).rejects.toMatchObject({
      response: { status: 404 },
    })
  })

  it("401 en /auth/refresh se rechaza sin reintentar (evita bucle)", async () => {
    mock.onPost("/v1/auth/refresh").reply(401, {})

    await expect(api.post("/v1/auth/refresh")).rejects.toMatchObject({
      response: { status: 401 },
    })
  })

  it("401 en /auth/login se rechaza sin reintentar", async () => {
    mock.onPost("/v1/auth/login").reply(401, { message: "Credenciales incorrectas" })

    await expect(api.post("/v1/auth/login")).rejects.toMatchObject({
      response: { status: 401 },
    })
  })

  it("401 en endpoint protegido dispara refresh y reintenta la petición", async () => {
    useAuthStore.getState().setAuth({ accessToken: "token-caducado", user: mockUser })

    // Primera llamada a /v1/protegido → 401
    // Después del refresh → 200
    let protegidoCalls = 0
    mock.onGet("/v1/protegido").reply(() => {
      protegidoCalls++
      if (protegidoCalls === 1) return [401, {}]
      return [200, { data: { ok: true } }]
    })

    mock.onPost("/v1/auth/refresh").reply(200, {
      data: {
        accessToken: "token-nuevo",
        socioId: mockUser.socioId,
        username: mockUser.username,
        nombre: mockUser.nombre,
        rol: mockUser.rol,
      },
    })

    const res = await api.get("/v1/protegido")
    expect(res.status).toBe(200)
    expect(protegidoCalls).toBe(2) // Se intentó dos veces
    expect(useAuthStore.getState().accessToken).toBe("token-nuevo")
  })

  it("si el refresh falla, limpia el store y redirige a /login", async () => {
    const originalHref = window.location.href
    // Espiar window.location.href
    Object.defineProperty(window, "location", {
      value: { href: originalHref },
      writable: true,
    })

    useAuthStore.getState().setAuth({ accessToken: "token-caducado", user: mockUser })

    mock.onGet("/v1/protegido").reply(401, {})
    mock.onPost("/v1/auth/refresh").reply(401, {})

    await expect(api.get("/v1/protegido")).rejects.toBeDefined()

    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(window.location.href).toBe("/login")
  })
})
