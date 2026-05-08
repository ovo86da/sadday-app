/**
 * Diálogo para que el Jefe de Salida cree o edite el informe de una salida.
 *
 * Pre-llena los datos de la salida (hora de encuentro, participantes) y permite:
 * - Editar todos los campos del informe.
 * - Gestionar segmentos de viaje con transporte externo opcional por tramo.
 * - Registrar guía externo (con contacto global) o guía socio.
 * - Registrar alojamiento (refugio / camping) con contacto opcional.
 * - Marcar participantes como "No fue" (NO_FUE) o reactivarlos (INSCRITO).
 * - Agregar participantes adicionales que se unieron el día de la salida.
 */
import { useState, useEffect, useRef } from "react"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Popover, PopoverContent, PopoverTrigger,
} from "@/components/ui/popover"
import {
  useCreateInforme, useUpdateInforme, useInforme,
} from "@/hooks/use-informes"
import {
  useSalidaDetail, useCambiarEstadoInscripcion, useInscribirSalida,
} from "@/hooks/use-salidas"
import {
  useBuscarContactos, useCreateContacto,
} from "@/hooks/use-contactos"
import { useQuery } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"
import { toast } from "sonner"
import {
  FileText, Clock, Search, UserPlus, X, Plus, Trash2,
  MapPin, User, Tent, Phone, Car, DollarSign,
} from "lucide-react"
import type { CreateInformeRequest, SegmentoViajeRequest, TipoTransporte, DondeAutos } from "@/types/informes"
import type { Contacto, CreateContactoRequest } from "@/types/contactos"

interface Props {
  open: boolean
  onClose: () => void
  salidaId: string
  /** Hora de encuentro del club, para pre-llenar horaSalidaClub */
  horaEncuentroClub?: string | null
}

// ─── MoneyInput ─────────────────────────────────────────────────────────────
// Input de texto para montos: sin flechas, solo dígitos y punto decimal (máx 2 decimales).
// Mantiene su propio estado string para permitir entrada parcial como "1." sin snapping.

interface MoneyInputProps {
  value: number | null | undefined
  onChange: (v: number | null) => void
  placeholder?: string
  className?: string
  min?: number
}

function MoneyInput({ value, onChange, placeholder = "0.00", className, min }: MoneyInputProps) {
  const [display, setDisplay] = useState(() => (value != null ? String(value) : ""))

  useEffect(() => {
    // Sincronizar cuando el padre resetea o pre-llena el valor
    setDisplay(value != null ? String(value) : "")
  }, [value])

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const raw = e.target.value
    // Solo dígitos y un punto decimal con máximo 2 decimales
    const stripped = raw.replace(/[^0-9.]/g, "")
    const parts = stripped.split(".")
    const sanitized =
      parts.length > 2
        ? parts[0] + "." + parts.slice(1).join("").slice(0, 2)
        : parts.length === 2
          ? parts[0] + "." + parts[1].slice(0, 2)
          : parts[0]
    setDisplay(sanitized)
    onChange(sanitized === "" || sanitized === "." ? null : Number(sanitized))
  }

  return (
    <Input
      type="text"
      inputMode="decimal"
      placeholder={placeholder}
      value={display}
      onChange={handleChange}
      min={min}
      className={className}
    />
  )
}

// ─── ContactoBuscarInput ────────────────────────────────────────────────────

interface ContactoBuscarInputProps {
  value: number | null | undefined
  displayName?: string | null
  displayPhone?: string | null
  onChange: (contacto: Contacto | null) => void
  label?: string
  placeholder?: string
}

