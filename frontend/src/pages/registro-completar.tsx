import { useState, useEffect, useCallback } from "react"
import { Link, useSearchParams, useNavigate } from "react-router"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useQuery } from "@tanstack/react-query"
import { toast } from "sonner"
import { Eye, EyeOff, Check, X as XIcon, ChevronLeft, ChevronRight } from "lucide-react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"
import { cn } from "@/lib/utils"

// ─── Shared UI helpers ────────────────────────────────────────────────────────

const inputClass =
  "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring"

function FieldError({ message }: { message?: string }) {
  if (!message) return null
  return <p className="text-xs text-destructive mt-1">{message}</p>
}

function StepIndicator({ current, total }: { current: number; total: number }) {
  return (
    <div className="flex items-center justify-center gap-2 mb-6">
      {Array.from({ length: total }, (_, i) => i + 1).map((step) => (
        <div
          key={step}
          className={cn(
            "h-2 rounded-full transition-all",
            step === current
              ? "w-6 bg-primary"
              : step < current
                ? "w-2 bg-primary/60"
                : "w-2 bg-muted",
          )}
        />
      ))}
    </div>
  )
}

function NavButtons({
  onBack,
  onNext,
  nextLabel = "Siguiente",
  isSubmitting = false,
  disableNext = false,
}: {
  onBack?: () => void
  onNext?: () => void
  nextLabel?: string
  isSubmitting?: boolean
  disableNext?: boolean
}) {
  return (
    <div className={cn("flex gap-3 pt-2", onBack ? "justify-between" : "justify-end")}>
      {onBack && (
        <button
          type="button"
          onClick={onBack}
          className="inline-flex h-10 items-center gap-1.5 rounded-md border border-border px-4 text-sm font-medium text-foreground hover:bg-muted/50 transition-colors"
        >
          <ChevronLeft className="h-4 w-4" />
          Atrás
        </button>
      )}
      <button
        type={onNext ? "button" : "submit"}
        onClick={onNext}
        disabled={isSubmitting || disableNext}
        className="inline-flex h-10 items-center gap-1.5 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50 transition-colors"
      >
        {isSubmitting ? (
          <>
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
            Procesando...
          </>
        ) : (
          <>
            {nextLabel}
            {!isSubmitting && <ChevronRight className="h-4 w-4" />}
          </>
        )}
      </button>
    </div>
  )
}

// ─── Token info ───────────────────────────────────────────────────────────────

type TokenInfo = {
  requiresPersonalData: boolean
  fromCsvImport: boolean
  prefilledNombre: string | null
  prefilledApellido: string | null
  prefilledTipoSocio: string | null
  prefilledNivelTecnico: string | null
}

type LegalDoc = {
  id: string
  code: string
  title: string
  content: string
  version: number
}

// ─── Wizard state (persisted in localStorage per token) ──────────────────────

type ContactData = {
  nombreCompleto: string
  relacion: string
  celular: string
  direccion: string
}

type MedicalData = {
  bloodType?: string
  hasRelevantAllergies: boolean
  allergiesDetail?: string
  hasRelevantMedicalCondition: boolean
  medicalConditionDetail?: string
  usesEmergencyMedication: boolean
  emergencyMedicationDetail?: string
}

type WizardData = {
  step: number
  // Step 1: initial consents
  step1DocIds: string[]
  // Step 2: personal data
  nombre?: string
  apellido?: string
  fechaNacimiento?: string
  direccion?: string
  username?: string
  // Step 3: emergency contacts
  contact1?: ContactData
  contact2?: ContactData
  // Step 4: medical info
  medical?: MedicalData
  // Step 5: final consents
  step5DocIds: string[]
}

function loadWizardData(token: string): WizardData {
  try {
    const stored = localStorage.getItem(`registro_wizard_${token}`)
    if (stored) return JSON.parse(stored)
  } catch { /* silent */ }
  return { step: 1, step1DocIds: [], step5DocIds: [] }
}

function saveWizardData(token: string, data: WizardData) {
  try {
    localStorage.setItem(`registro_wizard_${token}`, JSON.stringify(data))
  } catch { /* silent */ }
}

function clearWizardData(token: string) {
  try {
    localStorage.removeItem(`registro_wizard_${token}`)
  } catch { /* silent */ }
}

// ─── Simple message card ──────────────────────────────────────────────────────

function MessageCard({
  title,
  body,
  linkLabel = "Volver al inicio de sesión",
}: {
  title: string
  body: string
  linkLabel?: string
}) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="mx-auto w-full max-w-sm space-y-4 rounded-xl border border-border bg-card p-8 shadow-lg text-center">
        <h1 className="text-2xl font-bold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{body}</p>
        <Link
          to="/login"
          className="block text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          {linkLabel}
        </Link>
      </div>
    </div>
  )
}

// ─── Markdown document viewer ─────────────────────────────────────────────────

