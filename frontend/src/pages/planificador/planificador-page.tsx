import { useState, useEffect } from "react"
import { useSearchParams } from "react-router"
import {
  Map, Mountain, CheckCircle2, Clock, Truck, User2,
  DollarSign, AlertTriangle, Info, ExternalLink, Backpack,
  Bike, TrendingUp, Footprints,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Separator } from "@/components/ui/separator"
import { useMountainsList } from "@/hooks/use-mountains"
import { useRutasByMountain, useRutasByActividad } from "@/hooks/use-rutas"
import { useSalidasList, useSalidaDetail } from "@/hooks/use-salidas"
import { useRecomendacion, type RecomendacionResponse } from "@/hooks/use-planificador"
import type { TipoActividad } from "@/types/rutas"

// ─── Categorías ───────────────────────────────────────────────────────────────

const CATEGORIAS: { value: TipoActividad; label: string; Icon: React.ElementType }[] = [
  { value: "ALPINISMO", label: "Alpinismo",  Icon: Mountain   },
  { value: "ESCALADA",  label: "Escalada",   Icon: TrendingUp },
  { value: "TREKKING",  label: "Trekking",   Icon: Footprints },
  { value: "CICLISMO",  label: "Ciclismo",   Icon: Bike       },
]

// ─── Page ─────────────────────────────────────────────────────────────────────

export function PlanificadorPage() {
  const [searchParams] = useSearchParams()
  const salidaIdParam = searchParams.get("salidaId") ?? undefined
  const defaultTab = salidaIdParam ? "salida" : "ruta"

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Map className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Planificador de salidas</h1>
          <p className="text-muted-foreground">
            Consulta estadísticas históricas y datos técnicos de una ruta antes de planificar tu salida.
          </p>
        </div>
      </div>

      <Tabs defaultValue={defaultTab} className="space-y-4">
        <TabsList>
          <TabsTrigger value="ruta">Buscar por ruta</TabsTrigger>
          <TabsTrigger value="salida">Desde una salida planificada</TabsTrigger>
        </TabsList>

        <TabsContent value="ruta"><PorRutaTab /></TabsContent>
        <TabsContent value="salida"><PorSalidaTab initialSalidaId={salidaIdParam} /></TabsContent>
      </Tabs>
    </div>
  )
}

// ─── Tab: Buscar por ruta ─────────────────────────────────────────────────────

