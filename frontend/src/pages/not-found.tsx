import { Link } from "react-router"

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="mx-auto max-w-md space-y-4 p-8 text-center">
        <div className="text-6xl">🔍</div>
        <h1 className="text-3xl font-bold text-foreground">404</h1>
        <p className="text-muted-foreground">
          La página que buscas no existe.
        </p>
        <Link
          to="/dashboard"
          className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-6 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
        >
          Ir al inicio
        </Link>
      </div>
    </div>
  )
}
