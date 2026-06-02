import { useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import { Check, ChevronsUpDown, ChevronUp, ChevronDown, X } from "lucide-react"
import { cn } from "@/lib/utils"
import { toast } from "sonner"
import { useCreateRuta, useUpdateRuta, useRutaDetail } from "@/hooks/use-rutas"
import { useMountainsList, useMountainLookups } from "@/hooks/use-mountains"
import type { RutaSummary, CreateRutaRequest, TipoActividad } from "@/types/rutas"
import { TIPO_ACTIVIDAD_LABELS, TIPOS_ESCALADA, TIPOS_BICICLETA, DIFICULTADES_CICLISMO, TIPO_BICICLETA_LABELS } from "@/types/rutas"

interface Props {
  open: boolean
  onClose: () => void
  mode: "create" | "edit"
  ruta?: RutaSummary
  initialMountainId?: number
}

const EMPTY_FORM = {
  nombre: "", tipoActividad: "ALPINISMO" as TipoActividad,
  mountainId: "", lugarReferencia: "", sectorZona: "",
  longitudKm: "", desnivelM: "", duracionDias: "", duracionHoras: "",
  peligrosNotas: "", requierePermisos: "false", documentacionUrl: "", trackUrl: "",
  nivelMinimoSocioId: "",
  // Alpinismo
  escalaAlpinaIfasId: "", dificultadRocaId: "", dificultadHieloId: "",
  compromisoId: "", yosemiteId: "", saddayNivelTecnicoId: "", saddayNivelFisicoId: "",
  equipoMontanaId: "",
  // Escalada
  tipoEscalada: "", numCintas: "", alturaViaM: "", tipoRoca: "",
  // Trekking
  dificultadSenderismoId: "", esCircular: "false", fuentesAgua: "false", tipoTerreno: "",
  // Ciclismo
  tipoBicicleta: "", dificultadTecnicaCiclismo: "", superficiePredominante: "", ciclabilidadPct: "",
  // Integral
  dificultadMaximaDescripcion: "", descripcionItinerario: "",
}

export function RutaFormDialog({ open, onClose, mode, ruta, initialMountainId }: Props) {
  const { data: rutaDetail } = useRutaDetail(mode === "edit" ? ruta?.id : undefined)
  const createMutation = useCreateRuta()
  const updateMutation = useUpdateRuta(ruta?.id ?? 0)

  const { data: mountainsPage } = useMountainsList({ size: 500, sort: "nombre,asc" })
  const mountains = mountainsPage?.content ?? []
  const { data: lookups } = useMountainLookups()

  const [mountainOpen, setMountainOpen] = useState(false)
  const [addCumbreOpen, setAddCumbreOpen] = useState(false)
  const [integralCumbres, setIntegralCumbres] = useState<number[]>([])
  const [form, setForm] = useState({ ...EMPTY_FORM })

  const syncKey = `${mode}-${rutaDetail?.id ?? "new"}-${open}`
  const [prevSyncKey, setPrevSyncKey] = useState(syncKey)
  if (syncKey !== prevSyncKey) {
    setPrevSyncKey(syncKey)
    if (mode === "edit" && rutaDetail) {
      const alp = rutaDetail.alpinismo
      const esc = rutaDetail.escalada
      const trk = rutaDetail.trekking
      const cic = rutaDetail.ciclismo
      const int = rutaDetail.integral
      setForm({
        nombre:              rutaDetail.nombre,
        tipoActividad:       rutaDetail.tipoActividad,
        mountainId:          rutaDetail.mountainId ? String(rutaDetail.mountainId) : "",
        lugarReferencia:     rutaDetail.lugarReferencia ?? "",
        sectorZona:          rutaDetail.sectorZona ?? "",
        longitudKm:          rutaDetail.longitudKm ? String(rutaDetail.longitudKm) : "",
        desnivelM:           rutaDetail.desnivelM ? String(rutaDetail.desnivelM) : "",
        duracionDias:        rutaDetail.duracionDias ? String(rutaDetail.duracionDias) : "",
        duracionHoras:       rutaDetail.duracionHoras ? String(rutaDetail.duracionHoras) : "",
        peligrosNotas:       rutaDetail.peligrosNotas ?? "",
        requierePermisos:    String(rutaDetail.requierePermisos),
        documentacionUrl:    rutaDetail.documentacionUrl ?? "",
        trackUrl:            rutaDetail.trackUrl ?? "",
        nivelMinimoSocioId:  rutaDetail.nivelMinimoSocioId ?? "",
        // Alpinismo
        escalaAlpinaIfasId:  alp?.escalaAlpinaIfasId ?? "",
        dificultadRocaId:    alp?.dificultadRocaId ?? esc?.dificultadRocaId ?? "",
        dificultadHieloId:   alp?.dificultadHieloId ?? "",
        compromisoId:        alp?.compromisoId ?? "",
        yosemiteId:          alp?.yosemiteId ?? "",
        saddayNivelTecnicoId:alp?.saddayNivelTecnicoId ?? "",
        saddayNivelFisicoId: alp?.saddayNivelFisicoId ?? "",
        equipoMontanaId:     alp?.equipoMontanaId ? String(alp.equipoMontanaId) : "",
        // Escalada
        tipoEscalada:        esc?.tipoEscalada ?? "",
        numCintas:           esc?.numCintas ? String(esc.numCintas) : "",
        alturaViaM:          esc?.alturaViaM ? String(esc.alturaViaM) : "",
        tipoRoca:            esc?.tipoRoca ?? "",
        // Trekking
        dificultadSenderismoId: trk?.dificultadId ?? "",
        esCircular:          trk ? String(trk.esCircular) : "false",
        fuentesAgua:         trk ? String(trk.fuentesAgua) : "false",
        tipoTerreno:         trk?.tipoTerreno ?? "",
        // Ciclismo
        tipoBicicleta:       cic?.tipoBicicleta ?? "",
        dificultadTecnicaCiclismo: cic?.dificultadTecnica ?? "",
        superficiePredominante:    cic?.superficiePredominante ?? "",
        ciclabilidadPct:     cic?.ciclabilidadPct ? String(cic.ciclabilidadPct) : "",
        // Integral
        dificultadMaximaDescripcion: int?.dificultadMaximaDescripcion ?? "",
        descripcionItinerario:       int?.descripcionItinerario ?? "",
      })
      setIntegralCumbres(int?.cumbres.map((c) => c.mountainId) ?? [])
    } else if (mode === "create") {
      setForm({ ...EMPTY_FORM, mountainId: initialMountainId ? String(initialMountainId) : "" })
      setIntegralCumbres([])
    }
  }

  const update = (field: string, value: string) => setForm((p) => ({ ...p, [field]: value }))

  const selectedMountain = mountains.find((m) => String(m.id) === form.mountainId)
  const tipo = form.tipoActividad as TipoActividad

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (tipo === "INTEGRAL") {
      if (integralCumbres.length < 2) {
        toast.error("Una integral debe incluir al menos 2 cumbres")
        return
      }
      if (!form.lugarReferencia.trim()) {
        toast.error("Indica el macizo o zona de la integral")
        return
      }
    } else if (!form.mountainId && !form.lugarReferencia.trim()) {
      toast.error("Indica una montaña o un lugar de referencia")
      return
    }

    const payload: CreateRutaRequest = {
      nombre:           form.nombre,
      tipoActividad:    tipo,
      mountainId:       form.mountainId ? Number(form.mountainId) : undefined,
      lugarReferencia:  form.lugarReferencia || undefined,
      sectorZona:       form.sectorZona || undefined,
      longitudKm:       form.longitudKm ? Number(form.longitudKm) : undefined,
      desnivelM:        form.desnivelM ? Number(form.desnivelM) : undefined,
      duracionDias:     form.duracionDias ? Number(form.duracionDias) : undefined,
      duracionHoras:    form.duracionHoras ? Number(form.duracionHoras) : undefined,
      peligrosNotas:    form.peligrosNotas || undefined,
      requierePermisos: form.requierePermisos === "true",
      documentacionUrl: form.documentacionUrl || undefined,
      trackUrl:         form.trackUrl || undefined,
      nivelMinimoSocioId: form.nivelMinimoSocioId || undefined,

      // Alpinismo
      ...(tipo === "ALPINISMO" && {
        escalaAlpinaIfasId:  form.escalaAlpinaIfasId,
        dificultadRocaId:    form.dificultadRocaId,
        dificultadHieloId:   form.dificultadHieloId,
        compromisoId:        form.compromisoId,
        yosemiteId:          form.yosemiteId,
        saddayNivelTecnicoId:form.saddayNivelTecnicoId,
        saddayNivelFisicoId: form.saddayNivelFisicoId,
        equipoMontanaId:     form.equipoMontanaId ? Number(form.equipoMontanaId) : undefined,
      }),

      // Escalada
      ...(tipo === "ESCALADA" && {
        dificultadRocaId: form.dificultadRocaId,
        tipoEscalada:     form.tipoEscalada,
        numCintas:        form.numCintas ? Number(form.numCintas) : undefined,
        alturaViaM:       form.alturaViaM ? Number(form.alturaViaM) : undefined,
        tipoRoca:         form.tipoRoca || undefined,
      }),

      // Trekking
      ...(tipo === "TREKKING" && {
        dificultadSenderismoId: form.dificultadSenderismoId,
        esCircular:  form.esCircular === "true",
        fuentesAgua: form.fuentesAgua === "true",
        tipoTerreno: form.tipoTerreno || undefined,
      }),

      // Ciclismo
      ...(tipo === "CICLISMO" && {
        tipoBicicleta:            form.tipoBicicleta,
        dificultadTecnicaCiclismo:form.dificultadTecnicaCiclismo || undefined,
        superficiePredominante:   form.superficiePredominante || undefined,
        ciclabilidadPct:          form.ciclabilidadPct ? Number(form.ciclabilidadPct) : undefined,
      }),

      // Integral
      ...(tipo === "INTEGRAL" && {
        cumbresMountainIds:          integralCumbres,
        dificultadMaximaDescripcion: form.dificultadMaximaDescripcion || undefined,
        descripcionItinerario:       form.descripcionItinerario || undefined,
      }),
    }

    try {
      if (mode === "create") {
        await createMutation.mutateAsync(payload)
        toast.success("Ruta propuesta correctamente")
      } else {
        await updateMutation.mutateAsync(payload)
        toast.success("Ruta actualizada")
      }
      onClose()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      toast.error(axiosErr.response?.data?.message || "Error al guardar")
    }
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{mode === "create" ? "Proponer ruta" : "Editar ruta"}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-6">

          {/* ── Tipo de actividad ─────────────────────────────── */}
          <fieldset className="space-y-4">
            <legend className="text-sm font-semibold text-foreground">Tipo de actividad</legend>
            <div className="grid gap-2 grid-cols-3 sm:grid-cols-5">
              {(Object.keys(TIPO_ACTIVIDAD_LABELS) as TipoActividad[]).map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => update("tipoActividad", t)}
                  className={cn(
                    "rounded-lg border p-3 text-sm font-medium transition-colors text-center",
                    form.tipoActividad === t
                      ? "border-primary bg-primary/10 text-primary"
                      : "border-border bg-card text-muted-foreground hover:border-primary/50"
                  )}
                >
                  {TIPO_ACTIVIDAD_LABELS[t]}
                </button>
              ))}
            </div>
          </fieldset>

          {/* ── Datos básicos ─────────────────────────────────── */}
          <fieldset className="space-y-4">
            <legend className="text-sm font-semibold text-foreground">Datos generales</legend>
            <div className="grid gap-4 sm:grid-cols-2">

              <div className="space-y-2">
                <Label>Nombre *</Label>
                <Input value={form.nombre} onChange={(e) => update("nombre", e.target.value)} required />
              </div>

              {/* Combobox montaña — oculto en INTEGRAL */}
              {tipo !== "INTEGRAL" && (
                <div className="space-y-2">
                  <Label>Montaña {tipo === "ALPINISMO" ? "*" : "(opcional)"}</Label>
                  <Popover open={mountainOpen} onOpenChange={setMountainOpen}>
                    <PopoverTrigger asChild>
                      <Button type="button" variant="outline" role="combobox"
                        aria-expanded={mountainOpen} className="w-full justify-between font-normal">
                        {selectedMountain
                          ? <span>{selectedMountain.nombre} <span className="text-muted-foreground text-xs">({selectedMountain.altitud.toLocaleString()} m)</span></span>
                          : <span className="text-muted-foreground">Selecciona una montaña...</span>
                        }
                        <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                      </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
                      <Command>
                        <CommandInput placeholder="Buscar montaña..." />
                        <CommandList>
                          <CommandEmpty>No se encontraron montañas.</CommandEmpty>
                          <CommandGroup>
                            {mountains.map((m) => (
                              <CommandItem key={m.id} value={`${m.nombre} ${m.region}`}
                                onSelect={() => { update("mountainId", String(m.id)); setMountainOpen(false) }}>
                                <Check className={cn("mr-2 h-4 w-4", form.mountainId === String(m.id) ? "opacity-100" : "opacity-0")} />
                                <span>{m.nombre}</span>
                                <span className="ml-auto text-xs text-muted-foreground">{m.altitud.toLocaleString()} m</span>
                              </CommandItem>
                            ))}
                          </CommandGroup>
                        </CommandList>
                      </Command>
                    </PopoverContent>
                  </Popover>
                </div>
              )}

              {/* Lugar referencia */}
              {(tipo === "INTEGRAL" || tipo === "TREKKING" || tipo === "CICLISMO" || !form.mountainId) && (
                <div className={cn("space-y-2", tipo !== "INTEGRAL" && "sm:col-span-2")}>
                  <Label>
                    {tipo === "INTEGRAL" ? "Macizo / Zona *" : `Lugar / Zona de referencia ${!form.mountainId ? "*" : ""}`}
                  </Label>
                  <Input value={form.lugarReferencia} onChange={(e) => update("lugarReferencia", e.target.value)}
                    placeholder={tipo === "INTEGRAL" ? "Ej: Ilinizas, Nudo del Toisán..." : "Ej: Quilotoa Loop, Ruta Pasochoa-Antisana..."} />
                </div>
              )}

              <div className="space-y-2"><Label>Sector / Zona</Label><Input value={form.sectorZona} onChange={(e) => update("sectorZona", e.target.value)} /></div>
              <div className="space-y-2"><Label>Longitud (km)</Label><Input type="number" step="0.01" value={form.longitudKm} onChange={(e) => update("longitudKm", e.target.value)} /></div>
              <div className="space-y-2"><Label>Desnivel (m)</Label><Input type="number" value={form.desnivelM} onChange={(e) => update("desnivelM", e.target.value)} /></div>

              {tipo === "ALPINISMO" ? (
                <div className="space-y-2"><Label>Duración (días)</Label><Input type="number" min={1} value={form.duracionDias} onChange={(e) => update("duracionDias", e.target.value)} /></div>
              ) : (
                <div className="space-y-2"><Label>Duración (horas)</Label><Input type="number" min={1} value={form.duracionHoras} onChange={(e) => update("duracionHoras", e.target.value)} /></div>
              )}

              <div className="space-y-2">
                <Label>Nivel mínimo requerido</Label>
                <Select value={form.nivelMinimoSocioId || "__none__"} onValueChange={(v) => update("nivelMinimoSocioId", v === "__none__" ? "" : v)}>
                  <SelectTrigger><SelectValue placeholder="Sin restricción" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">Sin restricción</SelectItem>
                    {lookups?.clasificacionesSocio.map((c) => (
                      <SelectItem key={c.id} value={c.id}>{c.nombre}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="sm:col-span-2 space-y-2">
                <Label>Peligros / Notas</Label>
                <Input value={form.peligrosNotas} onChange={(e) => update("peligrosNotas", e.target.value)} />
              </div>
            </div>
          </fieldset>

          {/* ── Alpinismo ─────────────────────────────────────── */}
          {tipo === "ALPINISMO" && (
            <fieldset className="space-y-4">
              <legend className="text-sm font-semibold text-foreground">Dificultad técnica (Alpinismo)</legend>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>Escala Alpina IFAS *</Label>
                  <Select value={form.escalaAlpinaIfasId} onValueChange={(v) => update("escalaAlpinaIfasId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.escalasAlpina.map((e) => <SelectItem key={e.id} value={e.id}>{e.grado} — {e.nombre}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Dificultad Roca *</Label>
                  <Select value={form.dificultadRocaId} onValueChange={(v) => update("dificultadRocaId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.dificultadesRoca.map((r) => <SelectItem key={r.id} value={r.id}>{r.uiaa} ({r.francesa})</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Dificultad Hielo *</Label>
                  <Select value={form.dificultadHieloId} onValueChange={(v) => update("dificultadHieloId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.dificultadesHielo.map((h) => <SelectItem key={h.id} value={h.id}>{h.grado}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Compromiso *</Label>
                  <Select value={form.compromisoId} onValueChange={(v) => update("compromisoId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.compromisos.map((c) => <SelectItem key={c.id} value={c.id}>{c.tipo}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Yosemite *</Label>
                  <Select value={form.yosemiteId} onValueChange={(v) => update("yosemiteId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.yosemiteClases.map((y) => <SelectItem key={y.id} value={y.id}>{y.tipo}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Sadday Nivel Técnico *</Label>
                  <Select value={form.saddayNivelTecnicoId} onValueChange={(v) => update("saddayNivelTecnicoId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.saddayRiesgos.map((s) => <SelectItem key={s.id} value={s.id}>{s.escala}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Sadday Nivel Físico *</Label>
                  <Select value={form.saddayNivelFisicoId} onValueChange={(v) => update("saddayNivelFisicoId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.saddayRiesgos.map((s) => <SelectItem key={s.id} value={s.id}>{s.escala}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Equipo recomendado</Label>
                  <Select value={form.equipoMontanaId || "__none__"} onValueChange={(v) => update("equipoMontanaId", v === "__none__" ? "" : v)}>
                    <SelectTrigger><SelectValue placeholder="Sin especificar" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__">Sin especificar</SelectItem>
                      {lookups?.equipos.map((e) => <SelectItem key={e.id} value={String(e.id)}>{e.nombre}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </fieldset>
          )}

          {/* ── Escalada ──────────────────────────────────────── */}
          {tipo === "ESCALADA" && (
            <fieldset className="space-y-4">
              <legend className="text-sm font-semibold text-foreground">Dificultad técnica (Escalada)</legend>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>Grado de roca *</Label>
                  <Select value={form.dificultadRocaId} onValueChange={(v) => update("dificultadRocaId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.dificultadesRoca.map((r) => <SelectItem key={r.id} value={r.id}>{r.uiaa} ({r.francesa})</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Tipo de escalada *</Label>
                  <Select value={form.tipoEscalada} onValueChange={(v) => update("tipoEscalada", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{TIPOS_ESCALADA.map((t) => <SelectItem key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2"><Label>N° de cintas</Label><Input type="number" min={0} value={form.numCintas} onChange={(e) => update("numCintas", e.target.value)} /></div>
                <div className="space-y-2"><Label>Altura de la vía (m)</Label><Input type="number" min={1} value={form.alturaViaM} onChange={(e) => update("alturaViaM", e.target.value)} /></div>
                <div className="space-y-2 sm:col-span-2"><Label>Tipo de roca</Label><Input value={form.tipoRoca} onChange={(e) => update("tipoRoca", e.target.value)} placeholder="Basalto, granito, caliza..." /></div>
              </div>
            </fieldset>
          )}

          {/* ── Trekking ──────────────────────────────────────── */}
          {tipo === "TREKKING" && (
            <fieldset className="space-y-4">
              <legend className="text-sm font-semibold text-foreground">Características del trekking</legend>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>Dificultad *</Label>
                  <Select value={form.dificultadSenderismoId} onValueChange={(v) => update("dificultadSenderismoId", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{lookups?.dificultadesSenderismo?.map((d) => <SelectItem key={d.id} value={d.id}>{d.nombre}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2"><Label>Tipo de terreno</Label><Input value={form.tipoTerreno} onChange={(e) => update("tipoTerreno", e.target.value)} placeholder="Sendero, páramo, bosque..." /></div>
                <div className="space-y-2">
                  <Label>¿Ruta circular?</Label>
                  <Select value={form.esCircular} onValueChange={(v) => update("esCircular", v)}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent><SelectItem value="false">No (ida y vuelta)</SelectItem><SelectItem value="true">Sí (circular)</SelectItem></SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>¿Fuentes de agua?</Label>
                  <Select value={form.fuentesAgua} onValueChange={(v) => update("fuentesAgua", v)}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent><SelectItem value="false">No</SelectItem><SelectItem value="true">Sí</SelectItem></SelectContent>
                  </Select>
                </div>
              </div>
            </fieldset>
          )}

          {/* ── Ciclismo ──────────────────────────────────────── */}
          {tipo === "CICLISMO" && (
            <fieldset className="space-y-4">
              <legend className="text-sm font-semibold text-foreground">Características del ciclismo</legend>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>Tipo de bicicleta *</Label>
                  <Select value={form.tipoBicicleta} onValueChange={(v) => update("tipoBicicleta", v)} required>
                    <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                    <SelectContent>{TIPOS_BICICLETA.map((t) => <SelectItem key={t} value={t}>{TIPO_BICICLETA_LABELS[t]}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Dificultad técnica (Singletrail)</Label>
                  <Select value={form.dificultadTecnicaCiclismo || "__none__"} onValueChange={(v) => update("dificultadTecnicaCiclismo", v === "__none__" ? "" : v)}>
                    <SelectTrigger><SelectValue placeholder="Sin clasificar" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__">Sin clasificar</SelectItem>
                      {DIFICULTADES_CICLISMO.map((d) => <SelectItem key={d} value={d}>{d}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2"><Label>Superficie predominante</Label><Input value={form.superficiePredominante} onChange={(e) => update("superficiePredominante", e.target.value)} placeholder="Lastrado, singletrack, empedrado..." /></div>
                <div className="space-y-2"><Label>Ciclabilidad (%)</Label><Input type="number" min={0} max={100} step={5} value={form.ciclabilidadPct} onChange={(e) => update("ciclabilidadPct", e.target.value)} placeholder="0–100" /></div>
              </div>
            </fieldset>
          )}

          {/* ── Integral ──────────────────────────────────────── */}
          {tipo === "INTEGRAL" && (
            <fieldset className="space-y-4">
              <legend className="text-sm font-semibold text-foreground">Cumbres de la integral *</legend>

              {/* Selector para añadir cumbres */}
              <Popover open={addCumbreOpen} onOpenChange={setAddCumbreOpen}>
                <PopoverTrigger asChild>
                  <Button type="button" variant="outline" className="w-full justify-between font-normal">
                    <span className="text-muted-foreground">Añadir cumbre...</span>
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
                  <Command>
                    <CommandInput placeholder="Buscar montaña..." />
                    <CommandList>
                      <CommandEmpty>No se encontraron montañas.</CommandEmpty>
                      <CommandGroup>
                        {mountains
                          .filter((m) => !integralCumbres.includes(m.id))
                          .map((m) => (
                            <CommandItem key={m.id} value={`${m.nombre} ${m.region}`}
                              onSelect={() => {
                                setIntegralCumbres((prev) => [...prev, m.id])
                                setAddCumbreOpen(false)
                              }}>
                              <span>{m.nombre}</span>
                              <span className="ml-auto text-xs text-muted-foreground">{m.altitud.toLocaleString()} m</span>
                            </CommandItem>
                          ))}
                      </CommandGroup>
                    </CommandList>
                  </Command>
                </PopoverContent>
              </Popover>

              {/* Lista ordenada de cumbres */}
              {integralCumbres.length > 0 && (
                <ul className="space-y-1.5">
                  {integralCumbres.map((mid, idx) => {
                    const m = mountains.find((x) => x.id === mid)
                    if (!m) return null
                    return (
                      <li key={mid} className="flex items-center gap-2 rounded-lg border border-border bg-card px-3 py-2">
                        <span className="text-xs font-bold text-muted-foreground w-5 shrink-0 text-center">{idx + 1}</span>
                        <span className="flex-1 text-sm font-medium">{m.nombre}</span>
                        <span className="text-xs text-muted-foreground">{m.altitud.toLocaleString()} m</span>
                        <div className="flex gap-0.5 shrink-0">
                          <Button type="button" variant="ghost" size="icon" className="h-7 w-7"
                            disabled={idx === 0}
                            onClick={() => setIntegralCumbres((prev) => {
                              const next = [...prev]; [next[idx - 1], next[idx]] = [next[idx], next[idx - 1]]; return next
                            })}>
                            <ChevronUp className="h-3.5 w-3.5" />
                          </Button>
                          <Button type="button" variant="ghost" size="icon" className="h-7 w-7"
                            disabled={idx === integralCumbres.length - 1}
                            onClick={() => setIntegralCumbres((prev) => {
                              const next = [...prev]; [next[idx], next[idx + 1]] = [next[idx + 1], next[idx]]; return next
                            })}>
                            <ChevronDown className="h-3.5 w-3.5" />
                          </Button>
                          <Button type="button" variant="ghost" size="icon" className="h-7 w-7"
                            onClick={() => setIntegralCumbres((prev) => prev.filter((_, i) => i !== idx))}>
                            <X className="h-3.5 w-3.5 text-destructive" />
                          </Button>
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )}
              {integralCumbres.length < 2 && (
                <p className="text-xs text-muted-foreground">Añade al menos 2 cumbres en el orden en que se ascenderán.</p>
              )}

              {/* Dificultad e itinerario */}
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>Dificultad máxima</Label>
                  <Input value={form.dificultadMaximaDescripcion}
                    onChange={(e) => update("dificultadMaximaDescripcion", e.target.value)}
                    placeholder="Ej: AD (IFAS) — tramo norte sobre glaciar" />
                </div>
                <div className="space-y-2 sm:col-span-2">
                  <Label>Descripción del itinerario</Label>
                  <textarea
                    value={form.descripcionItinerario}
                    onChange={(e) => update("descripcionItinerario", e.target.value)}
                    rows={3}
                    placeholder="Describe el recorrido entre cumbres, puntos de acampe, accesos..."
                    className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
                  />
                </div>
              </div>
            </fieldset>
          )}

          {/* ── Info adicional ────────────────────────────────── */}
          <fieldset className="space-y-4">
            <legend className="text-sm font-semibold text-foreground">Información adicional</legend>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>¿Requiere permisos?</Label>
                <Select value={form.requierePermisos} onValueChange={(v) => update("requierePermisos", v)}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent><SelectItem value="false">No</SelectItem><SelectItem value="true">Sí</SelectItem></SelectContent>
                </Select>
              </div>
              <div className="space-y-2"><Label>URL Documentación</Label><Input value={form.documentacionUrl} onChange={(e) => update("documentacionUrl", e.target.value)} /></div>
              <div className="sm:col-span-2 space-y-2">
                <Label>URL Track GPS</Label>
                <Input value={form.trackUrl} onChange={(e) => update("trackUrl", e.target.value)} placeholder="Wikiloc, Komoot, AllTrails, Strava..." />
              </div>
            </div>
          </fieldset>

          <div className="flex justify-end gap-3 pt-4 border-t border-border">
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Guardando..." : mode === "create" ? "Proponer ruta" : "Guardar cambios"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
