import { expect, type Page } from "@playwright/test"

/**
 * Entra por la interfaz y espera a estar dentro.
 *
 * Hace falta porque el `storageState` del proyecto `setup` sirve **una sola
 * vez**. La aplicación sí renueva al arrancar en frío —medido: el primer uso
 * del fichero da 200 en /auth/refresh— pero el refresh token **rota y es de un
 * solo uso**, así que la cookie guardada queda muerta en cuanto alguien la
 * gasta. El segundo test, o el segundo fichero que comparta ese storageState,
 * recibe 401 y acaba en /login. El síntoma es un "element(s) not found" del
 * encabezado, que no se parece en nada a la causa.
 *
 * Por eso cada fichero abre su propia sesión una vez, en `beforeAll`, y la
 * comparte entre sus tests: así la cookie rotada vive en ese navegador, que es
 * lo que le pasa a un usuario de verdad.
 */
export async function entrar(page: Page, usuario: string, clave: string) {
  await page.goto("/login")
  await page.getByLabel(/usuario/i).fill(usuario)
  await page.getByLabel(/contraseña/i).fill(clave)
  await page.getByRole("button", { name: /iniciar sesión/i }).click()
  await expect(page).not.toHaveURL(/\/login/, { timeout: 15_000 })
}

/** Entra como el socio de pruebas, leyendo las credenciales de .env.e2e. */
export async function entrarComoSocio(page: Page) {
  const u = process.env.E2E_SOCIO_USERNAME
  const p = process.env.E2E_SOCIO_PASSWORD
  if (!u || !p) throw new Error("Faltan E2E_SOCIO_USERNAME / E2E_SOCIO_PASSWORD en .env.e2e")
  await entrar(page, u, p)
}

/** Entra como el admin de pruebas, leyendo las credenciales de .env.e2e. */
export async function entrarComoAdmin(page: Page) {
  const u = process.env.E2E_ADMIN_USERNAME
  const p = process.env.E2E_ADMIN_PASSWORD
  if (!u || !p) throw new Error("Faltan E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD en .env.e2e")
  await entrar(page, u, p)
}
