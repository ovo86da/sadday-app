import { useState, useEffect } from "react"
import { useQuery } from "@tanstack/react-query"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Badge } from "@/components/ui/badge"
import { Checkbox } from "@/components/ui/checkbox"
import { toast } from "sonner"
import { Loader2, Search, X } from "lucide-react"
import { useCreateActa, useUpdateActa, useActaDetail, useAgregarAsistente, useEliminarAsistente } from "@/hooks/use-actas"
import api from "@/lib/api"
import type { TipoActa } from "@/types/actas"
import type { ApiResponse, PageResponse, SocioSummary } from "@/types/socios"
import type { SalidaSummary } from "@/types/salidas"

function defaultHora(tipo: TipoActa) {
  return tipo === "SOCIOS" ? "20:00" : "19:00"
}

function formatFecha(iso: string) {
  return new Date(iso + "T00:00:00").toLocaleDateString("es-EC", {
    day: "numeric", month: "short", year: "numeric",
  })
}

// ─── Picker reutilizable ──────────────────────────────

interface SalidaPickerProps {
  /** Parámetros base cuando no hay búsqueda */
  defaultParams: Record<string, unknown>
  selectedIds: Set<string>
  loadingIds?: Set<string>
  onToggle: (salida: SalidaSummary) => void
}

function SalidaSearchPicker({ defaultParams, selectedIds, loadingIds = new Set(), onToggle }: SalidaPickerProps) {
  const [q, setQ] = useState("")

  const queryParams = q.length >= 2
    ? { q, size: 10, sort: "fechaInicio,desc" }
    : defaultParams

  const { data: salidas, isLoading } = useQuery({
    queryKey: ["salidas", "picker", queryParams],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<PageResponse<SalidaSummary>>>("/v1/salidas", {
        params: queryParams,
      })
      return data.data.content
    },
    staleTime: 2 * 60 * 1000,
  })

  return (
    <div className="space-y-2">
      {/* Búsqueda */}
      <div className="relative">
        <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
        <Input
          className="pl-8 h-8 text-sm"
          placeholder="Buscar salida..."
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        {q && (
          <button
            type="button"
            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            onClick={() => setQ("")}
          >
            <X className="h-3.5 w-3.5" />
          </button>
        )}
      </div>

      {/* Lista */}
      <div className="rounded-md border border-border max-h-44 overflow-y-auto">
        {isLoading ? (
          <div className="flex items-center justify-center py-4">
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          </div>
        ) : !salidas || salidas.length === 0 ? (
          <p className="py-4 text-center text-xs text-muted-foreground">
            {q.length >= 2 ? "Sin resultados" : "No hay salidas disponibles"}
          </p>
        ) : (
          salidas.map((s) => {
            const checked = selectedIds.has(s.id)
            const isLoading = loadingIds.has(s.id)
            return (
              <label
                key={s.id}
                className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-accent transition-colors border-b border-border last:border-0"
              >
                <Checkbox
                  checked={checked}
                  onCheckedChange={() => onToggle(s)}
                  disabled={isLoading}
                />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{s.nombre}</p>
                  <p className="text-xs text-muted-foreground">
                    {formatFecha(s.fechaInicio)} · {s.rutaNombre}
                  </p>
                </div>
                {isLoading && <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground shrink-0" />}
              </label>
            )
          })
        )}
      </div>
    </div>
  )
}

// ─── Picker de socios ─────────────────────────────────

interface SocioPickerProps {
  selectedIds: Set<string>
  onToggle: (socio: SocioSummary) => void
  disabledIds?: Set<string>
}

