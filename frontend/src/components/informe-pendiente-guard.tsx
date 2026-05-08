import { useEffect, useState } from "react"
import { AlertTriangle, FileText } from "lucide-react"
import { useAuthStore } from "@/stores/auth-store"
import { useInformesPendientesJefe } from "@/hooks/use-informes"
import { InformeJefeDialog } from "@/pages/informes/informe-jefe-dialog"
import { Button } from "@/components/ui/button"

interface Props {
  children: React.ReactNode
}

export function InformePendienteGuard({ children }: Props) {
  const { isAuthenticated } = useAuthStore()
  const [dialogOpen, setDialogOpen] = useState(false)

  const { data: pendientes, isLoading, refetch } = useInformesPendientesJefe(isAuthenticated)

  // React 18 batches setAuth() + navigate() in the same flush, so the
  // enabled:false→true transition may not trigger a fetch during login.
  // This effect guarantees a fresh fetch on every authentication event.
  useEffect(() => {
    if (isAuthenticated) {
      refetch()
    }
  }, [isAuthenticated, refetch])

  const primero = pendientes?.[0] ?? null
  const bloqueado = isAuthenticated && !isLoading && (pendientes?.length ?? 0) > 0

  function handleDialogClose() {
    setDialogOpen(false)
    // El hook se invalida automáticamente en useCreateInforme.
    // Si el usuario cierra sin guardar, la superposición permanece.
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

              <Button className="w-full gap-2" onClick={() => setDialogOpen(true)}>
                <FileText className="h-4 w-4" />
                Llenar informe ahora
              </Button>
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
