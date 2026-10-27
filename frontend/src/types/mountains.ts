// ─── Mountain types ──────────────────────────────────

export interface MountainSummary {
  id: number
  nombre: string
  region: string
  altitud: number
  pais: string
}

export interface MountainDetail {
  id: number
  nombre: string
  region: string
  altitud: number
  pais: string
  createdAt: string
  updatedAt: string
}

export interface CreateMountainRequest {
  nombre: string
  region: string
  altitud: number
  pais: string
}

export interface UpdateMountainRequest {
  nombre: string
  region: string
  altitud: number
  pais: string
}

// ─── Acceso por nivel ─────────────────────────────────

export interface AccesoNivelResponse {
  id: number
  nivelSocioId: string
  nivelSocioNombre: string
  maxIfasId: string
  maxIfasGrado: string
  maxRocaId: string
  maxRocaUiaa: string
  maxHieloId: string
  maxHieloGrado: string
  maxCompromisoId: string
  maxCompromisoTipo: string
  maxYosemiteId: string
  maxYosemiteTipo: string
  maxSaddayTecnicoId: string
  maxSaddayTecnicoEscala: string
  maxSaddayFisicoId: string
  maxSaddayFisicoEscala: string
  updatedAt: string | null
}

export interface UpdateAccesoNivelRequest {
  maxIfasId: string
  maxRocaId: string
  maxHieloId: string
  maxCompromisoId: string
  maxYosemiteId: string
  maxSaddayTecnicoId: string
  maxSaddayFisicoId: string
}

// ─── Mountain lookups ─────────────────────────────────

export interface MountainLookups {
  escalasAlpina: Array<{ id: string; grado: string; nombre: string; rank: number }>
  dificultadesRoca: Array<{ id: string; uiaa: string; francesa: string; rank: number }>
  dificultadesHielo: Array<{ id: string; grado: string; rank: number }>
  compromisos: Array<{ id: string; tipo: string; rank: number }>
  yosemiteClases: Array<{ id: string; tipo: string; rank: number }>
  saddayRiesgos: Array<{ id: string; escala: string; valor: number; rank: number }>
  clasificacionesSocio: Array<{ id: string; nivel: number; nombre: string }>
  equipos: Array<{ id: number; nombre: string; descripcion: string }>
  dificultadesSenderismo: Array<{ id: string; nombre: string; rank: number }>
}

/** Una escala dentro de un cambio de umbral pendiente (FR-026). */
export interface CambioEscala {
  escala: string
  valorActual: string | null
  valorPropuesto: string
  /** El sentido lo calcula el servidor: depende del orden de cada escala. */
  sentido: "IGUAL" | "RELAJA" | "ENDURECE"
}

/** Un cambio de umbrales esperando aprobación (FR-026). */
export interface CambioUmbralResponse {
  id: string
  nivelSocioId: string
  nivelSocioNombre: string
  solicitadoPorNombre: string
  solicitadoEn: string
  estado: "PENDIENTE" | "APROBADO" | "RECHAZADO" | "CANCELADO"
  resueltoPorNombre: string | null
  resueltoEn: string | null
  motivoRechazo: string | null
  /** Las siete escalas, cambien o no. */
  escalas: CambioEscala[]
  /** Lo decide el servidor: quien lo pidió no puede aprobarlo. */
  puedoAprobar: boolean
  puedoCancelar: boolean
}
