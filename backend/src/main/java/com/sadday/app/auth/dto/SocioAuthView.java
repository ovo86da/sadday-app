package com.sadday.app.auth.dto;

/**
 * Proyección JPA para obtener los datos mínimos de un socio necesarios para auth.
 *
 * <p>Se usa en consultas nativas desde {@code UsuarioAuthRepository} para evitar
 * crear la entidad JPA completa de {@code Socio} en el módulo de auth.
 */
public interface SocioAuthView {
    String getNombre();
    String getApellido();
    /** Nombre del rol del sistema, ej: "Admin", "Secretaria", "Directivo", "Socio". */
    String getRolNombre();
    /** Nombre del estado de habilitación, ej: "Habilitado", "Socio Vitalicio". */
    String getEstadoHabilitacion();
    /** Nombre del nivel técnico del socio, puede ser null si no tiene asignado. */
    String getNivelTecnico();
    /** Código del estado de acceso al sistema, ej: "ACTIVE", "BLOCKED". */
    String getEstadoAcceso();
    /** Correo electrónico del socio — usado para enviar alertas de seguridad. */
    String getCorreo();
    /** true si el socio tiene el flag de Jefe de Montaña activo. */
    Boolean getEsJefeMontana();
}
