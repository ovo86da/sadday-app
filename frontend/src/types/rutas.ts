// ─── Estado de una ruta ───────────────────────────────────────────────────────

export type EstadoRuta = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA'

export const ESTADO_RUTA_LABELS: Record<EstadoRuta, string> = {
  PENDIENTE:  'Pendiente',
  APROBADA:   'Aprobada',
  RECHAZADA:  'Rechazada',
}

export const ESTADO_RUTA_BADGE: Record<EstadoRuta, string> = {
  PENDIENTE: 'secondary',
  APROBADA:  'default',
  RECHAZADA: 'destructive',
}

// ─── Tipos de actividad ──────────────────────────────────────────────────────

export type TipoActividad = 'ALPINISMO' | 'ESCALADA' | 'TREKKING' | 'CICLISMO' | 'INTEGRAL'

export const TIPO_ACTIVIDAD_LABELS: Record<TipoActividad, string> = {
  ALPINISMO: 'Alpinismo',
  ESCALADA:  'Escalada',
  TREKKING:  'Trekking',
  CICLISMO:  'Ciclismo',
  INTEGRAL:  'Integral',
}

/** Hex colors for charts — orange / red / green-600 / blue / purple */
export const CATEGORIA_HEX: Record<string, string> = {
  ALPINISMO: "#f97316",
  ESCALADA:  "#ef4444",
  TREKKING:  "#16a34a",
  CICLISMO:  "#3b82f6",
  INTEGRAL:  "#a855f7",
}

/** Light badge classes (bg-color/15) */
export const CATEGORIA_BADGE: Record<string, string> = {
  ALPINISMO: "bg-orange-500/15 text-orange-600",
  ESCALADA:  "bg-red-500/15 text-red-600",
  TREKKING:  "bg-green-500/15 text-green-600",
  CICLISMO:  "bg-blue-500/15 text-blue-600",
  INTEGRAL:  "bg-purple-500/20 text-purple-300",
}

/** Solid badge classes (filled background) */
export const CATEGORIA_BADGE_SOLID: Record<string, string> = {
  ALPINISMO: "bg-orange-500 text-white",
  ESCALADA:  "bg-red-500 text-white",
  TREKKING:  "bg-green-600 text-white",
  CICLISMO:  "bg-blue-500 text-white",
  INTEGRAL:  "bg-purple-500 text-white",
}

export const TIPOS_ESCALADA = ['DEPORTIVA', 'TRADICIONAL', 'MIXTA', 'BOULDER'] as const
export const TIPOS_BICICLETA = ['RIGIDA', 'DOBLE_SUSPENSION', 'ENDURO', 'GRAVEL', 'RUTA'] as const
export const DIFICULTADES_CICLISMO = ['S0', 'S1', 'S2', 'S3', 'S4'] as const

export const TIPO_BICICLETA_LABELS: Record<string, string> = {
  RIGIDA:           'Rígida',
  DOBLE_SUSPENSION: 'Doble suspensión',
  ENDURO:           'Enduro',
  GRAVEL:           'Gravel',
  RUTA:             'Ruta',
}

// ─── Lookups ─────────────────────────────────────────────────────────────────

export interface EquipoMontana {
  id: number
  nombre: string
  descripcion: string
}

// ─── Sub-tipos de dificultad (en respuesta detalle) ──────────────────────────

export interface AlpinismoDetail {
  escalaAlpinaIfasId: string
  escalaAlpinaIfasGrado: string
  dificultadRocaId: string
  dificultadRocaUiaa: string
  dificultadHieloId: string
  dificultadHieloGrado: string
  compromisoId: string
  compromisoTipo: string
  yosemiteId: string
  yosemiteTipo: string
  saddayNivelTecnicoId: string
  saddayNivelTecnicoEscala: string
  saddayNivelFisicoId: string
  saddayNivelFisicoEscala: string
  equipoMontanaId: number | null
  equipoMontanaNombre: string | null
}

export interface EscaladaDetail {
  dificultadRocaId: string
  dificultadRocaUiaa: string
  tipoEscalada: string
  numCintas: number | null
  alturaViaM: number | null
  tipoRoca: string | null
}

