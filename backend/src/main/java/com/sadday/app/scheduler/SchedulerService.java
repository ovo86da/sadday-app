package com.sadday.app.scheduler;

import com.sadday.app.auth.repository.CountryChallengeTokenRepository;
import com.sadday.app.auth.repository.EmailVerificationTokenRepository;
import com.sadday.app.auth.repository.MfaChallengeTokenRepository;
import com.sadday.app.auth.repository.PasswordResetTokenRepository;
import com.sadday.app.auth.repository.RefreshTokenRepository;
import com.sadday.app.auth.service.AdminAlertMailSender;
import com.sadday.app.auth.service.GeoIpService;
import com.sadday.app.salidas.entity.EstadoSalida;
import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.salidas.repository.SalidaRepository;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.entity.TipoSocioClub;
import com.sadday.app.socios.repository.SocioRepository;
import com.sadday.app.socios.repository.TipoSocioClubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Tareas programadas del sistema.
 *
 * <p>Jobs:
 * <ul>
 *   <li>{@link #promoverJuvenilesMayoresDeEdad()} — convierte socios Juvenil que
 *       ya cumplieron 18 años a tipo "Socio Activo". Se ejecuta diariamente a las 00:01.</li>
 *   <li>{@link #actualizarEstadoSalidasDiario()} — transiciona estados de salidas según fechas:
 *       PLANIFICADA → EN_CURSO cuando inicia, EN_CURSO/PLANIFICADA → REALIZADA cuando termina.
 *       Se ejecuta diariamente a las 00:02 y también al arrancar la aplicación.</li>
 *   <li>{@link #limpiarTokensExpirados()} — elimina tokens expirados/usados de las 4 tablas de
 *       tokens de autenticación, previniendo el crecimiento ilimitado de la BD (SEC-09).
 *       Se ejecuta cada hora.</li>
 *   <li>{@link #verificarFrescuraGeoIp()} — comprueba que el archivo GeoLite2-City.mmdb exista
 *       y haya sido actualizado en los últimos 14 días. Envía email al admin si no.
 *       Se ejecuta cada lunes a las 09:00 y también al arrancar la aplicación.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final SocioRepository                   socioRepository;
    private final TipoSocioClubRepository           tipoSocioClubRepository;
    private final SalidaRepository                  salidaRepository;

    // Repositorios de tokens de autenticación (para limpieza periódica)
    private final MfaChallengeTokenRepository        mfaChallengeTokenRepository;
    private final CountryChallengeTokenRepository    countryChallengeTokenRepository;
    private final RefreshTokenRepository             refreshTokenRepository;
    private final PasswordResetTokenRepository       passwordResetTokenRepository;
    private final EmailVerificationTokenRepository   emailVerificationTokenRepository;
    private final GeoIpService                       geoIpService;
    private final AdminAlertMailSender               adminAlertMailSender;

    // ── Socios ───────────────────────────────────────────────────────────────

    /**
     * Promueve automáticamente a los socios de tipo Juvenil que ya tienen 18 años
     * o más al tipo "Socio Activo".
     *
     * <p>Se ejecuta a las 00:01 todos los días (cron {@code "0 1 0 * * *"}).
     */
    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void promoverJuvenilesMayoresDeEdad() {
        TipoSocioClub socioActivo = tipoSocioClubRepository.findByNombre("Socio Activo")
                .orElse(null);

        if (socioActivo == null) {
            log.error("[Scheduler] No se encontró el tipo 'Socio Activo' en la BD. Job abortado.");
            return;
        }

        List<Socio> juveniles = socioRepository.findJuvenilesMayoresDeEdad();

        if (juveniles.isEmpty()) {
            log.debug("[Scheduler] promoverJuveniles: ningún juvenil mayor de edad hoy.");
            return;
        }

        juveniles.forEach(s -> s.setTipoSocio(socioActivo));
        socioRepository.saveAll(juveniles);

        log.info("[Scheduler] promoverJuveniles: {} socio(s) promovidos a Socio Activo.", juveniles.size());
    }

    // ── Salidas ──────────────────────────────────────────────────────────────

    /**
     * Actualiza el estado de las salidas según sus fechas al arrancar la aplicación.
     * Garantiza que salidas con fechas pasadas queden en el estado correcto sin esperar
     * a la ejecución nocturna del scheduler.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void actualizarEstadoSalidasAlArrancar() {
        log.info("[Scheduler] Sincronizando estados de salidas al arrancar...");
        doActualizarEstadoSalidas();
    }

    /**
     * Transiciona estados de salidas según sus fechas.
     * <ul>
     *   <li>PLANIFICADA → EN_CURSO cuando {@code fechaInicio <= hoy <= fechaFin}</li>
     *   <li>PLANIFICADA / EN_CURSO → REALIZADA cuando {@code fechaFin < hoy}</li>
     * </ul>
     * Se ejecuta a las 00:02 todos los días (cron {@code "0 2 0 * * *"}).
     */
    @Scheduled(cron = "0 2 0 * * *")
    @Transactional
    public void actualizarEstadoSalidasDiario() {
        doActualizarEstadoSalidas();
    }

    private void doActualizarEstadoSalidas() {
        LocalDate hoy = LocalDate.now();

        // PLANIFICADA → EN_CURSO
        List<Salida> paraIniciar = salidaRepository.findSalidasParaIniciar(EstadoSalida.PLANIFICADA, hoy);
        if (!paraIniciar.isEmpty()) {
            paraIniciar.forEach(s -> s.setEstado(EstadoSalida.EN_CURSO));
            salidaRepository.saveAll(paraIniciar);
            log.info("[Scheduler] actualizarEstados: {} salida(s) → EN_CURSO", paraIniciar.size());
        }

        // PLANIFICADA o EN_CURSO → REALIZADA
        List<Salida> paraFinalizar = salidaRepository.findSalidasParaFinalizar(
                List.of(EstadoSalida.PLANIFICADA, EstadoSalida.EN_CURSO), hoy);
        if (!paraFinalizar.isEmpty()) {
            paraFinalizar.forEach(s -> s.setEstado(EstadoSalida.REALIZADA));
            salidaRepository.saveAll(paraFinalizar);
            log.info("[Scheduler] actualizarEstados: {} salida(s) → REALIZADA", paraFinalizar.size());
        }

        if (paraIniciar.isEmpty() && paraFinalizar.isEmpty()) {
            log.debug("[Scheduler] actualizarEstados: ningún cambio de estado necesario.");
        }
    }

    // ── Limpieza de tokens de autenticación (SEC-09) ─────────────────────────

    /** Limpia tokens al arrancar para no esperar hasta la próxima hora en punto. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void limpiarTokensAlArrancar() {
        log.info("[Scheduler] Limpiando tokens expirados al arrancar...");
        doLimpiarTokens();
    }

    /**
     * Elimina tokens expirados o ya consumidos de las 4 tablas de tokens.
     *
     * <p>Previene el crecimiento ilimitado de la base de datos por acumulación de
     * tokens que ya no son válidos. Se ejecuta cada hora.
     *
     * <p>Tablas afectadas:
     * <ul>
     *   <li>{@code mfa_challenge_tokens} — tokens MFA de 5 minutos de vida</li>
     *   <li>{@code refresh_tokens} — tokens revocados o expirados</li>
     *   <li>{@code password_reset_tokens} — tokens usados o expirados</li>
     *   <li>{@code email_verification_tokens} — tokens usados o expirados</li>
     * </ul>
     */
    @Scheduled(cron = "0 0 * * * *")   // Cada hora en punto
    @Transactional
    public void limpiarTokensExpirados() {
        doLimpiarTokens();
    }

    // ── Verificación de frescura del GeoIP (alertas al admin) ────────────────

    private static final long GEOIP_STALE_DAYS = 14;

    /** Verifica frescura del GeoIP al arrancar — detecta problemas antes de la primera semana. */
    @EventListener(ApplicationReadyEvent.class)
    public void verificarFrescuraGeoIpAlArrancar() {
        doVerificarFrescuraGeoIp();
    }

    /** Verifica cada lunes a las 09:00 que el .mmdb siga fresco. */
    @Scheduled(cron = "0 0 9 * * MON")
    public void verificarFrescuraGeoIp() {
        doVerificarFrescuraGeoIp();
    }

    /** Disparo manual desde el endpoint de diagnóstico (usado en dev/test). */
    public void ejecutarCheckGeoIpAhora() {
        doVerificarFrescuraGeoIp();
    }

    private void doVerificarFrescuraGeoIp() {
        if (!geoIpService.isConfigured()) {
            log.debug("[Scheduler] verificarGeoIp: GEOIP_DB_PATH no configurado — check omitido.");
            return;
        }

        Optional<Instant> lastModified = geoIpService.getLastModified();

        if (lastModified.isEmpty()) {
            log.warn("[Scheduler] verificarGeoIp: archivo GeoLite2 no encontrado — enviando alerta al admin.");
            adminAlertMailSender.sendGeoIpMissingAlert();
            return;
        }

        long days = ChronoUnit.DAYS.between(lastModified.get(), Instant.now());
        if (days > GEOIP_STALE_DAYS) {
            log.warn("[Scheduler] verificarGeoIp: GeoLite2 lleva {} días sin actualizarse — enviando alerta al admin.", days);
            adminAlertMailSender.sendGeoIpStaleAlert(days);
        } else {
            log.debug("[Scheduler] verificarGeoIp: GeoLite2 actualizado hace {} días — OK.", days);
        }
    }

    private void doLimpiarTokens() {
        LocalDateTime now = LocalDateTime.now();

        int mfa     = mfaChallengeTokenRepository.deleteExpired(now);
        int country = countryChallengeTokenRepository.deleteExpired(now);
        int refresh = refreshTokenRepository.deleteExpiredAndRevoked(now);
        int reset   = passwordResetTokenRepository.deleteExpiredAndUsed(now);
        int email   = emailVerificationTokenRepository.deleteExpiredAndUsed(now);

        int total = mfa + country + refresh + reset + email;

        if (total > 0) {
            log.info("[Scheduler] limpiarTokens: eliminados {} tokens (mfa={}, country={}, refresh={}, reset={}, email={})",
                    total, mfa, country, refresh, reset, email);
        } else {
            log.debug("[Scheduler] limpiarTokens: no hay tokens expirados que limpiar.");
        }
    }
}

