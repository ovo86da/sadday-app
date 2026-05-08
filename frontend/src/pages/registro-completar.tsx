import { useMemo, useState } from "react"
import { Link, useSearchParams, useNavigate } from "react-router"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useQuery } from "@tanstack/react-query"
import { toast } from "sonner"
import { Eye, EyeOff, Check, X as XIcon } from "lucide-react"
import api from "@/lib/api"
import type { ApiResponse } from "@/types/socios"
import { cn } from "@/lib/utils"

// ─── Helpers ─────────────────────────────────────────────────────────────────

const inputClass =
  "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"

const selectClass =
  "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring"

function FieldError({ message }: { message?: string }) {
  if (!message) return null
  return <p className="text-xs text-destructive">{message}</p>
}

// ─── Zod schemas ─────────────────────────────────────────────────────────────

const credentialsFields = {
  username: z
    .string()
    .min(4, "El nombre de usuario debe tener al menos 4 caracteres")
    .max(100, "No puede superar los 100 caracteres")
    .regex(/^[a-z0-9._-]+$/, "Solo letras minúsculas, números, puntos, guiones y guiones bajos"),
  password: z
    .string()
    .min(12, "Mínimo 12 caracteres")
    .regex(/[A-Z]/, "Debe incluir al menos una mayúscula")
    .regex(/[a-z]/, "Debe incluir al menos una minúscula")
    .regex(/[0-9]/, "Debe incluir al menos un número")
    .regex(/[^a-zA-Z0-9\s]/, "Debe incluir al menos un símbolo"),
  confirmPassword: z.string().min(1, "Confirma tu contraseña"),
}

// ─── Checklist de requisitos de contraseña ────────────────────────────────────

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

const personalFields = {
  nombre: z.string().min(1, "El nombre es obligatorio").max(100),
  apellido: z.string().min(1, "El apellido es obligatorio").max(100),
  fechaNacimiento: z.string().min(1, "La fecha de nacimiento es obligatoria"),
  tipoSangre: z.string().optional(),
  direccion: z.string().max(500).optional(),
  emergencyContactName: z.string().max(200).optional(),
  emergencyContactPhone: z.string().max(20).optional(),
  emergencyContactDireccion: z.string().max(500).optional(),
  emergencyContactName2: z.string().max(200).optional(),
  emergencyContactPhone2: z.string().max(20).optional(),
  emergencyContactDireccion2: z.string().max(500).optional(),
}

const personalFieldsOptional = {
  nombre: z.string().optional(),
  apellido: z.string().optional(),
  fechaNacimiento: z.string().optional(),
  tipoSangre: z.string().optional(),
  direccion: z.string().optional(),
  emergencyContactName: z.string().optional(),
  emergencyContactPhone: z.string().optional(),
  emergencyContactDireccion: z.string().optional(),
  emergencyContactName2: z.string().optional(),
  emergencyContactPhone2: z.string().optional(),
  emergencyContactDireccion2: z.string().optional(),
}

const csvRequiredFields = {
  ...personalFieldsOptional,
  fechaNacimiento: z.string().min(1, "La fecha de nacimiento es obligatoria"),
  direccion: z.string().min(1, "La dirección es obligatoria").max(500),
  emergencyContactName: z.string().min(1, "El nombre del contacto de emergencia es obligatorio").max(200),
  emergencyContactPhone: z.string().min(1, "El teléfono del contacto de emergencia es obligatorio").max(20),
}

function buildSchema(requiresPersonalData: boolean, fromCsvImport: boolean) {
  const fields = fromCsvImport
    ? csvRequiredFields
    : requiresPersonalData
      ? personalFields
      : personalFieldsOptional

  return z
    .object({ ...fields, ...credentialsFields })
    .refine((d) => d.password === d.confirmPassword, {
      message: "Las contraseñas no coinciden",
      path: ["confirmPassword"],
    })
}

// ─── Page component ───────────────────────────────────────────────────────────

export function RegistroCompletarPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")

  // Check token type (determines which fields to show)
  const {
    data: tokenInfo,
    isLoading: isCheckingToken,
    isError: isTokenError,
  } = useQuery({
    queryKey: ["registro-token-info", token],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<{
        requiresPersonalData: boolean
        fromCsvImport: boolean
        prefilledNombre: string | null
        prefilledApellido: string | null
        prefilledTipoSocio: string | null
        prefilledNivelTecnico: string | null
      }>>(
        "/v1/registro/token-info",
        { params: { token } },
      )
      return data.data
    },
    enabled: !!token,
    retry: false,
  })

  if (!token) {
    return <MessageCard title="Enlace inválido" body="El enlace de activación no es válido o no se encontró el token. Contacta con la Secretaría para recibir un nuevo enlace." />
  }

  if (isCheckingToken) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="mx-auto w-full max-w-sm space-y-4 rounded-xl border border-border bg-card p-8 shadow-lg text-center">
          <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground">Verificando enlace...</p>
        </div>
      </div>
    )
  }

  if (isTokenError || !tokenInfo) {
    return (
      <MessageCard
        title="Enlace inválido o expirado"
        body="Este enlace de activación no es válido, ya fue usado, o ha expirado. Contacta con la Secretaría para recibir un nuevo enlace."
      />
    )
  }

  return (
    <RegistroForm
      token={token}
      requiresPersonalData={tokenInfo.requiresPersonalData}
      fromCsvImport={tokenInfo.fromCsvImport}
      prefilledNombre={tokenInfo.prefilledNombre}
      prefilledApellido={tokenInfo.prefilledApellido}
      prefilledTipoSocio={tokenInfo.prefilledTipoSocio}
      prefilledNivelTecnico={tokenInfo.prefilledNivelTecnico}
    />
  )
}

