package com.sadday.app.auth.service;

import com.sadday.app.auth.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static com.sadday.app.auth.service.SecurityEventService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityEventService")
class SecurityEventServiceTest {

    @Mock private SecurityEventRepository securityEventRepository;
    @Mock private GeoIpService            geoIpService;
    @Mock private SecurityAlertMailSender alertMailSender;
    @Mock private SecurityEventService    selfMock;

    private SecurityEventService service;

    private static final UUID   SOCIO_ID   = UUID.randomUUID();
    private static final UUID   SESSION_ID = UUID.randomUUID();
    private static final String USERNAME   = "juan.perez";
    private static final String IP         = "200.10.20.30";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0";
    private static final String DEVICE_ID  = "abc123device";
    private static final String CORREO     = "juan@club.com";
    private static final String NOMBRE     = "Juan Pérez";

    @BeforeEach
    void setUp() {
        service = new SecurityEventService(securityEventRepository, geoIpService, alertMailSender);
        // Inyectar el mock de self para interceptar llamadas a record() desde applyLoginRules()
        ReflectionTestUtils.setField(service, "self", selfMock);
    }

    // =========================================================================
    // applyLoginRules — Regla 1: nuevo dispositivo
    // =========================================================================

    @Nested
    @DisplayName("Regla 1 — nuevo dispositivo")
    class ReglaDispositivo {

        @BeforeEach
        void geoSetup() {
            when(geoIpService.lookup(IP))
                    .thenReturn(new GeoIpService.GeoLocation("EC", "Quito"));
        }

        @Test
        @DisplayName("dispositivo nuevo → registra NEW_DEVICE_LOGIN y envía alerta")
        void dispositivoNuevo() {
            when(securityEventRepository.existsKnownDevice(SOCIO_ID, DEVICE_ID)).thenReturn(false);

            service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                    DEVICE_ID, true, CORREO, NOMBRE);

            verify(selfMock).record(eq(NEW_DEVICE_LOGIN), eq(SOCIO_ID), eq(USERNAME),
                    eq(SESSION_ID), eq(IP), eq(USER_AGENT), eq(DEVICE_ID), anyMap());
            verify(alertMailSender).sendNewDeviceAlert(eq(CORREO), eq(NOMBRE),
                    anyString(), anyString(), anyString(), anyString(), eq(true));
        }

        @Test
        @DisplayName("dispositivo conocido → no registra NEW_DEVICE_LOGIN")
        void dispositivoConocido() {
            when(securityEventRepository.existsKnownDevice(SOCIO_ID, DEVICE_ID)).thenReturn(true);

            service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                    DEVICE_ID, true, CORREO, NOMBRE);

            verify(selfMock, never()).record(eq(NEW_DEVICE_LOGIN), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("deviceId null → omite chequeo de dispositivo")
        void deviceIdNull() {
            service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                    null, true, CORREO, NOMBRE);

            verify(securityEventRepository, never()).existsKnownDevice(any(), any());
            verify(selfMock, never()).record(eq(NEW_DEVICE_LOGIN), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("error en sendNewDeviceAlert no propaga excepción")
        void alertaFalla() {
            when(securityEventRepository.existsKnownDevice(SOCIO_ID, DEVICE_ID)).thenReturn(false);
            doThrow(new RuntimeException("SMTP error"))
                    .when(alertMailSender).sendNewDeviceAlert(any(), any(), any(), any(), any(), any(), anyBoolean());

            assertThatNoException().isThrownBy(() ->
                    service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                            DEVICE_ID, true, CORREO, NOMBRE));
        }
    }

    // =========================================================================
    // applyLoginRules — Regla 2: nuevo país
    // =========================================================================

    @Nested
    @DisplayName("Regla 2 — nuevo país")
    class ReglaPais {

        @BeforeEach
        void geoSetup() {
            when(geoIpService.lookup(IP))
                    .thenReturn(new GeoIpService.GeoLocation("EC", "Quito"));
            // Dispositivo conocido para no mezclar con regla 1
            when(securityEventRepository.existsKnownDevice(SOCIO_ID, DEVICE_ID)).thenReturn(true);
        }

        @Test
        @DisplayName("país nuevo sin MFA → requiresBlock=true y registra COUNTRY_CHALLENGE_ISSUED")
        void paisNuevoSinMfa() {
            when(securityEventRepository.existsKnownCountry(eq(SOCIO_ID), eq("EC"), any()))
                    .thenReturn(false);

            boolean result = service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                    DEVICE_ID, false, CORREO, NOMBRE);

            assertThat(result).isTrue();
            verify(selfMock).record(eq("COUNTRY_CHALLENGE_ISSUED"), eq(SOCIO_ID), eq(USERNAME),
                    eq(SESSION_ID), eq(IP), eq(USER_AGENT), eq(DEVICE_ID), anyMap());
        }

        @Test
        @DisplayName("país nuevo con MFA → requiresBlock=false y registra NEW_COUNTRY_LOGIN")
        void paisNuevoConMfa() {
            when(securityEventRepository.existsKnownCountry(eq(SOCIO_ID), eq("EC"), any()))
                    .thenReturn(false);

            boolean result = service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                    DEVICE_ID, true, CORREO, NOMBRE);

