import { useEffect, useState } from "react"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import { toast } from "sonner"
import { useLookups, useCreateSocio, useUpdateSocio, useSocioDetail, useCambiarRol } from "@/hooks/use-socios"
import { useAuthStore } from "@/stores/auth-store"
import type { CreateSocioRequest, UpdateSocioRequest } from "@/types/socios"

interface Props {
  open: boolean
  onClose: () => void
  mode: "create" | "edit"
  socioId?: string
}

// ─── Validation helpers ───────────────────────────────

function onlyDigits(value: string) {
  return value.replace(/\D/g, "")
}

function validateCedula(v: string) {
  if (!v) return "La cédula es obligatoria"
  if (!/^\d+$/.test(v)) return "La cédula solo puede contener dígitos"
  if (v.length !== 10) return "La cédula debe tener exactamente 10 dígitos"
  return ""
}

function validateTelefono(v: string) {
  if (!v) return ""
  if (!/^\d+$/.test(v)) return "El teléfono solo puede contener dígitos"
  if (v.length > 15) return "El teléfono no puede tener más de 15 dígitos"
  return ""
}

function validateEmail(v: string) {
  if (!v) return "El correo es obligatorio"
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v))
    return "El correo debe tener un formato válido (ej: nombre@dominio.com)"
  return ""
}

// ─── Component ───────────────────────────────────────

