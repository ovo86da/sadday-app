import { useState } from "react"
import { QRCodeSVG } from "qrcode.react"
import { Link, useSearchParams } from "react-router"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { toast } from "sonner"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { useAuthStore } from "@/stores/auth-store"
import { useHistorialSocio } from "@/hooks/use-estadisticas"
import { useActiveDocuments, useMyAcceptances, useAcceptDocument } from "@/hooks/use-legal-documents"
import {
  useMyEmergencyContacts, useUpsertEmergencyContacts,
  useMyMedicalInfo, useUpdateMedicalInfo,
} from "@/hooks/use-profile-docs"
import type { LegalDoc, LegalAcceptance } from "@/hooks/use-legal-documents"
import type { EmergencyContact } from "@/hooks/use-profile-docs"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import api from "@/lib/api"
import { Crown, Mountain, ArrowRight, Monitor, Smartphone, Globe, Clock, MapPin, AlertTriangle, Pencil, Check, X as XIcon, Key, Copy, Trash2, Plus, FileText, ChevronDown, CheckCircle2, Phone, Heart, Stethoscope } from "lucide-react"
import { cn } from "@/lib/utils"
import { SalidaDetailDialog } from "@/pages/salidas/salida-detail-dialog"

// ─── Types ──────────────────────────────────────────────────────────────────

interface SocioResponse {
  id: string
  nombre: string
  apellido: string
  cedula: string
  correo: string
  telefono: string
  direccion: string
  fechaNacimiento: string
  fechaIngreso: string
  edad: number
  estadoHabilitacion: string
  tipoSocio: string
  nivelTecnico: string
  rolSistema: string
}

interface MfaSetupData {
  otpAuthUri: string
  base32Secret: string
}

// ─── Zod schemas ─────────────────────────────────────────────────────────────

const mfaCodeSchema = z.object({
  code: z
    .string()
    .length(6, "El código debe tener exactamente 6 dígitos")
    .regex(/^\d+$/, "Solo dígitos"),
})
type MfaCodeForm = z.infer<typeof mfaCodeSchema>

const editPerfilSchema = z.object({
  correo: z.string().email("Correo inválido").max(255).or(z.literal("")),
  telefono: z
    .string()
    .max(15, "Máximo 15 dígitos")
    .regex(/^\d*$/, "Solo dígitos, sin espacios ni guiones")
    .optional(),
  direccion: z.string().max(500).optional(),
})
type EditPerfilForm = z.infer<typeof editPerfilSchema>

const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Obligatorio"),
    newPassword: z
      .string()
      .min(12, "Mínimo 12 caracteres")
      .refine(
        (p) => /[A-Z]/.test(p) && /[a-z]/.test(p) && /[0-9]/.test(p) && /[^a-zA-Z0-9\s]/.test(p),
        "La contraseña no cumple los requisitos de seguridad",
      ),
    confirmPassword: z.string().min(1, "Obligatorio"),
    totpCode: z.string().optional(),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    message: "Las contraseñas no coinciden",
    path: ["confirmPassword"],
  })
type ChangePasswordForm = z.infer<typeof changePasswordSchema>

// ─── Helpers ─────────────────────────────────────────────────────────────────

function Field({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="space-y-1">
      <dt className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">
        {label}
      </dt>
      <dd className="text-sm font-medium text-foreground">{value ?? "—"}</dd>
    </div>
  )
}

function Card({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-border/50 bg-card/40 backdrop-blur-sm shadow-sm p-5 space-y-4">
      <div className="flex items-center justify-between border-b border-border/30 pb-3">
        <h2 className="text-base font-bold text-foreground">{title}</h2>
        {action}
      </div>
      <div className="pt-1">
        {children}
      </div>
    </div>
  )
}

// ─── Password checklist ───────────────────────────────────────────────────────

const PASSWORD_RULES = [
  { label: "Mínimo 12 caracteres",  test: (p: string) => p.length >= 12 },
  { label: "Una letra mayúscula",   test: (p: string) => /[A-Z]/.test(p) },
  { label: "Una letra minúscula",   test: (p: string) => /[a-z]/.test(p) },
  { label: "Un número (0–9)",       test: (p: string) => /[0-9]/.test(p) },
  { label: "Un símbolo (!@#$…)",    test: (p: string) => /[^a-zA-Z0-9\s]/.test(p) },
]

function PasswordChecklist({ password }: { password: string }) {
  const empty = password.length === 0
  return (
    <ul className="mt-2 grid grid-cols-1 gap-y-1 sm:grid-cols-2">
      {PASSWORD_RULES.map(({ label, test }) => {
        const passed = test(password)
        return (
          <li
            key={label}
            className={cn(
              "flex items-center gap-1.5 text-xs",
              empty    ? "text-muted-foreground"
              : passed ? "text-green-500 dark:text-green-400"
                       : "text-destructive",
            )}
          >
            {empty ? (
              <span className="h-3.5 w-3.5 shrink-0 rounded-full border border-current" />
            ) : passed ? (
              <Check className="h-3.5 w-3.5 shrink-0" />
            ) : (
              <XIcon className="h-3.5 w-3.5 shrink-0" />
            )}
            {label}
          </li>
        )
      })}
    </ul>
  )
}

// ─── MFA Section ─────────────────────────────────────────────────────────────

type MfaView = "idle" | "setup" | "disabling"

