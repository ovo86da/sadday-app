import { useState } from "react"
import { useSociosList, useLookups, useHabilitarSocio, useInhabilitarSocio, useDeleteSocio, useReenviarInvitacion, useCsvImportPreview, useCsvImportConfirmar } from "@/hooks/use-socios"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"
import { toast } from "sonner"
import { Plus, Search, ChevronLeft, ChevronRight, Eye, Pencil, Trash2, UserCheck, UserX, Mail, FileUp, Upload, AlertCircle, CheckCircle2, X } from "lucide-react"
import { SocioFormDialog } from "./socio-form-dialog"
import { SocioDetailDialog } from "./socio-detail-dialog"
import { CsvHabilitacionDialog } from "./csv-habilitacion-dialog"
import { InvitacionesTab } from "./invitaciones-tab"
import type { SocioSummary, CsvFilaValida, CsvImportPreviewResponse } from "@/types/socios"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { useAuthStore } from "@/stores/auth-store"

export function SociosPage() {
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.rol?.toUpperCase() === "ADMIN"
  const isAdminOrSecretaria = ["ADMIN", "SECRETARIA"].includes(user?.rol?.toUpperCase() ?? "")

  // ─── Filters & pagination ──────────────────────────
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState("")
  const [searchDebounced, setSearchDebounced] = useState("")
  const [rolFilter, setRolFilter] = useState<string>("")
  const [estadoFilter, setEstadoFilter] = useState<string>("")
  const [tipoFilter, setTipoFilter] = useState<string>("")

  // ─── Dialogs ───────────────────────────────────────
  const [createOpen, setCreateOpen] = useState(false)
  const [editId, setEditId] = useState<string | null>(null)
  const [detailId, setDetailId] = useState<string | null>(null)
  const [csvOpen, setCsvOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)

  // ─── Data ──────────────────────────────────────────
  const { data: lookups } = useLookups()
  const { data: sociosPage, isLoading, isError } = useSociosList({
    page,
    q: searchDebounced || undefined,
    rolId: rolFilter ? Number(rolFilter) : undefined,
    estadoId: estadoFilter ? Number(estadoFilter) : undefined,
    tipoId: tipoFilter ? Number(tipoFilter) : undefined,
  })

  const habilitarMutation = useHabilitarSocio()
  const inhabilitarMutation = useInhabilitarSocio()
  const deleteMutation = useDeleteSocio()
  const reenviarMutation = useReenviarInvitacion()

  // ─── Handlers ──────────────────────────────────────
  const handleSearch = () => {
    setSearchDebounced(search)
    setPage(0)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") handleSearch()
  }

  const handleHabilitar = async (id: string) => {
    try {
      await habilitarMutation.mutateAsync(id)
      toast.success("Socio habilitado")
    } catch (error) { console.error(error);
      toast.error("Error al habilitar socio")
    }
  }

  const handleInhabilitar = async (id: string) => {
    try {
      await inhabilitarMutation.mutateAsync(id)
      toast.success("Socio inhabilitado")
    } catch (error) { console.error(error);
      toast.error("Error al inhabilitar socio")
    }
  }

  const handleReenviarInvitacion = async (socio: SocioSummary) => {
    if (!confirm(`¿Reenviar invitación de activación a ${socio.nombre} ${socio.apellido} (${socio.correo})?`)) return
    try {
      await reenviarMutation.mutateAsync(socio.id)
      toast.success(`Invitación reenviada a ${socio.correo}`)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg ?? "Error al reenviar la invitación")
    }
  }

  const handleDelete = async (socio: SocioSummary) => {
    if (!confirm(`¿Eliminar a ${socio.nombre} ${socio.apellido}? Esta acción no se puede deshacer.`)) return
    try {
      await deleteMutation.mutateAsync(socio.id)
      toast.success("Socio eliminado")
    } catch (error) { console.error(error);
      toast.error("Error al eliminar socio")
    }
  }

  const estadoBadgeVariant = (estado: string): "default" | "secondary" | "destructive" | "outline" => {
    if (estado.toLowerCase().includes("habilitado") && !estado.toLowerCase().includes("in")) return "default"
    if (estado.toLowerCase().includes("inhabilitado")) return "destructive"
    if (estado.toLowerCase().includes("vitalicio")) return "secondary"
    return "outline"
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Socios</h1>
          <p className="text-muted-foreground">
            {sociosPage ? `${sociosPage.page.totalElements} socios registrados` : "Cargando..."}
          </p>
        </div>
        {isAdminOrSecretaria && (
          <div className="flex gap-2">
            {isAdminOrSecretaria && (
              <Button variant="outline" onClick={() => setImportOpen(true)} className="gap-2">
                <Upload className="h-4 w-4" />
                Importar socios
              </Button>
            )}
            <Button variant="outline" onClick={() => setCsvOpen(true)} className="gap-2">
              <FileUp className="h-4 w-4" />
              Carga CSV
            </Button>
            <Button onClick={() => setCreateOpen(true)} className="gap-2">
              <Plus className="h-4 w-4" />
              Invitar socio
            </Button>
          </div>
        )}
      </div>

      <Tabs defaultValue="socios">
        <TabsList>
          <TabsTrigger value="socios">Socios</TabsTrigger>
          {isAdminOrSecretaria && (
            <TabsTrigger value="invitaciones">Invitaciones pendientes</TabsTrigger>
          )}
        </TabsList>

        <TabsContent value="socios" className="mt-4 space-y-4">
      {/* Filters */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <Search className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Buscar socios</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Filtra por nombre, cédula, estado o rol</p>
            </div>
          </div>
        </div>
        <div className="p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Buscar por nombre, cédula o correo..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={handleKeyDown}
                className="pl-9"
              />
            </div>
            <Select value={estadoFilter} onValueChange={(v) => { setEstadoFilter(v === "all" ? "" : v); setPage(0) }}>
              <SelectTrigger className="w-[180px]"> <SelectValue placeholder="Estado" /> </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos los estados</SelectItem>
                {lookups?.estadosHabilitacion.map((e) => (
                  <SelectItem key={e.id} value={String(e.id)}>{e.nombre}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={tipoFilter} onValueChange={(v) => { setTipoFilter(v === "all" ? "" : v); setPage(0) }}>
              <SelectTrigger className="w-[180px]"> <SelectValue placeholder="Tipo" /> </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos los tipos</SelectItem>
                {lookups?.tiposSocio.map((t) => (
                  <SelectItem key={t.id} value={String(t.id)}>{t.nombre}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={rolFilter} onValueChange={(v) => { setRolFilter(v === "all" ? "" : v); setPage(0) }}>
              <SelectTrigger className="w-[150px]"> <SelectValue placeholder="Rol" /> </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos los roles</SelectItem>
                {lookups?.rolesSistema.map((r) => (
                  <SelectItem key={r.id} value={String(r.id)}>{r.nombre}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nombre</TableHead>
              <TableHead className="hidden md:table-cell">Cédula</TableHead>
              <TableHead className="hidden lg:table-cell">Correo</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead className="hidden sm:table-cell">Tipo</TableHead>
              <TableHead className="hidden lg:table-cell">Nivel técnico</TableHead>
              <TableHead className="hidden xl:table-cell">Rol</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isError ? (
              <TableRow>
                <TableCell colSpan={8} className="py-8 text-center text-destructive">
                  Error al cargar los socios. Intente de nuevo.
                </TableCell>
              </TableRow>
            ) : isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 8 }).map((_, j) => (
                    <TableCell key={j}>
                      <div className="h-4 w-24 animate-pulse rounded bg-muted" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : sociosPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                  No se encontraron socios
                </TableCell>
              </TableRow>
            ) : (
              sociosPage?.content.map((socio) => (
                <TableRow key={socio.id} className={`group ${socio.estadoAcceso && socio.estadoAcceso !== "ACTIVE" ? "bg-destructive/5" : ""}`}>
                  <TableCell>
                    <div>
                      <p className="font-medium">{socio.apellido}, {socio.nombre}</p>
                      <p className="text-xs text-muted-foreground md:hidden">{socio.cedula}</p>
                    </div>
                  </TableCell>
                  <TableCell className="hidden md:table-cell">{socio.cedula}</TableCell>
                  <TableCell className="hidden lg:table-cell text-sm">{socio.correo}</TableCell>
                  <TableCell>
                    <Badge variant={estadoBadgeVariant(socio.estadoHabilitacion)}>
                      {socio.estadoHabilitacion}
                    </Badge>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell text-sm">{socio.tipoSocio}</TableCell>
                  <TableCell className="hidden lg:table-cell text-sm">{socio.nivelTecnico ?? "—"}</TableCell>
                  <TableCell className="hidden xl:table-cell text-sm">
                    <span>{socio.rolSistema}</span>
                    {socio.esJefeMontana && (
                      <Badge variant="outline" className="ml-1.5 text-xs border-amber-500/60 text-amber-700 dark:text-amber-400">JM</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon" onClick={() => setDetailId(socio.id)} title="Ver detalle">
                        <Eye className="h-4 w-4" />
                      </Button>
                      {isAdminOrSecretaria && (
                        <Button variant="ghost" size="icon" onClick={() => setEditId(socio.id)} title="Editar">
                          <Pencil className="h-4 w-4" />
                        </Button>
                      )}
                      {isAdminOrSecretaria && !socio.tieneCuenta && (
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleReenviarInvitacion(socio)}
                          disabled={reenviarMutation.isPending}
                          title="Reenviar invitación de activación"
                        >
                          <Mail className="h-4 w-4 text-blue-500" />
                        </Button>
                      )}
                      {socio.estadoHabilitacion.toLowerCase().includes("inhabilitado") ? (
                        <Button variant="ghost" size="icon" onClick={() => handleHabilitar(socio.id)} title="Habilitar">
                          <UserCheck className="h-4 w-4 text-green-500" />
                        </Button>
                      ) : (
                        <Button variant="ghost" size="icon" onClick={() => handleInhabilitar(socio.id)} title="Inhabilitar">
                          <UserX className="h-4 w-4 text-yellow-500" />
                        </Button>
                      )}
                      {isAdmin && (
                        <Button variant="ghost" size="icon" onClick={() => handleDelete(socio)} title="Eliminar">
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
      {sociosPage && sociosPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Página {sociosPage.page.number + 1} de {sociosPage.page.totalPages}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={sociosPage.page.number === 0} onClick={() => setPage((p) => p - 1)}>
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button variant="outline" size="sm" disabled={sociosPage.page.number >= sociosPage.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
        </TabsContent>

        {isAdminOrSecretaria && (
          <TabsContent value="invitaciones" className="mt-4">
            <InvitacionesTab />
          </TabsContent>
        )}
      </Tabs>

      {/* Dialogs */}
      <SocioFormDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        mode="create"
      />

      {editId && (
        <SocioFormDialog
          open={!!editId}
          onClose={() => setEditId(null)}
          mode="edit"
          socioId={editId}
        />
      )}

      {detailId && (
        <SocioDetailDialog
          open={!!detailId}
          onClose={() => setDetailId(null)}
          socioId={detailId}
        />
      )}

      <CsvHabilitacionDialog open={csvOpen} onClose={() => setCsvOpen(false)} />
      <CsvImportSociosDialog open={importOpen} onClose={() => setImportOpen(false)} />
    </div>
  )
}

// ─── Dialog: importar socios desde CSV ───────────────────────────────────────

type ImportStep = "upload" | "preview" | "done"

function CsvImportSociosDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [step, setStep] = useState<ImportStep>("upload")
  const [preview, setPreview] = useState<CsvImportPreviewResponse | null>(null)
  const [selectedFilas, setSelectedFilas] = useState<Set<number>>(new Set())

  const previewMutation  = useCsvImportPreview()
  const confirmarMutation = useCsvImportConfirmar()

  const handleClose = () => {
    setStep("upload")
    setPreview(null)
    setSelectedFilas(new Set())
    previewMutation.reset()
    confirmarMutation.reset()
    onClose()
  }

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const result = await previewMutation.mutateAsync(file)
      setPreview(result)
      setSelectedFilas(new Set(result.validas.map((f) => f.fila)))
      setStep("preview")
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg ?? "Error al procesar el archivo")
    }
    e.target.value = ""
  }

  const toggleFila = (fila: number) => {
    setSelectedFilas((prev) => {
      const next = new Set(prev)
      next.has(fila) ? next.delete(fila) : next.add(fila)
      return next
    })
  }

  const handleConfirmar = async () => {
    if (!preview) return
    const filasAImportar = preview.validas.filter((f) => selectedFilas.has(f.fila))
    if (filasAImportar.length === 0) {
      toast.error("No hay filas seleccionadas para importar")
      return
    }
    try {
      await confirmarMutation.mutateAsync(filasAImportar)
      setStep("done")
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg ?? "Error al confirmar la importación")
    }
  }

  const result = confirmarMutation.data

  return (
    <Dialog open={open} onOpenChange={(v) => !v && handleClose()}>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Importar socios desde CSV</DialogTitle>
        </DialogHeader>

        {step === "upload" && (
          <div className="space-y-4">
            <div className="rounded-lg border border-border bg-muted/30 p-4 space-y-2 text-sm text-muted-foreground">
              <p className="font-medium text-foreground">Formato esperado del CSV:</p>
              <p>El archivo debe tener encabezado con estas columnas (obligatorias: <strong>cedula, nombre, apellido, correo</strong>):</p>
              <code className="block rounded bg-muted px-3 py-2 font-mono text-xs">
                cedula,nombre,apellido,correo,telefono,tipoSocio,nivelTecnico
              </code>
              <p>Los campos <em>telefono</em>, <em>tipoSocio</em> y <em>nivelTecnico</em> son opcionales.</p>
              <p>Cada socio recibirá un correo de invitación para completar su registro (fecha de nacimiento, dirección, contacto de emergencia y contraseña).</p>
            </div>

            <label className="flex flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-border bg-background p-10 cursor-pointer hover:bg-muted/30 transition-colors">
              <Upload className="h-8 w-8 text-muted-foreground" />
              <span className="text-sm font-medium text-foreground">Seleccionar archivo CSV</span>
              <span className="text-xs text-muted-foreground">Máximo 500 KB · UTF-8</span>
              <input type="file" accept=".csv" className="hidden" onChange={handleFileChange} disabled={previewMutation.isPending} />
            </label>

            {previewMutation.isPending && (
              <div className="flex items-center justify-center gap-2 text-sm text-muted-foreground py-4">
                <div className="h-4 w-4 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                Procesando archivo...
              </div>
            )}
          </div>
        )}

        {step === "preview" && preview && (
          <div className="space-y-4">
            {/* Resumen */}
            <div className="grid grid-cols-3 gap-3 text-center">
              <div className="rounded-lg border border-border p-3">
                <p className="text-2xl font-bold text-foreground">{preview.totalFilas}</p>
                <p className="text-xs text-muted-foreground mt-0.5">Total filas</p>
              </div>
              <div className="rounded-lg border border-border p-3">
                <p className="text-2xl font-bold text-primary">{preview.validas.length}</p>
                <p className="text-xs text-muted-foreground mt-0.5">Válidas</p>
              </div>
              <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3">
                <p className="text-2xl font-bold text-destructive">{preview.errores.length}</p>
                <p className="text-xs text-muted-foreground mt-0.5">Con errores</p>
              </div>
            </div>

            {/* Errores */}
            {preview.errores.length > 0 && (
              <div className="space-y-1">
                <p className="text-sm font-medium text-destructive flex items-center gap-1.5">
                  <AlertCircle className="h-4 w-4" /> Filas con errores (no se importarán)
                </p>
                <div className="max-h-36 overflow-y-auto rounded-lg border border-destructive/30 bg-destructive/5 divide-y divide-destructive/20">
                  {preview.errores.map((e) => (
                    <div key={e.fila} className="px-3 py-2 text-xs">
                      <span className="font-mono text-muted-foreground mr-2">Fila {e.fila}</span>
                      <span className="text-foreground">{e.cedula || e.correo}</span>
                      <span className="text-destructive ml-2">— {e.motivo}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Filas válidas */}
            {preview.validas.length > 0 && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-medium text-foreground">Socios a importar</p>
                  <div className="flex gap-2 text-xs">
                    <button className="text-primary hover:underline" onClick={() => setSelectedFilas(new Set(preview.validas.map((f) => f.fila)))}>
                      Seleccionar todo
                    </button>
                    <span className="text-muted-foreground">·</span>
                    <button className="text-muted-foreground hover:underline" onClick={() => setSelectedFilas(new Set())}>
                      Deseleccionar todo
                    </button>
                  </div>
                </div>
                <div className="max-h-64 overflow-y-auto rounded-lg border border-border divide-y divide-border">
                  {preview.validas.map((f) => (
                    <label key={f.fila} className={`flex items-center gap-3 px-3 py-2.5 cursor-pointer hover:bg-muted/30 transition-colors ${selectedFilas.has(f.fila) ? "" : "opacity-50"}`}>
                      <input
                        type="checkbox"
                        checked={selectedFilas.has(f.fila)}
                        onChange={() => toggleFila(f.fila)}
                        className="h-4 w-4 rounded border-input accent-primary"
                      />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium truncate">{f.nombre} {f.apellido}</p>
                        <p className="text-xs text-muted-foreground">{f.correo} · {f.cedula}</p>
                      </div>
                      <div className="shrink-0 text-right space-y-0.5">
                        {f.tipoSocio && <p className="text-xs text-muted-foreground">{f.tipoSocio}</p>}
                        {f.nivelTecnico && <p className="text-xs text-muted-foreground">{f.nivelTecnico}</p>}
                      </div>
                    </label>
                  ))}
                </div>
              </div>
            )}

            <div className="flex justify-between gap-2 pt-2">
              <Button variant="outline" onClick={() => { setStep("upload"); setPreview(null) }}>
                <X className="h-4 w-4 mr-1.5" /> Cancelar
              </Button>
              <Button
                onClick={handleConfirmar}
                disabled={confirmarMutation.isPending || selectedFilas.size === 0}
              >
                {confirmarMutation.isPending ? (
                  <span className="flex items-center gap-2">
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
                    Enviando invitaciones...
                  </span>
                ) : (
                  `Importar ${selectedFilas.size} socio${selectedFilas.size !== 1 ? "s" : ""}`
                )}
              </Button>
            </div>
          </div>
        )}

        {step === "done" && result && (
          <div className="space-y-4 py-4 text-center">
            <CheckCircle2 className="mx-auto h-12 w-12 text-primary" />
            <div>
              <p className="text-lg font-semibold text-foreground">Importación completada</p>
              <p className="text-sm text-muted-foreground mt-1">
                Se enviaron <strong>{result.importados}</strong> invitaciones correctamente.
                {result.omitidos > 0 && ` ${result.omitidos} filas omitidas por errores.`}
              </p>
            </div>
            {result.errores.length > 0 && (
              <div className="text-left rounded-lg border border-destructive/30 bg-destructive/5 divide-y divide-destructive/20 max-h-36 overflow-y-auto">
                {result.errores.map((e) => (
                  <div key={e.fila} className="px-3 py-2 text-xs">
                    <span className="text-muted-foreground mr-2">Fila {e.fila}</span>
                    <span>{e.cedula}</span>
                    <span className="text-destructive ml-2">— {e.motivo}</span>
                  </div>
                ))}
              </div>
            )}
            <Button onClick={handleClose}>Cerrar</Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
