// ─── Auditoría ────────────────────────────────────────────────────────────────

export interface AuditoriaEntry {
  id: number
  actorUsername: string | null
  actorNombre: string | null
  accion: string
  entidadAfectada: string | null
  entidadId: string | null
  entidadNombre: string | null
  datosAnteriores: string | null
  datosNuevos: string | null
  ipAddress: string | null
  resultado: "SUCCESS" | "FAILED" | "BLOCKED"
  detalle: string | null
  createdAt: string
}

// ─── Eventos de seguridad ─────────────────────────────────────────────────────

export interface SecurityEventEntry {
  id: string
  username: string | null
  nombre: string | null
  eventType: string
  ipAddress: string | null
  countryCode: string | null
  city: string | null
  browser: string | null
  os: string | null
  createdAt: string
  metadata: string | null
}

// ─── Configuración del sistema ────────────────────────────────────────────────

export interface ConfiguracionSistema {
  clave: string
  valor: string
  descripcion: string | null
  updatedById: string | null
  updatedAt: string
}

// ─── Usuarios Auth ────────────────────────────────────────────────────────────

export interface UsuarioAuthSummary {
  socioId: string
  username: string
  totpEnabled: boolean
  failedAttempts: number
  loginBlocked: boolean
  blockedUntil: string | null
  lastLogin: string | null
  createdAt: string
  nombre: string
  apellido: string
  correo: string
  estadoAcceso: string
  estadoAccesoNombre: string
}

export const ESTADOS_ACCESO = [
  { codigo: "ACTIVE",           nombre: "Activo",                variante: "default"     },
  { codigo: "BLOCKED",          nombre: "Bloqueado",             variante: "destructive"  },
  { codigo: "EX_MEMBER",        nombre: "Ex-miembro",            variante: "secondary"   },
  { codigo: "PENDING_REGISTER", nombre: "Pendiente de registro", variante: "outline"     },
  { codigo: "DISABLED",         nombre: "Deshabilitado",         variante: "destructive"  },
] as const

export type EstadoAccesoCodigo = typeof ESTADOS_ACCESO[number]["codigo"]
