export interface Contacto {
  id: number
  nombre: string
  telefono: string | null
  correo: string | null
  notas: string | null
  tiposContacto: string[]
  createdAt: string
  updatedAt: string
}

export interface CreateContactoRequest {
  nombre: string
  telefono?: string
  correo?: string
  notas?: string
}

export interface UpdateContactoRequest {
  nombre: string
  telefono?: string
  correo?: string
  notas?: string
}
