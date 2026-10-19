/**
 * Formatea el desnivel de una ruta (FR-024).
 *
 * Las dos cifras se guardan como magnitudes sin signo —1248 significa 1248 m de
 * bajada— y el signo lo pone esta funcion. Vive aparte porque la misma regla se
 * usa en el detalle de la ruta y en el listado, y porque el caso del cero es
 * facil de romper sin darse cuenta.
 *
 * `null` y `0` no significan lo mismo y no se muestran igual:
 *   - `null` es «no lo sabemos» → no se dibuja esa mitad.
 *   - `0` es «no baja nada» → se dibuja «↓ 0 m».
 * La travesia al campamento de Sincholagua sube 1038 m y baja 0 de verdad,
 * porque termina alli. Tratar ese 0 como ausente perderia el dato.
 */
export function formatearDesnivel(
  positivoM: number | null | undefined,
  negativoM: number | null | undefined,
): string {
  const partes: string[] = []
  if (positivoM != null) partes.push(`↑ ${positivoM} m`)
  if (negativoM != null) partes.push(`↓ ${negativoM} m`)
  return partes.length > 0 ? partes.join(" · ") : "—"
}
