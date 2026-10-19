/**
 * rutas.spec.ts — Documentos y tracks de una ruta (FR-022)
 *
 * Ejecuta con storageState de socio: la gracia de FR-022 es que subir dejó de
 * ser cosa de la directiva, así que probarlo como admin no demostraría nada.
 *
 * Cubre lo que de verdad se rompió durante el desarrollo, que es el criterio
 * para elegir qué automatizar:
 *   - Descargar un documento. Estuvo roto desde el commit inicial: la descarga
 *     usaba un <a href> y una navegación no lleva la cabecera Authorization,
 *     así que devolvía 401. Nadie lo notó hasta que hubo tracks que descargar.
 *   - Que la sección aparezca en rutas que NO exigen permisos. Estaba
 *     condicionada a `requierePermisos`, de modo que un track no se veía en la
 *     mayoría de rutas.
 *   - Que los botones del diálogo de reemplazo reciban clics. Radix desactiva
 *     el puntero fuera de su portal y el diálogo se veía pero estaba muerto.
 *   - Que un socio no pueda borrar lo que subió otro.
 */

import { test, expect, type APIRequestContext, type Page } from "@playwright/test"
import { e2eDir } from "./dirname"
import { entrarComoSocio } from "./login"
import dotenv from "dotenv"
import path from "path"
import { readFileSync } from "fs"

dotenv.config({ path: path.resolve(e2eDir, "../.env.e2e") })

const GPX = path.join(e2eDir, "fixtures", "ruta-prueba.gpx")
const PDF = path.join(e2eDir, "fixtures", "mapa-prueba.pdf")

/**
 * Un unico login para todo el fichero.
 *
 * No es una optimizacion: el backend limita el login a 3 por ventana y
 * devuelve 429 a partir del cuarto. Con un `entrarComoSocio` por test, del
 * cuarto en adelante el formulario no pasaba y el fallo aparecia como
 * "la pagina se queda en /login" —que parece credenciales equivocadas y no lo
 * es—. Por eso la sesion se abre una vez en `beforeAll` y los tests comparten
 * pagina, en serie. Tampoco vale el `storageState` del proyecto `setup`: ver
 * el porque en login.ts.
 */
test.describe.configure({ mode: "serial" })

let page: Page

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage()
  await entrarComoSocio(page)
})

test.afterAll(async () => {
  await page.close()
})

/** Abre el detalle de la primera ruta del listado. */
async function abrirPrimeraRuta(page: Page) {
  await page.goto("/rutas")
  await expect(page.getByRole("heading", { name: /rutas/i }).first())
    .toBeVisible({ timeout: 8_000 })

  // El listado es una tabla y cada fila abre el detalle al pulsarla.
  // La primera fila del role=row es la cabecera, de ahi el nth(1).
  const fila = page.getByRole("row").nth(1)
  await expect(fila).toBeVisible({ timeout: 10_000 })

  // El id sale de la peticion que dispara el propio dialogo al abrirse, no de
  // la API por separado: asi es siempre la ruta que el test tiene delante,
  // pase lo que pase con el orden del listado. Es el detalle de la ruta y no
  // /documentos: los documentos vienen dentro de `useRutaDetail`.
  const [peticion] = await Promise.all([
    page.waitForRequest((r) => /\/rutas\/\d+(\?|$)/.test(r.url()), { timeout: 10_000 }),
    fila.click(),
  ])
  await expect(page.getByText(/documentos y tracks/i)).toBeVisible({ timeout: 8_000 })

  return Number(/\/rutas\/(\d+)(?:\?|$)/.exec(peticion.url())![1])
}

