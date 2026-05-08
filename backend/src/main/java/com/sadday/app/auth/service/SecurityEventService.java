package com.sadday.app.auth.service;

import com.sadday.app.auth.entity.SecurityEvent;
import com.sadday.app.auth.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registra eventos de seguridad en la tabla {@code security_events} y
 * aplica reglas automáticas de detección de anomalías.
 *
 * <p>Stateless: las reglas hacen queries a BD, funciona correctamente en multi-instancia.
 * <p>Usa {@code Propagation.REQUIRES_NEW} para que los eventos se persistan incluso
 * si la transacción del llamador hace rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventService {

    // Tipos de evento
    public static final String LOGIN_SUCCESS                = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED                 = "LOGIN_FAILED";
    public static final String LOGIN_BLOCKED                = "LOGIN_BLOCKED";
    public static final String NEW_DEVICE_LOGIN             = "NEW_DEVICE_LOGIN";
    public static final String NEW_COUNTRY_LOGIN            = "NEW_COUNTRY_LOGIN";
    public static final String PASSWORD_CHANGED             = "PASSWORD_CHANGED";
    public static final String REFRESH_TOKEN_REUSED         = "REFRESH_TOKEN_REUSED";
    public static final String SESSION_REVOKED              = "SESSION_REVOKED";
    public static final String SESSION_REVOKED_ALL          = "SESSION_REVOKED_ALL";
    public static final String SUSPICIOUS_ACTIVITY_REPORTED = "SUSPICIOUS_ACTIVITY_REPORTED";
    public static final String MFA_ENABLED                  = "MFA_ENABLED";
    public static final String MFA_DISABLED                 = "MFA_DISABLED";

    private final SecurityEventRepository  securityEventRepository;
    private final GeoIpService             geoIpService;
    private final SecurityAlertMailSender  alertMailSender;

    /**
     * Registra un evento de seguridad.
     *
     * @param eventType tipo de evento (constantes de esta clase)
     * @param socioId   puede ser null para eventos de login fallido con username desconocido
     * @param username  siempre presente para permitir correlación en logs
     * @param sessionId ID del RefreshToken asociado (puede ser null)
     * @param ip        IP del cliente
     * @param userAgent User-Agent del cliente
     * @param deviceId  SHA-256(ua+platform)[:32]
     * @param metadata  datos adicionales específicos del evento
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, UUID socioId, String username,
                       UUID sessionId, String ip, String userAgent,
                       String deviceId, Map<String, Object> metadata) {
        GeoIpService.GeoLocation geo = geoIpService.lookup(ip);

        SecurityEvent event = SecurityEvent.builder()
                .socioId(socioId)
                .username(username)
                .eventType(eventType)
                .ipAddress(ip)
                .countryCode(geo != null ? geo.countryCode() : null)
                .city(geo != null ? geo.city() : null)
                .userAgent(userAgent)
                .deviceId(deviceId)
                .sessionId(sessionId)
                .metadata(metadata)
                .build();

        securityEventRepository.save(event);
        log.debug("SecurityEvent registrado: {} socio={} ip={}", eventType, socioId, ip);
    }

    /**
     * Aplica reglas de seguridad tras un login exitoso:
     * - Regla 1: nuevo dispositivo → registro + email de alerta
     * - Regla 2: nuevo país → registro (+ bloqueo si no tiene MFA, gestionado por el llamador)
     *
     * @return {@code true} si el país es nuevo Y el socio no tiene MFA (requiere bloqueo del login)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean applyLoginRules(UUID socioId, String username, UUID sessionId,
                                   String ip, String userAgent, String deviceId,
                                   boolean hasMfa, String correo, String nombreCompleto) {

        GeoIpService.GeoLocation geo = geoIpService.lookup(ip);
        String countryCode = geo != null ? geo.countryCode() : null;
        String city        = geo != null ? geo.city() : null;
        boolean requiresBlock = false;

        // Regla 1 — nuevo dispositivo
        if (deviceId != null && !securityEventRepository.existsKnownDevice(socioId, deviceId)) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("device_id", deviceId);

            record(NEW_DEVICE_LOGIN, socioId, username, sessionId, ip, userAgent, deviceId, meta);

            String[] parsed = parseUa(userAgent);
            try {
                alertMailSender.sendNewDeviceAlert(correo, nombreCompleto,
                        parsed[0], parsed[1], city, countryCode, hasMfa);
            } catch (Exception e) {
                log.warn("No se pudo enviar alerta de nuevo dispositivo para socio={}: {}", socioId, e.getMessage(), e);
            }

            log.info("Nuevo dispositivo detectado para socio={} device={}", socioId, deviceId);
        }

        // Regla 2 — nuevo país
        if (countryCode != null) {
            OffsetDateTime since = OffsetDateTime.now().minusDays(90);
            if (!securityEventRepository.existsKnownCountry(socioId, countryCode, since)) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("new_country", countryCode);

                if (!hasMfa) {
                    requiresBlock = true;
                    log.warn("Nuevo país {} para socio={} sin MFA — login bloqueado", countryCode, socioId);
                    // Se registra un evento diferente para no marcar el país como "conocido" hasta que pase el reto
                    record("COUNTRY_CHALLENGE_ISSUED", socioId, username, sessionId, ip, userAgent, deviceId, meta);
                } else {
                    record(NEW_COUNTRY_LOGIN, socioId, username, sessionId, ip, userAgent, deviceId, meta);
                    String[] parsed = parseUa(userAgent);
                    try {
                        alertMailSender.sendNewCountryAlert(correo, nombreCompleto,
                                countryCode, city, parsed[0]);
                    } catch (Exception e) {
                        log.warn("No se pudo enviar alerta de nuevo país para socio={}: {}", socioId, e.getMessage(), e);
                    }
                }
            }
        }

        return requiresBlock;
    }

    /** Extrae [browser, os] del User-Agent con regex simples. */
    public String[] parseUa(String userAgent) {
        if (userAgent == null) return new String[]{"Desconocido", "Desconocido"};
        String ua  = userAgent;
        String browser = detectBrowser(ua);
        String os      = detectOs(ua);
        return new String[]{browser, os};
    }

    private String detectBrowser(String ua) {
        if (ua.contains("Edg/") || ua.contains("Edge/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera/")) return "Opera";
        if (ua.contains("Chrome/") && !ua.contains("Chromium")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/") && !ua.contains("Chrome")) return "Safari";
        if (ua.contains("MSIE") || ua.contains("Trident/")) return "Internet Explorer";
        if (ua.toLowerCase().contains("dart") || ua.toLowerCase().contains("flutter")) return "Flutter App";
        return "Desconocido";
    }

    private String detectOs(String ua) {
        if (ua.contains("Windows NT")) return "Windows";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return "Desconocido";
    }
}
