import { useState } from "react"
import { useContactosList, useCreateContacto, useUpdateContacto, useDeleteContacto } from "@/hooks/use-contactos"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { toast } from "sonner"
import { Plus, Search, ChevronLeft, ChevronRight, Pencil, Trash2, Phone, Mail, Users } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import type { Contacto, CreateContactoRequest } from "@/types/contactos"

const TIPO_LABELS: Record<string, string> = {
  GUIA:       "Guía",
  TRANSPORTE: "Transporte",
  REFUGIO:    "Refugio",
  ALMUERZO:   "Almuerzo",
}

const TIPO_VARIANTS: Record<string, "default" | "secondary" | "outline" | "destructive"> = {
  GUIA:       "default",
  TRANSPORTE: "secondary",
  REFUGIO:    "outline",
  ALMUERZO:   "outline",
}

const EMPTY_FORM: CreateContactoRequest = {
  nombre: "",
  telefono: "",
  correo: "",
  notas: "",
}

export function ContactosPage() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState("")
  const [searchDebounced, setSearchDebounced] = useState("")

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<Contacto | null>(null)
  const [form, setForm] = useState<CreateContactoRequest>(EMPTY_FORM)

  const { data: contactosPage, isLoading } = useContactosList({
    page,
    q: searchDebounced || undefined,
    size: 20,
  })

  const createMutation = useCreateContacto()
  const updateMutation = useUpdateContacto()
  const deleteMutation = useDeleteContacto()

  const isPending = createMutation.isPending || updateMutation.isPending

  function handleSearch() {
    setSearchDebounced(search)
    setPage(0)
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Enter") handleSearch()
  }

  function openCreate() {
    setEditing(null)
    setForm(EMPTY_FORM)
    setDialogOpen(true)
  }

  function openEdit(c: Contacto) {
    setEditing(c)
    setForm({
      nombre: c.nombre,
      telefono: c.telefono ?? "",
      correo: c.correo ?? "",
      notas: c.notas ?? "",
    })
    setDialogOpen(true)
  }

  function closeDialog() {
    setDialogOpen(false)
    setEditing(null)
    setForm(EMPTY_FORM)
  }

  async function handleSave() {
    if (!form.nombre.trim()) {
      toast.error("El nombre es requerido")
      return
    }

    const payload: CreateContactoRequest = {
      nombre: form.nombre.trim(),
      telefono: form.telefono?.trim() || undefined,
      correo: form.correo?.trim() || undefined,
      notas: form.notas?.trim() || undefined,
    }

    try {
      if (editing) {
        await updateMutation.mutateAsync({ id: editing.id, data: payload })
        toast.success("Contacto actualizado")
      } else {
        await createMutation.mutateAsync(payload)
        toast.success("Contacto creado")
      }
      closeDialog()
    } catch (error) { console.error(error);
      toast.error("Error al guardar el contacto")
    }
  }

  async function handleDelete(c: Contacto) {
    if (!confirm(`¿Eliminar el contacto "${c.nombre}"?`)) return
    try {
      await deleteMutation.mutateAsync(c.id)
      toast.success("Contacto eliminado")
    } catch (error) { console.error(error);
      toast.error("Error al eliminar el contacto")
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Contactos</h1>
          <p className="text-muted-foreground">
            {contactosPage
              ? `${contactosPage.page.totalElements} contacto${contactosPage.page.totalElements !== 1 ? "s" : ""}`
              : "Cargando..."}
          </p>
        </div>
        <Button onClick={openCreate} className="gap-2">
          <Plus className="h-4 w-4" /> Nuevo contacto
        </Button>
      </div>

      {/* Search */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-teal-500/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-teal-500/10">
              <Search className="h-4 w-4 text-teal-600 dark:text-teal-400" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Buscar contactos</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Filtra por nombre o teléfono</p>
            </div>
          </div>
        </div>
        <div className="p-5">
          <div className="flex gap-3">
            <div className="relative flex-1 max-w-md">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Buscar por nombre o teléfono..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={handleKeyDown}
                className="pl-9"
              />
            </div>
            <Button variant="outline" onClick={handleSearch}>Buscar</Button>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nombre</TableHead>
              <TableHead className="hidden sm:table-cell">Teléfono</TableHead>
              <TableHead className="hidden md:table-cell">Correo</TableHead>
              <TableHead className="hidden lg:table-cell">Usado como</TableHead>
              <TableHead className="text-right">Acciones</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: 5 }).map((_, j) => (
                    <TableCell key={j}>
                      <div className="h-4 w-24 animate-pulse rounded bg-muted" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : contactosPage?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                  <Users className="mx-auto mb-2 h-8 w-8 opacity-30" />
                  No se encontraron contactos
                </TableCell>
              </TableRow>
            ) : (
              contactosPage?.content.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>
                    <p className="font-medium">{c.nombre}</p>
                    {/* Phone visible on small screens */}
                    {c.telefono && (
                      <p className="text-xs text-muted-foreground flex items-center gap-1 sm:hidden">
                        <Phone className="h-3 w-3" />
                        {c.telefono}
                      </p>
                    )}
                  </TableCell>
                  <TableCell className="hidden sm:table-cell text-sm">
                    {c.telefono ? (
                      <span className="flex items-center gap-1 text-muted-foreground">
                        <Phone className="h-3.5 w-3.5" />
                        {c.telefono}
                      </span>
                    ) : (
                      <span className="text-muted-foreground/40">—</span>
                    )}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-sm">
                    {c.correo ? (
                      <span className="flex items-center gap-1 text-muted-foreground">
                        <Mail className="h-3.5 w-3.5" />
                        {c.correo}
                      </span>
                    ) : (
                      <span className="text-muted-foreground/40">—</span>
                    )}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell">
                    {c.tiposContacto.length > 0 ? (
                      <div className="flex flex-wrap gap-1">
                        {c.tiposContacto.map((tipo) => (
                          <Badge key={tipo} variant={TIPO_VARIANTS[tipo] ?? "outline"} className="text-xs">
                            {TIPO_LABELS[tipo] ?? tipo}
                          </Badge>
                        ))}
                      </div>
                    ) : (
                      <span className="text-muted-foreground/40 text-sm">Sin asignar</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEdit(c)}
                        title="Editar"
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleDelete(c)}
                        disabled={deleteMutation.isPending}
                        title="Eliminar"
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {contactosPage && contactosPage.page.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Página {contactosPage.page.number + 1} de {contactosPage.page.totalPages}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={contactosPage.page.number === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="mr-1 h-4 w-4" /> Anterior
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={contactosPage.page.number >= contactosPage.page.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Siguiente <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Create / Edit dialog */}
      <Dialog open={dialogOpen} onOpenChange={(v) => !v && closeDialog()}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>
              {editing ? "Editar contacto" : "Nuevo contacto"}
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">
                Nombre <span className="text-destructive">*</span>
              </label>
              <Input
                placeholder="Nombre completo"
                value={form.nombre}
                onChange={(e) => setForm((f) => ({ ...f, nombre: e.target.value }))}
                autoFocus
              />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Teléfono</label>
              <Input
                placeholder="+593 99 999 9999"
                value={form.telefono ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, telefono: e.target.value }))}
              />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Correo electrónico</label>
              <Input
                type="email"
                placeholder="contacto@ejemplo.com"
                value={form.correo ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, correo: e.target.value }))}
              />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Notas</label>
              <Input
                placeholder="Información adicional..."
                value={form.notas ?? ""}
                onChange={(e) => setForm((f) => ({ ...f, notas: e.target.value }))}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={closeDialog} disabled={isPending}>
              Cancelar
            </Button>
            <Button onClick={handleSave} disabled={isPending}>
              {isPending ? "Guardando..." : editing ? "Guardar cambios" : "Crear contacto"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
