import { useState } from "react"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useEstadisticasMountain } from "@/hooks/use-estadisticas"
import { useRutasByMountain } from "@/hooks/use-rutas"
import { useAuthStore } from "@/stores/auth-store"
import { Mountain, CalendarDays, TrendingUp, Route, CheckCircle, Clock, Plus } from "lucide-react"
import type { MountainSummary } from "@/types/mountains"
import { RutaFormDialog } from "@/pages/rutas/ruta-form-dialog"

interface Props {
  open: boolean
  onClose: () => void
  mountain: MountainSummary
}

function formatDate(iso: string) {
  return new Date(iso + "T00:00:00").toLocaleDateString("es-EC", {
    day: "numeric", month: "long", year: "numeric",
  })
}

export function MountainDetailDialog({ open, onClose, mountain }: Props) {
  const { data: stats, isLoading } = useEstadisticasMountain(mountain.id)
  const { data: rutasPage } = useRutasByMountain(mountain.id)
  const rutas = rutasPage?.content ?? []

  const user = useAuthStore((s) => s.user)
  const canEdit = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(user?.rol?.toUpperCase() ?? "")
  const [addRutaOpen, setAddRutaOpen] = useState(false)

  const realizadasPct = stats && stats.totalSalidas > 0
    ? Math.round((stats.salidasRealizadas / stats.totalSalidas) * 100)
    : 0

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Estadísticas de montaña</DialogTitle>
        </DialogHeader>

        {/* Header de la montaña */}
        <div className="flex items-center gap-4">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
            <Mountain className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-foreground">{mountain.nombre}</h2>
            <div className="flex items-center gap-2 mt-1">
              <Badge variant="secondary">{mountain.region}</Badge>
              <Badge variant="outline" className="font-mono">
                {mountain.altitud.toLocaleString()} msnm
              </Badge>
              <Badge variant="outline">{mountain.pais}</Badge>
            </div>
          </div>
        </div>

        {isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-16 animate-pulse rounded-xl bg-muted" />
            ))}
          </div>
        ) : !stats ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center">
            <p className="text-sm text-muted-foreground">No se pudieron cargar las estadísticas</p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* KPIs */}
            <div className="grid grid-cols-3 gap-3">
              <div className="rounded-xl border border-border bg-card p-4 text-center">
                <p className="text-2xl font-bold text-foreground">{stats.totalSalidas}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">Total salidas</p>
              </div>
              <div className="rounded-xl border border-border bg-card p-4 text-center">
                <p className="text-2xl font-bold text-foreground">{stats.salidasRealizadas}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">Realizadas</p>
              </div>
              <div className="rounded-xl border border-border bg-card p-4 text-center">
                <p className="text-2xl font-bold text-foreground">{realizadasPct}%</p>
                <p className="mt-0.5 text-xs text-muted-foreground">Éxito</p>
              </div>
            </div>

            {/* Última salida */}
            <div className="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3">
              <CalendarDays className="h-4 w-4 shrink-0 text-muted-foreground" />
              <div>
                <p className="text-xs text-muted-foreground">Última salida</p>
                <p className="text-sm font-medium text-foreground">
                  {stats.ultimaSalida ? formatDate(stats.ultimaSalida) : "Sin salidas registradas"}
                </p>
              </div>
            </div>

            {/* Rutas */}
            {stats.rutas.length > 0 && (
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Route className="h-4 w-4 text-muted-foreground" />
                  <h3 className="text-sm font-semibold text-foreground">
                    Salidas por ruta
                  </h3>
                </div>
                <ul className="divide-y divide-border rounded-xl border border-border bg-card">
                  {stats.rutas
                    .slice()
                    .sort((a, b) => b.totalSalidas - a.totalSalidas)
                    .map((r) => {
                      const pct = stats.totalSalidas > 0
                        ? Math.round((r.totalSalidas / stats.totalSalidas) * 100)
                        : 0
                      return (
                        <li key={r.rutaId} className="px-4 py-3 space-y-1.5">
                          <div className="flex items-center justify-between gap-2">
                            <span className="text-sm font-medium text-foreground truncate">
                              {r.rutaNombre}
                            </span>
                            <div className="flex items-center gap-2 shrink-0">
                              <TrendingUp className="h-3.5 w-3.5 text-muted-foreground" />
                              <span className="text-sm font-mono text-foreground">
                                {r.totalSalidas}
                              </span>
                              <span className="text-xs text-muted-foreground">({pct}%)</span>
                            </div>
                          </div>
                          {/* Barra de progreso */}
                          <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                            <div
                              className="h-full rounded-full bg-primary transition-all"
                              style={{ width: `${pct}%` }}
                            />
                          </div>
                        </li>
                      )
                    })}
                </ul>
              </div>
            )}

            {stats.rutas.length === 0 && (
              <div className="rounded-xl border border-border bg-card p-8 text-center">
                <Route className="mx-auto mb-2 h-8 w-8 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No hay salidas por ruta registradas</p>
              </div>
            )}

            {/* Rutas registradas */}
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Route className="h-4 w-4 text-muted-foreground" />
                  <h3 className="text-sm font-semibold text-foreground">
                    Rutas registradas {rutas.length > 0 && `(${rutas.length})`}
                  </h3>
                </div>
                {canEdit && (
                  <Button size="sm" variant="outline" className="gap-1.5" onClick={() => setAddRutaOpen(true)}>
                    <Plus className="h-3.5 w-3.5" /> Nueva ruta
                  </Button>
                )}
              </div>
              {rutas.length > 0 && (
                <ul className="divide-y divide-border rounded-xl border border-border bg-card">
                  {rutas.map((r) => (
                    <li key={r.id} className="flex items-center justify-between gap-3 px-4 py-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-foreground">{r.nombre}</p>
                        {r.dificultadResumen && (
                          <p className="mt-0.5 text-xs text-muted-foreground">{r.dificultadResumen}</p>
                        )}
                      </div>
                      {r.aprobada
                        ? <CheckCircle className="h-4 w-4 shrink-0 text-green-500" />
                        : <Clock className="h-4 w-4 shrink-0 text-muted-foreground" />}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}
      </DialogContent>

      <RutaFormDialog
        open={addRutaOpen}
        onClose={() => setAddRutaOpen(false)}
        mode="create"
        initialMountainId={mountain.id}
      />
    </Dialog>
  )
}
