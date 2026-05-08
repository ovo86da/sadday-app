// ─── Acta types ──────────────────────────────────────

export type TipoActa = "DIRECTIVA" | "SOCIOS"

export interface AsistenteResponse {
  id: number
  socioId: string | null
  socioNombre: string | null
  socioApellido: string | null
  nombreRaw: string | null
}

export interface InformeLinkResponse {
  id: number
  informeId: string
  salidaId: string
  salidaNombre: string
}

export interface ActaSummary {
  id: string
  tipoActa: TipoActa
  numeroReunion: number | null
  fecha: string
  hora: string
  horaFin: string | null
  lugar: string | null
  totalAsistentes: number
  presidenteReunionId: string | null
  presidenteReunionNombre: string | null
  creadaPorId: string
  creadaPorNombre: string
  createdAt: string
}

export interface ActaDetail {
  id: string
  tipoActa: TipoActa
  numeroReunion: number | null
  fecha: string
  hora: string
  horaFin: string | null
  lugar: string | null
  actividadesRealizadasDesc: string | null
  actividadesPorRealizar: string | null
  acuerdos: string | null
  varios: string | null
  observaciones: string | null
  presidenteReunionId: string | null
  presidenteReunionNombre: string | null
  secretariaReunionId: string | null
  secretariaReunionNombre: string | null
  asistentes: AsistenteResponse[]
  informes: InformeLinkResponse[]
  documentoId: string | null
  documentoFilename: string | null
  creadaPorId: string
  creadaPorNombre: string
  createdAt: string
  updatedAt: string
}

export interface CreateActaRequest {
  tipoActa: TipoActa
  numeroReunion?: number
  fecha: string
  hora: string
  horaFin?: string
  lugar?: string
  actividadesRealizadasDesc?: string
  actividadesPorRealizar?: string
  acuerdos?: string
  varios?: string
  observaciones?: string
  presidenteReunionId?: string
  secretariaReunionId?: string
  asistentesIds?: string[]
  informesIds?: string[]
}

export interface UpdateActaRequest {
  tipoActa?: TipoActa
  numeroReunion?: number
  fecha?: string
  hora?: string
  horaFin?: string
  lugar?: string
  actividadesRealizadasDesc?: string
  actividadesPorRealizar?: string
  acuerdos?: string
  varios?: string
  observaciones?: string
  presidenteReunionId?: string
  secretariaReunionId?: string
}

// ─── Importación desde .md ──────────────────────────

export interface CandidatoSocio {
  socioId: string
  nombre: string
  apellido: string
}

export interface PersonaImport {
  nombreRaw: string
  resuelto: boolean
  socioId: string | null
  socioNombre: string | null
  socioApellido: string | null
  candidatos: CandidatoSocio[]
}

export interface AsistenteImport {
  nombreRaw: string
  resuelto: boolean
  socioId: string | null
  socioNombre: string | null
  socioApellido: string | null
  candidatos: CandidatoSocio[]
}

export interface ActaImportPreview {
  tipoActa: TipoActa
  numeroReunion: number | null
  fecha: string | null
  hora: string | null
  horaFin: string | null
  lugar: string | null
  presidenteReunion: PersonaImport | null
  secretariaReunion: PersonaImport | null
  asistentes: AsistenteImport[]
  actividadesRealizadasDesc: string | null
  actividadesPorRealizar: string | null
  acuerdos: string | null
  varios: string | null
  observaciones: string | null
  listaParaConfirmar: boolean
}

export interface AsistenteConfirm {
  nombreRaw: string
  socioId: string | null
}

export interface ActaImportConfirmRequest {
  tipoActa: TipoActa
  numeroReunion: number | null
  fecha: string
  hora: string
  horaFin: string | null
  lugar: string | null
  presidenteReunionId: string | null
  presidenteReunionNombreRaw: string | null
  secretariaReunionId: string | null
  secretariaReunionNombreRaw: string | null
  asistentes: AsistenteConfirm[]
  actividadesRealizadasDesc: string | null
  actividadesPorRealizar: string | null
  acuerdos: string | null
  varios: string | null
  observaciones: string | null
}