            assertThat(result).isFalse();
            verify(selfMock).record(eq(NEW_COUNTRY_LOGIN), eq(SOCIO_ID), eq(USERNAME),
                    eq(SESSION_ID), eq(IP), eq(USER_AGENT), eq(DEVICE_ID), anyMap());
            verify(alertMailSender).sendNewCountryAlert(eq(CORREO), eq(NOMBRE),
                    eq("EC"), eq("Quito"), anyString());
        }

        @Test
        @DisplayName("país conocido → no registra evento de país")
        void paisConocido() {
            when(securityEventRepository.existsKnownCountry(eq(SOCIO_ID), eq("EC"), any()))
                    .thenReturn(true);

            boolean result = service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                    DEVICE_ID, true, CORREO, NOMBRE);

            assertThat(result).isFalse();
            verify(selfMock, never()).record(eq(NEW_COUNTRY_LOGIN), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("geo null → no aplica regla de país")
        void geoNull() {
            when(geoIpService.lookup(IP)).thenReturn(null);

            boolean result = service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                    DEVICE_ID, false, CORREO, NOMBRE);

            assertThat(result).isFalse();
            verify(securityEventRepository, never()).existsKnownCountry(any(), any(), any());
        }

        @Test
        @DisplayName("error en sendNewCountryAlert no propaga excepción")
        void alertaPaisNoBloquea() {
            when(securityEventRepository.existsKnownCountry(eq(SOCIO_ID), eq("EC"), any()))
                    .thenReturn(false);
            doThrow(new RuntimeException("SMTP error"))
                    .when(alertMailSender).sendNewCountryAlert(any(), any(), any(), any(), any());

            assertThatNoException().isThrownBy(() ->
                    service.applyLoginRules(SOCIO_ID, USERNAME, SESSION_ID, IP, USER_AGENT,
                            DEVICE_ID, true, CORREO, NOMBRE));
        }
    }

    // =========================================================================
    // parseUa — detección de browser y OS
    // =========================================================================

    @Nested
    @DisplayName("parseUa")
    class ParseUa {

        @Test
        @DisplayName("null retorna Desconocido / Desconocido")
        void uaNull() {
            String[] r = service.parseUa(null);
            assertThat(r).containsExactly("Desconocido", "Desconocido");
        }

        @Test
        @DisplayName("detecta Chrome en Windows")
        void chrome() {
            String[] r = service.parseUa("Mozilla/5.0 (Windows NT 10.0) AppleWebKit Chrome/120.0");
            assertThat(r[0]).isEqualTo("Chrome");
            assertThat(r[1]).isEqualTo("Windows");
        }

        @Test
        @DisplayName("detecta Firefox en Linux")
        void firefox() {
            String[] r = service.parseUa("Mozilla/5.0 (X11; Linux x86_64) Firefox/121.0");
            assertThat(r[0]).isEqualTo("Firefox");
            assertThat(r[1]).isEqualTo("Linux");
        }

        @Test
        @DisplayName("detecta Edge")
        void edge() {
            String[] r = service.parseUa("Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Edg/120.0");
            assertThat(r[0]).isEqualTo("Edge");
        }

        @Test
        @DisplayName("detecta Safari en macOS")
        void safari() {
            String[] r = service.parseUa("Mozilla/5.0 (Macintosh; Intel Mac OS X 14) Safari/537.36");
            assertThat(r[0]).isEqualTo("Safari");
            assertThat(r[1]).isEqualTo("macOS");
        }

        @Test
        @DisplayName("detecta Opera")
        void opera() {
            String[] r = service.parseUa("Mozilla/5.0 OPR/106.0");
            assertThat(r[0]).isEqualTo("Opera");
        }

        @Test
        @DisplayName("detecta Internet Explorer")
        void ie() {
            String[] r = service.parseUa("Mozilla/5.0 (Windows NT 6.1; Trident/7.0; rv:11.0)");
            assertThat(r[0]).isEqualTo("Internet Explorer");
        }

        @Test
        @DisplayName("detecta Flutter App")
        void flutter() {
            String[] r = service.parseUa("Dart/3.0 Flutter/3.0");
            assertThat(r[0]).isEqualTo("Flutter App");
        }

        @Test
        @DisplayName("detecta Android")
        void android() {
            String[] r = service.parseUa("Mozilla/5.0 (Linux; Android 13) Chrome/120.0");
            assertThat(r[1]).isEqualTo("Android");
        }

        @Test
        @DisplayName("detecta iOS")
        void ios() {
            String[] r = service.parseUa("Mozilla/5.0 (iPhone; CPU iPhone OS 17) Safari/604.1");
            assertThat(r[1]).isEqualTo("iOS");
        }

        @Test
        @DisplayName("UA desconocido retorna Desconocido en ambos campos")
        void desconocido() {
            String[] r = service.parseUa("MiApp/1.0 CustomClient");
            assertThat(r).containsExactly("Desconocido", "Desconocido");
        }
    }
}
