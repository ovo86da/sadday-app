import { useState } from "react"
import { Link } from "react-router"
import { useQuery } from "@tanstack/react-query"
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend,
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from "recharts"
import { CalendarDays, Users, Mountain, Cake, TrendingUp, Crown, ClipboardCheck } from "lucide-react"
import { useAuthStore } from "@/stores/auth-store"
import api from "@/lib/api"
import { useDashboardEstadisticas, useHistorialSocio } from "@/hooks/use-estadisticas"
import { useAprobacionesPendientes, useAlertasSinJefe } from "@/hooks/use-salidas"
import { SalidaDetailDialog } from "@/pages/salidas/salida-detail-dialog"

// ─── Types ───────────────────────────────────────────────────────────────────

interface PageResponse<T> {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

interface SocioSummary {
  id: string
  tipoSocio: string
}

interface SalidaSummary {
  id: string
  nombre: string
  fechaInicio: string
  rutaNombre: string
  totalInscritos: number
  capacidadMaxima: number | null
}

interface CumpleanosItem {
  socioId: string
  nombre: string
  apellido: string
  edad: number
}

interface CumpleanosResponse {
  total: number
  cumpleanos: CumpleanosItem[]
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

const ROLES_ADMIN = ["ADMIN", "SECRETARIA", "DIRECTIVO"]
const PIE_COLORS  = ["#6366f1", "#10b981", "#f59e0b", "#ec4899", "#8b5cf6"]

function isRole(rol: string, roles: string[]) {
  return roles.includes(rol.toUpperCase())
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("es-EC", {
    day: "numeric",
    month: "short",
    year: "numeric",
  })
}

// ─── Small components ────────────────────────────────────────────────────────

function KpiCard({
  label, value, icon: Icon, loading, clickable,
  theme = "primary"
}: {
  label: string; value: string | number; icon: React.ElementType; loading?: boolean; clickable?: boolean;
  theme?: "primary" | "emerald" | "amber" | "indigo"
}) {
  const themeStyles = {
    primary: "text-primary bg-primary/10 group-hover:bg-primary/20",
    emerald: "text-emerald-500 bg-emerald-500/10 group-hover:bg-emerald-500/20",
    amber: "text-amber-500 bg-amber-500/10 group-hover:bg-amber-500/20",
    indigo: "text-indigo-500 bg-indigo-500/10 group-hover:bg-indigo-500/20"
  }
  const glowStyles = {
    primary: "bg-primary/5 group-hover:bg-primary/15",
    emerald: "bg-emerald-500/5 group-hover:bg-emerald-500/15",
    amber: "bg-amber-500/5 group-hover:bg-amber-500/15",
    indigo: "bg-indigo-500/5 group-hover:bg-indigo-500/15"
  }
  const borderHover = {
    primary: "hover:border-primary/30",
    emerald: "hover:border-emerald-500/30",
    amber: "hover:border-amber-500/30",
    indigo: "hover:border-indigo-500/30"
  }

  return (
    <div className={`group relative overflow-hidden rounded-2xl border border-border/50 bg-gradient-to-br from-card to-muted/10 p-5 flex items-center gap-4 transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-foreground/5 ${clickable ? "cursor-pointer " + borderHover[theme] : ""}`}>
      <div className={`absolute -right-6 -top-6 h-32 w-32 rounded-full blur-3xl transition-colors duration-500 ${glowStyles[theme]}`} />
      
      <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl transition-all duration-300 group-hover:scale-110 group-hover:rotate-3 ${themeStyles[theme]}`}>
        <Icon className="h-6 w-6" />
      </div>
      <div className="z-10 relative">
        <p className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">{label}</p>
        {loading ? (
          <div className="mt-1 h-8 w-20 animate-pulse rounded bg-muted" />
        ) : (
          <p className="text-3xl font-extrabold text-foreground mt-0.5 tracking-tight">{value}</p>
        )}
      </div>
    </div>
  )
}

function SkeletonCard({ rows = 3 }: { rows?: number }) {
  return (
    <div className="flex flex-col h-full rounded-2xl border border-border/50 bg-card/40 p-5 space-y-4">
      <div className="h-5 w-40 animate-pulse rounded-md bg-muted" />
      <div className="space-y-3 flex-1">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="h-12 animate-pulse rounded-lg bg-muted/60" />
        ))}
      </div>
    </div>
  )
}

// ─── Widgets ─────────────────────────────────────────────────────────────────

function JefeSalidaWidget() {
  const user = useAuthStore((s) => s.user)
  const esPrivilegiado = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(user?.rol?.toUpperCase() ?? "")
  const { data, isLoading } = useHistorialSocio(user?.socioId)
  const { data: aprobaciones, isLoading: loadingAprobaciones } = useAprobacionesPendientes()
  const { data: alertasSinJefe } = useAlertasSinJefe(esPrivilegiado)
  const [detailId, setDetailId] = useState<string | null>(null)

  const today = new Date().toISOString().slice(0, 10)
  const proximas = (data?.historial ?? [])
    .filter((h) => h.esJefeSalida && h.estadoInscripcion !== "CANCELADO" && h.fecha >= today)
    .sort((a, b) => a.fecha.localeCompare(b.fecha))

  const pendingCount = aprobaciones?.length ?? 0
  const sinJefeCount = alertasSinJefe?.length ?? 0

  if (!isLoading && !loadingAprobaciones && proximas.length === 0 && pendingCount === 0 && sinJefeCount === 0) return null

  return (
    <>
      <div className="rounded-2xl border border-yellow-400/50 bg-gradient-to-r from-yellow-500/10 to-amber-500/5 p-5 space-y-4 shadow-sm relative overflow-hidden">
        <div className="absolute top-0 left-0 w-1.5 h-full bg-gradient-to-b from-yellow-400 to-amber-500" />
        <h2 className="text-base font-bold text-foreground flex items-center gap-2 pl-2">
          <Crown className="h-5 w-5 text-yellow-500 drop-shadow-sm" />
          Jefe de Salida
          {proximas.length > 0 && (
            <span className="ml-auto inline-flex h-6 min-w-[24px] items-center justify-center rounded-full bg-yellow-500 px-2 text-xs font-bold text-white shadow-sm">
              {proximas.length}
            </span>
          )}
        </h2>

        {pendingCount > 0 && (
          <Link
            to="/notificaciones"
            className="flex items-center gap-2 rounded-xl border border-amber-400/50 bg-amber-500/10 px-4 py-3 text-sm font-semibold text-amber-700 transition-all hover:bg-amber-500/20 hover:shadow-sm dark:text-amber-400 ml-2"
          >
            <ClipboardCheck className="h-5 w-5 shrink-0" />
            Tienes {pendingCount} aprobación{pendingCount !== 1 ? "es" : ""} pendiente{pendingCount !== 1 ? "s" : ""} de revisar
          </Link>
        )}

        {sinJefeCount > 0 && (
          <Link
            to="/notificaciones"
            className="flex items-center gap-2 rounded-xl border border-red-400/50 bg-red-500/10 px-4 py-3 text-sm font-semibold text-red-700 transition-all hover:bg-red-500/20 hover:shadow-sm dark:text-red-400 ml-2"
          >
            <ClipboardCheck className="h-5 w-5 shrink-0" />
            {sinJefeCount} salida{sinJefeCount !== 1 ? "s" : ""} sin Jefe de Salida asignado
          </Link>
        )}
        {isLoading ? (
          <div className="space-y-3 pl-2">
            {[...Array(2)].map((_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-xl bg-muted/60" />
            ))}
          </div>
        ) : (
          <ul className="divide-y divide-yellow-200/30 pl-2">
            {proximas.map((h) => (
              <li
                key={h.salidaId}
                onClick={() => setDetailId(h.salidaId)}
                className="flex cursor-pointer items-center justify-between gap-3 py-3 hover:opacity-75 transition-opacity"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-foreground">{h.salidaNombre}</p>
                  <p className="text-xs font-medium text-muted-foreground">{h.mountainNombre}</p>
                </div>
                <div className="shrink-0 text-right">
                  <p className="text-sm font-bold text-foreground">{formatDate(h.fecha)}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {detailId && (
        <SalidaDetailDialog
          open={!!detailId}
          onClose={() => setDetailId(null)}
          salidaId={detailId}
        />
      )}
    </>
  )
}

function ProximasSalidasWidget() {
  const [detailId, setDetailId] = useState<string | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ["dashboard", "salidas-proximas"],
    queryFn: () =>
      api
        .get<{ data: PageResponse<SalidaSummary> }>(
          "/v1/salidas?estado=PLANIFICADA&size=5&sort=fechaInicio,asc"
        )
        .then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
  })

  if (isLoading) return <SkeletonCard rows={6} />
  const salidas = data?.content ?? []

  return (
    <>
      <div className="flex flex-col h-full rounded-2xl border border-border/50 bg-card/40 backdrop-blur-sm shadow-sm p-6 space-y-5">
        <h2 className="text-base font-bold text-foreground flex items-center gap-3">
          <div className="p-2 rounded-xl bg-amber-500/10">
            <CalendarDays className="h-5 w-5 text-amber-500" />
          </div>
          Próximas salidas
        </h2>
        <div className="flex-1 overflow-y-auto pr-2 -mr-2">
          {salidas.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center space-y-3 py-8 text-center">
              <div className="rounded-full bg-muted p-3">
                <CalendarDays className="h-6 w-6 text-muted-foreground/50" />
              </div>
              <p className="text-sm font-medium text-muted-foreground">
                No hay salidas planificadas
              </p>
            </div>
          ) : (
            <ul className="space-y-3">
              {salidas.map((s) => (
                <li
                  key={s.id}
                  onClick={() => setDetailId(s.id)}
                  className="group cursor-pointer rounded-xl border border-border/50 bg-card hover:bg-accent/30 hover:border-accent p-4 transition-all flex items-center justify-between gap-3"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-bold text-foreground group-hover:text-primary transition-colors">{s.nombre}</p>
                    <p className="text-xs font-medium text-muted-foreground mt-0.5">{s.rutaNombre ?? "—"}</p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="text-sm font-bold text-foreground">{formatDate(s.fechaInicio)}</p>
                    <p className="text-[11px] font-semibold text-muted-foreground mt-0.5 bg-muted/50 inline-block px-1.5 py-0.5 rounded">
                      {s.totalInscritos}
                      {s.capacidadMaxima ? `/${s.capacidadMaxima}` : ""} inscritos
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {detailId && (
        <SalidaDetailDialog
          open={!!detailId}
          onClose={() => setDetailId(null)}
          salidaId={detailId}
        />
      )}
    </>
  )
}

function CumpleanosWidget() {
  const { data, isLoading } = useQuery({
    queryKey: ["dashboard", "cumpleanos"],
    queryFn: () =>
      api
        .get<{ data: CumpleanosResponse }>("/v1/notificaciones/cumpleanos")
        .then((r) => r.data.data),
    staleTime: 60 * 60 * 1000,
  })

  if (isLoading) return <SkeletonCard rows={3} />
  const items = data?.cumpleanos ?? []

  return (
    <div className="flex flex-col h-full rounded-2xl border border-border/50 bg-card/40 backdrop-blur-sm shadow-sm p-6 space-y-5">
      <h2 className="text-base font-bold text-foreground flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-pink-500/10">
            <Cake className="h-5 w-5 text-pink-500" />
          </div>
          Cumpleaños hoy
        </div>
        {items.length > 0 && (
          <span className="inline-flex h-6 min-w-[24px] items-center justify-center rounded-full bg-pink-500 px-2 text-xs font-bold text-white shadow-sm">
            {items.length}
          </span>
        )}
      </h2>
      <div className="flex-1">
        {items.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center space-y-3 py-6 text-center">
            <div className="rounded-full bg-muted p-3">
              <Cake className="h-6 w-6 text-muted-foreground/50" />
            </div>
            <p className="text-sm font-medium text-muted-foreground">
              Ningún socio cumple años hoy
            </p>
          </div>
        ) : (
          <ul className="space-y-3">
            {items.map((c) => (
              <li
                key={c.socioId}
                className="flex items-center justify-between rounded-xl bg-pink-500/5 border border-pink-500/10 px-4 py-3"
              >
                <span className="text-sm font-bold text-foreground">
                  {c.nombre} {c.apellido}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function SociosPorTipoChart() {
  const { data, isLoading } = useQuery({
    queryKey: ["dashboard", "socios-todos"],
    queryFn: () =>
      api
        .get<{ data: PageResponse<SocioSummary> }>("/v1/socios?size=500")
        .then((r) => r.data.data),
    staleTime: 10 * 60 * 1000,
  })

  if (isLoading) return <SkeletonCard rows={4} />

  const conteo: Record<string, number> = {}
  for (const s of data?.content ?? []) {
    conteo[s.tipoSocio] = (conteo[s.tipoSocio] ?? 0) + 1
  }
  const chartData = Object.entries(conteo).map(([name, value]) => ({ name, value }))

  return (
    <div className="flex flex-col h-full rounded-2xl border border-border/50 bg-card/40 backdrop-blur-sm shadow-sm p-6 space-y-2">
      <h2 className="text-base font-bold text-foreground flex items-center gap-3">
        <div className="p-2 rounded-xl bg-indigo-500/10">
          <Users className="h-5 w-5 text-indigo-500" />
        </div>
        Distribución de Socios
      </h2>
      <div className="flex-1 flex flex-col justify-center min-h-[220px]">
        {chartData.length === 0 ? (
          <div className="flex flex-col items-center justify-center space-y-3 py-6 text-center">
            <div className="rounded-full bg-muted p-3">
              <Users className="h-6 w-6 text-muted-foreground/50" />
            </div>
            <p className="text-sm font-medium text-muted-foreground">Sin datos registrados</p>
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={240}>
            <PieChart>
              <Pie
                data={chartData}
                cx="50%"
                cy="45%"
                innerRadius={60}
                outerRadius={95}
                paddingAngle={4}
                dataKey="value"
                stroke="none"
              >
                {chartData.map((_, i) => (
                  <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                ))}
              </Pie>
              <Tooltip content={<SalidasTooltip />} />
              <Legend
                iconType="circle"
                iconSize={8}
                wrapperStyle={{ fontSize: "12px", color: "hsl(var(--muted-foreground))", fontWeight: 500, marginTop: "10px" }}
              />
            </PieChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}


const MESES_NOMBRE = ["Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"]

interface TooltipPayloadItem { name: string; value: number; color: string }
interface SalidasTooltipProps { active?: boolean; payload?: TooltipPayloadItem[]; label?: string }

function SalidasTooltip({ active, payload, label }: SalidasTooltipProps) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-lg border border-border bg-popover px-3 py-2 shadow-md text-sm min-w-[130px]">
      <p className="mb-1.5 font-semibold text-foreground">{label}</p>
      {payload.map((item) => (
        <div key={item.name} className="flex items-center justify-between gap-4">
          <span className="flex items-center gap-1.5 text-muted-foreground">
            <span className="inline-block h-2 w-2 rounded-sm" style={{ background: item.color }} />
            {item.name}
          </span>
          <span className="font-medium text-foreground tabular-nums">{item.value}</span>
        </div>
      ))}
    </div>
  )
}

function SalidasPorMesChart() {
  const [meses, setMeses] = useState(12)
  const { data, isLoading } = useDashboardEstadisticas(meses)

  const chartData = (data?.salidasPorMes ?? []).map((item) => ({
    label: `${MESES_NOMBRE[item.mes - 1]} ${String(item.anio).slice(2)}`,
    Realizadas:   item.realizadas   ?? 0,
    Canceladas:   item.canceladas   ?? 0,
    "En Curso":   item.enCurso      ?? 0,
  }))

  return (
    <div className="flex flex-col h-full rounded-2xl border border-border/50 bg-card/40 backdrop-blur-sm shadow-sm p-6 space-y-6">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <h2 className="text-base font-bold text-foreground flex items-center gap-3">
          <div className="p-2 rounded-xl bg-primary/10">
            <TrendingUp className="h-5 w-5 text-primary" />
          </div>
          Salidas totales del club
        </h2>
        <div className="flex gap-1.5 p-1 bg-muted/50 rounded-lg">
          {([6, 12, 24] as const).map((n) => (
            <button
              key={n}
              onClick={() => setMeses(n)}
              className={`rounded-md px-3 py-1.5 text-xs font-bold transition-all shadow-sm ${
                meses === n
                  ? "bg-background text-foreground"
                  : "text-muted-foreground hover:bg-background/50 hover:text-foreground"
              }`}
            >
              {n}m
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="flex-1 min-h-[260px] animate-pulse rounded-xl bg-muted/50" />
      ) : chartData.length === 0 ? (
        <div className="flex-1 flex flex-col items-center justify-center space-y-3 py-10 text-center">
          <div className="rounded-full bg-muted p-4">
            <TrendingUp className="h-8 w-8 text-muted-foreground/50" />
          </div>
          <p className="text-sm font-medium text-muted-foreground">
            No hay salidas registradas en este período
          </p>
        </div>
      ) : (
        <div className="flex-1 min-h-[260px]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} barGap={4} barCategoryGap="25%">
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} opacity={0.5} />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 12, fill: "hsl(var(--muted-foreground))", fontWeight: 500 }}
                axisLine={false}
                tickLine={false}
                dy={10}
              />
              <YAxis
                allowDecimals={false}
                tick={{ fontSize: 12, fill: "hsl(var(--muted-foreground))", fontWeight: 500 }}
                axisLine={false}
                tickLine={false}
                width={30}
                dx={-10}
              />
              <Tooltip
                content={<SalidasTooltip />}
                cursor={{ fill: "hsl(var(--muted))", opacity: 0.3 }}
              />
              <Legend
                iconType="circle"
                iconSize={8}
                wrapperStyle={{ fontSize: "12px", color: "hsl(var(--foreground))", fontWeight: 600, paddingTop: "20px" }}
              />
              <Bar dataKey="Realizadas"   fill="#10b981" radius={[4,4,0,0]} />
              <Bar dataKey="Canceladas"   fill="#f43f5e" radius={[4,4,0,0]} />
              <Bar dataKey="En Curso"     fill="#3b82f6" radius={[4,4,0,0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {data && (
        <div className="grid grid-cols-4 gap-4 border-t border-border/50 pt-6 mt-auto">
          <div className="text-center bg-card/50 rounded-xl p-3 border border-border/30">
            <p className="text-2xl font-extrabold text-foreground">{data.totalSalidas}</p>
            <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground mt-1">Total</p>
          </div>
          <div className="text-center bg-emerald-500/5 rounded-xl p-3 border border-emerald-500/10">
            <p className="text-2xl font-extrabold text-emerald-500">{data.totalRealizadas}</p>
            <p className="text-xs font-bold uppercase tracking-wider text-emerald-600/80 dark:text-emerald-400/80 mt-1">Realizadas</p>
          </div>
          <div className="text-center bg-blue-500/5 rounded-xl p-3 border border-blue-500/10">
            <p className="text-2xl font-extrabold text-blue-500">{data.totalEnCurso}</p>
            <p className="text-xs font-bold uppercase tracking-wider text-blue-600/80 dark:text-blue-400/80 mt-1">En Curso</p>
          </div>
          <div className="text-center bg-rose-500/5 rounded-xl p-3 border border-rose-500/10">
            <p className="text-2xl font-extrabold text-rose-500">{data.totalCanceladas}</p>
            <p className="text-xs font-bold uppercase tracking-wider text-rose-600/80 dark:text-rose-400/80 mt-1">Canceladas</p>
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export function DashboardPage() {
  const user = useAuthStore((s) => s.user)
  const rol = user?.rol ?? ""
  const esAdmin = isRole(rol, ROLES_ADMIN)

  const { data: sociosPage, isLoading: loadingSocios } = useQuery({
    queryKey: ["dashboard", "socios-count"],
    queryFn: () =>
      api
        .get<{ data: PageResponse<SocioSummary> }>("/v1/socios?size=1")
        .then((r) => r.data.data),
    enabled: esAdmin,
    staleTime: 5 * 60 * 1000,
  })

  const { data: planificadasPage, isLoading: loadingPlanificadas } = useQuery({
    queryKey: ["dashboard", "salidas-planificadas-count"],
    queryFn: () =>
      api
        .get<{ data: PageResponse<SalidaSummary> }>("/v1/salidas?estado=PLANIFICADA&size=1")
        .then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
  })

  const { data: completadasPage, isLoading: loadingCompletadas } = useQuery({
    queryKey: ["dashboard", "salidas-completadas-count"],
    queryFn: () =>
      api
        .get<{ data: PageResponse<SalidaSummary> }>("/v1/salidas?estado=REALIZADA&size=1")
        .then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
  })

  return (
    <div className="space-y-8 pb-10">
      {/* Header */}
      <div className="flex flex-col gap-1 md:flex-row md:items-end justify-between">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-foreground">Dashboard</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Bienvenido de nuevo, <span className="font-semibold text-foreground">{user?.nombre}</span>
          </p>
        </div>
      </div>

      {/* Banner de Acción Prioritaria */}
      <JefeSalidaWidget />

      {/* KPI Row */}
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-5">
        {esAdmin && (
          <KpiCard
            label="Total socios"
            value={sociosPage?.page.totalElements ?? 0}
            icon={Users}
            loading={loadingSocios}
            theme="indigo"
          />
        )}
        <Link to="/salidas" className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-2xl">
          <KpiCard
            label="Salidas planificadas"
            value={planificadasPage?.page.totalElements ?? 0}
            icon={CalendarDays}
            loading={loadingPlanificadas}
            clickable
            theme="amber"
          />
        </Link>
        <KpiCard
          label="Salidas completadas"
          value={completadasPage?.page.totalElements ?? 0}
          icon={Mountain}
          loading={loadingCompletadas}
          theme="emerald"
        />
      </div>

      {/* Bento Grid Principal */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 auto-rows-min">
        
        {/* Columna Izquierda: Próximas Salidas (alta, ocupa 2 filas) */}
        <div className="lg:col-span-1 lg:row-span-2">
          <ProximasSalidasWidget />
        </div>

        {/* Columna Derecha Superior: Gráfica de barras (ancha, ocupa 2 columnas) */}
        <div className="lg:col-span-2">
          <SalidasPorMesChart />
        </div>

        {/* Columna Derecha Inferior: Cumpleaños + Pie Chart (se dividen el espacio ancho) */}
        <div className="lg:col-span-2 grid grid-cols-1 md:grid-cols-2 gap-6">
          <CumpleanosWidget />
          {esAdmin && <SociosPorTipoChart />}
        </div>

      </div>
    </div>
  )
}
