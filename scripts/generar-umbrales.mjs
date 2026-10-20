#!/usr/bin/env node
/**
 * Genera las constantes del análisis de GPX desde `shared/umbrales.json`.
 *
 * Por qué existe: la fórmula de FR-023 está implementada dos veces, en
 * TypeScript y en Dart, porque el cálculo ocurre en el dispositivo. Dos
 * constantes con el mismo significado y ningún vínculo entre ellas se
 * desincronizan sin que nadie lo note, y el síntoma sería que la web y el móvil
 * dieran desniveles distintos para el mismo fichero.
 *
 *   node scripts/generar-umbrales.mjs            # genera
 *   node scripts/generar-umbrales.mjs --verificar # falla si hay diferencias
 *
 * La CI usa `--verificar`: así, editar un fichero generado a mano o cambiar el
 * JSON sin regenerar pone la rama en rojo en vez de colarse.
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const raiz = join(dirname(fileURLToPath(import.meta.url)), "..")
const fuente = join(raiz, "shared", "umbrales.json")

const datos = JSON.parse(readFileSync(fuente, "utf-8"))
const entradas = Object.entries(datos).filter(([k]) => !k.startsWith("_"))

const AVISO = [
  "GENERADO AUTOMÁTICAMENTE — no editar a mano.",
  "",
  "Fuente: shared/umbrales.json",
  "Regenerar: node scripts/generar-umbrales.mjs",
].join("\n")

/** Parte un texto largo en líneas de comentario de como mucho 76 caracteres. */
function envolver(texto, prefijo) {
  const palabras = texto.split(" ")
  const lineas = []
  let actual = ""
  for (const p of palabras) {
    if ((actual + " " + p).trim().length > 76 - prefijo.length) {
      lineas.push(prefijo + actual.trim())
      actual = p
    } else {
      actual += " " + p
    }
  }
  if (actual.trim()) lineas.push(prefijo + actual.trim())
  return lineas.join("\n")
}

const ts =
  `/**\n${envolver(AVISO.split("\n")[0], " * ")}\n * \n` +
  ` * Fuente: shared/umbrales.json\n` +
  ` * Regenerar: node scripts/generar-umbrales.mjs\n */\n\n` +
  entradas
    .map(([nombre, { valor, doc }]) =>
      `/**\n${envolver(doc, " * ")}\n */\nexport const ${nombre} = ${valor}\n`)
    .join("\n")

const dart =
  `// ${AVISO.split("\n").join("\n// ").replace(/\n\/\/ $/, "\n//")}\n\n` +
  entradas
    .map(([nombre, { valor, doc, tipoDart }]) =>
      `${envolver(doc, "/// ")}\nconst ${tipoDart} ${nombre} = ${tipoDart === "double" ? valor.toFixed(1) : valor};\n`)
    .join("\n")

const destinos = [
  [join(raiz, "frontend", "src", "lib", "umbrales.g.ts"), ts],
  [join(raiz, "mobile", "lib", "features", "rutas", "domain", "umbrales.g.dart"), dart],
]

const verificar = process.argv.includes("--verificar")
let desfasados = 0

for (const [ruta, contenido] of destinos) {
  const relativa = ruta.slice(raiz.length + 1)
  if (verificar) {
    const actual = existsSync(ruta) ? readFileSync(ruta, "utf-8") : ""
    if (actual !== contenido) {
      console.error(`  DESFASADO  ${relativa}`)
      desfasados++
    } else {
      console.log(`  al día     ${relativa}`)
    }
  } else {
    writeFileSync(ruta, contenido)
    console.log(`  generado   ${relativa}`)
  }
}

if (verificar && desfasados > 0) {
  console.error(
    `\n${desfasados} fichero(s) generado(s) no coinciden con shared/umbrales.json.\n` +
    `Ejecuta: node scripts/generar-umbrales.mjs`)
  process.exit(1)
}
