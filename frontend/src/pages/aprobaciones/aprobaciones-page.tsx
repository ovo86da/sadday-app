import { useState } from "react"
import { useNavigate } from "react-router"
import { CheckCircle2, XCircle, AlertTriangle, ArrowRight, Mountain } from "lucide-react"
import { toast } from "sonner"

import { useAprobacionesPendientes, useDecidirRiesgo, useAlertasSinJefe } from "@/hooks/use-salidas"
import { useHistorialSocio, type SocioHistorialResponse } from "@/hooks/use-estadisticas"
import { useAuthStore } from "@/stores/auth-store"
import type { AprobacionPendiente } from "@/types/salidas"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"

function formatFecha(isoDate: string): string {
  const [year, month, day] = isoDate.split("-")
  const months = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
  return `${parseInt(day)} ${months[parseInt(month) - 1]} ${year}`
}

// ─── SocioHistorialPanel ──────────────────────────────────────────────────────

function formatFechaHistorial(iso: string) {
  const [year, month, day] = iso.split("-")
  const months = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
  return `${parseInt(day)} ${months[parseInt(month) - 1]} ${year}`
}

function SocioHistorialPanel({ socioId }: { socioId: string }) {
  const { data, isLoading } = useHistorialSocio(socioId)

  if (isLoading) {
    return <p className="text-sm text-muted-foreground py-6 text-center">Cargando historial…</p>
  }
  if (!data) {
    return <p className="text-sm text-muted-foreground py-6 text-center">Sin datos disponibles.</p>
  }

  return <HistorialContent data={data} />
}

