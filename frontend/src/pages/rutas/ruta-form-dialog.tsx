import { useRef, useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import { Check, ChevronsUpDown, ChevronUp, ChevronDown, X, Upload, FileText } from "lucide-react"
import { cn } from "@/lib/utils"
import { toast } from "sonner"
import { useCreateRuta, useUpdateRuta, useRutaDetail } from "@/hooks/use-rutas"
import api from "@/lib/api"
import { useMountainsList, useMountainLookups } from "@/hooks/use-mountains"
import type { RutaSummary, CreateRutaRequest, TipoActividad, TipoDocumentoRuta } from "@/types/rutas"
import { TIPO_ACTIVIDAD_LABELS, TIPOS_ESCALADA, TIPOS_BICICLETA, DIFICULTADES_CICLISMO, TIPO_BICICLETA_LABELS,
  OPCION_TIPO_DOCUMENTO, ORDEN_SELECTOR_TIPO, errorDeTamano } from "@/types/rutas"

interface Props {
  open: boolean
  onClose: () => void
  mode: "create" | "edit"
  ruta?: RutaSummary
  initialMountainId?: number
  /**
   * Se llama con el id de la ruta recien creada.
   *
   * Existe porque a una ruta que todavia no existe no se le puede adjuntar un
   * fichero: no hay id al que asociarlo. En lugar de un boton de subir que no
   * podria funcionar, al crear se abre el detalle, que es donde si se puede.
   */
  onCreated?: (id: number) => void
}

const EMPTY_FORM = {
  nombre: "", tipoActividad: "ALPINISMO" as TipoActividad,
  mountainId: "", lugarReferencia: "", sectorZona: "",
  longitudKm: "", desnivelPositivoM: "", desnivelNegativoM: "",
  duracionDias: "", duracionHoras: "",
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
  dificultadMaxTipo: "", dificultadMaximaDescripcion: "", descripcionItinerario: "",
}

export function RutaFormDialog({ open, onClose, mode, ruta, initialMountainId, onCreated }: Props) {
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

  /**
   * Ficheros elegidos antes de que la ruta exista.
   *
   * No se pueden subir todavia: el endpoint los cuelga de una ruta y aqui no
   * hay id. Se guardan y se suben en cuanto la creacion devuelve uno. Si la
   * creacion falla, el dialogo sigue abierto con la cola intacta, asi que no
   * se pierde la seleccion.
   */
  const [pendientes, setPendientes] = useState<{ file: File; tipo: TipoDocumentoRuta }[]>([])
  const [tipoArchivo, setTipoArchivo] = useState<TipoDocumentoRuta>("TRACK_GPX")
  const archivoInputRef = useRef<HTMLInputElement>(null)

  const syncKey = `${mode}-${rutaDetail?.id ?? "new"}-${open}`
  const [prevSyncKey, setPrevSyncKey] = useState(syncKey)
  if (syncKey !== prevSyncKey) {
    setPrevSyncKey(syncKey)
    // Al abrir o cerrar el dialogo se vacia la cola: un fichero elegido en una
    // creacion cancelada no debe acabar subido a la ruta siguiente.
    setPendientes([])
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
        desnivelPositivoM:   rutaDetail.desnivelPositivoM != null ? String(rutaDetail.desnivelPositivoM) : "",
        desnivelNegativoM:   rutaDetail.desnivelNegativoM != null ? String(rutaDetail.desnivelNegativoM) : "",
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
        dificultadMaxTipo:           int?.dificultadMaxTipo ?? "",
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

  /**
   * Sube la cola a la ruta recien creada y devuelve los nombres que fallaron.
   *
   * Secuencial y no en paralelo: son pocos ficheros y asi un fallo no deja a
   * medias a los demas. Un error aqui no revierte la ruta — ya existe, y
   * decirle al usuario que fallo todo le haria proponerla dos veces.
   */
  const subirPendientes = async (rutaId: number): Promise<string[]> => {
    const fallidos: string[] = []
    for (const { file, tipo } of pendientes) {
      try {
        const fd = new FormData()
        fd.append("file", file)
        fd.append("tipo", tipo)
        await api.post(`/v1/rutas/${rutaId}/documentos`, fd,
          { headers: { "Content-Type": "multipart/form-data" } })
      } catch {
        fallidos.push(file.name)
      }
    }
    return fallidos
  }

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
      desnivelPositivoM: form.desnivelPositivoM !== "" ? Number(form.desnivelPositivoM) : undefined,
      desnivelNegativoM: form.desnivelNegativoM !== "" ? Number(form.desnivelNegativoM) : undefined,
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
        dificultadMaxTipo:           form.dificultadMaxTipo || undefined,
        dificultadMaximaDescripcion: form.dificultadMaximaDescripcion || undefined,
        descripcionItinerario:       form.descripcionItinerario || undefined,
      }),

      // Integral — campos de dificultad del tramo más difícil
      ...(tipo === "INTEGRAL" && form.dificultadMaxTipo === "ALPINISMO" && {
        escalaAlpinaIfasId:   form.escalaAlpinaIfasId,
        dificultadRocaId:     form.dificultadRocaId,
        dificultadHieloId:    form.dificultadHieloId,
        compromisoId:         form.compromisoId,
        yosemiteId:           form.yosemiteId,
        saddayNivelTecnicoId: form.saddayNivelTecnicoId,
        saddayNivelFisicoId:  form.saddayNivelFisicoId,
        equipoMontanaId:      form.equipoMontanaId ? Number(form.equipoMontanaId) : undefined,
      }),
      ...(tipo === "INTEGRAL" && form.dificultadMaxTipo === "ESCALADA" && {
        dificultadRocaId: form.dificultadRocaId,
        tipoEscalada:     form.tipoEscalada,
        numCintas:        form.numCintas ? Number(form.numCintas) : undefined,
        alturaViaM:       form.alturaViaM ? Number(form.alturaViaM) : undefined,
        tipoRoca:         form.tipoRoca || undefined,
      }),
      ...(tipo === "INTEGRAL" && form.dificultadMaxTipo === "TREKKING" && {
        dificultadSenderismoId: form.dificultadSenderismoId,
        esCircular:  form.esCircular === "true",
        fuentesAgua: form.fuentesAgua === "true",
        tipoTerreno: form.tipoTerreno || undefined,
      }),
      ...(tipo === "INTEGRAL" && form.dificultadMaxTipo === "CICLISMO" && {
        tipoBicicleta:             form.tipoBicicleta,
        dificultadTecnicaCiclismo: form.dificultadTecnicaCiclismo || undefined,
        superficiePredominante:    form.superficiePredominante || undefined,
        ciclabilidadPct:           form.ciclabilidadPct ? Number(form.ciclabilidadPct) : undefined,
      }),
    }

    try {
      if (mode === "create") {
        const creada = await createMutation.mutateAsync(payload)
        const nuevoId = creada.data.id
        const fallidos = await subirPendientes(nuevoId)
        if (fallidos.length === 0) {
          toast.success(pendientes.length > 0
            ? `Ruta propuesta con ${pendientes.length} archivo(s)`
            : "Ruta propuesta correctamente")
        } else {
          // La ruta si se creo: hay que decirlo, o el usuario la propondria otra vez.
          toast.warning(`Ruta creada, pero no se pudieron subir: ${fallidos.join(", ")}`)
        }
        setPendientes([])
        onClose()
        onCreated?.(nuevoId)
        return
      } else {
        await updateMutation.mutateAsync(payload)
        const fallidos = ruta ? await subirPendientes(ruta.id) : []
        toast[fallidos.length ? "warning" : "success"](
          fallidos.length ? `Ruta actualizada, fallo al subir: ${fallidos.join(", ")}` : "Ruta actualizada")
        setPendientes([])
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
              <div className="space-y-2">
                <Label>Desnivel + (m)</Label>
                <Input type="number" min={0} placeholder="Subida acumulada"
                       value={form.desnivelPositivoM}
                       onChange={(e) => update("desnivelPositivoM", e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label>Desnivel − (m)</Label>
                <Input type="number" min={0} placeholder="Bajada acumulada"
                       value={form.desnivelNegativoM}
                       onChange={(e) => update("desnivelNegativoM", e.target.value)} />
              </div>

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

              {/* Tipo del tramo más difícil + campos de dificultad */}
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label>Tipo del tramo más difícil</Label>
                  <Select value={form.dificultadMaxTipo || "__none__"}
                    onValueChange={(v) => update("dificultadMaxTipo", v === "__none__" ? "" : v)}>
                    <SelectTrigger><SelectValue placeholder="Sin especificar" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__none__">Sin especificar</SelectItem>
                      <SelectItem value="ALPINISMO">Alpinismo</SelectItem>
                      <SelectItem value="ESCALADA">Escalada</SelectItem>
                      <SelectItem value="TREKKING">Trekking</SelectItem>
                      <SelectItem value="CICLISMO">Ciclismo</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {/* Alpinismo */}
                {form.dificultadMaxTipo === "ALPINISMO" && (
                  <div className="grid gap-4 sm:grid-cols-2 rounded-lg border border-border p-4">
                    <div className="space-y-2">
                      <Label>Escala Alpina IFAS *</Label>
                      <Select value={form.escalaAlpinaIfasId} onValueChange={(v) => update("escalaAlpinaIfasId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{lookups?.escalasAlpina.map((e) => <SelectItem key={e.id} value={e.id}>{e.grado} — {e.nombre}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label>Dificultad Roca *</Label>
                      <Select value={form.dificultadRocaId} onValueChange={(v) => update("dificultadRocaId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{lookups?.dificultadesRoca.map((r) => <SelectItem key={r.id} value={r.id}>{r.uiaa} ({r.francesa})</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label>Dificultad Hielo *</Label>
                      <Select value={form.dificultadHieloId} onValueChange={(v) => update("dificultadHieloId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{lookups?.dificultadesHielo.map((h) => <SelectItem key={h.id} value={h.id}>{h.grado}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label>Compromiso *</Label>
                      <Select value={form.compromisoId} onValueChange={(v) => update("compromisoId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{lookups?.compromisos.map((c) => <SelectItem key={c.id} value={c.id}>{c.tipo}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label>Yosemite *</Label>
                      <Select value={form.yosemiteId} onValueChange={(v) => update("yosemiteId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{lookups?.yosemiteClases.map((y) => <SelectItem key={y.id} value={y.id}>{y.tipo}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label>Sadday Nivel Técnico *</Label>
                      <Select value={form.saddayNivelTecnicoId} onValueChange={(v) => update("saddayNivelTecnicoId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{lookups?.saddayRiesgos.map((s) => <SelectItem key={s.id} value={s.id}>{s.escala}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label>Sadday Nivel Físico *</Label>
                      <Select value={form.saddayNivelFisicoId} onValueChange={(v) => update("saddayNivelFisicoId", v)}>
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
                )}

                {/* Escalada */}
                {form.dificultadMaxTipo === "ESCALADA" && (
                  <div className="grid gap-4 sm:grid-cols-2 rounded-lg border border-border p-4">
                    <div className="space-y-2">
                      <Label>Grado de roca *</Label>
                      <Select value={form.dificultadRocaId} onValueChange={(v) => update("dificultadRocaId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{lookups?.dificultadesRoca.map((r) => <SelectItem key={r.id} value={r.id}>{r.uiaa} ({r.francesa})</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label>Tipo de escalada *</Label>
                      <Select value={form.tipoEscalada} onValueChange={(v) => update("tipoEscalada", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{TIPOS_ESCALADA.map((t) => <SelectItem key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2"><Label>N° de cintas</Label><Input type="number" min={0} value={form.numCintas} onChange={(e) => update("numCintas", e.target.value)} /></div>
                    <div className="space-y-2"><Label>Altura de la vía (m)</Label><Input type="number" min={1} value={form.alturaViaM} onChange={(e) => update("alturaViaM", e.target.value)} /></div>
                    <div className="space-y-2 sm:col-span-2"><Label>Tipo de roca</Label><Input value={form.tipoRoca} onChange={(e) => update("tipoRoca", e.target.value)} placeholder="Basalto, granito, caliza..." /></div>
                  </div>
                )}

                {/* Trekking */}
                {form.dificultadMaxTipo === "TREKKING" && (
                  <div className="grid gap-4 sm:grid-cols-2 rounded-lg border border-border p-4">
                    <div className="space-y-2">
                      <Label>Dificultad *</Label>
                      <Select value={form.dificultadSenderismoId} onValueChange={(v) => update("dificultadSenderismoId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{lookups?.dificultadesSenderismo?.map((d) => <SelectItem key={d.id} value={d.id}>{d.nombre}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2"><Label>Tipo de terreno</Label><Input value={form.tipoTerreno} onChange={(e) => update("tipoTerreno", e.target.value)} placeholder="Sendero, páramo, bosque..." /></div>
                  </div>
                )}

                {/* Ciclismo */}
                {form.dificultadMaxTipo === "CICLISMO" && (
                  <div className="grid gap-4 sm:grid-cols-2 rounded-lg border border-border p-4">
                    <div className="space-y-2">
                      <Label>Tipo de bicicleta *</Label>
                      <Select value={form.tipoBicicleta} onValueChange={(v) => update("tipoBicicleta", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                        <SelectContent>{TIPOS_BICICLETA.map((t) => <SelectItem key={t} value={t}>{TIPO_BICICLETA_LABELS[t]}</SelectItem>)}</SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label>Dificultad técnica</Label>
                      <Select value={form.dificultadTecnicaCiclismo || "__none__"} onValueChange={(v) => update("dificultadTecnicaCiclismo", v === "__none__" ? "" : v)}>
                        <SelectTrigger><SelectValue placeholder="Sin clasificar" /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value="__none__">Sin clasificar</SelectItem>
                          {DIFICULTADES_CICLISMO.map((d) => <SelectItem key={d} value={d}>{d}</SelectItem>)}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                )}
              </div>

              {/* Descripción del itinerario */}
              <div className="space-y-2">
                <Label>Descripción del itinerario</Label>
                <textarea
                  value={form.descripcionItinerario}
                  onChange={(e) => update("descripcionItinerario", e.target.value)}
                  rows={3}
                  placeholder="Describe el recorrido entre cumbres, puntos de acampe, accesos..."
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
                />
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
              {/* Los campos de arriba son enlaces; esto son ficheros. Se
                  eligen aqui y se suben en cuanto la ruta tiene id. */}
              <div className="sm:col-span-2 space-y-2 pt-2 border-t border-border">
                <Label>Archivos (track GPX, mapa, plantilla de permiso)</Label>
                <div className="flex items-center gap-2">
                  <select
                    value={tipoArchivo}
                    onChange={(e) => setTipoArchivo(e.target.value as TipoDocumentoRuta)}
                    className="h-9 flex-1 rounded-md border border-border bg-background px-2 text-sm text-foreground"
                    aria-label="Tipo de archivo a adjuntar"
                  >
                    {ORDEN_SELECTOR_TIPO.map((t) => (
                      <option key={t} value={t}>{OPCION_TIPO_DOCUMENTO[t]}</option>
                    ))}
                  </select>
                  <Button type="button" variant="outline" className="gap-1.5"
                          onClick={() => archivoInputRef.current?.click()}>
                    <Upload className="h-3.5 w-3.5" /> Adjuntar
                  </Button>
                  <input
                    ref={archivoInputRef}
                    type="file"
                    accept={tipoArchivo === "TRACK_GPX" ? ".gpx" : ".pdf,.doc,.docx,.xls,.xlsx"}
                    className="hidden"
                    onChange={(e) => {
                      const f = e.target.files?.[0]
                      e.target.value = ""
                      if (!f) return
                      // Igual que en el detalle: rechazar aqui y no tras subir
                      const err = errorDeTamano(f)
                      if (err) { toast.error(err); return }
                      setPendientes((p) => [...p, { file: f, tipo: tipoArchivo }])
                    }}
                  />
                </div>

                {pendientes.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    Los campos de arriba son enlaces a Wikiloc o Komoot. Esto es para el fichero
                    en si, que se subira al guardar la ruta.
                  </p>
                ) : (
                  <ul className="divide-y divide-border rounded-lg border border-border bg-card">
                    {pendientes.map((p, i) => (
                      <li key={`${p.file.name}-${i}`} className="flex items-center gap-2 px-3 py-2">
                        <FileText className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                        <span className="flex-1 min-w-0 truncate text-sm">{p.file.name}</span>
                        <span className="shrink-0 text-xs text-muted-foreground">
                          {OPCION_TIPO_DOCUMENTO[p.tipo].split(" —")[0].split(" (")[0]}
                        </span>
                        <Button type="button" variant="ghost" size="icon" className="h-7 w-7"
                                onClick={() => setPendientes((prev) => prev.filter((_, j) => j !== i))}
                                title="Quitar">
                          <X className="h-3.5 w-3.5" />
                        </Button>
                      </li>
                    ))}
                  </ul>
                )}
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
