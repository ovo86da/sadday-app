import { useState, useEffect } from "react"
import { useRutasList, useDeleteRuta, useAprobarRuta } from "@/hooks/use-rutas"
import { useAuthStore } from "@/stores/auth-store"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import { toast } from "sonner"
import { Plus, Search, ChevronLeft, ChevronRight, Eye, Pencil, Trash2, CheckCircle, Route } from "lucide-react"
import { RutaFormDialog } from "./ruta-form-dialog"
import { RutaDetailDialog } from "./ruta-detail-dialog"
import type { RutaSummary, TipoActividad } from "@/types/rutas"
import { TIPO_ACTIVIDAD_LABELS, CATEGORIA_BADGE, CATEGORIA_BADGE_SOLID } from "@/types/rutas"

const actividadColor       = CATEGORIA_BADGE
const actividadColorActive = CATEGORIA_BADGE_SOLID

const ACTIVIDADES: TipoActividad[] = ["ALPINISMO", "ESCALADA", "TREKKING", "CICLISMO"]

export function RutasPage() {
  const user = useAuthStore((s) => s.user)
  const userRole = user?.rol?.toUpperCase() ?? ""
  const canEdit = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)
  const canApprove = ["ADMIN", "DIRECTIVO"].includes(userRole)

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState("")
  const [searchDebounced, setSearchDebounced] = useState("")
  const [aprobadaFilter, setAprobadaFilter] = useState<string>("")
  const [actividadFilter, setActividadFilter] = useState<TipoActividad | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [editRuta, setEditRuta] = useState<RutaSummary | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)

  const { data: rutasPage, isLoading, isError } = useRutasList({
    page,
    q: searchDebounced || undefined,
    aprobada: aprobadaFilter === "" ? undefined : aprobadaFilter === "true",
    tipoActividad: actividadFilter ?? undefined,
  })

  const deleteMutation = useDeleteRuta()
  const aprobarMutation = useAprobarRuta()

  useEffect(() => {
    const timer = setTimeout(() => { setSearchDebounced(search); setPage(0) }, 300)
    return () => clearTimeout(timer)
  }, [search])

  const handleDelete = async (r: RutaSummary) => {
    if (!confirm(`¿Eliminar ruta "${r.nombre}"?`)) return
    try { await deleteMutation.mutateAsync(r.id); toast.success("Ruta eliminada") }
    catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      toast.error(axiosErr.response?.data?.message || "Error al eliminar ruta")
    }
  }

  const handleAprobar = async (r: RutaSummary) => {
    try { await aprobarMutation.mutateAsync(r.id); toast.success("Ruta aprobada") }
    catch (error) { console.error(error); toast.error("Error al aprobar") }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Rutas</h1>
          <p className="text-muted-foreground">
            {isLoading ? "Cargando..." : `${rutasPage?.page.totalElements ?? 0} rutas registradas`}
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)} className="gap-2">
          <Plus className="h-4 w-4" /> Proponer ruta
        </Button>
      </div>

      {/* Filters */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-violet-500/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-violet-500/10">
              <Route className="h-4 w-4 text-violet-600 dark:text-violet-400" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Buscar rutas</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Filtra por actividad, nombre o estado de aprobación</p>
            </div>
          </div>
        </div>
        <div className="p-5 space-y-4">
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
                {TIPO_ACTIVIDAD_LABELS[a]}
              </button>
            ))}
          </div>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input placeholder="Buscar por nombre o montaña..." value={search} onChange={(e) => setSearch(e.target.value)} className="pl-9" />
            </div>
            <Select value={aprobadaFilter} onValueChange={(v) => { setAprobadaFilter(v === "all" ? "" : v); setPage(0) }}>
              <SelectTrigger className="w-[180px]"><SelectValue placeholder="Aprobación" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todas</SelectItem>
                <SelectItem value="true">Aprobadas</SelectItem>
                <SelectItem value="false">Pendientes</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Ruta</TableHead>
              <TableHead className="hidden md:table-cell">Lugar</TableHead>
              <TableHead className="hidden lg:table-cell">Dificultad</TableHead>
              <TableHead className="hidden xl:table-cell">Duración</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isError ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-destructive">
                  Error al cargar las rutas. Intente de nuevo.
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
            ) : rutasPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">No se encontraron rutas</TableCell>
              </TableRow>
            ) : (
              rutasPage?.content.map((r) => (
                <TableRow key={r.id} className="cursor-pointer" onClick={() => setDetailId(r.id)}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Route className="h-4 w-4 text-muted-foreground shrink-0" />
                      <div>
                        <p className="font-medium">{r.nombre}</p>
                        <div className="flex items-center gap-1 mt-0.5">
                          <span className={`rounded-full px-1.5 py-0 text-xs font-medium ${actividadColor[r.tipoActividad]}`}>
                            {TIPO_ACTIVIDAD_LABELS[r.tipoActividad]}
                          </span>
                          {r.sectorZona && <span className="text-xs text-muted-foreground">{r.sectorZona}</span>}
                        </div>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-sm">
                    {r.mountainNombre ?? r.lugarReferencia ?? "—"}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell text-sm">
                    {r.dificultadResumen || "—"}
                  </TableCell>
                  <TableCell className="hidden xl:table-cell text-sm">
                    {r.duracionDias ? `${r.duracionDias}d` : r.duracionHoras ? `${r.duracionHoras}h` : "—"}
                  </TableCell>
                  <TableCell>
                    <Badge variant={r.aprobada ? "default" : "secondary"}>
                      {r.aprobada ? "Aprobada" : "Pendiente"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDetailId(r.id) }}>
                        <Eye className="h-4 w-4" />
                      </Button>
                      {canApprove && !r.aprobada && (
                        <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); handleAprobar(r) }}>
                          <CheckCircle className="h-4 w-4 text-green-500" />
                        </Button>
                      )}
                      {canEdit && (
                        <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setEditRuta(r) }}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                      )}
                      {canEdit && (
                        <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); handleDelete(r) }}>
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

      {rutasPage && rutasPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">Página {rutasPage.page.number + 1} de {rutasPage.page.totalPages}</p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={rutasPage.page.number === 0} onClick={() => setPage((p) => p - 1)}>
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button variant="outline" size="sm" disabled={rutasPage.page.number >= rutasPage.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      <RutaFormDialog open={createOpen} onClose={() => setCreateOpen(false)} mode="create" />
      {editRuta && <RutaFormDialog open={!!editRuta} onClose={() => setEditRuta(null)} mode="edit" ruta={editRuta} />}
      {detailId !== null && <RutaDetailDialog open={detailId !== null} onClose={() => setDetailId(null)} rutaId={detailId} />}
    </div>
  )
}
