import { useState, useRef } from "react"
import { useSalidasList } from "@/hooks/use-salidas"
import { useHistorialSocio } from "@/hooks/use-estadisticas"
import { useAuthStore } from "@/stores/auth-store"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useAccesoPorNivel } from "@/hooks/use-mountains"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { toast } from "sonner"
import {
  CalendarDays, Mountain, Search, ChevronLeft, ChevronRight,
  Crown, CheckCircle, XCircle, Clock, FileText, AlertTriangle, Lock,
} from "lucide-react"
import { CategoriaChip } from "@/lib/salida-tipo"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"
import { SalidaDetailDialog } from "@/pages/salidas/salida-detail-dialog"
import { InformeJefeDialog } from "@/pages/informes/informe-jefe-dialog"

// ─── Types ────────────────────────────────────────────────────────────────────

interface ParticipanteResponse {
  id: number
  socioId: string
  estadoInscripcion: string
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Date(iso + "T00:00:00").toLocaleDateString("es-EC", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
  })
}

const ESTADO_INSCRIPCION_LABEL: Record<string, string> = {
  INSCRITO: "Inscrito",
  CONFIRMADO: "Confirmado",
  NO_FUE: "No fue",
  CANCELADO: "Cancelado",
  NEGADO: "Negado",
  PENDIENTE_APROBACION: "Pendiente aprobación",
}

const ESTADO_INSCRIPCION_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  INSCRITO: "secondary",
  CONFIRMADO: "default",
  NO_FUE: "outline",
  CANCELADO: "destructive",
  NEGADO: "destructive",
  PENDIENTE_APROBACION: "outline",
}

/** Devuelve el texto de pendiente según quién falta por aprobar. */
function pendienteMensaje(directivoAprobado: boolean, jefeAprobado: boolean): string {
  if (!directivoAprobado && !jefeAprobado) return "Pendiente aprobación Jefe de Montaña y Jefe de Salida"
  if (!directivoAprobado) return "Pendiente aprobación Jefe de Montaña"
  if (!jefeAprobado) return "Pendiente aprobación Jefe de Salida"
  return "Pendiente aprobación"
}

/** Salidas donde se puede crear/editar informe */
const ESTADOS_INFORME = new Set(["EN_CURSO", "REALIZADA"])

/** True si la fecha (ISO yyyy-MM-dd) ya pasó */
function fechaYaPaso(iso: string): boolean {
  return new Date(iso + "T23:59:59") < new Date()
}

/** El jefe puede hacer informe si el estado es EN_CURSO/REALIZADA, o si es PLANIFICADA pero la fecha ya pasó */
function puedeHacerInforme(estadoSalida: string, fecha: string): boolean {
  return ESTADOS_INFORME.has(estadoSalida) || (estadoSalida === "PLANIFICADA" && fechaYaPaso(fecha))
}

// ─── Tabs exportados (reutilizados también desde SalidasPage staff) ──────────

// ─── Tab: Próximas salidas ────────────────────────────────────────────────────

