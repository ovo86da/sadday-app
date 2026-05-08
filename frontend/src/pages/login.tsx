import { useState } from "react"
import { Eye, EyeOff, ShieldCheck, Globe, ArrowLeft } from "lucide-react"
import { useNavigate, Link } from "react-router"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { toast } from "sonner"
import { useAuthStore } from "@/stores/auth-store"
import api from "@/lib/api"
import { getApiErrorMessage } from "@/lib/api-error"

// ─── Paso 1: credenciales ────────────────────────────────────────────────────

const credencialesSchema = z.object({
  username: z.string().min(1, "El usuario es obligatorio"),
  password: z.string().min(1, "La contraseña es obligatoria"),
})
type CredencialesForm = z.infer<typeof credencialesSchema>

// ─── Paso 2: código MFA ──────────────────────────────────────────────────────

const mfaSchema = z.object({
  mfaCode: z.string().length(6, "El código debe tener 6 dígitos").regex(/^\d{6}$/, "Solo dígitos"),
})
type MfaForm = z.infer<typeof mfaSchema>

// ─── Paso 3: verificación de país por email ───────────────────────────────────

const countrySchema = z.object({
  countryCode: z.string().length(6, "El código debe tener 6 dígitos").regex(/^\d{6}$/, "Solo dígitos"),
})
type CountryForm = z.infer<typeof countrySchema>

// ─── Componente principal ────────────────────────────────────────────────────

