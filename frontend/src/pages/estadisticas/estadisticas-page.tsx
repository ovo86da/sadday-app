import React, { useState, useEffect, useRef } from "react"
import {
  BarChart, Bar, PieChart, Pie, Cell, ResponsiveContainer,
  XAxis, YAxis, Tooltip, Legend, LabelList,
} from "recharts"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import {
  BarChart2, Users, Crown, Award, Search, Mountain, TrendingUp, Star, CalendarDays,
  Route, MapPin, CheckCircle2, XCircle, AlertCircle, Calendar,
} from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"
import {
  useClubEstadisticas,
  useClubRankings,
  useParticipantesFiltrados,
  useRankingReuniones,
  useRankingMontanaRuta,
  useBuscarMontanaRuta,
  useHistorialSocio,
  useActividadPorPeriodo,
  type ParticipantesFilters,
  type PeriodoBusquedaFilters,
  type TipoPeriodoBusqueda,
  type SalidaPeriodoItem,
  type MontanaPeriodoItem,
  type RutaPeriodoItem,
  type SocioRankingItem,
  type ReunionAsistenciaMesItem,
  type CategoriaEstadisticaItem,
  type CategoriaDignidadRankingItem,
  type MontanaRankingItem,
  type RutaRankingItem,
  type MontanaRutaBusquedaItem,
  type SocioHistorialResponse,
} from "@/hooks/use-estadisticas"
import { useLookups } from "@/hooks/use-socios"
import { useMountainsList } from "@/hooks/use-mountains"
import { useRutasList } from "@/hooks/use-rutas"
import { useSalidaLookups } from "@/hooks/use-salidas"
import { CATEGORIA_HEX, TIPO_ACTIVIDAD_LABELS } from "@/types/rutas"

// ─── Palette ──────────────────────────────────────────────────────────────────
const COLORS = [
  "#6366f1", "#f59e0b", "#10b981", "#3b82f6",
  "#ef4444", "#8b5cf6", "#ec4899", "#14b8a6",
]

// ─── Tooltip components ───────────────────────────────────────────────────────

const TT = "rounded-lg border border-border bg-popover px-3 py-2 shadow-md text-sm min-w-[140px]"
const TTRow = ({ label, value, color }: { label: string; value: string | number; color?: string }) => (
  <div className="flex items-center justify-between gap-4">
    <span className="flex items-center gap-1.5 text-muted-foreground">
      {color && <span className="inline-block h-2 w-2 shrink-0 rounded-sm" style={{ background: color }} />}
      {label}
    </span>
    <span className="font-medium text-foreground tabular-nums">{value}</span>
  </div>
)

interface TPBase { active?: boolean; label?: string | number }
// value matches Recharts ValueType (string | number | undefined); payload entries may have undefined value
interface TPItem  { name?: string | number; value: number | string | undefined; color: string; payload?: Record<string, unknown> }

function StatsTooltip({ active, payload, label }: TPBase & { payload?: readonly TPItem[] }) {
  if (!active || !payload?.length) return null
  return (
    <div className={TT}>
      {label && <p className="mb-1.5 font-semibold text-foreground">{label}</p>}
      {payload.map((p, i) => <TTRow key={i} label={String(p.name ?? "")} value={p.value ?? 0} color={p.color} />)}
    </div>
  )
}

function PieTooltip({ active, payload }: TPBase & { payload?: ReadonlyArray<TPItem & { payload: { porcentaje?: number } }> }) {
  if (!active || !payload?.length) return null
  const item = payload[0]
  return (
    <div className={TT}>
      <p className="mb-1.5 font-semibold text-foreground">{item.name}</p>
      <TTRow label="Total" value={item.value ?? 0} />
      {item.payload?.porcentaje != null && <TTRow label="Porcentaje" value={`${item.payload.porcentaje}%`} />}
    </div>
  )
}

function RankingTooltip({ active, payload, suffix = "" }: TPBase & { payload?: readonly TPItem[]; suffix?: string }) {
  if (!active || !payload?.length) return null
  const p = payload[0]
  const d = p.payload as { fullName?: string; nivel?: string }
  return (
    <div className={TT}>
      {d.fullName && <p className="mb-1.5 font-semibold text-foreground">{d.fullName}</p>}
      {d.nivel    && <TTRow label="Nivel" value={d.nivel} />}
      <TTRow label="Total" value={`${p.value ?? 0}${suffix ? " " + suffix : ""}`} color={p.color} />
    </div>
  )
}

function ReunionesTooltip({ active, payload, label }: TPBase & { payload?: readonly TPItem[] }) {
  if (!active || !payload?.length) return null
  const d = payload[0].payload as { promedio?: number; reuniones?: number }
  return (
    <div className={TT}>
      {label && <p className="mb-1.5 font-semibold text-foreground">{label}</p>}
      <TTRow label="Promedio asistentes" value={Number(d.promedio ?? 0).toFixed(1)} color={payload[0].color} />
      {d.reuniones != null && <TTRow label="Reuniones" value={d.reuniones} />}
    </div>
  )
}

function MontanaTooltip({ active, payload, suffix = "salidas" }: TPBase & { payload?: readonly TPItem[]; suffix?: string }) {
  if (!active || !payload?.length) return null
  const p = payload[0]
  const d = p.payload as { fullName?: string; region?: string; altitud?: number }
  const sub = [d.region, d.altitud ? `${d.altitud}m` : null].filter(Boolean).join(" · ")
  return (
    <div className={TT}>
      {d.fullName && <p className="font-semibold text-foreground leading-tight">{d.fullName}</p>}
      {sub        && <p className="mb-1.5 text-xs text-muted-foreground">{sub}</p>}
      <TTRow label={suffix} value={p.value ?? 0} color={p.color} />
    </div>
  )
}

function RutaTooltip({ active, payload, suffix = "salidas" }: TPBase & { payload?: readonly TPItem[]; suffix?: string }) {
  if (!active || !payload?.length) return null
  const p = payload[0]
  const d = p.payload as { fullName?: string; mountain?: string; tipo?: string }
  const sub = d.mountain ?? d.tipo ?? null
  return (
    <div className={TT}>
      {d.fullName && <p className="font-semibold text-foreground leading-tight">{d.fullName}</p>}
      {sub        && <p className="mb-1.5 text-xs text-muted-foreground">{sub}</p>}
      <TTRow label={suffix} value={p.value ?? 0} color={p.color} />
    </div>
  )
}

function RutaPartTooltip({ active, payload }: TPBase & { payload?: readonly TPItem[] }) {
  if (!active || !payload?.length) return null
  const p = payload[0]
  const d = p.payload as { fullName?: string; mountain?: string; tipo?: string; salidas?: number }
  const sub = d.mountain ?? d.tipo ?? null
  return (
    <div className={TT}>
      {d.fullName && <p className="font-semibold text-foreground leading-tight">{d.fullName}</p>}
      {sub        && <p className="mb-1.5 text-xs text-muted-foreground">{sub}</p>}
      <TTRow label="Personas" value={p.value ?? 0} color={p.color} />
      {d.salidas != null && <TTRow label="Salidas" value={d.salidas} />}
    </div>
  )
}
const COLOR_JEFE  = "#f59e0b"
const COLOR_PART  = "#6366f1"

// ─── Page ─────────────────────────────────────────────────────────────────────

export function EstadisticasPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <BarChart2 className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Estadísticas del club</h1>
          <p className="text-muted-foreground">
            Distribución de socios, rankings y búsqueda avanzada de participantes.
          </p>
        </div>
      </div>

      <Tabs defaultValue="resumen" className="space-y-4">
        <TabsList>
          <TabsTrigger value="resumen">Resumen</TabsTrigger>
          <TabsTrigger value="rankings">Rankings salidas</TabsTrigger>
          <TabsTrigger value="reuniones">Reuniones</TabsTrigger>
          <TabsTrigger value="montana-rutas">Ranking Montaña &amp; Rutas</TabsTrigger>
          <TabsTrigger value="busqueda">Búsqueda avanzada</TabsTrigger>
        </TabsList>

        <TabsContent value="resumen"><ResumenTab /></TabsContent>
        <TabsContent value="rankings"><RankingsTab /></TabsContent>
        <TabsContent value="reuniones"><ReunionesTab /></TabsContent>
        <TabsContent value="montana-rutas"><RankingMontanaRutaTab /></TabsContent>
        <TabsContent value="busqueda"><BusquedaTab /></TabsContent>
      </Tabs>
    </div>
  )
}

// ─── Resumen tab ──────────────────────────────────────────────────────────────

