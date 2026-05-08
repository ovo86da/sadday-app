package com.sadday.app.auth.dto;

/**
 * Respuesta del endpoint de inicio de configuración 2FA.
 *
 * <p>El cliente debe mostrar un QR generado a partir de {@code otpAuthUri},
 * o permitir la entrada manual del {@code base32Secret} en la app autenticadora.
 * Una vez escaneado, el usuario debe confirmar enviando un código válido a
 * {@code POST /api/v1/auth/mfa/confirm}.
 */
public record MfaSetupResponse(
        /** URI en formato otpauth:// para generar el QR code. */
        String otpAuthUri,
        /** Secret en Base32 para entrada manual en la app autenticadora. */
        String base32Secret
) {}
