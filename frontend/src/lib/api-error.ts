import axios from "axios"

export function getApiErrorMessage(err: unknown, fallback = "Ha ocurrido un error"): string {
  if (axios.isAxiosError(err)) {
    const msg: string | undefined = err.response?.data?.message
    if (msg) return msg.length > 200 ? msg.slice(0, 200) + "…" : msg
  }
  return fallback
}