function ResumenTab() {
  const { data, isLoading } = useClubEstadisticas()

  if (isLoading) return <Skeleton />
  if (!data) return <Empty />

  const total = data.totalSocios

  return (
    <div className="space-y-6">
      {/* KPI row */}
      <div className="grid gap-4 sm:grid-cols-3">
        <KpiCard icon={<Users className="h-5 w-5" />} label="Total socios" value={total} />
        <KpiCard
          icon={<Star className="h-5 w-5 text-green-500" />}
          label="Habilitados"
          value={data.habilitados}
          sub={total > 0 ? `${Math.round(data.habilitados * 100 / total)}%` : "—"}
        />
        <KpiCard
          icon={<Users className="h-5 w-5 text-red-400" />}
          label="Inhabilitados"
          value={data.inhabilitados}
          sub={total > 0 ? `${Math.round(data.inhabilitados * 100 / total)}%` : "—"}
        />
      </div>

      {/* Charts row */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Pie: nivel técnico */}
        <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
          <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4 flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <TrendingUp className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Distribución por nivel técnico</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Clasificación técnica del total de socios activos.</p>
            </div>
          </div>
          <div className="p-5">
          {data.porNivelTecnico.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-8">Sin datos</p>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie
                  data={data.porNivelTecnico}
                  dataKey="total"
                  nameKey="nombre"
                  cx="50%" cy="50%"
                  outerRadius={90}
                  label={(props: unknown) => {
                    const { nombre, porcentaje } = props as { nombre?: string; porcentaje?: number }
                    return `${nombre ?? ""} ${porcentaje ?? 0}%`
                  }}
                  labelLine={false}
                >
                  {data.porNivelTecnico.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip content={<PieTooltip />} />
                <Legend wrapperStyle={{ fontSize: "12px" }} />
              </PieChart>
            </ResponsiveContainer>
          )}
          <div className="mt-4 divide-y divide-border text-sm">
            {data.porNivelTecnico.map((n) => (
              <div key={n.nombre} className="flex items-center justify-between py-1.5">
                <span className="text-foreground">{n.nombre}</span>
                <span className="font-semibold tabular-nums">
                  {n.total}{" "}
                  <span className="font-normal text-muted-foreground">({n.porcentaje}%)</span>
                </span>
              </div>
            ))}
          </div>
          </div>
        </div>

        {/* Bar: tipo de socio */}
        <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
          <div className="border-b border-border/60 bg-gradient-to-r from-indigo-500/5 to-transparent px-5 py-4 flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10">
              <Users className="h-4 w-4 text-indigo-500" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Socios por tipo</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Distribución del padrón según la categoría de membresía.</p>
            </div>
          </div>
          <div className="p-5">
          {data.porTipoSocio.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-8">Sin datos</p>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={data.porTipoSocio} margin={{ top: 4, right: 8, bottom: 24, left: 0 }}>
                <XAxis dataKey="nombre" tick={{ fontSize: 11 }} angle={-20} textAnchor="end" interval={0} />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                <Tooltip content={<StatsTooltip />} cursor={{ fill: "hsl(var(--muted))", opacity: 0.4 }} />
                <Bar dataKey="total" name="Socios" radius={[4, 4, 0, 0]}>
                  {data.porTipoSocio.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
          </div>
        </div>
      </div>

    </div>
  )
}

// ─── Rankings tab ─────────────────────────────────────────────────────────────

/** Trunca el nombre para el eje del gráfico: "Apellido, N." */
function shortName(item: SocioRankingItem) {
  const inicial = item.nombre ? item.nombre.charAt(0) + "." : ""
  return `${item.apellido}, ${inicial}`
}

function RankingsTab() {
  const { data, isLoading } = useClubRankings(10)

  if (isLoading) return <Skeleton rows={3} />
  if (!data) return <Empty />

  const jefesChartData = data.topJefesSalida.map((item) => ({
    name: shortName(item),
    fullName: `${item.nombre} ${item.apellido}`,
    total: item.total,
    nivel: item.nivelTecnico ?? "—",
  }))

  const partChartData = data.topParticipaciones.map((item) => ({
    name: shortName(item),
    fullName: `${item.nombre} ${item.apellido}`,
    total: item.total,
    nivel: item.nivelTecnico ?? "—",
  }))

  return (
    <div className="space-y-6">

      {/* Top jefes de salida — gráfico + lista */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-yellow-500/5 to-transparent px-5 py-4 flex items-start gap-3">
          <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-yellow-500/10">
            <Crown className="h-4 w-4 text-yellow-500" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-foreground">Top 10 — Jefe de Salida</h2>
            <p className="text-xs text-muted-foreground mt-0.5">Socios con mayor número de veces como líer de expedición.</p>
          </div>
        </div>
        <div className="p-5">
        {jefesChartData.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">Sin datos</p>
        ) : (
          <div className="grid gap-6 lg:grid-cols-2">
            <ResponsiveContainer width="100%" height={280}>
              <BarChart
                data={jefesChartData}
                layout="vertical"
                margin={{ top: 0, right: 40, bottom: 0, left: 80 }}
              >
                <XAxis type="number" tick={{ fontSize: 11 }} allowDecimals={false} />
                <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={80} />
                <Tooltip content={(p) => <RankingTooltip active={p.active} payload={p.payload as unknown as readonly TPItem[]} suffix="veces" />} cursor={{ fill: "hsl(var(--muted))", opacity: 0.4 }} />
                <Bar dataKey="total" fill={COLOR_JEFE} radius={[0, 4, 4, 0]}>
                  <LabelList dataKey="total" position="right" style={{ fontSize: 11 }} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>

            <RankingList items={data.topJefesSalida} label="veces" medalColors />
          </div>
        )}
        </div>
      </div>

      {/* Top participaciones — gráfico + lista */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4 flex items-start gap-3">
          <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
            <Mountain className="h-4 w-4 text-primary" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-foreground">Top 10 — Más asistencias</h2>
            <p className="text-xs text-muted-foreground mt-0.5">Socios que más salidas han realizado en la historia del club.</p>
          </div>
        </div>
        <div className="p-5">
        {partChartData.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">Sin datos</p>
        ) : (
          <div className="grid gap-6 lg:grid-cols-2">
            <ResponsiveContainer width="100%" height={280}>
              <BarChart
                data={partChartData}
                layout="vertical"
                margin={{ top: 0, right: 40, bottom: 0, left: 80 }}
              >
                <XAxis type="number" tick={{ fontSize: 11 }} allowDecimals={false} />
                <YAxis type="category" dataKey="name" tick={{ fontSize: 11 }} width={80} />
                <Tooltip content={(p) => <RankingTooltip active={p.active} payload={p.payload as unknown as readonly TPItem[]} suffix="salidas" />} cursor={{ fill: "hsl(var(--muted))", opacity: 0.4 }} />
                <Bar dataKey="total" fill={COLOR_PART} radius={[0, 4, 4, 0]}>
                  <LabelList dataKey="total" position="right" style={{ fontSize: 11 }} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>

            <RankingList items={data.topParticipaciones} label="salidas" medalColors />
          </div>
        )}
        </div>
      </div>

      {/* Top por dignidad */}
      {data.topPorDignidad.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-base font-semibold text-foreground flex items-center gap-2">
            <Award className="h-4 w-4 text-primary" />
            Rankings por dignidad
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.topPorDignidad.map((dr) => (
              <RankingCard
                key={dr.dignidad}
                title={dr.dignidad}
                icon={<Award className="h-4 w-4 text-muted-foreground" />}
                items={dr.top}
                label="veces"
              />
            ))}
          </div>
        </div>
      )}

      {/* Por categoría */}
      {data.porCategoria.length > 0 && (
        <CategoriasDashboard items={data.porCategoria} />
      )}

      {/* Rankings por categoría (Jefe de Salida + Guía por deporte) */}
      {data.rankingsPorCategoria && data.rankingsPorCategoria.length > 0 && (
        <RankingsPorCategoria items={data.rankingsPorCategoria} />
      )}

      {data.topJefesSalida.length === 0 &&
        data.topParticipaciones.length === 0 &&
        data.topPorDignidad.length === 0 && (
          <p className="py-12 text-center text-sm text-muted-foreground">
            No hay participaciones registradas todavía.
          </p>
        )}
    </div>
  )
}

// ─── Rankings por categoría ───────────────────────────────────────────────────

const CATEGORIAS_ORDEN = ["ALPINISMO", "ESCALADA", "TREKKING", "CICLISMO"] as const
const DIGNIDADES_ORDEN = ["Jefe de Salida", "Guía"] as const

