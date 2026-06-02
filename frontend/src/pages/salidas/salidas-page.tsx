import { useState } from "react"
import { useSalidasList, useSalidaLookups, useDeleteSalida, useCancelarSalida } from "@/hooks/use-salidas"
import { useAuthStore } from "@/stores/auth-store"
import { useMountainsList } from "@/hooks/use-mountains"
import { useLookups } from "@/hooks/use-socios"
import { useRutasList } from "@/hooks/use-rutas"
import { SocioSalidasView, ProximasSalidasTab, SalidasAnterioresTab, MisSalidasTab } from "./salida-socio-view"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Textarea } from "@/components/ui/textarea"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { toast } from "sonner"
import { Plus, Search, ChevronLeft, ChevronRight, Eye, Pencil, Trash2, Calendar, Ban, SlidersHorizontal, ChevronDown, X } from "lucide-react"
import { CategoriaChip } from "@/lib/salida-tipo"
import { SalidaFormDialog } from "./salida-form-dialog"
import { SalidaDetailDialog } from "./salida-detail-dialog"
import type { SalidaSummary, EstadoSalida } from "@/types/salidas"
import { CATEGORIA_BADGE, CATEGORIA_BADGE_SOLID } from "@/types/rutas"
import type { TipoActividad } from "@/types/rutas"

const actividadColor = CATEGORIA_BADGE
const actividadColorActive = CATEGORIA_BADGE_SOLID

const ACTIVIDADES: TipoActividad[] = ["ALPINISMO", "ESCALADA", "TREKKING", "CICLISMO", "INTEGRAL"]
const ACTIVIDAD_LABELS: Record<TipoActividad, string> = {
  ALPINISMO: "Alpinismo",
  ESCALADA: "Escalada",
  TREKKING: "Trekking",
  CICLISMO: "Ciclismo",
  INTEGRAL: "Integral",
}

interface AdvancedFilters {
  nivelMinimoId: string
  montanaId: string
  rutaId: string
  fechaDesde: string
  fechaHasta: string
}

const ADVANCED_DEFAULTS: AdvancedFilters = { nivelMinimoId: "", montanaId: "", rutaId: "", fechaDesde: "", fechaHasta: "" }

function countActiveAdvanced(f: AdvancedFilters): number {
  return Object.values(f).filter((v) => v !== "").length
}

type ActionType = "eliminar" | "cancelar"

interface ConfirmState {
  action: ActionType
  salida: SalidaSummary
  motivo: string
}

type StaffTab = "todas" | "proximas" | "anteriores" | "mis-salidas"

// ─── Tabla "Todas las salidas" ────────────────────────────────────────────────

