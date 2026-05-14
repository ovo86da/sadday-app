// ─── API envelope ────────────────────────────────────

export interface ApiResponse<T> {
  status: string
  message: string | null
  data: T
}

// Spring Boot 3.3+ serializa la paginación en un sub-objeto "page"
export interface PageResponse<T> {
  content: T[]
  page: {
    size: number
    number: number              // page number (0-based)
    totalElements: number
    totalPages: number
  }
}

// ─── Registro token info ──────────────────────────────

export interface TokenInfoResponse {
  requiresPersonalData: boolean
  fromCsvImport: boolean
  prefilledNombre: string | null
  prefilledApellido: string | null
  prefilledTipoSocio: string | null
  prefilledNivelTecnico: string | null
}

// ─── CSV Import ───────────────────────────────────────

export interface CsvFilaValida {
  fila: number
  cedula: string
  nombre: string
  apellido: string
  correo: string
  telefono: string
  tipoSocio: string
  nivelTecnico: string
}

export interface CsvFilaError {
  fila: number
  cedula: string
  correo: string
  motivo: string
}

export interface CsvImportPreviewResponse {
  totalFilas: number
  validas: CsvFilaValida[]
  errores: CsvFilaError[]
}

export interface CsvImportResultResponse {
  importados: number
  omitidos: number
  errores: CsvFilaError[]
}

// ─── Lookups ─────────────────────────────────────────

export interface LookupItem<T = number> {
  id: T
  nombre: string
  descripcion: string
}

export interface ClasificacionItem {
  id: string
  nivel: number
  nombre: string
  descripcion: string
}

export interface LookupsResponse {
  tiposSocio: LookupItem[]
  estadosHabilitacion: LookupItem[]
  rolesSistema: LookupItem[]
  clasificaciones: ClasificacionItem[]
}

// ─── Socio Summary (lista paginada) ──────────────────

export interface SocioSummary {
  id: string
  nombre: string
  apellido: string
  cedula: string
  correo: string
  telefono: string | null
  fechaIngreso: string            // ISO date
  edad: number
  antiguedadAnios: number
  estadoHabilitacion: string
  tipoSocio: string
  nivelTecnico: string | null
  rolSistema: string
  estadoAcceso: string
  tieneCuenta: boolean
  esJefeMontana: boolean
}

// ─── Socio Detail (respuesta completa) ───────────────

export interface SocioDetail {
  id: string
  nombre: string
  apellido: string
  cedula: string
  correo: string
  telefono: string | null
  direccion: string | null
  fechaNacimiento: string         // ISO date
  fechaIngreso: string
  fechaSalida: string | null
  tipoSangre: string | null
  edad: number
  antiguedadAnios: number

  emergencyContactName: string | null
  emergencyContactPhone: string | null
  emergencyContactDireccion: string | null
  emergencyContactName2: string | null
  emergencyContactPhone2: string | null
  emergencyContactDireccion2: string | null

  estadoHabilitacionId: number
  estadoHabilitacion: string
  tipoSocioId: number
  tipoSocio: string
  nivelTecnicoId: string | null
  nivelTecnico: string | null
  rolSistemaId: number
  rolSistema: string

  estadoAccesoId: number
  estadoAcceso: string

  esJefeMontana: boolean

  createdAt: string
  updatedAt: string
}

// ─── Requests ────────────────────────────────────────

export interface CreateSocioRequest {
  cedula: string
  correo: string
  telefono?: string
}

export interface UpdateSocioRequest {
  nombre: string
  apellido: string
  cedula: string
  correo: string
  telefono?: string
  direccion?: string
  fechaNacimiento: string
  fechaIngreso?: string
  fechaSalida?: string
  tipoSangre?: string
  emergencyContactName?: string
  emergencyContactPhone?: string
  emergencyContactDireccion?: string
  emergencyContactName2?: string
  emergencyContactPhone2?: string
  emergencyContactDireccion2?: string
  tipoSocioId: number
  nivelTecnicoId?: string | null
}

export interface UpdateRolRequest {
  rolSistemaId: number
}

// ─── Cuotas ──────────────────────────────────────────

export interface CuotaResponse {
  id: number
  valor: number
  fecha: string
  estado: "PAGADO" | "PENDIENTE"
  registradoPorNombre: string | null
  createdAt: string
}

export interface CreateCuotaRequest {
  valor: number
  fecha: string
  estado: "PAGADO" | "PENDIENTE"
}

// ─── Habilitación log ─────────────────────────────────

export interface HabilitacionLogEntry {
  id: number
  estadoAnterior: string
  estadoNuevo: string
  cambiadoPorNombre: string
  cambiadoEn: string        // ISO datetime
  fuente: "MANUAL" | "CSV"
  notas: string | null
}

// ─── Invitaciones pendientes ──────────────────────────

export interface InvitacionPendiente {
  id: string
  cedula: string
  correo: string
  telefono: string | null
  nombre: string | null
  apellido: string | null
  fromCsvImport: boolean
  creadoEn: string        // ISO datetime
  expiresAt: string       // ISO datetime
  estado: "PENDIENTE" | "EXPIRADO"
}

// ─── CSV Habilitación ─────────────────────────────────

export interface CsvFilaError {
  fila: number
  nombre: string
  cedula: string
  motivo: string
}

export interface CsvHabilitacionResult {
  procesados: number
  habilitados: number
  deshabilitados: number
  sinCambio: number
  errores: CsvFilaError[]
  csvKey: string | null
}
