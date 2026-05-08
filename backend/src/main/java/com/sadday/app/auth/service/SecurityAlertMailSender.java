package com.sadday.app.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Envía alertas de seguridad (nuevo dispositivo, nuevo país) de forma asíncrona.
 *
 * <p>Separado de {@link SecurityEventService} para que @Async funcione
 * correctamente vía proxy AOP (no auto-invocación).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAlertMailSender {

    private final JavaMailSender mailSender;

    @Value("${sadday.mail.from}")
    private String mailFrom;

    @Value("${sadday.mail.app-url}")
    private String appUrl;

    @Async
    public void sendNewDeviceAlert(String correo, String nombre, String browser, String os,
                                   String city, String country, boolean hasMfa) {
        try {
            String location = buildLocation(city, country);
            String mfaSection = hasMfa ? "" : """

                    Para proteger mejor tu cuenta, te recomendamos activar la autenticación de dos factores (2FA):
                    %s/perfil
                    """.formatted(appUrl);

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(correo);
            msg.setSubject("Club Sadday — Nuevo inicio de sesión desde un dispositivo desconocido");
            msg.setText("""
                    Hola %s,

                    Detectamos un nuevo inicio de sesión en tu cuenta desde un dispositivo no reconocido.

                    Dispositivo: %s (%s)
                    Ubicación: %s
                    Hora: %s

                    Si fuiste tú, puedes ignorar este mensaje.

                    Si NO fuiste tú, te recomendamos cerrar todas las sesiones de forma inmediata desde:
                    %s/perfil
                    %s
                    El equipo de Sadday
                    """.formatted(
                    nombre,
                    browser != null ? browser : "Desconocido",
                    os != null ? os : "Desconocido",
                    location,
                    java.time.OffsetDateTime.now().toString(),
                    appUrl,
                    mfaSection));

            mailSender.send(msg);
            log.info("Alerta nuevo dispositivo enviada a {}", correo);
        } catch (Exception e) {
            log.error("Error enviando alerta nuevo dispositivo a {}: {}", correo, e.getMessage(), e);
        }
    }

    @Async
    public void sendNewCountryAlert(String correo, String nombre, String country,
                                    String city, String browser) {
        try {
            String location = buildLocation(city, country);

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(correo);
            msg.setSubject("Club Sadday — Inicio de sesión desde un nuevo país");
            msg.setText("""
                    Hola %s,

                    Detectamos un inicio de sesión desde un país no visto anteriormente en tu cuenta.

                    País: %s
                    Ubicación: %s
                    Navegador: %s
                    Hora: %s

                    Si fuiste tú y estás viajando, puedes ignorar este mensaje.

                    Si NO fuiste tú, cierra todas las sesiones de inmediato desde:
                    %s/perfil

                    El equipo de Sadday
                    """.formatted(
                    nombre,
                    country != null ? country : "Desconocido",
                    location,
                    browser != null ? browser : "Desconocido",
                    java.time.OffsetDateTime.now().toString(),
                    appUrl));

            mailSender.send(msg);
            log.info("Alerta nuevo país enviada a {}", correo);
        } catch (Exception e) {
            log.error("Error enviando alerta nuevo país a {}: {}", correo, e.getMessage(), e);
        }
    }

    /**
     * Envía el código de verificación de país de forma <b>síncrona</b>.
     *
     * <p>A diferencia de las demás alertas, este envío es parte del flujo de login:
     * si falla, el login debe fallar con un error claro en lugar de completarse sin código.
     */
    public void sendCountryChallengeCode(String correo, String nombre, String code,
                                         String country, String city, String browser) {
        String location = buildLocation(city, country);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailFrom);
        msg.setTo(correo);
        msg.setSubject("Club Sadday — Código de verificación de acceso");
        msg.setText("""
                Hola %s,

                Detectamos un inicio de sesión desde un país no reconocido anteriormente en tu cuenta.

                País:      %s
                Ubicación: %s
                Navegador: %s
                Hora:      %s

                Para confirmar que eres tú, ingresa el siguiente código en la pantalla de verificación:

                    %s

                Este código expira en 15 minutos y solo puede usarse una vez.

                Si NO iniciaste esta sesión, ignora este mensaje — el intento fue bloqueado.

                El equipo de Sadday
                """.formatted(
                nombre,
                country != null ? country : "Desconocido",
                location,
                browser != null ? browser : "Desconocido",
                java.time.OffsetDateTime.now().toString(),
                code));

        mailSender.send(msg);
        log.info("Código de verificación de país enviado a {}", correo);
    }

    private String buildLocation(String city, String country) {
        if (city != null && country != null) return city + ", " + country;
        if (country != null) return country;
        if (city != null) return city;
        return "Ubicación desconocida";
    }
}
