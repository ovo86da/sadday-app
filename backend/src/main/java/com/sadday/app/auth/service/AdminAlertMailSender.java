package com.sadday.app.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Envía alertas de infraestructura al administrador del sistema.
 *
 * <p>Si {@code sadday.admin.alert-email} no está configurado, todas las alertas
 * se omiten silenciosamente — el sistema no falla por falta de email de admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAlertMailSender {

    private final JavaMailSender mailSender;

    @Value("${sadday.mail.from}")
    private String mailFrom;

    @Value("${sadday.admin.alert-email:}")
    private String adminEmail;

    @Async
    public void sendGeoIpMissingAlert() {
        if (!hasAdminEmail()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(adminEmail);
            msg.setSubject("[Sadday] ALERTA — Base de datos GeoIP no encontrada");
            msg.setText("""
                    Alerta de infraestructura — Club Sadday

                    La base de datos de geolocalización (GeoLite2-City.mmdb) no fue encontrada en el servidor.

                    Esto significa que el contenedor geoip-updater nunca descargó el archivo, o que
                    la ruta configurada en GEOIP_DB_PATH es incorrecta.

                    Impacto: la detección de país inusual en el login está desactivada.

                    Acciones recomendadas:
                      1. Verificar que el contenedor geoip-updater esté corriendo:
                         docker-compose logs geoip-updater
                      2. Verificar que MAXMIND_ACCOUNT_ID y MAXMIND_LICENSE_KEY estén configurados.
                      3. Verificar que el volumen geoip-data esté montado correctamente.

                    Este mensaje fue generado automáticamente por el scheduler al arrancar.
                    """);
            mailSender.send(msg);
            log.info("Alerta GeoIP faltante enviada a {}", adminEmail);
        } catch (Exception e) {
            log.error("Error enviando alerta GeoIP faltante: {}", e.getMessage(), e);
        }
    }

    @Async
    public void sendGeoIpStaleAlert(long daysSinceUpdate) {
        if (!hasAdminEmail()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(adminEmail);
            msg.setSubject("[Sadday] ALERTA — Base de datos GeoIP desactualizada (%d días)".formatted(daysSinceUpdate));
            msg.setText("""
                    Alerta de infraestructura — Club Sadday

                    La base de datos de geolocalización (GeoLite2-City.mmdb) lleva %d días sin actualizarse.

                    MaxMind publica actualizaciones cada ~2 semanas. Una base de datos muy antigua puede
                    causar falsos positivos o negativos en la detección de país inusual.

                    Impacto: la detección de país inusual puede ser imprecisa para IPs recientemente reasignadas.

                    Acciones recomendadas:
                      1. Verificar que el contenedor geoip-updater esté corriendo:
                         docker-compose -f docker-compose.yml -f docker-compose.prod.yml logs geoip-updater
                      2. Verificar conectividad con updates.maxmind.com desde el servidor.
                      3. Verificar que la license key de MaxMind no haya expirado en:
                         https://www.maxmind.com/en/account

                    Este mensaje fue generado automáticamente por el scheduler.
                    """.formatted(daysSinceUpdate));
            mailSender.send(msg);
            log.info("Alerta GeoIP desactualizada ({} días) enviada a {}", daysSinceUpdate, adminEmail);
        } catch (Exception e) {
            log.error("Error enviando alerta GeoIP desactualizada: {}", e.getMessage(), e);
        }
    }

    private boolean hasAdminEmail() {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.debug("AdminAlertMailSender: ADMIN_ALERT_EMAIL no configurado — alerta omitida");
            return false;
        }
        return true;
    }
}
