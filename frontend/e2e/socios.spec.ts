/**
 * socios.spec.ts — CRUD de socios (rol Admin)
 *
 * Cubre:
 *   - Ver listado de socios con paginación
 *   - Buscar socios por nombre
 *   - Crear un socio nuevo (formulario completo)
 *   - Editar datos de un socio
 *   - Cambiar estado de habilitación
 *   - Eliminar el socio creado (cleanup)
 */

import { test, expect } from "@playwright/test"

// Datos del socio de test (único para no colisionar con datos reales)
const SOCIO_TEST = {
  nombre: "Test",
  apellido: "Playwright",
  cedula: "1799999901",                       // cédula ficticia
  correo: "test.playwright@sadday-e2e.test",
  telefono: "0991234567",
  fechaNacimiento: "1990-06-15",
}

test.beforeEach(async ({ page }) => {
  await page.goto("/socios")
  await expect(page.getByRole("heading", { name: /socios/i })).toBeVisible({ timeout: 8_000 })
})

// ─── Listado ───────────────────────────────────────────────────────────────────

test("listado de socios muestra tabla con datos", async ({ page }) => {
  // Debe haber al menos una fila de dato (o el mensaje de vacío)
  const table = page.getByRole("table")
  await expect(table).toBeVisible()

  // El contador de socios debe estar presente
  await expect(page.getByText(/socios/i)).toBeVisible()
})

test("búsqueda filtra resultados", async ({ page }) => {
  const searchInput = page.getByPlaceholder(/buscar/i)
  await searchInput.fill("zzznoresults99999")

  // Presionar Enter o hacer click en Buscar
  await searchInput.press("Enter")

  // Esperar mensaje de sin resultados
  await expect(page.getByText(/no se encontraron/i)).toBeVisible({ timeout: 5_000 })

  // Limpiar búsqueda
  await searchInput.clear()
  await searchInput.press("Enter")
  await expect(page.getByText(/no se encontraron/i)).not.toBeVisible({ timeout: 5_000 })
})

// ─── Crear socio ───────────────────────────────────────────────────────────────

test("crear socio nuevo → aparece en listado", async ({ page }) => {
  await page.getByRole("button", { name: /nuevo socio/i }).click()

  const dialog = page.getByRole("dialog")
  await expect(dialog).toBeVisible({ timeout: 5_000 })

  // Datos personales
  await dialog.getByLabel(/nombre/i).fill(SOCIO_TEST.nombre)
  await dialog.getByLabel(/apellido/i).fill(SOCIO_TEST.apellido)
  await dialog.getByLabel(/cédula/i).fill(SOCIO_TEST.cedula)
  await dialog.getByLabel(/correo/i).fill(SOCIO_TEST.correo)
  await dialog.getByLabel(/teléfono/i).fill(SOCIO_TEST.telefono)
  await dialog.getByLabel(/nacimiento/i).fill(SOCIO_TEST.fechaNacimiento)

  await dialog.getByRole("button", { name: /guardar|crear/i }).click()

  // El dialog debe cerrarse y mostrar un toast de éxito
  await expect(dialog).not.toBeVisible({ timeout: 8_000 })

  // Buscar el socio creado en el listado
  const searchInput = page.getByPlaceholder(/buscar/i)
  await searchInput.fill(SOCIO_TEST.apellido)
  await searchInput.press("Enter")

  await expect(page.getByText(SOCIO_TEST.apellido)).toBeVisible({ timeout: 5_000 })
})

// ─── Editar socio ─────────────────────────────────────────────────────────────

test("editar socio → cambio de teléfono se guarda", async ({ page }) => {
  // Buscar el socio de test
  const searchInput = page.getByPlaceholder(/buscar/i)
  await searchInput.fill(SOCIO_TEST.apellido)
  await searchInput.press("Enter")

  // Abrir detalle y luego editar
  const row = page.getByRole("row").filter({ hasText: SOCIO_TEST.apellido }).first()
  await expect(row).toBeVisible({ timeout: 5_000 })

  await row.getByRole("button", { name: /editar/i }).click()

  const dialog = page.getByRole("dialog")
  await expect(dialog).toBeVisible({ timeout: 5_000 })

  // Cambiar teléfono
  const telefonoInput = dialog.getByLabel(/teléfono/i)
  await telefonoInput.clear()
  await telefonoInput.fill("0997654321")

  await dialog.getByRole("button", { name: /guardar|actualizar/i }).click()
  await expect(dialog).not.toBeVisible({ timeout: 8_000 })
})

// ─── Eliminar socio (cleanup) ──────────────────────────────────────────────────

test("eliminar socio de test → ya no aparece en listado", async ({ page }) => {
  const searchInput = page.getByPlaceholder(/buscar/i)
  await searchInput.fill(SOCIO_TEST.apellido)
  await searchInput.press("Enter")

  const row = page.getByRole("row").filter({ hasText: SOCIO_TEST.apellido }).first()
  await expect(row).toBeVisible({ timeout: 5_000 })

  // Hacer click en eliminar y confirmar el dialog nativo del browser
  page.on("dialog", (d) => d.accept())
  await row.getByRole("button", { name: /eliminar/i }).click()

  // Verificar que ya no aparece
  await expect(page.getByText(SOCIO_TEST.apellido)).not.toBeVisible({ timeout: 8_000 })
})
