import { useState, useRef } from "react"
import { useUsuariosAuth, useDesbloquearUsuario, useForzarCierreSesion, useCambiarEstadoAcceso, useConfiguracion, useActualizarConfig } from "@/hooks/use-admin"
import { useAuthStore } from "@/stores/auth-store"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table"
import { toast } from "sonner"
import {
  Shield, Search, LockOpen, Lock,
  ShieldCheck, ShieldAlert, Settings, ClipboardList, Users, LogOut,
} from "lucide-react"
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

const CLAVE_BLOQUEO   = "BLOQUEAR_INSCRIPCION_INHABILITADOS"
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

function ConfiguracionTab() {
  const { data: configs, isLoading } = useConfiguracion()
  const actualizarMutation = useActualizarConfig()

  const bloqueoConfig = configs?.find((c) => c.clave === CLAVE_BLOQUEO)
  const bloqueoActivo = bloqueoConfig?.valor?.toLowerCase() === "true"

  const intentosValor = configs?.find((c) => c.clave === CLAVE_INTENTOS)?.valor
  const horasValor    = configs?.find((c) => c.clave === CLAVE_HORAS)?.valor

  const handleToggle = async (checked: boolean) => {
    try {
      await actualizarMutation.mutateAsync({ clave: CLAVE_BLOQUEO, valor: String(checked) })
      toast.success(
        checked
          ? "Bloqueo de inscripciones activado — socios inhabilitados no pueden inscribirse"
          : "Bloqueo de inscripciones desactivado — socios inhabilitados pueden inscribirse",
      )
    } catch (error) { console.error(error);
      toast.error("Error al actualizar la configuración")
    }
  }

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
            {[1,2,3].map((i) => <div key={i} className="h-10 animate-pulse rounded bg-muted" />)}
          </div>
        ) : (
          <div className="space-y-5 divide-y divide-border">
            {/* Toggle bloqueo inscripciones */}
            <div className="flex items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="flex items-center gap-3">
                  <Label className="text-sm font-medium">
                    Bloquear inscripciones de socios inhabilitados
                  </Label>
                  <Button
                    size="sm"
                    variant={bloqueoActivo ? "destructive" : "default"}
                    onClick={() => handleToggle(!bloqueoActivo)}
                    disabled={actualizarMutation.isPending}
                  >
                    {bloqueoActivo ? "Desactivar" : "Activar"}
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground">
                  {bloqueoActivo
                    ? "Activo — los socios inhabilitados no pueden inscribirse en salidas."
                    : "Inactivo — los socios inhabilitados pueden inscribirse en salidas."}
                </p>
              </div>
            </div>

            {/* Intentos de login */}
            <div className="pt-4">
              <NumericConfigRow
                label="Máximo de intentos de login fallidos"
                description="La cuenta se bloquea automáticamente al superar este número de intentos consecutivos fallidos."
                clave={CLAVE_INTENTOS}
                valor={intentosValor}
                min={1}
              />
            </div>

            {/* Horas de bloqueo */}
            <div className="pt-4">
              <NumericConfigRow
                label="Horas de bloqueo de cuenta"
                description="Tiempo que permanece bloqueada una cuenta tras superar el máximo de intentos fallidos."
                clave={CLAVE_HORAS}
                valor={horasValor}
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

        {canViewSecurity && (
          <TabsContent value="seguridad" className="mt-6">
            <SecurityTab />
          </TabsContent>
        )}
      </Tabs>
    </div>
  )
}