function MfaSection() {
  const [view, setView] = useState<MfaView>("idle")
  const [setupData, setSetupData] = useState<MfaSetupData | null>(null)
  const [copied, setCopied] = useState(false)

  const { data: mfaStatus, refetch: refetchStatus } = useQuery({
    queryKey: ["mfa-status"],
    queryFn: () =>
      api.get<{ data: { totpEnabled: boolean } }>("/v1/auth/mfa/status").then((r) => r.data.data),
  })
  const totpEnabled = mfaStatus?.totpEnabled ?? false

  const confirmForm = useForm<MfaCodeForm>({
    resolver: zodResolver(mfaCodeSchema),
  })
  const disableForm = useForm<MfaCodeForm>({
    resolver: zodResolver(mfaCodeSchema),
  })

  // Setup MFA — genera el secret y URI
  const setupMutation = useMutation({
    mutationFn: () =>
      api.post<{ data: MfaSetupData }>("/v1/auth/mfa/setup").then((r) => r.data.data),
    onSuccess: (data) => {
      setSetupData(data)
      setView("setup")
    },
    onError: () => {
      toast.error("Error al iniciar la configuración 2FA")
    },
  })

  // Confirmar activación 2FA
  const confirmMutation = useMutation({
    mutationFn: (code: string) => api.post("/v1/auth/mfa/confirm", { code }),
    onSuccess: () => {
      toast.success("Autenticación de dos factores activada")
      setView("idle")
      setSetupData(null)
      confirmForm.reset()
      refetchStatus()
    },
    onError: (err: unknown) => {
      const axiosError = err as { response?: { data?: { message?: string } } }
      toast.error(axiosError.response?.data?.message || "Código inválido")
    },
  })

  // Desactivar 2FA
  const disableMutation = useMutation({
    mutationFn: (code: string) => api.delete("/v1/auth/mfa", { data: { code } }),
    onSuccess: () => {
      toast.success("Autenticación de dos factores desactivada")
      setView("idle")
      disableForm.reset()
      refetchStatus()
    },
    onError: (err: unknown) => {
      const axiosError = err as { response?: { data?: { message?: string } } }
      toast.error(axiosError.response?.data?.message || "Código inválido o 2FA no activo")
    },
  })

  const copySecret = async () => {
    if (!setupData) return
    await navigator.clipboard.writeText(setupData.base32Secret)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  if (view === "setup" && setupData) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Escanea el código QR con tu aplicación autenticadora (Google Authenticator, Authy, etc.)
          o ingresa el código manualmente.
        </p>

        {/* QR code */}
        <div className="flex justify-center rounded-md bg-white p-4">
          <QRCodeSVG value={setupData.otpAuthUri} size={200} />
        </div>

        {/* Código secreto como alternativa */}
        <details className="rounded-md border border-border">
          <summary className="cursor-pointer px-4 py-2 text-xs font-medium text-muted-foreground select-none hover:text-foreground transition-colors">
            No puedo escanear el QR — ingresar código manualmente
          </summary>
          <div className="border-t border-border p-4 space-y-2">
            <p className="text-xs text-muted-foreground">
              Copia este código en tu aplicación autenticadora.
            </p>
            <div className="flex items-center gap-2">
              <code className="flex-1 break-all rounded bg-muted px-3 py-2 text-sm font-mono text-foreground select-all">
                {setupData.base32Secret}
              </code>
              <button
                onClick={copySecret}
                className="shrink-0 rounded-md border border-border px-3 py-2 text-xs font-medium text-foreground hover:bg-accent transition-colors"
              >
                {copied ? "Copiado" : "Copiar"}
              </button>
            </div>
          </div>
        </details>

        <form
          onSubmit={confirmForm.handleSubmit((d) => confirmMutation.mutate(d.code))}
          className="space-y-4 pt-2"
        >
          <div className="space-y-1.5">
            <label htmlFor="confirm-code" className="text-sm font-bold text-foreground">
              Confirma con un código de tu app
            </label>
            <input
              id="confirm-code"
              type="text"
              {...confirmForm.register("code")}
              onChange={(e) =>
                confirmForm.setValue("code", e.target.value.replace(/\D/g, "").slice(0, 6))
              }
              placeholder="123 456"
              maxLength={6}
              inputMode="numeric"
              autoComplete="one-time-code"
              className="flex h-12 w-full rounded-xl border border-input bg-background/50 px-4 text-center text-2xl font-mono tracking-[0.5em] text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 shadow-inner"
            />
            {confirmForm.formState.errors.code && (
              <p className="text-xs font-medium text-destructive">
                {confirmForm.formState.errors.code.message}
              </p>
            )}
          </div>
          <div className="flex flex-col sm:flex-row gap-3">
            <button
              type="submit"
              disabled={confirmMutation.isPending}
              className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg bg-primary px-6 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-sm"
            >
              {confirmMutation.isPending ? "Verificando..." : "Activar 2FA"}
            </button>
            <button
              type="button"
              onClick={() => { setView("idle"); setSetupData(null) }}
              className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg border border-border bg-background px-6 text-sm font-bold text-foreground hover:bg-accent transition-colors"
            >
              Cancelar
            </button>
          </div>
        </form>
      </div>
    )
  }

  if (view === "disabling") {
    return (
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Ingresa un código actual de tu app autenticadora para desactivar el 2FA.
        </p>
        <form
          onSubmit={disableForm.handleSubmit((d) => disableMutation.mutate(d.code))}
          className="space-y-4 pt-2"
        >
          <div className="space-y-1.5">
            <label htmlFor="disable-code" className="text-sm font-bold text-foreground">
              Código actual de tu app
            </label>
            <input
              id="disable-code"
              type="text"
              {...disableForm.register("code")}
              onChange={(e) =>
                disableForm.setValue("code", e.target.value.replace(/\D/g, "").slice(0, 6))
              }
              placeholder="123 456"
              maxLength={6}
              inputMode="numeric"
              autoComplete="one-time-code"
              className="flex h-12 w-full rounded-xl border border-input bg-background/50 px-4 text-center text-2xl font-mono tracking-[0.5em] text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-destructive/50 shadow-inner"
            />
            {disableForm.formState.errors.code && (
              <p className="text-xs font-medium text-destructive">
                {disableForm.formState.errors.code.message}
              </p>
            )}
          </div>
          <div className="flex flex-col sm:flex-row gap-3">
            <button
              type="submit"
              disabled={disableMutation.isPending}
              className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg bg-destructive px-6 text-sm font-bold text-destructive-foreground hover:bg-destructive/90 disabled:opacity-50 transition-colors shadow-sm"
            >
              {disableMutation.isPending ? "Desactivando..." : "Desactivar 2FA"}
            </button>
            <button
              type="button"
              onClick={() => { setView("idle"); disableForm.reset() }}
              className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg border border-border bg-background px-6 text-sm font-bold text-foreground hover:bg-accent transition-colors"
            >
              Cancelar
            </button>
          </div>
        </form>
      </div>
    )
  }

  // idle
  return (
    <div className="space-y-5">
      {/* Badge de estado */}
      {totpEnabled ? (
        <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 px-4 py-2.5">
          <span className="h-2 w-2 rounded-full bg-emerald-500 shrink-0" />
          <span className="text-sm font-semibold text-emerald-600 dark:text-emerald-400">2FA activado</span>
        </div>
      ) : (
        <div className="flex items-center gap-2 rounded-lg bg-amber-500/10 border border-amber-500/20 px-4 py-2.5">
          <span className="h-2 w-2 rounded-full bg-amber-500 shrink-0" />
          <span className="text-sm font-semibold text-amber-600 dark:text-amber-400">Se recomienda activar 2FA</span>
        </div>
      )}
      <p className="text-sm text-muted-foreground">
        Añade una capa extra de seguridad a tu cuenta requiriendo un código dinámico de tu app autenticadora cada vez que inicies sesión.
      </p>
      <div className="flex flex-col sm:flex-row gap-3">
        <button
          onClick={() => setupMutation.mutate()}
          disabled={setupMutation.isPending}
          className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg bg-primary px-6 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-sm"
        >
          {setupMutation.isPending ? "Preparando..." : totpEnabled ? "Reconfigurar 2FA" : "Activar 2FA"}
        </button>
        {totpEnabled && (
          <button
            onClick={() => setView("disabling")}
            className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg border border-destructive/40 bg-background px-6 text-sm font-bold text-destructive hover:bg-destructive/5 transition-colors"
          >
            Desactivar 2FA
          </button>
        )}
      </div>
    </div>
  )
}

// ─── Edit Perfil Section ──────────────────────────────────────────────────────

function EditPerfilSection({ data, onDone }: { data: SocioResponse; onDone: () => void }) {
  const queryClient = useQueryClient()

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } =
    useForm<EditPerfilForm>({
      resolver: zodResolver(editPerfilSchema),
      defaultValues: {
        correo: data.correo ?? "",
        telefono: data.telefono ?? "",
        direccion: data.direccion ?? "",
      },
    })

  const mutation = useMutation({
    mutationFn: (form: EditPerfilForm) => api.patch("/v1/socios/me", form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["perfil"] })
      toast.success("Datos actualizados correctamente")
      onDone()
    },
    onError: (err: unknown) => {
      const axiosError = err as { response?: { data?: { message?: string; data?: Record<string, string> } } }
      const fieldErrors = axiosError.response?.data?.data
      if (fieldErrors && Object.keys(fieldErrors).length > 0) {
        toast.error(Object.values(fieldErrors)[0])
      } else {
        toast.error(axiosError.response?.data?.message || "Error al actualizar datos")
      }
    },
  })

  return (
    <form onSubmit={handleSubmit((d: EditPerfilForm) => mutation.mutate(d))} className="space-y-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {([
          { name: "correo" as const, label: "Correo electrónico", type: "email" },
          { name: "telefono" as const, label: "Teléfono", type: "text" },
        ]).map(({ name, label, type }) => (
          <div key={name} className="space-y-1.5">
            <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">{label}</label>
            <input
              type={type}
              className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              {...register(name)}
            />
            {errors[name] && <p className="text-xs font-medium text-destructive">{errors[name]?.message}</p>}
          </div>
        ))}
        <div className="space-y-1.5 sm:col-span-2">
          <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">Dirección</label>
          <input
            type="text"
            className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            {...register("direccion")}
          />
        </div>
      </div>

      <p className="text-xs text-muted-foreground/70 italic pt-1">
        Los contactos de emergencia y la información médica se gestionan en sus respectivas pestañas.
      </p>

      <div className="flex flex-col sm:flex-row gap-3 pt-2">
        <button
          type="submit"
          disabled={isSubmitting || mutation.isPending}
          className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg bg-primary px-6 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-sm"
        >
          {mutation.isPending ? "Guardando..." : "Guardar cambios"}
        </button>
        <button
          type="button"
          onClick={() => { reset(); onDone() }}
          className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg border border-border bg-background px-6 text-sm font-bold text-foreground hover:bg-accent transition-colors"
        >
          Cancelar
        </button>
      </div>
    </form>
  )
}

