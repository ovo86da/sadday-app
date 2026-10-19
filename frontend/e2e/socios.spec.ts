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

import { test, expect, type Page } from "@playwright/test"
import { entrarComoAdmin } from "./login"

// Datos del socio de test (único para no colisionar con datos reales)
const SOCIO_TEST = {
  nombre: "Test",
  apellido: "Playwright",
  cedula: "1799999901",                       // cédula ficticia
  correo: "test.playwright@sadday-e2e.test",
  telefono: "0991234567",
  fechaNacimiento: "1990-06-15",
}

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

  await page.goto("/socios")
  await expect(page.getByRole("heading", { level: 1, name: /socios/i })).toBeVisible({ timeout: 8_000 })
})

// ─── Listado ───────────────────────────────────────────────────────────────────

test("listado de socios muestra tabla con datos", async () => {
  const table = page.getByRole("table")
  await expect(table).toBeVisible()

  // Antes esto era `getByText(/socios/i)` buscando "el contador": casaba con
  // seis elementos —el enlace del menu, la miga de pan, el <h1>, el boton de
  // importar, la pestana y el <h2> de la tarjeta de busqueda— y fallaba en modo
  // estricto. Tampoco afirmaba nada: la palabra "socios" sale en la pagina
  // pase lo que pase. Lo que importa es que el listado traiga filas, que es
  // lo que el titulo del test dice.
  const filas = table.getByRole("row").filter({ hasNot: page.getByRole("columnheader") })
  expect(await filas.count()).toBeGreaterThan(0)
})

test("búsqueda filtra resultados", async () => {
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

// Los tres de abajo no tienen un selector mal escrito: prueban un alta directa
// de socio que la aplicacion no tiene. El boton real es "Invitar socio" y abre
// "Invitar nuevo socio", de modo que el socio NO aparece en el listado al
// guardar —queda como invitacion hasta que la persona se registra—. Y los de
// editar y eliminar dependian de ese socio inexistente.
//
// Van con `fixme` y no con un skip condicional: `fixme` sale en el informe y se
// ve que faltan. Reescribirlos es cubrir el flujo de invitacion de verdad
// (invitar -> pestana Invitaciones -> correo en mailpit), que es otro trabajo.
test.fixme("crear socio nuevo → aparece en listado", async () => {
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

test.fixme("editar socio → cambio de teléfono se guarda", async () => {
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

test.fixme("eliminar socio de test → ya no aparece en listado", async () => {
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