function TodasLasSalidasTab({ canEdit, canDelete }: { canEdit: boolean; canDelete: boolean }) {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState("")
  const [searchDebounced, setSearchDebounced] = useState("")
  const [estadoFilter, setEstadoFilter] = useState<string>("")
  const [actividadFilter, setActividadFilter] = useState<TipoActividad | null>(null)
  const [advanced, setAdvanced] = useState<AdvancedFilters>(ADVANCED_DEFAULTS)
  const [advancedOpen, setAdvancedOpen] = useState(false)

  const [createOpen, setCreateOpen] = useState(false)
  const [editId, setEditId] = useState<string | null>(null)
  const [detailId, setDetailId] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)

  const { data: lookups } = useSalidaLookups()
  const { data: socioLookups } = useLookups()
  const { data: mountainsPage } = useMountainsList({ size: 500, sort: "nombre,asc" })
  const montanaIdNum = advanced.montanaId ? parseInt(advanced.montanaId, 10) : undefined
  const { data: rutasPage } = useRutasList({
    size: 500,
    sort: "nombre,asc",
    estado: "APROBADA",
    tipoActividad: actividadFilter ?? undefined,
    mountainId: montanaIdNum,
  })

  const { data: salidasPage, isLoading, isError } = useSalidasList({
    page,
    q: searchDebounced || undefined,
    estado: (estadoFilter as EstadoSalida) || undefined,
    tipoActividad: actividadFilter ?? undefined,
    nivelMinimoId: advanced.nivelMinimoId || undefined,
    montanaId: montanaIdNum,
    rutaId: advanced.rutaId ? parseInt(advanced.rutaId, 10) : undefined,
    fechaInicio: advanced.fechaDesde || undefined,
    fechaFin: advanced.fechaHasta || undefined,
  })

  const setAdv = (key: keyof AdvancedFilters, value: string) => {
    setAdvanced((prev) => {
      const next = { ...prev, [key]: value }
      if (key === "montanaId") next.rutaId = ""
      return next
    })
    setPage(0)
  }

  const resetAdvanced = () => { setAdvanced(ADVANCED_DEFAULTS); setPage(0) }
  const activeAdvancedCount = countActiveAdvanced(advanced)
  const hasAdvancedActive = activeAdvancedCount > 0

  const deleteMutation = useDeleteSalida()
  const cancelarMutation = useCancelarSalida()

  const handleSearch = () => { setSearchDebounced(search); setPage(0) }
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === "Enter") handleSearch() }

  const openConfirm = (action: ActionType, salida: SalidaSummary) => {
    setConfirm({ action, salida, motivo: "" })
  }

  const handleConfirm = async () => {
    if (!confirm) return
    const { action, salida, motivo } = confirm
    if (!motivo.trim()) { toast.error("El motivo es obligatorio"); return }

    try {
      if (action === "eliminar") {
        await deleteMutation.mutateAsync({ id: salida.id, motivo })
        toast.success("Salida eliminada")
      } else {
        await cancelarMutation.mutateAsync({ id: salida.id, motivo })
        toast.success("Salida cancelada")
      }
      setConfirm(null)
    } catch (error) { console.error(error);
      toast.error(action === "eliminar" ? "Error al eliminar" : "Error al cancelar")
    }
  }

  const fechaYaPaso = (fechaFin: string) =>
    new Date(fechaFin + "T23:59:59") < new Date()

  const estadoBadge = (estado: string) => {
    const map: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
      PLANIFICADA: "outline",
      EN_CURSO: "default",
      REALIZADA: "secondary",
      CANCELADA: "destructive",
    }
    const labels: Record<string, string> = {
      PLANIFICADA: "Planificada",
      EN_CURSO: "En curso",
      REALIZADA: "Realizada",
      CANCELADA: "Cancelada",
    }
    return <Badge variant={map[estado] ?? "outline"}>{labels[estado] ?? estado}</Badge>
  }

  const formatDate = (iso: string) =>
    new Date(iso + "T00:00:00").toLocaleDateString("es-EC", { day: "numeric", month: "short", year: "numeric" })

  const isPending = deleteMutation.isPending || cancelarMutation.isPending

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-emerald-500/5 to-transparent px-5 py-4">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-emerald-500/10">
                <Search className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
              </div>
              <div>
                <h2 className="text-base font-semibold text-foreground">Buscar salidas</h2>
                <p className="text-xs text-muted-foreground mt-0.5">Filtra por tipo, nombre, montaña y más</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {hasAdvancedActive && (
                <button
                  onClick={resetAdvanced}
                  className="flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-700 dark:text-emerald-400 hover:bg-emerald-500/20 transition-colors"
                >
                  <X className="h-3 w-3" />
                  Limpiar ({activeAdvancedCount})
                </button>
              )}
              {canEdit && (
                <Button onClick={() => setCreateOpen(true)} className="gap-2 shrink-0">
                  <Plus className="h-4 w-4" /> Nueva salida
                </Button>
              )}
            </div>
          </div>
        </div>
        <div className="p-5 space-y-4">
          {/* Chips de tipo de actividad */}
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => { setActividadFilter(null); setPage(0) }}
              className={`rounded-full px-3 py-1 text-sm font-medium transition-colors ${
                actividadFilter === null
                  ? "bg-foreground text-background"
                  : "bg-muted text-muted-foreground hover:bg-muted/80"
              }`}
            >
              Todas
            </button>
            {ACTIVIDADES.map((a) => (
              <button
                key={a}
                onClick={() => { setActividadFilter(a); setPage(0) }}
                className={`rounded-full px-3 py-1 text-sm font-medium transition-colors ${
                  actividadFilter === a ? actividadColorActive[a] : `${actividadColor[a]} hover:opacity-80`
                }`}
              >
                {ACTIVIDAD_LABELS[a]}
              </button>
            ))}
          </div>

          {/* Buscador + filtro de estado */}
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input placeholder="Buscar salidas..." value={search} onChange={(e) => setSearch(e.target.value)} onKeyDown={handleKeyDown} className="pl-9" />
            </div>
            <Select value={estadoFilter} onValueChange={(v) => { setEstadoFilter(v === "all" ? "" : v); setPage(0) }}>
              <SelectTrigger className="w-[180px]"><SelectValue placeholder="Estado" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos</SelectItem>
                {lookups?.estadosSalida.map((e) => (
                  <SelectItem key={e} value={e}>{e.replace("_", " ")}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Filtros avanzados */}
          <div>
            <button
              onClick={() => setAdvancedOpen((o) => !o)}
              className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
            >
              <SlidersHorizontal className="h-4 w-4" />
              Filtros avanzados
              {activeAdvancedCount > 0 && (
                <span className="rounded-full bg-emerald-500 px-1.5 py-0 text-xs text-white">
                  {activeAdvancedCount}
                </span>
              )}
              <ChevronDown className={`h-4 w-4 transition-transform ${advancedOpen ? "rotate-180" : ""}`} />
            </button>

            {advancedOpen && (
              <div className="pt-4 space-y-4">
              {/* Rango de fechas */}
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Fecha desde</Label>
                  <Input
                    type="date"
                    value={advanced.fechaDesde}
                    max={advanced.fechaHasta || undefined}
                    onChange={(e) => setAdv("fechaDesde", e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Fecha hasta</Label>
                  <Input
                    type="date"
                    value={advanced.fechaHasta}
                    min={advanced.fechaDesde || undefined}
                    onChange={(e) => setAdv("fechaHasta", e.target.value)}
                  />
                </div>
              </div>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                {/* Nivel mínimo */}
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Nivel mínimo requerido</Label>
                  <Select
                    value={advanced.nivelMinimoId}
                    onValueChange={(v) => setAdv("nivelMinimoId", v === "all" ? "" : v)}
                  >
                    <SelectTrigger><SelectValue placeholder="Cualquier nivel" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Cualquier nivel</SelectItem>
                      {socioLookups?.clasificaciones.map((c) => (
                        <SelectItem key={c.id} value={c.id}>{c.nombre}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {/* Montaña */}
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Montaña</Label>
                  <Select
                    value={advanced.montanaId}
                    onValueChange={(v) => setAdv("montanaId", v === "all" ? "" : v)}
                  >
                    <SelectTrigger><SelectValue placeholder="Todas las montañas" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Todas las montañas</SelectItem>
                      {mountainsPage?.content.map((m) => (
                        <SelectItem key={m.id} value={String(m.id)}>{m.nombre}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {/* Ruta — habilitada solo si hay tipo o montaña */}
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Ruta</Label>
                  <Select
                    value={advanced.rutaId}
                    onValueChange={(v) => setAdv("rutaId", v === "all" ? "" : v)}
                    disabled={!actividadFilter && !advanced.montanaId}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder={
                        !actividadFilter && !advanced.montanaId
                          ? "Selecciona tipo o montaña"
                          : "Todas las rutas"
                      } />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Todas las rutas</SelectItem>
                      {rutasPage?.content.map((r) => (
                        <SelectItem key={r.id} value={String(r.id)}>{r.nombre}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Salida</TableHead>
              <TableHead className="hidden md:table-cell">Fecha</TableHead>
              <TableHead className="text-center">Inscritos</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isError ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-destructive">
                  Error al cargar las salidas. Intente de nuevo.
                </TableCell>
              </TableRow>
            ) : isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 6 }).map((_, j) => (
                    <TableCell key={j}><div className="h-4 w-24 animate-pulse rounded bg-muted" /></TableCell>
                  ))}
                </TableRow>
              ))
            ) : salidasPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                  No se encontraron salidas
                </TableCell>
              </TableRow>
            ) : (
              salidasPage?.content.map((s) => (
                <TableRow key={s.id} className="cursor-pointer" onClick={() => setDetailId(s.id)}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
                      <div>
                        <p className="font-medium">{s.nombre}</p>
                        <div className="flex items-center gap-1.5 mt-0.5 flex-wrap">
                          {s.tipoActividad && <CategoriaChip tipoActividad={s.tipoActividad} />}
                          {s.formatoSalidaNombre && (
                            <span className="inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium bg-muted/50 text-muted-foreground border-border/60">
                              {s.formatoSalidaNombre}
                            </span>
                          )}
                          {s.publicoObjetivoNombre && s.publicoObjetivoNombre !== "Socios" && (
                            <span className="inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium bg-muted/50 text-muted-foreground border-border/60">
                              {s.publicoObjetivoNombre}
                            </span>
                          )}
                          {s.rutaNombre && (
                            <span className="text-xs text-muted-foreground">{s.rutaNombre}</span>
                          )}
                        </div>
                        {s.estado === "CANCELADA" && s.motivoCancelacion && (
                          <p className="text-xs text-destructive mt-0.5 line-clamp-1">
                            Motivo: {s.motivoCancelacion}
                          </p>
                        )}
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-sm">
                    {formatDate(s.fechaInicio)}
                    {s.fechaFin !== s.fechaInicio && ` — ${formatDate(s.fechaFin)}`}
                  </TableCell>
                  <TableCell className="text-center">
                    <span className="font-mono text-sm">
                      {s.totalInscritos}{s.capacidadMaxima ? `/${s.capacidadMaxima}` : ""}
                    </span>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-col items-start gap-1">
                      {estadoBadge(s.estado)}
                      {fechaYaPaso(s.fechaFin) && s.estado !== "CANCELADA" && (
                        s.tieneInforme
                          ? <span className="text-xs font-medium text-green-600 dark:text-green-400">Informe entregado</span>
                          : <span className="text-xs font-medium text-red-600 dark:text-red-400">Informe pendiente</span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDetailId(s.id) }} title="Ver detalle">
                        <Eye className="h-4 w-4" />
                      </Button>
                      {canEdit && s.estado === "PLANIFICADA" && (
                        <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setEditId(s.id) }} title="Editar">
                          <Pencil className="h-4 w-4" />
                        </Button>
                      )}
                      {canDelete && s.estado !== "CANCELADA" && s.estado !== "REALIZADA" && (
                        <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); openConfirm("cancelar", s) }} title="Cancelar salida">
                          <Ban className="h-4 w-4 text-orange-500" />
                        </Button>
                      )}
                      {canDelete && s.estado !== "REALIZADA" && (
                        <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); openConfirm("eliminar", s) }} title="Eliminar salida">
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {salidasPage && salidasPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">Página {salidasPage.page.number + 1} de {salidasPage.page.totalPages}</p>
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

      <SalidaFormDialog open={createOpen} onClose={() => setCreateOpen(false)} mode="create" />
      {editId && <SalidaFormDialog open={!!editId} onClose={() => setEditId(null)} mode="edit" salidaId={editId} />}
      {detailId && <SalidaDetailDialog open={!!detailId} onClose={() => setDetailId(null)} salidaId={detailId} />}

      <Dialog open={!!confirm} onOpenChange={(open) => { if (!open) setConfirm(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {confirm?.action === "eliminar" ? "Eliminar salida" : "Cancelar salida"}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <p className="text-sm text-muted-foreground">
              {confirm?.action === "eliminar"
                ? <>Vas a eliminar permanentemente <strong>"{confirm.salida.nombre}"</strong>. La salida dejará de ser visible para todos los usuarios.</>
                : <>Vas a cancelar <strong>"{confirm?.salida.nombre}"</strong>. La salida quedará visible como cancelada y no se podrán realizar inscripciones.</>
              }
            </p>
            <div className="space-y-1.5">
              <Label htmlFor="motivo">Motivo <span className="text-destructive">*</span></Label>
              <Textarea
                id="motivo"
                placeholder="Indica el motivo..."
                value={confirm?.motivo ?? ""}
                onChange={(e) => setConfirm((c) => c ? { ...c, motivo: e.target.value } : c)}
                rows={3}
                maxLength={500}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirm(null)} disabled={isPending}>
              Volver
            </Button>
            <Button
              variant={confirm?.action === "eliminar" ? "destructive" : "default"}
              onClick={handleConfirm}
              disabled={isPending || !confirm?.motivo.trim()}
            >
              {isPending ? "Procesando..." : confirm?.action === "eliminar" ? "Eliminar" : "Cancelar salida"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

// ─── Vista staff (con pestañas) ───────────────────────────────────────────────

function StaffSalidasView({ canEdit, canDelete }: { canEdit: boolean; canDelete: boolean }) {
  const [tab, setTab] = useState<StaffTab>("todas")

  const tabs: { key: StaffTab; label: string }[] = [
    { key: "todas", label: "Todas las salidas" },
    { key: "proximas", label: "Próximas salidas" },
    { key: "anteriores", label: "Salidas anteriores" },
    { key: "mis-salidas", label: "Mis Salidas (Kipu)" },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Salidas</h1>
        <p className="text-sm text-muted-foreground">Gestiona y explora las salidas del club</p>
      </div>

      <div className="flex gap-1 rounded-lg border border-border bg-muted p-1 w-fit flex-wrap">
        {tabs.map(({ key, label }) => (
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

      {tab === "todas" && <TodasLasSalidasTab canEdit={canEdit} canDelete={canDelete} />}
      {tab === "proximas" && <ProximasSalidasTab />}
      {tab === "anteriores" && <SalidasAnterioresTab />}
      {tab === "mis-salidas" && <MisSalidasTab />}
    </div>
  )
}

// ─── Entry point ──────────────────────────────────────────────────────────────

export function SalidasPage() {
  const user = useAuthStore((s) => s.user)
  const userRole = user?.rol?.toUpperCase() ?? ""
  const canEdit = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)
  const canDelete = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)

  if (!canEdit) return <SocioSalidasView />

  return <StaffSalidasView canEdit={canEdit} canDelete={canDelete} />
}