/** Sube un documento como admin, por API: sirve para tener uno "de otra persona". */
async function subirComoAdmin(
  request: APIRequestContext, rutaId: number, nombre: string,
): Promise<void> {
  const u = process.env.E2E_ADMIN_USERNAME
  const c = process.env.E2E_ADMIN_PASSWORD
  if (!u || !c) throw new Error("Faltan E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD en .env.e2e")

  const login = await request.post("/api/v1/auth/login", { data: { username: u, password: c } })
  expect(login.ok(), "login de admin por API").toBeTruthy()
  const token = (await login.json()).data.accessToken

  const alta = await request.post(`/api/v1/rutas/${rutaId}/documentos`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      tipo: "MAPA",
      file: { name: nombre, mimeType: "application/pdf", buffer: readFileSync(PDF) },
    },
  })
  // 409 = el admin ya tiene 3 mapas en esta ruta de una tanda anterior. Vale
  // igual: lo que el test necesita es que exista uno suyo, no que sea nuevo.
  expect([201, 409], `alta del documento (${alta.status()})`).toContain(alta.status())
}

/**
 * Elige tipo y adjunta un fichero por el input oculto.
 *
 * Si ya hay uno propio del mismo tipo aparece el dialogo de reemplazo, porque
 * los tests comparten la misma ruta y el mismo socio: `fullyParallel` esta
 * desactivado pero el estado del backend persiste entre tests. Se resuelve
 * reemplazando, de modo que cada test parte de un unico documento conocido.
 */
async function subir(page: Page, tipo: string, fichero: string) {
  await page.getByLabel(/tipo de documento a subir/i).selectOption(tipo)

  const alta = page.waitForResponse(
    (r) => /\/documentos(\?|$)/.test(r.url()) && r.request().method() === "POST",
    { timeout: 20_000 })
  // Se arma antes del POST a proposito: la invalidacion de la query dispara el
  // GET en cuanto el POST responde, y armarlo despues llega tarde a veces.
  const refresco = page.waitForResponse(
    (r) => /\/rutas\/\d+(\?|$)/.test(r.url()) && r.request().method() === "GET",
    { timeout: 20_000 })

  await page.locator("input[type='file']").first().setInputFiles(fichero)

  const reemplazar = page.getByRole("button", { name: /^reemplazar$/i })
  if (await reemplazar.isVisible({ timeout: 2_000 }).catch(() => false)) {
    await reemplazar.click()
  }

  // Hay que esperar a que la lista se recargue, no solo a que aparezca el
  // nombre del fichero: al reemplazar, el documento nuevo se llama igual que
  // el que acaba de borrarse, asi que la fila vieja es indistinguible de la
  // nueva por texto. Pulsar "descargar" en la vieja daba 404 contra un
  // documento que ya no existe, y el test culpaba a la descarga.
  const respuesta = await alta
  if (respuesta.ok()) await refresco
}

// ─── La sección se ve siempre ──────────────────────────────────────────────────

test("la seccion de documentos aparece aunque la ruta no exija permisos", async () => {
  await abrirPrimeraRuta(page)
  // Antes esto dependia de `requierePermisos` y no se veia en la mayoria de rutas.
  await expect(page.getByText(/documentos y tracks/i)).toBeVisible()
  await expect(page.getByRole("button", { name: /subir/i })).toBeVisible()
})

// ─── Subida ────────────────────────────────────────────────────────────────────

test("un socio puede subir un track y aparece clasificado como tal", async () => {
  await abrirPrimeraRuta(page)
  await subir(page, "TRACK_GPX", GPX)

  // Acotado a la fila del listado: el toast de exito dice el mismo nombre de
  // fichero, asi que un getByText suelto encuentra dos y falla en modo
  // estricto en cuanto la subida va rapida.
  await expect(page.locator("li").filter({ hasText: "ruta-prueba.gpx" }).first())
    .toBeVisible({ timeout: 15_000 })
  // Agrupado bajo su epigrafe, no mezclado con el resto
  await expect(page.getByText(/tracks gpx/i).first()).toBeVisible()
})

