/**
 * Analiza un GPX y saca los datos de la ruta (FR-023).
 *
 * Se hace en el navegador a proposito. El dato se necesita en el formulario
 * antes de que la ruta exista —el fichero se encola y se sube al guardar— y el
 * navegador ya lo tiene en memoria. Ademas mantiene la propiedad que hace
 * seguro a FR-022: el servidor nunca interpreta el XML de un tercero, asi que
 * no se abre superficie de XXE en el backend. `DOMParser` no resuelve entidades
 * externas, de modo que el vector clasico no aplica aqui tampoco.
 */

/** Movimiento minimo para que un tramo cuente como distancia, en metros. */
const MINIMO_MOVIMIENTO_M = 12

/** Cambio minimo de altitud para que cuente como desnivel, en metros. */
const UMBRAL_DESNIVEL_M = 40

/**
 * Tope de puntos a recorrer. Un GPX de 10 MB son decenas de miles de puntos y
 * el parseo no debe bloquear la interfaz; por encima de esto se muestrea.
 */
const MAXIMO_PUNTOS = 20_000

export interface DatosDelTrack {
  /** Kilometros, un decimal. */
  distanciaKm: number
  /** Metros, redondeado a la decena. */
  desnivelPositivoM: number
  /** Metros, redondeado a la decena. */
  desnivelNegativoM: number
  /** Horas con un decimal, o `null` si el GPX no trae marcas de tiempo. */
  duracionHoras: number | null
  /** Dias completos cuando el track pasa de 24 h, si no `null`. */
  duracionDias: number | null
  altitudMinimaM: number | null
  altitudMaximaM: number | null
  puntos: number
  /** True si se muestreo por exceder {@link MAXIMO_PUNTOS}. */
  muestreado: boolean
}

interface Punto {
  lat: number
  lon: number
  ele: number | null
  t: number | null
}

/** Distancia entre dos puntos sobre la esfera, en metros. */
function haversine(a: Punto, b: Punto): number {
  const R = 6_371_000
  const f1 = (a.lat * Math.PI) / 180
  const f2 = (b.lat * Math.PI) / 180
  const df = f2 - f1
  const dl = ((b.lon - a.lon) * Math.PI) / 180
  const h =
    Math.sin(df / 2) ** 2 + Math.cos(f1) * Math.cos(f2) * Math.sin(dl / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

function leerPuntos(xml: Document): Punto[] {
  const nodos = Array.from(xml.getElementsByTagName("trkpt"))
  return nodos.map((n) => {
    const ele = n.getElementsByTagName("ele")[0]?.textContent
    const t = n.getElementsByTagName("time")[0]?.textContent
    const ms = t ? Date.parse(t) : NaN
    return {
      lat: Number(n.getAttribute("lat")),
      lon: Number(n.getAttribute("lon")),
      ele: ele != null && ele !== "" ? Number(ele) : null,
      t: Number.isNaN(ms) ? null : ms,
    }
  })
}

/**
 * Distancia acumulada descartando los movimientos menores de 12 m.
 *
 * En crudo sale un 4-5 % larga: la geometria es exacta, pero se aplica sobre
 * puntos con ruido y cada temblor del GPS añade longitud. El filtro no es un
 * suavizado: se compara contra el ultimo punto que SI conto, no contra el
 * anterior, de modo que un tramo lento no se pierde, solo se agrupa.
 */
function distanciaM(puntos: Punto[]): number {
  if (puntos.length < 2) return 0
  let total = 0
  let referencia = puntos[0]
  for (const p of puntos.slice(1)) {
    const d = haversine(referencia, p)
    if (d >= MINIMO_MOVIMIENTO_M) {
      total += d
      referencia = p
    }
  }
  return total
}

/**
 * Desnivel acumulado de subida y de bajada, con histeresis de 40 m.
 *
 * El umbral no se puede clavar: en crudo infla entre un 21 % y un 48 %, y el
 * valor que acierta depende del perfil —30 m en una ruta de subida y bajada,
 * 70 m en una ondulada de 23 km—. 40 m es el compromiso calibrado contra las
 * referencias del club, con un error del -1 % al +4 %. Por eso el resultado se
 * presenta como estimacion y no como dato.
 */
function desnivelesM(puntos: Punto[]): { subida: number; bajada: number } {
  let subida = 0
  let bajada = 0
  let base: number | null = null
  for (const p of puntos) {
    if (p.ele == null) continue
    if (base == null) {
      base = p.ele
      continue
    }
    const d = p.ele - base
    if (d >= UMBRAL_DESNIVEL_M) {
      subida += d
      base = p.ele
    } else if (-d >= UMBRAL_DESNIVEL_M) {
      bajada += -d
      base = p.ele
    }
  }
  return { subida, bajada }
}

/** Redondea a la decena: dar «1112 m» sugiere una exactitud que no existe. */
const aDecenas = (m: number) => Math.round(m / 10) * 10

/**
 * Analiza el contenido de un GPX. Devuelve `null` si no hay puntos que medir,
 * que es como se distingue «este fichero no sirve» de «este fichero da ceros».
 */
export function analizarGpx(contenido: string): DatosDelTrack | null {
  const xml = new DOMParser().parseFromString(contenido, "application/xml")
  if (xml.getElementsByTagName("parsererror").length > 0) return null

  let puntos = leerPuntos(xml).filter(
    (p) => Number.isFinite(p.lat) && Number.isFinite(p.lon),
  )
  if (puntos.length < 2) return null

  const totales = puntos.length
  const muestreado = totales > MAXIMO_PUNTOS
  if (muestreado) {
    // Se muestrea conservando el primero y el ultimo: perder un extremo
    // falsearia la distancia y el desnivel justo donde mas se nota.
    const paso = Math.ceil(totales / MAXIMO_PUNTOS)
    const reducidos = puntos.filter((_, i) => i % paso === 0)
    if (reducidos[reducidos.length - 1] !== puntos[totales - 1]) {
      reducidos.push(puntos[totales - 1])
    }
    puntos = reducidos
  }

  const { subida, bajada } = desnivelesM(puntos)
  const alturas = puntos.map((p) => p.ele).filter((e): e is number => e != null)
  const tiempos = puntos.map((p) => p.t).filter((t): t is number => t != null)

  // El tiempo total, no el tiempo en movimiento: un descanso en una ruta de
  // montaña forma parte de lo que hay que planificar, y el club publica la
  // duracion para que la gente sepa a que hora vuelve.
  let duracionHoras: number | null = null
  let duracionDias: number | null = null
  if (tiempos.length >= 2) {
    const horas = (Math.max(...tiempos) - Math.min(...tiempos)) / 3_600_000
    if (horas >= 24) {
      // Dias y horas son campos distintos. Pasadas las 24 h lo util es el
      // numero de dias; dejar tambien 37 horas sueltas confundiria.
      duracionDias = Math.ceil(horas / 24)
    } else {
      duracionHoras = Math.round(horas * 10) / 10
    }
  }

  return {
    distanciaKm: Math.round((distanciaM(puntos) / 1000) * 10) / 10,
    desnivelPositivoM: aDecenas(subida),
    desnivelNegativoM: aDecenas(bajada),
    duracionHoras,
    duracionDias,
    altitudMinimaM: alturas.length > 0 ? Math.round(Math.min(...alturas)) : null,
    altitudMaximaM: alturas.length > 0 ? Math.round(Math.max(...alturas)) : null,
    puntos: totales,
    muestreado,
  }
}