export function LoginPage() {
  const [step, setStep] = useState<"credentials" | "mfa" | "country_challenge">("credentials")
  const [challengeToken, setChallengeToken] = useState<string | null>(null)
  const [countryChallengeToken, setCountryChallengeToken] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  const setAuth = useAuthStore((s) => s.setAuth)
  const navigate = useNavigate()

  // ── Formulario paso 1 ──
  const credForm = useForm<CredencialesForm>({ resolver: zodResolver(credencialesSchema) })

  // ── Formulario paso 2 ──
  const mfaForm = useForm<MfaForm>({ resolver: zodResolver(mfaSchema) })
  const mfaCodeProps = mfaForm.register("mfaCode")

  // ── Formulario paso 3 ──
  const countryForm = useForm<CountryForm>({ resolver: zodResolver(countrySchema) })
  const countryCodeProps = countryForm.register("countryCode")

  const saveAuth = (res: { data: Record<string, unknown> }) => {
    const d = res.data as {
      accessToken: string; socioId: string; username: string
      nombre: string; rol: string; nivelTecnico?: string; inhabilitado?: boolean; esJefeMontana?: boolean
    }
    setAuth({
      accessToken: d.accessToken,
      user: {
        socioId: d.socioId,
        username: d.username,
        nombre: d.nombre,
        rol: d.rol,
        nivelTecnico: d.nivelTecnico ?? null,
        inhabilitado: d.inhabilitado ?? false,
        esJefeMontana: d.esJefeMontana ?? false,
      },
    })
    toast.success(`Bienvenido, ${d.nombre}`)
    navigate("/dashboard", { replace: true })
  }

  // ── Submit paso 1: user + pass ──
  const onSubmitCredenciales = async (data: CredencialesForm) => {
    setServerError(null)
    try {
      const { data: res } = await api.post("/v1/auth/login", data)

      if (res.data.challengeToken) {
        // 202 + challengeToken → tiene 2FA activo
        setChallengeToken(res.data.challengeToken)
        setStep("mfa")
      } else if (res.data.countryChallengeToken) {
        // 202 + countryChallengeToken → país desconocido sin 2FA
        setCountryChallengeToken(res.data.countryChallengeToken)
        setStep("country_challenge")
      } else {
        // 200 → login completo
        saveAuth(res)
      }
    } catch (err: unknown) {
      setServerError(getApiErrorMessage(err, "Error al iniciar sesión"))
    }
  }

  // ── Submit paso 2: código TOTP ──
  const onSubmitMfa = async (data: MfaForm) => {
    setServerError(null)
    try {
      const { data: res } = await api.post("/v1/auth/mfa/login", {
        challengeToken,
        mfaCode: data.mfaCode,
      })
      saveAuth(res)
    } catch (err: unknown) {
      setServerError(getApiErrorMessage(err, "Código incorrecto o expirado"))
    }
  }

  // ── Submit paso 3: código por email (verificación de país) ──
  const onSubmitCountryChallenge = async (data: CountryForm) => {
    setServerError(null)
    try {
      const { data: res } = await api.post("/v1/auth/country-challenge/verify", {
        challengeToken: countryChallengeToken,
        code: data.countryCode,
      })
      saveAuth(res)
    } catch (err: unknown) {
      setServerError(getApiErrorMessage(err, "Código incorrecto o expirado"))
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="mx-auto w-full max-w-sm space-y-6 rounded-xl border border-border bg-card p-8 shadow-lg">

        {/* Header */}
        <div className="space-y-2 text-center">
          {step === "mfa" ? (
            <>
              <div className="flex justify-center">
                <ShieldCheck className="h-10 w-10 text-primary" />
              </div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                Verificación en dos pasos
              </h1>
              <p className="text-sm text-muted-foreground">
                Ingresa el código de 6 dígitos de tu aplicación autenticadora
              </p>
            </>
          ) : step === "country_challenge" ? (
            <>
              <div className="flex justify-center">
                <Globe className="h-10 w-10 text-primary" />
              </div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                Verificación de ubicación
              </h1>
              <p className="text-sm text-muted-foreground">
                Detectamos un acceso desde un país no reconocido. Te enviamos un código de 6 dígitos a tu correo.
              </p>
            </>
          ) : (
            <>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">Club Sadday</h1>
              <p className="text-sm text-muted-foreground">Inicia sesión para continuar</p>
            </>
          )}
        </div>

        {/* ── Paso 1: credenciales ── */}
        {step === "credentials" && (
          <form onSubmit={credForm.handleSubmit(onSubmitCredenciales)} className="space-y-4">
            <div className="space-y-2">
              <label htmlFor="username" className="text-sm font-medium text-foreground">
                Usuario
              </label>
              <input
                id="username"
                type="text"
                {...credForm.register("username")}
                placeholder="tu.usuario"
                autoComplete="username"
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
              />
              {credForm.formState.errors.username && (
                <p className="text-xs text-destructive">{credForm.formState.errors.username.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <label htmlFor="password" className="text-sm font-medium text-foreground">
                Contraseña
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  {...credForm.register("password")}
                  placeholder="••••••••"
                  autoComplete="current-password"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 pr-10 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {credForm.formState.errors.password && (
                <p className="text-xs text-destructive">{credForm.formState.errors.password.message}</p>
              )}
            </div>

            {serverError && (
              <div className="rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive">
                {serverError}
              </div>
            )}

            <button
              type="submit"
              disabled={credForm.formState.isSubmitting}
              className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
            >
              {credForm.formState.isSubmitting ? (
                <span className="flex items-center gap-2">
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
                  Verificando...
                </span>
              ) : (
                "Iniciar sesión"
              )}
            </button>

            <Link
              to="/forgot-password"
              className="block text-center text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              ¿Olvidaste tu contraseña?
            </Link>
          </form>
        )}

        {/* ── Paso 2: código MFA ── */}
        {step === "mfa" && (
          <form onSubmit={mfaForm.handleSubmit(onSubmitMfa)} className="space-y-4">
            <div className="space-y-2">
              <label htmlFor="mfaCode" className="text-sm font-medium text-foreground">
                Código de verificación
              </label>
              <input
                id="mfaCode"
                type="text"
                {...mfaCodeProps}
                onChange={(e) => {
                  e.target.value = e.target.value.replace(/\D/g, "").slice(0, 6)
                  mfaCodeProps.onChange(e)
                }}
                placeholder="123456"
                maxLength={6}
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                className="flex h-12 w-full rounded-md border border-input bg-background px-3 py-2 text-center text-2xl font-mono tracking-widest text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
              />
              {mfaForm.formState.errors.mfaCode && (
                <p className="text-xs text-destructive">{mfaForm.formState.errors.mfaCode.message}</p>
              )}
            </div>

            {serverError && (
              <div className="rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive">
                {serverError}
              </div>
            )}

            <button
              type="submit"
              disabled={mfaForm.formState.isSubmitting}
              className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
            >
              {mfaForm.formState.isSubmitting ? (
                <span className="flex items-center gap-2">
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
                  Verificando...
                </span>
              ) : (
                "Verificar"
              )}
            </button>

            <button
              type="button"
              onClick={() => { setStep("credentials"); setServerError(null); setChallengeToken(null) }}
              className="flex w-full items-center justify-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              Volver al inicio de sesión
            </button>
          </form>
        )}

        {/* ── Paso 3: código por email (verificación de país) ── */}
        {step === "country_challenge" && (
          <form onSubmit={countryForm.handleSubmit(onSubmitCountryChallenge)} className="space-y-4">
            <div className="space-y-2">
              <label htmlFor="countryCode" className="text-sm font-medium text-foreground">
                Código de verificación
              </label>
              <input
                id="countryCode"
                type="text"
                {...countryCodeProps}
                onChange={(e) => {
                  e.target.value = e.target.value.replace(/\D/g, "").slice(0, 6)
                  countryCodeProps.onChange(e)
                }}
                placeholder="123456"
                maxLength={6}
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                className="flex h-12 w-full rounded-md border border-input bg-background px-3 py-2 text-center text-2xl font-mono tracking-widest text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
              />
              {countryForm.formState.errors.countryCode && (
                <p className="text-xs text-destructive">{countryForm.formState.errors.countryCode.message}</p>
              )}
              <p className="text-xs text-muted-foreground text-center">
                El código expira en 15 minutos
              </p>
            </div>

            {serverError && (
              <div className="rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive">
                {serverError}
              </div>
            )}

            <button
              type="submit"
              disabled={countryForm.formState.isSubmitting}
              className="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
            >
              {countryForm.formState.isSubmitting ? (
                <span className="flex items-center gap-2">
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
                  Verificando...
                </span>
              ) : (
                "Verificar ubicación"
              )}
            </button>

            <button
              type="button"
              onClick={() => { setStep("credentials"); setServerError(null); setCountryChallengeToken(null) }}
              className="flex w-full items-center justify-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              Volver al inicio de sesión
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
