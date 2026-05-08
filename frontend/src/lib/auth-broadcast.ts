import type { User } from "@/stores/auth-store"

/**
 * Coordinación de refresco de tokens entre pestañas del mismo origen.
 *
 * Problema: si el usuario tiene dos tabs abiertas y ambas intentan rotar
 * el refresh token simultáneamente, la segunda pestaña llega con un token
 * ya revocado y dispara la detección de robo en el backend (revocar TODAS
 * las sesiones → usuario deslogueado).
 *
 * Solución: lock en localStorage + BroadcastChannel.
 * - La tab que adquiere el lock hace el refresh y emite el resultado.
 * - Las demás tabs esperan el resultado y actualizan su estado local
 *   sin tocar el backend.
 * - La cookie HttpOnly (refresh token) la actualiza el browser
 *   automáticamente para todas las tabs al recibir el Set-Cookie.
 */

const CHANNEL_NAME = "sadday-auth"
const LOCK_KEY     = "sadday_refresh_lock"
const LOCK_TTL_MS  = 10_000   // 10 s — margen amplio para la llamada al backend
const WAIT_TTL_MS  = 12_000   // 12 s — tiempo máximo esperando a otra tab

type RefreshDoneMsg   = { type: "REFRESH_DONE";   accessToken: string; user: User }
type RefreshFailedMsg = { type: "REFRESH_FAILED" }
type AuthMsg = RefreshDoneMsg | RefreshFailedMsg

const channel: BroadcastChannel | null =
  typeof BroadcastChannel !== "undefined" ? new BroadcastChannel(CHANNEL_NAME) : null

// ─── Lock cross-tab (localStorage) ───────────────────────────────────────────

/** Intenta adquirir el lock. Devuelve true si lo obtuvo, false si otra tab lo tiene. */
export function acquireRefreshLock(): boolean {
  const raw = localStorage.getItem(LOCK_KEY)
  if (raw !== null && Date.now() - parseInt(raw, 10) < LOCK_TTL_MS) {
    return false // otra tab tiene el lock y no ha expirado
  }
  localStorage.setItem(LOCK_KEY, Date.now().toString())
  return true
}

export function releaseRefreshLock(): void {
  localStorage.removeItem(LOCK_KEY)
}

// ─── Broadcast ────────────────────────────────────────────────────────────────

export function broadcastRefreshDone(accessToken: string, user: User): void {
  channel?.postMessage({ type: "REFRESH_DONE", accessToken, user } satisfies AuthMsg)
}

export function broadcastRefreshFailed(): void {
  channel?.postMessage({ type: "REFRESH_FAILED" } satisfies AuthMsg)
}

/**
 * Espera a que otra tab emita el resultado del refresh.
 * Devuelve el mensaje si llega antes del timeout, null si no.
 *
 * El listener se registra de forma síncrona (dentro del constructor de
 * Promise) para evitar que se pierda el mensaje si la otra tab termina
 * muy rápido.
 */
export function waitForRefreshResult(): Promise<RefreshDoneMsg | null> {
  return new Promise((resolve) => {
    if (!channel) {
      resolve(null)
      return
    }

    const timer = setTimeout(() => {
      channel.removeEventListener("message", handler)
      resolve(null)
    }, WAIT_TTL_MS)

    const handler = (e: MessageEvent<AuthMsg>) => {
      if (e.data.type === "REFRESH_DONE") {
        clearTimeout(timer)
        channel.removeEventListener("message", handler)
        resolve(e.data)
      } else if (e.data.type === "REFRESH_FAILED") {
        clearTimeout(timer)
        channel.removeEventListener("message", handler)
        resolve(null)
      }
    }

    channel.addEventListener("message", handler)
  })
}
