package com.sadday.app.mountains.service;

import com.sadday.app.mountains.entity.AccesoNivelCambioPendiente;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Avisa a la directiva de los cambios de umbrales (FR-026).
 *
 * <p>Se avisa al pedir, al aprobar y al rechazar: enterarse de que un umbral
 * cambió importa tanto como enterarse de que alguien quiere cambiarlo.
 *
 * <p>Va por correo y no por una bandeja dentro de la aplicación porque eso
 * último no existe — comprobado al implementarlo: {@code NotificacionService}
 * solo resuelve cumpleaños y no hay tabla de notificaciones. El correo es cómo
 * avisa hoy el sistema, siguiendo el patrón de los demás emisores.
 *
 * <p>La pantalla de umbrales enseña lo que está pendiente; el correo es lo que
 * hace que alguien vaya a mirarla.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CambioUmbralMailSender {

    private final JavaMailSender mailSender;
    private final SocioRepository socioRepository;

    @Value("${sadday.mail.from}")
    private String mailFrom;

    @Value("${sadday.mail.app-url:}")
    private String appUrl;

    @Async
    public void avisarSolicitud(AccesoNivelCambioPendiente c) {
        enviar("[Sadday] Cambio de umbrales esperando aprobación",
                """
                %s ha pedido cambiar los umbrales de acceso del nivel «%s».

                El cambio NO está aplicado: el umbral vigente sigue siendo el de
                antes hasta que otro directivo lo apruebe.

                Quien lo pidió no puede aprobarlo. Hace falta que lo revise otra
                persona de la directiva, y conviene mirar qué cambia antes de
                decidir: estos umbrales deciden a qué dificultad puede
                inscribirse cada socio.

                %s
                """.formatted(nombre(c), c.getNivelSocio().getNombre(), enlace()));
    }

    @Async
    public void avisarResolucion(AccesoNivelCambioPendiente c) {
        boolean aprobado = c.getEstado() == AccesoNivelCambioPendiente.Estado.APROBADO;
        enviar("[Sadday] Cambio de umbrales " + (aprobado ? "aplicado" : "rechazado"),
                """
                El cambio de umbrales del nivel «%s» que pidió %s ha sido %s por %s.

                %s
                %s
                """.formatted(
                        c.getNivelSocio().getNombre(),
                        nombre(c),
                        aprobado ? "APROBADO y aplicado" : "RECHAZADO",
                        c.getResueltoPor() == null ? "—"
                                : c.getResueltoPor().getNombre() + " " + c.getResueltoPor().getApellido(),
                        c.getMotivoRechazo() == null ? "" : "Motivo: " + c.getMotivoRechazo(),
                        enlace()));
    }

    private String nombre(AccesoNivelCambioPendiente c) {
        return c.getSolicitadoPor().getNombre() + " " + c.getSolicitadoPor().getApellido();
    }

    private String enlace() {
        return appUrl.isBlank() ? "" : "Revisarlo: " + appUrl + "/montanas";
    }

    /**
     * Manda el aviso a toda la directiva.
     *
     * <p>Si falla el envío se registra y se sigue: el cambio ya está guardado, y
     * perderlo porque el correo no salió sería peor que no avisar.
     */
    private void enviar(String asunto, String cuerpo) {
        List<String> destinos = socioRepository.findCorreosDeDirectivos();
        if (destinos.isEmpty()) {
            log.warn("Cambio de umbral sin avisar: no hay correos de directivos");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(destinos.toArray(String[]::new));
            msg.setSubject(asunto);
            msg.setText(cuerpo);
            mailSender.send(msg);
            log.info("Aviso de cambio de umbral enviado a {} directivos", destinos.size());
        } catch (Exception e) {
            log.error("No se pudo avisar del cambio de umbral: {}", e.getMessage());
        }
    }
}