function HistorialContent({ data }: { data: SocioHistorialResponse }) {
  return (
    <div className="space-y-4">
      <div className="flex gap-6 text-sm">
        <div className="flex flex-col gap-0.5">
          <span className="text-xs text-muted-foreground">Total salidas</span>
          <span className="text-2xl font-bold tabular-nums">{data.totalParticipaciones}</span>
        </div>
        <div className="flex flex-col gap-0.5">
          <span className="text-xs text-muted-foreground">Cumbres logradas</span>
          <span className="text-2xl font-bold tabular-nums text-green-600">{data.totalCumbresLogradas}</span>
        </div>
      </div>

      {data.historial.length === 0 ? (
        <p className="text-sm text-muted-foreground text-center py-4">Aún no hay salidas registradas.</p>
      ) : (
        <div className="max-h-[55vh] overflow-auto rounded-md border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Montaña</TableHead>
                <TableHead className="text-right">Altitud</TableHead>
                <TableHead>Ruta</TableHead>
                <TableHead>Fecha</TableHead>
                <TableHead className="text-center">Cumbre</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.historial.map((h) => (
                <TableRow key={h.participanteId}>
                  <TableCell className="font-medium">{h.mountainNombre}</TableCell>
                  <TableCell className="text-right tabular-nums text-muted-foreground text-xs">
                    {h.mountainAltitud > 0 ? `${h.mountainAltitud} m` : "—"}
                  </TableCell>
                  <TableCell className="text-xs">{h.rutaNombre}</TableCell>
                  <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                    {formatFechaHistorial(h.fecha)}
                  </TableCell>
                  <TableCell className="text-center">
                    {h.seRealizo === true ? (
                      <CheckCircle2 className="h-4 w-4 text-green-600 mx-auto" />
                    ) : h.seRealizo === false ? (
                      <XCircle className="h-4 w-4 text-muted-foreground mx-auto" />
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AprobacionesPage() {
  const { data: aprobaciones, isLoading, isError } = useAprobacionesPendientes()
  const user = useAuthStore((s) => s.user)
  const navigate = useNavigate()
  const [historialSocio, setHistorialSocio] = useState<{ id: string; nombre: string } | null>(null)

  const esPrivilegiado = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(user?.rol?.toUpperCase() ?? "")
  const esDirectivoOAdmin = ["ADMIN", "DIRECTIVO"].includes(user?.rol?.toUpperCase() ?? "")
  const { data: alertasSinJefe } = useAlertasSinJefe(esPrivilegiado)

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20 text-muted-foreground text-sm">
        Cargando notificaciones…
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex items-center justify-center py-20 text-destructive text-sm">
        Error al cargar las notificaciones.
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-4xl space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Notificaciones</h1>
        <p className="text-muted-foreground text-sm mt-1">
          Aprobaciones pendientes y alertas que requieren atención.
        </p>
      </div>

      {/* ── Alertas: salidas sin jefe ── */}
      {esPrivilegiado && (alertasSinJefe?.length ?? 0) > 0 && (
        <div className="space-y-3">
          <h2 className="text-base font-semibold text-foreground flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-red-500" />
            Salidas sin Jefe de Salida
          </h2>
          {alertasSinJefe!.map((a) => (
            <div
              key={a.salidaId}
              className="rounded-lg border border-red-300/60 bg-red-50/40 p-4 space-y-1"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold text-base">{a.salidaNombre}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{formatFecha(a.fechaSalida)}</p>
                </div>
                <Button
                  size="sm"
                  variant="ghost"
                  className="h-7 gap-1 px-2 text-xs text-muted-foreground shrink-0"
                  onClick={() => navigate(`/salidas?id=${a.salidaId}`)}
                >
                  Ver salida
                  <ArrowRight className="h-3.5 w-3.5" />
                </Button>
              </div>
              <p className="text-sm text-red-700">
                <span className="font-medium">{a.jefeAbandonoNombre}</span>, que estaba asignado
                como Jefe de Salida, se retiró de la salida. Por favor asigna un nuevo Jefe de Salida.
              </p>
            </div>
          ))}
        </div>
      )}

      {/* ── Aprobaciones pendientes ── */}
      <div className="space-y-3">
        <h2 className="text-base font-semibold text-foreground flex items-center gap-2">
          <CheckCircle2 className="h-4 w-4 text-amber-500" />
          Aprobaciones pendientes
          {(aprobaciones?.length ?? 0) > 0 && (
            <span className="inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-amber-500 px-1.5 text-xs font-bold text-white">
              {aprobaciones!.length}
            </span>
          )}
        </h2>

        {aprobaciones?.length === 0 ? (
          <div className="rounded-lg border border-dashed p-10 text-center text-muted-foreground text-sm">
            No hay aprobaciones pendientes.
          </div>
        ) : (
          <div className="space-y-3">
            {aprobaciones?.map((a) => (
              <AprobacionCard
                key={`${a.salidaId}-${a.participanteId}`}
                aprobacion={a}
                onVerSalida={() => navigate(`/salidas?id=${a.salidaId}`)}
                onVerHistorial={(id, nombre) => setHistorialSocio({ id, nombre })}
              />
            ))}
          </div>
        )}

        {!esDirectivoOAdmin && aprobaciones?.length === 0 && (alertasSinJefe?.length ?? 0) === 0 && (
          <div className="rounded-lg border border-dashed p-10 text-center text-muted-foreground text-sm">
            No hay notificaciones pendientes.
          </div>
        )}
      </div>

      <Dialog open={historialSocio !== null} onOpenChange={(o) => !o && setHistorialSocio(null)}>
        <DialogContent
          className="w-[calc(100vw-2rem)] sm:max-w-4xl md:max-w-5xl lg:max-w-6xl overflow-hidden"
          onInteractOutside={(e) => e.preventDefault()}
        >
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Mountain className="h-5 w-5 text-primary" />
              Historial de {historialSocio?.nombre}
            </DialogTitle>
          </DialogHeader>
          {historialSocio && <SocioHistorialPanel socioId={historialSocio.id} />}
        </DialogContent>
      </Dialog>
    </div>
  )
}

interface AprobacionCardProps {
  aprobacion: AprobacionPendiente
  onVerSalida: () => void
  onVerHistorial: (socioId: string, nombre: string) => void
}

function AprobacionCard({ aprobacion: a, onVerSalida, onVerHistorial }: AprobacionCardProps) {
  const decidirMutation = useDecidirRiesgo(a.salidaId)
  const [decisionOpen, setDecisionOpen] = useState(false)
  const [decidirAprobar, setDecidirAprobar] = useState(true)
  const [motivo, setMotivo] = useState("")

  const handleConfirmar = async () => {
    if (!motivo.trim()) return
    try {
      await decidirMutation.mutateAsync({
        participanteId: a.participanteId,
        aprobar: decidirAprobar,
        motivo: motivo.trim(),
      })
      toast.success(
        decidirAprobar
          ? `Aprobación registrada para ${a.socioNombre} ${a.socioApellido}`
          : `Inscripción de ${a.socioNombre} ${a.socioApellido} marcada como negada`,
      )
      setDecisionOpen(false)
      setMotivo("")
    } catch (error) { console.error(error);
      toast.error("Error al registrar la decisión")
    }
  }

  return (
    <div className="rounded-lg border border-amber-300/60 bg-card p-4 space-y-3">
      {/* Cabecera: salida + fecha + enlace */}
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-base">{a.salidaNombre}</p>
          <p className="text-xs text-muted-foreground mt-0.5">{formatFecha(a.fechaSalida)}</p>
          {a.fechaSalida > new Date().toISOString().slice(0, 10) && (
            <p className="text-xs text-red-500 font-medium mt-0.5">Pendiente</p>
          )}
        </div>
        <Button
          size="sm"
          variant="ghost"
          className="h-7 gap-1 px-2 text-xs text-muted-foreground shrink-0"
          onClick={onVerSalida}
        >
          Ver salida
          <ArrowRight className="h-3.5 w-3.5" />
        </Button>
      </div>

      {/* Datos del socio */}
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <button
          className="font-medium text-primary underline-offset-2 hover:underline cursor-pointer"
          onClick={() => onVerHistorial(a.socioId, `${a.socioApellido}, ${a.socioNombre}`)}
        >
          {a.socioApellido}, {a.socioNombre}
        </button>
        <div className="flex items-center gap-1.5">
          <Badge variant="outline" className="text-xs">
            Nivel: {a.nivelSocioNombre ?? "sin nivel"}
          </Badge>
          <AlertTriangle className="h-3.5 w-3.5 text-amber-500" />
          <Badge variant="outline" className="text-xs border-amber-400 text-amber-700">
            Mínimo: {a.nivelMinimoNombre ?? "—"}
          </Badge>
        </div>
      </div>

      {/* Estado de aprobaciones */}
      <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
        <span className="flex items-center gap-1">
          {a.aprobadoPorDirectivo ? (
            <CheckCircle2 className="h-3.5 w-3.5 text-green-600" />
          ) : (
            <AlertTriangle className="h-3.5 w-3.5 text-amber-500" />
          )}
          Jefe de Montaña {a.aprobadoPorDirectivo ? "(aprobado)" : "(pendiente)"}
        </span>
        <span className="flex items-center gap-1">
          {a.aprobadoPorJefe ? (
            <CheckCircle2 className="h-3.5 w-3.5 text-green-600" />
          ) : (
            <AlertTriangle className="h-3.5 w-3.5 text-amber-500" />
          )}
          Jefe de Salida {a.aprobadoPorJefe ? "(aprobado)" : "(pendiente)"}
        </span>
      </div>

      {/* Formulario de decisión */}
      {!decisionOpen ? (
        <Button
          size="sm"
          variant="outline"
          className="h-8 gap-1 px-3 text-xs border-amber-400 text-amber-700 hover:bg-amber-50"
          onClick={() => setDecisionOpen(true)}
        >
          Decidir
        </Button>
      ) : (
        <div className="space-y-2 rounded-md border border-amber-200 bg-amber-50/40 p-3">
          <div className="flex gap-2">
            <Button
              size="sm"
              variant={decidirAprobar ? "default" : "outline"}
              className={`h-7 gap-1 px-3 text-xs ${decidirAprobar ? "bg-green-600 hover:bg-green-700 text-white" : ""}`}
              onClick={() => setDecidirAprobar(true)}
            >
              <CheckCircle2 className="h-3.5 w-3.5" />
              Aprobar
            </Button>
            <Button
              size="sm"
              variant={!decidirAprobar ? "default" : "outline"}
              className={`h-7 gap-1 px-3 text-xs ${!decidirAprobar ? "bg-red-600 hover:bg-red-700 text-white" : ""}`}
              onClick={() => setDecidirAprobar(false)}
            >
              <XCircle className="h-3.5 w-3.5" />
              Negar
            </Button>
          </div>

          <textarea
            className="w-full rounded-md border border-amber-300 bg-background text-foreground px-2 py-1.5 text-xs placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-amber-400 resize-none"
            rows={2}
            placeholder="Motivo (obligatorio, máx. 500 caracteres)"
            maxLength={500}
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
          />

          <div className="flex gap-2">
            <Button
              size="sm"
              className={`h-7 gap-1 px-3 text-xs text-white ${decidirAprobar ? "bg-green-600 hover:bg-green-700" : "bg-red-600 hover:bg-red-700"}`}
              disabled={decidirMutation.isPending || motivo.trim().length === 0}
              onClick={handleConfirmar}
            >
              Confirmar
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="h-7 px-3 text-xs"
              onClick={() => {
                setDecisionOpen(false)
                setMotivo("")
              }}
            >
              Cancelar
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
