// ─── Tipos de actividad ──────────────────────────────────────────────────────

export type TipoActividad = 'ALPINISMO' | 'ESCALADA' | 'TREKKING' | 'CICLISMO'

export const TIPO_ACTIVIDAD_LABELS: Record<TipoActividad, string> = {
  ALPINISMO: 'Alpinismo',
  ESCALADA:  'Escalada',
  TREKKING:  'Trekking',
  CICLISMO:  'Ciclismo',
}

/** Hex colors for charts — orange / red / green-600 / blue */
export const CATEGORIA_HEX: Record<string, string> = {
  ALPINISMO: "#f97316",
  ESCALADA:  "#ef4444",
  TREKKING:  "#16a34a",
  CICLISMO:  "#3b82f6",
}

/** Light badge classes (bg-color/15) */
export const CATEGORIA_BADGE: Record<string, string> = {
  ALPINISMO: "bg-orange-500/15 text-orange-600",
  ESCALADA:  "bg-red-500/15 text-red-600",
  TREKKING:  "bg-green-500/15 text-green-600",
  CICLISMO:  "bg-blue-500/15 text-blue-600",
}

/** Solid badge classes (filled background) */
export const CATEGORIA_BADGE_SOLID: Record<string, string> = {
  ALPINISMO: "bg-orange-500 text-white",
  ESCALADA:  "bg-red-500 text-white",
  TREKKING:  "bg-green-600 text-white",
  CICLISMO:  "bg-blue-500 text-white",
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

// ─── Documento de permiso ────────────────────────────────────────────────────

export interface RutaDocumento {
  id: string
  filename: string
  contentType: string
  sizeBytes: number
  subidoPorNombre: string | null
  createdAt: string
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
  desnivelM: number | null
  duracionDias: number | null
  duracionHoras: number | null
  requierePermisos: boolean
  trackUrl: string | null
  nivelMinimoSocioId: string | null
  nivelMinimoSocioNombre: string | null
  aprobada: boolean
  propuestaPorId: string
  createdAt: string
  dificultadResumen: string
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
  desnivelM: number | null
  duracionDias: number | null
  duracionHoras: number | null
  peligrosNotas: string | null
  requierePermisos: boolean
  documentacionUrl: string | null
  trackUrl: string | null
  nivelMinimoSocioId: string | null
  nivelMinimoSocioNombre: string | null
  aprobada: boolean
  aprobadaPorId: string | null
  aprobadaEn: string | null
  propuestaPorId: string
  contactos: ContactoResponse[]
  documentosPermiso: RutaDocumento[]
  createdAt: string
  updatedAt: string

  // Solo uno será no nulo según tipoActividad
  alpinismo: AlpinismoDetail | null
  escalada:  EscaladaDetail  | null
  trekking:  TrekkingDetail  | null
  ciclismo:  CiclismoDetail  | null
}

// ─── Requests ────────────────────────────────────────────────────────────────

export interface CreateRutaRequest {
  nombre: string
  tipoActividad: TipoActividad
  mountainId?: number
  lugarReferencia?: string
  sectorZona?: string
  longitudKm?: number
  desnivelM?: number
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
}

export interface UpdateRutaRequest extends CreateRutaRequest {}
