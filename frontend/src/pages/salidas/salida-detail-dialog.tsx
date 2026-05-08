import { useState } from "react"
import { useNavigate } from "react-router"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"
import { useAccesoPorNivel } from "@/hooks/use-mountains"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  useSalidaDetail,
  useSalidaLookups,
  useDesignarJefeSalida,
  useAgregarDignidad,
  useEliminarDignidad,
  useDecidirRiesgo,
  useRevocarAprobacion,
  useToggleCerrarInscripciones,
} from "@/hooks/use-salidas"
import { useAuthStore } from "@/stores/auth-store"
import { Users, Crown, Shield, Plus, X, FileText, Map as MapIcon, AlertTriangle, CheckCircle2, Clock, XCircle, Lock, Unlock, Download } from "lucide-react"
import { toast } from "sonner"
import type { Participante } from "@/types/salidas"
import { InformeJefeDialog } from "@/pages/informes/informe-jefe-dialog"
import { CategoriaChip } from "@/lib/salida-tipo"

interface Props {
  open: boolean
  onClose: () => void
  salidaId: string
}

const estadoLabel: Record<string, string> = {
  PLANIFICADA: "Planificada", EN_CURSO: "En curso", REALIZADA: "Realizada", CANCELADA: "Cancelada",
}
const estadoVariant: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  PLANIFICADA: "outline", EN_CURSO: "default", REALIZADA: "secondary", CANCELADA: "destructive",
}
const inscripcionVariant: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  INSCRITO: "secondary", CONFIRMADO: "default", NO_FUE: "outline",
  CANCELADO: "destructive", NEGADO: "destructive",
  PENDIENTE_APROBACION: "outline",
}

const inscripcionLabel: Record<string, string> = {
  INSCRITO: "Inscrito", CONFIRMADO: "Confirmado", NO_FUE: "No fue",
  CANCELADO: "Cancelado", NEGADO: "Negado", PENDIENTE_APROBACION: "Pendiente",
}

const ESTADOS_EDITABLES = new Set(["PLANIFICADA", "EN_CURSO"])

function formatDate(iso: string) {
  return new Date(iso + "T00:00:00").toLocaleDateString("es-EC", {
    day: "numeric", month: "long", year: "numeric",
  })
}

const ESTADOS_INFORME = new Set(["EN_CURSO", "REALIZADA"])

function pendienteMensaje(directivoAprobado: boolean, jefeAprobado: boolean): string {
  if (!directivoAprobado && !jefeAprobado) return "Pendiente aprobación de Jefe de Montaña y Jefe de Salida"
  if (!directivoAprobado) return "Pendiente aprobación de Jefe de Montaña"
  if (!jefeAprobado) return "Pendiente aprobación de Jefe de Salida"
  return "Pendiente aprobación"
}