function ContactoBuscarInput({
  value,
  displayName,
  displayPhone,
  onChange,
  label,
  placeholder = "Buscar por nombre o teléfono...",
}: ContactoBuscarInputProps) {
  const [open, setOpen] = useState(false)
  const [q, setQ] = useState("")
  const [showCreate, setShowCreate] = useState(false)
  const [newForm, setNewForm] = useState<CreateContactoRequest>({ nombre: "" })
  const inputRef = useRef<HTMLInputElement>(null)

  const { data: resultados, isFetching } = useBuscarContactos(q)
  const createContacto = useCreateContacto()

  const selected = value != null

  function openCreate(initialNombre = "") {
    setShowCreate(true)
    setNewForm({ nombre: initialNombre })
    setOpen(true)
    setTimeout(() => inputRef.current?.focus(), 50)
  }

  function handleSelect(c: Contacto) {
    onChange(c)
    setOpen(false)
    setQ("")
    setShowCreate(false)
  }

  function handleClear() {
    onChange(null)
    setQ("")
    setShowCreate(false)
  }

  async function handleCreate() {
    if (!newForm.nombre.trim()) {
      toast.error("El nombre es requerido")
      return
    }
    try {
      const created = await createContacto.mutateAsync(newForm)
      onChange(created)
      setOpen(false)
      setQ("")
      setShowCreate(false)
      setNewForm({ nombre: "" })
      toast.success("Contacto registrado")
    } catch (error) { console.error(error);
      toast.error("Error al registrar contacto")
    }
  }

  return (
    <div className="space-y-1">
      {label && (
        <label className="text-xs text-muted-foreground">{label}</label>
      )}
      {selected ? (
        <div className="flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2">
          <User className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium truncate">{displayName ?? `Contacto #${value}`}</p>
            {displayPhone && (
              <p className="text-xs text-muted-foreground flex items-center gap-1">
                <Phone className="h-3 w-3" />
                {displayPhone}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={handleClear}
            className="text-muted-foreground hover:text-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ) : (
        <div className="space-y-1.5">
          <Popover open={open} onOpenChange={(v) => { setOpen(v); if (!v) { setShowCreate(false); setQ("") } }}>
            <PopoverTrigger asChild>
              <button
                type="button"
                className="flex w-full items-center gap-2 rounded-md border border-border bg-background px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
                onClick={() => { setOpen(true); setTimeout(() => inputRef.current?.focus(), 50) }}
              >
                <Search className="h-3.5 w-3.5 shrink-0" />
                <span className="truncate">{placeholder}</span>
              </button>
            </PopoverTrigger>
            <PopoverContent className="w-80 p-0" align="start">
              {!showCreate ? (
                <>
                  <div className="p-2 border-b border-border">
                    <Input
                      ref={inputRef}
                      value={q}
                      onChange={(e) => setQ(e.target.value)}
                      placeholder="Nombre o teléfono..."
                      className="h-8 text-sm"
                    />
                  </div>
                  <div className="max-h-52 overflow-y-auto">
                    {q.length < 2 && (
                      <p className="px-3 py-3 text-xs text-muted-foreground text-center">
                        Escribe al menos 2 caracteres para buscar
                      </p>
                    )}
                    {q.length >= 2 && isFetching && (
                      <p className="px-3 py-3 text-xs text-muted-foreground text-center">Buscando...</p>
                    )}
                    {q.length >= 2 && !isFetching && resultados && resultados.length > 0 && (
                      <>
                        <ul className="divide-y divide-border">
                          {resultados.map((c) => (
                            <li key={c.id}>
                              <button
                                type="button"
                                className="w-full flex items-center gap-2 px-3 py-2.5 text-left hover:bg-accent transition-colors"
                                onClick={() => handleSelect(c)}
                              >
                                <User className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                                <div className="flex-1 min-w-0">
                                  <p className="text-sm font-medium truncate">{c.nombre}</p>
                                  {c.telefono && (
                                    <p className="text-xs text-muted-foreground">{c.telefono}</p>
                                  )}
                                </div>
                              </button>
                            </li>
                          ))}
                        </ul>
                        <div className="border-t border-border p-2">
                          <button
                            type="button"
                            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs text-primary hover:bg-accent transition-colors"
                            onClick={() => { setShowCreate(true); setNewForm({ nombre: q }) }}
                          >
                            <Plus className="h-3.5 w-3.5" />
                            Registrar nuevo contacto
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                </>
              ) : (
                <div className="p-3 space-y-2">
                  <div className="flex items-center justify-between">
                    <p className="text-xs font-semibold text-foreground">Registrar nuevo contacto</p>
                    {resultados && resultados.length > 0 && (
                      <button
                        type="button"
                        className="text-xs text-muted-foreground hover:text-foreground"
                        onClick={() => setShowCreate(false)}
                      >
                        ← Volver
                      </button>
                    )}
                  </div>
                  <Input
                    autoFocus
                    placeholder="Nombre *"
                    value={newForm.nombre}
                    onChange={(e) => setNewForm((f) => ({ ...f, nombre: e.target.value }))}
                    className="h-8 text-sm"
                  />
                  <Input
                    placeholder="Teléfono"
                    value={newForm.telefono ?? ""}
                    onChange={(e) => setNewForm((f) => ({ ...f, telefono: e.target.value || undefined }))}
                    className="h-8 text-sm"
                  />
                  <Input
                    placeholder="Correo electrónico"
                    type="email"
                    value={newForm.correo ?? ""}
                    onChange={(e) => setNewForm((f) => ({ ...f, correo: e.target.value || undefined }))}
                    className="h-8 text-sm"
                  />
                  <Input
                    placeholder="Notas (opcional)"
                    value={newForm.notas ?? ""}
                    onChange={(e) => setNewForm((f) => ({ ...f, notas: e.target.value || undefined }))}
                    className="h-8 text-sm"
                  />
                  <div className="flex gap-2 pt-1">
                    <Button
                      type="button"
                      size="sm"
                      className="flex-1 h-7 text-xs"
                      onClick={handleCreate}
                      disabled={createContacto.isPending}
                    >
                      {createContacto.isPending ? "Guardando..." : "Registrar"}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="h-7 text-xs"
                      onClick={() => { setShowCreate(false); setQ("") }}
                    >
                      Cancelar
                    </Button>
                  </div>
                </div>
              )}
            </PopoverContent>
          </Popover>

          {/* Acceso directo sin necesidad de buscar primero */}
          <button
            type="button"
            className="flex items-center gap-1 text-xs text-primary hover:underline"
            onClick={() => openCreate()}
          >
            <Plus className="h-3 w-3" />
            Registrar nuevo contacto
          </button>
        </div>
      )}
    </div>
  )
}

// ─── SegmentoRow ────────────────────────────────────────────────────────────

const TIPO_TRANSPORTE_LABELS: Record<TipoTransporte, string> = {
  CAMIONETA: "Camioneta",
  FURGONETA: "Furgoneta",
  BUS_MEDIANO: "Bus mediano",
  BUS_GRANDE: "Bus grande",
}

const DONDE_AUTOS_LABELS: Record<DondeAutos, string> = {
  NO_AUTOS:             "No se dejó autos",
  PARQUEADERO_SEGURO:   "En parqueadero — seguro",
  PARQUEADERO_INSEGURO: "En parqueadero — inseguro",
  BASE_MONTANA:         "En la base de la montaña",
  CALLE_SEGURO:         "En la calle — seguro",
  CALLE_INSEGURO:       "En la calle — inseguro",
}

/** Opciones que requieren dirección/descripción adicional */
const DONDE_AUTOS_REQUIERE_DESCRIPCION: DondeAutos[] = [
  "PARQUEADERO_SEGURO",
  "PARQUEADERO_INSEGURO",
  "BASE_MONTANA",
  "CALLE_SEGURO",
  "CALLE_INSEGURO",
]

const DONDE_AUTOS_LABEL_DESCRIPCION: Partial<Record<DondeAutos, string>> = {
  BASE_MONTANA: "Descripción del lugar",
}

interface SegmentoRowProps {
  index: number
  segmento: SegmentoViajeRequest
  onChange: (s: SegmentoViajeRequest) => void
  onContactoChange: (contacto: Contacto | null) => void
  onRemove: () => void
  canRemove: boolean
  contactoNombre?: string | null
  contactoTelefono?: string | null
}

function SegmentoRow({
  index,
  segmento,
  onChange,
  onContactoChange,
  onRemove,
  canRemove,
  contactoNombre,
  contactoTelefono,
}: SegmentoRowProps) {
  function set<K extends keyof SegmentoViajeRequest>(k: K, v: SegmentoViajeRequest[K]) {
    onChange({ ...segmento, [k]: v })
  }

  return (
    <div className="rounded-lg border border-border p-3 space-y-3">
      <div className="flex items-center gap-2">
        <MapPin className="h-4 w-4 text-muted-foreground shrink-0" />
        <span className="text-xs font-medium text-muted-foreground">Tramo {index + 1}</span>
        {canRemove && (
          <button
            type="button"
            onClick={onRemove}
            className="ml-auto text-muted-foreground hover:text-destructive transition-colors"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        )}
      </div>

      <div className="grid grid-cols-2 gap-2">
        <div className="space-y-1">
          <label className="text-xs text-muted-foreground">Origen</label>
          <Input
            placeholder="Ej: Club Sadday"
            value={segmento.origen}
            onChange={(e) => set("origen", e.target.value)}
            className="h-8 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs text-muted-foreground">Destino</label>
          <Input
            placeholder="Ej: Parqueadero base"
            value={segmento.destino}
            onChange={(e) => set("destino", e.target.value)}
            className="h-8 text-sm"
          />
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Checkbox
          id={`alquilo-${index}`}
          checked={segmento.alquiloTransporte}
          onCheckedChange={(checked) => {
            onChange({
              ...segmento,
              alquiloTransporte: !!checked,
              tipoTransporte: checked ? segmento.tipoTransporte : null,
              costoIndividual: checked ? segmento.costoIndividual : null,
              contactoId: checked ? segmento.contactoId : null,
            })
          }}
        />
        <label htmlFor={`alquilo-${index}`} className="text-xs text-muted-foreground cursor-pointer">
          ¿Se alquiló transporte externo en este tramo?
        </label>
      </div>

      {segmento.alquiloTransporte && (
        <div className="space-y-3 pl-1 border-l-2 border-primary/30 ml-1">
          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">Tipo de vehículo</label>
              <Select
                value={segmento.tipoTransporte ?? "none"}
                onValueChange={(v) =>
                  set("tipoTransporte", v === "none" ? null : (v as TipoTransporte))
                }
              >
                <SelectTrigger className="h-8 text-sm">
                  <SelectValue placeholder="Tipo..." />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">Sin especificar</SelectItem>
                  {(Object.keys(TIPO_TRANSPORTE_LABELS) as TipoTransporte[]).map((t) => (
                    <SelectItem key={t} value={t}>
                      {TIPO_TRANSPORTE_LABELS[t]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">Costo total ($)</label>
              <MoneyInput
                value={segmento.costoIndividual}
                onChange={(v) => set("costoIndividual", v)}
                className="h-8 text-sm"
              />
            </div>
          </div>

          <ContactoBuscarInput
            value={segmento.contactoId}
            displayName={contactoNombre}
            displayPhone={contactoTelefono}
            onChange={(c) => {
              set("contactoId", c?.id ?? null)
              onContactoChange(c)
            }}
            label="Contacto del transportista"
            placeholder="Buscar transportista..."
          />
        </div>
      )}
    </div>
  )
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function timeOrUndefined(v: string): string | undefined {
  return v.trim() ? v.trim() : undefined
}

// ─── State: map contacto metadata alongside segmentos ───────────────────────

interface SegmentoMeta {
  contactoNombre?: string | null
  contactoTelefono?: string | null
}

// ─── EMPTY_FORM ─────────────────────────────────────────────────────────────

const defaultSegmento = (): SegmentoViajeRequest => ({
  origen: "Club Sadday",
  destino: "",
  alquiloTransporte: false,
  tipoTransporte: null,
  costoIndividual: null,
  contactoId: null,
})

const EMPTY_FORM: CreateInformeRequest = {
  seRealizo: true,
  lograronCumbre: true,
  horaSalidaClub: undefined,
  horaLlegadaMontana: undefined,
  horaCumbre: undefined,
  horaInicioDescenso: undefined,
  horaLlegadaAutos: undefined,
  horaRegresoClub: undefined,
  condicionesMeterologicas: undefined,
  cronica: undefined,
  observaciones: undefined,
  comentariosVarios: undefined,
  segmentos: [defaultSegmento()],
  alquiloGuia: false,
  costoGuia: null,
  contactoGuiaId: null,
  guiaSocioId: null,
  alquiloRefugio: false,
  nombreRefugio: null,
  costoRefugio: null,
  contactoRefugioId: null,
  acampo: false,
  nombreCamping: null,
  costoCamping: null,
  contactoCampingId: null,
  dondeAutos: null,
  autosDescripcion: null,
  autosLinkUbicacion: null,
  costoParqueadero: null,
  costoTotal: null,
  costoPorPersona: null,
}

// ─── Main dialog ─────────────────────────────────────────────────────────────

export function InformeJefeDialog({ open, onClose, salidaId, horaEncuentroClub }: Props) {
  const { data: salida } = useSalidaDetail(salidaId)
  const { data: informe } = useInforme(salidaId)
  const isEditing = !!informe

  const createMutation = useCreateInforme(salidaId)
  const updateMutation = useUpdateInforme(salidaId)
  const estadoMutation = useCambiarEstadoInscripcion(salidaId)
  const inscribirMutation = useInscribirSalida(salidaId)

  // ── Tab state ─────────────────────────────────────────────────────────────
  const [activeTab, setActiveTab] = useState<"datos" | "participantes">("datos")

  // ── Form state ─────────────────────────────────────────────────────────────
  const [form, setForm] = useState<CreateInformeRequest>(EMPTY_FORM)

  // ── Contacto metadata (display name/phone for selected contactos) ──────────
  // Guía
  const [guiaNombre, setGuiaNombre] = useState<string | null>(null)
  const [guiaTelefono, setGuiaTelefono] = useState<string | null>(null)
  // Refugio
  const [refugioContactoNombre, setRefugioContactoNombre] = useState<string | null>(null)
  // Camping
  const [campingContactoNombre, setCampingContactoNombre] = useState<string | null>(null)
  // Per-segment metadata
  const [segmentosMeta, setSegmentosMeta] = useState<SegmentoMeta[]>([{}])

  // ── Participant search ────────────────────────────────────────────────────
  const [searchQ, setSearchQ] = useState("")
  const [showSearch, setShowSearch] = useState(false)

  interface SocioMinimal { id: string; nombre: string; apellido: string }

  const { data: sociosData } = useQuery({
    queryKey: ["socios", "buscar", searchQ],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<SocioMinimal[]>>("/v1/socios/buscar", {
        params: { q: searchQ, size: 10 },
      })
      return data.data
    },
    enabled: showSearch && searchQ.length >= 2,
  })

  // ── Pre-fill on open ───────────────────────────────────────────────────────
  useEffect(() => {
    if (!open) return
    if (informe) {
      const segs: SegmentoViajeRequest[] = (informe.segmentos ?? []).map((s) => ({
        origen: s.origen,
        destino: s.destino,
        alquiloTransporte: s.alquiloTransporte,
        tipoTransporte: s.tipoTransporte ?? null,
        costoIndividual: s.costoIndividual ?? null,
        contactoId: s.contactoId ?? null,
      }))

      setForm({
        seRealizo: informe.seRealizo ?? true,
        lograronCumbre: informe.lograronCumbre ?? false,
        horaSalidaClub: informe.horaSalidaClub ?? undefined,
        horaLlegadaMontana: informe.horaLlegadaMontana ?? undefined,
        horaCumbre: informe.horaCumbre ?? undefined,
        horaInicioDescenso: informe.horaInicioDescenso ?? undefined,
        horaLlegadaAutos: informe.horaLlegadaAutos ?? undefined,
        horaRegresoClub: informe.horaRegresoClub ?? undefined,
        condicionesMeterologicas: informe.condicionesMeterologicas ?? undefined,
        cronica: informe.cronica ?? undefined,
        observaciones: informe.observaciones ?? undefined,
        comentariosVarios: informe.comentariosVarios ?? undefined,
        segmentos: segs.length > 0 ? segs : [defaultSegmento()],
        alquiloGuia: informe.alquiloGuia,
        costoGuia: informe.costoGuia ?? null,
        contactoGuiaId: informe.contactoGuiaId ?? null,
        guiaSocioId: null,
        alquiloRefugio: informe.alquiloRefugio ?? false,
        nombreRefugio: informe.nombreRefugio ?? null,
        costoRefugio: informe.costoRefugio ?? null,
        contactoRefugioId: informe.contactoRefugioId ?? null,
        acampo: informe.acampo ?? false,
        nombreCamping: informe.nombreCamping ?? null,
        costoCamping: informe.costoCamping ?? null,
        contactoCampingId: informe.contactoCampingId ?? null,
        dondeAutos: informe.dondeAutos ?? null,
        autosDescripcion: informe.autosDescripcion ?? null,
        autosLinkUbicacion: informe.autosLinkUbicacion ?? null,
        costoParqueadero: informe.costoParqueadero ?? null,
        costoTotal: informe.costoTotal ?? null,
        costoPorPersona: informe.costoPorPersona ?? null,
      })

      // Restore display names from existing informe
      setGuiaNombre(informe.contactoGuiaNombre ?? null)
      setGuiaTelefono(informe.contactoGuiaTelefono ?? null)
      setRefugioContactoNombre(informe.contactoRefugioNombre ?? null)
      setCampingContactoNombre(informe.contactoCampingNombre ?? null)
      setSegmentosMeta(
        (informe.segmentos ?? []).map((s) => ({
          contactoNombre: s.contactoNombre,
          contactoTelefono: s.contactoTelefono,
        }))
      )
    } else {
      setForm({
        ...EMPTY_FORM,
        horaSalidaClub: horaEncuentroClub ?? undefined,
        segmentos: [defaultSegmento()],
      })
      setGuiaNombre(null)
      setGuiaTelefono(null)
      setRefugioContactoNombre(null)
      setCampingContactoNombre(null)
      setSegmentosMeta([{}])
    }
  }, [open, informe, horaEncuentroClub])

  // ── Helpers ───────────────────────────────────────────────────────────────
  function set<K extends keyof CreateInformeRequest>(k: K, v: CreateInformeRequest[K]) {
    setForm((prev) => ({ ...prev, [k]: v }))
  }

  // ── Segmento handlers ─────────────────────────────────────────────────────
  function handleSegmentoChange(index: number, updated: SegmentoViajeRequest) {
    setForm((prev) => {
      const segs = [...prev.segmentos]
      segs[index] = updated
      return { ...prev, segmentos: segs }
    })
  }

  function handleSegmentoMetaChange(index: number, meta: SegmentoMeta) {
    setSegmentosMeta((prev) => {
      const next = [...prev]
      next[index] = meta
      return next
    })
  }

  function handleAddSegmento() {
    const last = form.segmentos[form.segmentos.length - 1]
    const newSeg: SegmentoViajeRequest = {
      origen: last?.destino ?? "",
      destino: "Club Sadday",
      alquiloTransporte: false,
      tipoTransporte: null,
      costoIndividual: null,
      contactoId: null,
    }
    setForm((prev) => ({ ...prev, segmentos: [...prev.segmentos, newSeg] }))
    setSegmentosMeta((prev) => [...prev, {}])
  }

  function handleRemoveSegmento(index: number) {
    setForm((prev) => ({
      ...prev,
      segmentos: prev.segmentos.filter((_, i) => i !== index),
    }))
    setSegmentosMeta((prev) => prev.filter((_, i) => i !== index))
  }

  const isPending = createMutation.isPending || updateMutation.isPending

  // ── Submit ────────────────────────────────────────────────────────────────
  async function handleSubmit() {
    // Validate segmentos
    for (let i = 0; i < form.segmentos.length; i++) {
      const s = form.segmentos[i]
      if (!s.origen.trim() || !s.destino.trim()) {
        toast.error(`El tramo ${i + 1} debe tener origen y destino`)
        return
      }
    }
    if (form.alquiloGuia && !form.contactoGuiaId) {
      toast.error("Selecciona el contacto de guía externo")
      return
    }

    const payload: CreateInformeRequest = {
      ...form,
      horaSalidaClub: timeOrUndefined(form.horaSalidaClub ?? ""),
      horaLlegadaMontana: timeOrUndefined(form.horaLlegadaMontana ?? ""),
      horaCumbre: timeOrUndefined(form.horaCumbre ?? ""),
      horaInicioDescenso: timeOrUndefined(form.horaInicioDescenso ?? ""),
      horaLlegadaAutos: timeOrUndefined(form.horaLlegadaAutos ?? ""),
      horaRegresoClub: timeOrUndefined(form.horaRegresoClub ?? ""),
      condicionesMeterologicas: form.condicionesMeterologicas?.trim() || undefined,
      cronica: form.cronica?.trim() || undefined,
      observaciones: form.observaciones?.trim() || undefined,
      comentariosVarios: form.comentariosVarios?.trim() || undefined,
      // Clear guide contact when not renting
      contactoGuiaId: form.alquiloGuia ? form.contactoGuiaId : null,
      guiaSocioId: !form.alquiloGuia ? form.guiaSocioId : null,
      // Clear refugio contact when not renting
      contactoRefugioId: form.alquiloRefugio ? form.contactoRefugioId : null,
      nombreRefugio: form.alquiloRefugio ? form.nombreRefugio : null,
      costoRefugio: form.alquiloRefugio ? form.costoRefugio : null,
      // Clear camping contact when not camping
      contactoCampingId: form.acampo ? form.contactoCampingId : null,
      nombreCamping: form.acampo ? form.nombreCamping : null,
      costoCamping: form.acampo ? form.costoCamping : null,
    }

    try {
      if (isEditing) {
        await updateMutation.mutateAsync(payload)
        toast.success("Informe actualizado")
      } else {
        await createMutation.mutateAsync(payload)
        toast.success("Informe creado")
      }
      onClose()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg ?? "Error al guardar el informe")
    }
  }

  // ── Participant helpers ───────────────────────────────────────────────────
  async function toggleAsistencia(participanteId: number, currentEstado: string) {
    // CONFIRMADO = asistió, NO_FUE = se inscribió pero no fue
    // Cualquier estado activo (INSCRITO, CONFIRMADO) se puede marcar como "no fue" → NO_FUE
    // Un NO_FUE puede volver a CONFIRMADO si fue un error
    const nuevoEstado = currentEstado === "NO_FUE" ? "CONFIRMADO" : "NO_FUE"
    try {
      await estadoMutation.mutateAsync({ participanteId, estadoInscripcion: nuevoEstado })
    } catch (error) { console.error(error);
      toast.error("Error al actualizar asistencia")
    }
  }

  async function handleAgregarSocio(socioId: string, nombre: string) {
    try {
      await inscribirMutation.mutateAsync(socioId)
      toast.success(`${nombre} agregado a la salida`)
      setSearchQ("")
      setShowSearch(false)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg || "Error al agregar participante")
    }
  }

  const participantes = salida?.participantes ?? []
  const activos = participantes.filter(
    (p) => p.estadoInscripcion === "INSCRITO" || p.estadoInscripcion === "CONFIRMADO",
  )
  const noFueron = participantes.filter((p) => p.estadoInscripcion === "NO_FUE")
  const sociosEnSalida = new Set(participantes.map((p) => p.socioId))

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent
        className="max-h-[92vh] max-w-2xl overflow-y-auto"
        onInteractOutside={(e) => e.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5 text-primary" />
            {isEditing ? "Editar informe" : "Crear informe de salida"}
          </DialogTitle>
          {salida && (
            <p className="text-sm text-muted-foreground pt-1">
              {salida.nombre} · {salida.rutaNombre}
            </p>
          )}
        </DialogHeader>

        <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as "datos" | "participantes")} className="mt-2">
          <TabsList className="w-full">
            <TabsTrigger value="datos" className="flex-1">Datos del informe</TabsTrigger>
            <TabsTrigger value="participantes" className="flex-1">
              Participantes ({activos.length + noFueron.length})
            </TabsTrigger>
          </TabsList>

          {/* ── Tab: Datos ──────────────────────────────────────────────── */}
          <TabsContent value="datos" className="space-y-5 pt-2">

            {/* ¿Se realizó? + ¿Lograron la cumbre? */}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">¿La salida se realizó?</label>
                <Select
                  value={form.seRealizo ? "si" : "no"}
                  onValueChange={(v) => set("seRealizo", v === "si")}
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="si">Sí, se realizó</SelectItem>
                    <SelectItem value="no">No se realizó</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">¿Lograron la cumbre?</label>
                <Select
                  value={form.lograronCumbre ? "si" : "no"}
                  onValueChange={(v) => set("lograronCumbre", v === "si")}
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="si">Sí, cumbre lograda</SelectItem>
                    <SelectItem value="no">No se llegó a la cumbre</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Horarios */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <Clock className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-foreground">Horarios</span>
              </div>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {([
                  { key: "horaSalidaClub", label: "Salida del club" },
                  { key: "horaLlegadaMontana", label: "Llegada a la montaña" },
                  { key: "horaCumbre", label: "Hora de cumbre" },
                  { key: "horaInicioDescenso", label: "Inicio descenso" },
                  { key: "horaLlegadaAutos", label: "Llegada a autos" },
                  { key: "horaRegresoClub", label: "Regreso al club" },
                ] as { key: keyof CreateInformeRequest; label: string }[]).map(({ key, label }) => (
                  <div key={key} className="space-y-1">
                    <label className="text-xs text-muted-foreground">{label}</label>
                    <Input
                      type="time"
                      value={(form[key] as string | undefined) ?? ""}
                      onChange={(e) => set(key, e.target.value || undefined)}
                    />
                  </div>
                ))}
              </div>
            </div>

            {/* Condiciones */}
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">
                Condiciones meteorológicas
              </label>
              <Input
                placeholder="Ej: Soleado, viento moderado..."
                value={form.condicionesMeterologicas ?? ""}
                onChange={(e) => set("condicionesMeterologicas", e.target.value || undefined)}
              />
            </div>

            {/* ── Segmentos de viaje ────────────────────────────────────── */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <MapPin className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-foreground">Segmentos de viaje</span>
              </div>
              <p className="text-xs text-muted-foreground">
                Añade un tramo por cada etapa del recorrido. Indica si se alquiló transporte externo en algún tramo.
              </p>

              <div className="space-y-2">
                {form.segmentos.map((seg, i) => (
                  <SegmentoRow
                    key={i}
                    index={i}
                    segmento={seg}
                    onChange={(updated) => handleSegmentoChange(i, updated)}
                    onContactoChange={(c) =>
                      handleSegmentoMetaChange(i, {
                        contactoNombre: c?.nombre ?? null,
                        contactoTelefono: c?.telefono ?? null,
                      })
                    }
                    onRemove={() => handleRemoveSegmento(i)}
                    canRemove={form.segmentos.length > 1}
                    contactoNombre={segmentosMeta[i]?.contactoNombre}
                    contactoTelefono={segmentosMeta[i]?.contactoTelefono}
                  />
                ))}
              </div>

              <Button
                type="button"
                variant="outline"
                size="sm"
                className="gap-2 text-xs"
                onClick={handleAddSegmento}
              >
                <Plus className="h-3.5 w-3.5" />
                Añadir tramo
              </Button>
            </div>

            {/* ── Guía ─────────────────────────────────────────────────── */}
            <div className="rounded-lg border border-border p-3 space-y-3">
              <div className="flex items-center gap-2">
                <User className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-foreground">Guía</span>
              </div>

              <div>
                <p className="text-xs font-medium text-muted-foreground mb-2">
                  ¿Se contrató guía externo? <span className="text-destructive">*</span>
                </p>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant={form.alquiloGuia ? "default" : "outline"}
                    className="flex-1"
                    onClick={() => {
                      set("alquiloGuia", true)
                      set("guiaSocioId", null)
                    }}
                  >
                    Sí
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant={!form.alquiloGuia ? "default" : "outline"}
                    className="flex-1"
                    onClick={() => {
                      set("alquiloGuia", false)
                      set("contactoGuiaId", null)
                      set("costoGuia", null)
                      setGuiaNombre(null)
                      setGuiaTelefono(null)
                    }}
                  >
                    No
                  </Button>
                </div>
              </div>

              {form.alquiloGuia && (
                <>
                  <ContactoBuscarInput
                    value={form.contactoGuiaId}
                    displayName={guiaNombre}
                    displayPhone={guiaTelefono}
                    onChange={(c) => {
                      set("contactoGuiaId", c?.id ?? null)
                      setGuiaNombre(c?.nombre ?? null)
                      setGuiaTelefono(c?.telefono ?? null)
                    }}
                    label="Contacto del guía *"
                    placeholder="Buscar guía..."
                  />
                  <div className="space-y-1">
                    <label className="text-xs text-muted-foreground">Costo ($)</label>
                    <MoneyInput
                      value={form.costoGuia}
                      onChange={(v) => set("costoGuia", v)}
                    />
                  </div>
                </>
              )}

              {!form.alquiloGuia && (
                <div className="space-y-1">
                  <label className="text-xs text-muted-foreground">
                    Socio que actuó como guía (opcional)
                  </label>
                  <Select
                    value={form.guiaSocioId ?? "none"}
                    onValueChange={(v) => set("guiaSocioId", v === "none" ? null : v)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Ninguno / No aplica" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">Ninguno</SelectItem>
                      {activos.map((p) => (
                        <SelectItem key={p.socioId} value={p.socioId}>
                          {p.socioApellido}, {p.socioNombre}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {form.guiaSocioId && (
                    <p className="text-xs text-muted-foreground">
                      Se registrará como "Guía" en las dignidades de la salida.
                    </p>
                  )}
                </div>
              )}
            </div>

            {/* ── Alojamiento ───────────────────────────────────────────── */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Tent className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-foreground">Alojamiento</span>
              </div>

              {/* Refugio */}
              <div className="rounded-lg border border-border p-3 space-y-3">
                <div className="flex items-center gap-2">
                  <Checkbox
                    id="alquilo-refugio"
                    checked={form.alquiloRefugio}
                    onCheckedChange={(checked) => {
                      set("alquiloRefugio", !!checked)
                      if (!checked) {
                        set("nombreRefugio", null)
                        set("costoRefugio", null)
                        set("contactoRefugioId", null)
                        setRefugioContactoNombre(null)
                      }
                    }}
                  />
                  <label htmlFor="alquilo-refugio" className="text-xs font-medium cursor-pointer">
                    ¿Se alquiló refugio?
                  </label>
                </div>

                {form.alquiloRefugio && (
                  <div className="space-y-3 pl-1 border-l-2 border-primary/30 ml-1">
                    <div className="grid grid-cols-2 gap-2">
                      <div className="space-y-1">
                        <label className="text-xs text-muted-foreground">Nombre del refugio</label>
                        <Input
                          placeholder="Ej: Refugio Whymper"
                          value={form.nombreRefugio ?? ""}
                          onChange={(e) => set("nombreRefugio", e.target.value || null)}
                          className="h-8 text-sm"
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-xs text-muted-foreground">Costo ($)</label>
                        <MoneyInput
                          value={form.costoRefugio}
                          onChange={(v) => set("costoRefugio", v)}
                          className="h-8 text-sm"
                        />
                      </div>
                    </div>
                    <ContactoBuscarInput
                      value={form.contactoRefugioId}
                      displayName={refugioContactoNombre}
                      onChange={(c) => {
                        set("contactoRefugioId", c?.id ?? null)
                        setRefugioContactoNombre(c?.nombre ?? null)
                      }}
                      label="Contacto del refugio (opcional)"
                      placeholder="Buscar contacto del refugio..."
                    />
                  </div>
                )}
              </div>

              {/* Camping */}
              <div className="rounded-lg border border-border p-3 space-y-3">
                <div className="flex items-center gap-2">
                  <Checkbox
                    id="acampo"
                    checked={form.acampo}
                    onCheckedChange={(checked) => {
                      set("acampo", !!checked)
                      if (!checked) {
                        set("nombreCamping", null)
                        set("costoCamping", null)
                        set("contactoCampingId", null)
                        setCampingContactoNombre(null)
                      }
                    }}
                  />
                  <label htmlFor="acampo" className="text-xs font-medium cursor-pointer">
                    ¿Se acampó?
                  </label>
                </div>

                {form.acampo && (
                  <div className="space-y-3 pl-1 border-l-2 border-primary/30 ml-1">
                    <div className="grid grid-cols-2 gap-2">
                      <div className="space-y-1">
                        <label className="text-xs text-muted-foreground">Nombre del camping</label>
                        <Input
                          placeholder="Ej: Camping Chimborazo"
                          value={form.nombreCamping ?? ""}
                          onChange={(e) => set("nombreCamping", e.target.value || null)}
                          className="h-8 text-sm"
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-xs text-muted-foreground">Costo ($)</label>
                        <MoneyInput
                          value={form.costoCamping}
                          onChange={(v) => set("costoCamping", v)}
                          className="h-8 text-sm"
                        />
                      </div>
                    </div>
                    <ContactoBuscarInput
                      value={form.contactoCampingId}
                      displayName={campingContactoNombre}
                      onChange={(c) => {
                        set("contactoCampingId", c?.id ?? null)
                        setCampingContactoNombre(c?.nombre ?? null)
                      }}
                      label="Contacto del camping (opcional)"
                      placeholder="Buscar contacto del camping..."
                    />
                  </div>
                )}
              </div>
            </div>

            {/* ── Autos ─────────────────────────────────────────────────── */}
            <div className="rounded-lg border border-border p-3 space-y-3">
              <div className="flex items-center gap-2">
                <Car className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-foreground">¿Dónde se dejaron los autos?</span>
              </div>

              <Select
                value={form.dondeAutos ?? ""}
                onValueChange={(v) => {
                  const val = v as DondeAutos | ""
                  set("dondeAutos", val || null)
                  if (!val || val === "NO_AUTOS") {
                    set("autosDescripcion", null)
                    set("autosLinkUbicacion", null)
                  }
                  if (val !== "PARQUEADERO_SEGURO" && val !== "PARQUEADERO_INSEGURO") {
                    set("costoParqueadero", null)
                  }
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecciona una opción..." />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(DONDE_AUTOS_LABELS) as DondeAutos[]).map((opt) => (
                    <SelectItem key={opt} value={opt}>
                      {DONDE_AUTOS_LABELS[opt]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              {form.dondeAutos && DONDE_AUTOS_REQUIERE_DESCRIPCION.includes(form.dondeAutos) && (
                <div className="space-y-2 pl-1 border-l-2 border-primary/30 ml-1">
                  <div className="space-y-1">
                    <label className="text-xs text-muted-foreground">
                      {DONDE_AUTOS_LABEL_DESCRIPCION[form.dondeAutos] ?? "Dirección"}{" "}
                      <span className="text-destructive">*</span>
                    </label>
                    <Input
                      placeholder={
                        form.dondeAutos === "BASE_MONTANA"
                          ? "Ej: Al inicio del sendero, junto al letrero..."
                          : "Ej: Av. Colón y 10 de Agosto, parqueadero El Centro"
                      }
                      value={form.autosDescripcion ?? ""}
                      onChange={(e) => set("autosDescripcion", e.target.value || null)}
                      className="h-8 text-sm"
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-xs text-muted-foreground">Link de ubicación (opcional)</label>
                    <Input
                      placeholder="https://maps.google.com/..."
                      value={form.autosLinkUbicacion ?? ""}
                      onChange={(e) => set("autosLinkUbicacion", e.target.value || null)}
                      className="h-8 text-sm"
                    />
                  </div>
                </div>
              )}

              {(form.dondeAutos === "PARQUEADERO_SEGURO" || form.dondeAutos === "PARQUEADERO_INSEGURO") && (
                <div className="space-y-2">
                  <p className="text-xs font-medium text-muted-foreground">
                    ¿El parqueadero tiene costo?
                  </p>
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant={form.costoParqueadero != null ? "default" : "outline"}
                      className="flex-1"
                      onClick={() => {
                        if (form.costoParqueadero == null) set("costoParqueadero", 0)
                      }}
                    >
                      Sí
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant={form.costoParqueadero == null ? "default" : "outline"}
                      className="flex-1"
                      onClick={() => set("costoParqueadero", null)}
                    >
                      No
                    </Button>
                  </div>
                  {form.costoParqueadero != null && (
                    <div className="space-y-1">
                      <label className="text-xs text-muted-foreground">Costo del parqueadero ($)</label>
                      <MoneyInput
                        value={form.costoParqueadero}
                        onChange={(v) => set("costoParqueadero", v ?? 0)}
                        className="h-8 text-sm"
                      />
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* ── Costo Total ───────────────────────────────────────────── */}
            <div className="rounded-lg border border-border p-3 space-y-3">
              <div className="flex items-center gap-2">
                <DollarSign className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm font-medium text-foreground">Costo Total</span>
              </div>

              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">Costo total del viaje ($)</label>
                <MoneyInput
                  value={form.costoTotal}
                  onChange={(v) => set("costoTotal", v)}
                  placeholder="0.00"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">Costo por persona ($)</label>
                <MoneyInput
                  value={form.costoPorPersona}
                  onChange={(v) => set("costoPorPersona", v)}
                  placeholder="0.00"
                />
              </div>
            </div>

            {/* Crónica */}
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Crónica de la salida</label>
              <Textarea
                rows={5}
                placeholder="Narra cómo fue la salida..."
                value={form.cronica ?? ""}
                onChange={(e) => set("cronica", e.target.value || undefined)}
              />
            </div>

            {/* Observaciones */}
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Observaciones</label>
              <Textarea
                rows={3}
                placeholder="Incidencias, aspectos técnicos..."
                value={form.observaciones ?? ""}
                onChange={(e) => set("observaciones", e.target.value || undefined)}
              />
            </div>

            {/* Comentarios varios */}
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Comentarios varios</label>
              <Textarea
                rows={2}
                placeholder="Cualquier otro comentario..."
                value={form.comentariosVarios ?? ""}
                onChange={(e) => set("comentariosVarios", e.target.value || undefined)}
              />
            </div>
          </TabsContent>

          {/* ── Tab: Participantes ──────────────────────────────────────── */}
          <TabsContent value="participantes" className="space-y-4 pt-2">
            <p className="text-xs text-muted-foreground">
              Marca como <strong>Confirmado</strong> a quienes sí asistieron, y como <strong>No fue</strong>
              a quienes se inscribieron pero no se presentaron. Los no asistentes aparecerán tachados.
              Puedes agregar socios que se unieron el día de la salida.
            </p>

            {activos.length === 0 && noFueron.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">
                No hay participantes registrados en esta salida.
              </p>
            ) : (
              <ul className="divide-y divide-border rounded-xl border border-border">
                {[...activos, ...noFueron].map((p) => {
                  const asistio = p.estadoInscripcion !== "NO_FUE"
                  return (
                    <li key={p.id} className="flex items-center gap-3 px-4 py-2.5">
                      <Checkbox
                        id={`p-${p.id}`}
                        checked={asistio}
                        onCheckedChange={() => toggleAsistencia(p.id, p.estadoInscripcion)}
                        disabled={estadoMutation.isPending}
                      />
                      <label
                        htmlFor={`p-${p.id}`}
                        className={`flex-1 cursor-pointer select-none text-sm ${
                          asistio
                            ? "text-foreground"
                            : "line-through text-muted-foreground"
                        }`}
                      >
                        {p.socioApellido}, {p.socioNombre}
                      </label>
                      {!asistio && (
                        <Badge variant="outline" className="text-xs text-muted-foreground">
                          No fue
                        </Badge>
                      )}
                      {asistio && p.estadoInscripcion === "CONFIRMADO" && (
                        <Badge variant="default" className="text-xs bg-green-600">Confirmado</Badge>
                      )}
                      {p.esJefeSalida && asistio && (
                        <Badge variant="secondary" className="text-xs">Jefe</Badge>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}

            {/* Agregar participante */}
            <div className="rounded-xl border border-dashed border-border p-4 space-y-3">
              <button
                type="button"
                className="flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                onClick={() => setShowSearch((v) => !v)}
              >
                <UserPlus className="h-4 w-4" />
                Agregar participante
              </button>

              {showSearch && (
                <div className="space-y-2">
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                    <Input
                      className="pl-9"
                      placeholder="Buscar socio por nombre o cédula..."
                      value={searchQ}
                      onChange={(e) => setSearchQ(e.target.value)}
                      autoFocus
                    />
                    {searchQ && (
                      <button
                        type="button"
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                        onClick={() => setSearchQ("")}
                      >
                        <X className="h-4 w-4" />
                      </button>
                    )}
                  </div>

                  {searchQ.length >= 2 && sociosData && (
                    <ul className="rounded-lg border border-border divide-y divide-border max-h-48 overflow-y-auto">
                      {sociosData
                        .filter((s) => !sociosEnSalida.has(s.id))
                        .map((s) => (
                          <li key={s.id}>
                            <button
                              type="button"
                              className="w-full px-3 py-2 text-left text-sm hover:bg-accent transition-colors flex items-center justify-between"
                              onClick={() => handleAgregarSocio(s.id, `${s.nombre} ${s.apellido}`)}
                              disabled={inscribirMutation.isPending}
                            >
                              <span className="font-medium">{s.apellido}, {s.nombre}</span>
                              <span className="text-xs text-primary">Agregar</span>
                            </button>
                          </li>
                        ))}
                      {sociosData.filter((s) => !sociosEnSalida.has(s.id)).length === 0 && (
                        <li className="px-3 py-3 text-sm text-muted-foreground text-center">
                          No se encontraron socios disponibles
                        </li>
                      )}
                    </ul>
                  )}
                  {searchQ.length >= 1 && searchQ.length < 2 && (
                    <p className="text-xs text-muted-foreground">Escribe al menos 2 caracteres...</p>
                  )}
                </div>
              )}
            </div>
          </TabsContent>
        </Tabs>

        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={onClose} disabled={isPending}>
            {activeTab === "participantes" ? "Cerrar" : "Cancelar"}
          </Button>
          {activeTab === "participantes" ? (
            <Button onClick={() => setActiveTab("datos")}>
              Guardar participantes →
            </Button>
          ) : (
            <Button onClick={handleSubmit} disabled={isPending}>
              {isPending ? "Guardando..." : isEditing ? "Guardar cambios" : "Crear informe"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
