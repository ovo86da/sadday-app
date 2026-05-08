import { defineConfig, devices } from "@playwright/test"

/**
 * Playwright E2E configuration.
 *
 * Antes de correr los tests:
 *   1. Levantar el backend: docker compose up (o ./mvnw spring-boot:run)
 *   2. Copiar .env.e2e.example a .env.e2e y completar credenciales reales de test
 *   3. pnpm test:e2e
 *
 * La autenticación se resuelve en el proyecto "setup" (auth.setup.ts) y se guarda
 * como cookie + storageState. Los tests recargan esas cookies para obtener un nuevo
 * access token vía el interceptor de refresh automático de la app.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,          // los tests comparten estado del backend → serial
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [["html", { open: "never" }], ["list"]],

  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "on-first-retry",
    locale: "es-EC",
  },

  projects: [
    // ── 1. Setup: hace login y guarda storageState ────────────────────────────
    {
      name: "setup",
      testMatch: /auth\.setup\.ts/,
    },

    // ── 2. Tests autenticados como ADMIN ──────────────────────────────────────
    {
      name: "admin-tests",
      use: {
        ...devices["Desktop Chrome"],
        storageState: "e2e/.auth/admin.json",
      },
      dependencies: ["setup"],
      testMatch: /\/(auth|socios|informes)\.spec\.ts/,
    },

    // ── 3. Tests autenticados como SOCIO ──────────────────────────────────────
    {
      name: "socio-tests",
      use: {
        ...devices["Desktop Chrome"],
        storageState: "e2e/.auth/socio.json",
      },
      dependencies: ["setup"],
      testMatch: /\/salidas\.spec\.ts/,
    },
  ],

  // Levanta pnpm dev si no está corriendo ya (útil en CI)
  webServer: {
    command: "pnpm dev",
    url: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    reuseExistingServer: true,     // en dev local reutiliza el servidor ya levantado
    timeout: 60_000,
  },
})
