import { describe, it, expect } from "vitest"
import { readFileSync, readdirSync, statSync } from "fs"
import { join } from "path"

/**
 * Un botón de solo icono necesita `title` o `aria-label`.
 *
 * Sin eso no tiene nombre accesible: un lector de pantalla anuncia «botón» y
 * nada más, y quien lo use no sabe si borra o edita. Los iconos de lucide
 * llevan `aria-hidden` —lo pone la propia librería— así que el SVG no aporta
 * nada al nombre; tiene que venir del botón.
 *
 * Esto es un test de código fuente y no de render a propósito: los botones
 * están repartidos por pantallas que requerirían montar media aplicación para
 * comprobar una regla que se ve leyendo el JSX. Si algún día hay una prueba de
 * accesibilidad de verdad sobre el DOM, esta sobra.
 */
function tsx(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n)
    if (statSync(p).isDirectory()) return tsx(p)
    return n.endsWith(".tsx") ? [p] : []
  })
}

describe("botones de solo icono", () => {
  it("todos tienen title o aria-label", () => {
    const sinNombre: string[] = []
    for (const fichero of tsx("src")) {
      const lineas = readFileSync(fichero, "utf-8").split("\n")
      lineas.forEach((linea, i) => {
        if (!linea.includes('size="icon"')) return
        // La ventana cubre las props del botón, que pueden ocupar varias líneas.
        const ventana = lineas.slice(Math.max(0, i - 3), i + 8).join("\n")
        if (!ventana.includes("title=") && !ventana.includes("aria-label")) {
          sinNombre.push(`${fichero}:${i + 1}`)
        }
      })
    }
    expect(sinNombre, `botones sin nombre accesible:\n${sinNombre.join("\n")}`)
      .toEqual([])
  })

  it("el test encuentra botones de solo icono, no una lista vacía", () => {
    // Sin esto, renombrar la prop `size="icon"` dejaría el test en verde sin
    // comprobar nada, que es el modo de fallo que más cuesta detectar.
    const total = tsx("src")
      .flatMap((f) => readFileSync(f, "utf-8").split("\n"))
      .filter((l) => l.includes('size="icon"')).length
    expect(total).toBeGreaterThan(20)
  })
})
