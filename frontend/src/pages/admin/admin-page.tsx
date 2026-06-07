import { useState, useRef, useMemo } from "react"
import {
  useUsuariosAuth, useDesbloquearUsuario, useForzarCierreSesion, useCambiarEstadoAcceso,
  useConfiguracion, useActualizarConfig,
  useAdminLegalDocs, useAdminDocAcceptances, useAdminPendingAcceptances, useAdminBlockedSocios,
  useCreateLegalDoc, useCreateNewVersion, useActivateLegalDoc, useLegalDocFull,
} from "@/hooks/use-admin"
import type { LegalDocSummary } from "@/hooks/use-admin"
import { useAuthStore } from "@/stores/auth-store"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { toast } from "sonner"
import {
  Shield, Search, LockOpen, Lock,
  ShieldCheck, ShieldAlert, Settings, ClipboardList, Users, LogOut,
  FileText, Plus, CheckCircle2, XCircle, Eye, GitBranch, Zap, AlertTriangle, ChevronDown, Loader2,
} from "lucide-react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import type { Components } from "react-markdown"
import { cn } from "@/lib/utils"
import { AuditoriaTab, SecurityTab } from "@/pages/auditoria/auditoria-page"
import type { UsuarioAuthSummary } from "@/types/admin"
import { ESTADOS_ACCESO } from "@/types/admin"

// ─── Helper ───────────────────────────────────────────────────────────────────

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString("es-EC", {
    day: "2-digit", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  })
}

// ─── Usuarios Auth ───────────────────────────────────────────────────────────

const ESTADO_ACCESO_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ACTIVE:           "default",
  BLOCKED:          "destructive",
  EX_MEMBER:        "secondary",
  PENDING_REGISTER: "outline",
  DISABLED:         "destructive",
}

