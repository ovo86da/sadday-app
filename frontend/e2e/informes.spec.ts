/**
 * informes.spec.ts — Descarga de PDF de informes (rol Admin)
 *
 * Cubre:
 *   - Ver listado de informes
 *   - Abrir detalle de un informe
 *   - Descargar PDF (verifica que el browser inicia una descarga)
 *   - Si no hay PDF, verificar que aparece el botón "Generar PDF"
 */

import { test, expect, type Page } from "@playwright/test"
import { entrarComoAdmin } from "./login"

/**
 * Un unico contexto de navegador para todo el fichero.
 *
 * El refresh token rota y es de un solo uso, asi que el `storageState` que deja
 * el proyecto `setup` sirve exactamente una vez: quien lo gasta primero deja la
 * cookie muerta para el resto. Daba igual que fuera otro test del mismo fichero
 * u otro fichero con el mismo storageState —socios e informes comparten el de
 * admin—: el segundo recibia 401 en /auth/refresh y acababa en /login, y el
 * sintoma era un "element(s) not found" del encabezado que no se parece en nada
 * a la causa.
 *
 * Por eso cada fichero abre su propia sesion una vez y la comparte entre sus
 * tests: la cookie rotada vive en ese navegador, como le pasa a un usuario.
 */
test.describe.configure({ mode: "serial" })

let page: Page

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage()
  await entrarComoAdmin(page)
})

test.afterAll(async () => {
  await page.close()
})

test.beforeEach(async () => {

  await page.goto("/informes")
  await expect(page.getByRole("heading", { level: 1, name: /informes/i })).toBeVisible({ timeout: 8_000 })
})

// ─── Listado ───────────────────────────────────────────────────────────────────

test("listado de informes carga correctamente", async () => {
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

test("abrir detalle de informe → muestra información y opciones de PDF", async () => {
  // Si no hay informes, saltamos el test
  // Contar `row` sin mas no vale: la tabla dibuja filas de esqueleto mientras
  // carga y una fila de "No hay salidas realizadas" cuando no hay nada. Las
  // dos pasaban el `count === 0` y el test acababa pulsando una fila que no
  // abre ningun dialogo, agotando los 30 s.
  if (await page.getByText(/no hay salidas realizadas/i).isVisible().catch(() => false)) {
    test.skip(true, "No hay informes en el entorno de test")
    return
  }
  const rows = page.getByRole("row")
    .filter({ hasNot: page.getByRole("columnheader") })
    .filter({ hasNot: page.locator(".animate-pulse") })
  await expect(rows.first()).toBeVisible({ timeout: 10_000 })

  // La fila entera es el disparador: `onClick` esta en el <TableRow> y el icono
  // del ojo es decorativo, no un boton. Buscar un boton /ver|detalle/ no
  // encontraba nada y el test agotaba los 30 s en el click.
  await rows.first().click()

  const dialog = page.getByRole("dialog")
  await expect(dialog).toBeVisible({ timeout: 5_000 })

  // El dialog debe mostrar información del informe
  await expect(dialog.getByRole("heading")).toBeVisible()

  // Debe haber al menos uno de: "Descargar PDF", "Generar PDF"
  const pdfOptions = dialog.getByRole("button", { name: /(descargar|generar) pdf/i })
  await expect(pdfOptions.first()).toBeVisible({ timeout: 5_000 })
})

test("descargar PDF dispara una descarga en el browser", async () => {
  // Contar `row` sin mas no vale: la tabla dibuja filas de esqueleto mientras
  // carga y una fila de "No hay salidas realizadas" cuando no hay nada. Las
  // dos pasaban el `count === 0` y el test acababa pulsando una fila que no
  // abre ningun dialogo, agotando los 30 s.
  if (await page.getByText(/no hay salidas realizadas/i).isVisible().catch(() => false)) {
    test.skip(true, "No hay informes en el entorno de test")
    return
  }
  const rows = page.getByRole("row")
    .filter({ hasNot: page.getByRole("columnheader") })
    .filter({ hasNot: page.locator(".animate-pulse") })
  await expect(rows.first()).toBeVisible({ timeout: 10_000 })

  await rows.first().click()   // la fila entera es el disparador

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
