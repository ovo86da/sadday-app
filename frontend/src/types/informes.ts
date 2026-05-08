// ─── Informe types ───────────────────────────────────

export interface InformePendienteItem {
  salidaId: string
  salidaNombre: string
  fechaFin: string
}

export type TipoReconocimiento = "AMONESTADO" | "DESTACADO"

export type TipoTransporte = "CAMIONETA" | "FURGONETA" | "BUS_MEDIANO" | "BUS_GRANDE"

export type DondeAutos =
  | "NO_AUTOS"
  | "PARQUEADERO_SEGURO"
  | "PARQUEADERO_INSEGURO"
  | "BASE_MONTANA"
  | "CALLE_SEGURO"
  | "CALLE_INSEGURO"

export interface ReconocimientoResponse {
  id: number
  socioId: string
  socioNombre: string
  socioApellido: string
  tipo: TipoReconocimiento
  motivo: string
  registradoPorId: string
  registradoPorNombre: string
  createdAt: string
}

export interface SegmentoViajeResponse {
  id: number
  orden: number
  origen: string
  destino: string
  alquiloTransporte: boolean
  tipoTransporte: TipoTransporte | null
  costoIndividual: number | null
  contactoId: number | null
  contactoNombre: string | null
  contactoTelefono: string | null
}

export interface SegmentoViajeRequest {
  origen: string
  destino: string
  alquiloTransporte: boolean
  tipoTransporte?: TipoTransporte | null
  costoIndividual?: number | null
  contactoId?: number | null
}

export interface InformeResponse {
  id: string
  salidaId: string
  salidaNombre: string
  seRealizo: boolean
  lograronCumbre: boolean
  condicionesMeterologicas: string | null
  horaSalidaClub: string | null
  horaLlegadaMontana: string | null
  horaCumbre: string | null
  horaInicioDescenso: string | null
  horaLlegadaAutos: string | null
  horaRegresoClub: string | null
  cronica: string | null
  observaciones: string | null
  comentariosVarios: string | null

  segmentos: SegmentoViajeResponse[]

  alquiloGuia: boolean
  costoGuia: number | null
  contactoGuiaId: number | null
  contactoGuiaNombre: string | null
  contactoGuiaTelefono: string | null
  contactoGuiaCorreo: string | null

  alquiloRefugio: boolean
  nombreRefugio: string | null
  costoRefugio: number | null
  contactoRefugioId: number | null
  contactoRefugioNombre: string | null

  acampo: boolean
  nombreCamping: string | null
  costoCamping: number | null
  contactoCampingId: number | null
  contactoCampingNombre: string | null

  dondeAutos: DondeAutos | null
  autosDescripcion: string | null
  autosLinkUbicacion: string | null
  costoParqueadero: number | null
  costoTotal: number | null
  costoPorPersona: number | null
  validadoPorId: string | null
  validadoPorNombre: string | null
  validadoEn: string | null
  documentoId: string | null
  documentoFilename: string | null
  reconocimientos: ReconocimientoResponse[]
  createdAt: string
  updatedAt: string
}

// Keep InformeDetail as alias for InformeResponse for backwards compat
export type InformeDetail = InformeResponse

export interface CreateInformeRequest {
  seRealizo: boolean
  lograronCumbre: boolean
  condicionesMeterologicas?: string
  horaSalidaClub?: string
  horaLlegadaMontana?: string
  horaCumbre?: string
  horaInicioDescenso?: string
  horaLlegadaAutos?: string
  horaRegresoClub?: string
  cronica?: string
  observaciones?: string
  comentariosVarios?: string

  segmentos: SegmentoViajeRequest[]

  alquiloGuia: boolean
  costoGuia?: number | null
  contactoGuiaId?: number | null
  guiaSocioId?: string | null

  alquiloRefugio: boolean
  nombreRefugio?: string | null
  costoRefugio?: number | null
  contactoRefugioId?: number | null

  acampo: boolean
  nombreCamping?: string | null
  costoCamping?: number | null
  contactoCampingId?: number | null

  dondeAutos?: DondeAutos | null
  autosDescripcion?: string | null
  autosLinkUbicacion?: string | null
  costoParqueadero?: number | null

  costoTotal?: number | null
  costoPorPersona?: number | null
}

export type UpdateInformeRequest = Partial<CreateInformeRequest>

export interface AgregarReconocimientoRequest {
  socioId: string
  tipo: TipoReconocimiento
  motivo: string
}