function RankingsPorCategoria({ items }: { items: CategoriaDignidadRankingItem[] }) {
  // Build a lookup: tipoActividad → dignidad → SocioRankingItem[]
  const byCategoria = new Map<string, Map<string, SocioRankingItem[]>>()
  for (const item of items) {
    if (!byCategoria.has(item.tipoActividad)) byCategoria.set(item.tipoActividad, new Map())
    byCategoria.get(item.tipoActividad)!.set(item.dignidad, item.top)
  }

  const categorias = CATEGORIAS_ORDEN.filter((c) => byCategoria.has(c))

  if (categorias.length === 0) return null

  return (
    <div className="space-y-4">
      <h2 className="text-base font-semibold text-foreground flex items-center gap-2">
        <Crown className="h-4 w-4 text-primary" />
        Rankings por categoría
      </h2>
      {categorias.map((cat) => {
        const hex   = CATEGORIA_HEX[cat] ?? "#8b5cf6"
        const label = TIPO_ACTIVIDAD_LABELS[cat as keyof typeof TIPO_ACTIVIDAD_LABELS] ?? cat
        const porDig = byCategoria.get(cat)!
        return (
          <div
            key={cat}
            className="rounded-xl border border-border bg-card p-5"
            style={{ borderTopColor: hex, borderTopWidth: 3 }}
          >
            <h3 className="mb-4 text-sm font-bold text-foreground" style={{ color: hex }}>
              {label}
            </h3>
            <div className="grid gap-4 sm:grid-cols-2">
              {DIGNIDADES_ORDEN.map((dig) => (
                <RankingCard
                  key={dig}
                  title={dig}
                  icon={<Award className="h-4 w-4 text-muted-foreground" />}
                  items={porDig.get(dig) ?? []}
                  label="veces"
                />
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}

// ─── Categorías dashboard ─────────────────────────────────────────────────────

// Colors are imported from types/rutas: CATEGORIA_HEX, TIPO_ACTIVIDAD_LABELS
// Chart uses two fixed colors to differentiate metric (salidas vs participantes);
// category identity comes from the X-axis label + the KPI card border.
const COLOR_SALIDAS       = "#6366f1"  // indigo — "cuántas salidas"
const COLOR_PARTICIPANTES = "#10b981"  // emerald — "cuántos participantes únicos"

function CategoriasDashboard({ items }: { items: CategoriaEstadisticaItem[] }) {
  const chartData = items.map((item) => ({
    name: TIPO_ACTIVIDAD_LABELS[item.tipoActividad as keyof typeof TIPO_ACTIVIDAD_LABELS] ?? item.tipoActividad,
    salidas: item.totalSalidas,
    participantes: item.totalParticipantes,
    hex: CATEGORIA_HEX[item.tipoActividad] ?? "#8b5cf6",
  }))

  return (
    <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
      <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4 flex items-start gap-3">
        <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
          <TrendingUp className="h-4 w-4 text-primary" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-foreground">Actividad por categoría</h2>
          <p className="text-xs text-muted-foreground mt-0.5">Salidas y participantes únicos agrupados por tipo de actividad.</p>
        </div>
      </div>
      <div className="p-5">

      {/* KPI cards — border accent uses the canonical category color */}
      <div className="mb-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {items.map((item) => (
          <div
            key={item.tipoActividad}
            className="rounded-lg border border-border p-3"
            style={{ borderLeftColor: CATEGORIA_HEX[item.tipoActividad] ?? "#8b5cf6", borderLeftWidth: 4 }}
          >
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
              {TIPO_ACTIVIDAD_LABELS[item.tipoActividad as keyof typeof TIPO_ACTIVIDAD_LABELS] ?? item.tipoActividad}
            </p>
            <p className="mt-1 text-2xl font-bold text-foreground">{item.totalSalidas}</p>
            <p className="text-xs text-muted-foreground">
              salidas · <span className="font-medium text-foreground">{item.totalParticipantes}</span> participantes
            </p>
          </div>
        ))}
      </div>

      {/* Grouped bar chart — indigo = salidas, emerald = participantes */}
      <ResponsiveContainer width="100%" height={240}>
        <BarChart data={chartData} margin={{ top: 0, right: 20, bottom: 0, left: 0 }}>
          <XAxis dataKey="name" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
          <Tooltip content={<StatsTooltip />} cursor={{ fill: "hsl(var(--muted))", opacity: 0.4 }} />
          <Legend wrapperStyle={{ fontSize: "12px" }} />
          <Bar dataKey="salidas" name="Salidas" fill={COLOR_SALIDAS} radius={[4, 4, 0, 0]} />
          <Bar dataKey="participantes" name="Participantes" fill={COLOR_PARTICIPANTES} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
      </div>
    </div>
  )
}

// ─── Búsqueda tab ─────────────────────────────────────────────────────────────

// ─── Socio historial helpers ──────────────────────────────────────────────────

interface SocioMinimal { id: string; nombre: string; apellido: string; cedula: string }

function formatFechaH(iso: string) {
  const [year, month, day] = iso.split("-")
  const months = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
  return `${parseInt(day)} ${months[parseInt(month) - 1]} ${year}`
}

function SocioHistorialPanel({ socioId }: { socioId: string }) {
  const { data, isLoading } = useHistorialSocio(socioId)

  if (isLoading) return <p className="text-sm text-muted-foreground py-4 text-center">Cargando historial…</p>
  if (!data) return <p className="text-sm text-muted-foreground py-4 text-center">Sin datos disponibles.</p>

  return <HistorialContent data={data} />
}

function HistorialContent({ data }: { data: SocioHistorialResponse }) {
  return (
    <div className="space-y-4 pt-2">
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
        <div className="max-h-80 overflow-y-auto rounded-md border border-border">
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
                    {formatFechaH(h.fecha)}
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

// ─── helpers de fecha ─────────────────────────────────────────────────────────

function hoy() {
  return new Date().toISOString().slice(0, 10)
}
function primerDiaMes() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`
}
function primerDiaAnio() {
  return `${new Date().getFullYear()}-01-01`
}
function haceNMeses(n: number) {
  const d = new Date()
  d.setMonth(d.getMonth() - n)
  return d.toISOString().slice(0, 10)
}
function haceNAnios(n: number) {
  const d = new Date()
  d.setFullYear(d.getFullYear() - n)
  return d.toISOString().slice(0, 10)
}

// ─── Formato fecha legible ────────────────────────────────────────────────────

function fmtFecha(iso: string | null | undefined) {
  if (!iso) return "—"
  const [y, m, d] = iso.split("-")
  const meses = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
  return `${parseInt(d)} ${meses[parseInt(m) - 1]} ${y}`
}

// ─── Estado salida badge ──────────────────────────────────────────────────────

const ESTADO_LABELS: Record<string, string> = {
  PLANIFICADA: "Planificada", REALIZADA: "Realizada",
  CANCELADA:   "Cancelada",   EN_CURSO:  "En curso",
}
const ESTADO_VARIANT: Record<string, "default" | "secondary" | "outline" | "destructive"> = {
  REALIZADA: "default", PLANIFICADA: "secondary",
  EN_CURSO: "outline", CANCELADA: "destructive",
}

// ─── Actividad por Período ────────────────────────────────────────────────────

function PeriodoBusquedaPanel() {
  const [tipo,          setTipo]          = useState<TipoPeriodoBusqueda>("salidas")
  const [tipoActividad, setTipoActividad] = useState<string | undefined>(undefined)
  const [fechaDesde,    setFechaDesde]    = useState(primerDiaMes())
  const [fechaHasta,    setFechaHasta]    = useState(hoy())
  const [applied,       setApplied]       = useState<PeriodoBusquedaFilters | null>(null)

  const { data, isLoading } = useActividadPorPeriodo(
    applied ?? { tipo, fechaDesde, fechaHasta },
    applied !== null,
  )

  function aplicar() {
    setApplied({ tipo, fechaDesde, fechaHasta, tipoActividad: tipoActividad || undefined })
  }
  function limpiar() {
    setTipo("salidas")
    setTipoActividad(undefined)
    setFechaDesde(primerDiaMes())
    setFechaHasta(hoy())
    setApplied(null)
  }
  function setAtajo(desde: string, hasta: string) {
    setFechaDesde(desde)
    setFechaHasta(hasta)
  }

  return (
    <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
      <div className="border-b border-border/60 bg-gradient-to-r from-emerald-500/5 to-transparent px-5 py-4 flex items-start gap-3">
        <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-emerald-500/10">
          <Calendar className="h-4 w-4 text-emerald-600" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-foreground">Actividad por Período</h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Qué salidas, montañas o rutas tuvieron actividad en un rango de fechas.
          </p>
        </div>
      </div>

      <div className="p-5 space-y-4">
        {/* Fila de filtros */}
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {/* Tipo */}
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Buscar</label>
            <Select value={tipo} onValueChange={(v) => setTipo(v as TipoPeriodoBusqueda)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="salidas">Salidas</SelectItem>
                <SelectItem value="montanas">Montañas</SelectItem>
                <SelectItem value="rutas">Rutas</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Categoría */}
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Categoría</label>
            <Select
              value={tipoActividad ?? "all"}
              onValueChange={(v) => setTipoActividad(v === "all" ? undefined : v)}
            >
              <SelectTrigger><SelectValue placeholder="Todas" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todas</SelectItem>
                <SelectItem value="ALPINISMO">Alpinismo</SelectItem>
                <SelectItem value="ESCALADA">Escalada</SelectItem>
                <SelectItem value="TREKKING">Trekking</SelectItem>
                <SelectItem value="CICLISMO">Ciclismo</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Fecha desde */}
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Desde</label>
            <Input type="date" value={fechaDesde} onChange={(e) => setFechaDesde(e.target.value)} />
          </div>

          {/* Fecha hasta */}
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Hasta</label>
            <Input type="date" value={fechaHasta} onChange={(e) => setFechaHasta(e.target.value)} />
          </div>
        </div>

        {/* Atajos de período */}
        <div className="flex flex-wrap gap-2">
          <span className="text-xs text-muted-foreground self-center">Período rápido:</span>
          {[
            { label: "Este mes",      desde: primerDiaMes(),  hasta: hoy() },
            { label: "Este año",      desde: primerDiaAnio(), hasta: hoy() },
            { label: "Últimos 6 m",   desde: haceNMeses(6),   hasta: hoy() },
            { label: "Últimos 2 años",desde: haceNAnios(2),   hasta: hoy() },
          ].map((a) => (
            <button
              key={a.label}
              type="button"
              onClick={() => setAtajo(a.desde, a.hasta)}
              className={`rounded-full border px-3 py-0.5 text-xs transition-colors ${
                fechaDesde === a.desde && fechaHasta === a.hasta
                  ? "border-emerald-500 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400"
                  : "border-border text-muted-foreground hover:border-emerald-400 hover:text-foreground"
              }`}
            >
              {a.label}
            </button>
          ))}
        </div>

        {/* Acciones */}
        <div className="flex items-center gap-2 pt-1 border-t border-border/40">
          <Button onClick={aplicar} disabled={isLoading || !fechaDesde || !fechaHasta} className="gap-2">
            <Search className="h-4 w-4" />
            {isLoading ? "Buscando..." : "Buscar"}
          </Button>
          <Button variant="outline" onClick={limpiar}>Limpiar</Button>
        </div>
      </div>

      {/* Resultados */}
      {applied === null ? (
        <div className="mx-5 mb-5 rounded-lg border border-dashed border-border p-8 text-center">
          <CalendarDays className="mx-auto mb-3 h-8 w-8 text-muted-foreground/40" />
          <p className="text-sm text-muted-foreground">
            Selecciona un período y presiona <strong>Buscar</strong>.
          </p>
        </div>
      ) : isLoading ? (
        <div className="mx-5 mb-5 space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-10 animate-pulse rounded-lg bg-muted" />
          ))}
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="mx-5 mb-5 rounded-lg border border-border p-8 text-center">
          <p className="text-sm text-muted-foreground">No hay actividad en ese período.</p>
        </div>
      ) : (
        <div className="mx-5 mb-5 rounded-xl border border-border">
          <div className="flex items-center border-b border-border px-5 py-3">
            <span className="text-sm font-medium text-foreground">
              {data.items.length}{" "}
              {data.tipo === "salidas" ? (data.items.length === 1 ? "salida" : "salidas")
                : data.tipo === "montanas" ? (data.items.length === 1 ? "montaña" : "montañas")
                : (data.items.length === 1 ? "ruta" : "rutas")}
              {" "}en el período
            </span>
          </div>

          {data.tipo === "salidas" && (
            <PeriodoSalidasTable items={data.items as SalidaPeriodoItem[]} />
          )}
          {data.tipo === "montanas" && (
            <PeriodoMontanasTable items={data.items as MontanaPeriodoItem[]} />
          )}
          {data.tipo === "rutas" && (
            <PeriodoRutasTable items={data.items as RutaPeriodoItem[]} />
          )}
        </div>
      )}
    </div>
  )
}

function PeriodoSalidasTable({ items }: { items: SalidaPeriodoItem[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Salida</TableHead>
          <TableHead>Fecha</TableHead>
          <TableHead>Categoría</TableHead>
          <TableHead>Montaña / Ruta</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead className="text-right">Participantes</TableHead>
          <TableHead className="text-center">Cumbre</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((s) => (
          <TableRow key={s.salidaId}>
            <TableCell className="font-medium">{s.nombre}</TableCell>
            <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
              {fmtFecha(s.fecha)}
            </TableCell>
            <TableCell>
              {s.tipoActividad
                ? <Badge variant="secondary" className="text-xs">{TIPO_ACTIVIDAD_LABELS[s.tipoActividad as keyof typeof TIPO_ACTIVIDAD_LABELS] ?? s.tipoActividad}</Badge>
                : <span className="text-xs text-muted-foreground">—</span>}
            </TableCell>
            <TableCell className="text-xs">
              {s.mountainNombre && <span className="font-medium">{s.mountainNombre}</span>}
              {s.mountainNombre && s.rutaNombre && <span className="text-muted-foreground"> · </span>}
              {s.rutaNombre && <span className="text-muted-foreground">{s.rutaNombre}</span>}
              {!s.mountainNombre && !s.rutaNombre && <span className="text-muted-foreground">—</span>}
            </TableCell>
            <TableCell>
              <Badge variant={ESTADO_VARIANT[s.estado] ?? "outline"} className="text-xs">
                {ESTADO_LABELS[s.estado] ?? s.estado}
              </Badge>
            </TableCell>
            <TableCell className="text-right tabular-nums">{s.totalParticipantes}</TableCell>
            <TableCell className="text-center">
              {s.seRealizo === true ? (
                <CheckCircle2 className="h-4 w-4 text-green-600 mx-auto" />
              ) : s.seRealizo === false ? (
                <XCircle className="h-4 w-4 text-muted-foreground mx-auto" />
              ) : (
                <span className="text-muted-foreground text-xs">—</span>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function PeriodoMontanasTable({ items }: { items: MontanaPeriodoItem[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Montaña</TableHead>
          <TableHead>Región</TableHead>
          <TableHead className="text-right">Altitud</TableHead>
          <TableHead className="text-right">Salidas</TableHead>
          <TableHead className="text-right">Participantes</TableHead>
          <TableHead>Primera salida</TableHead>
          <TableHead>Última salida</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((m) => (
          <TableRow key={m.mountainId}>
            <TableCell className="font-medium">{m.nombre}</TableCell>
            <TableCell className="text-xs text-muted-foreground">{m.region ?? "—"}</TableCell>
            <TableCell className="text-right tabular-nums text-xs text-muted-foreground">
              {m.altitud > 0 ? `${m.altitud} m` : "—"}
            </TableCell>
            <TableCell className="text-right tabular-nums font-semibold">{m.totalSalidas}</TableCell>
            <TableCell className="text-right tabular-nums">{m.totalParticipantes}</TableCell>
            <TableCell className="text-xs text-muted-foreground whitespace-nowrap">{fmtFecha(m.primeraFecha)}</TableCell>
            <TableCell className="text-xs text-muted-foreground whitespace-nowrap">{fmtFecha(m.ultimaFecha)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function PeriodoRutasTable({ items }: { items: RutaPeriodoItem[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Ruta</TableHead>
          <TableHead>Montaña</TableHead>
          <TableHead>Categoría</TableHead>
          <TableHead className="text-right">Salidas</TableHead>
          <TableHead className="text-right">Participantes</TableHead>
          <TableHead>Primera salida</TableHead>
          <TableHead>Última salida</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((r) => (
          <TableRow key={r.rutaId}>
            <TableCell className="font-medium">{r.nombre}</TableCell>
            <TableCell className="text-xs text-muted-foreground">{r.mountainNombre ?? "—"}</TableCell>
            <TableCell>
              {r.tipoActividad
                ? <Badge variant="secondary" className="text-xs">{TIPO_ACTIVIDAD_LABELS[r.tipoActividad as keyof typeof TIPO_ACTIVIDAD_LABELS] ?? r.tipoActividad}</Badge>
                : <span className="text-xs text-muted-foreground">—</span>}
            </TableCell>
            <TableCell className="text-right tabular-nums font-semibold">{r.totalSalidas}</TableCell>
            <TableCell className="text-right tabular-nums">{r.totalParticipantes}</TableCell>
            <TableCell className="text-xs text-muted-foreground whitespace-nowrap">{fmtFecha(r.primeraFecha)}</TableCell>
            <TableCell className="text-xs text-muted-foreground whitespace-nowrap">{fmtFecha(r.ultimaFecha)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── BusquedaTab ──────────────────────────────────────────────────────────────

function BusquedaTab() {
  const [filters, setFilters] = useState<ParticipantesFilters>({})
  const [appliedFilters, setAppliedFilters] = useState<ParticipantesFilters | null>(null)
  const [selectedMountain, setSelectedMountain] = useState<number | undefined>()

  // ── Buscar por socio ──────────────────────────────────────────────────────
  const [socioQ, setSocioQ] = useState("")
  const [debouncedQ, setDebouncedQ] = useState("")
  const [selectedSocio, setSelectedSocio] = useState<SocioMinimal | null>(null)
  const [showSocioList, setShowSocioList] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (socioQ.length < 2) { setDebouncedQ(""); return }
    debounceRef.current = setTimeout(() => setDebouncedQ(socioQ), 350)
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [socioQ])

  const { data: socioResults, isLoading: buscarSocio } = useQuery({
    queryKey: ["socios", "buscar-historial", debouncedQ],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<SocioMinimal[]>>("/v1/socios/buscar", {
        params: { q: debouncedQ, size: 10 },
      })
      return data.data
    },
    enabled: debouncedQ.length >= 2,
  })

  // ─────────────────────────────────────────────────────────────────────────

  const { data: lookups }       = useLookups()
  const { data: mountains }     = useMountainsList()
  const { data: rutasData }     = useRutasList({ mountainId: selectedMountain })
  const { data: salidaLookups } = useSalidaLookups()

  const { data: results, isLoading: searching } = useParticipantesFiltrados(
    appliedFilters ?? {},
    appliedFilters !== null,
  )

  function applyFilters() { setAppliedFilters({ ...filters }) }

  function clearFilters() {
    setFilters({})
    setSelectedMountain(undefined)
    setAppliedFilters(null)
  }

  const esAlpinismo = filters.tipoActividad === "ALPINISMO"

  function setFilter<K extends keyof ParticipantesFilters>(key: K, value: ParticipantesFilters[K]) {
    setFilters((prev) => ({ ...prev, [key]: value }))
  }

  const rutas = rutasData?.content ?? []

  return (
    <div className="space-y-5">
      {/* ── Historial Individual ── */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        {/* Header accent strip */}
        <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4">
          <div className="flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <Users className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Historial Individual</h2>
              <p className="text-xs text-muted-foreground mt-0.5">
                Consulta el recorrido de un socio: salidas realizadas, rutas y cumbres alcanzadas.
              </p>
            </div>
          </div>
        </div>

        <div className="p-5">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
            <Input
              className="pl-9"
              placeholder="Nombre, apellido o cédula…"
              value={socioQ}
              onChange={(e) => {
                setSocioQ(e.target.value)
                setSelectedSocio(null)
                setShowSocioList(true)
              }}
              onFocus={() => setShowSocioList(true)}
            />
            {showSocioList && debouncedQ.length >= 2 && (
              <div className="absolute z-20 mt-1 w-full rounded-md border border-border bg-popover shadow-lg">
                {buscarSocio ? (
                  <p className="px-3 py-2 text-sm text-muted-foreground">Buscando…</p>
                ) : !socioResults || socioResults.length === 0 ? (
                  <p className="px-3 py-2 text-sm text-muted-foreground">Sin resultados.</p>
                ) : (
                  <ul>
                    {socioResults.map((s) => (
                      <li key={s.id}>
                        <button
                          className="w-full text-left px-3 py-2 text-sm hover:bg-muted transition-colors"
                          onMouseDown={(e) => e.preventDefault()}
                          onClick={() => {
                            setSelectedSocio(s)
                            setSocioQ(`${s.apellido}, ${s.nombre}`)
                            setShowSocioList(false)
                          }}
                        >
                          <span className="font-medium">{s.apellido}, {s.nombre}</span>
                          <span className="ml-2 text-xs text-muted-foreground">{s.cedula}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>

          {selectedSocio && (
            <div className="mt-5 rounded-lg border border-border/60 bg-muted/20 p-4">
              <div className="mb-3 flex items-center gap-2">
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary font-bold text-sm shrink-0">
                  {selectedSocio.nombre[0]}{selectedSocio.apellido[0]}
                </div>
                <div>
                  <p className="text-sm font-semibold text-foreground">
                    {selectedSocio.apellido}, {selectedSocio.nombre}
                  </p>
                  <p className="text-xs text-muted-foreground">{selectedSocio.cedula}</p>
                </div>
              </div>
              <SocioHistorialPanel socioId={selectedSocio.id} />
            </div>
          )}

          {!selectedSocio && (
            <p className="mt-3 text-xs text-muted-foreground">
              Escribe al menos 2 caracteres para ver sugerencias.
            </p>
          )}
        </div>
      </div>

      {/* ── Actividad por Período ── */}
      <PeriodoBusquedaPanel />

      {/* ── Explorar Participantes ── */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        {/* Header accent strip */}
        <div className="border-b border-border/60 bg-gradient-to-r from-indigo-500/5 to-transparent px-5 py-4">
          <div className="flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10">
              <Search className="h-4 w-4 text-indigo-500" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Explorar Participantes</h2>
              <p className="text-xs text-muted-foreground mt-0.5">
                Encuentra socios según su actividad: filtra por categoría, montaña, nivel técnico, dignidad o nombre.
              </p>
            </div>
          </div>
        </div>

        <div className="p-5 space-y-5">
          {/* Nombre del socio — campo destacado */}
          <div>
            <label className="mb-1.5 block text-xs font-semibold text-muted-foreground uppercase tracking-wide">
              Buscar por nombre
            </label>
            <div className="relative max-w-sm">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
              <Input
                className="pl-9"
                placeholder="Nombre, apellido o cédula..."
                value={filters.q ?? ""}
                onChange={(e) => setFilter("q", e.target.value || undefined)}
                onKeyDown={(e) => e.key === "Enter" && applyFilters()}
              />
            </div>
          </div>

          {/* Filtros secundarios */}
          <div>
            <label className="mb-1.5 block text-xs font-semibold text-muted-foreground uppercase tracking-wide">
              Filtros de actividad
            </label>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {/* Nivel técnico */}
              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Nivel técnico</label>
                <Select
                  value={filters.nivelTecnicoId?.toString() ?? "all"}
                  onValueChange={(v) => setFilter("nivelTecnicoId", v === "all" ? undefined : v)}
                >
                  <SelectTrigger><SelectValue placeholder="Todos" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">Todos</SelectItem>
                    {lookups?.clasificaciones.map((c) => (
                      <SelectItem key={c.id} value={c.id}>{c.nombre}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Categoría */}
              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Categoría</label>
                <Select
                  value={filters.tipoActividad ?? "all"}
                  onValueChange={(v) => {
                    const val = v === "all" ? undefined : v
                    setFilter("tipoActividad", val)
                    if (v !== "ALPINISMO") {
                      setSelectedMountain(undefined)
                      setFilter("mountainId", undefined)
                      setFilter("rutaId", undefined)
                    }
                  }}
                >
                  <SelectTrigger><SelectValue placeholder="Todas" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">Todas</SelectItem>
                    <SelectItem value="ALPINISMO">Alpinismo</SelectItem>
                    <SelectItem value="ESCALADA">Escalada</SelectItem>
                    <SelectItem value="TREKKING">Trekking</SelectItem>
                    <SelectItem value="CICLISMO">Ciclismo</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {/* Montaña — solo si categoría es Alpinismo */}
              {esAlpinismo && (
                <div className="space-y-1">
                  <label className="text-xs font-medium text-muted-foreground">Montaña</label>
                  <Select
                    value={filters.mountainId?.toString() ?? "all"}
                    onValueChange={(v) => {
                      const id = v === "all" ? undefined : Number(v)
                      setSelectedMountain(id)
                      setFilter("mountainId", id)
                      setFilter("rutaId", undefined)
                    }}
                  >
                    <SelectTrigger><SelectValue placeholder="Todas" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Todas</SelectItem>
                      {mountains?.content.map((m) => (
                        <SelectItem key={m.id} value={m.id.toString()}>{m.nombre}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              {/* Ruta — solo si categoría es Alpinismo */}
              {esAlpinismo && (
                <div className="space-y-1">
                  <label className="text-xs font-medium text-muted-foreground">Ruta</label>
                  <Select
                    value={filters.rutaId?.toString() ?? "all"}
                    onValueChange={(v) => setFilter("rutaId", v === "all" ? undefined : Number(v))}
                    disabled={!filters.mountainId}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={filters.mountainId ? "Todas" : "Selecciona montaña primero"} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Todas</SelectItem>
                      {rutas.map((r) => (
                        <SelectItem key={r.id} value={r.id.toString()}>{r.nombre}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              {/* Dignidad */}
              <div className="space-y-1">
                <label className="text-xs font-medium text-muted-foreground">Dignidad</label>
                <Select
                  value={filters.dignidadId?.toString() ?? "all"}
                  onValueChange={(v) => setFilter("dignidadId", v === "all" ? undefined : Number(v))}
                >
                  <SelectTrigger><SelectValue placeholder="Todas" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">Todas</SelectItem>
                    {salidaLookups?.dignidades.map((d) => (
                      <SelectItem key={d.id} value={d.id.toString()}>{d.nombre}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>

          {/* Acciones */}
          <div className="flex items-center gap-2 pt-1 border-t border-border/40">
            <Button onClick={applyFilters} disabled={searching} className="gap-2">
              <Search className="h-4 w-4" />
              {searching ? "Buscando..." : "Buscar"}
            </Button>
            <Button variant="outline" onClick={clearFilters} className="gap-2">
              Limpiar filtros
            </Button>
          </div>
        </div>
      </div>

      {/* Results */}
      {appliedFilters === null ? (
        <div className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <Search className="mx-auto mb-3 h-8 w-8 text-muted-foreground/40" />
          <p className="text-sm text-muted-foreground">
            Aplica filtros y presiona <strong>Buscar</strong> (o Enter) para ver los resultados.
          </p>
        </div>
      ) : searching ? (
        <Skeleton rows={1} />
      ) : !results || results.length === 0 ? (
        <div className="rounded-xl border border-border bg-card p-10 text-center">
          <p className="text-sm text-muted-foreground">No se encontraron socios con esos filtros.</p>
        </div>
      ) : (
        <div className="rounded-xl border border-border bg-card">
          <div className="flex items-center justify-between border-b border-border px-5 py-3">
            <span className="text-sm font-medium text-foreground">
              {results.length} {results.length === 1 ? "socio encontrado" : "socios encontrados"}
            </span>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Socio</TableHead>
                <TableHead>Nivel</TableHead>
                <TableHead className="text-right">Participaciones</TableHead>
                <TableHead className="text-right">Jefe de salida</TableHead>
                <TableHead>Dignidades</TableHead>
                <TableHead>Última salida</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {results.map((p) => (
                <TableRow key={p.socioId}>
                  <TableCell className="font-medium">{p.nombre} {p.apellido}</TableCell>
                  <TableCell>
                    {p.nivelTecnico
                      ? <Badge variant="secondary">{p.nivelTecnico}</Badge>
                      : <span className="text-xs text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{p.totalParticipaciones}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {p.vecesJefeSalida > 0
                      ? <span className="font-semibold text-yellow-600">{p.vecesJefeSalida}</span>
                      : <span className="text-muted-foreground">0</span>}
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {p.dignidades.map((d) => (
                        <Badge key={d} variant="outline" className="text-xs">{d}</Badge>
                      ))}
                      {p.dignidades.length === 0 && (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {p.ultimaParticipacion ?? "—"}
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

// ─── Shared components ────────────────────────────────────────────────────────

function KpiCard({
  icon, label, value, sub,
}: { icon: React.ReactNode; label: string; value: number; sub?: string }) {
  return (
    <div className="rounded-xl border border-border bg-card p-5 flex items-start gap-4 shadow-sm">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className="mt-1 text-3xl font-bold text-foreground tabular-nums">{value}</p>
        {sub && <p className="mt-0.5 text-xs text-muted-foreground">{sub} del total</p>}
      </div>
    </div>
  )
}

/** Lista numerada compacta para rankings */
function RankingList({
  items,
  label,
  medalColors = false,
}: {
  items: SocioRankingItem[]
  label: string
  medalColors?: boolean
}) {
  return (
    <ol className="space-y-2">
      {items.map((item, i) => (
        <li key={item.socioId} className="flex items-center gap-3">
          <span
            className={`w-5 shrink-0 text-right text-xs font-bold ${
              medalColors
                ? i === 0
                  ? "text-yellow-500"
                  : i === 1
                    ? "text-gray-400"
                    : i === 2
                      ? "text-amber-600"
                      : "text-muted-foreground"
                : "text-muted-foreground"
            }`}
          >
            {i + 1}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-foreground">
              {item.nombre} {item.apellido}
            </p>
            {item.nivelTecnico && (
              <p className="truncate text-xs text-muted-foreground">{item.nivelTecnico}</p>
            )}
          </div>
          <span className="shrink-0 text-sm font-semibold tabular-nums">
            {item.total}
            <span className="ml-1 text-xs font-normal text-muted-foreground">{label}</span>
          </span>
        </li>
      ))}
    </ol>
  )
}

/** Card compacta para rankings por dignidad */
function RankingCard({
  title, icon, items, label,
}: {
  title: string
  icon: React.ReactNode
  items: SocioRankingItem[]
  label: string
}) {
  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-foreground">
        {icon}
        {title}
      </h3>
      {items.length === 0 ? (
        <p className="py-4 text-center text-xs text-muted-foreground">Sin datos</p>
      ) : (
        <ol className="space-y-2">
          {items.map((item, i) => (
            <li key={item.socioId} className="flex items-center gap-2">
              <span className="w-4 shrink-0 text-right text-xs font-bold text-muted-foreground">
                {i + 1}
              </span>
              <p className="min-w-0 flex-1 truncate text-xs font-medium text-foreground">
                {item.nombre} {item.apellido}
              </p>
              <span className="shrink-0 text-xs font-semibold tabular-nums">
                {item.total}
                <span className="ml-1 font-normal text-muted-foreground">{label}</span>
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

function Skeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-4">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="h-40 animate-pulse rounded-xl bg-muted" />
      ))}
    </div>
  )
}

function Empty() {
  return (
    <div className="rounded-xl border border-border bg-card p-10 text-center">
      <p className="text-sm text-muted-foreground">No se pudieron cargar los datos</p>
    </div>
  )
}

// ─── Pestaña Reuniones ────────────────────────────────────────────────────────

const MESES_ES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"]

function ReunionesTab() {
  const [meses, setMeses] = useState(12)
  const [top,   setTop]   = useState(10)
  const { data, isLoading } = useRankingReuniones(top, meses)

  if (isLoading) return <LoadingCard />
  if (!data)     return <Empty />

  const porMesData = data.asistenciaPorMes.map((m: ReunionAsistenciaMesItem) => ({
    label:    `${MESES_ES[m.mes - 1]} ${m.anio}`,
    promedio: m.promedioAsistentes,
    total:    m.totalAsistencias,
    reuniones: m.totalReuniones,
  }))

  return (
    <div className="space-y-6">

      {/* KPIs */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <KpiCardFlex
          icon={<CalendarDays className="h-5 w-5 text-primary" />}
          label="Total reuniones"
          value={data.totalReuniones}
        />
        <KpiCardFlex
          icon={<Users className="h-5 w-5 text-primary" />}
          label="Promedio asistentes"
          value={data.promedioAsistentesGlobal.toFixed(1)}
        />
        <KpiCardFlex
          icon={<Star className="h-5 w-5 text-primary" />}
          label="Mayor asistente"
          value={data.topAsistentes[0]
            ? `${data.topAsistentes[0].nombre} (${data.topAsistentes[0].total})`
            : "—"}
          small
        />
      </div>

      {/* Filtros */}
      <div className="flex gap-3 flex-wrap">
        <div className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">Período:</span>
          <Select value={String(meses)} onValueChange={(v) => setMeses(Number(v))}>
            <SelectTrigger className="h-8 w-28">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="6">6 meses</SelectItem>
              <SelectItem value="12">12 meses</SelectItem>
              <SelectItem value="24">24 meses</SelectItem>
              <SelectItem value="36">36 meses</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">Top:</span>
          <Select value={String(top)} onValueChange={(v) => setTop(Number(v))}>
            <SelectTrigger className="h-8 w-20">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="5">5</SelectItem>
              <SelectItem value="10">10</SelectItem>
              <SelectItem value="20">20</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Gráfico asistencia por mes */}
      {porMesData.length > 0 && (
        <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
          <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4 flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <TrendingUp className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h3 className="text-base font-semibold text-foreground">Promedio de asistentes por reunión</h3>
              <p className="text-xs text-muted-foreground mt-0.5">Evolución mensual de la participación en asambleas.</p>
            </div>
          </div>
          <div className="p-5">
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={porMesData} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
              <XAxis dataKey="label" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip content={<ReunionesTooltip />} cursor={{ fill: "hsl(var(--muted))", opacity: 0.4 }} />
              <Bar dataKey="promedio" name="promedio" fill={COLORS[0]} radius={[3, 3, 0, 0]}>
                <LabelList dataKey="promedio" position="top" formatter={(v: unknown) => Number(v).toFixed(1)} style={{ fontSize: 10 }} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Rankings lado a lado */}
      <div className="grid gap-4 md:grid-cols-2">
        <RankingReunionesCard
          title="Más asistentes a reuniones"
          icon={<Crown className="h-4 w-4 text-yellow-500" />}
          items={data.topAsistentes}
          colorBar={COLORS[0]}
        />
        <RankingReunionesCard
          title="Menor asistencia"
          icon={<Award className="h-4 w-4 text-muted-foreground" />}
          items={data.menosAsistentes}
          colorBar={COLORS[4]}
        />
      </div>
    </div>
  )
}

function KpiCardFlex({
  icon, label, value, small = false,
}: {
  icon: React.ReactNode
  label: string
  value: string | number
  small?: boolean
}) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 flex flex-col gap-1">
      <div className="flex items-center gap-2 text-muted-foreground text-xs">{icon}{label}</div>
      <p className={`font-bold text-foreground ${small ? "text-base" : "text-2xl"}`}>{value}</p>
    </div>
  )
}

function RankingReunionesCard({
  title,
  icon,
  items,
  colorBar,
}: {
  title: string
  icon: React.ReactNode
  items: SocioRankingItem[]
  colorBar: string
}) {
  if (!items.length) return null
  const max = items[0].total || 1
  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <h3 className="text-sm font-semibold mb-3 flex items-center gap-2">{icon}{title}</h3>
      <div className="space-y-2">
        {items.map((s, i) => (
          <div key={s.socioId} className="flex items-center gap-2 text-sm">
            <span className="w-5 text-xs text-muted-foreground text-right shrink-0">{i + 1}.</span>
            <span className="w-32 truncate font-medium">{s.nombre} {s.apellido}</span>
            <div className="flex-1 h-2 rounded bg-muted overflow-hidden">
              <div
                className="h-full rounded transition-all"
                style={{ width: `${(s.total / max) * 100}%`, backgroundColor: colorBar }}
              />
            </div>
            <span className="w-6 text-xs text-muted-foreground shrink-0">{s.total}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function LoadingCard() {
  return (
    <div className="rounded-xl border border-border bg-card p-8">
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="h-4 animate-pulse rounded bg-muted" />
        ))}
      </div>
    </div>
  )
}

// ─── Ranking Montaña & Rutas tab ──────────────────────────────────────────────

const COLOR_MAS    = "#6366f1"
const COLOR_MENOS  = "#f59e0b"
const COLOR_ASIST  = "#10b981"

function RankingMontanaRutaTab() {
  const { data, isLoading } = useRankingMontanaRuta(10)

  const { data: montanasSinSalidas, isLoading: loadingMontanasSin } =
    useBuscarMontanaRuta("montana", "", true, true)
  const { data: rutasSinSalidas, isLoading: loadingRutasSin } =
    useBuscarMontanaRuta("ruta", "", true, true)

  const [busquedaTipo,       setBusquedaTipo]       = useState<"ambos" | "montana" | "ruta">("ambos")
  const [busquedaQ,          setBusquedaQ]          = useState("")
  const [busquedaSinSalidas, setBusquedaSinSalidas] = useState(false)
  const [busquedaAplicada,   setBusquedaAplicada]   = useState<{
    tipo: "ambos" | "montana" | "ruta"
    q: string
    sinSalidas: boolean
  } | null>(null)

  const { data: busquedaResult, isLoading: buscando } = useBuscarMontanaRuta(
    busquedaAplicada?.tipo ?? "ambos",
    busquedaAplicada?.q ?? "",
    busquedaAplicada?.sinSalidas ?? false,
    busquedaAplicada !== null,
  )

  function aplicarBusqueda() {
    setBusquedaAplicada({ tipo: busquedaTipo, q: busquedaQ, sinSalidas: busquedaSinSalidas })
  }

  function limpiarBusqueda() {
    setBusquedaQ("")
    setBusquedaTipo("ambos")
    setBusquedaSinSalidas(false)
    setBusquedaAplicada(null)
  }

  if (isLoading) return <Skeleton rows={4} />
  if (!data)     return <Empty />

  return (
    <div className="space-y-6">

      {/* ── Montañas — más / menos ── */}
      <div className="grid gap-6 lg:grid-cols-2">
        <MontanaBarCard
          title="Top 10 — Montañas con más salidas"
          icon={<Mountain className="h-4 w-4 text-primary" />}
          items={data.topMontanasMasSalidas}
          color={COLOR_MAS}
          label="salidas"
        />
        <MontanaBarCard
          title="Top 10 — Montañas con menos salidas"
          icon={<Mountain className="h-4 w-4 text-yellow-500" />}
          items={data.topMontanasMenosSalidas}
          color={COLOR_MENOS}
          label="salidas"
        />
      </div>

      {/* ── Rutas — más / menos ── */}
      <div className="grid gap-6 lg:grid-cols-2">
        <RutaBarCard
          title="Top 10 — Rutas con más salidas"
          icon={<Route className="h-4 w-4 text-primary" />}
          items={data.topRutasMasSalidas}
          color={COLOR_MAS}
          dataKey="totalSalidas"
          label="salidas"
        />
        <RutaBarCard
          title="Top 10 — Rutas con menos salidas"
          icon={<Route className="h-4 w-4 text-yellow-500" />}
          items={data.topRutasMenosSalidas}
          color={COLOR_MENOS}
          dataKey="totalSalidas"
          label="salidas"
        />
      </div>

      {/* ── Rutas con más gente asistiendo ── */}
      <div className="rounded-xl border border-border bg-card p-5">
        <h2 className="mb-5 flex items-center gap-2 text-base font-semibold text-foreground">
          <Users className="h-4 w-4 text-emerald-500" />
          Top 10 — Rutas con más gente asistiendo
        </h2>
        {data.topRutasMasParticipantes.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">Sin datos</p>
        ) : (
          <div className="grid gap-6 lg:grid-cols-2">
            <ResponsiveContainer width="100%" height={300}>
              <BarChart
                data={data.topRutasMasParticipantes.map((r) => ({
                  name: r.nombre.length > 22 ? r.nombre.slice(0, 22) + "…" : r.nombre,
                  fullName: r.nombre,
                  participantes: r.totalParticipantes,
                  salidas: r.totalSalidas,
                  mountain: r.mountainNombre,
                  tipo: r.tipoActividad,
                }))}
                layout="vertical"
                margin={{ top: 0, right: 50, bottom: 0, left: 120 }}
              >
                <XAxis type="number" tick={{ fontSize: 11 }} allowDecimals={false} />
                <YAxis type="category" dataKey="name" tick={{ fontSize: 10 }} width={120} />
                <Tooltip content={<RutaPartTooltip />} cursor={{ fill: "hsl(var(--muted))", opacity: 0.4 }} />
                <Bar dataKey="participantes" fill={COLOR_ASIST} radius={[0, 4, 4, 0]}>
                  <LabelList dataKey="participantes" position="right" style={{ fontSize: 11 }} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>

            <ol className="space-y-2">
              {data.topRutasMasParticipantes.map((r, i) => (
                <li key={r.rutaId} className="flex items-start gap-3">
                  <span className={`mt-0.5 w-5 shrink-0 text-right text-xs font-bold ${
                    i === 0 ? "text-yellow-500" : i === 1 ? "text-gray-400" : i === 2 ? "text-amber-600" : "text-muted-foreground"
                  }`}>{i + 1}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-foreground">{r.nombre}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      {r.mountainNombre ?? r.tipoActividad}
                    </p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="text-sm font-semibold tabular-nums">
                      {r.totalParticipantes}
                      <span className="ml-1 text-xs font-normal text-muted-foreground">pers.</span>
                    </p>
                    <p className="text-xs text-muted-foreground">{r.totalSalidas} salidas</p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        )}
      </div>

      {/* ── Sin salidas ── */}
      <div className="grid gap-6 lg:grid-cols-2">
        <SinSalidasCard
          title="Montañas sin ninguna salida"
          icon={<Mountain className="h-4 w-4 text-amber-500" />}
          items={montanasSinSalidas ?? []}
          isLoading={loadingMontanasSin}
          emptyMessage="¡Todas las montañas registradas tienen al menos una salida!"
        />
        <SinSalidasCard
          title="Rutas sin ninguna salida"
          icon={<Route className="h-4 w-4 text-amber-500" />}
          items={rutasSinSalidas ?? []}
          isLoading={loadingRutasSin}
          emptyMessage="¡Todas las rutas registradas tienen al menos una salida!"
        />
      </div>

      {/* ── Búsqueda ── */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-indigo-500/5 to-transparent px-5 py-4 flex items-start gap-3">
          <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-500/10">
            <Search className="h-4 w-4 text-indigo-500" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-foreground">Buscar montañas o rutas</h2>
            <p className="text-xs text-muted-foreground mt-0.5">Encuentra cualquier destino por nombre, tipo o disponibilidad de salidas.</p>
          </div>
        </div>
        <div className="p-5">

        <div className="grid gap-3 sm:grid-cols-3 mb-4">
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Tipo</label>
            <Select value={busquedaTipo} onValueChange={(v) => setBusquedaTipo(v as typeof busquedaTipo)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ambos">Ambos</SelectItem>
                <SelectItem value="montana">Montañas</SelectItem>
                <SelectItem value="ruta">Rutas</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1 sm:col-span-2">
            <label className="text-xs font-medium text-muted-foreground">Nombre</label>
            <Input
              placeholder="Buscar por nombre..."
              value={busquedaQ}
              onChange={(e) => setBusquedaQ(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && aplicarBusqueda()}
            />
          </div>
        </div>

        <div className="flex items-center gap-4 mb-4">
          <label className="flex items-center gap-2 text-sm cursor-pointer select-none">
            <input
              type="checkbox"
              checked={busquedaSinSalidas}
              onChange={(e) => setBusquedaSinSalidas(e.target.checked)}
              className="h-4 w-4 rounded border-input accent-primary"
            />
            Mostrar solo los que <strong className="ml-1">no tienen salidas</strong>
          </label>
        </div>

        <div className="flex gap-2">
          <Button onClick={aplicarBusqueda} disabled={buscando}>
            <Search className="mr-2 h-4 w-4" />
            {buscando ? "Buscando..." : "Buscar"}
          </Button>
          <Button variant="outline" onClick={limpiarBusqueda}>Limpiar</Button>
        </div>

        {/* Resultados */}
        {busquedaAplicada === null ? (
          <div className="mt-5 rounded-lg border border-dashed border-border p-8 text-center">
            <MapPin className="mx-auto mb-3 h-7 w-7 text-muted-foreground/40" />
            <p className="text-sm text-muted-foreground">
              Configura los filtros y presiona <strong>Buscar</strong>.
            </p>
          </div>
        ) : buscando ? (
          <div className="mt-5 space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-10 animate-pulse rounded-lg bg-muted" />
            ))}
          </div>
        ) : !busquedaResult || busquedaResult.length === 0 ? (
          <div className="mt-5 rounded-lg border border-border p-8 text-center">
            <p className="text-sm text-muted-foreground">No se encontraron resultados.</p>
          </div>
        ) : (
          <div className="mt-5 rounded-xl border border-border">
            <div className="flex items-center justify-between border-b border-border px-5 py-3">
              <span className="text-sm font-medium text-foreground">
                {busquedaResult.length} {busquedaResult.length === 1 ? "resultado" : "resultados"}
              </span>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nombre</TableHead>
                  <TableHead>Tipo</TableHead>
                  <TableHead>Categoría / Montaña</TableHead>
                  <TableHead className="text-right">Salidas</TableHead>
                  <TableHead className="text-right">Participantes únicos</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {busquedaResult.map((item) => (
                  <TableRow key={`${item.tipo}-${item.id}`}>
                    <TableCell className="font-medium">{item.nombre}</TableCell>
                    <TableCell>
                      <Badge variant={item.tipo === "MONTANA" ? "default" : "secondary"}>
                        {item.tipo === "MONTANA" ? "Montaña" : "Ruta"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {item.tipo === "MONTANA"
                        ? "—"
                        : item.mountainNombre
                          ? `${TIPO_ACTIVIDAD_LABELS[item.tipoActividad as keyof typeof TIPO_ACTIVIDAD_LABELS] ?? item.tipoActividad} · ${item.mountainNombre}`
                          : (TIPO_ACTIVIDAD_LABELS[item.tipoActividad as keyof typeof TIPO_ACTIVIDAD_LABELS] ?? item.tipoActividad)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {item.totalSalidas === 0
                        ? <span className="text-muted-foreground">0</span>
                        : <span className="font-semibold">{item.totalSalidas}</span>}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {item.totalParticipantes === 0
                        ? <span className="text-muted-foreground">0</span>
                        : item.totalParticipantes}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        </div>
      </div>

    </div>
  )
}

function SinSalidasCard({
  title, icon, items, isLoading, emptyMessage,
}: {
  title: string
  icon: React.ReactNode
  items: MontanaRutaBusquedaItem[]
  isLoading: boolean
  emptyMessage: string
}) {
  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-base font-semibold text-foreground">
          {icon}{title}
        </h2>
        {!isLoading && items.length > 0 && (
          <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">
            {items.length}
          </span>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-muted" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-8 text-center">
          <CheckCircle2 className="h-8 w-8 text-green-500" />
          <p className="text-sm text-muted-foreground">{emptyMessage}</p>
        </div>
      ) : (
        <div className="max-h-72 overflow-y-auto">
          <ol className="divide-y divide-border">
            {items.map((item, i) => (
              <li key={item.id} className="flex items-center gap-3 py-2">
                <span className="w-5 shrink-0 text-right text-xs font-bold text-muted-foreground">
                  {i + 1}
                </span>
                <AlertCircle className="h-3.5 w-3.5 shrink-0 text-amber-500" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-foreground">{item.nombre}</p>
                  {item.mountainNombre && (
                    <p className="truncate text-xs text-muted-foreground">{item.mountainNombre}</p>
                  )}
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}
    </div>
  )
}

function MontanaBarCard({
  title, icon, items, color, label,
}: {
  title: string
  icon: React.ReactNode
  items: MontanaRankingItem[]
  color: string
  label: string
}) {
  const chartData = items.map((m) => ({
    name: m.nombre.length > 18 ? m.nombre.slice(0, 18) + "…" : m.nombre,
    fullName: m.nombre,
    total: m.totalSalidas,
    region: m.region,
    altitud: m.altitud,
  }))

  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-foreground">
        {icon}{title}
      </h2>
      {chartData.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">Sin datos</p>
      ) : (
        <>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart
              data={chartData}
              layout="vertical"
              margin={{ top: 0, right: 40, bottom: 0, left: 90 }}
            >
              <XAxis type="number" tick={{ fontSize: 11 }} allowDecimals={false} />
              <YAxis type="category" dataKey="name" tick={{ fontSize: 10 }} width={90} />
              <Tooltip content={(p) => <MontanaTooltip active={p.active} payload={p.payload as unknown as readonly TPItem[]} suffix={label} />} cursor={{ fill: "hsl(var(--muted))", opacity: 0.4 }} />
              <Bar dataKey="total" fill={color} radius={[0, 4, 4, 0]}>
                <LabelList dataKey="total" position="right" style={{ fontSize: 11 }} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
          <div className="mt-3 divide-y divide-border text-xs">
            {items.map((m, i) => (
              <div key={m.mountainId} className="flex items-center justify-between py-1.5">
                <div className="flex items-center gap-2">
                  <span className="w-4 text-right font-bold text-muted-foreground">{i + 1}</span>
                  <span className="font-medium text-foreground">{m.nombre}</span>
                  <span className="text-muted-foreground">{m.altitud}m · {m.region}</span>
                </div>
                <span className="font-semibold tabular-nums">{m.totalSalidas}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function RutaBarCard({
  title, icon, items, color, dataKey, label,
}: {
  title: string
  icon: React.ReactNode
  items: RutaRankingItem[]
  color: string
  dataKey: "totalSalidas" | "totalParticipantes"
  label: string
}) {
  const chartData = items.map((r) => ({
    name: r.nombre.length > 20 ? r.nombre.slice(0, 20) + "…" : r.nombre,
    fullName: r.nombre,
    total: r[dataKey],
    mountain: r.mountainNombre,
    tipo: r.tipoActividad,
  }))

  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-foreground">
        {icon}{title}
      </h2>
      {chartData.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">Sin datos</p>
      ) : (
        <>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart
              data={chartData}
              layout="vertical"
              margin={{ top: 0, right: 40, bottom: 0, left: 100 }}
            >
              <XAxis type="number" tick={{ fontSize: 11 }} allowDecimals={false} />
              <YAxis type="category" dataKey="name" tick={{ fontSize: 10 }} width={100} />
              <Tooltip content={(p) => <RutaTooltip active={p.active} payload={p.payload as unknown as readonly TPItem[]} suffix={label} />} cursor={{ fill: "hsl(var(--muted))", opacity: 0.4 }} />
              <Bar dataKey="total" fill={color} radius={[0, 4, 4, 0]}>
                <LabelList dataKey="total" position="right" style={{ fontSize: 11 }} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
          <div className="mt-3 divide-y divide-border text-xs">
            {items.map((r, i) => (
              <div key={r.rutaId} className="flex items-center justify-between py-1.5">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="w-4 shrink-0 text-right font-bold text-muted-foreground">{i + 1}</span>
                  <span className="truncate font-medium text-foreground">{r.nombre}</span>
                  {r.mountainNombre && (
                    <span className="shrink-0 text-muted-foreground">{r.mountainNombre}</span>
                  )}
                </div>
                <span className="shrink-0 font-semibold tabular-nums">{r[dataKey]}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