// ─── Pendientes de afinar ──────────────────────────────────────────────────────
//
// Los tres de abajo quedan marcados con `fixme`: la funcionalidad esta
// verificada a mano y por API, pero el selector todavia no acierta y dejarlos
// en rojo convertiria la suite en ruido que se acaba ignorando. Se activan
// quitando el `.fixme`.

test("un PDF marcado como track se rechaza, y dice por que", async () => {
  await abrirPrimeraRuta(page)
  await subir(page, "TRACK_GPX", PDF)

  await expect(page.getByText(/debe ser un fichero \.gpx/i)).toBeVisible({ timeout: 10_000 })
})

// ─── Descarga: el fallo que estuvo oculto desde el commit inicial ──────────────

test("descargar un documento entrega el fichero, no un 401", async () => {
  await abrirPrimeraRuta(page)
  await subir(page, "TRACK_GPX", GPX)
  // Acotado a la fila del listado: el toast de exito dice el mismo nombre de
  // fichero, asi que un getByText suelto encuentra dos y falla en modo
  // estricto en cuanto la subida va rapida.
  await expect(page.locator("li").filter({ hasText: "ruta-prueba.gpx" }).first())
    .toBeVisible({ timeout: 15_000 })

  // Hay que pulsar la descarga DE ESA fila: la lista va agrupada por tipo y
  // `.first()` cogia el primer documento de la ruta, que suele ser un PDF.
  const fila = page.locator("li").filter({ hasText: "ruta-prueba.gpx" }).first()
  const descarga = page.waitForEvent("download", { timeout: 15_000 })
  await fila.getByTitle(/descargar/i).click()
  const fichero = await descarga

  expect(fichero.suggestedFilename()).toMatch(/\.gpx$/)
})

// ─── El diálogo de reemplazo: los botones tienen que responder ─────────────────

test("al subir un segundo track el dialogo aparece y sus botones responden", async () => {
  await abrirPrimeraRuta(page)
  await subir(page, "TRACK_GPX", GPX)
  // Acotado a la fila del listado: el toast de exito dice el mismo nombre de
  // fichero, asi que un getByText suelto encuentra dos y falla en modo
  // estricto en cuanto la subida va rapida.
  await expect(page.locator("li").filter({ hasText: "ruta-prueba.gpx" }).first())
    .toBeVisible({ timeout: 15_000 })

  // Segundo intento, sin pasar por el helper: aqui lo que se prueba es
  // justamente que aparezca el dialogo y que responda.
  await page.getByLabel(/tipo de documento a subir/i).selectOption("TRACK_GPX")
  await page.locator("input[type='file']").first().setInputFiles(GPX)
  const dialogo = page.getByText(/ya tienes .* en esta ruta/i)
  await expect(dialogo).toBeVisible({ timeout: 10_000 })

  // El limite del track es 1, asi que no hay opcion de añadir
  await expect(page.getByRole("button", { name: /anadir nuevo/i })).toHaveCount(0)

  // Lo que fallaba: el boton se veia pero no recibia el clic
  await page.getByRole("button", { name: /^cancelar$/i }).click()
  await expect(dialogo).toBeHidden({ timeout: 5_000 })
})

// ─── Permisos ──────────────────────────────────────────────────────────────────