function UsuariosAuthTab() {
  const userRole = useAuthStore((s) => s.user?.rol?.toUpperCase() ?? "")
  const isAdmin = userRole === "ADMIN"
  const isAdminOrSecretaria = isAdmin || userRole === "SECRETARIA"
  const { data: usuarios, isLoading } = useUsuariosAuth()
  const desbloquearMutation = useDesbloquearUsuario()
  const cerrarSesionMutation = useForzarCierreSesion()
  const cambiarEstadoMutation = useCambiarEstadoAcceso()

  const [search, setSearch] = useState("")

  const filtered = (usuarios ?? []).filter((u) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      u.username.toLowerCase().includes(q) ||
      u.nombre.toLowerCase().includes(q) ||
      u.apellido.toLowerCase().includes(q) ||
      u.correo.toLowerCase().includes(q)
    )
  })

  const handleDesbloquear = async (u: UsuarioAuthSummary) => {
    if (!confirm(`¿Desbloquear la cuenta de ${u.nombre} ${u.apellido} (${u.username})?`)) return
    try {
      await desbloquearMutation.mutateAsync(u.socioId)
      toast.success(`Cuenta de ${u.username} desbloqueada`)
    } catch (error) { console.error(error);
      toast.error("Error al desbloquear")
    }
  }

  const handleCambiarEstado = async (u: UsuarioAuthSummary, codigo: string) => {
    const estado = ESTADOS_ACCESO.find((e) => e.codigo === codigo)
    if (!confirm(`¿Cambiar el estado de acceso de ${u.nombre} ${u.apellido} a "${estado?.nombre}"?`)) return
    try {
      await cambiarEstadoMutation.mutateAsync({ socioId: u.socioId, codigo })
      toast.success(`Estado de acceso actualizado a ${estado?.nombre}`)
    } catch (error) { console.error(error);
      toast.error("Error al cambiar el estado de acceso")
    }
  }

  const handleCerrarSesion = async (u: UsuarioAuthSummary) => {
    if (!confirm(`¿Cerrar todas las sesiones activas de ${u.nombre} ${u.apellido} (${u.username})?\n\nEl usuario perderá el acceso inmediatamente en todos sus dispositivos. Usa esta acción solo en casos de seguridad (cuenta comprometida, baja del socio, actividad sospechosa).`)) return
    try {
      await cerrarSesionMutation.mutateAsync(u.socioId)
      toast.success(`Sesiones de ${u.username} cerradas`)
    } catch (error) { console.error(error);
      toast.error("Error al cerrar las sesiones")
    }
  }

  return (
    <div className="space-y-4">
      {/* Búsqueda local */}
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <Search className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-foreground">Buscar cuentas</h2>
              <p className="text-xs text-muted-foreground mt-0.5">Filtra por nombre, usuario o correo</p>
            </div>
          </div>
        </div>
        <div className="p-5">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Buscar por nombre, usuario, correo..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Socio</TableHead>
              <TableHead>Usuario</TableHead>
              <TableHead className="text-center">2FA</TableHead>
              <TableHead className="text-center">Intentos</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead className="hidden md:table-cell">Último login</TableHead>
              {isAdminOrSecretaria && <TableHead className="text-right">Acciones</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {Array.from({ length: isAdmin ? 7 : 6 }).map((_, j) => (
                    <TableCell key={j}><div className="h-4 w-24 animate-pulse rounded bg-muted" /></TableCell>
                  ))}
                </TableRow>
              ))
            ) : filtered.length === 0 ? (
              <TableRow>
                <TableCell colSpan={isAdmin ? 7 : 6} className="py-8 text-center text-muted-foreground">
                  {search ? "No se encontraron usuarios" : "No hay usuarios registrados"}
                </TableCell>
              </TableRow>
            ) : (
              filtered.map((u) => (
                <TableRow key={u.socioId} className={u.estadoAcceso !== "ACTIVE" ? "bg-destructive/5" : ""}>
                  <TableCell>
                    <div>
                      <p className="font-medium text-sm">{u.nombre} {u.apellido}</p>
                      <p className="text-xs text-muted-foreground">{u.correo}</p>
                    </div>
                  </TableCell>
                  <TableCell className="font-mono text-sm">{u.username}</TableCell>
                  <TableCell className="text-center">
                    {u.totpEnabled
                      ? <ShieldCheck className="h-4 w-4 text-primary mx-auto" aria-label="2FA activo" />
                      : <span className="text-xs text-muted-foreground">—</span>
                    }
                  </TableCell>
                  <TableCell className="text-center">
                    <span className={`font-mono text-sm ${u.failedAttempts > 0 ? "text-destructive font-semibold" : ""}`}>
                      {u.failedAttempts}
                    </span>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Badge variant={ESTADO_ACCESO_VARIANT[u.estadoAcceso] ?? "outline"} className="text-xs">
                        {u.estadoAccesoNombre}
                      </Badge>
                      {u.loginBlocked && (
                        <Badge variant="destructive" className="gap-1 text-xs">
                          <Lock className="h-3 w-3" /> Brute-force
                        </Badge>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-xs text-muted-foreground">
                    {u.lastLogin ? formatDateTime(u.lastLogin) : "Nunca"}
                  </TableCell>
                  {isAdminOrSecretaria && (
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-2">
                        {/* Cambiar estado de acceso */}
                        <select
                          value={u.estadoAcceso}
                          onChange={(e) => handleCambiarEstado(u, e.target.value)}
                          disabled={cambiarEstadoMutation.isPending}
                          className="h-8 rounded-md border border-input bg-background px-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
                        >
                          {ESTADOS_ACCESO.map((e) => (
                            <option key={e.codigo} value={e.codigo}>{e.nombre}</option>
                          ))}
                        </select>
                        {/* Desbloquear brute-force */}
                        {isAdmin && (u.loginBlocked || u.failedAttempts > 0) && (
                          <Button
                            variant="outline"
                            size="sm"
                            className="gap-1.5 text-primary border-primary/30 hover:bg-primary/10"
                            onClick={() => handleDesbloquear(u)}
                            disabled={desbloquearMutation.isPending}
                            title="Resetear intentos fallidos"
                          >
                            <LockOpen className="h-3.5 w-3.5" />
                          </Button>
                        )}
                        {isAdminOrSecretaria && (
                          <Button
                            variant="outline"
                            size="sm"
                            className="gap-1.5 text-orange-600 border-orange-300 hover:bg-orange-50 dark:text-orange-400 dark:border-orange-800 dark:hover:bg-orange-950/30"
                            onClick={() => handleCerrarSesion(u)}
                            disabled={cerrarSesionMutation.isPending}
                            title="Cerrar todas las sesiones activas"
                          >
                            <LogOut className="h-3.5 w-3.5" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {!isLoading && (
        <p className="text-xs text-muted-foreground">
          {filtered.length} {filtered.length === 1 ? "usuario" : "usuarios"}
          {search && ` (filtrado de ${usuarios?.length ?? 0})`}
        </p>
      )}
    </div>
  )
}

// ─── Configuración del sistema ───────────────────────────────────────────────

const CLAVE_BLOQUEO              = "BLOQUEAR_INSCRIPCION_INHABILITADOS"
const CLAVE_BLOQUEO_LICENCIA     = "BLOQUEAR_INSCRIPCION_LICENCIA"
const CLAVE_BLOQUEO_REINSCRIPCION = "BLOQUEAR_INSCRIPCION_REINSCRIPCION"
const CLAVE_INTENTOS  = "MAX_INTENTOS_LOGIN"
const CLAVE_HORAS     = "HORAS_BLOQUEO_LOGIN"

function NumericConfigRow({
  label,
  description,
  clave,
  valor,
  min = 1,
}: {
  label: string
  description: string
  clave: string
  valor: string | undefined
  min?: number
}) {
  const actualizarMutation = useActualizarConfig()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState("")
  const inputRef = useRef<HTMLInputElement>(null)

  const startEdit = () => {
    setDraft(valor ?? "")
    setEditing(true)
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  const cancel = () => setEditing(false)

  const save = async () => {
    const num = parseInt(draft, 10)
    if (isNaN(num) || num < min) {
      toast.error(`El valor debe ser un número mayor o igual a ${min}`)
      return
    }
    if (String(num) === valor) { setEditing(false); return }
    try {
      await actualizarMutation.mutateAsync({ clave, valor: String(num) })
      toast.success(`${label} actualizado a ${num}`)
      setEditing(false)
    } catch (error) { console.error(error);
      toast.error("Error al actualizar la configuración")
    }
  }

  return (
    <div className="flex items-center justify-between gap-4">
      <div className="space-y-1">
        <Label className="text-sm font-medium">{label}</Label>
        <p className="text-xs text-muted-foreground">{description}</p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        {editing ? (
          <>
            <Input
              ref={inputRef}
              type="number"
              min={min}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") save(); if (e.key === "Escape") cancel() }}
              className="w-20 h-8 text-center"
            />
            <Button size="sm" onClick={save} disabled={actualizarMutation.isPending}>Guardar</Button>
            <Button size="sm" variant="outline" onClick={cancel}>Cancelar</Button>
          </>
        ) : (
          <>
            <span className="font-mono font-semibold text-sm w-10 text-center">{valor ?? "—"}</span>
            <Button size="sm" variant="outline" onClick={startEdit}>Editar</Button>
          </>
        )}
      </div>
    </div>
  )
}

function ToggleConfigRow({
  label,
  descriptionOn,
  descriptionOff,
  clave,
  valor,
}: {
  label: string
  descriptionOn: string
  descriptionOff: string
  clave: string
  valor: string | undefined
}) {
  const actualizarMutation = useActualizarConfig()
  const activo = valor?.toLowerCase() === "true"

  const handleToggle = async () => {
    try {
      await actualizarMutation.mutateAsync({ clave, valor: String(!activo) })
      toast.success(!activo ? descriptionOn : descriptionOff)
    } catch (error) { console.error(error);
      toast.error("Error al actualizar la configuración")
    }
  }

  return (
    <div className="flex items-center justify-between gap-4">
      <div className="space-y-1">
        <div className="flex items-center gap-3">
          <Label className="text-sm font-medium">{label}</Label>
          <Button
            size="sm"
            variant={activo ? "destructive" : "default"}
            onClick={handleToggle}
            disabled={actualizarMutation.isPending}
          >
            {activo ? "Desactivar" : "Activar"}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          {activo ? descriptionOn : descriptionOff}
        </p>
      </div>
    </div>
  )
}

function ConfiguracionTab() {
  const { data: configs, isLoading } = useConfiguracion()

  const getValue = (clave: string) => configs?.find((c) => c.clave === clave)?.valor

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
        <div className="border-b border-border/60 bg-gradient-to-r from-primary/5 to-transparent px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
              <Settings className="h-4 w-4 text-primary" />
            </div>
            <div>
              <p className="text-base font-semibold text-foreground">Parámetros del sistema</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                Cambios aplicados de inmediato. Cada modificación queda registrada en la auditoría.
              </p>
            </div>
          </div>
        </div>
        <div className="p-5 space-y-5">

        {isLoading ? (
          <div className="space-y-3">
            {[1,2,3,4,5].map((i) => <div key={i} className="h-10 animate-pulse rounded bg-muted" />)}
          </div>
        ) : (
          <div className="space-y-5 divide-y divide-border">
            <ToggleConfigRow
              label="Bloquear inscripciones de socios inhabilitados"
              descriptionOn="Activo — los socios inhabilitados no pueden inscribirse en salidas."
              descriptionOff="Inactivo — los socios inhabilitados pueden inscribirse en salidas."
              clave={CLAVE_BLOQUEO}
              valor={getValue(CLAVE_BLOQUEO)}
            />

            <div className="pt-4">
              <ToggleConfigRow
                label="Bloquear inscripciones de socios en Licencia"
                descriptionOn="Activo — los socios en Licencia no pueden inscribirse en salidas."
                descriptionOff="Inactivo — los socios en Licencia pueden inscribirse en salidas."
                clave={CLAVE_BLOQUEO_LICENCIA}
                valor={getValue(CLAVE_BLOQUEO_LICENCIA)}
              />
            </div>

            <div className="pt-4">
              <ToggleConfigRow
                label="Bloquear inscripciones de socios en Re-inscripción"
                descriptionOn="Activo — los socios en Re-inscripción no pueden inscribirse en salidas."
                descriptionOff="Inactivo — los socios en Re-inscripción pueden inscribirse en salidas."
                clave={CLAVE_BLOQUEO_REINSCRIPCION}
                valor={getValue(CLAVE_BLOQUEO_REINSCRIPCION)}
              />
            </div>

            <div className="pt-4">
              <NumericConfigRow
                label="Máximo de intentos de login fallidos"
                description="La cuenta se bloquea automáticamente al superar este número de intentos consecutivos fallidos."
                clave={CLAVE_INTENTOS}
                valor={getValue(CLAVE_INTENTOS)}
                min={1}
              />
            </div>

            <div className="pt-4">
              <NumericConfigRow
                label="Horas de bloqueo de cuenta"
                description="Tiempo que permanece bloqueada una cuenta tras superar el máximo de intentos fallidos."
                clave={CLAVE_HORAS}
                valor={getValue(CLAVE_HORAS)}
                min={1}
              />
            </div>
          </div>
        )}
        </div>
      </div>
    </div>
  )
}

// ─── Documentos Legales Tab ───────────────────────────────────────────────────

const STAGE_LABEL: Record<string, string> = {
  REGISTRATION: "Registro",
  PROFILE_COMPLETION: "Completar perfil",
  ACTIVITY_ENROLLMENT: "Inscripción actividades",
}

function formatDate(iso: string | null) {
  if (!iso) return "—"
  return new Date(iso).toLocaleDateString("es-EC", { day: "numeric", month: "short", year: "numeric" })
}

function formatDateTimeDoc(iso: string) {
  return new Date(iso).toLocaleString("es-EC", {
    day: "2-digit", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  })
}

const mdComponents: Components = {
  h1: ({ children }) => <h1 className="text-base font-bold text-foreground mt-4 mb-1 uppercase tracking-wide">{children}</h1>,
  h2: ({ children }) => <h2 className="text-sm font-semibold text-primary mt-3 mb-1">{children}</h2>,
  h3: ({ children }) => <h3 className="text-xs font-semibold text-foreground mt-2 mb-1 uppercase tracking-wide">{children}</h3>,
  p:  ({ children }) => <p className="text-sm text-muted-foreground leading-relaxed mb-2">{children}</p>,
  ul: ({ children }) => <ul className="list-disc list-outside pl-5 mb-2 space-y-0.5">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal list-outside pl-5 mb-2 space-y-0.5">{children}</ol>,
  li: ({ children }) => <li className="text-sm text-muted-foreground">{children}</li>,
  strong: ({ children }) => <strong className="font-semibold text-foreground">{children}</strong>,
  a: ({ children, href }) => <a href={href} className="text-primary underline underline-offset-2">{children}</a>,
}

// ── Create Doc Modal ──────────────────────────────────────────────────────────

function CreateDocModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const mutation = useCreateLegalDoc()
  const [form, setForm] = useState({
    code: "", title: "", description: "", documentType: "POLICY",
    requiredStage: "REGISTRATION", content: "",
    required: true, requiresReacceptanceOnNewVersion: true,
  })

  const set = (k: keyof typeof form) => (v: string | boolean) =>
    setForm((prev) => ({ ...prev, [k]: v }))

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.code || !form.title || !form.content) {
      toast.error("Código, título y contenido son obligatorios")
      return
    }
    try {
      await mutation.mutateAsync(form)
      toast.success("Documento creado correctamente")
      onClose()
      setForm({ code: "", title: "", description: "", documentType: "POLICY",
        requiredStage: "REGISTRATION", content: "", required: true, requiresReacceptanceOnNewVersion: true })
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg || "Error al crear el documento")
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Nuevo documento legal</DialogTitle>
          <DialogDescription>El documento se creará como versión 1, inactivo. Actívalo cuando esté listo.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4 pt-2">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Código <span className="text-destructive">*</span></Label>
              <Input value={form.code} onChange={(e) => set("code")(e.target.value.toUpperCase())}
                placeholder="PRIVACY_POLICY" className="font-mono" />
            </div>
            <div className="space-y-1.5">
              <Label>Tipo</Label>
              <Select value={form.documentType} onValueChange={set("documentType")}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="POLICY">Política</SelectItem>
                  <SelectItem value="WAIVER">Exoneración</SelectItem>
                  <SelectItem value="CONSENT">Consentimiento</SelectItem>
                  <SelectItem value="TERMS">Términos</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>Título <span className="text-destructive">*</span></Label>
            <Input value={form.title} onChange={(e) => set("title")(e.target.value)} placeholder="Política de Privacidad" />
          </div>

          <div className="space-y-1.5">
            <Label>Descripción</Label>
            <Input value={form.description} onChange={(e) => set("description")(e.target.value)}
              placeholder="Breve descripción del propósito del documento" />
          </div>

          <div className="space-y-1.5">
            <Label>Etapa requerida</Label>
            <Select value={form.requiredStage} onValueChange={set("requiredStage")}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="REGISTRATION">Registro</SelectItem>
                <SelectItem value="PROFILE_COMPLETION">Completar perfil</SelectItem>
                <SelectItem value="ACTIVITY_ENROLLMENT">Inscripción a actividades</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="flex gap-6">
            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={form.required}
                onChange={(e) => set("required")(e.target.checked)} className="accent-primary" />
              <span className="text-sm">Obligatorio</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={form.requiresReacceptanceOnNewVersion}
                onChange={(e) => set("requiresReacceptanceOnNewVersion")(e.target.checked)} className="accent-primary" />
              <span className="text-sm">Requiere re-aceptación en nueva versión</span>
            </label>
          </div>

          <div className="space-y-1.5">
            <Label>Contenido (Markdown) <span className="text-destructive">*</span></Label>
            <textarea
              value={form.content}
              onChange={(e) => set("content")(e.target.value)}
              rows={10}
              placeholder="# Título&#10;&#10;Texto del documento en Markdown..."
              className="w-full rounded-lg border border-input bg-background/50 px-3 py-2 text-sm font-mono focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-y"
            />
          </div>

          <div className="flex gap-3 pt-2 justify-end">
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "Creando..." : "Crear documento"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ── New Version Modal ─────────────────────────────────────────────────────────

function NewVersionModal({ doc, onClose }: { doc: LegalDocSummary | null; onClose: () => void }) {
  const mutation = useCreateNewVersion()
  const docId = doc?.id ?? null
  const { data: docFull, isLoading: loadingFull } = useLegalDocFull(docId)
  const [editedContent, setEditedContent] = useState<string | null>(null)
  const [prevDocId, setPrevDocId] = useState<string | null>(null)
  const [viewMode, setViewMode] = useState<"editor" | "split" | "preview">("split")

  // Reset edited content when the target doc changes (React's "storing previous renders" pattern)
  if (prevDocId !== docId) {
    setPrevDocId(docId)
    setEditedContent(null)
  }

  const content = editedContent ?? docFull?.content ?? ""

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!doc || !content.trim()) return
    try {
      await mutation.mutateAsync({ id: doc.id, content })
      toast.success(`Nueva versión de "${doc.title}" creada`)
      setEditedContent(null)
      onClose()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg || "Error al crear la nueva versión")
    }
  }

  return (
    <Dialog open={!!doc} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-5xl max-h-[92vh] flex flex-col gap-0 p-0">
        <div className="px-6 pt-6 pb-4 border-b border-border">
          <DialogTitle>Nueva versión — {doc?.title}</DialogTitle>
          <DialogDescription className="mt-1">
            Se creará la v{doc ? doc.version + 1 : ""} como inactiva. El contenido actual (v{doc?.version}) está precargado.
          </DialogDescription>
        </div>
        <form onSubmit={handleSubmit} className="flex flex-col flex-1 gap-3 p-6 min-h-0 overflow-hidden">
          {/* View mode toggle */}
          <div className="flex items-center gap-1 p-1 bg-muted/50 rounded-lg w-fit self-start">
            {(["editor", "split", "preview"] as const).map((mode) => (
              <button
                key={mode}
                type="button"
                onClick={() => setViewMode(mode)}
                className={cn(
                  "rounded-md px-3 py-1 text-xs font-bold transition-all",
                  viewMode === mode
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:bg-background/50 hover:text-foreground",
                )}
              >
                {mode === "editor" ? "Editor" : mode === "split" ? "Dividido" : "Vista previa"}
              </button>
            ))}
          </div>

          {/* Editor / Split / Preview panes */}
          <div className={cn("flex gap-3 min-h-0")} style={{ height: 440 }}>
            {(viewMode === "editor" || viewMode === "split") && (
              <div className={cn("flex flex-col min-h-0", viewMode === "split" ? "w-1/2" : "w-full")}>
                <Label className="mb-1.5 text-xs text-muted-foreground font-medium">Markdown</Label>
                {loadingFull && editedContent === null ? (
                  <div className="flex-1 flex items-center justify-center rounded-lg border border-input bg-background/50">
                    <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                  </div>
                ) : (
                  <textarea
                    value={content}
                    onChange={(e) => setEditedContent(e.target.value)}
                    placeholder={"# Título\n\nTexto del documento en Markdown..."}
                    className="flex-1 w-full rounded-lg border border-input bg-background/50 px-3 py-2 text-sm font-mono focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
                  />
                )}
              </div>
            )}
            {(viewMode === "preview" || viewMode === "split") && (
              <div className={cn("flex flex-col min-h-0", viewMode === "split" ? "w-1/2" : "w-full")}>
                <Label className="mb-1.5 text-xs text-muted-foreground font-medium">Vista previa</Label>
                <div className="flex-1 overflow-y-auto rounded-lg border border-border bg-background/30 px-4 py-3">
                  {content.trim() ? (
                    <ReactMarkdown remarkPlugins={[remarkGfm]} components={mdComponents}>
                      {content}
                    </ReactMarkdown>
                  ) : (
                    <p className="text-xs text-muted-foreground italic">Sin contenido aún…</p>
                  )}
                </div>
              </div>
            )}
          </div>

          <div className="flex gap-3 justify-end pt-1">
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={mutation.isPending || !content.trim()}>
              {mutation.isPending ? "Creando..." : "Crear versión"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ── Acceptances Dialog ────────────────────────────────────────────────────────

function AcceptancesDialog({ doc, onClose }: { doc: LegalDocSummary | null; onClose: () => void }) {
  const { data, isLoading } = useAdminDocAcceptances(doc?.id ?? null)

  return (
    <Dialog open={!!doc} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Aceptaciones — {doc?.title}</DialogTitle>
          <DialogDescription>{doc?.code} · v{doc?.version}</DialogDescription>
        </DialogHeader>
        {isLoading ? (
          <div className="space-y-2 py-4">
            {[...Array(3)].map((_, i) => <div key={i} className="h-10 animate-pulse rounded-lg bg-muted" />)}
          </div>
        ) : (data ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">Ningún socio ha aceptado este documento aún.</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Socio</TableHead>
                  <TableHead>Cédula</TableHead>
                  <TableHead>Versión</TableHead>
                  <TableHead>Fecha</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(data ?? []).map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="font-medium">{a.socioNombreCompleto}</TableCell>
                    <TableCell className="font-mono text-xs">{a.socioCedula}</TableCell>
                    <TableCell>v{a.documentVersion}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{formatDateTimeDoc(a.acceptedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

// ── Document Row ──────────────────────────────────────────────────────────────

function DocAdminRow({ doc, onViewAcceptances, onNewVersion, historyCount, historyOpen, onToggleHistory }: {
  doc: LegalDocSummary
  onViewAcceptances: () => void
  onNewVersion: () => void
  historyCount?: number
  historyOpen?: boolean
  onToggleHistory?: () => void
}) {
  const activateMutation = useActivateLegalDoc()

  const handleActivate = async () => {
    try {
      await activateMutation.mutateAsync(doc.id)
      toast.success(`"${doc.title}" activado correctamente`)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg || "Error al activar el documento")
    }
  }

  return (
    <TableRow>
      <TableCell>
        <div className="space-y-0.5">
          <p className="font-mono text-xs font-bold text-foreground">{doc.code}</p>
          <p className="text-sm font-semibold text-foreground">{doc.title}</p>
        </div>
      </TableCell>
      <TableCell>
        <span className="text-xs font-medium px-1.5 py-0.5 rounded bg-muted text-muted-foreground">
          {STAGE_LABEL[doc.requiredStage] ?? doc.requiredStage}
        </span>
      </TableCell>
      <TableCell>
        <span className="text-xs font-semibold">v{doc.version}</span>
      </TableCell>
      <TableCell>
        {doc.active ? (
          <Badge variant="default" className="gap-1 text-xs">
            <CheckCircle2 className="h-3 w-3" /> Activo
          </Badge>
        ) : (
          <Badge variant="outline" className="gap-1 text-xs text-muted-foreground">
            <XCircle className="h-3 w-3" /> Inactivo
          </Badge>
        )}
      </TableCell>
      <TableCell>
        <span className="text-xs text-muted-foreground">{formatDate(doc.approvedAt)}</span>
      </TableCell>
      <TableCell>
        <div className="flex items-center gap-1 flex-wrap">
          <Button size="sm" variant="ghost" className="h-7 gap-1 px-2 text-xs"
            onClick={onViewAcceptances} title="Ver aceptaciones">
            <Eye className="h-3.5 w-3.5" /> Ver
          </Button>
          <Button size="sm" variant="ghost" className="h-7 gap-1 px-2 text-xs"
            onClick={onNewVersion} title="Crear nueva versión">
            <GitBranch className="h-3.5 w-3.5" /> Versionar
          </Button>
          {!doc.active && (
            <Button size="sm" variant="ghost"
              className="h-7 gap-1 px-2 text-xs text-primary hover:text-primary hover:bg-primary/10"
              disabled={activateMutation.isPending}
              onClick={handleActivate} title="Activar esta versión">
              <Zap className="h-3.5 w-3.5" /> Activar
            </Button>
          )}
          {onToggleHistory && (historyCount ?? 0) > 0 && (
            <Button size="sm" variant="ghost"
              className="h-7 gap-1 px-2 text-xs text-muted-foreground"
              onClick={onToggleHistory}
              title="Ver versiones anteriores">
              <ChevronDown className={cn("h-3.5 w-3.5 transition-transform", historyOpen && "rotate-180")} />
              {historyCount}v
            </Button>
          )}
        </div>
      </TableCell>
    </TableRow>
  )
}

// ── Pending + Blocked sections ────────────────────────────────────────────────

function PendingAcceptancesSection() {
  const { data, isLoading } = useAdminPendingAcceptances()
  const [expandedDoc, setExpandedDoc] = useState<string | null>(null)

  if (isLoading) {
    return <div className="space-y-2">{[...Array(2)].map((_, i) => <div key={i} className="h-12 animate-pulse rounded-lg bg-muted" />)}</div>
  }
  if (!data || data.every(d => d.sociosPendientes.length === 0)) {
    return (
      <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 px-4 py-3">
        <CheckCircle2 className="h-4 w-4 text-emerald-500" />
        <span className="text-sm font-semibold text-emerald-600 dark:text-emerald-400">
          Todos los socios tienen los documentos al día
        </span>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {(data ?? []).filter(d => d.sociosPendientes.length > 0).map((doc) => (
        <div key={doc.documentId} className="rounded-xl border border-border/50 bg-card/40 overflow-hidden">
          <button
            type="button"
            className="w-full flex items-center justify-between gap-3 px-4 py-3 hover:bg-accent/20 transition-colors text-left"
            onClick={() => setExpandedDoc(expandedDoc === doc.documentId ? null : doc.documentId)}
          >
            <div className="flex items-center gap-3 min-w-0">
              <AlertTriangle className="h-4 w-4 shrink-0 text-amber-500" />
              <div className="min-w-0">
                <p className="text-sm font-bold text-foreground truncate">{doc.documentTitle}</p>
                <p className="text-xs text-muted-foreground">{doc.documentCode} · v{doc.activeVersion}</p>
              </div>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <Badge variant="secondary" className="text-xs">{doc.sociosPendientes.length} pendiente{doc.sociosPendientes.length !== 1 ? "s" : ""}</Badge>
              <ChevronDown className={cn("h-4 w-4 text-muted-foreground transition-transform", expandedDoc === doc.documentId && "rotate-180")} />
            </div>
          </button>
          {expandedDoc === doc.documentId && (
            <div className="border-t border-border/50">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nombre</TableHead>
                    <TableHead>Cédula</TableHead>
                    <TableHead>Correo</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {doc.sociosPendientes.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell className="font-medium text-sm">{s.nombre} {s.apellido}</TableCell>
                      <TableCell className="font-mono text-xs">{s.cedula}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">{s.correo}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

function BlockedSociosSection() {
  const { data, isLoading } = useAdminBlockedSocios()

  if (isLoading) {
    return <div className="space-y-2">{[...Array(2)].map((_, i) => <div key={i} className="h-10 animate-pulse rounded-lg bg-muted" />)}</div>
  }
  if (!data || data.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 px-4 py-3">
        <CheckCircle2 className="h-4 w-4 text-emerald-500" />
        <span className="text-sm font-semibold text-emerald-600 dark:text-emerald-400">
          Ningún socio está bloqueado para actividades
        </span>
      </div>
    )
  }
  return (
    <div className="rounded-xl border border-border/50 bg-card/40 overflow-hidden">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Nombre</TableHead>
            <TableHead>Cédula</TableHead>
            <TableHead>Correo</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.map((s) => (
            <TableRow key={s.id}>
              <TableCell className="font-medium text-sm">{s.nombre} {s.apellido}</TableCell>
              <TableCell className="font-mono text-xs">{s.cedula}</TableCell>
              <TableCell className="text-xs text-muted-foreground">{s.correo}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

// ── Doc group row ─────────────────────────────────────────────────────────────

function DocGroupRows({ group, onViewAcceptances, onNewVersion }: {
  group: LegalDocSummary[]
  onViewAcceptances: (doc: LegalDocSummary) => void
  onNewVersion: (doc: LegalDocSummary) => void
}) {
  const [historyOpen, setHistoryOpen] = useState(false)
  const latest = group[0]
  const older  = group.slice(1)

  return (
    <>
      <DocAdminRow
        doc={latest}
        onViewAcceptances={() => onViewAcceptances(latest)}
        onNewVersion={() => onNewVersion(latest)}
        historyCount={older.length}
        historyOpen={historyOpen}
        onToggleHistory={older.length > 0 ? () => setHistoryOpen((v) => !v) : undefined}
      />
      {historyOpen && older.map((doc) => (
        <TableRow key={doc.id} className="bg-muted/30 text-muted-foreground">
          <TableCell className="pl-8">
            <div className="space-y-0.5">
              <p className="font-mono text-xs text-muted-foreground">{doc.code}</p>
              <p className="text-xs text-muted-foreground">{doc.title}</p>
            </div>
          </TableCell>
          <TableCell>
            <span className="text-xs px-1.5 py-0.5 rounded bg-muted text-muted-foreground/70">
              {STAGE_LABEL[doc.requiredStage] ?? doc.requiredStage}
            </span>
          </TableCell>
          <TableCell><span className="text-xs font-semibold text-muted-foreground">v{doc.version}</span></TableCell>
          <TableCell>
            <Badge variant="outline" className="gap-1 text-xs text-muted-foreground">
              <XCircle className="h-3 w-3" /> Inactivo
            </Badge>
          </TableCell>
          <TableCell><span className="text-xs text-muted-foreground">{formatDate(doc.approvedAt)}</span></TableCell>
          <TableCell>
            <Button size="sm" variant="ghost" className="h-7 gap-1 px-2 text-xs"
              onClick={() => onViewAcceptances(doc)} title="Ver aceptaciones de esta versión">
              <Eye className="h-3.5 w-3.5" /> Ver
            </Button>
          </TableCell>
        </TableRow>
      ))}
    </>
  )
}

// ── Tab principal ─────────────────────────────────────────────────────────────

function DocumentosLegalesTab() {
  const { data: docs, isLoading } = useAdminLegalDocs()
  const [createOpen, setCreateOpen] = useState(false)
  const [versioningDoc, setVersioningDoc] = useState<LegalDocSummary | null>(null)
  const [viewingDoc, setViewingDoc] = useState<LegalDocSummary | null>(null)
  const [section, setSection] = useState<"documentos" | "pendientes" | "bloqueados">("documentos")

  // Group by code, sorted latest version first
  const docGroups = useMemo<LegalDocSummary[][]>(() => {
    const map: Record<string, LegalDocSummary[]> = {}
    for (const doc of docs ?? []) {
      if (!map[doc.code]) map[doc.code] = []
      map[doc.code].push(doc)
    }
    return Object.values(map).map((g) => g.sort((a, b) => b.version - a.version))
  }, [docs])

  return (
    <div className="space-y-5">
      {/* Sub-navegación */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-1 p-1 bg-muted/50 rounded-lg">
          {(["documentos", "pendientes", "bloqueados"] as const).map((s) => (
            <button
              key={s}
              onClick={() => setSection(s)}
              className={cn(
                "rounded-md px-3 py-1.5 text-xs font-bold transition-all capitalize",
                section === s
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground hover:bg-background/50 hover:text-foreground",
              )}
            >
              {s === "documentos" ? "Documentos" : s === "pendientes" ? "Pendientes" : "Bloqueados"}
            </button>
          ))}
        </div>
        {section === "documentos" && (
          <Button size="sm" className="gap-1.5" onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" /> Nuevo documento
          </Button>
        )}
      </div>

      {/* Documentos */}
      {section === "documentos" && (
        <>
          {isLoading ? (
            <div className="space-y-2">{[...Array(3)].map((_, i) => <div key={i} className="h-14 animate-pulse rounded-lg bg-muted" />)}</div>
          ) : docGroups.length === 0 ? (
            <div className="flex flex-col items-center gap-3 rounded-xl border border-border/50 p-10 text-center">
              <FileText className="h-10 w-10 text-muted-foreground/30" />
              <p className="text-sm text-muted-foreground">No hay documentos legales creados</p>
              <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5">
                <Plus className="h-4 w-4" /> Crear primer documento
              </Button>
            </div>
          ) : (
            <div className="rounded-xl border border-border/50 bg-card/40 overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Documento</TableHead>
                    <TableHead>Etapa</TableHead>
                    <TableHead>Versión</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Aprobado</TableHead>
                    <TableHead>Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {docGroups.map((group) => (
                    <DocGroupRows
                      key={group[0].code}
                      group={group}
                      onViewAcceptances={(doc) => setViewingDoc(doc)}
                      onNewVersion={(doc) => setVersioningDoc(doc)}
                    />
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </>
      )}

      {/* Pendientes */}
      {section === "pendientes" && <PendingAcceptancesSection />}

      {/* Bloqueados */}
      {section === "bloqueados" && <BlockedSociosSection />}

      {/* Modales */}
      <CreateDocModal open={createOpen} onClose={() => setCreateOpen(false)} />
      <NewVersionModal doc={versioningDoc} onClose={() => setVersioningDoc(null)} />
      <AcceptancesDialog doc={viewingDoc} onClose={() => setViewingDoc(null)} />
    </div>
  )
}

// ─── Main export ──────────────────────────────────────────────────────────────

export function AdminPage() {
  const userRole = useAuthStore((s) => s.user?.rol?.toUpperCase() ?? "")
  const canViewSecurity = userRole === "ADMIN" || userRole === "SECRETARIA"

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Shield className="h-7 w-7 text-primary shrink-0" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Administración</h1>
          <p className="text-muted-foreground">Gestión de cuentas y configuración del sistema</p>
        </div>
      </div>

      <Tabs defaultValue="configuracion">
        <TabsList>
          <TabsTrigger value="configuracion" className="gap-1.5">
            <Settings className="h-4 w-4" /> Configuración
          </TabsTrigger>
          <TabsTrigger value="cuentas" className="gap-1.5">
            <Users className="h-4 w-4" /> Cuentas de acceso
          </TabsTrigger>
          <TabsTrigger value="auditoria" className="gap-1.5">
            <ClipboardList className="h-4 w-4" /> Auditoría
          </TabsTrigger>
          <TabsTrigger value="documentos" className="gap-1.5">
            <FileText className="h-4 w-4" /> Documentos
          </TabsTrigger>
          {canViewSecurity && (
            <TabsTrigger value="seguridad" className="gap-1.5">
              <ShieldAlert className="h-4 w-4" /> Seguridad
            </TabsTrigger>
          )}
        </TabsList>

        <TabsContent value="configuracion" className="mt-6">
          <ConfiguracionTab />
        </TabsContent>

        <TabsContent value="cuentas" className="mt-6">
          <UsuariosAuthTab />
        </TabsContent>

        <TabsContent value="auditoria" className="mt-6">
          <AuditoriaTab />
        </TabsContent>

        <TabsContent value="documentos" className="mt-6">
          <DocumentosLegalesTab />
        </TabsContent>

        {canViewSecurity && (
          <TabsContent value="seguridad" className="mt-6">
            <SecurityTab />
          </TabsContent>
        )}
      </Tabs>
    </div>
  )
}