function PorRutaTab() {
  const [categoria, setCategoria] = useState<TipoActividad | null>(null)
  const [mountainId, setMountainId] = useState<number | null>(null)
  const [rutaId, setRutaId] = useState<number | null>(null)

  const { data: mountains, isLoading: loadingMountains } = useMountainsList({ size: 500, sort: "nombre,asc" })

  const { data: rutasMtnPage, isLoading: loadingRutasMtn } = useRutasByMountain(
    categoria === "ALPINISMO" ? mountainId : null
  )
  const { data: rutasActPage, isLoading: loadingRutasAct } = useRutasByActividad(
    categoria && categoria !== "ALPINISMO" ? categoria : null
  )

  const rutas        = categoria === "ALPINISMO" ? (rutasMtnPage?.content ?? []) : (rutasActPage?.content ?? [])
  const loadingRutas = categoria === "ALPINISMO" ? loadingRutasMtn : loadingRutasAct

  const { data: recomendacion, isLoading: loadingRec, error } = useRecomendacion(rutaId)

  // Auto-seleccionar si solo hay una ruta
  useEffect(() => {
    if (rutas.length === 1 && rutaId === null) setRutaId(rutas[0].id)
  }, [rutas, rutaId])

  function handleCategoriaChange(value: TipoActividad) {
    setCategoria(value)
    setMountainId(null)
    setRutaId(null)
  }

  function handleMountainChange(value: string) {
    setMountainId(Number(value))
    setRutaId(null)
  }

  const routeSelectorEnabled = categoria === "ALPINISMO" ? !!mountainId : !!categoria

  return (
    <div className="space-y-5">
      {/* Botones de categoría */}
      <div>
        <p className="text-sm font-medium mb-2">Tipo de actividad</p>
        <div className="flex flex-wrap gap-2">
          {CATEGORIAS.map(({ value, label, Icon }) => (
            <button
              key={value}
              onClick={() => handleCategoriaChange(value)}
              className={`inline-flex items-center gap-1.5 rounded-full border px-4 py-1.5 text-sm font-medium transition-colors
                ${categoria === value
                  ? "bg-primary text-primary-foreground border-primary"
                  : "border-border bg-background hover:bg-muted text-foreground"}`}
            >
              <Icon className="h-4 w-4" />
              {label}
            </button>
          ))}
        </div>
      </div>

      {categoria && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-2xl">
          {/* Montaña (solo Alpinismo) */}
          {categoria === "ALPINISMO" && (
            <div className="space-y-1">
              <label className="text-sm font-medium">Montaña</label>
              <Select
                disabled={loadingMountains}
                value={mountainId?.toString() ?? ""}
                onValueChange={handleMountainChange}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecciona una montaña" />
                </SelectTrigger>
                <SelectContent>
                  {mountains?.content.map((m) => (
                    <SelectItem key={m.id} value={m.id.toString()}>
                      {m.nombre}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          {/* Ruta */}
          <div className="space-y-1">
            <label className="text-sm font-medium">Ruta</label>
            <Select
              disabled={!routeSelectorEnabled || loadingRutas}
              value={rutaId?.toString() ?? ""}
              onValueChange={(v) => setRutaId(Number(v))}
            >
              <SelectTrigger>
                <SelectValue
                  placeholder={
                    categoria === "ALPINISMO" && !mountainId
                      ? "Primero selecciona una montaña"
                      : loadingRutas
                      ? "Cargando rutas..."
                      : rutas.length === 0
                      ? "No hay rutas aprobadas"
                      : "Selecciona una ruta"
                  }
                />
              </SelectTrigger>
              <SelectContent>
                {rutas.map((r) => (
                  <SelectItem key={r.id} value={r.id.toString()}>
                    {r.nombre}
                    {r.dificultadResumen && (
                      <span className="ml-2 text-xs text-muted-foreground">{r.dificultadResumen}</span>
                    )}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      )}

      {loadingRec && rutaId && <LoadingPanel />}
      {error && <ErrorPanel />}
      {recomendacion && <RecomendacionPanel rec={recomendacion} />}
    </div>
  )
}

// ─── Tab: Desde salida planificada ───────────────────────────────────────────

function PorSalidaTab({ initialSalidaId }: { initialSalidaId?: string }) {
  const [rutaId, setRutaId] = useState<number | null>(null)
  const [salidaSeleccionada, setSalidaSeleccionada] = useState<string | null>(initialSalidaId ?? null)

  const { data: salidasPage, isLoading: loadingSalidas } = useSalidasList({
    estado: "PLANIFICADA",
    size: 100,
    sort: "fechaInicio,asc",
  })
  const { data: recomendacion, isLoading: loadingRec, error } = useRecomendacion(rutaId)

  const salidas = salidasPage?.content ?? []

  useEffect(() => {
    if (salidas.length === 1 && salidaSeleccionada === null) {
      setSalidaSeleccionada(salidas[0].id)
    }
  }, [salidas, salidaSeleccionada])

  function handleSalidaChange(salidaId: string) {
    setSalidaSeleccionada(salidaId)
    setRutaId(null)
  }

  return (
    <div className="space-y-4">
      <div className="max-w-lg space-y-1">
        <label className="text-sm font-medium">Salida planificada</label>
        <Select
          disabled={loadingSalidas}
          value={salidaSeleccionada ?? ""}
          onValueChange={handleSalidaChange}
        >
          <SelectTrigger>
            <SelectValue placeholder="Selecciona una salida planificada" />
          </SelectTrigger>
          <SelectContent>
            {salidas.length === 0 && (
              <SelectItem value="__none__" disabled>
                No hay salidas planificadas
              </SelectItem>
            )}
            {salidas.map((s) => (
              <SelectItem key={s.id} value={s.id}>
                {s.nombre} — {new Date(s.fechaInicio).toLocaleDateString("es-EC")}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {salidaSeleccionada && (
        <SalidaRutaLoader
          salidaId={salidaSeleccionada}
          onRutaId={(id) => setRutaId(id)}
        />
      )}

      {loadingRec && rutaId && <LoadingPanel />}
      {error && <ErrorPanel />}
      {recomendacion && <RecomendacionPanel rec={recomendacion} />}
    </div>
  )
}

// ─── Loader: obtener rutaId desde SalidaDetail ───────────────────────────────

function SalidaRutaLoader({
  salidaId,
  onRutaId,
}: {
  salidaId: string
  onRutaId: (id: number | null) => void
}) {
  const { data } = useSalidaDetail(salidaId)

  useEffect(() => {
    if (data) onRutaId(data.rutaId)
  }, [data, onRutaId])

  if (data && !data.rutaId) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Info className="h-4 w-4" />
        Esta salida no tiene una ruta asignada.
      </div>
    )
  }

  return null
}

// ─── Panel principal de recomendaciones ──────────────────────────────────────

function RecomendacionPanel({ rec }: { rec: RecomendacionResponse }) {
  return (
    <div className="space-y-4">
      {/* Encabezado */}
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-xl font-semibold">{rec.rutaNombre}</h2>
          <p className="text-muted-foreground text-sm">
            {rec.mountainNombre}{rec.sectorZona ? ` · ${rec.sectorZona}` : ""}
          </p>
        </div>
        {rec.trackUrl && (
          <Button variant="outline" size="sm" asChild>
            <a href={rec.trackUrl} target="_blank" rel="noopener noreferrer">
              <ExternalLink className="h-4 w-4 mr-1" />
              Ver track GPS
            </a>
          </Button>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Datos técnicos — según tipo de actividad */}
        {rec.tipoActividad === "ALPINISMO" && <DatosTecnicosAlpinismo rec={rec} />}
        {rec.tipoActividad === "ESCALADA"  && <DatosTecnicosEscalada rec={rec} />}
        {rec.tipoActividad === "TREKKING"  && <DatosTecnicosTrekking rec={rec} />}
        {rec.tipoActividad === "CICLISMO"  && <DatosTecnicosCiclismo rec={rec} />}

        {/* Permisos */}
        <div className="rounded-xl border border-border bg-card p-5 space-y-3">
          <h3 className="text-base font-semibold flex items-center gap-2">
            <Info className="h-4 w-4 text-primary" />
            General
          </h3>
          <div className="space-y-2 text-sm">
            <DataRow
              label="Permisos requeridos"
              value={
                <Badge variant={rec.requierePermisos ? "destructive" : "secondary"}>
                  {rec.requierePermisos ? "Sí" : "No"}
                </Badge>
              }
            />
          </div>
        </div>
      </div>

      <Separator />

      {/* Estadísticas históricas */}
      <EstadisticasPanel rec={rec} />
    </div>
  )
}

// ─── Paneles técnicos por tipo ────────────────────────────────────────────────

function DatosTecnicosAlpinismo({ rec }: { rec: RecomendacionResponse }) {
  return (
    <>
      <div className="rounded-xl border border-border bg-card p-5 space-y-3">
        <h3 className="text-base font-semibold flex items-center gap-2">
          <Mountain className="h-4 w-4 text-primary" />
          Datos técnicos
        </h3>
        <div className="space-y-2 text-sm">
          <DataRow label="Sadday Nivel Técnico"   value={rec.saddayNivelTecnicoEscala} />
          <DataRow label="Sadday Nivel Físico"    value={rec.saddayNivelFisicoEscala} />
          <DataRow label="Escala IFAS"            value={rec.escalaAlpinaIfasGrado} />
          <DataRow label="Dificultad roca (UIAA)" value={rec.dificultadRocaUiaa} />
          <DataRow label="Dificultad hielo (WI)"  value={rec.dificultadHieloGrado} />
        </div>
      </div>
      <div className="rounded-xl border border-border bg-card p-5 space-y-3">
        <h3 className="text-base font-semibold flex items-center gap-2">
          <Backpack className="h-4 w-4 text-primary" />
          Equipo recomendado
        </h3>
        {rec.equipoMontanaNombre
          ? <p className="text-sm font-medium">{rec.equipoMontanaNombre}</p>
          : <p className="text-sm text-muted-foreground">No especificado</p>}
      </div>
    </>
  )
}

function DatosTecnicosEscalada({ rec }: { rec: RecomendacionResponse }) {
  return (
    <div className="rounded-xl border border-border bg-card p-5 space-y-3">
      <h3 className="text-base font-semibold flex items-center gap-2">
        <TrendingUp className="h-4 w-4 text-primary" />
        Datos técnicos
      </h3>
      <div className="space-y-2 text-sm">
        <DataRow label="Dificultad roca (UIAA)" value={rec.escaladaDificultadRocaUiaa} />
        <DataRow label="Tipo de escalada"       value={rec.escaladaTipoEscalada} />
        <DataRow label="Número de cintas"       value={rec.escaladaNumCintas != null ? String(rec.escaladaNumCintas) : null} />
        <DataRow label="Altura de la vía"       value={rec.escaladaAlturaViaM != null ? `${rec.escaladaAlturaViaM} m` : null} />
        <DataRow label="Tipo de roca"           value={rec.escaladaTipoRoca} />
      </div>
    </div>
  )
}

function DatosTecnicosTrekking({ rec }: { rec: RecomendacionResponse }) {
  return (
    <div className="rounded-xl border border-border bg-card p-5 space-y-3">
      <h3 className="text-base font-semibold flex items-center gap-2">
        <Footprints className="h-4 w-4 text-primary" />
        Datos técnicos
      </h3>
      <div className="space-y-2 text-sm">
        <DataRow label="Dificultad"       value={rec.trekkingDificultadNombre} />
        <DataRow label="Ruta circular"    value={rec.trekkingEsCircular != null ? (rec.trekkingEsCircular ? "Sí" : "No") : null} />
        <DataRow label="Fuentes de agua"  value={rec.trekkingFuentesAgua != null ? (rec.trekkingFuentesAgua ? "Sí" : "No") : null} />
        <DataRow label="Tipo de terreno"  value={rec.trekkingTipoTerreno} />
      </div>
    </div>
  )
}

function DatosTecnicosCiclismo({ rec }: { rec: RecomendacionResponse }) {
  return (
    <div className="rounded-xl border border-border bg-card p-5 space-y-3">
      <h3 className="text-base font-semibold flex items-center gap-2">
        <Bike className="h-4 w-4 text-primary" />
        Datos técnicos
      </h3>
      <div className="space-y-2 text-sm">
        <DataRow label="Tipo de bicicleta"      value={rec.ciclismoTipoBicicleta} />
        <DataRow label="Dificultad técnica"     value={rec.ciclismoDificultadTecnica} />
        <DataRow label="Superficie predominante" value={rec.ciclismoSuperficiePredominante} />
      </div>
    </div>
  )
}

// ─── Estadísticas históricas ──────────────────────────────────────────────────

function EstadisticasPanel({ rec }: { rec: RecomendacionResponse }) {
  return (
    <div>
      <div className="flex items-center gap-2 mb-3">
        <h3 className="font-semibold">Estadísticas históricas</h3>
        <Badge variant="outline">
          {rec.totalSalidasPrevias} salida{rec.totalSalidasPrevias !== 1 ? "s" : ""} registrada{rec.totalSalidasPrevias !== 1 ? "s" : ""}
        </Badge>
        {rec.datosInsuficientes && (
          <Badge variant="secondary" className="gap-1">
            <AlertTriangle className="h-3 w-3" />
            Datos insuficientes
          </Badge>
        )}
      </div>

      {rec.totalSalidasPrevias === 0 ? (
        <p className="text-sm text-muted-foreground">No hay salidas previas registradas para esta ruta.</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <StatCard
            icon={<CheckCircle2 className="h-4 w-4" />}
            label="Tasa de éxito"
            value={rec.tasaExitoPct !== null ? `${rec.tasaExitoPct}%` : "—"}
            warning={rec.datosInsuficientes}
          />
          <StatCard
            icon={<Clock className="h-4 w-4" />}
            label="Hora de salida promedio"
            value={rec.horaSalidaPromedioClub ?? "—"}
            warning={rec.datosInsuficientes}
          />
          <StatCard
            icon={<Truck className="h-4 w-4" />}
            label="Salidas con transporte"
            value={rec.pctAlquiloTransporte !== null ? `${rec.pctAlquiloTransporte}%` : "—"}
            sub={rec.costoPromedioTransporte !== null ? `Costo prom. $${rec.costoPromedioTransporte.toFixed(2)}` : undefined}
            warning={rec.datosInsuficientes}
          />
          <StatCard
            icon={<User2 className="h-4 w-4" />}
            label="Salidas con guía externo"
            value={rec.pctContratoGuia !== null ? `${rec.pctContratoGuia}%` : "—"}
            sub={rec.costoPromedioGuia !== null ? `Costo prom. $${rec.costoPromedioGuia.toFixed(2)}` : undefined}
            warning={rec.datosInsuficientes}
          />
          <StatCard
            icon={<DollarSign className="h-4 w-4" />}
            label="Presupuesto total promedio"
            value={rec.costoTotalPromedio !== null ? `$${rec.costoTotalPromedio.toFixed(2)}` : "—"}
            warning={rec.datosInsuficientes}
          />
        </div>
      )}

      {rec.datosInsuficientes && rec.totalSalidasPrevias > 0 && (
        <p className="text-xs text-muted-foreground mt-3 flex items-center gap-1">
          <AlertTriangle className="h-3 w-3" />
          Las estadísticas son referenciales — se necesitan al menos 3 salidas para considerarlas fiables.
        </p>
      )}
    </div>
  )
}

// ─── Sub-componentes ──────────────────────────────────────────────────────────

function DataRow({ label, value }: { label: string; value: React.ReactNode }) {
  if (value === null || value === undefined || value === "") return null
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  )
}

function StatCard({
  icon, label, value, sub, warning,
}: {
  icon: React.ReactNode
  label: string
  value: string
  sub?: string
  warning?: boolean
}) {
  return (
    <div className={`rounded-xl border bg-card p-4 ${warning ? "border-yellow-300 dark:border-yellow-700" : "border-border"}`}>
      <div className="flex items-center gap-2 text-muted-foreground mb-1">
        {icon}
        <span className="text-xs">{label}</span>
      </div>
      <p className="text-2xl font-bold">{value}</p>
      {sub && <p className="text-xs text-muted-foreground mt-0.5">{sub}</p>}
    </div>
  )
}

function LoadingPanel() {
  return (
    <div className="rounded-xl border border-border bg-card p-6 text-center text-muted-foreground text-sm">
      Cargando recomendaciones…
    </div>
  )
}

function ErrorPanel() {
  return (
    <div className="rounded-xl border border-destructive bg-card p-6 text-center text-destructive text-sm">
      No se pudieron cargar las recomendaciones. Verifica que la ruta esté aprobada.
    </div>
  )
}
