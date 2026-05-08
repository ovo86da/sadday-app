import { useState } from "react"
import { Link, useSearchParams, useNavigate } from "react-router"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { toast } from "sonner"
import { Eye, EyeOff, Check, X as XIcon } from "lucide-react"
import api from "@/lib/api"
import { cn } from "@/lib/utils"

// ─── Requisitos de contraseña (deben coincidir con @StrongPassword del backend) ──

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

// ─── Schema ───────────────────────────────────────────────────────────────────

const schema = z
  .object({
    nuevaPassword: z
      .string()
      .min(12, "Mínimo 12 caracteres")
      .regex(/[A-Z]/, "Debe incluir al menos una mayúscula")
      .regex(/[a-z]/, "Debe incluir al menos una minúscula")
      .regex(/[0-9]/, "Debe incluir al menos un número")
      .regex(/[^a-zA-Z0-9\s]/, "Debe incluir al menos un símbolo"),
    confirmPassword: z.string().min(1, "Confirma tu contraseña"),
  })
  .refine((d) => d.nuevaPassword === d.confirmPassword, {
    message: "Las contraseñas no coinciden",
    path: ["confirmPassword"],
  })

type FormData = z.infer<typeof schema>

// ─── Page ─────────────────────────────────────────────────────────────────────

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get("token")
  const [done, setDone] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const passwordValue = watch("nuevaPassword") ?? ""

  const onSubmit = async (data: FormData) => {
    try {
      await api.post("/v1/auth/reset-password", {
        token,
        nuevaPassword: data.nuevaPassword,
        confirmPassword: data.confirmPassword,
      })
      setDone(true)
      toast.success("Contraseña restablecida correctamente")
      setTimeout(() => navigate("/login", { replace: true }), 3000)
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { message?: string } } }
      toast.error(axiosError.response?.data?.message || "El enlace es inválido o ya expiró")
    }
  }

  const inputClass =
    "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"

  if (!token) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="mx-auto w-full max-w-sm space-y-4 rounded-xl border border-border bg-card p-8 shadow-lg text-center">
          <h1 className="text-2xl font-bold text-foreground">Enlace inválido</h1>
          <p className="text-sm text-muted-foreground">
            El enlace de recuperación no es válido o no se encontró el token.
          </p>
          <Link
            to="/forgot-password"
            className="block text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            Solicitar un nuevo enlace
          </Link>
        </div>
      </div>
    )
  }

  if (done) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="mx-auto w-full max-w-sm space-y-4 rounded-xl border border-border bg-card p-8 shadow-lg text-center">
          <h1 className="text-2xl font-bold text-foreground">¡Contraseña restablecida!</h1>
          <p className="text-sm text-muted-foreground">
            Tu contraseña fue actualizada. Serás redirigido al inicio de sesión en unos segundos.
          </p>
          <Link to="/login" className="block text-sm text-muted-foreground hover:text-foreground transition-colors">
            Ir al inicio de sesión
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="mx-auto w-full max-w-sm space-y-6 rounded-xl border border-border bg-card p-8 shadow-lg">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">Nueva contraseña</h1>
          <p className="text-sm text-muted-foreground">
            Elige una contraseña segura que cumpla todos los requisitos.
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {/* Nueva contraseña */}
          <div className="space-y-1">
            <label htmlFor="nuevaPassword" className="text-sm font-medium text-foreground">
              Nueva contraseña
            </label>
            <div className="relative">
              <input
                id="nuevaPassword"
                type={showPassword ? "text" : "password"}
                {...register("nuevaPassword")}
                placeholder="Mín. 12 caracteres"
                autoComplete="new-password"
                className={cn(inputClass, "pr-10")}
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                tabIndex={-1}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            {errors.nuevaPassword && (
              <p className="text-xs text-destructive">{errors.nuevaPassword.message}</p>
            )}
            <PasswordChecklist password={passwordValue} />
          </div>

          {/* Confirmar contraseña */}
          <div className="space-y-1">
            <label htmlFor="confirmPassword" className="text-sm font-medium text-foreground">
              Confirmar contraseña
            </label>
            <div className="relative">
              <input
                id="confirmPassword"
                type={showConfirm ? "text" : "password"}
                {...register("confirmPassword")}
                placeholder="Repite la contraseña"
                autoComplete="new-password"
                className={cn(inputClass, "pr-10")}
              />
              <button
                type="button"
                onClick={() => setShowConfirm((v) => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                tabIndex={-1}
              >
                {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            {errors.confirmPassword && (
              <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
          >
            {isSubmitting ? (
              <span className="flex items-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
                Guardando...
              </span>
            ) : (
              "Guardar contraseña"
            )}
          </button>
        </form>

        <p className="text-center text-xs text-muted-foreground">
          <Link to="/login" className="hover:text-foreground transition-colors">
            Volver al inicio de sesión
          </Link>
        </p>
      </div>
    </div>
  )
}
