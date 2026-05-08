import { useState } from "react"
import { Link } from "react-router"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import api from "@/lib/api"

const schema = z.object({
  correo: z
    .string()
    .min(1, "El correo es obligatorio")
    .email("Formato de correo inválido"),
})

type FormData = z.infer<typeof schema>

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const onSubmit = async (data: FormData) => {
    try {
      await api.post("/v1/auth/forgot-password", data)
    } catch (error) { console.error(error);
      // Always show success — backend never revela si el correo existe
    }
    setSent(true)
  }

  if (sent) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="mx-auto w-full max-w-sm space-y-6 rounded-xl border border-border bg-card p-8 shadow-lg text-center">
          <div className="space-y-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Revisa tu correo
            </h1>
            <p className="text-sm text-muted-foreground">
              Si el correo está registrado, recibirás un enlace para restablecer
              tu contraseña en los próximos minutos.
            </p>
          </div>
          <Link
            to="/login"
            className="block text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            Volver al inicio de sesión
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="mx-auto w-full max-w-sm space-y-6 rounded-xl border border-border bg-card p-8 shadow-lg">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            Recuperar contraseña
          </h1>
          <p className="text-sm text-muted-foreground">
            Ingresa tu correo y te enviaremos un enlace para restablecerla.
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <label htmlFor="correo" className="text-sm font-medium text-foreground">
              Correo electrónico
            </label>
            <input
              id="correo"
              type="email"
              {...register("correo")}
              placeholder="tu@correo.com"
              autoComplete="email"
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            />
            {errors.correo && (
              <p className="text-xs text-destructive">{errors.correo.message}</p>
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
                Enviando...
              </span>
            ) : (
              "Enviar enlace"
            )}
          </button>

          <Link
            to="/login"
            className="block text-center text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            Volver al inicio de sesión
          </Link>
        </form>
      </div>
    </div>
  )
}
