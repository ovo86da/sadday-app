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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SchedulerService — Unit Tests")
class SchedulerServiceTest {

    @Mock SocioRepository                  socioRepository;
    @Mock TipoSocioClubRepository          tipoSocioClubRepository;
    @Mock SalidaRepository                 salidaRepository;
    @Mock MfaChallengeTokenRepository      mfaChallengeTokenRepository;
    @Mock CountryChallengeTokenRepository  countryChallengeTokenRepository;
    @Mock RefreshTokenRepository           refreshTokenRepository;
    @Mock PasswordResetTokenRepository     passwordResetTokenRepository;
    @Mock EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock GeoIpService                     geoIpService;
    @Mock AdminAlertMailSender             adminAlertMailSender;

    @InjectMocks SchedulerService schedulerService;

    // ── promoverJuvenilesMayoresDeEdad ────────────────────────────────────────

    @Nested
    @DisplayName("promoverJuvenilesMayoresDeEdad")
    class PromoverJuveniles {

        @Test
        void socioActivoNoEncontrado_abortaJob() {
            when(tipoSocioClubRepository.findByNombre("Socio Activo")).thenReturn(Optional.empty());

            schedulerService.promoverJuvenilesMayoresDeEdad();

            verify(socioRepository, never()).findJuvenilesMayoresDeEdad();
        }

        @Test
        void sinJuvenilesMayores_noGuardaNada() {
            TipoSocioClub activo = mock(TipoSocioClub.class);
            when(tipoSocioClubRepository.findByNombre("Socio Activo")).thenReturn(Optional.of(activo));
            when(socioRepository.findJuvenilesMayoresDeEdad()).thenReturn(List.of());

            schedulerService.promoverJuvenilesMayoresDeEdad();

            verify(socioRepository, never()).saveAll(any());
        }

        @Test
        void hayJuvenilesMayores_actualizaTipoYGuarda() {
            TipoSocioClub activo = mock(TipoSocioClub.class);
            when(activo.getNombre()).thenReturn("Socio Activo");
            when(tipoSocioClubRepository.findByNombre("Socio Activo")).thenReturn(Optional.of(activo));

            Socio juvenil = new Socio();
            when(socioRepository.findJuvenilesMayoresDeEdad()).thenReturn(List.of(juvenil));

            schedulerService.promoverJuvenilesMayoresDeEdad();

            verify(socioRepository).saveAll(List.of(juvenil));
            assertSame(activo, juvenil.getTipoSocio());
        }
    }

    // ── actualizarEstadoSalidas ───────────────────────────────────────────────

    @Nested
    @DisplayName("actualizarEstadoSalidas")
    class ActualizarEstados {

        @Test
        void sinSalidasQueActualizar_noGuardaNada() {
            when(salidaRepository.findSalidasParaIniciar(any(), any())).thenReturn(List.of());
            when(salidaRepository.findSalidasParaFinalizar(any(), any())).thenReturn(List.of());

            schedulerService.actualizarEstadoSalidasDiario();

            verify(salidaRepository, never()).saveAll(any());
        }

        @Test
        void salidaParaIniciar_cambiaAEnCurso() {
            Salida salida = new Salida();
            when(salidaRepository.findSalidasParaIniciar(eq(EstadoSalida.PLANIFICADA), any()))
                    .thenReturn(List.of(salida));
            when(salidaRepository.findSalidasParaFinalizar(any(), any())).thenReturn(List.of());

            schedulerService.actualizarEstadoSalidasDiario();

            assertEquals(EstadoSalida.EN_CURSO, salida.getEstado());
            verify(salidaRepository).saveAll(List.of(salida));
        }

        @Test
        void salidaParaFinalizar_cambiaAFinalizada() {
            Salida salida = new Salida();
            when(salidaRepository.findSalidasParaIniciar(any(), any())).thenReturn(List.of());
            when(salidaRepository.findSalidasParaFinalizar(any(), any())).thenReturn(List.of(salida));

            schedulerService.actualizarEstadoSalidasDiario();

            assertEquals(EstadoSalida.REALIZADA, salida.getEstado());
            verify(salidaRepository).saveAll(List.of(salida));
        }

        @Test
        void alArrancar_llamaDoActualizarEstados() {
            when(salidaRepository.findSalidasParaIniciar(any(), any())).thenReturn(List.of());
            when(salidaRepository.findSalidasParaFinalizar(any(), any())).thenReturn(List.of());

            schedulerService.actualizarEstadoSalidasAlArrancar();

            verify(salidaRepository).findSalidasParaIniciar(any(), any());
        }
    }

