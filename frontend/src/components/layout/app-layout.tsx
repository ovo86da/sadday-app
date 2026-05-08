import { useState } from "react"
import { Link, Outlet, useLocation, useNavigate } from "react-router"
import { useAuthStore } from "@/stores/auth-store"
import { navConfig, type NavItem } from "@/config/nav"
import { cn } from "@/lib/utils"
import api from "@/lib/api"
import { toast } from "sonner"
import { useAprobacionesPendientes, useAlertasSinJefe } from "@/hooks/use-salidas"
import {
  LogOut,
  Menu,
  X,
  ChevronRight,
  ClipboardCheck,
} from "lucide-react"
import logoUrl from "@/assets/logo_dorado.svg"

/**
 * Layout principal de la app autenticada.
 * Sidebar (desktop) + Sheet (mobile) + Header + Outlet.
 */
export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const user = useAuthStore((s) => s.user)
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const location = useLocation()
  const navigate = useNavigate()

  const userRole = user?.rol?.toUpperCase() ?? ""

  const filteredNav = navConfig
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) => !item.roles || item.roles.includes(userRole)
      ),
    }))
    .filter((group) => group.items.length > 0)

  const handleLogout = async () => {
    try {
      await api.post("/v1/auth/logout")
    } catch (error) { console.error(error);
      // Ignorar errores — igual limpiamos la sesión local
    }
    clearAuth()
    toast.success("Sesión cerrada")
    navigate("/login", { replace: true })
  }

  const isActive = (href: string) =>
    location.pathname === href || location.pathname.startsWith(href + "/")

  const esPrivilegiado = ["ADMIN", "SECRETARIA", "DIRECTIVO"].includes(userRole)
  const { data: aprobaciones } = useAprobacionesPendientes(true)
  const { data: alertasSinJefe } = useAlertasSinJefe(esPrivilegiado)
  const pendingCount = aprobaciones?.length ?? 0
  const sinJefeCount = alertasSinJefe?.length ?? 0
  const totalNotificaciones = pendingCount + sinJefeCount

  // Breadcrumb from current path
  const pathSegments = location.pathname.split("/").filter(Boolean)

  return (
    <div className="flex h-screen bg-background">
      {/* ── Sidebar (desktop) ──────────────────────────── */}
      <aside className="hidden w-64 flex-col border-r border-sidebar-border bg-sidebar lg:flex">
        <SidebarContent
          nav={filteredNav}
          isActive={isActive}
          onLogout={handleLogout}
          user={user}
        />
      </aside>

      {/* ── Mobile overlay ─────────────────────────────── */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-black/60 backdrop-blur-sm"
            onClick={() => setSidebarOpen(false)}
          />
          <aside className="absolute left-0 top-0 h-full w-64 flex-col border-r border-sidebar-border bg-sidebar shadow-xl flex">
            <SidebarContent
              nav={filteredNav}
              isActive={isActive}
              onLogout={handleLogout}
              user={user}
              onItemClick={() => setSidebarOpen(false)}
            />
          </aside>
        </div>
      )}

      {/* ── Main content area ──────────────────────────── */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Header */}
        <header className="flex h-14 items-center gap-4 border-b border-border bg-card px-4 lg:px-6">
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="rounded-md p-2 text-muted-foreground hover:bg-accent hover:text-foreground lg:hidden"
          >
            {sidebarOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>

          {/* Breadcrumb */}
          <nav className="flex items-center gap-1 text-sm text-muted-foreground">
            {pathSegments.map((seg, i) => (
              <span key={i} className="flex items-center gap-1">
                {i > 0 && <ChevronRight className="h-3 w-3" />}
                <span className={cn(
                  "capitalize",
                  i === pathSegments.length - 1 && "text-foreground font-medium"
                )}>
                  {decodeURIComponent(seg)}
                </span>
              </span>
            ))}
          </nav>

          <div className="flex-1" />

          {/* User info */}
          <div className="flex items-center gap-2">
            <span className="hidden text-sm text-muted-foreground sm:block">
              {user?.nombre}
            </span>
            {user?.nivelTecnico && (
              <span className="hidden rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground sm:block">
                {user.nivelTecnico}
              </span>
            )}
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
              {user?.rol}
            </span>
          </div>
        </header>

        {/* Banner — socio inhabilitado */}
        {user?.inhabilitado && (
          <div className="w-full bg-destructive px-4 py-3 text-white">
            <p className="text-center text-sm font-bold leading-snug">
              ⚠️ Socio deshabilitado, por favor igualese en cuotas!!
            </p>
            <p className="mt-1 text-center text-xs opacity-90">
              Nota: si no puede pagar, por favor contacte a la secretaria o presidenta del club para conversar sobre opciones de pago.
            </p>
          </div>
        )}

        {/* Alert banner — notificaciones pendientes */}
        {totalNotificaciones > 0 && location.pathname !== "/notificaciones" && (
          <button
            className="flex w-full items-center justify-center gap-2 bg-amber-400 px-4 py-2 text-xs font-medium text-amber-950 hover:bg-amber-500 transition-colors"
            onClick={() => navigate("/notificaciones")}
          >
            <ClipboardCheck className="h-4 w-4 shrink-0" />
            Tienes {totalNotificaciones} notificación{totalNotificaciones !== 1 ? "es" : ""} pendiente{totalNotificaciones !== 1 ? "s" : ""} — Clic para ver
          </button>
        )}

        {/* Page content */}
        <main className="flex-1 overflow-y-auto p-4 lg:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

// ─── Sidebar inner component ──────────────────────────────

interface SidebarContentProps {
  nav: { label: string; items: NavItem[] }[]
  isActive: (href: string) => boolean
  onLogout: () => void
  user: { nombre: string; username: string; rol: string } | null
  onItemClick?: () => void
}

function SidebarContent({ nav, isActive, onLogout, user, onItemClick }: SidebarContentProps) {
  return (
    <>
      {/* Logo / Brand */}
      <div className="flex h-14 items-center border-b border-sidebar-border px-4">
        <img src={logoUrl} alt="Club Sadday" className="w-full h-auto" />
      </div>

      {/* Nav groups */}
      <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
        {nav.map((group) => (
          <div key={group.label} className="mb-4">
            <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/50">
              {group.label}
            </p>
            {group.items.map((item) => (
              <Link
                key={item.href}
                to={item.href}
                onClick={onItemClick}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  isActive(item.href)
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground/70 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground"
                )}
              >
                <item.icon className="h-4 w-4 shrink-0" />
                {item.title}
              </Link>
            ))}
          </div>
        ))}
      </nav>

      {/* Footer / User */}
      <div className="border-t border-sidebar-border p-3">
        <div className="flex items-center gap-3 rounded-lg px-3 py-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-sidebar-accent text-sm font-bold text-sidebar-accent-foreground">
            {user?.nombre?.charAt(0)?.toUpperCase() ?? "?"}
          </div>
          <div className="flex-1 overflow-hidden">
            <p className="truncate text-sm font-medium text-sidebar-foreground">
              {user?.nombre}
            </p>
            <p className="truncate text-xs text-sidebar-foreground/50">
              {user?.username}
            </p>
          </div>
          <button
            onClick={onLogout}
            className="rounded-md p-1.5 text-sidebar-foreground/50 hover:bg-sidebar-accent hover:text-sidebar-foreground"
            title="Cerrar sesión"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </>
  )
}
