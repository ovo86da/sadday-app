import { useState, useEffect } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Checkbox } from "@/components/ui/checkbox"
import { toast } from "sonner"
import { useSalidaLookups, useCreateSalida, useUpdateSalida, useSalidaDetail, verificarSolapamiento } from "@/hooks/use-salidas"
import type { SolapamientoItem } from "@/hooks/use-salidas"
import { AlertTriangle } from "lucide-react"
import { useLookups } from "@/hooks/use-socios"
import { useMountainsList } from "@/hooks/use-mountains"
import { useRutasByMountain, useRutasByActividad, useRutaDetail } from "@/hooks/use-rutas"
import type { CreateSalidaRequest } from "@/types/salidas"
import type { TipoActividad } from "@/types/rutas"

const CATEGORIAS: { value: TipoActividad; label: string }[] = [
  { value: "ALPINISMO", label: "Alpinismo" },
  { value: "CICLISMO",  label: "Ciclismo"  },
  { value: "ESCALADA",  label: "Escalada"  },
  { value: "TREKKING",  label: "Trekking"  },
]

interface Props {
  open: boolean
  onClose: () => void
  mode: "create" | "edit"
  salidaId?: string
}

export function SalidaFormDialog({ open, onClose, mode, salidaId }: Props) {
  const { data: salidaLookups } = useSalidaLookups()
  const { data: socioLookups } = useLookups()
  const { data: salidaData } = useSalidaDetail(mode === "edit" ? salidaId : undefined)
  const createMutation = useCreateSalida()
  const updateMutation = useUpdateSalida(salidaId ?? "")

  const { data: rutaActual } = useRutaDetail(
    mode === "edit" && salidaData?.rutaId ? salidaData.rutaId : undefined
  )

  const [categoria, setCategoria] = useState<TipoActividad | null>(null)
  const [mountainId, setMountainId] = useState<number | null>(null)
  const [nombreManual, setNombreManual] = useState(false)
  const [multiDia, setMultiDia] = useState(false)

  const { data: mountainsPage } = useMountainsList({ size: 500, sort: "nombre,asc" })
  const mountains = mountainsPage?.content ?? []

  // Rutas para Alpinismo: filtradas por montaña
  const { data: rutasMtnPage, isLoading: rutasMtnLoading } = useRutasByMountain(
    categoria === "ALPINISMO" ? mountainId : null
  )
  const rutasMtn = rutasMtnPage?.content ?? []

  // Rutas para Ciclismo / Escalada / Trekking: filtradas por tipo de actividad
  const { data: rutasActPage, isLoading: rutasActLoading } = useRutasByActividad(
    categoria && categoria !== "ALPINISMO" ? categoria : null
  )
  const rutasAct = rutasActPage?.content ?? []

  const rutas       = categoria === "ALPINISMO" ? rutasMtn      : rutasAct
  const rutasLoading = categoria === "ALPINISMO" ? rutasMtnLoading : rutasActLoading

  const [form, setForm] = useState({
    nombre: "", fechaInicio: "", horaEncuentroClub: "06:00", fechaFin: "",
    horaEstimadaRegresoClub: "", rutaId: "",
    publicoObjetivoId: "", formatoSalidaId: "",
    nivelMinimoRequeridoId: "", capacidadMaxima: "",
  })

  useEffect(() => {
    if (mode === "edit" && salidaData) {
      if (salidaData.rutaId && rutaActual) {
        setCategoria(rutaActual.tipoActividad)
        setMountainId(rutaActual.mountainId ?? null)
      } else if (!salidaData.rutaId) {
        setCategoria((salidaData.tipoActividad as TipoActividad) ?? null)
        setMountainId(null)
      }
      setMultiDia(salidaData.fechaInicio !== salidaData.fechaFin)
      setNombreManual(true)
      setForm({
        nombre: salidaData.nombre,
        fechaInicio: salidaData.fechaInicio,
        horaEncuentroClub: salidaData.horaEncuentroClub,
        fechaFin: salidaData.fechaFin,
        horaEstimadaRegresoClub: salidaData.horaEstimadaRegresoClub ?? "",
        rutaId: salidaData.rutaId ? String(salidaData.rutaId) : "",
        publicoObjetivoId: salidaData.publicoObjetivoId ?? "",
        formatoSalidaId: salidaData.formatoSalidaId ?? "",
        nivelMinimoRequeridoId: salidaData.nivelMinimoRequeridoId ?? "",
        capacidadMaxima: salidaData.capacidadMaxima ? String(salidaData.capacidadMaxima) : "",
      })
    } else if (mode === "create") {
      setCategoria(null)
      setMountainId(null)
      setNombreManual(false)
      setMultiDia(false)
      setForm({
        nombre: "", fechaInicio: "", horaEncuentroClub: "06:00", fechaFin: "",
        horaEstimadaRegresoClub: "", rutaId: "",
        publicoObjetivoId: "", formatoSalidaId: "",
        nivelMinimoRequeridoId: "", capacidadMaxima: "",
      })
    }
  }, [mode, salidaData, rutaActual, open])

  const update = (field: string, value: string) => setForm((p) => ({ ...p, [field]: value }))

  const handleCategoriaChange = (value: string) => {
    setCategoria(value as TipoActividad)
    setMountainId(null)
    setNombreManual(false)
    setForm((p) => ({ ...p, rutaId: "", nombre: "" }))
  }

  const handleMountainChange = (id: string) => {
    setMountainId(Number(id))
    setNombreManual(false)
    setForm((p) => ({ ...p, rutaId: "", nombre: "" }))
  }

  const handleRutaChange = (rutaId: string) => {
    const ruta = rutas.find((r) => String(r.id) === rutaId)
    let nombre = ""
    if (ruta) {
      if (categoria === "ALPINISMO" && mountainId) {
        const mountain = mountains.find((m) => m.id === mountainId)
        nombre = mountain ? `${mountain.nombre} — ${ruta.nombre}` : ruta.nombre
      } else {
        nombre = ruta.nombre
      }
    }
    setNombreManual(false)
    setForm((p) => ({ ...p, rutaId, nombre }))
  }

  const [solapadas, setSolapadas] = useState<SolapamientoItem[]>([])
  const [solapadoConfirmOpen, setSolapadoConfirmOpen] = useState(false)
  const [pendingPayload, setPendingPayload] = useState<CreateSalidaRequest | null>(null)
  const [checkingOverlap, setCheckingOverlap] = useState(false)

  const doSave = async (payload: CreateSalidaRequest) => {
    try {
      if (mode === "create") {
        await createMutation.mutateAsync(payload)
        toast.success("Salida creada")
      } else {
        await updateMutation.mutateAsync(payload)
        toast.success("Salida actualizada")
      }
      onClose()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      toast.error(axiosErr.response?.data?.message || "Error al guardar")
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const payload: CreateSalidaRequest = {
      nombre: form.nombre,
      fechaInicio: form.fechaInicio,
      horaEncuentroClub: form.horaEncuentroClub,
      fechaFin: form.fechaFin,
      horaEstimadaRegresoClub: form.horaEstimadaRegresoClub || undefined,
      rutaId: form.rutaId ? Number(form.rutaId) : undefined,
      tipoActividad: categoria ?? undefined,
      publicoObjetivoId: form.publicoObjetivoId || undefined,
      formatoSalidaId: form.formatoSalidaId || undefined,
      nivelMinimoRequeridoId: form.nivelMinimoRequeridoId || undefined,
      capacidadMaxima: form.capacidadMaxima ? Number(form.capacidadMaxima) : undefined,
    }

    setCheckingOverlap(true)
    try {
      const overlaps = await verificarSolapamiento(
        form.fechaInicio,
        form.fechaFin,
        mode === "edit" ? salidaId : undefined,
      )
      if (overlaps.length > 0) {
        setSolapadas(overlaps)
        setPendingPayload(payload)
        setSolapadoConfirmOpen(true)
        return
      }
    } catch (error) { console.error(error);
      // Si falla la verificación no bloqueamos el guardado
    } finally {
      setCheckingOverlap(false)
    }

    await doSave(payload)
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending || checkingOverlap
  const canSubmit = !!categoria && !!form.nombre && !!form.fechaInicio && !!form.fechaFin

  const routeSelectorEnabled = categoria === "ALPINISMO" ? !!mountainId : true

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {solapadoConfirmOpen
              ? "Solapamiento de fechas"
              : mode === "create" ? "Nueva salida" : "Editar salida"}
          </DialogTitle>
        </DialogHeader>

        {/* Vista de confirmación de solapamiento */}
        {solapadoConfirmOpen && (
          <div className="space-y-4">
            <div className="flex items-start gap-3">
              <div className="rounded-full bg-amber-100 p-2 shrink-0">
                <AlertTriangle className="h-5 w-5 text-amber-600" />
              </div>
              <p className="text-sm text-muted-foreground mt-2">
                Las fechas seleccionadas coinciden con{" "}
                {solapadas.length === 1 ? "otra salida activa" : `${solapadas.length} salidas activas`}:
              </p>
            </div>

            <ul className="space-y-1.5 text-sm">
              {solapadas.map((s) => (
                <li key={s.id} className="flex items-center gap-2 rounded-md border border-amber-300/50 bg-amber-50/60 px-3 py-2">
                  <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-amber-500" />
                  <div>
                    <span className="font-medium">{s.nombre}</span>
                    <span className="text-xs text-muted-foreground ml-2">
                      {s.fechaInicio === s.fechaFin ? s.fechaInicio : `${s.fechaInicio} → ${s.fechaFin}`}
                    </span>
                  </div>
                </li>
              ))}
            </ul>

            <p className="text-sm text-foreground">¿Desea continuar de todos modos y registrar esta salida?</p>

            <div className="flex gap-3 pt-1">
              <Button
                className="flex-1"
                disabled={isSubmitting}
                onClick={async () => {
                  setSolapadoConfirmOpen(false)
                  if (pendingPayload) await doSave(pendingPayload)
                }}
              >
                {isSubmitting ? "Guardando..." : "Sí, continuar"}
              </Button>
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => {
                  setSolapadoConfirmOpen(false)
                  setPendingPayload(null)
                }}
              >
                Volver atrás
              </Button>
            </div>
          </div>
        )}

        {/* Formulario principal */}
        {!solapadoConfirmOpen && (
          <form onSubmit={handleSubmit} className="space-y-4">

            {/* 1. Categoría */}
            <div className="space-y-2">
              <Label>Categoría</Label>
              <Select value={categoria ?? ""} onValueChange={handleCategoriaChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Seleccionar categoría..." />
                </SelectTrigger>
                <SelectContent>
                  {CATEGORIAS.map((c) => (
                    <SelectItem key={c.value} value={c.value}>{c.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* 2. Montaña (solo Alpinismo) */}
            {categoria === "ALPINISMO" && (
              <div className="space-y-2">
                <Label>Montaña</Label>
                <Select
                  value={mountainId ? String(mountainId) : ""}
                  onValueChange={handleMountainChange}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Seleccionar montaña..." />
                  </SelectTrigger>
                  <SelectContent>
                    {mountains.map((m) => (
                      <SelectItem key={m.id} value={String(m.id)}>
                        {m.nombre}
                        <span className="ml-2 text-xs text-muted-foreground">
                          {m.altitud.toLocaleString()} msnm
                        </span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {/* 3. Ruta (si hay categoría seleccionada) */}
            {categoria && (
              <div className="space-y-2">
                <Label>Ruta</Label>
                <Select
                  value={form.rutaId}
                  onValueChange={handleRutaChange}
                  disabled={!routeSelectorEnabled || rutasLoading}
                >
                  <SelectTrigger>
                    <SelectValue
                      placeholder={
                        categoria === "ALPINISMO" && !mountainId
                          ? "Primero selecciona una montaña"
                          : rutasLoading
                          ? "Cargando rutas..."
                          : rutas.length === 0
                          ? "No hay rutas aprobadas para esta categoría"
                          : "Seleccionar ruta..."
                      }
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {rutas.map((r) => (
                      <SelectItem key={r.id} value={String(r.id)}>
                        {r.nombre}
                        {r.dificultadResumen && (
                          <span className="ml-2 text-xs text-muted-foreground">{r.dificultadResumen}</span>
                        )}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {/* 4. Nombre */}
            <div className="space-y-2">
              <Label>Nombre *</Label>
              <Input
                value={form.nombre}
                onChange={(e) => { setNombreManual(true); update("nombre", e.target.value) }}
                placeholder={
                  categoria && form.rutaId && !nombreManual
                    ? "Se completa al seleccionar la ruta"
                    : "Nombre de la salida"
                }
                required
              />
            </div>

            {/* 5. Público objetivo y formato */}
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Público objetivo</Label>
                <Select value={form.publicoObjetivoId} onValueChange={(v) => update("publicoObjetivoId", v)}>
                  <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                  <SelectContent>
                    {salidaLookups?.publicosObjetivo.map((p) => (
                      <SelectItem key={p.id} value={p.id}>{p.nombre}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Formato</Label>
                <Select value={form.formatoSalidaId} onValueChange={(v) => update("formatoSalidaId", v)}>
                  <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                  <SelectContent>
                    {salidaLookups?.formatosSalida.map((f) => (
                      <SelectItem key={f.id} value={f.id}>{f.nombre}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* 6. Fechas y horas */}
            {!multiDia ? (
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>Fecha *</Label>
                  <Input
                    type="date"
                    value={form.fechaInicio}
                    onChange={(e) => setForm((p) => ({ ...p, fechaInicio: e.target.value, fechaFin: e.target.value }))}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label>Hora encuentro *</Label>
                  <Input type="time" value={form.horaEncuentroClub} onChange={(e) => update("horaEncuentroClub", e.target.value)} required />
                </div>
                <div className="space-y-2">
                  <Label>Hora regreso est.</Label>
                  <Input type="time" value={form.horaEstimadaRegresoClub} onChange={(e) => update("horaEstimadaRegresoClub", e.target.value)} />
                </div>
              </div>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>Fecha inicio *</Label>
                  <Input type="date" value={form.fechaInicio} onChange={(e) => update("fechaInicio", e.target.value)} required />
                </div>
                <div className="space-y-2">
                  <Label>Fecha fin *</Label>
                  <Input type="date" value={form.fechaFin} onChange={(e) => update("fechaFin", e.target.value)} required />
                </div>
                <div className="space-y-2">
                  <Label>Hora encuentro *</Label>
                  <Input type="time" value={form.horaEncuentroClub} onChange={(e) => update("horaEncuentroClub", e.target.value)} required />
                </div>
                <div className="space-y-2">
                  <Label>Hora regreso est.</Label>
                  <Input type="time" value={form.horaEstimadaRegresoClub} onChange={(e) => update("horaEstimadaRegresoClub", e.target.value)} />
                </div>
              </div>
            )}

            <div className="flex items-center gap-2">
              <Checkbox
                id="multiDia"
                checked={multiDia}
                onCheckedChange={(checked) => {
                  const isMulti = !!checked
                  setMultiDia(isMulti)
                  if (!isMulti) {
                    // Al volver a un día, igualar fechaFin a fechaInicio
                    setForm((p) => ({ ...p, fechaFin: p.fechaInicio }))
                  }
                }}
              />
              <Label htmlFor="multiDia" className="font-normal cursor-pointer">Salida de varios días</Label>
            </div>

            {/* 7. Nivel y capacidad */}
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Nivel mínimo</Label>
                <Select value={form.nivelMinimoRequeridoId} onValueChange={(v) => update("nivelMinimoRequeridoId", v)}>
                  <SelectTrigger><SelectValue placeholder="Sin restricción" /></SelectTrigger>
                  <SelectContent>
                    {socioLookups?.clasificaciones.map((c) => (
                      <SelectItem key={c.id} value={c.id}>{c.nombre}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Capacidad máxima</Label>
                <Input type="number" min={1} value={form.capacidadMaxima} onChange={(e) => update("capacidadMaxima", e.target.value)} placeholder="Sin límite" />
              </div>
            </div>

            <div className="pt-4 border-t border-border flex justify-end gap-3">
              <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
              <Button type="submit" disabled={isSubmitting || !canSubmit}>
                {isSubmitting ? "Guardando..." : mode === "create" ? "Crear salida" : "Guardar cambios"}
              </Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