// ─── Form component (rendered once token type is known) ───────────────────────

type FormData = {
  nombre?: string
  apellido?: string
  fechaNacimiento?: string
  tipoSangre?: string
  direccion?: string
  emergencyContactName?: string
  emergencyContactPhone?: string
  emergencyContactDireccion?: string
  emergencyContactName2?: string
  emergencyContactPhone2?: string
  emergencyContactDireccion2?: string
  username: string
  password: string
  confirmPassword: string
}

function RegistroForm({
  token,
  requiresPersonalData,
  fromCsvImport = false,
  prefilledNombre,
  prefilledApellido,
  prefilledTipoSocio,
  prefilledNivelTecnico,
}: {
  token: string
  requiresPersonalData: boolean
  fromCsvImport?: boolean
  prefilledNombre?: string | null
  prefilledApellido?: string | null
  prefilledTipoSocio?: string | null
  prefilledNivelTecnico?: string | null
}) {
  const navigate = useNavigate()
  const [done, setDone] = useState(false)

  const schema = useMemo(() => buildSchema(requiresPersonalData, fromCsvImport), [requiresPersonalData, fromCsvImport])

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const passwordValue = watch("password") ?? ""
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const onSubmit = async (data: FormData) => {
    try {
      await api.post("/v1/registro/complete", {
        token,
        ...data,
        // Send empty strings as undefined so backend ignores them in legacy flow
        nombre: data.nombre || undefined,
        apellido: data.apellido || undefined,
        fechaNacimiento: data.fechaNacimiento || undefined,
        tipoSangre: data.tipoSangre || undefined,
        direccion: data.direccion || undefined,
        emergencyContactName: data.emergencyContactName || undefined,
        emergencyContactPhone: data.emergencyContactPhone || undefined,
        emergencyContactDireccion: data.emergencyContactDireccion || undefined,
        emergencyContactName2: data.emergencyContactName2 || undefined,
        emergencyContactPhone2: data.emergencyContactPhone2 || undefined,
        emergencyContactDireccion2: data.emergencyContactDireccion2 || undefined,
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
      <div className="mx-auto w-full max-w-xl space-y-6 rounded-xl border border-border bg-card p-8 shadow-lg">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Activar cuenta</h1>
          <p className="text-sm text-muted-foreground">
            {fromCsvImport
              ? "La secretaría ya cargó tus datos principales. Completa la información faltante y elige tus credenciales."
              : requiresPersonalData
                ? "Completa tus datos personales y elige tus credenciales de acceso."
                : "Elige tu nombre de usuario y una contraseña segura para completar tu registro en el club."}
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">

          {/* Personal data section — pre-registro and CSV import */}
          {(requiresPersonalData || fromCsvImport) && (
            <>
              <section className="space-y-4">
                <h2 className="text-sm font-semibold text-foreground border-b border-border pb-2">
                  Datos personales
                </h2>

                {/* Nombre y apellido: read-only si vienen del CSV */}
                {fromCsvImport ? (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="space-y-1">
                      <label className="text-xs text-muted-foreground">Nombre</label>
                      <p className="text-sm font-medium text-foreground px-3 py-2 rounded-md border border-input bg-muted/40">{prefilledNombre}</p>
                    </div>
                    <div className="space-y-1">
                      <label className="text-xs text-muted-foreground">Apellido</label>
                      <p className="text-sm font-medium text-foreground px-3 py-2 rounded-md border border-input bg-muted/40">{prefilledApellido}</p>
                    </div>
                    {(prefilledTipoSocio || prefilledNivelTecnico) && (
                      <div className="space-y-1 sm:col-span-2 flex gap-4">
                        {prefilledTipoSocio && (
                          <div>
                            <label className="text-xs text-muted-foreground">Tipo de socio</label>
                            <p className="text-sm text-foreground">{prefilledTipoSocio}</p>
                          </div>
                        )}
                        {prefilledNivelTecnico && (
                          <div>
                            <label className="text-xs text-muted-foreground">Nivel técnico</label>
                            <p className="text-sm text-foreground">{prefilledNivelTecnico}</p>
                          </div>
                        )}
                      </div>
                    )}
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
                    <label className="text-sm font-medium text-foreground">Tipo de sangre</label>
                    <select {...register("tipoSangre")} className={selectClass}>
                      <option value="">— Sin especificar —</option>
                      {["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"].map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1 sm:col-span-2">
                    <label className="text-sm font-medium text-foreground">
                      Dirección {fromCsvImport && <span className="text-destructive">*</span>}
                    </label>
                    <input {...register("direccion")} className={inputClass} placeholder="Dirección de domicilio" />
                    <FieldError message={errors.direccion?.message} />
                  </div>
                </div>
              </section>

              <section className="space-y-4">
                <h2 className="text-sm font-semibold text-foreground border-b border-border pb-2">
                  Contacto de emergencia {fromCsvImport && <span className="font-normal text-muted-foreground text-xs">(obligatorio)</span>}
                </h2>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1">
                    <label className="text-sm font-medium text-foreground">
                      Nombre {fromCsvImport && <span className="text-destructive">*</span>}
                    </label>
                    <input {...register("emergencyContactName")} className={inputClass} placeholder="Nombre del contacto" />
                    <FieldError message={errors.emergencyContactName?.message} />
                  </div>
                  <div className="space-y-1">
                    <label className="text-sm font-medium text-foreground">
                      Teléfono {fromCsvImport && <span className="text-destructive">*</span>}
                    </label>
                    <input {...register("emergencyContactPhone")} className={inputClass} placeholder="Teléfono del contacto" />
                    <FieldError message={errors.emergencyContactPhone?.message} />
                  </div>
                  <div className="space-y-1 sm:col-span-2">
                    <label className="text-sm font-medium text-foreground">Dirección</label>
                    <input {...register("emergencyContactDireccion")} className={inputClass} placeholder="Dirección del contacto" />
                  </div>
                </div>
              </section>

              <section className="space-y-4">
                <h2 className="text-sm font-semibold text-foreground border-b border-border pb-2">
                  Segundo contacto de emergencia <span className="font-normal text-muted-foreground">(opcional)</span>
                </h2>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1">
                    <label className="text-sm font-medium text-foreground">Nombre</label>
                    <input {...register("emergencyContactName2")} className={inputClass} placeholder="Nombre del contacto" />
                  </div>
                  <div className="space-y-1">
                    <label className="text-sm font-medium text-foreground">Teléfono</label>
                    <input {...register("emergencyContactPhone2")} className={inputClass} placeholder="Teléfono del contacto" />
                  </div>
                  <div className="space-y-1 sm:col-span-2">
                    <label className="text-sm font-medium text-foreground">Dirección</label>
                    <input {...register("emergencyContactDireccion2")} className={inputClass} placeholder="Dirección del contacto" />
                  </div>
                </div>
              </section>
            </>
          )}

          {/* Credentials — always shown */}
          <section className="space-y-4">
            {requiresPersonalData && (
              <h2 className="text-sm font-semibold text-foreground border-b border-border pb-2">
                Credenciales de acceso
              </h2>
            )}
            <div className="space-y-1">
              <label htmlFor="username" className="text-sm font-medium text-foreground">
                Nombre de usuario *
              </label>
              <input
                id="username"
                type="text"
                {...register("username")}
                placeholder="ej. juan.perez"
                autoComplete="username"
                autoCapitalize="none"
                autoCorrect="off"
                className={inputClass}
              />
              <FieldError message={errors.username?.message} />
            </div>
            <div className="space-y-1">
              <label htmlFor="password" className="text-sm font-medium text-foreground">
                Contraseña *
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  {...register("password")}
                  placeholder="Mín. 12 caracteres"
                  autoComplete="new-password"
                  className={cn(inputClass, "pr-10")}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
                  tabIndex={-1}
                  aria-label={showPassword ? "Ocultar contraseña" : "Mostrar contraseña"}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              <PasswordChecklist password={passwordValue} />
            </div>
            <div className="space-y-1">
              <label htmlFor="confirmPassword" className="text-sm font-medium text-foreground">
                Confirmar contraseña *
              </label>
              <div className="relative">
                <input
                  id="confirmPassword"
                  type={showConfirmPassword ? "text" : "password"}
                  {...register("confirmPassword")}
                  placeholder="Repite la contraseña"
                  autoComplete="new-password"
                  className={cn(inputClass, "pr-10")}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword((v) => !v)}
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
                  tabIndex={-1}
                  aria-label={showConfirmPassword ? "Ocultar contraseña" : "Mostrar contraseña"}
                >
                  {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              <FieldError message={errors.confirmPassword?.message} />
            </div>
          </section>

          <button
            type="submit"
            disabled={isSubmitting}
            className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
          >
            {isSubmitting ? (
              <span className="flex items-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
                Activando cuenta...
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