// ─── Change Password Section ─────────────────────────────────────────────────

function ChangePasswordSection() {
  const [totpDialogOpen, setTotpDialogOpen] = useState(false)
  const [totpCode, setTotpCode] = useState("")
  const [totpError, setTotpError] = useState("")

  const {
    register,
    handleSubmit,
    reset,
    watch,
    getValues,
    formState: { errors },
  } = useForm<ChangePasswordForm>({ resolver: zodResolver(changePasswordSchema) })

  // eslint-disable-next-line react-hooks/incompatible-library
  const newPasswordValue = watch("newPassword") ?? ""

  // Preflight: valida contraseñas en el backend sin cambiar nada
  const preflightMutation = useMutation({
    mutationFn: () => {
      const d = getValues()
      return api
        .post<{ data: { totpRequired: boolean } }>("/v1/auth/change-password/verify", {
          currentPassword: d.currentPassword,
          newPassword: d.newPassword,
          confirmPassword: d.confirmPassword,
        })
        .then((r) => r.data.data)
    },
    onSuccess: (result) => {
      if (result.totpRequired) {
        setTotpCode("")
        setTotpError("")
        setTotpDialogOpen(true)
      } else {
        changeMutation.mutate(undefined)
      }
    },
    onError: (err: unknown) => {
      const axiosError = err as { response?: { data?: { message?: string } } }
      toast.error(axiosError.response?.data?.message || "Error al validar la contraseña")
    },
  })

  // Cambio real de contraseña (con TOTP si aplica)
  const changeMutation = useMutation({
    mutationFn: (code?: string) => {
      const d = getValues()
      return api.post("/v1/auth/change-password", {
        currentPassword: d.currentPassword,
        newPassword: d.newPassword,
        confirmPassword: d.confirmPassword,
        totpCode: code || undefined,
      })
    },
    onSuccess: () => {
      toast.success("Contraseña actualizada. Vuelve a iniciar sesión en otros dispositivos.")
      reset()
      setTotpCode("")
      setTotpDialogOpen(false)
    },
    onError: (err: unknown) => {
      const axiosError = err as { response?: { data?: { message?: string } } }
      const msg = axiosError.response?.data?.message || "Error al cambiar la contraseña"
      if (totpDialogOpen) {
        setTotpError(msg)
      } else {
        toast.error(msg)
      }
    },
  })

  const isPending = preflightMutation.isPending || changeMutation.isPending

  const handleTotpConfirm = () => {
    if (!/^\d{6}$/.test(totpCode)) {
      setTotpError("Ingresa el código de 6 dígitos")
      return
    }
    setTotpError("")
    changeMutation.mutate(totpCode)
  }

  return (
    <>
      <form onSubmit={handleSubmit(() => preflightMutation.mutate())} className="space-y-4">
        <div className="space-y-1.5">
          <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">Contraseña actual</label>
          <input
            type="password"
            autoComplete="current-password"
            className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            {...register("currentPassword")}
          />
          {errors.currentPassword && (
            <p className="text-xs font-medium text-destructive">{errors.currentPassword.message}</p>
          )}
        </div>

        <div className="space-y-1.5">
          <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">Nueva contraseña</label>
          <input
            type="password"
            autoComplete="new-password"
            className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            {...register("newPassword")}
          />
          <PasswordChecklist password={newPasswordValue} />
          {errors.newPassword && (
            <p className="text-xs font-medium text-destructive">{errors.newPassword.message}</p>
          )}
        </div>

        <div className="space-y-1.5">
          <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">Confirmar nueva contraseña</label>
          <input
            type="password"
            autoComplete="new-password"
            className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            {...register("confirmPassword")}
          />
          {errors.confirmPassword && (
            <p className="text-xs font-medium text-destructive">{errors.confirmPassword.message}</p>
          )}
        </div>

        <div className="pt-2">
          <button
            type="submit"
            disabled={isPending}
            className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg bg-primary px-6 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-sm"
          >
            {isPending && !totpDialogOpen ? "Validando..." : "Cambiar contraseña"}
          </button>
        </div>
      </form>

      {/* Dialog 2FA */}
      <Dialog open={totpDialogOpen} onOpenChange={(open) => { if (!open) setTotpDialogOpen(false) }}>
        <DialogContent className="sm:max-w-sm" showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>Verificación de dos factores</DialogTitle>
            <DialogDescription>
              Ingresa el código de tu app autenticadora para confirmar el cambio de contraseña.
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col items-center gap-4 py-2">
            <input
              type="text"
              inputMode="numeric"
              maxLength={6}
              autoComplete="one-time-code"
              autoFocus
              placeholder="000000"
              value={totpCode}
              onChange={(e) => {
                setTotpCode(e.target.value.replace(/\D/g, "").slice(0, 6))
                setTotpError("")
              }}
              onKeyDown={(e) => { if (e.key === "Enter") handleTotpConfirm() }}
              className="flex h-14 w-48 rounded-xl border border-input bg-background/50 px-4 text-center font-mono text-3xl tracking-[0.5em] text-foreground placeholder:tracking-widest shadow-inner focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50"
            />
            {totpError && (
              <p className="text-sm font-medium text-destructive">{totpError}</p>
            )}
          </div>

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={() => setTotpDialogOpen(false)}
              className="inline-flex h-10 items-center justify-center rounded-lg border border-border bg-background px-5 text-sm font-bold text-foreground hover:bg-accent transition-colors"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={handleTotpConfirm}
              disabled={changeMutation.isPending}
              className="inline-flex h-10 items-center justify-center rounded-lg bg-primary px-5 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-sm"
            >
              {changeMutation.isPending ? "Verificando..." : "Confirmar"}
            </button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

// ─── API Keys Section ────────────────────────────────────────────────────────

interface ApiKeyData {
  id: string
  nombre: string
  createdAt: string
  expiresAt: string | null
  lastUsedAt: string | null
}

interface CreatedApiKey extends ApiKeyData {
  key: string
}

function CreateApiKeyModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: () => void
}) {
  const [nombre, setNombre] = useState("")
  const [error, setError] = useState("")
  const [createdKey, setCreatedKey] = useState<CreatedApiKey | null>(null)
  const [copied, setCopied] = useState(false)

  const createMutation = useMutation({
    mutationFn: () =>
      api.post<{ data: CreatedApiKey }>("/v1/profile/api-keys", { nombre }).then((r) => r.data.data),
    onSuccess: (data) => {
      setCreatedKey(data)
    },
    onError: (err: unknown) => {
      const axiosError = err as { response?: { data?: { message?: string } } }
      setError(axiosError.response?.data?.message ?? "Error al generar la API Key")
    },
  })

  const handleCopy = async () => {
    if (!createdKey) return
    await navigator.clipboard.writeText(createdKey.key)
    setCopied(true)
    setTimeout(() => setCopied(false), 2500)
  }

  const handleClose = () => {
    setNombre("")
    setError("")
    setCreatedKey(null)
    setCopied(false)
    if (createdKey) onCreated()
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) handleClose() }}>
      <DialogContent className="sm:max-w-md" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{createdKey ? "API Key generada" : "Nueva API Key"}</DialogTitle>
          <DialogDescription>
            {createdKey
              ? "Copia la key ahora — no se volverá a mostrar."
              : "Ingresa un nombre descriptivo para identificar esta key."}
          </DialogDescription>
        </DialogHeader>

        {createdKey ? (
          <div className="space-y-4 py-2">
            <div className="rounded-xl border border-amber-400/40 bg-amber-500/10 p-3 text-xs font-semibold text-amber-700 dark:text-amber-400">
              Guarda esta key en un lugar seguro. No se puede recuperar después.
            </div>
            <div className="flex items-center gap-2">
              <code className="flex-1 break-all rounded-lg border border-border bg-muted px-3 py-2 text-xs font-mono text-foreground select-all">
                {createdKey.key}
              </code>
              <button
                onClick={handleCopy}
                className="shrink-0 inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-background hover:bg-accent transition-colors"
              >
                {copied ? <Check className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
              </button>
            </div>
            <button
              onClick={handleClose}
              className="inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary px-6 text-sm font-bold text-primary-foreground hover:bg-primary/90 transition-colors shadow-sm"
            >
              Ya la copié — cerrar
            </button>
          </div>
        ) : (
          <div className="space-y-4 py-2">
            <div className="space-y-1.5">
              <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">
                Nombre descriptivo
              </label>
              <input
                type="text"
                value={nombre}
                onChange={(e) => { setNombre(e.target.value); setError("") }}
                placeholder="Ej: MCP Claude Desktop"
                maxLength={100}
                className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              />
              {error && <p className="text-xs font-medium text-destructive">{error}</p>}
            </div>
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button
                onClick={handleClose}
                className="inline-flex h-10 items-center justify-center rounded-lg border border-border bg-background px-5 text-sm font-bold text-foreground hover:bg-accent transition-colors"
              >
                Cancelar
              </button>
              <button
                onClick={() => { if (nombre.trim()) createMutation.mutate(); else setError("El nombre es obligatorio") }}
                disabled={createMutation.isPending}
                className="inline-flex h-10 items-center justify-center rounded-lg bg-primary px-5 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-sm"
              >
                {createMutation.isPending ? "Generando..." : "Generar"}
              </button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function ApiKeysSection() {
  const queryClient = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)

  const { data: keys, isLoading } = useQuery({
    queryKey: ["api-keys"],
    queryFn: () =>
      api.get<{ data: ApiKeyData[] }>("/v1/profile/api-keys").then((r) => r.data.data),
    staleTime: 30 * 1000,
  })

  const revokeMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/v1/profile/api-keys/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["api-keys"] })
      toast.success("API Key revocada")
    },
    onError: () => toast.error("No se pudo revocar la key"),
  })

  const list = keys ?? []

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {list.length} key{list.length !== 1 ? "s" : ""} activa{list.length !== 1 ? "s" : ""} (máx. 5)
        </p>
        <button
          onClick={() => setModalOpen(true)}
          disabled={list.length >= 5}
          className="inline-flex h-9 items-center gap-2 rounded-lg border border-border bg-background px-4 text-sm font-bold text-foreground hover:bg-accent disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <Plus className="h-4 w-4" />
          Nueva key
        </button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(2)].map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-lg bg-muted" />
          ))}
        </div>
      ) : list.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-border/50 bg-background/50 p-8 text-center">
          <div className="rounded-full bg-muted p-3">
            <Key className="h-5 w-5 text-muted-foreground/50" />
          </div>
          <p className="text-sm font-medium text-muted-foreground">No tienes API Keys activas</p>
          <p className="text-xs text-muted-foreground/70">Genera una para conectar el asistente MCP</p>
        </div>
      ) : (
        <ul className="divide-y divide-border/50 rounded-xl border border-border/50 bg-background/50 backdrop-blur-sm">
          {list.map((k) => (
            <li key={k.id} className="flex items-center justify-between gap-4 px-4 py-3 hover:bg-accent/20 transition-colors">
              <div className="min-w-0 space-y-0.5">
                <p className="text-sm font-bold text-foreground truncate">{k.nombre}</p>
                <p className="text-xs font-medium text-muted-foreground/80">
                  Creada {formatRelative(k.createdAt)}
                  {k.lastUsedAt ? ` · Último uso ${formatRelative(k.lastUsedAt)}` : " · Sin uso aún"}
                </p>
              </div>
              <button
                onClick={() => revokeMutation.mutate(k.id)}
                disabled={revokeMutation.isPending}
                className="shrink-0 inline-flex h-8 w-8 items-center justify-center rounded-md border border-border bg-background text-muted-foreground hover:bg-destructive hover:text-destructive-foreground hover:border-destructive disabled:opacity-50 transition-colors"
                title="Revocar"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </li>
          ))}
        </ul>
      )}

      <CreateApiKeyModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={() => queryClient.invalidateQueries({ queryKey: ["api-keys"] })}
      />
    </div>
  )
}