export interface TrekkingDetail {
  dificultadId: string
  dificultadNombre: string
  esCircular: boolean
  fuentesAgua: boolean
  tipoTerreno: string | null
}

export interface CiclismoDetail {
  tipoBicicleta: string
  dificultadTecnica: string | null
  superficiePredominante: string | null
  ciclabilidadPct: number | null
}

export interface CumbreItem {
  secuencia: number
  mountainId: number
  mountainNombre: string
  altitud: number | null
}

export interface IntegralDetail {
  dificultadMaxTipo: string | null
  dificultadMaximaDescripcion: string | null
  descripcionItinerario: string | null
  cumbres: CumbreItem[]
}

// ─── Documentos de la ruta ───────────────────────────────────────────────────

/** PERMISO es la plantilla oficial en blanco, no un permiso ya firmado. */
export const TIPOS_DOCUMENTO_RUTA = ["PERMISO", "MAPA", "TRACK_GPX", "OTRO"] as const
export type TipoDocumentoRuta = (typeof TIPOS_DOCUMENTO_RUTA)[number]

/** Encabezado de cada grupo en la lista: describe un conjunto, va en plural. */
export const ETIQUETA_TIPO_DOCUMENTO: Record<TipoDocumentoRuta, string> = {
  PERMISO:   "Plantillas de permiso",
  MAPA:      "Mapas",
  TRACK_GPX: "Tracks GPX",
  OTRO:      "Otros documentos",
}

/**
 * Opción del selector al subir: describe *el fichero que vas a adjuntar*, en
 * singular y diciendo qué formato se espera.
 *
 * Son textos distintos de los encabezados a propósito. Reutilizar «Otros
 * documentos» como opción de un selector no dice qué estás subiendo, solo
 * nombra un cajón.
 */
export const OPCION_TIPO_DOCUMENTO: Record<TipoDocumentoRuta, string> = {
  TRACK_GPX: "Track GPX — el recorrido grabado (.gpx)",
  MAPA:      "Mapa de la ruta (PDF)",
  PERMISO:   "Plantilla de permiso — el formulario en blanco",
  OTRO:      "Otro documento (PDF, Word o Excel)",
}

/**
 * Cuantos documentos de cada tipo puede tener un socio en una ruta.
 *
 * Espejo de LIMITE_POR_SOCIO del backend, que es quien manda: esto solo sirve
 * para avisar antes de subir en vez de dejar que falle con un 409.
 */
export const LIMITE_POR_TIPO: Record<TipoDocumentoRuta, number> = {
  TRACK_GPX: 1,
  MAPA:      3,
  PERMISO:   3,
  OTRO:      3,
}

/**
 * Tamaño máximo por fichero, en bytes. Espejo de MAX_SIZE_BYTES del backend.
 *
 * Se comprueba también en el navegador para no enviar megabytes que van a ser
 * rechazados, y sobre todo porque al superar el límite de multipart el servidor
 * corta la conexión y el navegador se queda sin respuesta: sin esta
 * comprobación el usuario ve un error genérico en lugar del motivo.
 */
export const MAX_TAMANO_BYTES = 10 * 1024 * 1024

/** Mensaje de rechazo por tamaño, diciendo cuánto ocupa de verdad. */
export function errorDeTamano(file: File): string | null {
  if (file.size <= MAX_TAMANO_BYTES) return null
  const mb = (file.size / 1024 / 1024).toFixed(1)
  return `"${file.name}" ocupa ${mb} MB y el máximo permitido son 10 MB.`
}

/** Orden del selector: primero lo que más se sube. */
export const ORDEN_SELECTOR_TIPO: TipoDocumentoRuta[] = ["TRACK_GPX", "MAPA", "PERMISO", "OTRO"]

export interface RutaDocumento {
  id: string
  tipo: TipoDocumentoRuta
  filename: string
  contentType: string
  sizeBytes: number
  subidoPorId: string | null
  subidoPorNombre: string | null
  createdAt: string
  /** Cuántos socios avalan este fichero. */
  confirmaciones: number
  /** Si quien consulta ya lo confirmó. */
  confirmadoPorMi: boolean
  /** Si quien consulta puede borrarlo: lo subió él, o es de la directiva. */
  puedoEliminar: boolean
}

