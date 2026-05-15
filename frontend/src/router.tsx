import { BrowserRouter, Routes, Route, Navigate } from "react-router"
import { PrivateRoute, RoleRoute } from "@/components/auth/route-guards"
import { AppLayout } from "@/components/layout/app-layout"

// Pages
import { LoginPage } from "@/pages/login"
import { ForgotPasswordPage } from "@/pages/forgot-password"
import { ResetPasswordPage } from "@/pages/reset-password"
import { RegistroCompletarPage } from "@/pages/registro-completar"
import { NotFoundPage } from "@/pages/not-found"
import { ForbiddenPage } from "@/pages/forbidden"
import { DashboardPage } from "@/pages/dashboard"
import { SociosPage } from "@/pages/socios"
import { MontanasPage } from "@/pages/montanas"
import { SalidasPage } from "@/pages/salidas"
import { RutasPage } from "@/pages/rutas"
import { InformesPage } from "@/pages/informes"
import { ActasPage } from "@/pages/actas"
import { PerfilPage } from "@/pages/perfil"
import { AdminPage } from "@/pages/admin/admin-page"
import { AccesoNivelPage } from "@/pages/acceso-nivel/acceso-nivel-page"
import { EstadisticasPage } from "@/pages/estadisticas/estadisticas-page"
import { PlanificadorPage } from "@/pages/planificador/planificador-page"
import { ContactosPage } from "@/pages/contactos/contactos-page"
import NotificacionesPage from "@/pages/aprobaciones/aprobaciones-page"
import { TeoriaPage } from "@/pages/teoria/teoria-page"

const ADMIN_ROLES = ["ADMIN", "SECRETARIA", "DIRECTIVO"]

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* ─── Públicas (sin layout) ─────────────────── */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/registro/completar" element={<RegistroCompletarPage />} />
        <Route path="/403" element={<ForbiddenPage />} />

        {/* ─── Autenticadas (con layout) ─────────────── */}
        <Route
          element={
            <PrivateRoute>
              <AppLayout />
            </PrivateRoute>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/socios" element={<RoleRoute allowedRoles={ADMIN_ROLES}><SociosPage /></RoleRoute>} />
          <Route path="/montanas" element={<MontanasPage />} />
          <Route path="/rutas" element={<RutasPage />} />
          <Route path="/salidas" element={<SalidasPage />} />
          <Route path="/notificaciones" element={<NotificacionesPage />} />
          <Route path="/aprobaciones" element={<Navigate to="/notificaciones" replace />} />
          <Route path="/acceso-nivel" element={<AccesoNivelPage />} />
          <Route path="/planificador" element={<PlanificadorPage />} />
          <Route path="/estadisticas" element={<EstadisticasPage />} />
          <Route path="/informes" element={<InformesPage />} />
          <Route path="/actas" element={<ActasPage />} />
          <Route path="/perfil" element={<PerfilPage />} />
          <Route path="/teoria" element={<TeoriaPage />} />
          <Route
            path="/admin"
            element={
              <RoleRoute allowedRoles={["ADMIN", "SECRETARIA"]}>
                <AdminPage />
              </RoleRoute>
            }
          />
          <Route path="/auditoria" element={<Navigate to="/admin" replace />} />
          <Route
            path="/contactos"
            element={
              <RoleRoute allowedRoles={["ADMIN", "SECRETARIA"]}>
                <ContactosPage />
              </RoleRoute>
            }
          />
        </Route>

        {/* ─── Redirecciones ─────────────────────────── */}
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}
