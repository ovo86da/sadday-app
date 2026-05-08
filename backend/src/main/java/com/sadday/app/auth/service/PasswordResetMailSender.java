package com.sadday.app.auth.service;

import com.sadday.app.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Envía el email de recuperación de contraseña de forma asíncrona.
 *
 * <p>Separado de {@link PasswordResetService} porque @Async requiere un bean distinto
 * para que Spring aplique el proxy — la auto-invocación dentro del mismo bean
 * no pasa por el proxy y el método se ejecutaría de forma síncrona.
 *
 * <p>El envío asíncrono elimina la diferencia de tiempo observable entre
 * "correo registrado" (antes: lento por SMTP) y "correo no registrado" (antes: inmediato),
 * cerrando el timing attack de enumeración de correos (A4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailSender {

    private final JavaMailSender  mailSender;
    private final AuthProperties  authProperties;

    @Value("${sadday.mail.from}")
    private String mailFrom;

    @Value("${sadday.mail.app-url}")
    private String appUrl;

    @Async
    public void send(String correo, String rawToken) {
        try {
            String resetLink   = appUrl + "/reset-password?token=" + rawToken;
            int    expiryMins  = authProperties.getPasswordResetTokenExpiryMinutes();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(correo);
            message.setSubject("Club Sadday — Recuperación de contraseña");
            message.setText("""
                    Recibiste este correo porque solicitaste restablecer tu contraseña en el sistema del Club Sadday.

                    Haz clic en el siguiente enlace para continuar:
                    %s

                    Este enlace expira en %d minutos.

                    Si no solicitaste este cambio, puedes ignorar este correo. Tu contraseña no será modificada.
                    """.formatted(resetLink, expiryMins));

            mailSender.send(message);
            log.info("Email de recuperación enviado a: {}", correo);
        } catch (Exception e) {
            log.error("Error enviando email de recuperación a {}: {}", correo, e.getMessage(), e);
        }
    }
}
