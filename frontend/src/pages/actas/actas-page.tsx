import { useState } from "react"
import { useActasList, useDeleteActa, useActaDetail, useGenerarPdfActa } from "@/hooks/use-actas"
import api from "@/lib/api"

async function downloadPdf(url: string, filename: string) {
  const response = await api.get(url, { responseType: "blob" })
  const blob = new Blob([response.data], { type: "application/pdf" })
  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = objectUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(objectUrl)
}

import { useAuthStore } from "@/stores/auth-store"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import { toast } from "sonner"
import {
  Plus, Search, ChevronLeft, ChevronRight, Eye, Pencil, Trash2,
  BookOpen, Users, FileText, Download, RefreshCw, Upload,
} from "lucide-react"
import { ActaFormDialog } from "./acta-form-dialog"
import { ActaImportDialog } from "./acta-import-dialog"
import type { TipoActa, ActaDetail } from "@/types/actas"

// ─── Page ────────────────────────────────────────────────────────────────────

export function ActasPage() {
  const user = useAuthStore((s) => s.user)
  const userRole = user?.rol?.toUpperCase() ?? ""

  const isDirectivo       = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)
  const isSecretaria      = userRole === "SECRETARIA"
  const isAdminOrSec      = ["ADMIN", "SECRETARIA"].includes(userRole)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Actas de Reunión</h1>
        <p className="text-muted-foreground">Gestión de actas de reuniones del club</p>
      </div>

      <Tabs defaultValue="socios">
        <TabsList>
          <TabsTrigger value="socios">Actas de Socios</TabsTrigger>
          {isDirectivo && <TabsTrigger value="directiva">Actas de Directiva</TabsTrigger>}
        </TabsList>

        <TabsContent value="socios" className="mt-4">
          <ActasTabContent
            tipo="SOCIOS"
            canEdit={isAdminOrSec}
            canImport={isAdminOrSec}
            canGenerarPdf={isAdminOrSec}
            canDownloadPdf={true}
          />
        </TabsContent>

        {isDirectivo && (
          <TabsContent value="directiva" className="mt-4">
            <ActasTabContent
              tipo="DIRECTIVA"
              canEdit={isAdminOrSec}
              canImport={isSecretaria}
              canGenerarPdf={isAdminOrSec}
              canDownloadPdf={isDirectivo}
            />
          </TabsContent>
        )}
      </Tabs>
    </div>
  )
}

// ─── Tab content ─────────────────────────────────────────────────────────────

interface TabContentProps {
  tipo: TipoActa
  canEdit: boolean
  canImport: boolean
  canGenerarPdf: boolean
  canDownloadPdf: boolean
}

