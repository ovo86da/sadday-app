import { useEffect, useState } from "react"
import { AlertTriangle, FileText, RefreshCw } from "lucide-react"
import { useAuthStore } from "@/stores/auth-store"
import { useInformesPendientesJefe } from "@/hooks/use-informes"
import { InformeJefeDialog } from "@/pages/informes/informe-jefe-dialog"
import { Button } from "@/components/ui/button"
import api from "@/lib/api"
import {
  withRefreshLock,
  broadcastRefreshDone,
  broadcastRefreshFailed,
} from "@/lib/auth-broadcast"

interface Props {
  children: React.ReactNode
}

const CINCO_MIN_MS = 5 * 60 * 1000

export function InformePendienteGuard({ children }: Props) {
  const { isAuthenticated } = useAuthStore()
  const [dialogOpen, setDialogOpen]     = useState(false)
  const [refreshing, setRefreshing]     = useState(false)
  const [sessionError, setSessionError] = useState(false)

  const { data: pendientes, isLoading, refetch } = useInformesPendientesJefe(isAuthenticated)

  useEffect(() => {
    if (isAuthenticated) {
      refetch()
      // Limpiar error de sesión al re-autenticarse
      setSessionError(false)
    }
  }, [isAuthenticated, refetch])

  const primero   = pendientes?.[0] ?? null
  const bloqueado = isAuthenticated && !isLoading && (pendientes?.length ?? 0) > 0

  function handleDialogClose() {
    setDialogOpen(false)
  }

  async function handleAbrirInforme() {
    const { tokenExpiresAt } = useAuthStore.getState()
    const expiraPronto = tokenExpiresAt === null || tokenExpiresAt - Date.now() < CINCO_MIN_MS

    if (!expiraPronto) {
      setDialogOpen(true)
      return
    }

    // Token expirado o a punto de expirar — refrescar antes de abrir el formulario
    setRefreshing(true)
    setSessionError(false)
    try {
      await withRefreshLock(async () => {
        const { data } = await api.post("/v1/auth/refresh")
        const d = data.data
        const user = {
          socioId:            d.socioId,
          username:           d.username,
          nombre:             d.nombre,
          rol:                d.rol,
          nivelTecnico:       d.nivelTecnico       ?? null,
          inhabilitado:       d.inhabilitado       ?? false,
          esJefeMontana:      d.esJefeMontana      ?? false,
          esPresidenta:       d.esPresidenta       ?? false,
          esJefeSalidaActivo: d.esJefeSalidaActivo ?? false,
        }
        useAuthStore.getState().setAuth({ accessToken: d.accessToken, user })
        broadcastRefreshDone(d.accessToken, user)
      })
      setDialogOpen(true)
    } catch {
      broadcastRefreshFailed()
      useAuthStore.getState().clearAuth()
      setSessionError(true)
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <>
      {children}

      {bloqueado && primero && (
        <>
          {/* Superposición que bloquea toda interacción con la app */}
          <div className="fixed inset-0 z-40 bg-background/80 backdrop-blur-sm" />

          <div className="fixed inset-0 z-40 flex items-center justify-center p-4 pointer-events-none">
            <div className="bg-card border border-border rounded-xl shadow-2xl max-w-md w-full p-6 space-y-5 pointer-events-auto">
              <div className="flex items-start gap-3">
                <div className="rounded-full bg-amber-500/15 p-2 shrink-0">
                  <AlertTriangle className="h-5 w-5 text-amber-500" />
                </div>
                <div className="space-y-1">
                  <h2 className="text-base font-semibold text-foreground leading-tight">
                    Tienes un informe de salida pendiente
                  </h2>
                  <p className="text-sm text-muted-foreground">
                    Eres Jefe de Salida de{" "}
                    <strong className="text-foreground">{primero.salidaNombre}</strong>.
                    Debes completar el informe antes de continuar.
                  </p>
                </div>
              </div>

              {pendientes!.length > 1 && (
                <p className="text-xs text-muted-foreground border-l-2 border-amber-500/40 pl-3">
                  Tienes {pendientes!.length} informes pendientes en total. Completa uno a la vez.
                </p>
              )}

              {sessionError ? (
                <div className="space-y-3">
                  <p className="text-sm text-destructive">
                    Tu sesión expiró. Inicia sesión nuevamente para completar el informe.
                  </p>
                  <Button
                    variant="outline"
                    className="w-full"
                    onClick={() => { window.location.href = "/login" }}
                  >
                    Ir a iniciar sesión
                  </Button>
                </div>
              ) : (
                <Button
                  className="w-full gap-2"
                  disabled={refreshing}
                  onClick={handleAbrirInforme}
                >
                  {refreshing ? (
                    <>
                      <RefreshCw className="h-4 w-4 animate-spin" />
                      Verificando sesión…
                    </>
                  ) : (
                    <>
                      <FileText className="h-4 w-4" />
                      Llenar informe ahora
                    </>
                  )}
                </Button>
              )}
            </div>
          </div>

          <InformeJefeDialog
            open={dialogOpen}
            onClose={handleDialogClose}
            salidaId={primero.salidaId}
          />
        </>
      )}
    </>
  )
}
