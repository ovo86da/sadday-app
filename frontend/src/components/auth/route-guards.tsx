import { Navigate, useLocation } from "react-router"
import { useAuthStore } from "@/stores/auth-store"
import type { ReactNode } from "react"

interface PrivateRouteProps {
  children: ReactNode
}

/**
 * Protege rutas que requieren autenticación.
 * Redirige a /login si no hay sesión activa.
 */
export function PrivateRoute({ children }: PrivateRouteProps) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}

interface RoleRouteProps {
  children: ReactNode
  allowedRoles: string[]
}

/**
 * Protege rutas que requieren un rol específico.
 * Redirige a /403 si el rol del usuario no está en la lista.
 */
export function RoleRoute({ children, allowedRoles }: RoleRouteProps) {
  const user = useAuthStore((s) => s.user)

  if (!user || !allowedRoles.includes(user.rol.toUpperCase())) {
    return <Navigate to="/403" replace />
  }

  if (user.inhabilitado) {
    return <Navigate to="/403" replace />
  }

  return <>{children}</>
}
