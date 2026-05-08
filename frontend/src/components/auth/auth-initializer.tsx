import { useEffect, useState, type ReactNode } from "react"
import { useAuthStore } from "@/stores/auth-store"
import {
  acquireRefreshLock,
  releaseRefreshLock,
  broadcastRefreshDone,
  broadcastRefreshFailed,
  waitForRefreshResult,
} from "@/lib/auth-broadcast"
import api from "@/lib/api"

interface Props {
  children: ReactNode
}

/**
 * Intenta restaurar la sesión al montar la app.
 *
 * Como el access token vive solo en memoria, al recargar la página se pierde.
 * Este componente llama a POST /auth/refresh (la cookie HttpOnly se envía
 * automáticamente) para obtener un nuevo access token.
 *
 * Coordinación cross-tab: si dos tabs cargan simultáneamente con el mismo
 * refresh token, solo la que adquiere el lock hace la petición. La otra
 * espera el resultado por BroadcastChannel y actualiza su estado local
 * sin tocar el backend (evita race condition A9).
 *
 * Muestra un spinner mientras se resuelve.
 */
export function AuthInitializer({ children }: Props) {
  const [isLoading, setIsLoading] = useState(true)
  const setAuth = useAuthStore((s) => s.setAuth)

  useEffect(() => {
    const tryRefresh = async () => {
      try {
        if (!acquireRefreshLock()) {
          // Otra tab está refrescando — esperar su resultado
          const result = await waitForRefreshResult()
          if (result) {
            setAuth({ accessToken: result.accessToken, user: result.user })
          }
          return
        }

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
          setAuth({ accessToken: d.accessToken, user })
          broadcastRefreshDone(d.accessToken, user)
        } catch (error) { console.error(error);
          broadcastRefreshFailed()
        } finally {
          releaseRefreshLock()
        }
      } finally {
        setIsLoading(false)
      }
    }

    tryRefresh()
  }, [setAuth])

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-4">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground">Cargando...</p>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