function SocioSearchPicker({ selectedIds, onToggle, disabledIds = new Set() }: SocioPickerProps) {
  const [q, setQ] = useState("")

  const { data: socios, isLoading } = useQuery({
    queryKey: ["socios", "picker", q],
    queryFn: async () => {
      const params: Record<string, unknown> = { size: 15, sort: "apellido,asc" }
      if (q.length >= 2) params.q = q
      const { data } = await api.get<ApiResponse<PageResponse<SocioSummary>>>("/v1/socios", { params })
      return data.data.content
    },
    staleTime: 2 * 60 * 1000,
  })

  return (
    <div className="space-y-2">
      <div className="relative">
        <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
        <Input
          className="pl-8 h-8 text-sm"
          placeholder="Buscar socio..."
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        {q && (
          <button
            type="button"
            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            onClick={() => setQ("")}
          >
            <X className="h-3.5 w-3.5" />
          </button>
        )}
      </div>
      <div className="rounded-md border border-border max-h-44 overflow-y-auto">
        {isLoading ? (
          <div className="flex items-center justify-center py-4">
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          </div>
        ) : !socios || socios.length === 0 ? (
          <p className="py-4 text-center text-xs text-muted-foreground">Sin resultados</p>
        ) : (
          socios.map((s) => {
            const checked = selectedIds.has(s.id)
            const disabled = disabledIds.has(s.id)
            return (
              <label
                key={s.id}
                className="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-accent transition-colors border-b border-border last:border-0"
              >
                <Checkbox
                  checked={checked}
                  onCheckedChange={() => onToggle(s)}
                  disabled={disabled}
                />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{s.apellido}, {s.nombre}</p>
                  <p className="text-xs text-muted-foreground">{s.tipoSocio}</p>
                </div>
                {disabled && <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground shrink-0" />}
              </label>
            )
          })
        )}
      </div>
    </div>
  )
}

// ─── Estado de salida vinculada (realizadas) ──────────

interface SelectedSalida {
  salidaId: string
  nombre: string
  fechaInicio: string
  rutaNombre: string
  informeId: string | null
  loading: boolean
}

// ─── Form dialog ──────────────────────────────────────

interface Props {
  open: boolean
  onClose: () => void
  mode: "create" | "edit"
  actaId?: string
  defaultTipo?: TipoActa
}

export function ActaFormDialog({ open, onClose, mode, actaId, defaultTipo = "SOCIOS" }: Props) {
  const { data: actaData } = useActaDetail(mode === "edit" ? actaId : undefined)
  // En edit, el tipo real viene del acta cargada; en create, del tab activo
  const tipoActual: TipoActa = mode === "edit" && actaData ? actaData.tipoActa : defaultTipo
  const createMutation = useCreateActa()
  const updateMutation = useUpdateActa(actaId ?? "")
  const agregarAsistenteMutation = useAgregarAsistente(actaId ?? "")
  const eliminarAsistenteMutation = useEliminarAsistente(actaId ?? "")

  const [form, setForm] = useState({
    fecha: "", hora: defaultHora(tipoActual), horaFin: "",
    lugar: tipoActual === "SOCIOS" ? "Presencial" : "",
    actividadesRealizadasDesc: "", actividadesPorRealizar: "",
    varios: "", observaciones: "",
  })

  // Asistentes seleccionados (solo en create mode)
  const [selectedAsistentes, setSelectedAsistentes] = useState<Map<string, SocioSummary>>(new Map())
  const [addingAsistenteIds, setAddingAsistenteIds] = useState<Set<string>>(new Set())

  // Salidas realizadas seleccionadas (con informeId)
  const [selectedRealizadas, setSelectedRealizadas] = useState<Map<string, SelectedSalida>>(new Map())

  // Salidas por realizar seleccionadas (solo referencia)
  const [selectedPorRealizar, setSelectedPorRealizar] = useState<Map<string, SalidaSummary>>(new Map())

  useEffect(() => {
    if (mode === "edit" && actaData) {
      setForm({
        fecha: actaData.fecha,
        hora: actaData.hora,
        horaFin: actaData.horaFin ?? "",
        lugar: actaData.lugar ?? "",
        actividadesRealizadasDesc: actaData.actividadesRealizadasDesc ?? "",
        actividadesPorRealizar: actaData.actividadesPorRealizar ?? "",
        varios: actaData.varios ?? "",
        observaciones: actaData.observaciones ?? "",
      })
    } else if (mode === "create") {
      setForm({
        fecha: "", hora: defaultHora(tipoActual), horaFin: "",
        lugar: tipoActual === "SOCIOS" ? "Presencial" : "",
        actividadesRealizadasDesc: "", actividadesPorRealizar: "",
        varios: "", observaciones: "",
      })
      setSelectedAsistentes(new Map())
      setSelectedRealizadas(new Map())
      setSelectedPorRealizar(new Map())
    }
  }, [mode, actaData, open])

  const update = (field: string, value: string) => setForm((p) => ({ ...p, [field]: value }))

  // Toggle asistente — create mode: estado local; edit mode: llamada inmediata al API
  async function toggleAsistente(socio: SocioSummary) {
    if (mode === "create") {
      setSelectedAsistentes((prev) => {
        const m = new Map(prev)
        if (m.has(socio.id)) m.delete(socio.id)
        else m.set(socio.id, socio)
        return m
      })
      return
    }
    // edit mode: ya existe en actaData.asistentes?
    const existente = actaData?.asistentes.find((a) => a.socioId === socio.id)
    if (existente) {
      try {
        await eliminarAsistenteMutation.mutateAsync(existente.id)
      } catch (error) { console.error(error);
        toast.error("Error al quitar asistente")
      }
    } else {
      setAddingAsistenteIds((prev) => new Set(prev).add(socio.id))
      try {
        await agregarAsistenteMutation.mutateAsync(socio.id)
      } catch (error) { console.error(error);
        toast.error("Error al agregar asistente")
      } finally {
        setAddingAsistenteIds((prev) => { const s = new Set(prev); s.delete(socio.id); return s })
      }
    }
  }

  // Toggle salida realizada: carga su informe al seleccionar
  async function toggleRealizada(salida: SalidaSummary) {
    if (selectedRealizadas.has(salida.id)) {
      setSelectedRealizadas((prev) => { const m = new Map(prev); m.delete(salida.id); return m })
      return
    }
    setSelectedRealizadas((prev) => new Map(prev).set(salida.id, {
      salidaId: salida.id, nombre: salida.nombre,
      fechaInicio: salida.fechaInicio, rutaNombre: salida.rutaNombre ?? "",
      informeId: null, loading: true,
    }))
    try {
      const { data } = await api.get<ApiResponse<{ id: string }>>(`/v1/informes/${salida.id}`)
      setSelectedRealizadas((prev) => {
        const m = new Map(prev)
        const e = m.get(salida.id)
        if (e) m.set(salida.id, { ...e, informeId: data.data.id, loading: false })
        return m
      })
    } catch (error) { console.error(error);
      setSelectedRealizadas((prev) => {
        const m = new Map(prev)
        const e = m.get(salida.id)
        if (e) m.set(salida.id, { ...e, loading: false })
        return m
      })
      toast.warning(`"${salida.nombre}" no tiene informe aún`)
    }
  }

  // Toggle salida por realizar (solo referencia)
  function togglePorRealizar(salida: SalidaSummary) {
    setSelectedPorRealizar((prev) => {
      const m = new Map(prev)
      if (m.has(salida.id)) m.delete(salida.id)
      else m.set(salida.id, salida)
      return m
    })
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!form.fecha || !form.hora) {
      toast.error("La fecha y la hora son obligatorias")
      return
    }

    try {
      if (tipoActual === "SOCIOS") {
        const informesIds = [...selectedRealizadas.values()]
          .filter((s) => s.informeId !== null)
          .map((s) => s.informeId!)

        // Auto-generar actividadesPorRealizar si está vacío y hay salidas seleccionadas
        let actPorRealizar = form.actividadesPorRealizar
        if (!actPorRealizar.trim() && selectedPorRealizar.size > 0) {
          actPorRealizar = [...selectedPorRealizar.values()]
            .map((s) => `${s.nombre} (${formatFecha(s.fechaInicio)})`)
            .join(", ")
        }

        if (mode === "create") {
          await createMutation.mutateAsync({
            tipoActa: "SOCIOS",
            fecha: form.fecha,
            hora: form.hora,
            lugar: form.lugar || undefined,
            actividadesRealizadasDesc: form.actividadesRealizadasDesc || undefined,
            actividadesPorRealizar: actPorRealizar || undefined,
            varios: form.varios || undefined,
            observaciones: form.observaciones || undefined,
            asistentesIds: selectedAsistentes.size > 0 ? [...selectedAsistentes.keys()] : undefined,
            informesIds: informesIds.length > 0 ? informesIds : undefined,
          })
        } else {
          await updateMutation.mutateAsync({
            tipoActa: "SOCIOS",
            fecha: form.fecha,
            hora: form.hora,
            lugar: form.lugar || undefined,
            actividadesRealizadasDesc: form.actividadesRealizadasDesc || undefined,
            actividadesPorRealizar: form.actividadesPorRealizar || undefined,
            varios: form.varios || undefined,
            observaciones: form.observaciones || undefined,
          })
        }
      } else {
        // DIRECTIVA
        if (mode === "create") {
          await createMutation.mutateAsync({
            tipoActa: "DIRECTIVA",
            fecha: form.fecha,
            hora: form.hora,
            horaFin: form.horaFin || undefined,
            actividadesPorRealizar: form.actividadesPorRealizar || undefined,   // Orden del día
            actividadesRealizadasDesc: form.actividadesRealizadasDesc || undefined, // Desarrollo
            asistentesIds: selectedAsistentes.size > 0 ? [...selectedAsistentes.keys()] : undefined,
          })
        } else {
          await updateMutation.mutateAsync({
            tipoActa: "DIRECTIVA",
            fecha: form.fecha,
            hora: form.hora,
            horaFin: form.horaFin || undefined,
            actividadesPorRealizar: form.actividadesPorRealizar || undefined,
            actividadesRealizadasDesc: form.actividadesRealizadasDesc || undefined,
          })
        }
      }

      toast.success(mode === "create" ? "Acta creada" : "Acta actualizada")
      onClose()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      toast.error(axiosErr.response?.data?.message || "Error al guardar")
    }
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const realizadasList = [...selectedRealizadas.values()]
  const porRealizarList = [...selectedPorRealizar.values()]
  const loadingIds = new Set([...selectedRealizadas.values()].filter((s) => s.loading).map((s) => s.salidaId))

  const tipoLabel = tipoActual === "DIRECTIVA" ? "Directiva" : "Socios"

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center gap-3">
            <DialogTitle>{mode === "create" ? "Nueva acta" : "Editar acta"}</DialogTitle>
            <Badge variant={tipoActual === "DIRECTIVA" ? "secondary" : "outline"}>{tipoLabel}</Badge>
          </div>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">

          {/* ── Fecha / Hora ── */}
          {tipoActual === "SOCIOS" ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Fecha *</Label>
                <Input type="date" value={form.fecha} onChange={(e) => update("fecha", e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label>Hora *</Label>
                <Input type="time" value={form.hora} onChange={(e) => update("hora", e.target.value)} required />
              </div>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-3">
              <div className="space-y-2">
                <Label>Fecha *</Label>
                <Input type="date" value={form.fecha} onChange={(e) => update("fecha", e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label>Hora inicio *</Label>
                <Input type="time" value={form.hora} onChange={(e) => update("hora", e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label>Hora fin</Label>
                <Input type="time" value={form.horaFin} onChange={(e) => update("horaFin", e.target.value)} />
              </div>
            </div>
          )}

          {/* ── Lugar (solo socios) ── */}
          {tipoActual === "SOCIOS" && (
            <div className="space-y-2">
              <Label>Lugar</Label>
              <Select value={form.lugar || "Presencial"} onValueChange={(v) => update("lugar", v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="Presencial">Presencial</SelectItem>
                  <SelectItem value="Virtual">Virtual</SelectItem>
                </SelectContent>
              </Select>
            </div>
          )}

          {/* ── Asistentes ── */}
          <div className="space-y-2">
            <Label>Asistentes</Label>
            <div className="rounded-md border border-border bg-muted/30 p-3 space-y-2">
              {mode === "create" && selectedAsistentes.size > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {[...selectedAsistentes.values()].map((s) => (
                    <Badge key={s.id} variant="secondary" className="flex items-center gap-1 pr-1 text-xs">
                      {s.apellido}, {s.nombre}
                      <button
                        type="button"
                        onClick={() => setSelectedAsistentes((p) => { const m = new Map(p); m.delete(s.id); return m })}
                        className="ml-0.5 rounded hover:bg-muted-foreground/20 p-0.5"
                      >
                        <X className="h-2.5 w-2.5" />
                      </button>
                    </Badge>
                  ))}
                </div>
              )}
              {mode === "edit" && actaData && actaData.asistentes.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {actaData.asistentes.map((a) => (
                    <Badge key={a.id} variant="secondary" className="flex items-center gap-1 pr-1 text-xs">
                      {a.socioApellido ?? a.nombreRaw}, {a.socioNombre ?? ""}
                      <button
                        type="button"
                        onClick={() => eliminarAsistenteMutation.mutate(a.id)}
                        className="ml-0.5 rounded hover:bg-muted-foreground/20 p-0.5"
                        disabled={eliminarAsistenteMutation.isPending}
                      >
                        <X className="h-2.5 w-2.5" />
                      </button>
                    </Badge>
                  ))}
                </div>
              )}
              <SocioSearchPicker
                selectedIds={
                  mode === "create"
                    ? new Set(selectedAsistentes.keys())
                    : new Set((actaData?.asistentes ?? []).flatMap((a) => a.socioId ? [a.socioId] : []))
                }
                onToggle={toggleAsistente}
                disabledIds={addingAsistenteIds}
              />
            </div>
          </div>

          {/* ── Contenido según tipo ── */}
          {tipoActual === "SOCIOS" ? (
            <>
              {/* Actividades realizadas + salidas */}
              <div className="space-y-2">
                <Label>Actividades realizadas</Label>
                <textarea
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  rows={3}
                  placeholder="Descripción de lo realizado en la reunión..."
                  value={form.actividadesRealizadasDesc}
                  onChange={(e) => update("actividadesRealizadasDesc", e.target.value)}
                />
                {mode === "create" && (
                  <div className="rounded-md border border-border bg-muted/30 p-3 space-y-2">
                    <p className="text-xs font-medium text-muted-foreground">Salidas realizadas — selecciona para vincular informes</p>
                    {realizadasList.length > 0 && (
                      <div className="flex flex-wrap gap-1.5">
                        {realizadasList.map((s) => (
                          <Badge key={s.salidaId} variant="secondary" className="flex items-center gap-1 pr-1 text-xs">
                            {formatFecha(s.fechaInicio)} · {s.rutaNombre}
                            {s.loading && <Loader2 className="h-3 w-3 animate-spin ml-0.5" />}
                            {!s.loading && !s.informeId && <span className="text-muted-foreground ml-0.5">(sin inf.)</span>}
                            <button type="button" onClick={() => setSelectedRealizadas((p) => { const m = new Map(p); m.delete(s.salidaId); return m })} className="ml-0.5 rounded hover:bg-muted-foreground/20 p-0.5">
                              <X className="h-2.5 w-2.5" />
                            </button>
                          </Badge>
                        ))}
                      </div>
                    )}
                    <SalidaSearchPicker
                      defaultParams={{ size: 10, sort: "fechaInicio,desc", estado: "REALIZADA" }}
                      selectedIds={new Set(selectedRealizadas.keys())}
                      loadingIds={loadingIds}
                      onToggle={toggleRealizada}
                    />
                  </div>
                )}
              </div>

              {/* Actividades por realizar + próximas salidas */}
              <div className="space-y-2">
                <Label>Actividades por realizar</Label>
                <textarea
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  rows={2}
                  placeholder="Descripción libre (o usa el selector)..."
                  value={form.actividadesPorRealizar}
                  onChange={(e) => update("actividadesPorRealizar", e.target.value)}
                />
                {mode === "create" && (
                  <div className="rounded-md border border-border bg-muted/30 p-3 space-y-2">
                    <p className="text-xs font-medium text-muted-foreground">
                      Próximas salidas — si el texto está vacío, se generará automáticamente
                    </p>
                    {porRealizarList.length > 0 && (
                      <div className="flex flex-wrap gap-1.5">
                        {porRealizarList.map((s) => (
                          <Badge key={s.id} variant="outline" className="flex items-center gap-1 pr-1 text-xs">
                            {formatFecha(s.fechaInicio)} · {s.rutaNombre}
                            <button type="button" onClick={() => togglePorRealizar(s)} className="ml-0.5 rounded hover:bg-muted-foreground/20 p-0.5">
                              <X className="h-2.5 w-2.5" />
                            </button>
                          </Badge>
                        ))}
                      </div>
                    )}
                    <SalidaSearchPicker
                      defaultParams={{ size: 10, sort: "fechaInicio,asc", estado: "PLANIFICADA" }}
                      selectedIds={new Set(selectedPorRealizar.keys())}
                      onToggle={togglePorRealizar}
                    />
                  </div>
                )}
              </div>

              <div className="space-y-2">
                <Label>Varios</Label>
                <textarea
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  rows={2}
                  value={form.varios}
                  onChange={(e) => update("varios", e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label>Observaciones</Label>
                <textarea
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  rows={2}
                  value={form.observaciones}
                  onChange={(e) => update("observaciones", e.target.value)}
                />
              </div>
            </>
          ) : (
            <>
              {/* DIRECTIVA: Orden del día + Desarrollo */}
              <div className="space-y-2">
                <Label>Orden del día</Label>
                <p className="text-xs text-muted-foreground">Lista numerada de temas a tratar</p>
                <textarea
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm font-mono"
                  rows={5}
                  placeholder={"1. Informe de actividades\n2. Planificación de salidas\n3. Asuntos varios"}
                  value={form.actividadesPorRealizar}
                  onChange={(e) => update("actividadesPorRealizar", e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label>Desarrollo de la reunión y compromisos</Label>
                <p className="text-xs text-muted-foreground">Narración completa de cada punto tratado</p>
                <textarea
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm font-mono"
                  rows={10}
                  placeholder={"1. **Informe de actividades:**\n   - punto 1\n   - punto 2\n\n2. **Planificación de salidas:**\n   - punto 1"}
                  value={form.actividadesRealizadasDesc}
                  onChange={(e) => update("actividadesRealizadasDesc", e.target.value)}
                />
              </div>
            </>
          )}

          <div className="flex justify-end gap-3 pt-4 border-t border-border">
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Guardando..." : mode === "create" ? "Crear acta" : "Guardar cambios"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
