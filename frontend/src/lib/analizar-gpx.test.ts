import { describe, it, expect } from "vitest"
import { analizarGpx } from "./analizar-gpx"

/** Construye un GPX minimo a partir de [lat, lon, ele?, tiempo?]. */
function gpx(puntos: [number, number, number?, string?][]): string {
  const trkpts = puntos
    .map(([lat, lon, ele, t]) =>
      `<trkpt lat="${lat}" lon="${lon}">` +
      (ele != null ? `<ele>${ele}</ele>` : "") +
      (t != null ? `<time>${t}</time>` : "") +
      `</trkpt>`)
    .join("")
  return `<?xml version="1.0"?><gpx version="1.1"><trk><trkseg>${trkpts}</trkseg></trk></gpx>`
}

/** Un grado de latitud son ~111.195 m, asi que esto desplaza `m` metros al norte. */
const norte = (m: number) => m / 111_195

describe("analizarGpx", () => {
  it("devuelve null si el XML no es valido", () => {
    expect(analizarGpx("<gpx><trk>")).toBeNull()
  })

  it("devuelve null con menos de dos puntos", () => {
    // null distingue «este fichero no sirve» de «este fichero da ceros».
    expect(analizarGpx(gpx([[0.1, -78.2, 3000]]))).toBeNull()
  })

  it("deja la duracion vacia si el GPX no trae marcas de tiempo", () => {
    // El caso de una ruta planificada en Wikiloc o Komoot: tiene altitud, de un
    // modelo del terreno, pero nunca se ha caminado. Verificado con
    // Cochapamba_-_Ruco.gpx: 1643 puntos, 0 marcas de tiempo.
    const r = analizarGpx(gpx([
      [0.1, -78.2, 3000],
      [0.1 + norte(500), -78.2, 3100],
    ]))!
    expect(r.duracionHoras).toBeNull()
    expect(r.duracionDias).toBeNull()
    expect(r.distanciaKm).toBeGreaterThan(0)
  })

  it("una bajada de cero es 0, no se confunde con ausencia", () => {
    // sincholagua-camping sube 1038 m y no baja nada porque termina en el
    // campamento. Es el caso que obligo a FR-024.
    const r = analizarGpx(gpx([
      [0.1, -78.2, 3800],
      [0.1 + norte(300), -78.2, 4100],
      [0.1 + norte(600), -78.2, 4400],
    ]))!
    expect(r.desnivelNegativoM).toBe(0)
    expect(r.desnivelPositivoM).toBeGreaterThan(0)
  })

  it("cuenta subida y bajada por separado", () => {
    const r = analizarGpx(gpx([
      [0.1, -78.2, 3700],
      [0.1 + norte(400), -78.2, 4000],   // +300
      [0.1 + norte(800), -78.2, 3500],   // -500
    ]))!
    expect(r.desnivelPositivoM).toBe(300)
    expect(r.desnivelNegativoM).toBe(500)
  })

  it("ignora las oscilaciones por debajo del umbral de 40 m", () => {
    // Doce temblores de 10 m no son 120 m de desnivel. Sin umbral, el calculo
    // en crudo infla entre un 21 % y un 48 % sobre los tracks del club.
    const puntos: [number, number, number?][] = []
    for (let i = 0; i < 12; i++) {
      puntos.push([0.1 + norte(i * 100), -78.2, 3000 + (i % 2) * 10])
    }
    expect(analizarGpx(gpx(puntos))!.desnivelPositivoM).toBe(0)
  })

  it("descarta los movimientos menores de 12 m al medir la distancia", () => {
    // Cien puntos a 5 m, en linea recta: 495 m reales. Sin filtro los suma
    // todos; con filtro los agrupa de 12 en 12 y el total sigue siendo el
    // correcto, que es la diferencia entre filtrar y suavizar.
    const quietos: [number, number, number?][] = []
    for (let i = 0; i < 100; i++) quietos.push([0.1 + norte(i * 5), -78.2, 3000])
    const r = analizarGpx(gpx(quietos))!
    expect(r.distanciaKm).toBeCloseTo(0.5, 1)
  })

  it("pasadas las 24 h rellena dias y deja las horas vacias", () => {
    const r = analizarGpx(gpx([
      [0.1, -78.2, 3000, "2026-08-01T06:00:00Z"],
      [0.1 + norte(500), -78.2, 3100, "2026-08-02T18:00:00Z"],
    ]))!
    expect(r.duracionDias).toBe(2)
    expect(r.duracionHoras).toBeNull()
  })

  it("usa el tiempo total, no el tiempo en movimiento", () => {
    // Tres puntos en seis horas con el del medio parado: la duracion son 6 h.
    // Un descanso en montaña forma parte de lo que hay que planificar.
    const r = analizarGpx(gpx([
      [0.1, -78.2, 3000, "2026-08-01T06:00:00Z"],
      [0.1 + norte(20), -78.2, 3000, "2026-08-01T09:00:00Z"],
      [0.1 + norte(500), -78.2, 3100, "2026-08-01T12:00:00Z"],
    ]))!
    expect(r.duracionHoras).toBe(6)
  })

  it("redondea el desnivel a decenas y la distancia a un decimal", () => {
    // Dar «1112 m» sugiere una exactitud que el metodo no tiene.
    const r = analizarGpx(gpx([
      [0.1, -78.2, 3000],
      [0.1 + norte(1234), -78.2, 3117],
    ]))!
    expect(r.desnivelPositivoM % 10).toBe(0)
    expect(r.distanciaKm).toBe(1.2)
  })

  it("informa de las altitudes y del numero de puntos", () => {
    const r = analizarGpx(gpx([
      [0.1, -78.2, 2768],
      [0.1 + norte(500), -78.2, 4027],
    ]))!
    expect(r.altitudMinimaM).toBe(2768)
    expect(r.altitudMaximaM).toBe(4027)
    expect(r.puntos).toBe(2)
    expect(r.muestreado).toBe(false)
  })
})