export function ProximasSalidasTab() {
  const user = useAuthStore((s) => s.user)
  const socioId = user?.socioId ?? ""

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState("")
  const [searchDebounced, setSearchDebounced] = useState("")
  const [detailId, setDetailId] = useState<string | null>(null)
  const [informeSalidaId, setInformeSalidaId] = useState<string | null>(null)
  const [informeHoraEncuentro, setInformeHoraEncuentro] = useState<string | null>(null)
  const qc = useQueryClient()

  const inscribedRef = useRef<Map<string, number>>(new Map())
  const [, forceRender] = useState(0)

  const { data: salidasPage, isLoading } = useSalidasList({
    page,
    size: 12,
    estado: "PLANIFICADA",
    q: searchDebounced || undefined,
    sort: "fechaInicio,asc",
  })

  // Salidas actualmente en curso (sin paginación, pocas a la vez)
  const { data: salidasEnCursoPage } = useSalidasList({
    estado: "EN_CURSO",
    size: 50,
    sort: "fechaInicio,asc",
  })
  const salidasEnCurso = salidasEnCursoPage?.content ?? []

  const { data: historial } = useHistorialSocio(socioId)

  // Mapa salidaId → {participanteId, estadoInscripcion} para salidas activas (no canceladas)
  const inscripcionEnHistorial = new Map(
    historial?.historial
      .filter((h) => h.estadoInscripcion !== "CANCELADO" && h.estadoInscripcion !== "NEGADO")
      .map((h) => [h.salidaId, { participanteId: h.participanteId, estadoInscripcion: h.estadoInscripcion }])
      ?? [],
  )

  // Inscripciones en estado PENDIENTE_APROBACION → para mostrar banner en la tarjeta
  const pendienteEnHistorial = new Map<string, { directivoAprobado: boolean; jefeAprobado: boolean }>(
    historial?.historial
      .filter((h) => h.estadoInscripcion === "PENDIENTE_APROBACION")
      .map((h) => [h.salidaId, { directivoAprobado: h.directivoAprobado, jefeAprobado: h.jefeAprobado }])
      ?? [],
  )

  // Lista de niveles ordenada (menor a mayor) para comparar nivel del socio con mínimo de salida
  const { data: nivelesAcceso } = useAccesoPorNivel()
  const nivelOrdenMap = new Map<string, number>(
    (nivelesAcceso ?? []).map((n, i) => [n.nivelSocioNombre, i]),
  )

  const jefeEnHistorial = new Set(
    historial?.historial
      .filter((h) => h.esJefeSalida && h.estadoInscripcion !== "CANCELADO")
      .map((h) => h.salidaId) ?? [],
  )

  const handleSearch = () => { setSearchDebounced(search); setPage(0) }
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === "Enter") handleSearch() }

  function InscribirButton({
    salidaId,
    llena,
    inscripcionesCerradas,
    nivelMinimoNombre,
    fechaInicio,
  }: {
    salidaId: string
    llena: boolean
    inscripcionesCerradas: boolean
    nivelMinimoNombre: string | null
    fechaInicio: string
  }) {
    const historialEntry = inscripcionEnHistorial.get(salidaId)
    // ID del participante: puede venir de sesión actual o del historial cargado
    const sessionParticipanteId = inscribedRef.current.get(salidaId)
    const participanteId = sessionParticipanteId ?? historialEntry?.participanteId
    const estadoActual = historialEntry?.estadoInscripcion
    const [confirmOpen, setConfirmOpen] = useState(false)

    // Determina si el nivel del socio es insuficiente para esta salida
    const nivelInsuficiente = (() => {
      if (!nivelMinimoNombre) return false
      const userNivel = user?.nivelTecnico
      if (!userNivel) return true // sin nivel → siempre insuficiente
      const userIdx = nivelOrdenMap.get(userNivel) ?? -1
      const minIdx = nivelOrdenMap.get(nivelMinimoNombre) ?? -1
      if (userIdx === -1 || minIdx === -1) return false // no podemos comparar → no bloqueamos
      return userIdx < minIdx
    })()

    const inscribirMutation = useMutation({
      mutationFn: () =>
        api
          .post<ApiResponse<ParticipanteResponse>>(`/v1/salidas/${salidaId}/inscripciones`, { socioId })
          .then((r) => r.data.data),
      onSuccess: (data) => {
        inscribedRef.current.set(salidaId, data.id)
        forceRender((n) => n + 1)
        qc.invalidateQueries({ queryKey: ["estadisticas", "socio", socioId] })
        qc.invalidateQueries({ queryKey: ["salidas", "list"] })
        qc.invalidateQueries({ queryKey: ["aprobaciones-pendientes"] })
        if (data.estadoInscripcion === "PENDIENTE_APROBACION") {
          toast.success("Inscripción registrada — pendiente de aprobación")
        } else {
          toast.success("¡Inscripción realizada!")
        }
      },
      onError: (err: unknown) => {
        const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
        toast.error(msg || "Error al inscribirse")
      },
    })

    const cancelarMutation = useMutation({
      mutationFn: () => api.delete(`/v1/salidas/${salidaId}/inscripciones/${participanteId}`),
      onSuccess: () => {
        inscribedRef.current.delete(salidaId)
        forceRender((n) => n + 1)
        qc.invalidateQueries({ queryKey: ["estadisticas", "socio", socioId] })
        qc.invalidateQueries({ queryKey: ["salidas", "list"] })
        qc.invalidateQueries({ queryKey: ["aprobaciones-pendientes"] })
        toast.success(estadoActual === "PENDIENTE_APROBACION" ? "Solicitud cancelada" : "Inscripción cancelada")
      },
      onError: (err: unknown) => {
        const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
        toast.error(msg || "Error al cancelar")
      },
    })

    // ¿Puede cancelar? PENDIENTE siempre; INSCRITO solo si faltan > 48h; ninguno si inscripciones cerradas
    const puedeCancel = (() => {
      if (!participanteId) return false
      if (inscripcionesCerradas) return false
      if (estadoActual === "PENDIENTE_APROBACION") return true
      if (estadoActual === "INSCRITO") {
        const cutoff = new Date(fechaInicio + "T00:00:00").getTime() - 48 * 3600 * 1000
        return Date.now() < cutoff
      }
      return false
    })()

    const handleInscribirse = () => {
      if (nivelInsuficiente) {
        setConfirmOpen(true)
      } else {
        inscribirMutation.mutate()
      }
    }

    // Ya inscrito o pendiente desde historial previo
    if (historialEntry && !sessionParticipanteId) {
      if (estadoActual === "PENDIENTE_APROBACION") {
        return (
          <Button
            size="sm"
            variant="outline"
            onClick={() => cancelarMutation.mutate()}
            disabled={cancelarMutation.isPending}
            className="gap-1.5 text-amber-700 border-amber-400 hover:bg-amber-50"
          >
            <XCircle className="h-3.5 w-3.5" />
            {cancelarMutation.isPending ? "Cancelando..." : "Cancelar solicitud"}
          </Button>
        )
      }
      if (estadoActual === "INSCRITO") {
        if (puedeCancel) {
          return (
            <Button
              size="sm"
              variant="outline"
              onClick={() => cancelarMutation.mutate()}
              disabled={cancelarMutation.isPending}
              className="gap-1.5 text-destructive border-destructive hover:bg-destructive/10"
            >
              <XCircle className="h-3.5 w-3.5" />
              {cancelarMutation.isPending ? "Cancelando..." : "Cancelar inscripción"}
            </Button>
          )
        }
        return (
          <span className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground">
            <CheckCircle className="h-3.5 w-3.5 text-primary" /> Inscrito
          </span>
        )
      }
    }

    // Recién inscrito en la sesión actual
    if (sessionParticipanteId !== undefined) {
      return puedeCancel ? (
        <Button
          size="sm"
          variant="outline"
          onClick={() => cancelarMutation.mutate()}
          disabled={cancelarMutation.isPending}
          className="gap-1.5 text-destructive border-destructive hover:bg-destructive/10"
        >
          <XCircle className="h-3.5 w-3.5" />
          {cancelarMutation.isPending ? "Cancelando..." : "Cancelar inscripción"}
        </Button>
      ) : (
        <span className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground">
          <CheckCircle className="h-3.5 w-3.5 text-primary" /> Inscrito
        </span>
      )
    }

    return (
      <>
        <Button
          size="sm"
          onClick={handleInscribirse}
          disabled={inscribirMutation.isPending || llena || inscripcionesCerradas}
          className="gap-1.5"
        >
          {inscribirMutation.isPending ? (
            <span className="flex items-center gap-1.5">
              <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
              Inscribiendo...
            </span>
          ) : inscripcionesCerradas ? (
            <><Lock className="h-3.5 w-3.5" /> Cerrado</>
          ) : llena ? (
            "Sin cupos"
          ) : (
            "Inscribirse"
          )}
        </Button>

        {/* Diálogo de confirmación cuando el nivel es insuficiente */}
        {confirmOpen && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
            onClick={() => setConfirmOpen(false)}
          >
            <div
              className="w-full max-w-md rounded-xl bg-card border shadow-xl p-6 space-y-4"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-start gap-3">
                <div className="rounded-full bg-amber-100 p-2 shrink-0">
                  <AlertTriangle className="h-5 w-5 text-amber-600" />
                </div>
                <div>
                  <h3 className="font-semibold text-base">Requisitos no cumplidos</h3>
                  <p className="text-sm text-muted-foreground mt-1">
                    Usted no cumple con los requisitos técnicos y/o físicos para esta salida
                    {nivelMinimoNombre ? ` (nivel mínimo: ${nivelMinimoNombre})` : ""}.
                  </p>
                </div>
              </div>
              <p className="text-sm text-foreground">
                ¿Desea continuar y solicitar que el <strong>Jefe de Salida</strong> y el{" "}
                <strong>Jefe de Montaña</strong> validen y autoricen su inscripción?
              </p>
              <div className="flex gap-3 pt-1">
                <Button
                  className="flex-1"
                  disabled={inscribirMutation.isPending}
                  onClick={() => {
                    setConfirmOpen(false)
                    inscribirMutation.mutate()
                  }}
                >
                  {inscribirMutation.isPending ? "Inscribiendo..." : "Continuar y solicitar aprobación"}
                </Button>
                <Button
                  variant="outline"
                  className="flex-1"
                  onClick={() => setConfirmOpen(false)}
                >
                  Volver atrás
                </Button>
              </div>
            </div>
          </div>
        )}
      </>
    )
  }

  return (
    <div className="space-y-6">
      {/* ── Salidas en curso ──────────────────────────────────────────────── */}
      {salidasEnCurso.length > 0 && (
        <div className="space-y-3">
          <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <span className="inline-block h-2 w-2 rounded-full bg-green-500 animate-pulse" />
            Salidas en curso ({salidasEnCurso.length})
          </h3>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {salidasEnCurso.map((s) => {
              const esJefe = jefeEnHistorial.has(s.id)
              return (
                <div
                  key={s.id}
                  onClick={() => setDetailId(s.id)}
                  className={`rounded-xl border p-4 flex flex-col gap-3 cursor-pointer transition-colors ${
                    esJefe
                      ? "border-yellow-400/60 bg-yellow-500/5 hover:bg-yellow-500/10"
                      : "border-green-500/30 bg-green-500/5 hover:bg-green-500/10"
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      {esJefe && (
                        <div className="flex items-center gap-1.5 mb-1.5">
                          <Crown className="h-3.5 w-3.5 text-yellow-500 shrink-0" />
                          <span className="text-xs font-semibold text-yellow-600">Jefe de Salida</span>
                        </div>
                      )}
                      <h3 className="font-semibold text-sm text-foreground leading-tight">{s.nombre}</h3>
                      <p className="text-xs text-muted-foreground mt-0.5">{s.rutaNombre}</p>
                      {fechaYaPaso(s.fechaFin) && (
                        s.tieneInforme
                          ? <span className="text-xs font-medium text-green-600 dark:text-green-400">Informe entregado</span>
                          : <span className="text-xs font-medium text-red-600 dark:text-red-400">Informe pendiente</span>
                      )}
                    </div>
                    <Badge variant="default" className="shrink-0 text-xs bg-green-600">En curso</Badge>
                  </div>

                  <div className="text-xs text-muted-foreground flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5 shrink-0" />
                    <span>Encuentro: {s.horaEncuentroClub}</span>
                  </div>

                  {esJefe && (
                    <div onClick={(e) => e.stopPropagation()}>
                      <Button
                        size="sm"
                        className="w-full gap-1.5 bg-yellow-500 hover:bg-yellow-600 text-white"
                        onClick={() => {
                          setInformeSalidaId(s.id)
                          setInformeHoraEncuentro(s.horaEncuentroClub)
                        }}
                      >
                        <FileText className="h-3.5 w-3.5" />
                        Llenar informe de salida
                      </Button>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* ── Próximas salidas (PLANIFICADA) ────────────────────────────────── */}
      <div className="space-y-4">
      <div className="flex gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Buscar salidas..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={handleKeyDown}
            className="pl-9"
          />
        </div>
        <Button variant="outline" onClick={handleSearch}>Buscar</Button>
      </div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-44 animate-pulse rounded-xl bg-muted" />
          ))}
        </div>
      ) : salidasPage?.content.length === 0 ? (
        <div className="rounded-xl border border-border bg-card p-12 text-center">
          <CalendarDays className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
          <p className="text-muted-foreground">No hay salidas planificadas en este momento</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {salidasPage?.content.map((s) => {
            const llena = !!(s.capacidadMaxima && s.totalInscritos >= s.capacidadMaxima)
            const esJefe = jefeEnHistorial.has(s.id)
            const pendiente = pendienteEnHistorial.get(s.id)
            const cerrada = s.inscripcionesCerradas
            return (
              <div
                key={s.id}
                onClick={() => setDetailId(s.id)}
                className={`rounded-xl border p-5 flex flex-col gap-3 cursor-pointer transition-colors ${
                  esJefe
                    ? "border-yellow-400/60 bg-yellow-500/5 hover:bg-yellow-500/10"
                    : pendiente
                      ? "border-amber-400/60 bg-amber-500/5 hover:bg-amber-500/10"
                      : "border-border bg-card hover:bg-accent/50"
                }`}
              >
                {esJefe && (
                  <div className="flex items-center gap-1.5 rounded-lg border border-yellow-400/40 bg-yellow-500/10 px-3 py-1.5 -mt-1">
                    <Crown className="h-4 w-4 shrink-0 text-yellow-500" />
                    <span className="text-xs font-semibold text-yellow-600">Eres Jefe de Salida</span>
                  </div>
                )}
                {pendiente && (
                  <div className="flex items-center gap-1.5 rounded-lg border border-amber-400/40 bg-amber-500/10 px-3 py-1.5 -mt-1">
                    <AlertTriangle className="h-4 w-4 shrink-0 text-amber-500" />
                    <span className="text-xs font-medium text-amber-700">
                      {pendienteMensaje(pendiente.directivoAprobado, pendiente.jefeAprobado)}
                    </span>
                  </div>
                )}
                <div className="flex-1 space-y-1">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="font-semibold text-foreground leading-tight">{s.nombre}</h3>
                      <div className="flex items-center gap-1.5 mt-1 flex-wrap">
                        {s.tipoActividad && <CategoriaChip tipoActividad={s.tipoActividad} />}
                        {s.rutaNombre && (
                          <span className="text-xs text-muted-foreground truncate">{s.rutaNombre}</span>
                        )}
                      </div>
                    </div>
                    {cerrada ? (
                      <Badge variant="destructive" className="shrink-0 text-xs gap-1">
                        <Lock className="h-3 w-3" /> Cerrado
                      </Badge>
                    ) : llena && (
                      <Badge variant="destructive" className="shrink-0 text-xs">Sin cupos</Badge>
                    )}
                  </div>
                  {fechaYaPaso(s.fechaFin) && (
                    s.tieneInforme
                      ? <span className="text-xs font-medium text-green-600 dark:text-green-400">Informe entregado</span>
                      : <span className="text-xs font-medium text-red-600 dark:text-red-400">Informe pendiente</span>
                  )}
                </div>

                <div className="space-y-1.5 text-xs text-muted-foreground">
                  <div className="flex items-center gap-1.5">
                    <CalendarDays className="h-3.5 w-3.5 shrink-0" />
                    <span>{formatDate(s.fechaInicio)}</span>
                  </div>
                  {s.nivelMinimoNombre && (
                    <div className="flex items-center gap-1.5">
                      <Mountain className="h-3.5 w-3.5 shrink-0" />
                      <span>Nivel mín: {s.nivelMinimoNombre}</span>
                    </div>
                  )}
                  <div className="flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5 shrink-0" />
                    <span>Encuentro: {s.horaEncuentroClub}</span>
                  </div>
                </div>

                {esJefe && fechaYaPaso(s.fechaFin) && (
                  <div onClick={(e) => e.stopPropagation()}>
                    <Button
                      size="sm"
                      className="w-full gap-1.5 bg-yellow-500 hover:bg-yellow-600 text-white"
                      onClick={() => {
                        setInformeSalidaId(s.id)
                        setInformeHoraEncuentro(s.horaEncuentroClub)
                      }}
                    >
                      <FileText className="h-3.5 w-3.5" />
                      Llenar informe de salida
                    </Button>
                  </div>
                )}

                <div
                  className="flex items-center justify-between pt-1 border-t border-border"
                  onClick={(e) => e.stopPropagation()}
                >
                  <span className="text-xs text-muted-foreground">
                    {s.totalInscritos}{s.capacidadMaxima ? `/${s.capacidadMaxima}` : ""} inscritos
                  </span>
                  <InscribirButton salidaId={s.id} llena={llena} inscripcionesCerradas={cerrada} nivelMinimoNombre={s.nivelMinimoNombre} fechaInicio={s.fechaInicio} />
                </div>
              </div>
            )
          })}
        </div>
      )}

      {salidasPage && salidasPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Página {salidasPage.page.number + 1} de {salidasPage.page.totalPages}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={salidasPage.page.number === 0} onClick={() => setPage((p) => p - 1)}>
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button variant="outline" size="sm" disabled={salidasPage.page.number >= salidasPage.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {detailId && (
        <SalidaDetailDialog
          open={!!detailId}
          onClose={() => setDetailId(null)}
          salidaId={detailId}
        />
      )}
      </div>{/* fin space-y-4 próximas */}

      {informeSalidaId && (
        <InformeJefeDialog
          open={!!informeSalidaId}
          onClose={() => setInformeSalidaId(null)}
          salidaId={informeSalidaId}
          horaEncuentroClub={informeHoraEncuentro}
        />
      )}
    </div>
  )
}

// ─── Tab: Mis salidas ─────────────────────────────────────────────────────────

export function MisSalidasTab() {
  const user = useAuthStore((s) => s.user)
  const { data, isLoading, isError } = useHistorialSocio(user?.socioId)

  const [informeSalidaId, setInformeSalidaId] = useState<string | null>(null)
  const [informeHoraEncuentro, setInformeHoraEncuentro] = useState<string | null>(null)

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="h-16 animate-pulse rounded-xl bg-muted" />
        ))}
      </div>
    )
  }

  if (isError || !data) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        Error al cargar el historial
      </p>
    )
  }

  const historial = data.historial

  return (
    <div className="space-y-4">
      {/* Resumen */}
      <div className="grid grid-cols-3 gap-4">
        {[
          { label: "Participaciones", value: data.totalParticipaciones },
          { label: "Cumbres logradas", value: data.totalCumbresLogradas },
          { label: "Veces jefe", value: data.vecesJefeSalida },
        ].map(({ label, value }) => (
          <div key={label} className="rounded-xl border border-border bg-card p-4 text-center">
            <p className="text-2xl font-bold text-foreground">{value}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">{label}</p>
          </div>
        ))}
      </div>

      {historial.length === 0 ? (
        <div className="rounded-xl border border-border bg-card p-12 text-center">
          <Mountain className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
          <p className="text-muted-foreground">Aún no tienes salidas registradas</p>
        </div>
      ) : (
        <div className="space-y-2">
          <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
            Historial ({historial.length})
          </h3>
          <ul className="divide-y divide-border rounded-xl border border-border bg-card">
            {historial.map((h) => {
              const puedeInforme =
                h.esJefeSalida && puedeHacerInforme(h.estadoSalida ?? "", h.fecha)

              return (
                <li key={h.salidaId} className="flex items-start justify-between gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      {h.esJefeSalida && (
                        <Crown className="h-3.5 w-3.5 shrink-0 text-yellow-500" />
                      )}
                      <p className="truncate text-sm font-medium text-foreground">{h.salidaNombre}</p>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {formatDate(h.fecha)} · {h.mountainNombre} ({h.mountainAltitud} m)
                    </p>
                    {Array.isArray(h.dignidades) && h.dignidades.length > 0 && (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {h.dignidades.map((d, i) => (
                          <Badge key={i} variant="outline" className="text-xs">
                            {typeof d === "string" ? d : (d as { dignidadNombre: string }).dignidadNombre}
                          </Badge>
                        ))}
                      </div>
                    )}

                    {/* Botón informe junto al badge de jefe */}
                    {puedeInforme && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="mt-2 gap-1.5 h-7 text-xs border-yellow-400/60 text-yellow-700 hover:bg-yellow-500/10"
                        onClick={() => {
                          setInformeSalidaId(h.salidaId)
                          setInformeHoraEncuentro(h.horaEncuentroClub ?? null)
                        }}
                      >
                        <FileText className="h-3.5 w-3.5" />
                        {h.seRealizo !== null ? "Ver / Editar informe" : "Crear informe"}
                      </Button>
                    )}
                  </div>

                  <div className="shrink-0 text-right space-y-1">
                    <Badge variant={ESTADO_INSCRIPCION_VARIANT[h.estadoInscripcion] ?? "outline"}>
                      {ESTADO_INSCRIPCION_LABEL[h.estadoInscripcion] ?? h.estadoInscripcion}
                    </Badge>
                    {h.estadoInscripcion === "PENDIENTE_APROBACION" && (
                      <p className="text-xs text-amber-600 max-w-[180px] text-right">
                        {pendienteMensaje(h.directivoAprobado, h.jefeAprobado)}
                      </p>
                    )}
                    {h.seRealizo !== null && (
                      <p className="text-xs text-muted-foreground">
                        {h.seRealizo ? "Realizada ✓" : "No realizada"}
                      </p>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        </div>
      )}

      {informeSalidaId && (
        <InformeJefeDialog
          open={!!informeSalidaId}
          onClose={() => setInformeSalidaId(null)}
          salidaId={informeSalidaId}
          horaEncuentroClub={informeHoraEncuentro}
        />
      )}
    </div>
  )
}

// ─── Tab: Salidas anteriores ──────────────────────────────────────────────────

export function SalidasAnterioresTab() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState("")
  const [searchDebounced, setSearchDebounced] = useState("")
  const [detailId, setDetailId] = useState<string | null>(null)

  const { data: salidasPage, isLoading } = useSalidasList({
    page,
    size: 12,
    estado: "REALIZADA",
    q: searchDebounced || undefined,
    sort: "fechaInicio,desc",
  })

  const handleSearch = () => { setSearchDebounced(search); setPage(0) }
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === "Enter") handleSearch() }

  const formatDateShort = (iso: string) =>
    new Date(iso + "T00:00:00").toLocaleDateString("es-EC", { day: "numeric", month: "short", year: "numeric" })

  return (
    <div className="space-y-4">
      <div className="flex gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Buscar salidas..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={handleKeyDown}
            className="pl-9"
          />
        </div>
        <Button variant="outline" onClick={handleSearch}>Buscar</Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-16 animate-pulse rounded-xl bg-muted" />
          ))}
        </div>
      ) : salidasPage?.content.length === 0 ? (
        <div className="rounded-xl border border-border bg-card p-12 text-center">
          <Mountain className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
          <p className="text-muted-foreground">No se encontraron salidas anteriores</p>
        </div>
      ) : (
        <ul className="divide-y divide-border rounded-xl border border-border bg-card">
          {salidasPage?.content.map((s) => (
            <li
              key={s.id}
              className="flex items-center justify-between gap-3 px-4 py-3 cursor-pointer hover:bg-accent/50 transition-colors"
              onClick={() => setDetailId(s.id)}
            >
              <div className="min-w-0 flex-1">
                <p className="font-medium text-sm text-foreground truncate">{s.nombre}</p>
                <p className="text-xs text-muted-foreground">
                  {formatDateShort(s.fechaInicio)}
                  {s.fechaFin !== s.fechaInicio && ` — ${formatDateShort(s.fechaFin)}`}
                  {" · "}{s.rutaNombre}
                </p>
                {s.tieneInforme
                  ? <span className="text-xs font-medium text-green-600 dark:text-green-400">Informe entregado</span>
                  : <span className="text-xs font-medium text-red-600 dark:text-red-400">Informe pendiente</span>
                }
              </div>
              <div className="shrink-0 text-right space-y-1">
                <p className="text-xs text-muted-foreground font-mono">
                  {s.totalInscritos}{s.capacidadMaxima ? `/${s.capacidadMaxima}` : ""} inscritos
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}

      {salidasPage && salidasPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Página {salidasPage.page.number + 1} de {salidasPage.page.totalPages}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={salidasPage.page.number === 0} onClick={() => setPage((p) => p - 1)}>
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button variant="outline" size="sm" disabled={salidasPage.page.number >= salidasPage.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {detailId && (
        <SalidaDetailDialog
          open={!!detailId}
          onClose={() => setDetailId(null)}
          salidaId={detailId}
        />
      )}
    </div>
  )
}

// ─── Main export ──────────────────────────────────────────────────────────────

type SocioTab = "proximas" | "mis-salidas" | "anteriores"

export function SocioSalidasView() {
  const [tab, setTab] = useState<SocioTab>("proximas")

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Salidas</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Explora las salidas planificadas y gestiona tus inscripciones
        </p>
      </div>

      <div className="flex gap-1 rounded-lg border border-border bg-muted p-1 w-fit">
        {([
          { key: "proximas", label: "Próximas salidas" },
          { key: "anteriores", label: "Salidas anteriores" },
          { key: "mis-salidas", label: "Mis Salidas (Kipu)" },
        ] as { key: SocioTab; label: string }[]).map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
              tab === key
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "proximas" && <ProximasSalidasTab />}
      {tab === "anteriores" && <SalidasAnterioresTab />}
      {tab === "mis-salidas" && <MisSalidasTab />}
    </div>
  )
}
