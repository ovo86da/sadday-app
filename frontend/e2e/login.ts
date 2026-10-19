import { expect, type Page } from "@playwright/test"

/**
 * Entra por la interfaz y espera a estar dentro.
 *
 * Hace falta porque el `storageState` del proyecto `setup` **no basta**: el
 * access token vive solo en memoria —decisión deliberada y correcta— y lo
 * único que persiste es la cookie de refresh. Al cargar en frío,
 * `route-guards.tsx` ve que no hay token y redirige a /login sin intentar
 * renovar, así que la sesión guardada no sirve para arrancar un test.
 *
 * Si algún día la aplicación intenta renovar al arrancar, esto sobra y los
 * tests vuelven a tirar de `storageState`, que es bastante más rápido.
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