function ActasTabContent({ tipo, canEdit, canImport, canGenerarPdf, canDownloadPdf }: TabContentProps) {
  const [page, setPage]                   = useState(0)
  const [search, setSearch]               = useState("")
  const [searchDebounced, setSearchDeb]   = useState("")
  const [createOpen, setCreateOpen]       = useState(false)
  const [importOpen, setImportOpen]       = useState(false)
  const [editActaId, setEditActaId]       = useState<string | null>(null)
  const [detailId, setDetailId]           = useState<string | null>(null)

  const { data: actasPage, isLoading } = useActasList({ page, q: searchDebounced || undefined, tipo })
  const deleteMutation = useDeleteActa()

  const handleSearch  = () => { setSearchDeb(search); setPage(0) }
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === "Enter") handleSearch() }

  const handleDelete = async (id: string, label: string) => {
    if (!confirm(`¿Eliminar acta "${label}"?`)) return
    try { await deleteMutation.mutateAsync(id); toast.success("Acta eliminada") }
    catch (error) { console.error(error); toast.error("Error al eliminar") }
  }

  const formatDate = (iso: string) =>
    new Date(iso + "T00:00:00").toLocaleDateString("es-EC", { day: "numeric", month: "long", year: "numeric" })

  const formatNumero = (fecha: string, num: number | null) => {
    if (!num) return null
    if (tipo === "DIRECTIVA") {
      const year = new Date(fecha + "T00:00:00").getFullYear()
      return `No. ${year}-${String(num).padStart(4, "0")}`
    }
    return `No. ${num}`
  }

  return (
    <div className="space-y-4">
      {/* Barra de búsqueda + acciones */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-amber-500/5 to-transparent px-5 py-4">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-amber-500/10">
                <BookOpen className="h-4 w-4 text-amber-600 dark:text-amber-400" />
              </div>
              <div>
                <h2 className="text-base font-semibold text-foreground">
                  Actas de {tipo === "DIRECTIVA" ? "Directiva" : "Socios"}
                </h2>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {actasPage ? `${actasPage.page.totalElements} actas registradas` : "Cargando..."}
                </p>
              </div>
            </div>
            {canEdit && (
              <div className="flex gap-2">
                {canImport && (
                  <Button variant="outline" onClick={() => setImportOpen(true)} className="gap-2">
                    <Upload className="h-4 w-4" /> Importar .md
                  </Button>
                )}
                <Button onClick={() => setCreateOpen(true)} className="gap-2">
                  <Plus className="h-4 w-4" /> Nueva acta
                </Button>
              </div>
            )}
          </div>
        </div>
        <div className="p-5">
          <div className="flex gap-2">
            <div className="relative flex-1 max-w-md">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder={`Buscar actas de ${tipo === "DIRECTIVA" ? "directiva" : "socios"}...`}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={handleKeyDown}
                className="pl-9"
              />
            </div>
            <Button variant="outline" size="icon" onClick={handleSearch}>
              <Search className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      {/* Tabla */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Fecha</TableHead>
              <TableHead className="hidden sm:table-cell">Número</TableHead>
              <TableHead className="hidden md:table-cell">Asistentes</TableHead>
              <TableHead className="hidden lg:table-cell">Creada por</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 4 }).map((_, j) => (
                    <TableCell key={j}><div className="h-4 w-24 animate-pulse rounded bg-muted" /></TableCell>
                  ))}
                </TableRow>
              ))
            ) : actasPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                  No se encontraron actas
                </TableCell>
              </TableRow>
            ) : (
              actasPage?.content.map((a) => (
                <TableRow key={a.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <BookOpen className="h-4 w-4 text-muted-foreground shrink-0" />
                      <div>
                        <p className="font-medium">{formatDate(a.fecha)}</p>
                        <p className="text-xs text-muted-foreground">{a.hora}{a.horaFin ? ` – ${a.horaFin}` : ""}</p>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell text-sm text-muted-foreground">
                    {formatNumero(a.fecha, a.numeroReunion) ?? "—"}
                  </TableCell>
                  <TableCell className="hidden md:table-cell">
                    <div className="flex items-center gap-1 text-sm text-muted-foreground">
                      <Users className="h-3.5 w-3.5" />
                      <span>{a.totalAsistentes}</span>
                    </div>
                  </TableCell>
                  <TableCell className="hidden lg:table-cell text-sm text-muted-foreground">
                    {a.creadaPorNombre}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon" title="Ver detalle" onClick={() => setDetailId(a.id)}>
                        <Eye className="h-4 w-4" />
                      </Button>
                      {canEdit && (
                        <>
                          <Button variant="ghost" size="icon" title="Editar" onClick={() => setEditActaId(a.id)}>
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost" size="icon" title="Eliminar"
                            onClick={() => handleDelete(a.id, a.fecha)}
                          >
                            <Trash2 className="h-4 w-4 text-destructive" />
                          </Button>
                        </>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Paginación */}
      {actasPage && actasPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Página {actasPage.page.number + 1} de {actasPage.page.totalPages}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={actasPage.page.number === 0} onClick={() => setPage((p) => p - 1)}>
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button variant="outline" size="sm" disabled={actasPage.page.number >= actasPage.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Diálogos */}
      <ActaFormDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        mode="create"
        defaultTipo={tipo}
      />
      {editActaId && (
        <ActaFormDialog
          open={!!editActaId}
          onClose={() => setEditActaId(null)}
          mode="edit"
          actaId={editActaId}
          defaultTipo={tipo}
        />
      )}
      {detailId && (
        <ActaDetailDialog
          actaId={detailId}
          tipo={tipo}
          onClose={() => setDetailId(null)}
          canEdit={canEdit}
          canGenerarPdf={canGenerarPdf}
          canDownloadPdf={canDownloadPdf}
        />
      )}
      {canImport && (
        <ActaImportDialog
          open={importOpen}
          onOpenChange={setImportOpen}
          onImported={() => setImportOpen(false)}
          tipo={tipo}
        />
      )}
    </div>
  )
}

// ─── Acta Detail Dialog ───────────────────────────────────────────────────────

function ActaDetailDialog({
  actaId, tipo, onClose, canEdit: _canEdit, canGenerarPdf, canDownloadPdf,
}: {
  actaId: string
  tipo: TipoActa
  onClose: () => void
  canEdit: boolean
  canGenerarPdf: boolean
  canDownloadPdf: boolean
}) {
  const { data: acta, isLoading } = useActaDetail(actaId)
  const [isDownloading, setIsDownloading] = useState(false)
  const generarPdfMutation = useGenerarPdfActa(actaId)

  const handleDescargarPdf = async () => {
    if (!acta) return
    setIsDownloading(true)
    try {
      await downloadPdf(`/v1/actas/${actaId}/pdf`, acta.documentoFilename ?? `acta-${actaId}.pdf`)
    } catch (error) { console.error(error);
      toast.error("Error al descargar el PDF")
    } finally {
      setIsDownloading(false)
    }
  }

  const handleGenerarPdf = async () => {
    try {
      const { blob, filename } = await generarPdfMutation.mutateAsync()
      const objectUrl = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = objectUrl
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(objectUrl)
      toast.success("PDF generado y descargado correctamente")
    } catch (error) { console.error(error);
      toast.error("Error al generar el PDF")
    }
  }

  const formatDate = (iso: string) =>
    new Date(iso + "T00:00:00").toLocaleDateString("es-EC", { day: "numeric", month: "long", year: "numeric" })

  const labelActDesc = tipo === "DIRECTIVA" ? "Desarrollo de la reunión" : "Actividades realizadas"
  const labelActPorReal = tipo === "DIRECTIVA" ? "Orden del día" : "Actividades por realizar"

  return (
    <Dialog open onOpenChange={() => onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center gap-3">
            <DialogTitle>Detalle del Acta</DialogTitle>
            <Badge variant={tipo === "DIRECTIVA" ? "secondary" : "outline"}>
              {tipo === "DIRECTIVA" ? "Directiva" : "Socios"}
            </Badge>
          </div>
        </DialogHeader>
        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : acta ? (
          <ActaDetailContent
            acta={acta}
            tipo={tipo}
            labelActDesc={labelActDesc}
            labelActPorReal={labelActPorReal}
            formatDate={formatDate}
            canGenerarPdf={canGenerarPdf}
            canDownloadPdf={canDownloadPdf}
            isDownloading={isDownloading}
            isGenerating={generarPdfMutation.isPending}
            onDescargar={handleDescargarPdf}
            onGenerar={handleGenerarPdf}
            onClose={onClose}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function ActaDetailContent({
  acta, tipo, labelActDesc, labelActPorReal, formatDate,
  canGenerarPdf, canDownloadPdf,
  isDownloading, isGenerating,
  onDescargar, onGenerar, onClose,
}: {
  acta: ActaDetail
  tipo: TipoActa
  labelActDesc: string
  labelActPorReal: string
  formatDate: (iso: string) => string
  canGenerarPdf: boolean
  canDownloadPdf: boolean
  isDownloading: boolean
  isGenerating: boolean
  onDescargar: () => void
  onGenerar: () => void
  onClose: () => void
}) {
  return (
    <div className="space-y-5">
      {/* Datos generales */}
      <div className="grid gap-2 sm:grid-cols-3">
        <div><p className="text-xs text-muted-foreground">Fecha</p><p className="text-sm font-medium">{formatDate(acta.fecha)}</p></div>
        <div>
          <p className="text-xs text-muted-foreground">Hora</p>
          <p className="text-sm font-medium">{acta.hora}{acta.horaFin ? ` – ${acta.horaFin}` : ""}</p>
        </div>
        <div><p className="text-xs text-muted-foreground">Lugar</p><p className="text-sm font-medium">{acta.lugar || "Virtual"}</p></div>
      </div>

      {/* Autoridades (solo si existen) */}
      {(acta.presidenteReunionNombre || acta.secretariaReunionNombre) && (
        <div className="grid gap-2 sm:grid-cols-2">
          {acta.presidenteReunionNombre && (
            <div>
              <p className="text-xs text-muted-foreground">Preside</p>
              <p className="text-sm font-medium">{acta.presidenteReunionNombre}</p>
            </div>
          )}
          {acta.secretariaReunionNombre && (
            <div>
              <p className="text-xs text-muted-foreground">Secretaria</p>
              <p className="text-sm font-medium">{acta.secretariaReunionNombre}</p>
            </div>
          )}
        </div>
      )}

      {/* Contenido textual */}
      {acta.actividadesPorRealizar && (
        <div>
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">{labelActPorReal}</p>
          <p className="text-sm whitespace-pre-wrap mt-1">{acta.actividadesPorRealizar}</p>
        </div>
      )}
      {acta.actividadesRealizadasDesc && (
        <div>
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">{labelActDesc}</p>
          <p className="text-sm whitespace-pre-wrap mt-1">{acta.actividadesRealizadasDesc}</p>
        </div>
      )}
      {acta.acuerdos && (
        <div>
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Acuerdos</p>
          <p className="text-sm whitespace-pre-wrap mt-1">{acta.acuerdos}</p>
        </div>
      )}
      {acta.varios && (
        <div>
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Varios</p>
          <p className="text-sm whitespace-pre-wrap mt-1">{acta.varios}</p>
        </div>
      )}
      {acta.observaciones && (
        <div>
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Observaciones</p>
          <p className="text-sm whitespace-pre-wrap mt-1">{acta.observaciones}</p>
        </div>
      )}

      {/* Asistentes */}
      <div className="space-y-2">
        <h3 className="flex items-center gap-2 text-sm font-semibold border-b border-border pb-2">
          <Users className="h-4 w-4" /> Asistentes ({acta.asistentes.length})
        </h3>
        {acta.asistentes.length === 0 ? (
          <p className="text-sm text-muted-foreground">Sin asistentes registrados</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {acta.asistentes.map((a) => (
              <Badge key={a.id} variant="secondary">
                {a.socioApellido && a.socioNombre
                  ? `${a.socioApellido}, ${a.socioNombre}`
                  : a.nombreRaw ?? "—"}
              </Badge>
            ))}
          </div>
        )}
      </div>

      {/* Informes vinculados */}
      {acta.informes.length > 0 && (
        <div className="space-y-2">
          <h3 className="flex items-center gap-2 text-sm font-semibold border-b border-border pb-2">
            <FileText className="h-4 w-4" /> Informes vinculados ({acta.informes.length})
          </h3>
          {acta.informes.map((inf) => (
            <div key={inf.id} className="flex items-center gap-2 rounded-lg border border-border p-2">
              <FileText className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm">{inf.salidaNombre}</span>
            </div>
          ))}
        </div>
      )}

      {/* Acciones PDF */}
      <div className="flex flex-wrap justify-end gap-2 pt-4 border-t border-border">
        {acta.documentoId ? (
          <>
            {canDownloadPdf && (
              <Button variant="outline" onClick={onDescargar} disabled={isDownloading} className="gap-2">
                <Download className="h-4 w-4" />
                {isDownloading ? "Descargando..." : "Descargar PDF"}
              </Button>
            )}
            {canGenerarPdf && (
              <Button variant="ghost" size="icon" onClick={onGenerar} disabled={isGenerating} title="Regenerar PDF">
                <RefreshCw className={`h-4 w-4 ${isGenerating ? "animate-spin" : ""}`} />
              </Button>
            )}
          </>
        ) : (
          canGenerarPdf && (
            <Button variant="outline" onClick={onGenerar} disabled={isGenerating} className="gap-2">
              <FileText className="h-4 w-4" />
              {isGenerating ? "Generando..." : "Generar PDF"}
            </Button>
          )
        )}
        <Button variant="outline" onClick={onClose}>Cerrar</Button>
      </div>
    </div>
  )
}
