import { useState } from "react"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from "@/components/ui/dialog"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useSocioDetail, useCuotas, useRegistrarCuota, useEliminarCuota, useActualizarNivelTecnico, useLookups, useHabilitacionLog, useSetJefeMontana } from "@/hooks/use-socios"
import { useHistorialSocio } from "@/hooks/use-estadisticas"
import { useUsuarioAuthBySocio, useDesbloquearUsuario, useEmergencyReset } from "@/hooks/use-admin"
import { useAuthStore } from "@/stores/auth-store"
import { Crown, Mountain, Plus, Trash2, Lock, LockOpen, ShieldCheck, Shield, Pencil, Check, X, History, ShieldAlert } from "lucide-react"
import { toast } from "sonner"
import type { CreateCuotaRequest } from "@/types/socios"

interface Props {
  open: boolean
  onClose: () => void
  socioId: string
}

const ESTADO_INSCRIPCION_LABEL: Record<string, string> = {
  INSCRITO: "Inscrito",
  CONFIRMADO: "Confirmado",
  NO_FUE: "No fue",
  CANCELADO: "Cancelado",
}

const ESTADO_INSCRIPCION_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  INSCRITO: "secondary",
  CONFIRMADO: "default",
  NO_FUE: "outline",
  CANCELADO: "destructive",
}

