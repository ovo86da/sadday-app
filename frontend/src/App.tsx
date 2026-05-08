import { Toaster } from "sonner"
import { QueryProvider } from "@/lib/query-client"
import { AuthInitializer } from "@/components/auth/auth-initializer"
import { ErrorBoundary } from "@/components/error-boundary"
import { AppRouter } from "@/router"
import { InformePendienteGuard } from "@/components/informe-pendiente-guard"

export default function App() {
  return (
    <ErrorBoundary>
      <QueryProvider>
        <AuthInitializer>
          <InformePendienteGuard>
            <AppRouter />
          </InformePendienteGuard>
          <Toaster
            position="top-right"
            richColors
            closeButton
            theme="dark"
          />
        </AuthInitializer>
      </QueryProvider>
    </ErrorBoundary>
  )
}
