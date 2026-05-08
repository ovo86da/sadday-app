/**
 * auth.setup.ts
 *
 * Proyecto "setup" de Playwright: hace login como Admin y como Socio,
 * guarda las cookies en e2e/.auth/*.json para que los demás tests las reutilicen.
 *
 * Cuando un test carga con esas cookies, la app detecta la cookie de refresh y
 * llama automáticamente a POST /auth/refresh → obtiene un nuevo access token.
 */

import { test as setup, expect } from "@playwright/test"
import path from "path"
import dotenv from "dotenv"

dotenv.config({ path: path.resolve(__dirname, "../.env.e2e") })

const ADMIN_FILE = path.resolve(__dirname, ".auth/admin.json")
const SOCIO_FILE = path.resolve(__dirname, ".auth/socio.json")

setup("Login como Admin y guardar sesión", async ({ page }) => {
  const username = process.env.E2E_ADMIN_USERNAME
  const password = process.env.E2E_ADMIN_PASSWORD

  if (!username || !password) {
    throw new Error("Faltan E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD en .env.e2e")
  }

  await page.goto("/login")
  await page.getByLabel("Usuario").fill(username)
  await page.getByLabel("Contraseña").fill(password)
  await page.getByRole("button", { name: "Iniciar sesión" }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })

  await page.context().storageState({ path: ADMIN_FILE })
})

setup("Login como Socio y guardar sesión", async ({ page }) => {
  const username = process.env.E2E_SOCIO_USERNAME
  const password = process.env.E2E_SOCIO_PASSWORD

  if (!username || !password) {
    throw new Error("Faltan E2E_SOCIO_USERNAME / E2E_SOCIO_PASSWORD en .env.e2e")
  }

  await page.goto("/login")
  await page.getByLabel("Usuario").fill(username)
  await page.getByLabel("Contraseña").fill(password)
  await page.getByRole("button", { name: "Iniciar sesión" }).click()
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 10_000 })

  await page.context().storageState({ path: SOCIO_FILE })
})