// ─── Sessions Section ────────────────────────────────────────────────────────

interface SessionData {
  sessionId: string
  platform: string
  browser: string | null
  os: string | null
  city: string | null
  country: string | null
  ipAddress: string | null
  createdAt: string
  lastUsedAt: string | null
  isCurrent: boolean
}

function formatRelative(iso: string | null) {
  if (!iso) return "Nunca"
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return "Hace un momento"
  if (mins < 60) return `Hace ${mins} minuto${mins > 1 ? "s" : ""}`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `Hace ${hrs} hora${hrs > 1 ? "s" : ""}`
  const days = Math.floor(hrs / 24)
  if (days < 7) return `Hace ${days} día${days > 1 ? "s" : ""}`
  const weeks = Math.floor(days / 7)
  return `Hace ${weeks} semana${weeks > 1 ? "s" : ""}`
}

function SessionsSection() {
  const queryClient = useQueryClient()

  const { data: sessions, isLoading } = useQuery({
    queryKey: ["sessions"],
    queryFn: () =>
      api.get<{ data: SessionData[] }>("/v1/auth/sessions").then((r) => r.data.data),
    staleTime: 30 * 1000,
  })

  const revokeMutation = useMutation({
    mutationFn: (sessionId: string) => api.delete(`/v1/auth/sessions/${sessionId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sessions"] })
      toast.success("Sesión cerrada")
    },
    onError: () => toast.error("No se pudo cerrar la sesión"),
  })

  const revokeOthersMutation = useMutation({
    mutationFn: () => api.delete("/v1/auth/sessions/others"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sessions"] })
      toast.success("Otras sesiones cerradas")
    },
    onError: () => toast.error("No se pudo cerrar las sesiones"),
  })

  const reportMutation = useMutation({
    mutationFn: () => api.post("/v1/auth/report-suspicious"),
    onSuccess: () => {
      queryClient.clear()
      toast.success("Actividad reportada. Todas las sesiones han sido cerradas.")
    },
    onError: () => toast.error("Error al reportar actividad sospechosa"),
  })

  if (isLoading) {
    return (
      <div className="space-y-2">
        {[...Array(2)].map((_, i) => (
          <div key={i} className="h-16 animate-pulse rounded-lg bg-muted" />
        ))}
      </div>
    )
  }

  const list = sessions ?? []
  const others = list.filter((s) => !s.isCurrent)

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        {list.length} sesión{list.length !== 1 ? "es" : ""} activa{list.length !== 1 ? "s" : ""}
      </p>

      <ul className="divide-y divide-border/50 rounded-xl border border-border/50 bg-background/50 backdrop-blur-sm">
        {list.map((s) => (
          <li key={s.sessionId} className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 px-4 py-3 hover:bg-accent/20 transition-colors">
            <div className="flex items-start gap-4 min-w-0">
              <div className="mt-1 shrink-0 text-muted-foreground bg-muted p-2 rounded-lg">
                {s.platform === "MOBILE" ? (
                  <Smartphone className="h-4 w-4" />
                ) : (
                  <Monitor className="h-4 w-4" />
                )}
              </div>
              <div className="min-w-0 space-y-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-sm font-bold text-foreground">
                    {[s.browser, s.os].filter(Boolean).join(" · ") || "Dispositivo desconocido"}
                  </span>
                  {s.isCurrent && (
                    <span className="inline-flex items-center rounded-full bg-primary/10 px-2.5 py-0.5 text-[10px] font-extrabold uppercase tracking-widest text-primary">
                      Esta sesión
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-3 flex-wrap text-xs font-medium text-muted-foreground/80">
                  {(s.city || s.country) && (
                    <span className="flex items-center gap-1">
                      <MapPin className="h-3 w-3" />
                      {[s.city, s.country].filter(Boolean).join(", ")}
                    </span>
                  )}
                  {s.ipAddress && (
                    <span className="flex items-center gap-1">
                      <Globe className="h-3 w-3" />
                      {s.ipAddress}
                    </span>
                  )}
                  <span className="flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {formatRelative(s.lastUsedAt ?? s.createdAt)}
                  </span>
                </div>
              </div>
            </div>
            {!s.isCurrent && (
              <button
                onClick={() => revokeMutation.mutate(s.sessionId)}
                disabled={revokeMutation.isPending}
                className="shrink-0 inline-flex h-8 items-center justify-center rounded-md border border-border bg-background px-3 text-xs font-bold text-foreground hover:bg-destructive hover:text-destructive-foreground hover:border-destructive disabled:opacity-50 transition-colors w-full sm:w-auto"
              >
                Cerrar
              </button>
            )}
          </li>
        ))}
      </ul>

      <div className="flex flex-col sm:flex-row gap-3 pt-2">
        {others.length > 0 && (
          <button
            onClick={() => revokeOthersMutation.mutate()}
            disabled={revokeOthersMutation.isPending}
            className="inline-flex h-9 items-center justify-center rounded-lg border border-border bg-background px-4 text-sm font-bold text-foreground hover:bg-accent disabled:opacity-50 transition-colors w-full sm:w-auto"
          >
            {revokeOthersMutation.isPending ? "Cerrando..." : "Cerrar todas las demás"}
          </button>
        )}
        <button
          onClick={() => reportMutation.mutate()}
          disabled={reportMutation.isPending}
          className="inline-flex h-9 items-center gap-2 justify-center rounded-lg bg-destructive/10 px-4 text-sm font-bold text-destructive hover:bg-destructive hover:text-destructive-foreground disabled:opacity-50 transition-colors w-full sm:w-auto"
        >
          <AlertTriangle className="h-4 w-4" />
          {reportMutation.isPending ? "Reportando..." : "Reportar actividad sospechosa"}
        </button>
      </div>
    </div>
  )
}

// ─── Emergency Contacts Tab ──────────────────────────────────────────────────

const RELACION_OPTIONS = [
  "Cónyuge / Pareja", "Madre", "Padre", "Hermano/a", "Hijo/a",
  "Amigo/a", "Compañero/a de trabajo", "Otro",
]

function ContactoForm({
  index,
  optional,
  value,
  onChange,
  errors: fieldErrors,
}: {
  index: number
  optional: boolean
  value: { nombreCompleto: string; relacion: string; celular: string; direccion: string }
  onChange: (field: string, v: string) => void
  errors: Record<string, string>
}) {
  return (
    <div className="space-y-4">
      <p className="text-[10px] font-extrabold uppercase tracking-[0.1em] text-primary bg-primary/10 inline-block px-2 py-1 rounded-md">
        Contacto {index + 1}{optional ? " (opcional)" : " (obligatorio)"}
      </p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">Nombre completo</label>
          <input
            type="text"
            value={value.nombreCompleto}
            onChange={(e) => onChange("nombreCompleto", e.target.value)}
            maxLength={200}
            className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
          {fieldErrors.nombreCompleto && <p className="text-xs text-destructive">{fieldErrors.nombreCompleto}</p>}
        </div>
        <div className="space-y-1.5">
          <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">Relación</label>
          <select
            value={value.relacion}
            onChange={(e) => onChange("relacion", e.target.value)}
            className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          >
            <option value="">Seleccionar...</option>
            {RELACION_OPTIONS.map((r) => <option key={r} value={r}>{r}</option>)}
          </select>
          {fieldErrors.relacion && <p className="text-xs text-destructive">{fieldErrors.relacion}</p>}
        </div>
        <div className="space-y-1.5">
          <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">Celular</label>
          <input
            type="text"
            inputMode="numeric"
            value={value.celular}
            onChange={(e) => onChange("celular", e.target.value.replace(/\D/g, "").slice(0, 15))}
            className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80">Dirección</label>
          <input
            type="text"
            value={value.direccion}
            onChange={(e) => onChange("direccion", e.target.value)}
            maxLength={500}
            className="flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>
      </div>
    </div>
  )
}

const emptyContact = { nombreCompleto: "", relacion: "", celular: "", direccion: "" }

function contactFromApi(c: EmergencyContact) {
  return { nombreCompleto: c.nombreCompleto, relacion: c.relacion, celular: c.celular ?? "", direccion: c.direccion ?? "" }
}

function EmergencyContactsTab() {
  const { data: contacts, isLoading } = useMyEmergencyContacts()
  const mutation = useUpsertEmergencyContacts()

  const [c1, setC1] = useState(emptyContact)
  const [c2, setC2] = useState(emptyContact)
  const [hasSecond, setHasSecond] = useState(false)
  const [errors, setErrors] = useState<{ c1: Record<string, string>; c2: Record<string, string> }>({ c1: {}, c2: {} })
  const [initialized, setInitialized] = useState(false)

  if (!isLoading && !initialized && contacts !== undefined) {
    const first = contacts.find((c) => c.orden === 1)
    const second = contacts.find((c) => c.orden === 2)
    if (first) setC1(contactFromApi(first))
    if (second) { setC2(contactFromApi(second)); setHasSecond(true) }
    setInitialized(true)
  }

  const validate = () => {
    const e1: Record<string, string> = {}
    const e2: Record<string, string> = {}
    if (!c1.nombreCompleto.trim()) e1.nombreCompleto = "Obligatorio"
    if (!c1.relacion) e1.relacion = "Obligatorio"
    if (hasSecond) {
      if (!c2.nombreCompleto.trim()) e2.nombreCompleto = "Obligatorio"
      if (!c2.relacion) e2.relacion = "Obligatorio"
    }
    setErrors({ c1: e1, c2: e2 })
    return Object.keys(e1).length === 0 && Object.keys(e2).length === 0
  }

  const handleSave = async () => {
    if (!validate()) return
    const contactos = [
      { orden: 1, ...c1 },
      ...(hasSecond ? [{ orden: 2, ...c2 }] : []),
    ]
    try {
      await mutation.mutateAsync(contactos)
      toast.success("Contactos de emergencia actualizados")
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg || "Error al guardar contactos")
    }
  }

  const update1 = (field: string, v: string) => setC1((p) => ({ ...p, [field]: v }))
  const update2 = (field: string, v: string) => setC2((p) => ({ ...p, [field]: v }))

  if (isLoading) {
    return <div className="space-y-3">{[...Array(3)].map((_, i) => <div key={i} className="h-12 animate-pulse rounded-lg bg-muted" />)}</div>
  }

  return (
    <div className="space-y-6">
      <ContactoForm index={0} optional={false} value={c1} onChange={update1} errors={errors.c1} />

      {hasSecond ? (
        <div className="border-t border-border/30 pt-5 space-y-4">
          <ContactoForm index={1} optional value={c2} onChange={update2} errors={errors.c2} />
          <button
            type="button"
            onClick={() => { setC2(emptyContact); setHasSecond(false) }}
            className="flex items-center gap-1.5 text-xs font-semibold text-destructive hover:text-destructive/80 transition-colors"
          >
            <XIcon className="h-3.5 w-3.5" /> Quitar contacto 2
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setHasSecond(true)}
          className="flex items-center gap-1.5 text-xs font-semibold text-primary hover:text-primary/80 transition-colors"
        >
          <Plus className="h-3.5 w-3.5" /> Agregar segundo contacto
        </button>
      )}

      <div className="pt-2">
        <button
          type="button"
          onClick={handleSave}
          disabled={mutation.isPending}
          className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg bg-primary px-6 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-sm"
        >
          {mutation.isPending ? "Guardando..." : "Guardar contactos"}
        </button>
      </div>
    </div>
  )
}

// ─── Medical Info Tab ─────────────────────────────────────────────────────────

const BLOOD_TYPES = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"]

function MedicalInfoTab() {
  const { data: info, isLoading } = useMyMedicalInfo()
  const mutation = useUpdateMedicalInfo()

  const [form, setForm] = useState({
    bloodType: "",
    hasRelevantAllergies: false,
    allergiesDetail: "",
    hasRelevantMedicalCondition: false,
    medicalConditionDetail: "",
    usesEmergencyMedication: false,
    emergencyMedicationDetail: "",
    additionalNotes: "",
  })
  const [initialized, setInitialized] = useState(false)

  if (!isLoading && !initialized && info !== undefined) {
    setForm({
      bloodType: info?.bloodType ?? "",
      hasRelevantAllergies: info?.hasRelevantAllergies ?? false,
      allergiesDetail: info?.allergiesDetail ?? "",
      hasRelevantMedicalCondition: info?.hasRelevantMedicalCondition ?? false,
      medicalConditionDetail: info?.medicalConditionDetail ?? "",
      usesEmergencyMedication: info?.usesEmergencyMedication ?? false,
      emergencyMedicationDetail: info?.emergencyMedicationDetail ?? "",
      additionalNotes: info?.additionalNotes ?? "",
    })
    setInitialized(true)
  }

  const set = (field: string) => (v: string | boolean) =>
    setForm((p) => ({ ...p, [field]: v }))

  const handleSave = async () => {
    try {
      await mutation.mutateAsync({
        bloodType: form.bloodType || undefined,
        hasRelevantAllergies: form.hasRelevantAllergies,
        allergiesDetail: form.hasRelevantAllergies ? form.allergiesDetail : undefined,
        hasRelevantMedicalCondition: form.hasRelevantMedicalCondition,
        medicalConditionDetail: form.hasRelevantMedicalCondition ? form.medicalConditionDetail : undefined,
        usesEmergencyMedication: form.usesEmergencyMedication,
        emergencyMedicationDetail: form.usesEmergencyMedication ? form.emergencyMedicationDetail : undefined,
        additionalNotes: form.additionalNotes || undefined,
      })
      toast.success("Información médica actualizada")
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg || "Error al guardar información médica")
    }
  }

  if (isLoading) {
    return <div className="space-y-3">{[...Array(4)].map((_, i) => <div key={i} className="h-12 animate-pulse rounded-lg bg-muted" />)}</div>
  }

  const fieldClass = "flex h-10 w-full rounded-lg border border-input bg-background/50 px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
  const textareaClass = "w-full rounded-lg border border-input bg-background/50 px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
  const labelClass = "text-[11px] font-bold uppercase tracking-wider text-muted-foreground/80"

  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-amber-400/30 bg-amber-500/5 px-4 py-3 text-xs text-amber-700 dark:text-amber-400">
        Esta información es confidencial y solo se comparte con el Jefe de Salida y el equipo directivo en caso de emergencia.
      </div>

      {/* Tipo de sangre */}
      <div className="space-y-1.5">
        <label className={labelClass}>Tipo de sangre</label>
        <select
          value={form.bloodType}
          onChange={(e) => set("bloodType")(e.target.value)}
          className={fieldClass}
        >
          <option value="">— Sin especificar —</option>
          {BLOOD_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
      </div>

      {/* Alergias */}
      <div className="space-y-3">
        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={form.hasRelevantAllergies}
            onChange={(e) => set("hasRelevantAllergies")(e.target.checked)}
            className="h-4 w-4 accent-primary"
          />
          <span className="text-sm font-semibold text-foreground">Tengo alergias relevantes</span>
        </label>
        {form.hasRelevantAllergies && (
          <div className="pl-6 space-y-1.5">
            <label className={labelClass}>Detalle de alergias</label>
            <textarea
              value={form.allergiesDetail}
              onChange={(e) => set("allergiesDetail")(e.target.value)}
              rows={3}
              maxLength={2000}
              className={textareaClass}
              placeholder="Ej: alergia a la penicilina, picaduras de abejas..."
            />
          </div>
        )}
      </div>

      {/* Condición médica */}
      <div className="space-y-3">
        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={form.hasRelevantMedicalCondition}
            onChange={(e) => set("hasRelevantMedicalCondition")(e.target.checked)}
            className="h-4 w-4 accent-primary"
          />
          <span className="text-sm font-semibold text-foreground">Tengo condición médica relevante</span>
        </label>
        {form.hasRelevantMedicalCondition && (
          <div className="pl-6 space-y-1.5">
            <label className={labelClass}>Detalle de la condición</label>
            <textarea
              value={form.medicalConditionDetail}
              onChange={(e) => set("medicalConditionDetail")(e.target.value)}
              rows={3}
              maxLength={2000}
              className={textareaClass}
              placeholder="Ej: diabetes tipo 2, asma..."
            />
          </div>
        )}
      </div>

      {/* Medicación de emergencia */}
      <div className="space-y-3">
        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={form.usesEmergencyMedication}
            onChange={(e) => set("usesEmergencyMedication")(e.target.checked)}
            className="h-4 w-4 accent-primary"
          />
          <span className="text-sm font-semibold text-foreground">Uso medicación de emergencia</span>
        </label>
        {form.usesEmergencyMedication && (
          <div className="pl-6 space-y-1.5">
            <label className={labelClass}>Medicación y dosis</label>
            <textarea
              value={form.emergencyMedicationDetail}
              onChange={(e) => set("emergencyMedicationDetail")(e.target.value)}
              rows={3}
              maxLength={2000}
              className={textareaClass}
              placeholder="Ej: EpiPen (epinefrina 0.3mg), carry it at all times..."
            />
          </div>
        )}
      </div>

      {/* Notas adicionales */}
      <div className="space-y-1.5">
        <label className={labelClass}>Notas adicionales (opcional)</label>
        <textarea
          value={form.additionalNotes}
          onChange={(e) => set("additionalNotes")(e.target.value)}
          rows={3}
          maxLength={2000}
          className={textareaClass}
          placeholder="Cualquier información adicional relevante para emergencias..."
        />
      </div>

      <div className="pt-2">
        <button
          type="button"
          onClick={handleSave}
          disabled={mutation.isPending}
          className="inline-flex h-10 w-full sm:w-auto items-center justify-center rounded-lg bg-primary px-6 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-sm"
        >
          {mutation.isPending ? "Guardando..." : "Guardar información médica"}
        </button>
      </div>
    </div>
  )
}

// ─── Documentos Section ──────────────────────────────────────────────────────

const STAGE_LABEL: Record<string, string> = {
  REGISTRATION: "Registro",
  PROFILE_COMPLETION: "Completar perfil",
  ACTIVITY_ENROLLMENT: "Inscripción a actividades",
}

function DocCard({ doc, onAccepted }: { doc: LegalDoc; onAccepted: () => void }) {
  const [expanded, setExpanded] = useState(false)
  const [agreed, setAgreed] = useState(false)
  const acceptMutation = useAcceptDocument()

  const handleAccept = async () => {
    try {
      await acceptMutation.mutateAsync(doc.id)
      toast.success(`"${doc.title}" aceptado correctamente`)
      onAccepted()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
      toast.error(msg || "Error al aceptar el documento")
    }
  }

  return (
    <div className="rounded-xl border border-amber-400/40 bg-amber-500/5 p-4 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div className="space-y-0.5">
          <p className="text-sm font-bold text-foreground">{doc.title}</p>
          <p className="text-xs text-muted-foreground">
            {doc.code} · v{doc.version} · {STAGE_LABEL[doc.requiredStage] ?? doc.requiredStage}
          </p>
          {doc.description && (
            <p className="text-xs text-muted-foreground mt-1">{doc.description}</p>
          )}
        </div>
        <span className="inline-flex shrink-0 items-center rounded-full bg-amber-500/10 border border-amber-400/40 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-amber-700">
          Pendiente
        </span>
      </div>

      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="flex items-center gap-1.5 text-xs font-semibold text-primary hover:text-primary/80 transition-colors"
      >
        <ChevronDown className={cn("h-3.5 w-3.5 transition-transform", expanded && "rotate-180")} />
        {expanded ? "Ocultar contenido" : "Leer documento"}
      </button>

      {expanded && (
        <div className="max-h-64 overflow-y-auto rounded-lg border border-border bg-background/70 p-3 text-xs text-foreground prose prose-xs max-w-none">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{doc.content}</ReactMarkdown>
        </div>
      )}

      <label className="flex items-start gap-2 cursor-pointer">
        <input
          type="checkbox"
          checked={agreed}
          onChange={(e) => setAgreed(e.target.checked)}
          className="mt-0.5 h-4 w-4 accent-primary"
        />
        <span className="text-xs text-foreground leading-relaxed">
          He leído y acepto el documento <strong>{doc.title}</strong>
        </span>
      </label>

      <button
        type="button"
        disabled={!agreed || acceptMutation.isPending}
        onClick={handleAccept}
        className="inline-flex h-9 w-full sm:w-auto items-center justify-center rounded-lg bg-primary px-5 text-sm font-bold text-primary-foreground hover:bg-primary/90 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
      >
        {acceptMutation.isPending ? "Aceptando..." : "Confirmar aceptación"}
      </button>
    </div>
  )
}

function AcceptedDocRow({ acceptance }: { acceptance: LegalAcceptance }) {
  const date = new Date(acceptance.acceptedAt).toLocaleDateString("es-EC", {
    day: "numeric", month: "short", year: "numeric",
  })
  return (
    <li className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-accent/20 transition-colors">
      <div className="min-w-0 space-y-0.5">
        <p className="text-sm font-bold text-foreground truncate">{acceptance.documentTitle}</p>
        <p className="text-xs font-medium text-muted-foreground">
          {acceptance.documentCode} · v{acceptance.documentVersion}
        </p>
      </div>
      <div className="shrink-0 text-right space-y-0.5">
        <div className="flex items-center gap-1 justify-end">
          <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
          <span className="text-xs font-semibold text-emerald-600 dark:text-emerald-400">Aceptado</span>
        </div>
        <p className="text-xs text-muted-foreground">{date}</p>
      </div>
    </li>
  )
}

function DocumentosSection() {
  const qc = useQueryClient()
  const { data: allDocs, isLoading: loadingDocs } = useActiveDocuments()
  const { data: acceptances, isLoading: loadingAcceptances } = useMyAcceptances()

  const acceptedIds = new Set((acceptances ?? []).map((a) => a.documentId))
  const pendingRequired = (allDocs ?? []).filter((d) => d.required && !acceptedIds.has(d.id))

  const onAccepted = () => {
    qc.invalidateQueries({ queryKey: ["legal-acceptances", "me"] })
  }

  if (loadingDocs || loadingAcceptances) {
    return (
      <div className="space-y-3">
        {[...Array(2)].map((_, i) => (
          <div key={i} className="h-20 animate-pulse rounded-xl bg-muted" />
        ))}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {pendingRequired.length > 0 && (
        <div className="space-y-3">
          <p className="text-[10px] font-extrabold uppercase tracking-widest text-amber-700 dark:text-amber-400">
            Pendientes de aceptar ({pendingRequired.length})
          </p>
          <div className="space-y-3">
            {pendingRequired.map((doc) => (
              <DocCard key={doc.id} doc={doc} onAccepted={onAccepted} />
            ))}
          </div>
        </div>
      )}

      {(acceptances ?? []).length > 0 && (
        <div className="space-y-3">
          <p className="text-[10px] font-extrabold uppercase tracking-widest text-muted-foreground/80">
            Historial de aceptaciones
          </p>
          <ul className="divide-y divide-border/50 rounded-xl border border-border/50 bg-background/50 backdrop-blur-sm">
            {(acceptances ?? []).map((a) => (
              <AcceptedDocRow key={a.id} acceptance={a} />
            ))}
          </ul>
        </div>
      )}

      {pendingRequired.length === 0 && (acceptances ?? []).length === 0 && (
        <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-border/50 bg-background/50 p-10 text-center">
          <div className="rounded-full bg-muted p-3">
            <FileText className="h-5 w-5 text-muted-foreground/50" />
          </div>
          <p className="text-sm font-medium text-muted-foreground">No hay documentos legales activos</p>
        </div>
      )}

      {pendingRequired.length === 0 && (acceptances ?? []).length > 0 && (
        <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 px-4 py-3">
          <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
          <span className="text-sm font-semibold text-emerald-600 dark:text-emerald-400">
            Tienes todos los documentos requeridos al día
          </span>
        </div>
      )}
    </div>
  )
}

// ─── Main Page ────────────────────────────────────────────────────────────────

const ESTADO_LABEL: Record<string, string> = {
  INSCRITO: "Inscrito", CONFIRMADO: "Confirmado",
  NO_FUE: "No fue", CANCELADO: "Cancelado",
}
const ESTADO_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  INSCRITO: "secondary", CONFIRMADO: "default",
  NO_FUE: "outline", CANCELADO: "destructive",
}

function formatDateShort(iso: string) {
  return new Date(iso + "T00:00:00").toLocaleDateString("es-EC", {
    day: "numeric", month: "short", year: "numeric",
  })
}

export function PerfilPage() {
  const socioId = useAuthStore((s) => s.user?.socioId)
  const [searchParams] = useSearchParams()

  const [detailId, setDetailId] = useState<string | null>(null)
  const [editingPerfil, setEditingPerfil] = useState(false)
  const [activeTab, setActiveTab] = useState(searchParams.get("tab") ?? "perfil")

  const { data: historial, isLoading: loadingHistorial } = useHistorialSocio(socioId)

  const { data, isLoading, isError } = useQuery({
    queryKey: ["perfil"],
    queryFn: () =>
      api.get<{ data: SocioResponse }>("/v1/socios/me").then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
  })

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="h-8 w-48 animate-pulse rounded bg-muted" />
        <div className="grid gap-6 sm:grid-cols-2">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-48 animate-pulse rounded-xl bg-muted" />
          ))}
        </div>
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="rounded-xl border border-destructive/30 bg-destructive/10 p-6 text-center">
        <p className="text-sm text-destructive">Error al cargar el perfil. Intenta de nuevo.</p>
      </div>
    )
  }

  const nombreCompleto = `${data.nombre} ${data.apellido}`

  const today = new Date().toISOString().slice(0, 10)
  const proximasJefe = (historial?.historial ?? [])
    .filter((h) => h.esJefeSalida && h.estadoInscripcion !== "CANCELADO" && h.fecha >= today)
    .sort((a, b) => a.fecha.localeCompare(b.fecha))

  return (
    <div className="space-y-6 pb-10">
      {/* Hero Header: Avatar + Datos Personales */}
      <div className="relative overflow-hidden rounded-3xl border border-border/50 bg-card/60 backdrop-blur-md shadow-sm">
        <div className="absolute top-0 left-0 w-full h-32 bg-gradient-to-r from-primary/10 via-primary/5 to-transparent" />
        
        <div className="relative p-6 sm:p-8 space-y-8">
          {/* Top Info */}
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6">
            <div className="flex items-center gap-5">
              <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-primary to-primary/80 shadow-lg shadow-primary/20 text-3xl font-extrabold text-primary-foreground">
                {data.nombre[0]}{data.apellido[0]}
              </div>
              <div>
                <h1 className="text-3xl font-extrabold tracking-tight text-foreground">{nombreCompleto}</h1>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center rounded-full bg-primary/20 px-2.5 py-0.5 text-xs font-bold text-primary">
                    {data.rolSistema}
                  </span>
                  <span className="inline-flex items-center rounded-full bg-muted/80 px-2.5 py-0.5 text-xs font-bold text-muted-foreground">
                    {data.tipoSocio}
                  </span>
                  <span className="inline-flex items-center rounded-full border border-border/50 bg-background/50 px-2.5 py-0.5 text-xs font-bold text-foreground">
                    {data.estadoHabilitacion}
                  </span>
                </div>
              </div>
            </div>
            {!editingPerfil && (
              <button
                onClick={() => setEditingPerfil(true)}
                className="inline-flex h-10 items-center gap-2 rounded-xl bg-background border border-border/60 px-4 text-sm font-bold text-foreground hover:bg-accent hover:border-accent shadow-sm transition-all"
              >
                <Pencil className="h-4 w-4" />
                Editar perfil
              </button>
            )}
          </div>

          {/* Datos Grid */}
          <div className="border-t border-border/30 pt-6">
            {!editingPerfil ? (
               <div className="space-y-4">
                <dl className="grid grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-5">
                  <Field label="Cédula" value={data.cedula} />
                  <Field label="Correo" value={data.correo} />
                  <Field label="Teléfono" value={data.telefono} />
                  <Field label="F. Nacimiento" value={data.fechaNacimiento} />
                  <Field label="Edad" value={`${data.edad} años`} />
                  <Field label="F. Ingreso" value={data.fechaIngreso} />
                  <Field label="Nivel técnico" value={data.nivelTecnico} />
                  <div className="col-span-2 md:col-span-4">
                    <Field label="Dirección" value={data.direccion} />
                  </div>
                </dl>
                <p className="text-[11px] font-medium text-muted-foreground/60 uppercase tracking-widest pt-2">
                  El nombre, cédula y fechas son gestionados por la Secretaría. Contactos de emergencia e info médica en sus respectivas pestañas.
                </p>
               </div>
            ) : (
              <EditPerfilSection data={data} onDone={() => setEditingPerfil(false)} />
            )}
          </div>
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="flex-wrap h-auto gap-1">
          <TabsTrigger value="perfil">Perfil</TabsTrigger>
          <TabsTrigger value="seguridad">Seguridad</TabsTrigger>
          <TabsTrigger value="documentos">Documentos</TabsTrigger>
          <TabsTrigger value="contactos" className="gap-1.5">
            <Phone className="h-3.5 w-3.5" /> Contactos
          </TabsTrigger>
          <TabsTrigger value="salud" className="gap-1.5">
            <Heart className="h-3.5 w-3.5" /> Salud
          </TabsTrigger>
        </TabsList>

        {/* ── Pestaña Perfil ── */}
        <TabsContent value="perfil" className="mt-6">
          <div className="space-y-6">
            {proximasJefe.length > 0 && (
              <div className="rounded-2xl border border-yellow-400/50 bg-gradient-to-r from-yellow-500/10 to-amber-500/5 p-6 shadow-sm relative overflow-hidden">
                <div className="absolute top-0 left-0 w-1.5 h-full bg-gradient-to-b from-yellow-400 to-amber-500" />
                <div className="flex items-center gap-3 mb-4 pl-2">
                  <div className="p-2 bg-yellow-500/20 rounded-xl">
                    <Crown className="h-5 w-5 text-yellow-500" />
                  </div>
                  <h2 className="text-base font-bold text-foreground">
                    Eres Jefe de Salida ({proximasJefe.length})
                  </h2>
                </div>
                <ul className="space-y-3 pl-2">
                  {proximasJefe.map((h) => (
                    <li
                      key={h.salidaId}
                      onClick={() => setDetailId(h.salidaId)}
                      className="flex cursor-pointer items-center justify-between rounded-xl border border-yellow-400/30 bg-yellow-500/5 px-4 py-3 hover:bg-yellow-500/20 transition-all shadow-sm"
                    >
                      <div>
                        <p className="text-sm font-bold text-foreground">{h.salidaNombre}</p>
                        <p className="text-xs font-medium text-muted-foreground mt-0.5">{h.mountainNombre} · {formatDateShort(h.fecha)}</p>
                      </div>
                      <ArrowRight className="h-4 w-4 shrink-0 text-yellow-500/50" />
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <Card title="Mis estadísticas">
              {loadingHistorial ? (
                <div className="space-y-4">
                  <div className="grid grid-cols-3 gap-3">
                    {[...Array(3)].map((_, i) => (
                      <div key={i} className="h-20 animate-pulse rounded-xl bg-muted/60" />
                    ))}
                  </div>
                  <div className="space-y-3">
                    {[...Array(3)].map((_, i) => (
                      <div key={i} className="h-12 animate-pulse rounded-xl bg-muted/60" />
                    ))}
                  </div>
                </div>
              ) : !historial ? (
                <p className="text-sm text-muted-foreground py-4 text-center">No se pudieron cargar las estadísticas.</p>
              ) : (
                <div className="space-y-6">
                  <div className="grid grid-cols-3 gap-3">
                    {[
                      { label: "Salidas", value: historial.totalParticipaciones, color: "text-blue-500", bg: "bg-blue-500/10" },
                      { label: "Cumbres", value: historial.totalCumbresLogradas, color: "text-emerald-500", bg: "bg-emerald-500/10" },
                      { label: "Veces jefe", value: historial.vecesJefeSalida, color: "text-amber-500", bg: "bg-amber-500/10" },
                    ].map(({ label, value, color, bg }) => (
                      <div key={label} className={`rounded-2xl border border-border/50 ${bg} p-4 text-center`}>
                        <p className={`text-2xl font-extrabold ${color}`}>{value}</p>
                        <p className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground mt-1">{label}</p>
                      </div>
                    ))}
                  </div>
                  {historial.historial.length === 0 ? (
                    <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-border/50 bg-background/50 p-8 text-center">
                      <div className="rounded-full bg-muted p-3">
                        <Mountain className="h-6 w-6 text-muted-foreground/50" />
                      </div>
                      <p className="text-sm font-medium text-muted-foreground">Aún no tienes salidas registradas</p>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      <p className="text-[10px] font-extrabold uppercase tracking-widest text-muted-foreground/80">Últimas salidas</p>
                      <ul className="divide-y divide-border/50 rounded-2xl border border-border/50 bg-background/50 backdrop-blur-sm">
                        {historial.historial.slice(0, 5).map((h) => (
                          <li key={h.salidaId} className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-accent/20 transition-colors">
                            <div className="min-w-0 flex-1">
                              <div className="flex items-center gap-2">
                                {h.esJefeSalida && <Crown className="h-3.5 w-3.5 shrink-0 text-yellow-500" />}
                                <p className="truncate text-sm font-bold text-foreground">{h.salidaNombre}</p>
                              </div>
                              <p className="text-xs font-medium text-muted-foreground mt-0.5">
                                {formatDateShort(h.fecha)} · {h.mountainNombre}
                              </p>
                            </div>
                            <Badge variant={ESTADO_VARIANT[h.estadoInscripcion] ?? "outline"} className="shrink-0 text-[10px] uppercase tracking-wider font-bold">
                              {ESTADO_LABEL[h.estadoInscripcion] ?? h.estadoInscripcion}
                            </Badge>
                          </li>
                        ))}
                      </ul>
                      {historial.historial.length > 5 && (
                        <Link
                          to="/salidas"
                          className="flex items-center gap-1.5 text-xs font-bold text-primary hover:text-primary/80 transition-colors pt-2 justify-end"
                        >
                          Ver historial completo ({historial.historial.length})
                          <ArrowRight className="h-3.5 w-3.5" />
                        </Link>
                      )}
                    </div>
                  )}
                </div>
              )}
            </Card>
          </div>
        </TabsContent>

        {/* ── Pestaña Seguridad ── */}
        <TabsContent value="seguridad" className="mt-6">
          <div className="space-y-6">
            <div className="grid gap-6 lg:grid-cols-2 items-start">
              <div className="space-y-6">
                <Card title="Cambiar contraseña">
                  <ChangePasswordSection />
                </Card>
                <Card title="Autenticación de dos factores (2FA)">
                  <MfaSection />
                </Card>
              </div>
              <div>
                <Card title="Sesiones activas">
                  <SessionsSection />
                </Card>
              </div>
            </div>
            <Card title="API Keys (Asistente MCP)">
              <ApiKeysSection />
            </Card>
          </div>
        </TabsContent>

        {/* ── Pestaña Documentos ── */}
        <TabsContent value="documentos" className="mt-6">
          <Card title="Documentos legales">
            <DocumentosSection />
          </Card>
        </TabsContent>

        {/* ── Pestaña Contactos de emergencia ── */}
        <TabsContent value="contactos" className="mt-6">
          <Card title="Contactos de emergencia" action={
            <div className="flex items-center gap-1.5">
              <Phone className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="text-xs text-muted-foreground">Máx. 2 contactos</span>
            </div>
          }>
            <EmergencyContactsTab />
          </Card>
        </TabsContent>

        {/* ── Pestaña Información médica ── */}
        <TabsContent value="salud" className="mt-6">
          <Card title="Información médica" action={
            <div className="flex items-center gap-1.5">
              <Stethoscope className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="text-xs text-muted-foreground">Solo emergencias</span>
            </div>
          }>
            <MedicalInfoTab />
          </Card>
        </TabsContent>
      </Tabs>

      {detailId && (
        <SalidaDetailDialog
          open={!!detailId}
          onClose={() => setDetailId(null)}
          salidaId={detailId}
        />
      )}
    </div>
  )
}