// ─── Enlaces externos de la ruta ─────────────────────────────────────────────

export interface RutaEnlace {
  id: number
  etiqueta: string
  url: string
  orden: number
}

export interface RutaEnlaceRequest {
  etiqueta: string
  url: string
  orden?: number
}

// ─── Contacto ────────────────────────────────────────────────────────────────

export interface ContactoResponse {
  id: number
  nombre: string
  telefono: string | null
  correo: string | null
  tipoContactoNombre: string
}

// ─── Ruta Summary (listados) ─────────────────────────────────────────────────

export interface RutaSummary {
  id: number
  nombre: string
  tipoActividad: TipoActividad
  mountainId: number | null
  mountainNombre: string | null
  lugarReferencia: string | null
  sectorZona: string | null
  longitudKm: number | null
  desnivelPositivoM: number | null
  /** Magnitud sin signo: 1248 significa 1248 m de bajada. */
  desnivelNegativoM: number | null
  duracionDias: number | null
  duracionHoras: number | null
  requierePermisos: boolean
  trackUrl: string | null
  nivelMinimoSocioId: string | null
  nivelMinimoSocioNombre: string | null
  estado: EstadoRuta
  propuestaPorId: string
  createdAt: string
  dificultadResumen: string
  numeroCumbres: number | null
}

// ─── Ruta Detail (detalle completo) ─────────────────────────────────────────

export interface RutaDetail {
  id: number
  nombre: string
  tipoActividad: TipoActividad
  mountainId: number | null
  mountainNombre: string | null
  lugarReferencia: string | null
  sectorZona: string | null
  longitudKm: number | null
  desnivelPositivoM: number | null
  /** Magnitud sin signo: 1248 significa 1248 m de bajada. */
  desnivelNegativoM: number | null
  duracionDias: number | null
  duracionHoras: number | null
  peligrosNotas: string | null
  requierePermisos: boolean
  documentacionUrl: string | null
  trackUrl: string | null
  nivelMinimoSocioId: string | null
  nivelMinimoSocioNombre: string | null
  estado: EstadoRuta
  revisadaPorId: string | null
  revisadaEn: string | null
  motivoRechazo: string | null
  propuestaPorId: string
  contactos: ContactoResponse[]
  documentos: RutaDocumento[]
  enlaces: RutaEnlace[]
  createdAt: string
  updatedAt: string

  // Solo uno será no nulo según tipoActividad
  alpinismo: AlpinismoDetail | null
  escalada:  EscaladaDetail  | null
  trekking:  TrekkingDetail  | null
  ciclismo:  CiclismoDetail  | null
  integral:  IntegralDetail  | null
}

// ─── Requests ────────────────────────────────────────────────────────────────

export interface CreateRutaRequest {
  nombre: string
  tipoActividad: TipoActividad
  mountainId?: number
  lugarReferencia?: string
  sectorZona?: string
  longitudKm?: number
  desnivelPositivoM?: number
  desnivelNegativoM?: number
  duracionDias?: number
  duracionHoras?: number
  peligrosNotas?: string
  requierePermisos: boolean
  documentacionUrl?: string
  trackUrl?: string
  nivelMinimoSocioId?: string

  // Alpinismo
  escalaAlpinaIfasId?: string
  dificultadRocaId?: string
  dificultadHieloId?: string
  compromisoId?: string
  yosemiteId?: string
  saddayNivelTecnicoId?: string
  saddayNivelFisicoId?: string
  equipoMontanaId?: number

  // Escalada
  tipoEscalada?: string
  numCintas?: number
  alturaViaM?: number
  tipoRoca?: string

  // Trekking
  dificultadSenderismoId?: string
  esCircular?: boolean
  fuentesAgua?: boolean
  tipoTerreno?: string

  // Ciclismo
  tipoBicicleta?: string
  dificultadTecnicaCiclismo?: string
  superficiePredominante?: string
  ciclabilidadPct?: number

  // Integral
  cumbresMountainIds?: number[]
  dificultadMaxTipo?: string
  dificultadMaximaDescripcion?: string
  descripcionItinerario?: string
}

export type UpdateRutaRequest = CreateRutaRequest
