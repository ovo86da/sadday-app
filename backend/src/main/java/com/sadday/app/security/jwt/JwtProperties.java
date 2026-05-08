package com.sadday.app.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades JWT leídas desde application.yml (prefijo: sadday.jwt).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sadday.jwt")
public class JwtProperties {

    /** Ruta al archivo PEM de la clave privada RSA (para firmar tokens). */
    private String privateKeyLocation;

    /** Ruta al archivo PEM de la clave pública RSA (para verificar tokens). */
    private String publicKeyLocation;

    /** Emisor (iss) del JWT. */
    private String issuer = "sadday-app";

    /** Duración del access token en segundos (por defecto 15 min). */
    private long accessTokenExpirationSeconds = 900;

    /** Duración del refresh token en segundos (por defecto 30 días). */
    private long refreshTokenExpirationSeconds = 2_592_000;
}
