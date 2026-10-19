import { fileURLToPath } from "url"
import path from "path"

/**
 * Equivalente de `__dirname` para módulos ES.
 *
 * El paquete es `"type": "module"`, donde `__dirname` no existe. Los specs lo
 * usaban igualmente, así que la suite entera fallaba al cargar con
 * «__dirname is not defined in ES module scope» y ningún test llegaba a
 * ejecutarse.
 */
export const e2eDir = path.dirname(fileURLToPath(import.meta.url))