    // ── limpiarTokens ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("limpiarTokens")
    class LimpiarTokens {

        @Test
        void eliminaTokensDeTodasLasTablas() {
            when(mfaChallengeTokenRepository.deleteExpired(any())).thenReturn(2);
            when(countryChallengeTokenRepository.deleteExpired(any())).thenReturn(1);
            when(refreshTokenRepository.deleteExpiredAndRevoked(any())).thenReturn(3);
            when(passwordResetTokenRepository.deleteExpiredAndUsed(any())).thenReturn(0);
            when(emailVerificationTokenRepository.deleteExpiredAndUsed(any())).thenReturn(1);

            schedulerService.limpiarTokensExpirados();

            verify(mfaChallengeTokenRepository).deleteExpired(any());
            verify(countryChallengeTokenRepository).deleteExpired(any());
            verify(refreshTokenRepository).deleteExpiredAndRevoked(any());
            verify(passwordResetTokenRepository).deleteExpiredAndUsed(any());
            verify(emailVerificationTokenRepository).deleteExpiredAndUsed(any());
        }

        @Test
        void sinTokensExpirados_noLanzaExcepcion() {
            when(mfaChallengeTokenRepository.deleteExpired(any())).thenReturn(0);
            when(countryChallengeTokenRepository.deleteExpired(any())).thenReturn(0);
            when(refreshTokenRepository.deleteExpiredAndRevoked(any())).thenReturn(0);
            when(passwordResetTokenRepository.deleteExpiredAndUsed(any())).thenReturn(0);
            when(emailVerificationTokenRepository.deleteExpiredAndUsed(any())).thenReturn(0);

            schedulerService.limpiarTokensAlArrancar();

            verify(mfaChallengeTokenRepository).deleteExpired(any());
        }
    }

    // ── verificarFrescuraGeoIp ────────────────────────────────────────────────

    @Nested
    @DisplayName("verificarFrescuraGeoIp")
    class VerificarGeoIp {

        @Test
        void geoIpNoConfigurado_noEnviaAlerta() {
            when(geoIpService.isConfigured()).thenReturn(false);

            schedulerService.verificarFrescuraGeoIp();

            verify(adminAlertMailSender, never()).sendGeoIpMissingAlert();
            verify(adminAlertMailSender, never()).sendGeoIpStaleAlert(anyLong());
        }

        @Test
        void archivoNoEncontrado_enviaAlertaMissing() {
            when(geoIpService.isConfigured()).thenReturn(true);
            when(geoIpService.getLastModified()).thenReturn(Optional.empty());

            schedulerService.verificarFrescuraGeoIp();

            verify(adminAlertMailSender).sendGeoIpMissingAlert();
        }

        @Test
        void archivoDeMasde14Dias_enviaAlertaStale() {
            when(geoIpService.isConfigured()).thenReturn(true);
            Instant old = Instant.now().minus(15, ChronoUnit.DAYS);
            when(geoIpService.getLastModified()).thenReturn(Optional.of(old));

            schedulerService.verificarFrescuraGeoIp();

            verify(adminAlertMailSender).sendGeoIpStaleAlert(anyLong());
        }

        @Test
        void archivoReciente_noEnviaAlerta() {
            when(geoIpService.isConfigured()).thenReturn(true);
            Instant recent = Instant.now().minus(3, ChronoUnit.DAYS);
            when(geoIpService.getLastModified()).thenReturn(Optional.of(recent));

            schedulerService.verificarFrescuraGeoIp();

            verify(adminAlertMailSender, never()).sendGeoIpMissingAlert();
            verify(adminAlertMailSender, never()).sendGeoIpStaleAlert(anyLong());
        }

        @Test
        void ejecutarCheckAhora_delegaADoVerificar() {
            when(geoIpService.isConfigured()).thenReturn(false);

            schedulerService.ejecutarCheckGeoIpAhora();

            verify(geoIpService).isConfigured();
        }

        @Test
        void alArrancar_ejecutaCheckGeoIp() {
            when(geoIpService.isConfigured()).thenReturn(false);

            schedulerService.verificarFrescuraGeoIpAlArrancar();

            verify(geoIpService).isConfigured();
        }
    }
}