export function SalidaDetailDialog({ open, onClose, salidaId }: Props) {
  const navigate = useNavigate()
  const { data: salida, isLoading } = useSalidaDetail(salidaId)
  const { data: lookups } = useSalidaLookups()
  const designarMutation = useDesignarJefeSalida(salidaId)
  const agregarMutation = useAgregarDignidad(salidaId)
  const eliminarMutation = useEliminarDignidad(salidaId)
  const decidirRiesgoMutation = useDecidirRiesgo(salidaId)
  const revocarAprobacionMutation = useRevocarAprobacion(salidaId)
  const toggleCerrarMutation = useToggleCerrarInscripciones(salidaId)

  const user = useAuthStore((s) => s.user)
  const userRole = user?.rol?.toUpperCase() ?? ""
  const canManage = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)
  // Solo puede aprobar riesgo un ADMIN o un DIRECTIVO con flag Jefe de Montaña
  const esDirectivoOAdmin =
    userRole === "ADMIN" || (userRole === "DIRECTIVO" && (user?.esJefeMontana ?? false))

  const [informeOpen, setInformeOpen] = useState(false)

  // Edición de dignidades solo en salidas no finalizadas
  const canEdit = canManage && !!salida && ESTADOS_EDITABLES.has(salida.estado)

  // El socio actual es jefe de esta salida
  const esJefe =
    !!salida && salida.participantes.some((p) => p.socioId === user?.socioId && p.esJefeSalida)
  const puedeVerInforme =
    esJefe &&
    !!salida &&
    (ESTADOS_INFORME.has(salida.estado) ||
      (salida.estado === "PLANIFICADA" &&
        new Date(salida.fechaFin + "T23:59:59") < new Date()))

  // ¿Ya hay un Jefe de Salida designado?
  const hayJefe = salida?.participantes.some((p) => p.esJefeSalida) ?? false

  // ── Auto-inscripción del Directivo ─────────────────────────────────────────
  const qc = useQueryClient()
  const { data: nivelesAcceso } = useAccesoPorNivel()
  const nivelOrdenMap = new Map<string, number>(
    (nivelesAcceso ?? []).map((n, i) => [n.nivelSocioNombre, i] as [string, number]),
  )

  const miParticipante = salida?.participantes.find((p) => p.socioId === user?.socioId)
  const yaInscrito = !!miParticipante
  const miEstado = miParticipante?.estadoInscripcion
  const miParticipanteId = miParticipante?.id

  const nivelMinimoNombre = salida?.nivelMinimoRequeridoNombre ?? null

  const puedeDescargar = canManage || esJefe

  const descargarParticipantes = () => {
    if (!salida) return
    const encabezado = ["Nombre", "Apellido", "Cédula", "Teléfono", "Edad", "Estado"]
    const filas = salida.participantes.map((p) => [
      p.socioNombre,
      p.socioApellido,
      p.cedula ?? "",
      p.telefono ?? "",
      p.edad != null ? String(p.edad) : "",
      p.estadoInscripcion,
    ])
    const csv = [encabezado, ...filas]
      .map((row) => row.map((v) => `"${v.replace(/"/g, '""')}"`).join(","))
      .join("\n")
    const blob = new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8;" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `participantes_${salida.nombre.replace(/\s+/g, "_")}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }
  const directivoNivelInsuficiente = (() => {
    if (!nivelMinimoNombre) return false
    const userNivel = user?.nivelTecnico
    if (!userNivel) return true
    const userIdx = nivelOrdenMap.get(userNivel) ?? -1
    const minIdx = nivelOrdenMap.get(nivelMinimoNombre) ?? -1
    if (userIdx === -1 || minIdx === -1) return false
    return userIdx < minIdx
  })()

  const [inscripcionConfirmOpen, setInscripcionConfirmOpen] = useState(false)

  const inscribirSelfMutation = useMutation({
    mutationFn: () =>
      api
        .post<ApiResponse<{ id: number; estadoInscripcion: string }>>(
          `/v1/salidas/${salidaId}/inscripciones`,
          { socioId: user?.socioId },
        )
        .then((r) => r.data.data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["salidas", "detail", salidaId] })
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

  // ¿Puede el usuario cancelar su propia inscripción?
  // PENDIENTE: siempre. INSCRITO: solo si faltan > 48h. Nunca si es jefe de salida sin rol privilegiado.
  const esJefeSalidaPropio = miParticipante?.esJefeSalida ?? false
  const puedeCancelarPropia = (() => {
    if (!miParticipante || !salida) return false
    if (esJefeSalidaPropio && !canManage) return false
    if (miEstado === "PENDIENTE_APROBACION") return true
    if (miEstado === "INSCRITO") {
      const cutoff = new Date(salida.fechaInicio + "T00:00:00").getTime() - 48 * 3600 * 1000
      return Date.now() < cutoff
    }
    return false
  })()

  const cancelarSelfMutation = useMutation({
    mutationFn: () => api.delete(`/v1/salidas/${salidaId}/inscripciones/${miParticipanteId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["salidas", "detail", salidaId] })
      qc.invalidateQueries({ queryKey: ["aprobaciones-pendientes"] })
      toast.success(miEstado === "PENDIENTE_APROBACION" ? "Solicitud cancelada" : "Inscripción cancelada")
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg || "Error al cancelar inscripción")
    },
  })

  const handleInscribirSelf = () => {
    if (directivoNivelInsuficiente) {
      setInscripcionConfirmOpen(true)
    } else {
      inscribirSelfMutation.mutate()
    }
  }

  const handleDesignar = async (participanteId: number, nombre: string) => {
    try {
      await designarMutation.mutateAsync(participanteId)
      toast.success(`${nombre} designado como Jefe de Salida`)
    } catch (err: unknown) {
      const apiMsg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(apiMsg ?? "Error al designar jefe de salida")
    }
  }

  const handleAgregarDignidad = async (participanteId: number, dignidadId: number, dignidadNombre: string) => {
    try {
      await agregarMutation.mutateAsync({ participanteId, dignidadId })
      toast.success(`Dignidad "${dignidadNombre}" asignada`)
    } catch (error) { console.error(error);
      toast.error("Error al asignar dignidad")
    }
  }

  const handleEliminarDignidad = async (
    participanteId: number,
    dignidadAsignadaId: number,
    dignidadNombre: string,
  ) => {
    try {
      await eliminarMutation.mutateAsync({ participanteId, dignidadAsignadaId })
      toast.success(`Dignidad "${dignidadNombre}" removida`)
    } catch (error) { console.error(error);
      toast.error("Error al remover dignidad")
    }
  }

  const participantesElegibles = salida?.participantes.filter(
    (p) => p.estadoInscripcion === "INSCRITO" || p.estadoInscripcion === "CONFIRMADO",
  ) ?? []

  // Dignidades disponibles para el dropdown "Agregar" — excluye siempre "Jefe de Salida"
  // (ese rol solo se asigna via el botón "Designar")
  const dignidadesBase = (lookups?.dignidades ?? []).filter((d) => d.nombre !== "Jefe de Salida")

  const isPending =
    designarMutation.isPending || agregarMutation.isPending ||
    eliminarMutation.isPending || decidirRiesgoMutation.isPending ||
    revocarAprobacionMutation.isPending

  const handleToggleCerrar = async () => {
    try {
      const cerradas = await toggleCerrarMutation.mutateAsync()
      toast.success(cerradas ? "Inscripciones cerradas" : "Inscripciones abiertas nuevamente")
    } catch (error) { console.error(error);
      toast.error("Error al cambiar estado de inscripciones")
    }
  }

  const handleDecidirRiesgo = async (
    participanteId: number,
    nombre: string,
    aprobar: boolean,
    motivo: string,
  ) => {
    try {
      await decidirRiesgoMutation.mutateAsync({ participanteId, aprobar, motivo })
      toast.success(
        aprobar
          ? `Aprobación registrada para ${nombre}`
          : `Inscripción de ${nombre} marcada como negada`,
      )
    } catch (error) { console.error(error);
      toast.error("Error al registrar la decisión")
    }
  }

  const handleRevocarAprobacion = async (participanteId: number, nombre: string) => {
    try {
      await revocarAprobacionMutation.mutateAsync(participanteId)
      toast.success(`Aprobación revocada para ${nombre}`)
    } catch (error) { console.error(error);
      toast.error("Error al revocar la aprobación")
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Detalle de la salida</DialogTitle>
        </DialogHeader>

        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : salida ? (
          <div className="space-y-6">
            {/* Banner jefe de salida */}
            {esJefe && ESTADOS_EDITABLES.has(salida.estado) && (
              <div className="rounded-lg border border-yellow-400/40 bg-yellow-500/10 px-4 py-3 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Crown className="h-4 w-4 text-yellow-500" />
                    <span className="text-sm font-semibold text-yellow-700">Eres Jefe de Salida</span>
                    {salida.inscripcionesCerradas && (
                      <Badge variant="destructive" className="text-xs gap-1">
                        <Lock className="h-3 w-3" /> Cerradas
                      </Badge>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      size="sm"
                      variant={salida.inscripcionesCerradas ? "default" : "destructive"}
                      className="gap-1.5"
                      onClick={handleToggleCerrar}
                      disabled={toggleCerrarMutation.isPending}
                    >
                      {salida.inscripcionesCerradas ? (
                        <><Unlock className="h-3.5 w-3.5" /> Abrir inscripciones</>
                      ) : (
                        <><Lock className="h-3.5 w-3.5" /> Cerrar inscripciones</>
                      )}
                    </Button>
                    {puedeVerInforme && (
                      <Button
                        size="sm"
                        className="gap-1.5 bg-yellow-500 hover:bg-yellow-600 text-white"
                        onClick={() => setInformeOpen(true)}
                      >
                        <FileText className="h-3.5 w-3.5" />
                        Llenar informe
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            )}
            {/* Informe solo (cuando ya no es estado editable pero sí puede ver informe) */}
            {puedeVerInforme && !ESTADOS_EDITABLES.has(salida.estado) && (
              <div className="flex items-center justify-between rounded-lg border border-yellow-400/40 bg-yellow-500/10 px-4 py-3">
                <div className="flex items-center gap-2">
                  <Crown className="h-4 w-4 text-yellow-500" />
                  <span className="text-sm font-semibold text-yellow-700">Eres Jefe de Salida</span>
                </div>
                <Button
                  size="sm"
                  className="gap-1.5 bg-yellow-500 hover:bg-yellow-600 text-white"
                  onClick={() => setInformeOpen(true)}
                >
                  <FileText className="h-3.5 w-3.5" />
                  Llenar informe de salida
                </Button>
              </div>
            )}

            {/* Encabezado */}
            <div>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <h2 className="text-xl font-bold text-foreground">{salida.nombre}</h2>
                {salida.rutaId && (
                  <Button
                    size="sm"
                    variant="destructive"
                    className="gap-1.5 shrink-0"
                    onClick={() => {
                      onClose()
                      navigate(`/planificador?salidaId=${salidaId}`)
                    }}
                  >
                    <MapIcon className="h-3.5 w-3.5" />
                    Recomendaciones
                  </Button>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2 mt-2">
                <Badge variant={estadoVariant[salida.estado]}>{estadoLabel[salida.estado]}</Badge>
                {salida.tipoActividad && <CategoriaChip tipoActividad={salida.tipoActividad} />}
                {salida.formatoSalidaNombre && (
                  <Badge variant="outline">{salida.formatoSalidaNombre}</Badge>
                )}
                {salida.publicoObjetivoNombre && salida.publicoObjetivoNombre !== "Socios" && (
                  <Badge variant="outline">{salida.publicoObjetivoNombre}</Badge>
                )}
                {salida.nivelMinimoRequeridoNombre && (
                  <Badge variant="outline">Nivel mín: {salida.nivelMinimoRequeridoNombre}</Badge>
                )}
                {salida.inscripcionesCerradas && (
                  <Badge variant="destructive" className="gap-1">
                    <Lock className="h-3 w-3" /> Inscripciones cerradas
                  </Badge>
                )}
              </div>
            </div>

            {/* Datos generales */}
            <div className="grid gap-2 sm:grid-cols-2">
              <InfoRow label="Fecha inicio" value={formatDate(salida.fechaInicio)} />
              <InfoRow label="Fecha fin" value={formatDate(salida.fechaFin)} />
              <InfoRow label="Hora encuentro" value={salida.horaEncuentroClub} />
              <InfoRow label="Hora regreso est." value={salida.horaEstimadaRegresoClub ?? "—"} />
              <InfoRow label="Ruta" value={salida.rutaNombre} />
              <InfoRow
                label="Capacidad"
                value={
                  salida.capacidadMaxima
                    ? `${salida.totalInscritos}/${salida.capacidadMaxima}`
                    : `${salida.totalInscritos} inscritos`
                }
              />
              <InfoRow label="Creada por" value={salida.creadoPorNombreCompleto} />
            </div>

            {/* Documentos de permiso requeridos */}
            {salida.documentosPermiso && salida.documentosPermiso.length > 0 && (
              <div className="rounded-xl border border-amber-400/40 bg-amber-500/10 p-4 space-y-2">
                <p className="text-sm font-semibold text-amber-700 flex items-center gap-2">
                  <FileText className="h-4 w-4" />
                  Documentación requerida para esta salida
                </p>
                <ul className="space-y-1.5">
                  {salida.documentosPermiso.map((doc) => (
                    <li key={doc.id} className="flex items-center justify-between gap-3">
                      <span className="text-sm text-foreground truncate">{doc.filename}</span>
                      <a
                        href={`/api/v1/rutas/${salida.rutaId}/documentos/${doc.id}/descargar`}
                        download={doc.filename}
                        className="flex items-center gap-1 text-xs text-primary hover:underline shrink-0"
                      >
                        <Download className="h-3.5 w-3.5" /> Descargar
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Aviso informativo cuando la salida ya no es editable */}
            {canManage && !canEdit && (
              <p className="text-xs text-muted-foreground italic">
                Las dignidades no se pueden modificar en salidas {estadoLabel[salida.estado].toLowerCase()}.
              </p>
            )}

            {/* Banner para Directivos/Jefes: inscripciones que aún requieren SU aprobación */}
            {(() => {
              if (!esDirectivoOAdmin && !esJefe) return null
              const misPendientes = salida.participantes.filter((p) => {
                if (p.estadoInscripcion !== "PENDIENTE_APROBACION") return false
                if (esDirectivoOAdmin && !p.riesgoAprobadoPorDirectivo) return true
                if (esJefe && !p.riesgoAprobadoPorJefe) return true
                return false
              })
              if (misPendientes.length === 0) return null
              return (
                <div className="flex items-start gap-2 rounded-lg border border-amber-400/40 bg-amber-500/10 px-4 py-3">
                  <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600 mt-0.5" />
                  <div>
                    <p className="text-sm font-semibold text-amber-700">
                      {misPendientes.length === 1
                        ? "1 inscripción requiere tu aprobación"
                        : `${misPendientes.length} inscripciones requieren tu aprobación`}
                    </p>
                    <p className="text-xs text-amber-600 mt-0.5">
                      Estos socios tienen nivel insuficiente. Directivo y Jefe de Salida deben aprobar para que queden inscritos.
                    </p>
                  </div>
                </div>
              )
            })()}

            {/* Banner para el Socio: su propia inscripción está pendiente de aprobación */}
            {!canManage && !esJefe && miEstado === "PENDIENTE_APROBACION" && miParticipante && (
              <div className="flex items-start gap-2 rounded-lg border border-amber-400/40 bg-amber-500/10 px-4 py-3">
                <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600 mt-0.5" />
                <div>
                  <p className="text-sm font-semibold text-amber-700">Tu inscripción está pendiente de aprobación</p>
                  <p className="text-xs text-amber-600 mt-0.5">
                    {pendienteMensaje(
                      !!miParticipante.riesgoAprobadoPorDirectivo,
                      !!miParticipante.riesgoAprobadoPorJefe,
                    )}
                  </p>
                </div>
              </div>
            )}

            {/* Panel de inscripción propia — visible para todos los perfiles */}
            {ESTADOS_EDITABLES.has(salida.estado) && (
              <div className="rounded-lg border border-border bg-muted/30 px-4 py-3 space-y-2">
                <p className="text-sm font-medium text-foreground">Tu inscripción</p>

                {/* Sin inscripción: solo pueden auto-inscribirse roles no-admin o DIRECTIVO/SECRETARIA */}
                {!yaInscrito && (
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs text-muted-foreground">No estás inscrito en esta salida.</span>
                    <Button
                      size="sm"
                      className="shrink-0 gap-1.5"
                      disabled={inscribirSelfMutation.isPending}
                      onClick={handleInscribirSelf}
                    >
                      {inscribirSelfMutation.isPending ? "Inscribiendo..." : "Inscribirme"}
                    </Button>
                  </div>
                )}

                {/* Inscrito */}
                {yaInscrito && miEstado === "INSCRITO" && (
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-xs text-green-700 flex items-center gap-1">
                        <CheckCircle2 className="h-3.5 w-3.5" /> Inscrito
                      </span>
                      {esJefeSalidaPropio && !canManage ? (
                        <span className="text-xs font-medium text-amber-700 italic">
                          Eres Jefe de Salida
                        </span>
                      ) : puedeCancelarPropia ? (
                        <Button
                          size="sm"
                          variant="outline"
                          className="shrink-0 gap-1.5 text-destructive border-destructive hover:bg-destructive/10"
                          disabled={cancelarSelfMutation.isPending}
                          onClick={() => cancelarSelfMutation.mutate()}
                        >
                          <XCircle className="h-3.5 w-3.5" />
                          {cancelarSelfMutation.isPending ? "Cancelando..." : "Cancelar inscripción"}
                        </Button>
                      ) : (
                        <span className="text-xs text-muted-foreground italic">
                          No se puede cancelar (menos de 48 h)
                        </span>
                      )}
                    </div>
                    {/* Aviso bloqueo jefe de salida */}
                    {esJefeSalidaPropio && !canManage && (
                      <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-3 py-2 leading-relaxed">
                        Fuiste elegido como Jefe de Salida, por lo que no puedes cancelar tu inscripción.
                        Si deseas retirarte, por favor contacta al Jefe de Montaña para que lo gestione.
                      </p>
                    )}
                    {/* Motivos de aprobación si pasó por revisión de nivel */}
                    {miParticipante?.motivoDirectivo && (
                      <p className="text-xs text-muted-foreground pl-1">
                        <span className="font-medium text-foreground">Jefe de Montaña:</span>{" "}
                        {miParticipante.motivoDirectivo}
                      </p>
                    )}
                    {miParticipante?.motivoJefe && (
                      <p className="text-xs text-muted-foreground pl-1">
                        <span className="font-medium text-foreground">Jefe de Salida:</span>{" "}
                        {miParticipante.motivoJefe}
                      </p>
                    )}
                  </div>
                )}

                {/* Pendiente de aprobación */}
                {yaInscrito && miEstado === "PENDIENTE_APROBACION" && (
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-xs text-amber-700 flex items-center gap-1">
                        <AlertTriangle className="h-3.5 w-3.5" /> Pendiente de aprobación
                      </span>
                      {esJefeSalidaPropio && !canManage ? (
                        <span className="text-xs text-muted-foreground italic">
                          Eres Jefe de Salida
                        </span>
                      ) : (
                        <Button
                          size="sm"
                          variant="outline"
                          className="shrink-0 gap-1.5 text-amber-700 border-amber-400 hover:bg-amber-50"
                          disabled={cancelarSelfMutation.isPending}
                          onClick={() => cancelarSelfMutation.mutate()}
                        >
                          <XCircle className="h-3.5 w-3.5" />
                          {cancelarSelfMutation.isPending ? "Cancelando..." : "Cancelar solicitud"}
                        </Button>
                      )}
                    </div>
                    {miParticipante?.motivoDirectivo && (
                      <p className="text-xs text-muted-foreground pl-1">
                        <span className="font-medium text-foreground">Jefe de Montaña:</span>{" "}
                        {miParticipante.motivoDirectivo}
                      </p>
                    )}
                    {miParticipante?.motivoJefe && (
                      <p className="text-xs text-muted-foreground pl-1">
                        <span className="font-medium text-foreground">Jefe de Salida:</span>{" "}
                        {miParticipante.motivoJefe}
                      </p>
                    )}
                  </div>
                )}

                {yaInscrito && miEstado === "NEGADO" && (
                  <div className="space-y-1.5">
                    <span className="text-xs text-destructive flex items-center gap-1 font-medium">
                      <XCircle className="h-3.5 w-3.5" /> Tu inscripción fue negada.
                    </span>
                    {miParticipante?.motivoDirectivo && (
                      <p className="text-xs text-muted-foreground pl-5">
                        <span className="font-medium text-foreground">Jefe de Montaña:</span>{" "}
                        {miParticipante.motivoDirectivo}
                      </p>
                    )}
                    {miParticipante?.motivoJefe && (
                      <p className="text-xs text-muted-foreground pl-5">
                        <span className="font-medium text-foreground">Jefe de Salida:</span>{" "}
                        {miParticipante.motivoJefe}
                      </p>
                    )}
                  </div>
                )}

                {yaInscrito && miEstado === "CANCELADO" && (
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs text-muted-foreground">Inscripción cancelada.</span>
                    <Button
                      size="sm"
                      className="shrink-0 gap-1.5"
                      disabled={inscribirSelfMutation.isPending}
                      onClick={handleInscribirSelf}
                    >
                      {inscribirSelfMutation.isPending ? "Inscribiendo..." : "Volver a inscribirme"}
                    </Button>
                  </div>
                )}
              </div>
            )}

            {/* Diálogo de confirmación de nivel insuficiente (Directivo) */}
            {inscripcionConfirmOpen && (
              <div
                className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4"
                onClick={() => setInscripcionConfirmOpen(false)}
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
                      disabled={inscribirSelfMutation.isPending}
                      onClick={() => {
                        setInscripcionConfirmOpen(false)
                        inscribirSelfMutation.mutate()
                      }}
                    >
                      {inscribirSelfMutation.isPending ? "Inscribiendo..." : "Continuar y solicitar aprobación"}
                    </Button>
                    <Button
                      variant="outline"
                      className="flex-1"
                      onClick={() => setInscripcionConfirmOpen(false)}
                    >
                      Volver atrás
                    </Button>
                  </div>
                </div>
              </div>
            )}

            {/* Participantes */}
            <div className="space-y-3">
              <div className="flex items-center justify-between border-b border-border pb-2">
                <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <Users className="h-4 w-4" /> Participantes ({salida.participantes.length})
                </h3>
                {puedeDescargar && salida.participantes.length > 0 && (
                  <Button size="sm" variant="outline" className="gap-1.5" onClick={descargarParticipantes}>
                    <Download className="h-3.5 w-3.5" />
                    Descargar
                  </Button>
                )}
              </div>

              {salida.participantes.length === 0 ? (
                <p className="text-sm text-muted-foreground">No hay inscritos aún</p>
              ) : (
                <div className="space-y-2">
                  {salida.participantes.map((p) => {
                    const nombre = `${p.socioNombre} ${p.socioApellido}`
                    const esElegible = participantesElegibles.some((e) => e.id === p.id)

                    // Dignidades disponibles para este participante = base sin las ya asignadas
                    const asignadasIds = new Set(p.dignidades.map((d) => d.dignidadId))
                    const disponibles = dignidadesBase.filter((d) => !asignadasIds.has(d.id))

                    return (
                      <ParticipanteRow
                        key={p.id}
                        participante={p}
                        esElegible={esElegible}
                        canEdit={canEdit}
                        hayJefe={hayJefe}
                        isPending={isPending}
                        disponibles={disponibles}
                        esDirectivoOAdmin={esDirectivoOAdmin}
                        esJefeSalida={esJefe}
                        onDesignar={() => handleDesignar(p.id, nombre)}
                        onAgregarDignidad={(dignidadId, dignidadNombre) =>
                          handleAgregarDignidad(p.id, dignidadId, dignidadNombre)
                        }
                        onEliminarDignidad={(asignadaId, dignidadNombre) =>
                          handleEliminarDignidad(p.id, asignadaId, dignidadNombre)
                        }
                        onDecidirRiesgo={(aprobar, motivo) =>
                          handleDecidirRiesgo(p.id, nombre, aprobar, motivo)
                        }
                        onRevocarAprobacion={() =>
                          handleRevocarAprobacion(p.id, nombre)
                        }
                      />
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        ) : null}
      </DialogContent>

      {informeOpen && salida && (
        <InformeJefeDialog
          open={informeOpen}
          onClose={() => setInformeOpen(false)}
          salidaId={salida.id}
          horaEncuentroClub={salida.horaEncuentroClub}
        />
      )}
    </Dialog>
  )
}

// ─── Fila de participante ────────────────────────────────────────────────────

interface ParticipanteRowProps {
  participante: Participante
  esElegible: boolean
  canEdit: boolean
  hayJefe: boolean
  isPending: boolean
  disponibles: { id: number; nombre: string }[]
  esDirectivoOAdmin: boolean
  esJefeSalida: boolean
  onDesignar: () => void
  onAgregarDignidad: (dignidadId: number, dignidadNombre: string) => void
  onEliminarDignidad: (asignadaId: number, dignidadNombre: string) => void
  onDecidirRiesgo: (aprobar: boolean, motivo: string) => void
  onRevocarAprobacion: () => void
}

function ParticipanteRow({
  participante: p,
  esElegible,
  canEdit,
  hayJefe,
  isPending,
  disponibles,
  esDirectivoOAdmin,
  esJefeSalida,
  onDesignar,
  onAgregarDignidad,
  onEliminarDignidad,
  onDecidirRiesgo,
  onRevocarAprobacion,
}: ParticipanteRowProps) {
  const [decisionOpen, setDecisionOpen] = useState(false)
  const [decidirAprobar, setDecidirAprobar] = useState<boolean>(true)
  const [motivo, setMotivo] = useState("")

  const esPendiente = p.estadoInscripcion === "PENDIENTE_APROBACION"

  // Botón "Designar" visible solo cuando no hay jefe aún y este participante es elegible
  const mostrarDesignar = canEdit && esElegible && !p.esJefeSalida && !hayJefe

  // Botón "Agregar dignidad" visible para participantes elegibles con dignidades disponibles
  const mostrarAgregar = canEdit && esElegible && disponibles.length > 0

  return (
    <div
      className={`rounded-lg border p-3 transition-colors ${
        p.esJefeSalida
          ? "border-yellow-400/50 bg-yellow-500/5"
          : esPendiente
            ? "border-amber-400/50 bg-amber-500/5"
            : "border-border"
      }`}
    >
      {/* Primera línea: nombre + estado + acción jefe */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          {p.esJefeSalida && <Crown className="h-4 w-4 shrink-0 text-yellow-500" />}
          {esPendiente && <AlertTriangle className="h-4 w-4 shrink-0 text-amber-500" />}
          <div className="min-w-0">
            <span className="block truncate text-sm font-medium text-foreground">
              {p.socioApellido}, {p.socioNombre}
            </span>
            {p.esJefeSalida && (
              <span className="text-xs font-medium text-yellow-600">Jefe de Salida</span>
            )}
            {esPendiente && p.nivelInsuficiente && (
              <span className="text-xs text-amber-600">
                Nivel: {p.nivelSocioNombre ?? "sin nivel"} — mínimo: {p.nivelMinimoRequeridoNombre}
              </span>
            )}
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-1">
          <Badge
            variant={inscripcionVariant[p.estadoInscripcion] ?? "secondary"}
            className={`text-xs ${esPendiente ? "border-amber-400 text-amber-700 bg-amber-500/10" : ""}`}
          >
            {inscripcionLabel[p.estadoInscripcion] ?? p.estadoInscripcion}
          </Badge>

          {mostrarDesignar && (
            <Button
              size="sm"
              variant="ghost"
              className="h-7 gap-1 px-2 text-xs text-muted-foreground hover:text-yellow-600"
              disabled={isPending}
              onClick={onDesignar}
              title="Designar como Jefe de Salida"
            >
              <Crown className="h-3.5 w-3.5" />
              Designar jefe
            </Button>
          )}
        </div>
      </div>

      {/* Panel de aprobación de riesgo */}
      {esPendiente && (
        <div className="mt-2 rounded-md border border-amber-300/40 bg-amber-50/50 px-3 py-2 space-y-2">
          {/* Fila Jefe de Montaña (Directivo) */}
          <div className="flex items-center justify-between gap-2 text-xs">
            <span className="flex items-center gap-1 text-amber-700">
              {p.riesgoAprobadoPorDirectivo ? (
                <CheckCircle2 className="h-3.5 w-3.5 text-green-600 shrink-0" />
              ) : (
                <Clock className="h-3.5 w-3.5 text-amber-500 shrink-0" />
              )}
              {p.riesgoAprobadoPorDirectivo
                ? <span>Jefe de Montaña: <span className="font-medium text-green-700">{p.riesgoAprobadoPorDirectivoNombre ?? "aprobado"}</span></span>
                : <span>Jefe de Montaña: pendiente</span>
              }
            </span>
            {esDirectivoOAdmin && !decisionOpen && (
              p.riesgoAprobadoPorDirectivo ? (
                <Button
                  size="sm" variant="ghost"
                  className="h-6 px-2 text-xs text-muted-foreground hover:text-amber-700"
                  disabled={isPending}
                  onClick={onRevocarAprobacion}
                >
                  Cambiar decisión
                </Button>
              ) : (
                <Button
                  size="sm" variant="outline"
                  className="h-6 px-2 text-xs border-amber-400 text-amber-700 hover:bg-amber-100"
                  onClick={() => setDecisionOpen(true)}
                >
                  Decidir
                </Button>
              )
            )}
          </div>

          {/* Fila Jefe de Salida */}
          <div className="flex items-center justify-between gap-2 text-xs">
            <span className="flex items-center gap-1 text-amber-700">
              {p.riesgoAprobadoPorJefe ? (
                <CheckCircle2 className="h-3.5 w-3.5 text-green-600 shrink-0" />
              ) : (
                <Clock className="h-3.5 w-3.5 text-amber-500 shrink-0" />
              )}
              {p.riesgoAprobadoPorJefe
                ? <span>Jefe de Salida: <span className="font-medium text-green-700">{p.riesgoAprobadoPorJefeNombre ?? "aprobado"}</span></span>
                : <span>Jefe de Salida: pendiente</span>
              }
            </span>
            {esJefeSalida && !decisionOpen && (
              p.riesgoAprobadoPorJefe ? (
                <Button
                  size="sm" variant="ghost"
                  className="h-6 px-2 text-xs text-muted-foreground hover:text-amber-700"
                  disabled={isPending}
                  onClick={onRevocarAprobacion}
                >
                  Cambiar decisión
                </Button>
              ) : (
                <Button
                  size="sm" variant="outline"
                  className="h-6 px-2 text-xs border-amber-400 text-amber-700 hover:bg-amber-100"
                  onClick={() => setDecisionOpen(true)}
                >
                  Decidir
                </Button>
              )
            )}
          </div>

          {/* Formulario de decisión inline */}
          {decisionOpen && (
            <div className="space-y-2 border-t border-amber-200 pt-2">
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
                  disabled={isPending || motivo.trim().length === 0}
                  onClick={() => {
                    onDecidirRiesgo(decidirAprobar, motivo.trim())
                    setDecisionOpen(false)
                    setMotivo("")
                  }}
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

          {/* Motivos registrados */}
          {p.motivoDirectivo && (
            <p className="text-xs text-muted-foreground border-t border-amber-200 pt-1">
              <span className="font-medium">Motivo Jefe Montaña:</span> {p.motivoDirectivo}
            </p>
          )}
          {p.motivoJefe && (
            <p className="text-xs text-muted-foreground">
              <span className="font-medium">Motivo Jefe Salida:</span> {p.motivoJefe}
            </p>
          )}
        </div>
      )}

      {/* Segunda línea: dignidades con gestión */}
      {(p.dignidades.length > 0 || mostrarAgregar) && (
        <div className="flex flex-wrap items-center gap-1 mt-2">
          <Shield className="h-3.5 w-3.5 text-muted-foreground shrink-0" />

          {p.dignidades.map((d) => {
            // "Jefe de Salida" solo se puede quitar desde aquí (no hay "Designar" cuando hay jefe)
            const puedeQuitar = canEdit && (
              d.dignidadNombre !== "Jefe de Salida" || p.esJefeSalida
            )

            return (
              <span
                key={d.id}
                className="inline-flex items-center gap-0.5 rounded-full border border-border bg-muted/50 px-2 py-0.5 text-xs text-foreground"
              >
                {d.dignidadNombre}
                {puedeQuitar && (
                  <button
                    type="button"
                    className="ml-0.5 rounded-full p-0.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive disabled:opacity-50"
                    disabled={isPending}
                    onClick={() => onEliminarDignidad(d.id, d.dignidadNombre)}
                    title={`Quitar ${d.dignidadNombre}`}
                  >
                    <X className="h-2.5 w-2.5" />
                  </button>
                )}
              </span>
            )
          })}

          {mostrarAgregar && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className="inline-flex items-center gap-0.5 rounded-full border border-dashed border-border px-2 py-0.5 text-xs text-muted-foreground hover:border-primary hover:text-primary disabled:opacity-50"
                  disabled={isPending}
                  title="Agregar dignidad"
                >
                  <Plus className="h-3 w-3" />
                  Agregar
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                {disponibles.map((d) => (
                  <DropdownMenuItem
                    key={d.id}
                    onSelect={() => onAgregarDignidad(d.id, d.nombre)}
                  >
                    {d.nombre}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      )}
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm text-foreground">{value}</p>
    </div>
  )
}
