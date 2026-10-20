import { describe, it, expect } from "vitest"
import { readFileSync, readdirSync, existsSync } from "fs"
import { join } from "path"
import { analizarGpx } from "./analizar-gpx"

/**
 * Suite de conformidad compartida con el móvil.
 *
 * Los ficheros y los valores esperados viven en `test-fixtures/gpx/`, fuera de
 * este paquete, y los lee también `mobile/test/unit/analizar_gpx_test.dart`.
 * La fórmula de FR-023 está implementada dos veces —TypeScript y Dart— y el
 * riesgo es que se desincronicen sin que nadie lo note: la web y el móvil
 * darían desniveles distintos para el mismo fichero.
 *
 * Si este test falla tras tocar un umbral, es que se cambió en un solo lado.
 */
const DIR = join(process.cwd(), "..", "test-fixtures", "gpx")

describe("conformidad del análisis de GPX", () => {
  it("los ficheros de conformidad están donde se espera", () => {
    // Sin esto, un `for` sobre una lista vacía pasaría en verde sin comprobar
    // nada, que es el modo de fallo que más cuesta detectar.
    expect(existsSync(DIR), `falta ${DIR}`).toBe(true)
    expect(existsSync(join(DIR, "esperado.json"))).toBe(true)
  })

  const esperado = JSON.parse(readFileSync(join(DIR, "esperado.json"), "utf-8"))

  it("hay un fichero .gpx por cada entrada de esperado.json", () => {
    const ficheros = readdirSync(DIR)
      .filter((n) => n.endsWith(".gpx"))
      .map((n) => n.replace(/\.gpx$/, ""))
      .sort()
    expect(ficheros).toEqual(Object.keys(esperado).sort())
  })

  for (const nombre of Object.keys(esperado)) {
    it(`conformidad: ${nombre}`, () => {
      const r = analizarGpx(readFileSync(join(DIR, `${nombre}.gpx`), "utf-8"))
      expect(r, "el analizador devolvió null").not.toBeNull()
      const e = esperado[nombre]
      expect(r!.distanciaKm).toBeCloseTo(e.distanciaKm, 1)
      expect(r!.desnivelPositivoM).toBe(e.desnivelPositivoM)
      expect(r!.desnivelNegativoM).toBe(e.desnivelNegativoM)
      expect(r!.duracionHoras).toBe(e.duracionHoras)
      expect(r!.duracionDias).toBe(e.duracionDias)
      expect(r!.altitudMinimaM).toBe(e.altitudMinimaM)
      expect(r!.altitudMaximaM).toBe(e.altitudMaximaM)
      expect(r!.puntos).toBe(e.puntos)
    })
  }
})