export function SocioDetailDialog({ open, onClose, socioId }: Props) {
  const user = useAuthStore((s) => s.user)
  const isAdminOrSecretaria = ["ADMIN", "SECRETARIA"].includes(user?.rol?.toUpperCase() ?? "")
  const canEditNivel = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(user?.rol?.toUpperCase() ?? "")

  const isAdmin = user?.rol?.toUpperCase() === "ADMIN"

  const { data: socio, isLoading: loadingSocio } = useSocioDetail(socioId)
  const { data: lookups } = useLookups()
  const { data: historial, isLoading: loadingHistorial } = useHistorialSocio(socioId)
  const { data: cuotas, isLoading: loadingCuotas } = useCuotas(socioId)
  const { data: cuentaAuth, isLoading: loadingCuenta } = useUsuarioAuthBySocio(socioId)
  const { data: habilitacionLog, isLoading: loadingHabLog } = useHabilitacionLog(socioId)

  const desbloquearMutation = useDesbloquearUsuario()
  const emergencyResetMutation = useEmergencyReset()
  const actualizarNivel = useActualizarNivelTecnico(socioId)
  const setJMMutation = useSetJefeMontana()

  const [emergencyResetOpen, setEmergencyResetOpen] = useState(false)

  // ─── Estado para edición inline de nivel técnico ───────────────────────────
  const [editingNivel, setEditingNivel] = useState(false)
  const [nivelSeleccionado, setNivelSeleccionado] = useState<string>("")

  const handleEditNivel = () => {
    setNivelSeleccionado(socio?.nivelTecnicoId ?? "")
    setEditingNivel(true)
  }

  const handleSaveNivel = async () => {
    try {
      await actualizarNivel.mutateAsync(nivelSeleccionado || null)
      toast.success("Nivel técnico actualizado")
      setEditingNivel(false)
    } catch (error) { console.error(error);
      toast.error("Error al actualizar el nivel técnico")
    }
  }

  const handleToggleJM = async () => {
    if (!socio) return
    const nuevoValor = !socio.esJefeMontana
    const accion = nuevoValor ? "asignar" : "quitar"
    if (!confirm(`¿${nuevoValor ? "Asignar" : "Quitar"} el rol de Jefe de Montaña a ${socio.nombre} ${socio.apellido}?`)) return
    try {
      await setJMMutation.mutateAsync({ id: socioId, valor: nuevoValor })
      toast.success(`Flag Jefe de Montaña ${accion === "asignar" ? "asignado" : "quitado"} correctamente`)
    } catch (error) { console.error(error);
      toast.error(`Error al ${accion} el flag Jefe de Montaña`)
    }
  }

  const handleDesbloquear = async () => {
    if (!confirm(`¿Desbloquear la cuenta de ${socio?.nombre} ${socio?.apellido}?`)) return
    try {
      await desbloquearMutation.mutateAsync(socioId)
      toast.success("Cuenta desbloqueada correctamente")
    } catch (error) { console.error(error);
      toast.error("Error al desbloquear la cuenta")
    }
  }

  const handleEmergencyReset = async () => {
    try {
      await emergencyResetMutation.mutateAsync(socioId)
      setEmergencyResetOpen(false)
      toast.success(`Reset ejecutado. Se envió el email de restablecimiento a ${socio?.correo}.`)
    } catch (error) { console.error(error);
      toast.error("Error al ejecutar el reset de emergencia")
    }
  }

  const registrarCuota = useRegistrarCuota(socioId)
  const eliminarCuota = useEliminarCuota(socioId)

  // ─── Form state para nueva cuota ──────────────────────
  const [showForm, setShowForm] = useState(false)
  const [formData, setFormData] = useState<CreateCuotaRequest>({
    valor: 0,
    fecha: new Date().toISOString().split("T")[0],
    estado: "PAGADO",
  })

  const handleRegistrar = async () => {
    if (!formData.valor || formData.valor <= 0) {
      toast.error("El valor debe ser mayor a 0")
      return
    }
    try {
      await registrarCuota.mutateAsync(formData)
      toast.success("Cuota registrada")
      setShowForm(false)
      setFormData({ valor: 0, fecha: new Date().toISOString().split("T")[0], estado: "PAGADO" })
    } catch (error) { console.error(error);
      toast.error("Error al registrar la cuota")
    }
  }

  const handleEliminar = async (cuotaId: number) => {
    if (!confirm("¿Eliminar este registro de cuota?")) return
    try {
      await eliminarCuota.mutateAsync(cuotaId)
      toast.success("Cuota eliminada")
    } catch (error) { console.error(error);
      toast.error("Error al eliminar la cuota")
    }
  }

  return (
    <>
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Detalle del socio</DialogTitle>
        </DialogHeader>

        {loadingSocio ? (
          <div className="flex items-center justify-center py-12">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : socio ? (
          <div className="space-y-4">
            {/* Header */}
            <div className="flex items-center gap-4">
              <div className="flex h-14 w-14 items-center justify-center rounded-full bg-primary/10 text-xl font-bold text-primary">
                {socio.nombre.charAt(0)}{socio.apellido.charAt(0)}
              </div>
              <div>
                <h2 className="text-xl font-bold text-foreground">
                  {socio.nombre} {socio.apellido}
                </h2>
                <div className="flex items-center gap-2 mt-1 flex-wrap">
                  <Badge variant="default">{socio.estadoHabilitacion}</Badge>
                  <Badge variant="secondary">{socio.tipoSocio}</Badge>
                  <Badge variant="outline">{socio.rolSistema}</Badge>
                  {socio.esJefeMontana && (
                    <Badge variant="outline" className="border-amber-500 bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-400">
                      Jefe de Montaña
                    </Badge>
                  )}
                  {cuentaAuth && cuentaAuth.estadoAcceso !== "ACTIVE" && (
                    <Badge variant="destructive">{cuentaAuth.estadoAccesoNombre}</Badge>
                  )}
                </div>
              </div>
            </div>

            <Tabs defaultValue="datos">
              <TabsList className="w-full">
                <TabsTrigger value="datos" className="flex-1">Datos</TabsTrigger>
                <TabsTrigger value="cuotas" className="flex-1">Cuotas</TabsTrigger>
                <TabsTrigger value="cuenta" className="flex-1">Cuenta</TabsTrigger>
                <TabsTrigger value="estadisticas" className="flex-1">Estadísticas</TabsTrigger>
                <TabsTrigger value="historial-hab" className="flex-1">Historial</TabsTrigger>
              </TabsList>

              {/* ─── Tab: Datos personales ───────────────────── */}
              <TabsContent value="datos" className="mt-4 space-y-6">
                <Section title="Datos Personales">
                  <InfoRow label="Cédula" value={socio.cedula} />
                  <InfoRow label="Correo" value={socio.correo} />
                  <InfoRow label="Teléfono" value={socio.telefono} />
                  <InfoRow label="Dirección" value={socio.direccion} />
                  <InfoRow label="Fecha de nacimiento" value={formatDate(socio.fechaNacimiento)} />
                  <InfoRow label="Edad" value={`${socio.edad} años`} />
                  <InfoRow label="Tipo de sangre" value={socio.tipoSangre} />
                  <InfoRow label="Fecha de ingreso" value={formatDate(socio.fechaIngreso)} />
                  {socio.fechaSalida && <InfoRow label="Fecha de salida" value={formatDate(socio.fechaSalida)} />}
                </Section>

                <div className="space-y-3">
                  <h3 className="text-sm font-semibold text-foreground border-b border-border pb-2">Clasificación</h3>
                  <div className="flex items-center gap-3">
                    {editingNivel ? (
                      <>
                        <select
                          value={nivelSeleccionado}
                          onChange={(e) => setNivelSeleccionado(e.target.value)}
                          className="flex h-9 flex-1 rounded-md border border-input bg-background px-3 py-1 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                        >
                          <option value="">Sin asignar</option>
                          {(lookups?.clasificaciones ?? [])
                            .sort((a, b) => a.nivel - b.nivel)
                            .map((c) => (
                              <option key={c.id} value={c.id}>{c.nombre}</option>
                            ))}
                        </select>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-8 w-8 text-primary hover:bg-primary/10"
                          onClick={handleSaveNivel}
                          disabled={actualizarNivel.isPending}
                          title="Guardar"
                        >
                          <Check className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-8 w-8 text-muted-foreground"
                          onClick={() => setEditingNivel(false)}
                          disabled={actualizarNivel.isPending}
                          title="Cancelar"
                        >
                          <X className="h-4 w-4" />
                        </Button>
                      </>
                    ) : (
                      <>
                        <div className="flex-1">
                          <p className="text-xs text-muted-foreground">Nivel técnico</p>
                          <p className="text-sm text-foreground">{socio.nivelTecnico || "Sin asignar"}</p>
                        </div>
                        {canEditNivel && (
                          <Button
                            size="icon"
                            variant="ghost"
                            className="h-8 w-8 text-muted-foreground hover:text-foreground"
                            onClick={handleEditNivel}
                            title="Editar nivel técnico"
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                        )}
                      </>
                    )}
                  </div>
                </div>

                {isAdminOrSecretaria && socio.rolSistema?.toUpperCase() === "DIRECTIVO" && (
                  <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2.5">
                    <div>
                      <p className="text-xs text-muted-foreground">Jefe de Montaña</p>
                      <p className={`text-sm font-medium ${socio.esJefeMontana ? "text-amber-600 dark:text-amber-400" : "text-muted-foreground"}`}>
                        {socio.esJefeMontana ? "Activo" : "No asignado"}
                      </p>
                    </div>
                    <Button
                      size="sm"
                      variant={socio.esJefeMontana ? "outline" : "outline"}
                      className={socio.esJefeMontana
                        ? "border-destructive/50 text-destructive hover:bg-destructive/10"
                        : "border-amber-500/50 text-amber-700 hover:bg-amber-50 dark:text-amber-400 dark:hover:bg-amber-950/20"}
                      onClick={handleToggleJM}
                      disabled={setJMMutation.isPending}
                    >
                      {setJMMutation.isPending
                        ? "Guardando..."
                        : socio.esJefeMontana ? "Quitar JM" : "Asignar JM"}
                    </Button>
                  </div>
                )}

                {(socio.emergencyContactName || socio.emergencyContactName2) && (
                  <Section title="Contactos de Emergencia">
                    {socio.emergencyContactName && (
                      <div className="space-y-1">
                        <p className="text-sm font-medium">{socio.emergencyContactName}</p>
                        <p className="text-sm text-muted-foreground">{socio.emergencyContactPhone}</p>
                      </div>
                    )}
                    {socio.emergencyContactName2 && (
                      <div className="space-y-1">
                        <p className="text-sm font-medium">{socio.emergencyContactName2}</p>
                        <p className="text-sm text-muted-foreground">{socio.emergencyContactPhone2}</p>
                      </div>
                    )}
                  </Section>
                )}

                <Section title="Información del Sistema">
                  <InfoRow label="Creado" value={formatDateTime(socio.createdAt)} />
                  <InfoRow label="Actualizado" value={formatDateTime(socio.updatedAt)} />
                </Section>
              </TabsContent>

              {/* ─── Tab: Cuotas ─────────────────────────────── */}
              <TabsContent value="cuotas" className="mt-4">
                <div className="space-y-4">
                  {/* Resumen rápido */}
                  {!loadingCuotas && cuotas && (
                    <div className="grid grid-cols-2 gap-3">
                      <div className="rounded-xl border border-border bg-card p-4 text-center">
                        <p className="text-2xl font-bold text-foreground">
                          {cuotas.filter((c) => c.estado === "PAGADO").length}
                        </p>
                        <p className="mt-0.5 text-xs text-muted-foreground">Pagadas</p>
                      </div>
                      <div className="rounded-xl border border-border bg-card p-4 text-center">
                        <p className="text-2xl font-bold text-destructive">
                          {cuotas.filter((c) => c.estado === "PENDIENTE").length}
                        </p>
                        <p className="mt-0.5 text-xs text-muted-foreground">Pendientes</p>
                      </div>
                    </div>
                  )}

                  {/* Botón registrar (Admin/Secretaria) */}
                  {isAdminOrSecretaria && (
                    <div>
                      {showForm ? (
                        <div className="rounded-xl border border-border bg-card p-4 space-y-3">
                          <h4 className="text-sm font-semibold text-foreground">Nuevo registro</h4>
                          <div className="grid gap-3 sm:grid-cols-3">
                            <div className="space-y-1">
                              <Label className="text-xs">Valor ($)</Label>
                              <Input
                                type="number"
                                min="0.01"
                                step="0.01"
                                value={formData.valor || ""}
                                onChange={(e) => setFormData((f) => ({ ...f, valor: parseFloat(e.target.value) || 0 }))}
                                placeholder="0.00"
                              />
                            </div>
                            <div className="space-y-1">
                              <Label className="text-xs">Fecha</Label>
                              <Input
                                type="date"
                                value={formData.fecha}
                                onChange={(e) => setFormData((f) => ({ ...f, fecha: e.target.value }))}
                              />
                            </div>
                            <div className="space-y-1">
                              <Label className="text-xs">Estado</Label>
                              <select
                                value={formData.estado}
                                onChange={(e) => setFormData((f) => ({ ...f, estado: e.target.value as "PAGADO" | "PENDIENTE" }))}
                                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                              >
                                <option value="PAGADO">Pagado</option>
                                <option value="PENDIENTE">Pendiente</option>
                              </select>
                            </div>
                          </div>
                          <div className="flex gap-2 justify-end">
                            <Button variant="outline" size="sm" onClick={() => setShowForm(false)}>
                              Cancelar
                            </Button>
                            <Button
                              size="sm"
                              onClick={handleRegistrar}
                              disabled={registrarCuota.isPending}
                            >
                              {registrarCuota.isPending ? "Guardando..." : "Guardar"}
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <Button variant="outline" size="sm" className="gap-2" onClick={() => setShowForm(true)}>
                          <Plus className="h-4 w-4" /> Registrar cuota
                        </Button>
                      )}
                    </div>
                  )}

                  {/* Lista */}
                  {loadingCuotas ? (
                    <div className="space-y-2">
                      {Array.from({ length: 3 }).map((_, i) => (
                        <div key={i} className="h-12 animate-pulse rounded-lg bg-muted" />
                      ))}
                    </div>
                  ) : !cuotas || cuotas.length === 0 ? (
                    <div className="rounded-xl border border-border bg-card p-10 text-center">
                      <p className="text-sm text-muted-foreground">No hay registros de cuotas</p>
                    </div>
                  ) : (
                    <ul className="divide-y divide-border rounded-xl border border-border bg-card">
                      {cuotas.map((c) => (
                        <li key={c.id} className="flex items-center justify-between gap-3 px-4 py-3">
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <Badge variant={c.estado === "PAGADO" ? "default" : "destructive"}>
                                {c.estado === "PAGADO" ? "Pagado" : "Pendiente"}
                              </Badge>
                              <span className="text-sm font-medium text-foreground">
                                ${Number(c.valor).toFixed(2)}
                              </span>
                            </div>
                            <p className="text-xs text-muted-foreground mt-0.5">
                              {formatDate(c.fecha)}
                              {c.registradoPorNombre && ` · por ${c.registradoPorNombre}`}
                            </p>
                          </div>
                          {isAdminOrSecretaria && (
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleEliminar(c.id)}
                              disabled={eliminarCuota.isPending}
                              title="Eliminar registro"
                            >
                              <Trash2 className="h-4 w-4 text-destructive" />
                            </Button>
                          )}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </TabsContent>

              {/* ─── Tab: Cuenta ─────────────────────────────── */}
              <TabsContent value="cuenta" className="mt-4">
                {loadingCuenta ? (
                  <div className="space-y-3">
                    {Array.from({ length: 4 }).map((_, i) => (
                      <div key={i} className="h-10 animate-pulse rounded-lg bg-muted" />
                    ))}
                  </div>
                ) : !cuentaAuth ? (
                  <div className="rounded-xl border border-border bg-card p-10 text-center">
                    <Shield className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                    <p className="text-sm text-muted-foreground">
                      Este socio aún no ha activado su cuenta de acceso
                    </p>
                  </div>
                ) : (
                  <div className="space-y-4">
                    {/* Estado de acceso al sistema (no-ACTIVE) */}
                    {cuentaAuth.estadoAcceso !== "ACTIVE" && (
                      <div className="flex items-start gap-3 rounded-xl border border-destructive/40 bg-destructive/10 p-4">
                        <Lock className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
                        <div className="flex-1">
                          <p className="text-sm font-semibold text-destructive">{cuentaAuth.estadoAccesoNombre}</p>
                          <p className="text-xs text-muted-foreground mt-0.5">
                            El acceso al sistema está restringido. Gestiona el estado desde Administración → Cuentas de acceso.
                          </p>
                        </div>
                      </div>
                    )}

                    {/* Estado de bloqueo brute-force */}
                    {cuentaAuth.loginBlocked && (
                      <div className="flex items-start gap-3 rounded-xl border border-destructive/40 bg-destructive/10 p-4">
                        <Lock className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
                        <div className="flex-1">
                          <p className="text-sm font-semibold text-destructive">Cuenta bloqueada</p>
                          <p className="text-xs text-muted-foreground mt-0.5">
                            {cuentaAuth.blockedUntil
                              ? `Bloqueada hasta ${formatDateTime(cuentaAuth.blockedUntil)}`
                              : "Bloqueada por intentos fallidos de inicio de sesión"}
                          </p>
                        </div>
                        {isAdmin && (
                          <Button
                            size="sm"
                            variant="outline"
                            className="shrink-0 gap-1.5 border-primary/30 text-primary hover:bg-primary/10"
                            onClick={handleDesbloquear}
                            disabled={desbloquearMutation.isPending}
                          >
                            <LockOpen className="h-3.5 w-3.5" />
                            {desbloquearMutation.isPending ? "Desbloqueando..." : "Desbloquear"}
                          </Button>
                        )}
                      </div>
                    )}

                    {/* Datos de la cuenta */}
                    <div className="rounded-xl border border-border bg-card p-4 space-y-3">
                      <div className="grid gap-3 sm:grid-cols-2">
                        <div>
                          <p className="text-xs text-muted-foreground">Nombre de usuario</p>
                          <p className="text-sm font-mono font-medium text-foreground">{cuentaAuth.username}</p>
                        </div>
                        <div>
                          <p className="text-xs text-muted-foreground">Último inicio de sesión</p>
                          <p className="text-sm text-foreground">
                            {cuentaAuth.lastLogin ? formatDateTime(cuentaAuth.lastLogin) : "Nunca"}
                          </p>
                        </div>
                        <div>
                          <p className="text-xs text-muted-foreground">Autenticación 2FA</p>
                          <div className="flex items-center gap-1.5 mt-0.5">
                            {cuentaAuth.totpEnabled ? (
                              <>
                                <ShieldCheck className="h-4 w-4 text-primary" />
                                <span className="text-sm text-primary font-medium">Activa</span>
                              </>
                            ) : (
                              <span className="text-sm text-muted-foreground">No configurada</span>
                            )}
                          </div>
                        </div>
                        <div>
                          <p className="text-xs text-muted-foreground">Intentos fallidos</p>
                          <p className={`text-sm font-mono font-medium ${cuentaAuth.failedAttempts > 0 ? "text-destructive" : "text-foreground"}`}>
                            {cuentaAuth.failedAttempts}
                          </p>
                        </div>
                        <div>
                          <p className="text-xs text-muted-foreground">Cuenta creada</p>
                          <p className="text-sm text-foreground">{formatDateTime(cuentaAuth.createdAt)}</p>
                        </div>
                      </div>
                    </div>

                    {/* Reset de emergencia — solo si el socio tiene 2FA activo */}
                    {isAdminOrSecretaria && cuentaAuth.totpEnabled && (
                      <div className="flex items-center justify-between rounded-xl border border-amber-500/30 bg-amber-500/5 px-4 py-3">
                        <div className="space-y-0.5">
                          <p className="text-sm font-semibold text-amber-700 dark:text-amber-400">Reset de emergencia</p>
                          <p className="text-xs text-muted-foreground">Pérdida de teléfono con 2FA activo</p>
                        </div>
                        <Button
                          size="sm"
                          variant="outline"
                          className="gap-1.5 border-amber-500/40 text-amber-700 dark:text-amber-400 hover:bg-amber-500/10"
                          onClick={() => setEmergencyResetOpen(true)}
                          disabled={emergencyResetMutation.isPending}
                        >
                          <ShieldAlert className="h-3.5 w-3.5" />
                          Reset de emergencia
                        </Button>
                      </div>
                    )}

                    {/* Botón desbloquear si tiene intentos fallidos pero no está bloqueada */}
                    {!cuentaAuth.loginBlocked && cuentaAuth.failedAttempts > 0 && isAdmin && (
                      <div className="flex items-center justify-between rounded-xl border border-border bg-card px-4 py-3">
                        <p className="text-sm text-muted-foreground">
                          {cuentaAuth.failedAttempts} intento{cuentaAuth.failedAttempts > 1 ? "s" : ""} fallido{cuentaAuth.failedAttempts > 1 ? "s" : ""} registrado{cuentaAuth.failedAttempts > 1 ? "s" : ""}
                        </p>
                        <Button
                          size="sm"
                          variant="outline"
                          className="gap-1.5 border-primary/30 text-primary hover:bg-primary/10"
                          onClick={handleDesbloquear}
                          disabled={desbloquearMutation.isPending}
                        >
                          <LockOpen className="h-3.5 w-3.5" />
                          Resetear intentos
                        </Button>
                      </div>
                    )}
                  </div>
                )}
              </TabsContent>

              {/* ─── Tab: Estadísticas ───────────────────────── */}
              <TabsContent value="estadisticas" className="mt-4">
                {loadingHistorial ? (
                  <div className="space-y-3">
                    {Array.from({ length: 4 }).map((_, i) => (
                      <div key={i} className="h-16 animate-pulse rounded-xl bg-muted" />
                    ))}
                  </div>
                ) : !historial ? (
                  <p className="py-8 text-center text-sm text-muted-foreground">
                    No se pudieron cargar las estadísticas
                  </p>
                ) : (
                  <div className="space-y-4">
                    {/* KPIs */}
                    <div className="grid grid-cols-3 gap-4">
                      {[
                        { label: "Participaciones", value: historial.totalParticipaciones },
                        { label: "Cumbres logradas", value: historial.totalCumbresLogradas },
                        { label: "Veces jefe", value: historial.vecesJefeSalida },
                      ].map(({ label, value }) => (
                        <div key={label} className="rounded-xl border border-border bg-card p-4 text-center">
                          <p className="text-2xl font-bold text-foreground">{value}</p>
                          <p className="mt-0.5 text-xs text-muted-foreground">{label}</p>
                        </div>
                      ))}
                    </div>

                    {/* Historial */}
                    {historial.historial.length === 0 ? (
                      <div className="rounded-xl border border-border bg-card p-10 text-center">
                        <Mountain className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                        <p className="text-sm text-muted-foreground">
                          Este socio aún no tiene salidas registradas
                        </p>
                      </div>
                    ) : (
                      <div className="space-y-2">
                        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                          Historial ({historial.historial.length})
                        </h3>
                        <ul className="divide-y divide-border rounded-xl border border-border bg-card">
                          {historial.historial.map((h) => (
                            <li key={h.salidaId} className="flex items-start justify-between gap-3 px-4 py-3">
                              <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2">
                                  {h.esJefeSalida && (
                                    <Crown className="h-3.5 w-3.5 shrink-0 text-yellow-500" />
                                  )}
                                  <p className="truncate text-sm font-medium text-foreground">
                                    {h.salidaNombre}
                                  </p>
                                </div>
                                <p className="text-xs text-muted-foreground">
                                  {formatDate(h.fecha)} · {h.mountainNombre} ({h.mountainAltitud} m)
                                </p>
                                {Array.isArray(h.dignidades) && h.dignidades.length > 0 && (
                                  <div className="mt-1 flex flex-wrap gap-1">
                                    {h.dignidades.map((d, i) => (
                                      <Badge key={i} variant="outline" className="text-xs">
                                        {typeof d === "string" ? d : (d as { dignidadNombre: string }).dignidadNombre}
                                      </Badge>
                                    ))}
                                  </div>
                                )}
                              </div>
                              <div className="shrink-0 text-right space-y-1">
                                <Badge variant={ESTADO_INSCRIPCION_VARIANT[h.estadoInscripcion] ?? "outline"}>
                                  {ESTADO_INSCRIPCION_LABEL[h.estadoInscripcion] ?? h.estadoInscripcion}
                                </Badge>
                                {h.seRealizo !== null && (
                                  <p className="text-xs text-muted-foreground">
                                    {h.seRealizo ? "Realizada ✓" : "No realizada"}
                                  </p>
                                )}
                              </div>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}
              </TabsContent>
              {/* ─── Tab: Historial de habilitación ─────────── */}
              <TabsContent value="historial-hab" className="mt-4">
                {loadingHabLog ? (
                  <div className="space-y-2">
                    {Array.from({ length: 3 }).map((_, i) => (
                      <div key={i} className="h-14 animate-pulse rounded-lg bg-muted" />
                    ))}
                  </div>
                ) : !habilitacionLog || habilitacionLog.length === 0 ? (
                  <div className="rounded-xl border border-border bg-card p-10 text-center">
                    <History className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                    <p className="text-sm text-muted-foreground">
                      No hay cambios de estado de habilitación registrados
                    </p>
                  </div>
                ) : (
                  <ul className="divide-y divide-border rounded-xl border border-border bg-card">
                    {habilitacionLog.map((entry) => (
                      <li key={entry.id} className="flex items-start justify-between gap-3 px-4 py-3">
                        <div className="min-w-0 flex-1 space-y-0.5">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-xs text-muted-foreground line-through">{entry.estadoAnterior}</span>
                            <span className="text-xs text-muted-foreground">→</span>
                            <span className={`text-xs font-semibold ${
                              entry.estadoNuevo.toLowerCase().includes("inhabilitado")
                                ? "text-destructive"
                                : "text-primary"
                            }`}>{entry.estadoNuevo}</span>
                            <Badge variant="outline" className="text-xs py-0">
                              {entry.fuente === "CSV" ? "CSV" : "Manual"}
                            </Badge>
                          </div>
                          <p className="text-xs text-muted-foreground">
                            por {entry.cambiadoPorNombre}
                            {entry.notas && ` · ${entry.notas}`}
                          </p>
                        </div>
                        <p className="shrink-0 text-xs text-muted-foreground whitespace-nowrap">
                          {formatDateTime(entry.cambiadoEn)}
                        </p>
                      </li>
                    ))}
                  </ul>
                )}
              </TabsContent>
            </Tabs>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>

    {/* ─── Confirmación reset de emergencia ─────────────────────── */}
    <Dialog open={emergencyResetOpen} onOpenChange={setEmergencyResetOpen}>
      <DialogContent className="sm:max-w-md" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldAlert className="h-5 w-5 text-amber-500" />
            Reset de emergencia
          </DialogTitle>
          <DialogDescription asChild>
            <div className="space-y-2 pt-1">
              <p>
                ¿Confirmas el reset de emergencia para{" "}
                <span className="font-semibold text-foreground">
                  {socio?.nombre} {socio?.apellido}
                </span>
                ?
              </p>
              <p className="text-sm">Esta acción realizará lo siguiente:</p>
              <ul className="text-sm space-y-1 list-disc list-inside text-muted-foreground">
                <li>Desactivará el 2FA de la cuenta</li>
                <li>Cerrará todas las sesiones activas</li>
                <li>
                  Enviará un email a{" "}
                  <span className="font-medium text-foreground">{socio?.correo}</span>{" "}
                  con un link para crear una nueva contraseña
                </li>
              </ul>
            </div>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => setEmergencyResetOpen(false)}
            disabled={emergencyResetMutation.isPending}
          >
            Cancelar
          </Button>
          <Button
            variant="destructive"
            onClick={handleEmergencyReset}
            disabled={emergencyResetMutation.isPending}
            className="gap-1.5"
          >
            <ShieldAlert className="h-4 w-4" />
            {emergencyResetMutation.isPending ? "Ejecutando..." : "Confirmar reset"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    </>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-foreground border-b border-border pb-2">{title}</h3>
      <div className="grid gap-2 sm:grid-cols-2">{children}</div>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm text-foreground">{value || "—"}</p>
    </div>
  )
}

function formatDate(iso: string | null): string {
  if (!iso) return "—"
  return new Date(iso + "T00:00:00").toLocaleDateString("es-EC", {
    year: "numeric", month: "long", day: "numeric",
  })
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleDateString("es-EC", {
    year: "numeric", month: "short", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  })
}
