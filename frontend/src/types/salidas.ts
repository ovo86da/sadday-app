import type { RutaDocumento } from "@/types/rutas"

// ─── Salida enums ────────────────────────────────────

export type EstadoSalida = "PLANIFICADA" | "EN_CURSO" | "REALIZADA" | "CANCELADA"
export type EstadoInscripcion = "INSCRITO" | "CONFIRMADO" | "NO_FUE" | "CANCELADO" | "PENDIENTE_APROBACION" | "NEGADO"

// ─── Lookups ─────────────────────────────────────────

export interface PublicoObjetivoItem {
  id: string
  nombre: string
}

export interface FormatoSalidaItem {
  id: string
  nombre: string
}

export interface DignidadItem {
  id: number
  nombre: string
  descripcion: string
}

export interface SalidaLookupsResponse {
  publicosObjetivo: PublicoObjetivoItem[]
  formatosSalida: FormatoSalidaItem[]
  dignidades: DignidadItem[]
  estadosSalida: string[]
  estadosInscripcion: string[]
}

// ─── Salida Summary (lista) ──────────────────────────

export interface SalidaSummary {
  id: string
  nombre: string
  fechaInicio: string
  horaEncuentroClub: string
  fechaFin: string
  rutaNombre: string | null
  tipoActividad: string | null
  publicoObjetivoNombre: string | null
  formatoSalidaNombre: string | null
  nivelMinimoNombre: string | null
  capacidadMaxima: number | null
  totalInscritos: number
  inscripcionesCerradas: boolean
  estado: EstadoSalida
  motivoCancelacion: string | null
  tieneInforme: boolean
  createdAt: string
}

// ─── Salida Detail ───────────────────────────────────

export interface DignidadAsignada {
  id: number
  dignidadId: number
  dignidadNombre: string
}

/**
 * Qué dice la matriz de acceso sobre un socio y una ruta (FR-028).
 *
 * El sentido y los valores los calcula el servidor: dependen del `rank` de cada
 * catálogo, que el cliente no conoce. Aquí solo se muestran.
 */
export interface EvaluacionNivel {
  /** true solo si no hay nada que exceda y nada sin comprobar. */
  apto: boolean
  hallazgos: HallazgoNivel[]
}

export interface HallazgoNivel {
  /** «Hielo», «Senderismo»… o null cuando el hueco no es de una escala concreta. */
  escala: string | null
  exigeRuta: string | null
  permiteNivel: string | null
  tipo: 'EXCEDE' | 'SIN_COMPROBAR'
  /** Frase lista para mostrar, construida en el servidor. */
  detalle: string
}

export interface Participante {
  id: number
  socioId: string
  socioNombre: string
  socioApellido: string
  estadoInscripcion: EstadoInscripcion
  esJefeSalida: boolean
  dignidades: DignidadAsignada[]
  nivelSocioId: string | null
  nivelSocioNombre: string | null
  nivelMinimoRequeridoId: string | null
  nivelMinimoRequeridoNombre: string | null
  nivelInsuficiente: boolean
  riesgoAprobadoPorDirectivo: string | null
  riesgoAprobadoPorDirectivoNombre: string | null
  riesgoAprobadoPorJefe: string | null
  riesgoAprobadoPorJefeNombre: string | null
  riesgoAprobadoEn: string | null
  motivoDirectivo: string | null
  motivoJefe: string | null
  createdAt: string
  cedula: string | null
  telefono: string | null
  edad: number | null
  /** Null cuando la salida no tiene ruta. */
  evaluacionNivel: EvaluacionNivel | null
}

export interface AprobacionPendiente {
  participanteId: number
  salidaId: string
  salidaNombre: string
  fechaSalida: string
  socioId: string
  socioNombre: string
  socioApellido: string
  nivelSocioNombre: string | null
  nivelMinimoNombre: string | null
  aprobadoPorDirectivo: boolean
  aprobadoPorJefe: boolean
  hayJefeSalida: boolean
  /** Por qué está parada, escala por escala (FR-028). */
  evaluacionNivel: EvaluacionNivel | null
}

export interface SalidaDetail {
  id: string
  nombre: string
  fechaInicio: string
  horaEncuentroClub: string
  fechaFin: string
  horaEstimadaRegresoClub: string | null
  rutaId: number | null
  rutaNombre: string | null
  tipoActividad: string | null
  publicoObjetivoId: string | null
  publicoObjetivoNombre: string | null
  formatoSalidaId: string | null
  formatoSalidaNombre: string | null
  nivelMinimoRequeridoId: string | null
  nivelMinimoRequeridoNombre: string | null
  /**
   * El mínimo que exige la ruta según la matriz (FR-028). Deducido, no tecleado.
   * Null cuando ningún nivel la permite o falta algún dato para saberlo.
   */
  nivelMinimoDerivadoId: string | null
  nivelMinimoDerivadoNombre: string | null
  capacidadMaxima: number | null
  totalInscritos: number
  inscripcionesCerradas: boolean
  estado: EstadoSalida
  motivoCancelacion: string | null
  creadoPorId: string
  creadoPorNombreCompleto: string
  participantes: Participante[]
  documentos: RutaDocumento[]
  createdAt: string
  updatedAt: string
}

// ─── Requests ────────────────────────────────────────

export interface CreateSalidaRequest {
  nombre: string
  fechaInicio: string
  horaEncuentroClub: string
  fechaFin: string
  horaEstimadaRegresoClub?: string
  rutaId?: number
  tipoActividad?: string
  publicoObjetivoId?: string
  formatoSalidaId?: string
  nivelMinimoRequeridoId?: string
  capacidadMaxima?: number
}

export type UpdateSalidaRequest = CreateSalidaRequest

export interface CambiarEstadoSalidaRequest {
  estado: EstadoSalida
}

export interface InscribirRequest {
  socioId: string
}

export interface EliminarSalidaRequest {
  motivo: string
}

export interface CancelarSalidaRequest {
  motivo: string
}
