#!/usr/bin/env node
/**
 * Informa de qué pasaría si se recalcularan los datos de las rutas desde sus
 * tracks, con los umbrales que haya configurados ahora.
 *
 *   cd frontend
 *   RECALC_CLAVE='…' node --experimental-strip-types \
 *     scripts/recalcular-desniveles.mjs --usuario admin
 *
 * **Solo informa. No modifica nada.** Y eso es deliberado, no una limitación
 * pendiente: aplicarlo pisaría los números que un socio corrigió a mano con el
 * dato de su app, que es justo lo que el formulario se cuida de no hacer. Antes
 * de permitir `--aplicar` hay que decidir qué pasa con esos valores, y para
 * decidirlo hace falta primero este informe.
 *
 * Reutiliza `src/lib/analizar-gpx.ts`, la misma implementación que usa la web.
 * No es una copia: si lo fuera, el informe podría decir que hay que recalcular
 * cuando el problema es que el script calcula distinto. `DOMParser` lo presta
 * jsdom, que ya es una dependencia de desarrollo.
 *
 * Para probar otro umbral: cambiar `shared/umbrales.json`, ejecutar
 * `node scripts/generar-umbrales.mjs` y volver a lanzar esto. No hay una opción
 * `--umbral` a propósito: habría dos sitios desde donde fijar el mismo número.
 */
import { JSDOM } from "jsdom"
import { readFileSync } from "node:fs"
import { join, dirname } from "node:path"
import { fileURLToPath } from "node:url"

globalThis.DOMParser = new JSDOM("").window.DOMParser

const raiz = join(dirname(fileURLToPath(import.meta.url)), "..", "..")
const { analizarGpx } = await import("../src/lib/analizar-gpx.ts")
const umbrales = JSON.parse(readFileSync(join(raiz, "shared", "umbrales.json"), "utf-8"))

const args = process.argv.slice(2)
const opcion = (nombre, pordefecto) => {
  const i = args.indexOf(`--${nombre}`)
  return i >= 0 ? args[i + 1] : pordefecto
}

const API = opcion("api", "http://localhost:8080/api/v1")
const USUARIO = opcion("usuario")
// La clave por variable de entorno y nunca por argumento: un argumento queda
// en el historial del shell y es visible en la lista de procesos.
const CLAVE = process.env.RECALC_CLAVE

if (!USUARIO || !CLAVE) {
  console.error("Uso: RECALC_CLAVE='…' node --experimental-strip-types \\")
  console.error("       scripts/recalcular-desniveles.mjs --usuario <usuario> [--api <url>]")
  process.exit(2)
}

const pedir = async (ruta, opts = {}) => {
  const res = await fetch(`${API}${ruta}`, opts)
  if (!res.ok) throw new Error(`${opts.method ?? "GET"} ${ruta} → ${res.status}`)
  return res
}

console.log(`umbrales actuales: movimiento ${umbrales.minimoMovimientoM.valor} m · ` +
            `desnivel ${umbrales.umbralDesnivelM.valor} m\n`)

const login = await pedir("/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: USUARIO, password: CLAVE }),
})
const token = (await login.json()).data.accessToken
const auth = { Authorization: `Bearer ${token}` }

// Recorre todas las páginas: con 70 rutas cabe en una, pero no conviene que el
// informe deje de ser completo en silencio el día que haya 300.
const rutas = []
for (let pagina = 0; ; pagina++) {
  const res = await pedir(`/rutas?page=${pagina}&size=100`, { headers: auth })
  const datos = (await res.json()).data
  rutas.push(...datos.content)
  if (datos.content.length === 0 || pagina + 1 >= (datos.page?.totalPages ?? 1)) break
}
console.log(`${rutas.length} rutas en total\n`)

const filas = []
let conTrack = 0

for (const resumen of rutas) {
  const detalle = (await (await pedir(`/rutas/${resumen.id}`, { headers: auth })).json()).data
  const tracks = (detalle.documentos ?? []).filter((d) => d.tipo === "TRACK_GPX")
  if (tracks.length === 0) continue
  conTrack++

  // Si hay varios tracks de distintas personas se usa el más reciente. Es una
  // decisión del informe, no de la aplicación, y conviene saberla al leerlo.
  const track = tracks.sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
  const bytes = await (await pedir(
    `/rutas/${resumen.id}/documentos/${track.id}/descargar`, { headers: auth })).arrayBuffer()

  const calc = analizarGpx(Buffer.from(bytes).toString("utf-8"))
  if (!calc) {
    filas.push({ id: resumen.id, nombre: resumen.nombre, nota: "el track no se pudo analizar" })
    continue
  }

  const comparar = (campo, calculado) => {
    const guardado = detalle[campo]
    if (guardado == null) return { estado: "vacío", guardado, calculado }
    if (guardado === calculado) return { estado: "coincide", guardado, calculado }
    return { estado: "DIFIERE", guardado, calculado }
  }

  filas.push({
    id: resumen.id,
    nombre: resumen.nombre,
    fichero: track.filename,
    longitud: comparar("longitudKm", calc.distanciaKm),
    dmas: comparar("desnivelPositivoM", calc.desnivelPositivoM),
    dmenos: comparar("desnivelNegativoM", calc.desnivelNegativoM),
  })
}

console.log(`${conTrack} con track GPX\n`)

const difieren = filas.filter((f) =>
  f.longitud?.estado === "DIFIERE" || f.dmas?.estado === "DIFIERE" || f.dmenos?.estado === "DIFIERE")
const vacios = filas.filter((f) =>
  !difieren.includes(f) &&
  (f.longitud?.estado === "vacío" || f.dmas?.estado === "vacío" || f.dmenos?.estado === "vacío"))

const linea = (f) => {
  const c = (x, u) => x == null ? "—"
    : x.estado === "coincide" ? `=${x.guardado}${u}`
    : x.estado === "vacío" ? `(vacío → ${x.calculado}${u})`
    : `${x.guardado}${u} → ${x.calculado}${u}`
  return `  #${String(f.id).padStart(4)}  ${(f.nombre ?? "").slice(0, 34).padEnd(34)}  ` +
         `${f.nota ?? `${c(f.longitud, "km")}  ${c(f.dmas, "m")}  ${c(f.dmenos, "m")}`}`
}

if (difieren.length > 0) {
  console.log("DIFIEREN — el track dice otra cosa que lo guardado.")
  console.log("Puede ser un umbral distinto al que se usó, o una corrección a mano.")
  console.log("Lo segundo NO hay que pisarlo.\n")
  difieren.forEach((f) => console.log(linea(f)))
  console.log()
}

if (vacios.length > 0) {
  console.log(`SE PODRÍAN RELLENAR — ${vacios.length} ruta(s) con huecos y un track que los cubre.\n`)
  vacios.forEach((f) => console.log(linea(f)))
  console.log()
}

console.log(`resumen: ${difieren.length} difieren · ${vacios.length} con huecos · ` +
            `${conTrack - difieren.length - vacios.length} coinciden`)
console.log("\nEste script no modifica nada. Para aplicar algo hay que decidir antes")
console.log("qué pasa con los valores escritos a mano (ver la cabecera del fichero).")