function DocumentViewer({ doc }: { doc: LegalDoc }) {
  return (
    <div className="rounded-lg border border-border bg-muted/30 p-4 max-h-72 overflow-y-auto text-sm">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => <h1 className="text-base font-bold mb-2 text-foreground">{children}</h1>,
          h2: ({ children }) => <h2 className="text-sm font-semibold mt-3 mb-1 text-foreground">{children}</h2>,
          p: ({ children }) => <p className="mb-2 text-muted-foreground leading-relaxed">{children}</p>,
          ul: ({ children }) => <ul className="list-disc pl-4 mb-2 text-muted-foreground">{children}</ul>,
          ol: ({ children }) => <ol className="list-decimal pl-4 mb-2 text-muted-foreground">{children}</ol>,
          li: ({ children }) => <li className="mb-0.5">{children}</li>,
          strong: ({ children }) => <strong className="font-semibold text-foreground">{children}</strong>,
        }}
      >
        {doc.content}
      </ReactMarkdown>
    </div>
  )
}

// ─── Consent checkbox ─────────────────────────────────────────────────────────

function ConsentCheckbox({
  doc,
  accepted,
  onToggle,
}: {
  doc: LegalDoc
  accepted: boolean
  onToggle: () => void
}) {
  return (
    <div className="space-y-3">
      <div className="space-y-1">
        <p className="text-sm font-medium text-foreground">{doc.title}</p>
        <p className="text-xs text-muted-foreground">Versión {doc.version}</p>
      </div>
      <DocumentViewer doc={doc} />
      <label className="flex items-start gap-3 cursor-pointer select-none group">
        <div
          onClick={onToggle}
          className={cn(
            "mt-0.5 h-5 w-5 shrink-0 rounded border-2 flex items-center justify-center transition-colors cursor-pointer",
            accepted
              ? "border-primary bg-primary"
              : "border-muted-foreground/50 group-hover:border-primary/70",
          )}
        >
          {accepted && <Check className="h-3 w-3 text-primary-foreground" />}
        </div>
        <span className="text-sm text-foreground">
          He leído y acepto el documento <span className="font-medium">{doc.title}</span>
        </span>
      </label>
    </div>
  )
}

// ─── Password rules ───────────────────────────────────────────────────────────

const PASSWORD_RULES = [
  { label: "Mínimo 12 caracteres",   test: (p: string) => p.length >= 12 },
  { label: "Una letra mayúscula",    test: (p: string) => /[A-Z]/.test(p) },
  { label: "Una letra minúscula",    test: (p: string) => /[a-z]/.test(p) },
  { label: "Un número (0–9)",        test: (p: string) => /[0-9]/.test(p) },
  { label: "Un símbolo (!@#$…)",     test: (p: string) => /[^a-zA-Z0-9\s]/.test(p) },
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
              empty   ? "text-muted-foreground"
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

// ─── Zod schemas ─────────────────────────────────────────────────────────────

const step2Schema = z
  .object({
    nombre: z.string().min(1, "El nombre es obligatorio").max(100),
    apellido: z.string().min(1, "El apellido es obligatorio").max(100),
    fechaNacimiento: z.string().min(1, "La fecha de nacimiento es obligatoria"),
    direccion: z.string().min(1, "La dirección es obligatoria").max(500),
    username: z
      .string()
      .min(4, "Mínimo 4 caracteres")
      .max(100)
      .regex(/^[a-z0-9._-]+$/, "Solo letras minúsculas, números, puntos, guiones y guiones bajos"),
    password: z
      .string()
      .min(12, "Mínimo 12 caracteres")
      .regex(/[A-Z]/, "Debe incluir al menos una mayúscula")
      .regex(/[a-z]/, "Debe incluir al menos una minúscula")
      .regex(/[0-9]/, "Debe incluir al menos un número")
      .regex(/[^a-zA-Z0-9\s]/, "Debe incluir al menos un símbolo"),
    confirmPassword: z.string().min(1, "Confirma tu contraseña"),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: "Las contraseñas no coinciden",
    path: ["confirmPassword"],
  })

const step2SchemaCsv = z
  .object({
    nombre: z.string().optional(),
    apellido: z.string().optional(),
    fechaNacimiento: z.string().min(1, "La fecha de nacimiento es obligatoria"),
    direccion: z.string().min(1, "La dirección es obligatoria").max(500),
    username: z
      .string()
      .min(4, "Mínimo 4 caracteres")
      .max(100)
      .regex(/^[a-z0-9._-]+$/, "Solo letras minúsculas, números, puntos, guiones y guiones bajos"),
    password: z
      .string()
      .min(12, "Mínimo 12 caracteres")
      .regex(/[A-Z]/, "Debe incluir al menos una mayúscula")
      .regex(/[a-z]/, "Debe incluir al menos una minúscula")
      .regex(/[0-9]/, "Debe incluir al menos un número")
      .regex(/[^a-zA-Z0-9\s]/, "Debe incluir al menos un símbolo"),
    confirmPassword: z.string().min(1, "Confirma tu contraseña"),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: "Las contraseñas no coinciden",
    path: ["confirmPassword"],
  })

const contactSchema = z.object({
  nombreCompleto: z.string().min(1, "El nombre es obligatorio").max(200),
  relacion: z.string().min(1, "La relación es obligatoria").max(100),
  celular: z.string().max(20).optional().or(z.literal("")),
  direccion: z.string().max(500).optional().or(z.literal("")),
})

type Step2Data = {
  nombre?: string
  apellido?: string
  fechaNacimiento: string
  direccion: string
  username: string
  password: string
  confirmPassword: string
}
type ContactFormData = z.infer<typeof contactSchema>

// ─── Step 1: Initial consents ─────────────────────────────────────────────────

function Step1Consents({
  token,
  wizardData,
  onNext,
}: {
  token: string
  wizardData: WizardData
  onNext: (updatedData: WizardData) => void
}) {
  const [accepted, setAccepted] = useState<Record<string, boolean>>({})

  const { data: docs, isLoading, isError } = useQuery({
    queryKey: ["legal-docs-registration"],
    queryFn: async () => {
      const [r1, r2] = await Promise.all([
        api.get<ApiResponse<LegalDoc>>("/v1/legal-documents/DATA_PROCESSING_POLICY/active"),
        api.get<ApiResponse<LegalDoc>>("/v1/legal-documents/MEDICAL_DATA_CONSENT/active"),
      ])
      return [r1.data.data, r2.data.data].filter(Boolean)
    },
    retry: 1,
  })

  useEffect(() => {
    if (docs && wizardData.step1DocIds.length > 0) {
      const initial: Record<string, boolean> = {}
      for (const doc of docs) {
        initial[doc.id] = wizardData.step1DocIds.includes(doc.id)
      }
      setAccepted(initial)
    }
  }, [docs]) // eslint-disable-line react-hooks/exhaustive-deps

  const allAccepted = docs
    ? docs.every((doc) => accepted[doc.id])
    : false

  const handleNext = () => {
    if (!allAccepted) {
      toast.error("Debes aceptar todos los documentos para continuar")
      return
    }
    const acceptedIds = docs!.map((d) => d.id)
    const updated: WizardData = { ...wizardData, step: 2, step1DocIds: acceptedIds }
    saveWizardData(token, updated)
    onNext(updated)
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    )
  }

  if (isError || !docs || docs.length === 0) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground text-center py-4">
          No se encontraron documentos de consentimiento activos. Contacta con la Secretaría.
        </p>
        <NavButtons onNext={handleNext} nextLabel="Continuar" disableNext />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-foreground">Consentimientos requeridos</h2>
        <p className="text-sm text-muted-foreground">
          Lee cada documento y marca la casilla de aceptación para continuar.
        </p>
      </div>

      <div className="space-y-6">
        {docs.map((doc) => (
          <ConsentCheckbox
            key={doc.id}
            doc={doc}
            accepted={!!accepted[doc.id]}
            onToggle={() => setAccepted((prev) => ({ ...prev, [doc.id]: !prev[doc.id] }))}
          />
        ))}
      </div>

      <NavButtons
        onNext={handleNext}
        nextLabel="Continuar"
        disableNext={!allAccepted}
      />
    </div>
  )
}

