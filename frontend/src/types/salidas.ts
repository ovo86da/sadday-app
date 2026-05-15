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
  capacidadMaxima: number | null
  totalInscritos: number
  inscripcionesCerradas: boolean
  estado: EstadoSalida
  motivoCancelacion: string | null
  creadoPorId: string
  creadoPorNombreCompleto: string
  participantes: Participante[]
  documentosPermiso: RutaDocumento[]
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
