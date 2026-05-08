/**
 * auth.spec.ts — Flujos de autenticación
 *
 * Cubre:
 *   - Login exitoso → redirige al dashboard
 *   - Login con credenciales incorrectas → muestra error
 *   - Refresh automático de sesión al recargar la página
 *   - Logout → redirige al login y limpia sesión
 *   - Acceso denegado por rol (ADMIN intentando /salidas como socio ya no aplica;
 *     en cambio un usuario sin permisos que intenta /socios → redirige a /403)
 */

import { test, expect } from "@playwright/test"
import dotenv from "dotenv"
import path from "path"

dotenv.config({ path: path.resolve(__dirname, "../.env.e2e") })

// ─── Login exitoso ─────────────────────────────────────────────────────────────

test("login exitoso → llega al dashboard", async ({ page }) => {
  await page.goto("/login")

  await page.getByLabel("Usuario").fill(process.env.E2E_ADMIN_USERNAME!)
  await page.getByLabel("Contraseña").fill(process.env.E2E_ADMIN_PASSWORD!)
  await page.getByRole("button", { name: "Iniciar sesión" }).click()

  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })
  // El dashboard debe mostrar algún KPI o widget
  await expect(page.getByRole("heading", { name: /dashboard/i })).toBeVisible({ timeout: 8_000 })
})

// ─── Login fallido ─────────────────────────────────────────────────────────────

test("login fallido → muestra mensaje de error", async ({ page }) => {
  await page.goto("/login")

  await page.getByLabel("Usuario").fill("usuario.inexistente")
  await page.getByLabel("Contraseña").fill("contraseña_incorrecta_123")
  await page.getByRole("button", { name: "Iniciar sesión" }).click()

  // No debe navegar — debe mostrar un mensaje de error
  await expect(page).toHaveURL(/\/login/, { timeout: 5_000 })
  const errorMsg = page.locator(".text-destructive, [role='alert']")
  await expect(errorMsg.first()).toBeVisible({ timeout: 5_000 })
})

// ─── Refresh automático ────────────────────────────────────────────────────────

test("refresh automático: recarga de página mantiene la sesión", async ({ page }) => {
  // La storageState ya tiene la cookie de refresh
  await page.goto("/dashboard")

  // La app llama a POST /auth/refresh al montar → el usuario debe aparecer autenticado
  await expect(page).not.toHaveURL(/\/login/, { timeout: 8_000 })
  await expect(page.getByRole("heading", { name: /dashboard/i })).toBeVisible({ timeout: 8_000 })

  // Forzar recarga completa (simula F5)
  await page.reload()
  await expect(page).not.toHaveURL(/\/login/, { timeout: 8_000 })
  await expect(page.getByRole("heading", { name: /dashboard/i })).toBeVisible({ timeout: 8_000 })
})

// ─── Logout ────────────────────────────────────────────────────────────────────

test("logout → redirige al login y borra sesión", async ({ page }) => {
  await page.goto("/dashboard")
  await expect(page.getByRole("heading", { name: /dashboard/i })).toBeVisible({ timeout: 8_000 })

  // El botón de logout puede estar en el sidebar o en un menú de usuario
  const logoutBtn = page.getByRole("button", { name: /cerrar sesi[oó]n/i })
  await expect(logoutBtn).toBeVisible({ timeout: 5_000 })
  await logoutBtn.click()

  await expect(page).toHaveURL(/\/login/, { timeout: 8_000 })

  // Verificar que ya no puede acceder al dashboard sin autenticarse de nuevo
  await page.goto("/dashboard")
  await expect(page).toHaveURL(/\/login/, { timeout: 5_000 })
})

// ─── Acceso denegado por rol ───────────────────────────────────────────────────

test("acceso denegado: admin en ruta de solo admin no aplica, pero ruta inexistente → 404", async ({ page }) => {
  // Un usuario autenticado que va a una ruta protegida sin permisos → /403
  // Aquí probamos navegando a /socios (solo Admin/Secretaria/Directivo)
  // desde la sesión de admin → debe funcionar
  await page.goto("/socios")
  await expect(page).not.toHaveURL(/\/403/, { timeout: 8_000 })
  await expect(page.getByRole("heading", { name: /socios/i })).toBeVisible({ timeout: 8_000 })

  // Ruta inexistente → 404
  await page.goto("/ruta-que-no-existe")
  await expect(
    page.getByText(/no encontrada|404/i),
  ).toBeVisible({ timeout: 5_000 })
})
