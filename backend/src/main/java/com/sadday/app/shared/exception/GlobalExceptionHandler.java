package com.sadday.app.shared.exception;

import com.sadday.app.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones.
 *
 * <p>Principios de seguridad aplicados:
 * <ul>
 *   <li>Nunca se expone el stack trace en la respuesta HTTP.</li>
 *   <li>Los errores inesperados devuelven un mensaje genérico (no filtrar
 *       detalles de implementación al cliente).</li>
 *   <li>Los errores de seguridad (401/403) no revelan si el recurso existe.</li>
 *   <li>Los errores de validación sí devuelven detalles (son errores del cliente,
 *       no del servidor).</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Errores de negocio controlados: {@link BusinessException}.
     * Devuelve el HttpStatus y el mensaje definidos en el {@link ErrorCode}.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex,
                                                                     HttpServletRequest request) {
        if (ex.getErrorCode().getHttpStatus().is4xxClientError()) {
            log.debug("BusinessException [code={} | {} {}]: {}",
                    ex.getErrorCode(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        } else {
            log.warn("BusinessException [code={} | {} {}]: {}",
                    ex.getErrorCode(), request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        }
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Errores de validación de Bean Validation (@Valid).
     * Devuelve 422 con un mapa de campo → primer error.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> errores = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Valor inválido",
                        (first, second) -> first
                ));

        log.debug("ValidationError [{} {}]: {}", request.getMethod(), request.getRequestURI(), errores);
        return ResponseEntity
                .status(422)
                .body(ApiResponse.error("Error de validación", errores));
    }

    /**
     * Errores de autenticación de Spring Security.
     * Devuelve 401 con mensaje genérico (no revelar detalles).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex,
                                                                  HttpServletRequest request) {
        log.debug("AuthenticationException [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("No autenticado"));
    }

    /**
     * Errores de autorización de Spring Security (@PreAuthorize).
     * Devuelve 403 — nivel INFO para visibilidad de control de acceso.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex,
                                                                HttpServletRequest request) {
        log.info("AccessDenied [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Acceso denegado"));
    }

    /**
     * Cualquier otra excepción no controlada.
     * Devuelve 500 con mensaje genérico — el stack trace solo va a los logs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("UnhandledException [{} {}]", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Error interno del servidor"));
    }
}
