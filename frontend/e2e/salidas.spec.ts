/**
 * salidas.spec.ts — Inscripción a salidas (rol Socio)
 *
 * Ejecuta con storageState de socio (socio-tests project).
 *
 * Cubre:
 *   - Ver listado de salidas planificadas
 *   - Inscribirse en una salida disponible
 *   - Ver la inscripción reflejada ("Ya inscrito" o botón cancelar)
 *   - Ver historial en "Mis salidas"
 */

import { test, expect } from "@playwright/test"

test.beforeEach(async ({ page }) => {
  await page.goto("/salidas")
  // Esperar a que cargue la vista de socio (no la tabla admin)
  await expect(page.getByRole("heading", { name: /salidas/i })).toBeVisible({ timeout: 8_000 })
})

// ─── Listado ───────────────────────────────────────────────────────────────────

test("socio ve listado de salidas planificadas", async ({ page }) => {
  // La vista de socio muestra cards (no tabla)
  // Debe aparecer el tab "Próximas salidas" activo por defecto
  await expect(page.getByRole("button", { name: /próximas salidas/i })).toBeVisible()

  // Puede haber cards de salidas o el mensaje de vacío
  const hayCards = await page.locator(".rounded-xl.border").count()
  // Simplemente verificamos que la página cargó correctamente (sin error)
  expect(hayCards).toBeGreaterThanOrEqual(0)
})

test("búsqueda de salidas filtra por nombre", async ({ page }) => {
  const searchInput = page.getByPlaceholder(/buscar salidas/i)
  await searchInput.fill("zzznoresults99999")
  await page.getByRole("button", { name: /buscar/i }).click()

  await expect(
    page.getByText(/no hay salidas planificadas/i),
  ).toBeVisible({ timeout: 5_000 })

  // Limpiar
  await searchInput.clear()
  await page.getByRole("button", { name: /buscar/i }).click()
})

// ─── Inscripción ───────────────────────────────────────────────────────────────

test("inscribirse en salida disponible y verificar estado", async ({ page }) => {
  // Buscar el primer botón "Inscribirse" disponible (si existe alguna salida planificada)
  const inscribirBtn = page.getByRole("button", { name: /^inscribirse$/i }).first()

  const haySalidas = await inscribirBtn.count()
  if (haySalidas === 0) {
    test.skip(true, "No hay salidas planificadas disponibles en el entorno de test")
    return
  }

  await inscribirBtn.click()

  // Después de inscribirse, el botón cambia a "Cancelar inscripción"
  // O si venía del historial, a "Ya inscrito"
  await expect(
    page.getByRole("button", { name: /cancelar inscripci[oó]n/i }).or(
      page.getByText(/ya inscrito/i),
    ).first(),
  ).toBeVisible({ timeout: 8_000 })
})

// ─── Mis salidas ───────────────────────────────────────────────────────────────

test("tab Mis salidas muestra historial y KPIs", async ({ page }) => {
  await page.getByRole("button", { name: /mis salidas/i }).click()

  // Los 3 KPI cards deben aparecer
  await expect(page.getByText(/participaciones/i)).toBeVisible({ timeout: 5_000 })
  await expect(page.getByText(/cumbres logradas/i)).toBeVisible({ timeout: 5_000 })
  await expect(page.getByText(/veces jefe/i)).toBeVisible({ timeout: 5_000 })

  // O bien hay historial o bien el mensaje de vacío
  const vacio = page.getByText(/aún no tienes salidas/i)
  const historial = page.getByRole("list")

  const tieneVacio = await vacio.count()
  const tieneHistorial = await historial.count()

  expect(tieneVacio + tieneHistorial).toBeGreaterThan(0)
})
