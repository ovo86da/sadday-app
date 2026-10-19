import { defineConfig, devices } from "@playwright/test"

/**
 * Playwright E2E configuration.
 *
 * Antes de correr los tests:
 *   1. Levantar el backend: docker compose up (o ./mvnw spring-boot:run)
 *   2. Copiar .env.e2e.example a .env.e2e y completar credenciales reales de test
 *   3. pnpm test:e2e
 *
 * La autenticación NO se comparte por `storageState`. El refresh token del
 * backend rota y es de un solo uso, así que un fichero de storageState vale
 * exactamente una carga: el primero que lo gasta lo deja muerto para todos los
 * demás, y el resto acaba en /login con un error que no se parece a la causa.
 * Cada fichero de tests abre su propia sesión una vez, en `beforeAll`, y la
 * comparte entre sus tests (ver e2e/login.ts).
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,          // los tests comparten estado del backend → serial
  // Y un solo worker: `fullyParallel: false` solo serializa dentro de cada
  // fichero, no entre proyectos. Con `admin-tests` y `socio-tests` a la vez se
  // pisan el mismo backend —suben y borran los mismos documentos, crean los
  // mismos socios— y los fallos salen en sitios que no tienen nada que ver.
  // Antes quedaba serializado de rebote porque ambos dependian de `setup`.
  workers: 1,
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
    {
      name: "admin-tests",
      use: { ...devices["Desktop Chrome"] },
      testMatch: /\/(auth|socios|informes)\.spec\.ts/,
    },
    {
      name: "socio-tests",
      use: { ...devices["Desktop Chrome"] },
      testMatch: /\/(salidas|rutas)\.spec\.ts/,
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