// ─── Step 2: Personal data + credentials ─────────────────────────────────────

function Step2PersonalData({
  token,
  wizardData,
  tokenInfo,
  onBack,
  onNext,
}: {
  token: string
  wizardData: WizardData
  tokenInfo: TokenInfo
  onBack: () => void
  onNext: (updatedData: WizardData) => void
}) {
  const isCsv = tokenInfo.fromCsvImport
  const schema = isCsv ? step2SchemaCsv : step2Schema

  const [showPwd, setShowPwd] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } = useForm<Step2Data>({
    resolver: zodResolver(schema) as any,
    defaultValues: {
      nombre: wizardData.nombre ?? tokenInfo.prefilledNombre ?? "",
      apellido: wizardData.apellido ?? tokenInfo.prefilledApellido ?? "",
      fechaNacimiento: wizardData.fechaNacimiento ?? "",
      direccion: wizardData.direccion ?? "",
      username: wizardData.username ?? "",
      password: "",
      confirmPassword: "",
    },
  })

  const passwordValue = watch("password") ?? ""

  const onSubmit = (data: Step2Data & { _password?: string }) => {
    const updated: WizardData = {
      ...wizardData,
      step: 3,
      nombre: data.nombre,
      apellido: data.apellido,
      fechaNacimiento: data.fechaNacimiento,
      direccion: data.direccion,
      username: data.username,
      // password is NOT stored in localStorage for security
    }
    saveWizardData(token, updated)
    // Pass password via callback (stays in memory only)
    onNext({ ...updated, _password: data.password } as WizardData & { _password: string })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-foreground">Datos personales y credenciales</h2>
        <p className="text-sm text-muted-foreground">
          Completa tus datos y elige las credenciales de acceso al sistema.
        </p>
      </div>

      <section className="space-y-4">
        {isCsv ? (
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">Nombre</label>
              <p className="text-sm font-medium px-3 py-2 rounded-md border border-input bg-muted/40">
                {tokenInfo.prefilledNombre}
              </p>
            </div>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">Apellido</label>
              <p className="text-sm font-medium px-3 py-2 rounded-md border border-input bg-muted/40">
                {tokenInfo.prefilledApellido}
              </p>
            </div>
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1">
              <label className="text-sm font-medium text-foreground">Nombre *</label>
              <input {...register("nombre")} className={inputClass} placeholder="Tu nombre" />
              <FieldError message={errors.nombre?.message} />
            </div>
            <div className="space-y-1">
              <label className="text-sm font-medium text-foreground">Apellido *</label>
              <input {...register("apellido")} className={inputClass} placeholder="Tu apellido" />
              <FieldError message={errors.apellido?.message} />
            </div>
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-sm font-medium text-foreground">Fecha de nacimiento *</label>
            <input {...register("fechaNacimiento")} type="date" className={inputClass} />
            <FieldError message={errors.fechaNacimiento?.message} />
          </div>
          <div className="space-y-1">
            <label className="text-sm font-medium text-foreground">Dirección *</label>
            <input {...register("direccion")} className={inputClass} placeholder="Dirección de domicilio" />
            <FieldError message={errors.direccion?.message} />
          </div>
        </div>
      </section>

      <section className="space-y-4">
        <h3 className="text-sm font-semibold text-foreground border-b border-border pb-2">
          Credenciales de acceso
        </h3>
        <div className="space-y-1">
          <label className="text-sm font-medium text-foreground">Nombre de usuario *</label>
          <input
            {...register("username")}
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            autoCorrect="off"
            placeholder="ej. juan.perez"
            className={inputClass}
          />
          <FieldError message={errors.username?.message} />
        </div>
        <div className="space-y-1">
          <label className="text-sm font-medium text-foreground">Contraseña *</label>
          <div className="relative">
            <input
              {...register("password")}
              type={showPwd ? "text" : "password"}
              autoComplete="new-password"
              placeholder="Mín. 12 caracteres"
              className={cn(inputClass, "pr-10")}
            />
            <button
              type="button"
              onClick={() => setShowPwd((v) => !v)}
              className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
              tabIndex={-1}
            >
              {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          <PasswordChecklist password={passwordValue} />
          <FieldError message={errors.password?.message} />
        </div>
        <div className="space-y-1">
          <label className="text-sm font-medium text-foreground">Confirmar contraseña *</label>
          <div className="relative">
            <input
              {...register("confirmPassword")}
              type={showConfirm ? "text" : "password"}
              autoComplete="new-password"
              placeholder="Repite la contraseña"
              className={cn(inputClass, "pr-10")}
            />
            <button
              type="button"
              onClick={() => setShowConfirm((v) => !v)}
              className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
              tabIndex={-1}
            >
              {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          <FieldError message={errors.confirmPassword?.message} />
        </div>
      </section>

      <NavButtons onBack={onBack} nextLabel="Continuar" />
    </form>
  )
}

// ─── Step 3: Emergency contacts ───────────────────────────────────────────────

function ContactForm({
  title,
  defaultValues,
  onSave,
}: {
  title: string
  defaultValues?: ContactData
  onSave: (data: ContactData) => void
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ContactFormData>({
    resolver: zodResolver(contactSchema),
    defaultValues: defaultValues ?? { nombreCompleto: "", relacion: "", celular: "", direccion: "" },
  })

  return (
    <div className="space-y-3 rounded-lg border border-border p-4">
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1">
          <label className="text-sm font-medium text-foreground">Nombre completo *</label>
          <input {...register("nombreCompleto")} className={inputClass} placeholder="Nombre del contacto" />
          <FieldError message={errors.nombreCompleto?.message} />
        </div>
        <div className="space-y-1">
          <label className="text-sm font-medium text-foreground">Relación *</label>
          <input {...register("relacion")} className={inputClass} placeholder="Ej: Padre, Hermano, Amigo" />
          <FieldError message={errors.relacion?.message} />
        </div>
        <div className="space-y-1">
          <label className="text-sm font-medium text-foreground">Celular</label>
          <input {...register("celular")} className={inputClass} placeholder="Número de teléfono" />
          <FieldError message={errors.celular?.message} />
        </div>
        <div className="space-y-1">
          <label className="text-sm font-medium text-foreground">Dirección</label>
          <input {...register("direccion")} className={inputClass} placeholder="Dirección" />
          <FieldError message={errors.direccion?.message} />
        </div>
      </div>
      <div className="flex justify-end">
        <button
          type="button"
          onClick={handleSubmit((d) => onSave(d as ContactData))}
          className="inline-flex h-8 items-center gap-1 rounded-md bg-muted px-3 text-xs font-medium text-foreground hover:bg-muted/80 transition-colors"
        >
          <Check className="h-3.5 w-3.5" />
          Guardar contacto
        </button>
      </div>
    </div>
  )
}

function Step3EmergencyContacts({
  token,
  wizardData,
  onBack,
  onNext,
}: {
  token: string
  wizardData: WizardData
  onBack: () => void
  onNext: (updatedData: WizardData) => void
}) {
  const [contact1, setContact1] = useState<ContactData | undefined>(wizardData.contact1)
  const [contact2, setContact2] = useState<ContactData | undefined>(wizardData.contact2)

  const handleNext = () => {
    if (!contact1) {
      toast.error("Debes registrar al menos el primer contacto de emergencia")
      return
    }
    if (!contact2) {
      toast.error("Debes registrar el segundo contacto de emergencia")
      return
    }
    const updated: WizardData = { ...wizardData, step: 4, contact1, contact2 }
    saveWizardData(token, updated)
    onNext(updated)
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-foreground">Contactos de emergencia</h2>
        <p className="text-sm text-muted-foreground">
          Registra dos personas de confianza a quienes contactar en caso de emergencia.
        </p>
      </div>

      <div className="space-y-4">
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-foreground">Contacto 1</span>
            {contact1 && <Check className="h-4 w-4 text-green-500" />}
          </div>
          <ContactForm
            title=""
            defaultValues={contact1}
            onSave={(d) => {
              setContact1(d)
              toast.success("Contacto 1 guardado")
            }}
          />
        </div>

        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-foreground">Contacto 2</span>
            {contact2 && <Check className="h-4 w-4 text-green-500" />}
          </div>
          <ContactForm
            title=""
            defaultValues={contact2}
            onSave={(d) => {
              setContact2(d)
              toast.success("Contacto 2 guardado")
            }}
          />
        </div>
      </div>

      <NavButtons
        onBack={onBack}
        onNext={handleNext}
        nextLabel="Continuar"
        disableNext={!contact1 || !contact2}
      />
    </div>
  )
}

// ─── Step 4: Medical info ─────────────────────────────────────────────────────

function Step4MedicalInfo({
  token,
  wizardData,
  onBack,
  onNext,
}: {
  token: string
  wizardData: WizardData
  onBack: () => void
  onNext: (updatedData: WizardData) => void
}) {
  const [med, setMed] = useState<MedicalData>(
    wizardData.medical ?? {
      bloodType: "",
      hasRelevantAllergies: false,
      allergiesDetail: "",
      hasRelevantMedicalCondition: false,
      medicalConditionDetail: "",
      usesEmergencyMedication: false,
      emergencyMedicationDetail: "",
    },
  )

  const handleNext = () => {
    const updated: WizardData = { ...wizardData, step: 5, medical: med }
    saveWizardData(token, updated)
    onNext(updated)
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-foreground">Información médica</h2>
        <p className="text-sm text-muted-foreground">
          Esta información es confidencial y se usa únicamente en caso de emergencia durante actividades del club.
        </p>
      </div>

      <div className="space-y-4">
        <div className="space-y-1">
          <label className="text-sm font-medium text-foreground">Tipo de sangre</label>
          <select
            value={med.bloodType ?? ""}
            onChange={(e) => setMed((m) => ({ ...m, bloodType: e.target.value }))}
            className={selectClass}
          >
            <option value="">— Sin especificar —</option>
            {["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"].map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>

        <MedicalToggle
          question="¿Tiene alergias relevantes?"
          value={med.hasRelevantAllergies}
          detailLabel="¿Cuáles?"
          detail={med.allergiesDetail ?? ""}
          onToggle={(v) => setMed((m) => ({ ...m, hasRelevantAllergies: v, allergiesDetail: v ? m.allergiesDetail : "" }))}
          onDetail={(d) => setMed((m) => ({ ...m, allergiesDetail: d }))}
        />

        <MedicalToggle
          question="¿Tiene alguna condición médica relevante?"
          value={med.hasRelevantMedicalCondition}
          detailLabel="¿Cuál?"
          detail={med.medicalConditionDetail ?? ""}
          onToggle={(v) => setMed((m) => ({ ...m, hasRelevantMedicalCondition: v, medicalConditionDetail: v ? m.medicalConditionDetail : "" }))}
          onDetail={(d) => setMed((m) => ({ ...m, medicalConditionDetail: d }))}
        />

        <MedicalToggle
          question="¿Usa medicación de emergencia?"
          value={med.usesEmergencyMedication}
          detailLabel="¿Cuál?"
          detail={med.emergencyMedicationDetail ?? ""}
          onToggle={(v) => setMed((m) => ({ ...m, usesEmergencyMedication: v, emergencyMedicationDetail: v ? m.emergencyMedicationDetail : "" }))}
          onDetail={(d) => setMed((m) => ({ ...m, emergencyMedicationDetail: d }))}
        />
      </div>

      <NavButtons onBack={onBack} onNext={handleNext} nextLabel="Continuar" />
    </div>
  )
}

function MedicalToggle({
  question,
  value,
  detailLabel,
  detail,
  onToggle,
  onDetail,
}: {
  question: string
  value: boolean
  detailLabel: string
  detail: string
  onToggle: (v: boolean) => void
  onDetail: (d: string) => void
}) {
  return (
    <div className="space-y-2 rounded-lg border border-border p-4">
      <p className="text-sm font-medium text-foreground">{question}</p>
      <div className="flex gap-3">
        {[
          { label: "Sí", val: true },
          { label: "No", val: false },
        ].map(({ label, val }) => (
          <button
            key={label}
            type="button"
            onClick={() => onToggle(val)}
            className={cn(
              "flex-1 h-9 rounded-md border text-sm font-medium transition-colors",
              value === val
                ? "border-primary bg-primary/10 text-primary"
                : "border-border text-muted-foreground hover:border-primary/50",
            )}
          >
            {label}
          </button>
        ))}
      </div>
      {value && (
        <input
          type="text"
          value={detail}
          onChange={(e) => onDetail(e.target.value)}
          placeholder={detailLabel}
          className={cn(inputClass, "mt-2")}
        />
      )}
    </div>
  )
}

// ─── Step 5: Final consents ───────────────────────────────────────────────────

function Step5FinalConsents({
  token,
  wizardData,
  onBack,
  onNext,
}: {
  token: string
  wizardData: WizardData
  onBack: () => void
  onNext: (updatedData: WizardData) => void
}) {
  const [accepted, setAccepted] = useState<Record<string, boolean>>({})

  const { data: docs, isLoading, isError } = useQuery({
    queryKey: ["legal-docs-final"],
    queryFn: async () => {
      const results = await Promise.allSettled([
        api.get<ApiResponse<LegalDoc>>("/v1/legal-documents/DATA_RETENTION_POLICY/active"),
        api.get<ApiResponse<LegalDoc>>("/v1/legal-documents/LIABILITY_WAIVER/active"),
      ])
      return results
        .filter((r) => r.status === "fulfilled")
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .map((r) => (r as PromiseFulfilledResult<any>).value.data.data as LegalDoc)
        .filter(Boolean)
    },
    retry: 1,
  })

  useEffect(() => {
    if (docs && wizardData.step5DocIds.length > 0) {
      const initial: Record<string, boolean> = {}
      for (const doc of docs) {
        initial[doc.id] = wizardData.step5DocIds.includes(doc.id)
      }
      setAccepted(initial)
    }
  }, [docs]) // eslint-disable-line react-hooks/exhaustive-deps

  const allAccepted = docs && docs.length > 0
    ? docs.every((doc) => accepted[doc.id])
    : true // if no docs, can proceed

  const handleNext = () => {
    if (!allAccepted) {
      toast.error("Debes aceptar todos los documentos para continuar")
      return
    }
    const acceptedIds = (docs ?? []).map((d) => d.id)
    const updated: WizardData = { ...wizardData, step: 6, step5DocIds: acceptedIds }
    saveWizardData(token, updated)
    onNext(updated)
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground text-center py-4">
          No se encontraron documentos adicionales. Puedes continuar.
        </p>
        <NavButtons onBack={onBack} onNext={handleNext} nextLabel="Continuar" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-foreground">Aceptaciones finales</h2>
        <p className="text-sm text-muted-foreground">
          Lee y acepta los últimos documentos para completar tu registro.
        </p>
      </div>

      {docs && docs.length > 0 ? (
        <div className="space-y-6">
          {docs.map((doc) => (
            <ConsentCheckbox
              key={doc.id}
              doc={doc}
              accepted={!!accepted[doc.id]}
              onToggle={() => setAccepted((prev) => ({ ...prev, [doc.id]: !prev[doc.id] }))}
            />
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground py-4 text-center">
          No hay documentos adicionales por aceptar.
        </p>
      )}

      <NavButtons
        onBack={onBack}
        onNext={handleNext}
        nextLabel="Revisar y finalizar"
        disableNext={!allAccepted}
      />
    </div>
  )
}

// ─── Step 6: Confirm & submit ─────────────────────────────────────────────────

function Step6Confirm({
  token,
  wizardData,
  password,
  tokenInfo,
  onBack,
}: {
  token: string
  wizardData: WizardData
  password: string
  tokenInfo: TokenInfo
  onBack: () => void
}) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const navigate = useNavigate()

  const handleSubmit = async () => {
    setIsSubmitting(true)
    try {
      const allDocIds = [...wizardData.step1DocIds, ...wizardData.step5DocIds]
      const contacts = [wizardData.contact1, wizardData.contact2].filter(Boolean)

      await api.post("/v1/registro/complete", {
        token,
        nombre: wizardData.nombre ?? tokenInfo.prefilledNombre,
        apellido: wizardData.apellido ?? tokenInfo.prefilledApellido,
        fechaNacimiento: wizardData.fechaNacimiento,
        direccion: wizardData.direccion,
        username: wizardData.username,
        password,
        confirmPassword: password,
        documentIdsToAccept: allDocIds.length > 0 ? allDocIds : undefined,
        contactosEmergencia: contacts.length > 0
          ? contacts.map((c) => ({
              nombreCompleto: c!.nombreCompleto,
              relacion: c!.relacion,
              celular: c!.celular || undefined,
              direccion: c!.direccion || undefined,
            }))
          : undefined,
        informacionMedica: wizardData.medical
          ? {
              bloodType: wizardData.medical.bloodType || undefined,
              hasRelevantAllergies: wizardData.medical.hasRelevantAllergies,
              allergiesDetail: wizardData.medical.allergiesDetail || undefined,
              hasRelevantMedicalCondition: wizardData.medical.hasRelevantMedicalCondition,
              medicalConditionDetail: wizardData.medical.medicalConditionDetail || undefined,
              usesEmergencyMedication: wizardData.medical.usesEmergencyMedication,
              emergencyMedicationDetail: wizardData.medical.emergencyMedicationDetail || undefined,
            }
          : undefined,
      })

      clearWizardData(token)
      setDone(true)
      toast.success("¡Registro completado correctamente!")
      setTimeout(() => navigate("/login", { replace: true }), 3000)
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { message?: string } } }
      toast.error(axiosError.response?.data?.message || "Error al completar el registro. Intenta de nuevo.")
    } finally {
      setIsSubmitting(false)
    }
  }

  if (done) {
    return (
      <MessageCard
        title="¡Registro completado!"
        body="Tu cuenta ha sido activada. Serás redirigido al inicio de sesión en unos segundos."
        linkLabel="Ir al inicio de sesión"
      />
    )
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-foreground">Confirmar registro</h2>
        <p className="text-sm text-muted-foreground">
          Revisa el resumen de tu registro antes de finalizar.
        </p>
      </div>

      <div className="space-y-3">
        <SummaryRow label="Nombre" value={`${wizardData.nombre ?? tokenInfo.prefilledNombre ?? ""} ${wizardData.apellido ?? tokenInfo.prefilledApellido ?? ""}`} />
        <SummaryRow label="Fecha de nacimiento" value={wizardData.fechaNacimiento ?? "—"} />
        <SummaryRow label="Dirección" value={wizardData.direccion ?? "—"} />
        <SummaryRow label="Usuario" value={wizardData.username ?? "—"} />
        <SummaryRow
          label="Contactos de emergencia"
          value={
            [wizardData.contact1?.nombreCompleto, wizardData.contact2?.nombreCompleto]
              .filter(Boolean)
              .join(", ") || "—"
          }
        />
        <SummaryRow
          label="Documentos aceptados"
          value={`${wizardData.step1DocIds.length + wizardData.step5DocIds.length} documento(s)`}
        />
      </div>

      <NavButtons
        onBack={onBack}
        onNext={handleSubmit}
        nextLabel="Finalizar registro"
        isSubmitting={isSubmitting}
      />
    </div>
  )
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between items-start gap-4 py-2 border-b border-border/50 last:border-0">
      <span className="text-sm text-muted-foreground shrink-0">{label}</span>
      <span className="text-sm text-foreground text-right">{value}</span>
    </div>
  )
}

// ─── Main wizard ──────────────────────────────────────────────────────────────

function RegistroWizard({
  token,
  tokenInfo,
}: {
  token: string
  tokenInfo: TokenInfo
}) {
  const [wizardData, setWizardData] = useState<WizardData>(() => loadWizardData(token))
  const [password, setPassword] = useState("")

  const currentStep = wizardData.step

  const handleStepNext = useCallback(
    (updatedData: WizardData & { _password?: string }) => {
      if (updatedData._password) {
        setPassword(updatedData._password)
        const { _password, ...clean } = updatedData
        setWizardData(clean)
      } else {
        setWizardData(updatedData)
      }
    },
    [],
  )

  const goToStep = (step: number) => {
    const updated = { ...wizardData, step }
    saveWizardData(token, updated)
    setWizardData(updated)
  }

  const stepTitles = [
    "Consentimientos iniciales",
    "Datos personales",
    "Contactos de emergencia",
    "Información médica",
    "Aceptaciones finales",
    "Confirmar registro",
  ]

  return (
    <div className="flex min-h-screen items-start justify-center bg-background py-8">
      <div className="mx-auto w-full max-w-xl space-y-6 rounded-xl border border-border bg-card p-8 shadow-lg">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Activar cuenta</h1>
          <p className="text-sm text-muted-foreground">
            Paso {currentStep} de 6 — {stepTitles[currentStep - 1]}
          </p>
        </div>

        <StepIndicator current={currentStep} total={6} />

        {currentStep === 1 && (
          <Step1Consents token={token} wizardData={wizardData} onNext={handleStepNext} />
        )}
        {currentStep === 2 && (
          <Step2PersonalData
            token={token}
            wizardData={wizardData}
            tokenInfo={tokenInfo}
            onBack={() => goToStep(1)}
            onNext={handleStepNext}
          />
        )}
        {currentStep === 3 && (
          <Step3EmergencyContacts
            token={token}
            wizardData={wizardData}
            onBack={() => goToStep(2)}
            onNext={handleStepNext}
          />
        )}
        {currentStep === 4 && (
          <Step4MedicalInfo
            token={token}
            wizardData={wizardData}
            onBack={() => goToStep(3)}
            onNext={handleStepNext}
          />
        )}
        {currentStep === 5 && (
          <Step5FinalConsents
            token={token}
            wizardData={wizardData}
            onBack={() => goToStep(4)}
            onNext={handleStepNext}
          />
        )}
        {currentStep === 6 && (
          <Step6Confirm
            token={token}
            wizardData={wizardData}
            password={password}
            tokenInfo={tokenInfo}
            onBack={() => goToStep(5)}
          />
        )}
      </div>
    </div>
  )
}

// ─── Legacy simple form (for existing socios getting credentials only) ─────────

function LegacyRegistroForm({
  token,
}: {
  token: string
}) {
  const navigate = useNavigate()
  const [done, setDone] = useState(false)
  const [showPwd, setShowPwd] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const schema = z
    .object({
      username: z
        .string()
        .min(4, "Mínimo 4 caracteres")
        .max(100)
        .regex(/^[a-z0-9._-]+$/, "Solo letras minúsculas, números, puntos, guiones y guiones bajos"),
      password: z
        .string()
        .min(12, "Mínimo 12 caracteres")
        .regex(/[A-Z]/, "Debe incluir al menos una mayúscula")
        .regex(/[a-z]/, "Debe incluir al menos una minúscula")
        .regex(/[0-9]/, "Debe incluir al menos un número")
        .regex(/[^a-zA-Z0-9\s]/, "Debe incluir al menos un símbolo"),
      confirmPassword: z.string().min(1, "Confirma tu contraseña"),
    })
    .refine((d) => d.password === d.confirmPassword, {
      message: "Las contraseñas no coinciden",
      path: ["confirmPassword"],
    })

  type FormData = z.infer<typeof schema>

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const passwordValue = watch("password") ?? ""

  const onSubmit = async (data: FormData) => {
    try {
      await api.post("/v1/registro/complete", {
        token,
        username: data.username,
        password: data.password,
        confirmPassword: data.confirmPassword,
      })
      setDone(true)
      toast.success("¡Cuenta activada correctamente!")
      setTimeout(() => navigate("/login", { replace: true }), 3000)
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { message?: string } } }
      toast.error(axiosError.response?.data?.message || "El enlace es inválido o ya expiró")
    }
  }

  if (done) {
    return (
      <MessageCard
        title="¡Cuenta activada!"
        body="Tu cuenta ha sido activada correctamente. Serás redirigido al inicio de sesión en unos segundos."
        linkLabel="Ir al inicio de sesión"
      />
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background py-8">
      <div className="mx-auto w-full max-w-sm space-y-6 rounded-xl border border-border bg-card p-8 shadow-lg">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Activar cuenta</h1>
          <p className="text-sm text-muted-foreground">
            Elige tu nombre de usuario y una contraseña segura para acceder al sistema.
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1">
            <label className="text-sm font-medium text-foreground">Nombre de usuario *</label>
            <input
              {...register("username")}
              type="text"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              placeholder="ej. juan.perez"
              className={inputClass}
            />
            <FieldError message={errors.username?.message} />
          </div>
          <div className="space-y-1">
            <label className="text-sm font-medium text-foreground">Contraseña *</label>
            <div className="relative">
              <input
                {...register("password")}
                type={showPwd ? "text" : "password"}
                autoComplete="new-password"
                placeholder="Mín. 12 caracteres"
                className={cn(inputClass, "pr-10")}
              />
              <button
                type="button"
                onClick={() => setShowPwd((v) => !v)}
                className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
                tabIndex={-1}
              >
                {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            <PasswordChecklist password={passwordValue} />
          </div>
          <div className="space-y-1">
            <label className="text-sm font-medium text-foreground">Confirmar contraseña *</label>
            <div className="relative">
              <input
                {...register("confirmPassword")}
                type={showConfirm ? "text" : "password"}
                autoComplete="new-password"
                placeholder="Repite la contraseña"
                className={cn(inputClass, "pr-10")}
              />
              <button
                type="button"
                onClick={() => setShowConfirm((v) => !v)}
                className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
                tabIndex={-1}
              >
                {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            <FieldError message={errors.confirmPassword?.message} />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50 transition-colors"
          >
            {isSubmitting ? (
              <span className="flex items-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
                Activando...
              </span>
            ) : (
              "Activar cuenta"
            )}
          </button>
        </form>
      </div>
    </div>
  )
}

// ─── Page entry point ─────────────────────────────────────────────────────────

export function RegistroCompletarPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")

  const {
    data: tokenInfo,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["registro-token-info", token],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<TokenInfo>>(
        "/v1/registro/token-info",
        { params: { token } },
      )
      return data.data
    },
    enabled: !!token,
    retry: false,
  })

  if (!token) {
    return (
      <MessageCard
        title="Enlace inválido"
        body="El enlace de activación no es válido. Contacta con la Secretaría para recibir un nuevo enlace."
      />
    )
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="mx-auto w-full max-w-sm space-y-4 rounded-xl border border-border bg-card p-8 shadow-lg text-center">
          <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground">Verificando enlace...</p>
        </div>
      </div>
    )
  }

  if (isError || !tokenInfo) {
    return (
      <MessageCard
        title="Enlace inválido o expirado"
        body="Este enlace de activación no es válido, ya fue usado, o ha expirado. Contacta con la Secretaría para recibir un nuevo enlace."
      />
    )
  }

  // New registration flow: show wizard
  if (tokenInfo.requiresPersonalData || tokenInfo.fromCsvImport) {
    return <RegistroWizard token={token} tokenInfo={tokenInfo} />
  }

  // Legacy flow: simple credentials form
  return <LegacyRegistroForm token={token} />
}
