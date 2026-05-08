import { describe, it, expect, beforeEach } from "vitest"
import { render, screen } from "@testing-library/react"
import { MemoryRouter, Routes, Route } from "react-router"
import { PrivateRoute, RoleRoute } from "@/components/auth/route-guards"
import { useAuthStore } from "@/stores/auth-store"

const mockUser = {
  socioId: "uuid-1234",
  username: "juan.perez",
  nombre: "Juan Pérez",
  rol: "SOCIO",
  nivelTecnico: null,
}

function renderInRouter(
  element: React.ReactNode,
  initialPath = "/"
) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/" element={element} />
        <Route path="/login" element={<div>Página de Login</div>} />
        <Route path="/403" element={<div>Página 403</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe("PrivateRoute", () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  it("redirige a /login si no está autenticado", () => {
    renderInRouter(
      <PrivateRoute>
        <div>Contenido protegido</div>
      </PrivateRoute>
    )
    expect(screen.getByText("Página de Login")).toBeInTheDocument()
    expect(screen.queryByText("Contenido protegido")).not.toBeInTheDocument()
  })

  it("muestra el contenido si está autenticado", () => {
    useAuthStore.getState().setAuth({ accessToken: "token", user: mockUser })

    renderInRouter(
      <PrivateRoute>
        <div>Contenido protegido</div>
      </PrivateRoute>
    )
    expect(screen.getByText("Contenido protegido")).toBeInTheDocument()
    expect(screen.queryByText("Página de Login")).not.toBeInTheDocument()
  })
})

describe("RoleRoute", () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  it("redirige a /403 si no hay usuario autenticado", () => {
    renderInRouter(
      <RoleRoute allowedRoles={["ADMIN"]}>
        <div>Solo Admin</div>
      </RoleRoute>
    )
    expect(screen.getByText("Página 403")).toBeInTheDocument()
    expect(screen.queryByText("Solo Admin")).not.toBeInTheDocument()
  })

  it("redirige a /403 si el rol no está permitido", () => {
    useAuthStore.getState().setAuth({ accessToken: "token", user: { ...mockUser, rol: "SOCIO" } })

    renderInRouter(
      <RoleRoute allowedRoles={["ADMIN", "SECRETARIA"]}>
        <div>Solo Admin/Secretaria</div>
      </RoleRoute>
    )
    expect(screen.getByText("Página 403")).toBeInTheDocument()
    expect(screen.queryByText("Solo Admin/Secretaria")).not.toBeInTheDocument()
  })

  it("muestra el contenido si el rol está en la lista", () => {
    useAuthStore.getState().setAuth({ accessToken: "token", user: { ...mockUser, rol: "ADMIN" } })

    renderInRouter(
      <RoleRoute allowedRoles={["ADMIN", "SECRETARIA"]}>
        <div>Solo Admin/Secretaria</div>
      </RoleRoute>
    )
    expect(screen.getByText("Solo Admin/Secretaria")).toBeInTheDocument()
  })

  it("la comparación de rol es case-insensitive", () => {
    // El store guarda el rol en mayúsculas, pero el guard usa toUpperCase() por seguridad
    useAuthStore.getState().setAuth({ accessToken: "token", user: { ...mockUser, rol: "admin" } })

    renderInRouter(
      <RoleRoute allowedRoles={["ADMIN"]}>
        <div>Contenido Admin</div>
      </RoleRoute>
    )
    expect(screen.getByText("Contenido Admin")).toBeInTheDocument()
  })

  it("permite acceso a DIRECTIVO cuando está en la lista", () => {
    useAuthStore.getState().setAuth({ accessToken: "token", user: { ...mockUser, rol: "DIRECTIVO" } })

    renderInRouter(
      <RoleRoute allowedRoles={["ADMIN", "SECRETARIA", "DIRECTIVO"]}>
        <div>Panel admin</div>
      </RoleRoute>
    )
    expect(screen.getByText("Panel admin")).toBeInTheDocument()
  })
})
