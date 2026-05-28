import type { User } from "@/stores/auth-store"

/**
 * Coordinación de refresco de tokens entre pestañas del mismo origen.
 *
 * Problema: si el usuario tiene dos tabs abiertas y ambas intentan rotar
 * el refresh token simultáneamente, la segunda pestaña llega con un token
 * ya revocado y dispara la detección de robo en el backend (revocar TODAS
 * las sesiones → usuario deslogueado).
 *
 * Solución: Web Locks API + BroadcastChannel.
 * - La tab que adquiere el lock (navigator.locks) hace el refresh y emite el resultado.
 * - Las demás tabs esperan el resultado via BroadcastChannel y actualizan su estado
 *   local sin tocar el backend.
 * - La cookie HttpOnly (refresh token) la actualiza el browser automáticamente
 *   para todas las tabs al recibir el Set-Cookie.
 *
 * Web Locks API garantiza atomicidad a nivel de navegador (a diferencia del patrón
 * anterior con localStorage getItem/setItem, que tenía una race condition check-then-act).
 * Fallback: si Web Locks no está disponible, se asume lock libre (un solo intento).
 */

const CHANNEL_NAME = "sadday-auth"
const LOCK_NAME    = "sadday-refresh"
const WAIT_TTL_MS  = 12_000   // 12 s — tiempo máximo esperando a otra tab

type RefreshDoneMsg   = { type: "REFRESH_DONE";   accessToken: string; user: User }
type RefreshFailedMsg = { type: "REFRESH_FAILED" }
type AuthMsg = RefreshDoneMsg | RefreshFailedMsg

const channel: BroadcastChannel | null =
  typeof BroadcastChannel !== "undefined" ? new BroadcastChannel(CHANNEL_NAME) : null

export const hasWebLocks = typeof navigator !== "undefined" && "locks" in navigator

// ─── Lock cross-tab (Web Locks API) ──────────────────────────────────────────

/**
 * Intenta adquirir el lock de forma atómica.
 * - Si lo obtiene: ejecuta `onLockAcquired` y libera el lock al terminar.
 * - Si ya está tomado: devuelve null sin bloquear (ifAvailable: true).
 *
 * Retorna el resultado de `onLockAcquired`, o null si otra tab tiene el lock.
 */
export async function withRefreshLock<T>(
  onLockAcquired: () => Promise<T>
): Promise<T | null> {
  if (!hasWebLocks) {
    // Fallback: sin Web Locks simplemente ejecutamos (single-tab o entorno sin soporte)
    return onLockAcquired()
  }
  return navigator.locks.request(
    LOCK_NAME,
    { ifAvailable: true },
    async (lock): Promise<T | null> => {
      if (!lock) return null   // otra tab tiene el lock
      return onLockAcquired()
    }
  )
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
