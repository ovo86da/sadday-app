package com.sadday.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de autenticación leídas desde application.yml (prefijo: sadday.auth).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sadday.auth")
public class AuthProperties {

    /** Expiración del token de recuperación de contraseña en minutos (default: 15). */
    private int passwordResetTokenExpiryMinutes = 15;

    /** Expiración del token de verificación de email en horas (default: 72). */
    private int emailVerificationTokenExpiryHours = 72;

    /**
     * Si true, la cookie de refresh token se emite con el atributo Secure (solo HTTPS).
     * Debe ser true en producción y false en desarrollo local (sin HTTPS).
     */
    private boolean cookieSecure = true;
}
