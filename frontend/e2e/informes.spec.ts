/**
 * informes.spec.ts — Descarga de PDF de informes (rol Admin)
 *
 * Cubre:
 *   - Ver listado de informes
 *   - Abrir detalle de un informe
 *   - Descargar PDF (verifica que el browser inicia una descarga)
 *   - Si no hay PDF, verificar que aparece el botón "Generar PDF"
 */

import { test, expect } from "@playwright/test"

test.beforeEach(async ({ page }) => {
  await page.goto("/informes")
  await expect(page.getByRole("heading", { name: /informes/i })).toBeVisible({ timeout: 8_000 })
})

// ─── Listado ───────────────────────────────────────────────────────────────────

test("listado de informes carga correctamente", async ({ page }) => {
  const table = page.getByRole("table")
  await expect(table).toBeVisible({ timeout: 5_000 })

  // Puede estar vacío o con datos — simplemente no debe haber error
  const emptyMsg = page.getByText(/no se encontraron informes/i)
  const hasRows = await page.getByRole("row").count()

  // Al menos la fila de header
  expect(hasRows).toBeGreaterThanOrEqual(1)
  // Si está vacío, el mensaje de vacío debe estar visible
  if (hasRows === 1) {
    await expect(emptyMsg).toBeVisible()
  }
})

// ─── Detalle y PDF ─────────────────────────────────────────────────────────────

test("abrir detalle de informe → muestra información y opciones de PDF", async ({ page }) => {
  // Si no hay informes, saltamos el test
  const rows = page.getByRole("row").filter({ hasNot: page.getByRole("columnheader") })
  const count = await rows.count()

  if (count === 0) {
    test.skip(true, "No hay informes en el entorno de test")
    return
  }

  // Click en el primer informe (ícono de ojo o fila)
  await rows.first().getByRole("button", { name: /ver|detalle/i }).click()

  const dialog = page.getByRole("dialog")
  await expect(dialog).toBeVisible({ timeout: 5_000 })

  // El dialog debe mostrar información del informe
  await expect(dialog.getByRole("heading")).toBeVisible()

  // Debe haber al menos uno de: "Descargar PDF", "Generar PDF"
  const pdfOptions = dialog.getByRole("button", { name: /(descargar|generar) pdf/i })
  await expect(pdfOptions.first()).toBeVisible({ timeout: 5_000 })
})

test("descargar PDF dispara una descarga en el browser", async ({ page }) => {
  const rows = page.getByRole("row").filter({ hasNot: page.getByRole("columnheader") })
  const count = await rows.count()

  if (count === 0) {
    test.skip(true, "No hay informes en el entorno de test")
    return
  }

  await rows.first().getByRole("button", { name: /ver|detalle/i }).click()

  const dialog = page.getByRole("dialog")
  await expect(dialog).toBeVisible({ timeout: 5_000 })

  // Buscar botón de descarga (solo existe si hay PDF generado)
  const downloadBtn = dialog.getByRole("button", { name: /descargar pdf/i })
  const hasDownload = await downloadBtn.count()

  if (hasDownload === 0) {
    test.skip(true, "El informe no tiene PDF generado todavía")
    return
  }

  // Capturar el evento de descarga
  const [download] = await Promise.all([
    page.waitForEvent("download", { timeout: 15_000 }),
    downloadBtn.click(),
  ])

  expect(download.suggestedFilename()).toMatch(/\.pdf$/i)
})
