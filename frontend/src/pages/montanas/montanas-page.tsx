import { useState, useEffect } from "react"
import { useMountainsList, useDeleteMountain } from "@/hooks/use-mountains"
import { useAuthStore } from "@/stores/auth-store"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import { toast } from "sonner"
import { Plus, Search, ChevronLeft, ChevronRight, Eye, Pencil, Trash2, Mountain } from "lucide-react"
import { MountainFormDialog } from "./mountain-form-dialog"
import { MountainDetailDialog } from "./mountain-detail-dialog"
import type { MountainSummary } from "@/types/mountains"

export function MontanasPage() {
  const user = useAuthStore((s) => s.user)
  const userRole = user?.rol?.toUpperCase() ?? ""
  const canEdit = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState("")
  const [searchDebounced, setSearchDebounced] = useState("")

  const [createOpen, setCreateOpen] = useState(false)
  const [editMountain, setEditMountain] = useState<MountainSummary | null>(null)
  const [detailMountain, setDetailMountain] = useState<MountainSummary | null>(null)

  const { data: mountainsPage, isLoading, isError } = useMountainsList({
    page,
    q: searchDebounced || undefined,
  })

  const deleteMutation = useDeleteMountain()

  useEffect(() => {
    const timer = setTimeout(() => { setSearchDebounced(search); setPage(0) }, 300)
    return () => clearTimeout(timer)
  }, [search])

  const handleDelete = async (m: MountainSummary) => {
    if (!confirm(`¿Eliminar "${m.nombre}"? Esta acción no se puede deshacer.`)) return
    try {
      await deleteMutation.mutateAsync(m.id)
      toast.success("Montaña eliminada")
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      toast.error(axiosErr.response?.data?.message || "Error al eliminar montaña")
    }
  }


  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Montañas</h1>
          <p className="text-muted-foreground">
            {isLoading ? "Cargando..." : `${mountainsPage?.page.totalElements ?? 0} montañas registradas`}
          </p>
        </div>
        {canEdit && (
          <Button onClick={() => setCreateOpen(true)} className="gap-2">
            <Plus className="h-4 w-4" />
            Nueva montaña
          </Button>
        )}
      </div>

      {/* Search */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <Search className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Buscar montañas</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Filtra por nombre o región</p>
            </div>
          </div>
        </div>
        <div className="p-5">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Buscar por nombre o región..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Montaña</TableHead>
              <TableHead>Región</TableHead>
              <TableHead>País</TableHead>
              <TableHead className="text-right">Altitud (msnm)</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isError ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-destructive">
                  Error al cargar las montañas. Intente de nuevo.
                </TableCell>
              </TableRow>
            ) : isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 5 }).map((_, j) => (
                    <TableCell key={j}><div className="h-4 w-24 animate-pulse rounded bg-muted" /></TableCell>
                  ))}
                </TableRow>
              ))
            ) : mountainsPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                  No se encontraron montañas
                </TableCell>
              </TableRow>

            ) : (
              mountainsPage?.content.map((m) => (
                <TableRow key={m.id} className="cursor-pointer" onClick={() => setDetailMountain(m)}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Mountain className="h-4 w-4 text-muted-foreground" />
                      <span className="font-medium">{m.nombre}</span>
                    </div>
                  </TableCell>
                  <TableCell>{m.region}</TableCell>
                  <TableCell>{m.pais}</TableCell>
                  <TableCell className="text-right">
                    <Badge variant="secondary" className="font-mono">
                      {m.altitud.toLocaleString()} m
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setDetailMountain(m) }} title="Ver estadísticas">
                        <Eye className="h-4 w-4" />
                      </Button>
                      {canEdit && (
                        <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); setEditMountain(m) }} title="Editar">
                          <Pencil className="h-4 w-4" />
                        </Button>
                      )}
                      {canEdit && (
                        <Button variant="ghost" size="icon" onClick={(e) => { e.stopPropagation(); handleDelete(m) }} title="Eliminar">
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

      {/* Pagination */}
      {mountainsPage && mountainsPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Página {mountainsPage.page.number + 1} de {mountainsPage.page.totalPages}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={mountainsPage.page.number === 0} onClick={() => setPage((p) => p - 1)}>
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button variant="outline" size="sm" disabled={mountainsPage.page.number >= mountainsPage.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Dialogs */}
      <MountainFormDialog open={createOpen} onClose={() => setCreateOpen(false)} mode="create" />
      {editMountain && (
        <MountainFormDialog
          open={!!editMountain}
          onClose={() => setEditMountain(null)}
          mode="edit"
          mountain={editMountain}
        />
      )}
      {detailMountain && (
        <MountainDetailDialog
          open={!!detailMountain}
          onClose={() => setDetailMountain(null)}
          mountain={detailMountain}
        />
      )}
    </div>
  )
}
