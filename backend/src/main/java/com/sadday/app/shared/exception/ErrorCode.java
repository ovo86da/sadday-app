package com.sadday.app.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Códigos de error de negocio normalizados.
 * Cada código lleva su HTTP status correspondiente.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    INVALID_CREDENTIALS     (HttpStatus.UNAUTHORIZED,  "Credenciales incorrectas"),
    ACCOUNT_LOCKED          (HttpStatus.UNAUTHORIZED,  "Cuenta bloqueada. Contacta a un administrador"),
    TOKEN_INVALID           (HttpStatus.UNAUTHORIZED,  "Token inválido o expirado"),
    TOKEN_EXPIRED           (HttpStatus.UNAUTHORIZED,  "La sesión ha expirado"),
    MFA_REQUIRED            (HttpStatus.UNAUTHORIZED,  "Se requiere código de doble factor"),
    MFA_INVALID             (HttpStatus.UNAUTHORIZED,  "Código de doble factor inválido"),

    // Autorización
    ACCESS_DENIED           (HttpStatus.FORBIDDEN,     "No tienes permisos para esta acción"),
    INSUFICIENT_LEVEL       (HttpStatus.FORBIDDEN,     "Tu nivel técnico no cumple el mínimo requerido"),
    RISK_APPROVAL_REQUIRED  (HttpStatus.FORBIDDEN,     "Se requiere aprobación de Directivo y Jefe de Salida"),

    // Socios
    SOCIO_NOT_FOUND         (HttpStatus.NOT_FOUND,     "Socio no encontrado"),
    SOCIO_ALREADY_EXISTS    (HttpStatus.CONFLICT,      "Ya existe un socio con esa cédula o correo"),
    SOCIO_INHABILITADO      (HttpStatus.FORBIDDEN,     "El socio está inhabilitado"),
    ACCESO_SISTEMA_BLOQUEADO(HttpStatus.FORBIDDEN,     "Tu acceso al sistema ha sido suspendido. Contacta a un administrador"),

    // Salidas
    SALIDA_NOT_FOUND        (HttpStatus.NOT_FOUND,     "Salida no encontrada"),
    SALIDA_FULL             (HttpStatus.CONFLICT,      "La salida ha alcanzado su capacidad máxima"),
    SALIDA_NOT_PLANIFICADA  (HttpStatus.CONFLICT,      "La salida no admite cambios en su estado actual"),
    SALIDA_CANCELADA        (HttpStatus.CONFLICT,      "La salida está cancelada y no admite ninguna interacción"),
    ALREADY_INSCRIBED       (HttpStatus.CONFLICT,      "El socio ya está inscrito en esta salida"),

    // Rutas y montañas
    RUTA_NOT_FOUND          (HttpStatus.NOT_FOUND,     "Ruta no encontrada"),
    MOUNTAIN_NOT_FOUND      (HttpStatus.NOT_FOUND,     "Montaña no encontrada"),
    RUTA_NOT_APPROVED       (HttpStatus.CONFLICT,      "La ruta no está aprobada"),

    // Informes
    INFORME_NOT_FOUND          (HttpStatus.NOT_FOUND,  "Informe no encontrado"),
    INFORME_ALREADY_EXISTS     (HttpStatus.CONFLICT,   "Ya existe un informe para esta salida"),
    INFORME_VALIDATED          (HttpStatus.CONFLICT,   "El informe ya fue validado y no puede modificarse"),
    INFORME_NO_VALIDADO        (HttpStatus.FORBIDDEN,  "El informe debe estar validado antes de poder descargar el PDF"),
    RECONOCIMIENTO_NOT_FOUND   (HttpStatus.NOT_FOUND,  "Reconocimiento no encontrado"),
    SOCIO_NOT_PARTICIPANT      (HttpStatus.CONFLICT,   "El socio no es participante de esta salida"),

    // API Keys (MCP)
    API_KEY_NOT_FOUND       (HttpStatus.NOT_FOUND,     "API Key no encontrada"),
    API_KEY_LIMIT_REACHED   (HttpStatus.valueOf(422), "Has alcanzado el límite de 5 API keys activas. Revoca alguna antes de crear una nueva."),

    // Actas
    ACTA_NOT_FOUND          (HttpStatus.NOT_FOUND,     "Acta no encontrada"),

    // Documentos / Storage
    DOCUMENTO_NOT_FOUND     (HttpStatus.NOT_FOUND,     "Documento no encontrado"),
    DOCUMENTO_NO_GENERADO   (HttpStatus.CONFLICT,      "El PDF aún no ha sido generado"),
    RUTA_DOCUMENTO_NOT_FOUND(HttpStatus.NOT_FOUND,     "Documento de ruta no encontrado"),
    INVALID_FILE_TYPE       (HttpStatus.BAD_REQUEST,   "Tipo de archivo no permitido. Solo se aceptan: pdf, doc, docx, xls, xlsx"),
    FILE_TOO_LARGE          (HttpStatus.BAD_REQUEST,   "El archivo supera el tamaño máximo de 10 MB"),

    // General
    VALIDATION_ERROR        (HttpStatus.BAD_REQUEST,   "Error de validación en los datos enviados"),
    RESOURCE_NOT_FOUND      (HttpStatus.NOT_FOUND,     "Recurso no encontrado"),
    RESOURCE_CONFLICT       (HttpStatus.CONFLICT,      "Conflicto: el recurso ya existe"),
    INTERNAL_ERROR          (HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");

    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
