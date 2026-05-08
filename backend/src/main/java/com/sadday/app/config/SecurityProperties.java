package com.sadday.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Propiedades de seguridad leídas desde application.yml (prefijo: sadday.security).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sadday.security")
public class SecurityProperties {

    /** Número máximo de intentos de login fallidos antes de bloquear la cuenta (default: 3). */
    private int maxLoginAttempts = 3;

    /** Duración del bloqueo de cuenta en horas (default: 24). */
    private int lockoutDurationHours = 24;

    /**
     * Clave AES-256 en Base64 (exactamente 32 bytes) para cifrar TOTP secrets en reposo.
     * Generar con: openssl rand -base64 32
     */
    private String totpEncryptionKey;

    /**
     * CIDRs de proxies inversos confiables (Nginx, Cloudflare, etc.).
     * CF-Connecting-IP y X-Forwarded-For solo se leen si getRemoteAddr() pertenece a uno de estos rangos.
     * Default: solo localhost para desarrollo.
     */
    private List<String> trustedProxyCidrs = List.of("127.0.0.1/32", "::1/128");
}