export function SocioFormDialog({ open, onClose, mode, socioId }: Props) {
  const user = useAuthStore((s) => s.user)
  const isAdminOrSecretaria = ["ADMIN", "SECRETARIA"].includes(user?.rol?.toUpperCase() ?? "")

  const { data: lookups } = useLookups()
  const { data: socioData } = useSocioDetail(mode === "edit" ? socioId : undefined)
  const createMutation = useCreateSocio()
  const updateMutation = useUpdateSocio(socioId ?? "")
  const cambiarRolMutation = useCambiarRol()

  // ─── Create mode state ────────────────────────────
  const [createForm, setCreateForm] = useState({ cedula: "", correo: "", telefono: "" })
  const [createErrors, setCreateErrors] = useState({ cedula: "", correo: "", telefono: "" })

  // ─── Edit mode state ──────────────────────────────
  const [form, setForm] = useState({
    nombre: "", apellido: "", cedula: "", correo: "",
    telefono: "", direccion: "", fechaNacimiento: "",
    fechaIngreso: "", tipoSangre: "",
    emergencyContactName: "", emergencyContactPhone: "", emergencyContactDireccion: "",
    emergencyContactName2: "", emergencyContactPhone2: "", emergencyContactDireccion2: "",
    tipoSocioId: "", nivelTecnicoId: "",
    estadoHabilitacionId: "",
    fechaSalida: "",
  })
  const [rolSistemaId, setRolSistemaId] = useState<string>("")
  const [originalRolSistemaId, setOriginalRolSistemaId] = useState<string>("")
  const [editErrors, setEditErrors] = useState({
    cedula: "", correo: "", telefono: "",
    emergencyContactPhone: "", emergencyContactPhone2: "",
  })

  useEffect(() => {
    if (mode === "edit" && socioData) {
      setForm({
        nombre: socioData.nombre,
        apellido: socioData.apellido,
        cedula: socioData.cedula,
        correo: socioData.correo,
        telefono: socioData.telefono ?? "",
        direccion: socioData.direccion ?? "",
        fechaNacimiento: socioData.fechaNacimiento,
        fechaIngreso: socioData.fechaIngreso,
        tipoSangre: socioData.tipoSangre ?? "",
        emergencyContactName: socioData.emergencyContactName ?? "",
        emergencyContactPhone: socioData.emergencyContactPhone ?? "",
        emergencyContactDireccion: socioData.emergencyContactDireccion ?? "",
        emergencyContactName2: socioData.emergencyContactName2 ?? "",
        emergencyContactPhone2: socioData.emergencyContactPhone2 ?? "",
        emergencyContactDireccion2: socioData.emergencyContactDireccion2 ?? "",
        tipoSocioId: String(socioData.tipoSocioId),
        nivelTecnicoId: socioData.nivelTecnicoId ?? "",
        estadoHabilitacionId: String(socioData.estadoHabilitacionId),
        fechaSalida: socioData.fechaSalida ?? "",
      })
      setRolSistemaId(String(socioData.rolSistemaId))
      setOriginalRolSistemaId(String(socioData.rolSistemaId))
      setEditErrors({ cedula: "", correo: "", telefono: "", emergencyContactPhone: "", emergencyContactPhone2: "" })
    }
  }, [mode, socioData])

  // ─── Create form handlers ─────────────────────────

  const handleCreateCedula = (v: string) => {
    const digits = onlyDigits(v).slice(0, 10)
    setCreateForm((p) => ({ ...p, cedula: digits }))
    setCreateErrors((p) => ({ ...p, cedula: validateCedula(digits) }))
  }

  const handleCreateTelefono = (v: string) => {
    const digits = onlyDigits(v).slice(0, 15)
    setCreateForm((p) => ({ ...p, telefono: digits }))
    setCreateErrors((p) => ({ ...p, telefono: validateTelefono(digits) }))
  }

  const handleCreateEmail = (v: string) => {
    setCreateForm((p) => ({ ...p, correo: v }))
    setCreateErrors((p) => ({ ...p, correo: validateEmail(v) }))
  }

  // ─── Edit form handlers ───────────────────────────

  const update = (field: string, value: string) =>
    setForm((prev) => ({ ...prev, [field]: value }))

  const handleEditCedula = (v: string) => {
    const digits = onlyDigits(v).slice(0, 10)
    update("cedula", digits)
    setEditErrors((p) => ({ ...p, cedula: validateCedula(digits) }))
  }

  const handleEditTelefono = (v: string) => {
    const digits = onlyDigits(v).slice(0, 15)
    update("telefono", digits)
    setEditErrors((p) => ({ ...p, telefono: validateTelefono(digits) }))
  }

  const handleEditEmail = (v: string) => {
    update("correo", v)
    setEditErrors((p) => ({ ...p, correo: validateEmail(v) }))
  }

  const handleEmergencyPhone = (field: "emergencyContactPhone" | "emergencyContactPhone2", v: string) => {
    const digits = onlyDigits(v).slice(0, 15)
    update(field, digits)
    setEditErrors((p) => ({ ...p, [field]: validateTelefono(digits) }))
  }

  // ─── Submit ───────────────────────────────────────

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (mode === "create") {
      const errs = {
        cedula: validateCedula(createForm.cedula),
        correo: validateEmail(createForm.correo),
        telefono: validateTelefono(createForm.telefono),
      }
      setCreateErrors(errs)
      if (Object.values(errs).some(Boolean)) return

      try {
        const request: CreateSocioRequest = {
          cedula: createForm.cedula,
          correo: createForm.correo,
          telefono: createForm.telefono || undefined,
        }
        await createMutation.mutateAsync(request)
        toast.success("Invitación enviada correctamente. El socio recibirá un email para completar su registro.")
        onClose()
      } catch (err: unknown) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        toast.error(axiosErr.response?.data?.message || "Error al enviar la invitación")
      }
      return
    }

    // Edit mode
    const errs = {
      cedula: validateCedula(form.cedula),
      correo: validateEmail(form.correo),
      telefono: validateTelefono(form.telefono),
      emergencyContactPhone: validateTelefono(form.emergencyContactPhone),
      emergencyContactPhone2: validateTelefono(form.emergencyContactPhone2),
    }
    setEditErrors(errs)
    if (Object.values(errs).some(Boolean)) return

    try {
      const request: UpdateSocioRequest = {
        nombre: form.nombre,
        apellido: form.apellido,
        cedula: form.cedula,
        correo: form.correo,
        telefono: form.telefono || undefined,
        direccion: form.direccion || undefined,
        fechaNacimiento: form.fechaNacimiento,
        fechaIngreso: form.fechaIngreso || undefined,
        fechaSalida: form.fechaSalida || undefined,
        tipoSangre: form.tipoSangre || undefined,
        emergencyContactName: form.emergencyContactName || undefined,
        emergencyContactPhone: form.emergencyContactPhone || undefined,
        emergencyContactDireccion: form.emergencyContactDireccion || undefined,
        emergencyContactName2: form.emergencyContactName2 || undefined,
        emergencyContactPhone2: form.emergencyContactPhone2 || undefined,
        emergencyContactDireccion2: form.emergencyContactDireccion2 || undefined,
        tipoSocioId: Number(form.tipoSocioId),
        nivelTecnicoId: form.nivelTecnicoId || null,
        estadoHabilitacionId: Number(form.estadoHabilitacionId),
      }
      await updateMutation.mutateAsync(request)

      if (isAdminOrSecretaria && rolSistemaId && rolSistemaId !== originalRolSistemaId) {
        await cambiarRolMutation.mutateAsync({
          id: socioId!,
          request: { rolSistemaId: Number(rolSistemaId) },
        })
      }

      toast.success("Socio actualizado correctamente")
      onClose()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      toast.error(axiosErr.response?.data?.message || "Error al guardar")
    }
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending || cambiarRolMutation.isPending

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{mode === "create" ? "Invitar nuevo socio" : "Editar socio"}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-6">
          {mode === "create" ? (
            <fieldset className="space-y-4">
              <legend className="text-sm font-semibold text-foreground">Datos de invitación</legend>
              <p className="text-sm text-muted-foreground">
                El socio recibirá un enlace por email para completar sus datos personales y
                establecer sus credenciales de acceso.
              </p>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field
                  label="Cédula *"
                  value={createForm.cedula}
                  onChange={handleCreateCedula}
                  inputMode="numeric"
                  maxLength={10}
                  error={createErrors.cedula}
                  placeholder="Ej: 1234567890"
                  required
                />
                <Field
                  label="Correo electrónico *"
                  value={createForm.correo}
                  onChange={handleCreateEmail}
                  type="email"
                  error={createErrors.correo}
                  placeholder="nombre@dominio.com"
                  required
                />
                <Field
                  label="Teléfono"
                  value={createForm.telefono}
                  onChange={handleCreateTelefono}
                  inputMode="numeric"
                  maxLength={15}
                  error={createErrors.telefono}
                  placeholder="Ej: 0991234567"
                />
              </div>
            </fieldset>
          ) : (
            <>
              <fieldset className="space-y-4">
                <legend className="text-sm font-semibold text-foreground">Datos Personales</legend>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Nombre *" value={form.nombre} onChange={(v) => update("nombre", v)} required />
                  <Field label="Apellido *" value={form.apellido} onChange={(v) => update("apellido", v)} required />
                  <Field
                    label="Cédula *"
                    value={form.cedula}
                    onChange={handleEditCedula}
                    inputMode="numeric"
                    maxLength={10}
                    error={editErrors.cedula}
                    placeholder="Ej: 1234567890"
                    required
                  />
                  <Field
                    label="Correo *"
                    value={form.correo}
                    onChange={handleEditEmail}
                    type="email"
                    error={editErrors.correo}
                    placeholder="nombre@dominio.com"
                    required
                  />
                  <Field
                    label="Teléfono"
                    value={form.telefono}
                    onChange={handleEditTelefono}
                    inputMode="numeric"
                    maxLength={15}
                    error={editErrors.telefono}
                    placeholder="Ej: 0991234567"
                  />
                  <Field label="Dirección" value={form.direccion} onChange={(v) => update("direccion", v)} />
                  <Field label="Fecha de nacimiento *" value={form.fechaNacimiento} onChange={(v) => update("fechaNacimiento", v)} type="date" required />
                  <Field label="Fecha de ingreso" value={form.fechaIngreso} onChange={(v) => update("fechaIngreso", v)} type="date" />
                  <div className="space-y-2">
                    <Label>Tipo de sangre</Label>
                    <Select value={form.tipoSangre || "__none__"} onValueChange={(v) => update("tipoSangre", v === "__none__" ? "" : v)}>
                      <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value="__none__">— Sin especificar —</SelectItem>
                        {["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"].map((t) => (
                          <SelectItem key={t} value={t}>{t}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <Field label="Fecha de salida" value={form.fechaSalida} onChange={(v) => update("fechaSalida", v)} type="date" />
                </div>
              </fieldset>

              <fieldset className="space-y-4">
                <legend className="text-sm font-semibold text-foreground">Contacto de emergencia 1</legend>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Nombre" value={form.emergencyContactName} onChange={(v) => update("emergencyContactName", v)} />
                  <Field
                    label="Teléfono"
                    value={form.emergencyContactPhone}
                    onChange={(v) => handleEmergencyPhone("emergencyContactPhone", v)}
                    inputMode="numeric"
                    maxLength={15}
                    error={editErrors.emergencyContactPhone}
                    placeholder="Ej: 0991234567"
                  />
                </div>
              </fieldset>

              <fieldset className="space-y-4">
                <legend className="text-sm font-semibold text-foreground">Contacto de emergencia 2</legend>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Nombre" value={form.emergencyContactName2} onChange={(v) => update("emergencyContactName2", v)} />
                  <Field
                    label="Teléfono"
                    value={form.emergencyContactPhone2}
                    onChange={(v) => handleEmergencyPhone("emergencyContactPhone2", v)}
                    inputMode="numeric"
                    maxLength={15}
                    error={editErrors.emergencyContactPhone2}
                    placeholder="Ej: 0991234567"
                  />
                </div>
              </fieldset>

              <fieldset className="space-y-4">
                <legend className="text-sm font-semibold text-foreground">Clasificación</legend>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label>Tipo de socio</Label>
                    <Select value={form.tipoSocioId} onValueChange={(v) => update("tipoSocioId", v)}>
                      <SelectTrigger><SelectValue placeholder="Seleccionar..." /></SelectTrigger>
                      <SelectContent>
                        {lookups?.tiposSocio.map((t) => (
                          <SelectItem key={t.id} value={String(t.id)}>{t.nombre}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>Nivel técnico</Label>
                    <Select value={form.nivelTecnicoId} onValueChange={(v) => update("nivelTecnicoId", v)}>
                      <SelectTrigger><SelectValue placeholder="Sin asignar" /></SelectTrigger>
                      <SelectContent>
                        {lookups?.clasificaciones.map((c) => (
                          <SelectItem key={c.id} value={c.id}>{c.nombre}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  {isAdminOrSecretaria && (
                    <div className="space-y-2">
                      <Label>Estado de habilitación</Label>
                      <Select value={form.estadoHabilitacionId} onValueChange={(v) => update("estadoHabilitacionId", v)}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar estado..." /></SelectTrigger>
                        <SelectContent>
                          {lookups?.estadosHabilitacion.map((e) => (
                            <SelectItem key={e.id} value={String(e.id)}>{e.nombre}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  )}
                  {isAdminOrSecretaria && (
                    <div className="space-y-2">
                      <Label>Rol en el sistema</Label>
                      <Select value={rolSistemaId} onValueChange={setRolSistemaId}>
                        <SelectTrigger><SelectValue placeholder="Seleccionar rol..." /></SelectTrigger>
                        <SelectContent>
                          {lookups?.rolesSistema
                            .filter((r) => r.nombre.toLowerCase() !== "admin")
                            .map((r) => (
                              <SelectItem key={r.id} value={String(r.id)}>{r.nombre}</SelectItem>
                            ))}
                        </SelectContent>
                      </Select>
                    </div>
                  )}
                </div>
              </fieldset>
            </>
          )}

          <div className="flex justify-end gap-3 pt-4 border-t border-border">
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting
                ? "Guardando..."
                : mode === "create"
                  ? "Enviar invitación"
                  : "Guardar cambios"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ─── Reusable field component ─────────────────────────

function Field({
  label, value, onChange, type = "text", inputMode, maxLength, error, required = false, placeholder,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  type?: string
  inputMode?: React.HTMLAttributes<HTMLInputElement>["inputMode"]
  maxLength?: number
  error?: string
  required?: boolean
  placeholder?: string
}) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      <Input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        inputMode={inputMode}
        maxLength={maxLength}
        required={required}
        placeholder={placeholder}
        className={error ? "border-destructive focus-visible:ring-destructive" : ""}
      />
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}
