import api from "@/lib/api"
import { toast } from "sonner"

/**
 * Descarga un documento de ruta a traves del cliente autenticado.
 *
 * No vale apuntar un `<a href>` al endpoint: una navegacion del navegador no
 * pasa por el interceptor de axios, asi que la peticion sale sin la cabecera
 * `Authorization` y el backend responde 401. La unica cookie que viaja es la
 * del refresh token, que no autoriza la peticion.
 *
 * Vive aqui y no en cada pantalla porque el mismo documento se descarga desde
 * el detalle de la ruta y desde el de la salida, y el error ya se habia
 * repetido en los dos sitios.
 */
export async function descargarDocumentoRuta(
  // Acepta null porque `salida.rutaId` lo es: una salida puede no tener ruta.
  // Protegerse aqui evita repetir la comprobacion en cada pantalla, y evita
  // el `/rutas/null/...` que generaba el enlace anterior sin avisar.
  rutaId: number | null,
  docId: string,
  filename: string,
  contentType?: string,
): Promise<void> {
  if (rutaId === null) return
  try {
    const response = await api.get(`/v1/rutas/${rutaId}/documentos/${docId}/descargar`,
                                   { responseType: "blob" })
    const objectUrl = URL.createObjectURL(
      new Blob([response.data], contentType ? { type: contentType } : undefined))
    const a = document.createElement("a")
    a.href = objectUrl
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(objectUrl)
  } catch (err: unknown) {
    const msg = (err as { response?: { data?: { message?: string } } }).response?.data?.message
    toast.error(msg ?? "No se pudo descargar el documento")
  }
}