test("un socio no ve el boton de borrar en documentos de otra persona", async ({ request }) => {
  // Este test se saltaba SIEMPRE y nadie lo noto. Filtraba las filas con
  // /\.(gpx|pdf)$/ y el <li> no termina en el nombre del fichero —sigue con
  // el autor, la fecha y los botones—, asi que la cuenta era 0 y `test.skip`
  // lo daba por bueno. Un test que nunca corre es peor que no tenerlo.
  //
  // Y arreglar solo el filtro no bastaba: sobre una base limpia todos los
  // documentos son del propio socio y el bucle no comprobaria ni un caso. El
  // documento ajeno se crea aqui, por API y como admin.
  const rutaId = await abrirPrimeraRuta(page)
  const ajeno = "mapa-de-otra-persona.pdf"
  await subirComoAdmin(request, rutaId, ajeno)

  // Sin `page.reload()`: `abrirPrimeraRuta` ya hace `goto("/rutas")`, que es una
  // navegacion completa y vuelve a pedir los datos. El reload sobraba y encima
  // anadia una carga en frio mas, con su renovacion de token; en CI se quedaba
  // sin sesion y el fallo salia como "no encuentro el encabezado /rutas/".
  const abierta = await abrirPrimeraRuta(page)
  expect(abierta, "tiene que volver a la misma ruta").toBe(rutaId)

  const filaAjena = page.locator("li").filter({ hasText: ajeno }).first()
  await expect(filaAjena).toBeVisible({ timeout: 10_000 })
  await expect(filaAjena.getByTitle(/eliminar/i)).toHaveCount(0)

  // Control positivo: sin esto el test pasaria igual si la lista no dibujara
  // ningun boton de borrar, que es un fallo distinto y tambien grave.
  const filaPropia = page.locator("li").filter({ hasText: "ruta-prueba.gpx" }).first()
  await expect(filaPropia).toBeVisible()
  await expect(filaPropia.getByTitle(/eliminar/i)).toHaveCount(1)
})

// ─── FR-023: los datos salen del GPX ──────────────────────────────────────────

test("al adjuntar un GPX en proponer ruta, los datos se rellenan solos", async () => {
  await page.goto("/rutas")
  await page.getByRole("button", { name: /proponer ruta/i }).click()
  await expect(page.getByLabel("Longitud (km)")).toBeVisible({ timeout: 8_000 })

  // El fixture es sintetico y deterministico: 3 puntos, 4000 → 4050 → 4120 m,
  // sin marcas de tiempo. Da 0,3 km y 120 m de subida con el umbral de 40 m.
  await page.getByLabel(/tipo de archivo a adjuntar/i).selectOption("TRACK_GPX")
  await page.locator("input[type='file']").first().setInputFiles(GPX)

  await expect(page.getByLabel("Longitud (km)")).toHaveValue("0.3", { timeout: 10_000 })
  await expect(page.getByLabel("Desnivel + (m)")).toHaveValue("120")
  // Sube y no baja: el cero es un dato, no una ausencia (FR-024).
  await expect(page.getByLabel("Desnivel − (m)")).toHaveValue("0")

  // Sin tiempos no hay duracion, y se dice por que para que no parezca un fallo.
  //
  // No se comprueba el campo de duracion porque el formulario dibuja uno solo
  // segun la actividad —dias en alpinismo, horas en el resto—, asi que pedirlo
  // por su etiqueta depende del tipo elegido. Lo que importa aqui es el aviso.
  await expect(page.getByText(/no trae marcas de tiempo/i)).toBeVisible()
})

test("un valor ya escrito no se pisa: se avisa y se ofrece", async () => {
  await page.goto("/rutas")
  await page.getByRole("button", { name: /proponer ruta/i }).click()

  // Lo que mas rapido hace que alguien deje de confiar en un formulario es que
  // le borre lo que acaba de teclear.
  await page.getByLabel("Longitud (km)").fill("99")

  await page.getByLabel(/tipo de archivo a adjuntar/i).selectOption("TRACK_GPX")
  await page.locator("input[type='file']").first().setInputFiles(GPX)

  await expect(page.getByText(/ya los habías escrito/i)).toBeVisible({ timeout: 10_000 })
  await expect(page.getByLabel("Longitud (km)")).toHaveValue("99")

  // Y el desnivel, que estaba vacio, si se ha rellenado.
  await expect(page.getByLabel("Desnivel + (m)")).toHaveValue("120")

  await page.getByRole("button", { name: /usar este/i }).click()
  await expect(page.getByLabel("Longitud (km)")).toHaveValue("0.3")
})
