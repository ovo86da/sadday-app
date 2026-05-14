package com.sadday.app.informes.service;

import com.sadday.app.informes.dto.AgregarReconocimientoRequest;
import com.sadday.app.informes.entity.InformeSalida;
import com.sadday.app.informes.entity.InformeSalidaReconocimiento;
import com.sadday.app.informes.entity.TipoReconocimiento;
import com.sadday.app.informes.repository.InformeSalidaReconocimientoRepository;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.mountains.repository.ContactoRepository;
import com.sadday.app.mountains.repository.ContactoRutaRepository;
import com.sadday.app.salidas.entity.EstadoSalida;
import com.sadday.app.salidas.entity.Salida;
import com.sadday.app.salidas.repository.DignidadRepository;
import com.sadday.app.salidas.repository.SalidaParticipanteDignidadRepository;
import com.sadday.app.salidas.repository.SalidaParticipanteRepository;
import com.sadday.app.salidas.repository.SalidaRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InformeService — Unit Tests")
class InformeServiceTest {

    @Mock InformeSalidaRepository                informeRepository;
    @Mock InformeSalidaReconocimientoRepository  reconocimientoRepository;
    @Mock SalidaRepository                       salidaRepository;
    @Mock SalidaParticipanteRepository           participanteRepository;
    @Mock SalidaParticipanteDignidadRepository   dignidadAsignadaRepository;
    @Mock DignidadRepository                     dignidadRepository;
    @Mock ContactoRepository                     contactoRepository;
    @Mock ContactoRutaRepository                 contactoRutaRepository;
    @Mock SocioRepository                        socioRepository;
    @Mock EntityManager                          entityManager;

    @InjectMocks InformeService service;

    private final UUID SALIDA_ID  = UUID.randomUUID();
    private final UUID SOCIO_ID   = UUID.randomUUID();
    private final UUID CURRENT_ID = UUID.randomUUID();

    private Socio currentUser;

    @BeforeEach
    void setUp() {
        currentUser = socio(CURRENT_ID);
        when(socioRepository.findById(CURRENT_ID)).thenReturn(Optional.of(currentUser));
        when(reconocimientoRepository.findByInformeId(any())).thenReturn(List.of());
        setAuth("ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── obtener ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("obtener")
    class Obtener {

        @Test
        void salidaNoEncontrada_lanzaSalidaNotFound() {
            when(salidaRepository.existsById(SALIDA_ID)).thenReturn(false);

            var ex = assertThrows(BusinessException.class, () -> service.obtener(SALIDA_ID));
            assertEquals(ErrorCode.SALIDA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void sinInforme_devuelveEmpty() {
            when(salidaRepository.existsById(SALIDA_ID)).thenReturn(true);
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.empty());

            var result = service.obtener(SALIDA_ID);

            assertTrue(result.isEmpty());
        }

        @Test
        void conInforme_devuelveResponse() {
            InformeSalida informe = informeConSalida(SALIDA_ID);
            when(salidaRepository.existsById(SALIDA_ID)).thenReturn(true);
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.of(informe));

            var result = service.obtener(SALIDA_ID);

            assertTrue(result.isPresent());
        }
    }

    // ── validar ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validar")
    class Validar {

        @Test
        void informeNoEncontrado_lanzaInformeNotFound() {
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> service.validar(SALIDA_ID, CURRENT_ID));
            assertEquals(ErrorCode.INFORME_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void informeYaValidado_lanzaInformeValidated() {
            InformeSalida informe = informeConSalida(SALIDA_ID);
            informe.setValidadoPor(currentUser);
            informe.setValidadoEn(java.time.LocalDateTime.now().minusDays(1));
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.of(informe));

            var ex = assertThrows(BusinessException.class, () -> service.validar(SALIDA_ID, CURRENT_ID));
            assertEquals(ErrorCode.INFORME_VALIDATED, ex.getErrorCode());
        }

        @Test
        void validador_noEncontrado_lanzaSocioNotFound() {
            InformeSalida informe = informeConSalida(SALIDA_ID);
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.of(informe));
            when(socioRepository.findById(CURRENT_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> service.validar(SALIDA_ID, CURRENT_ID));
            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void validar_exito() {
            InformeSalida informe = informeConSalida(SALIDA_ID);
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.of(informe));

            service.validar(SALIDA_ID, CURRENT_ID);

            assertEquals(currentUser, informe.getValidadoPor());
            assertNotNull(informe.getValidadoEn());
        }
    }

    // ── agregarReconocimiento ─────────────────────────────────────────────────

    @Nested
    @DisplayName("agregarReconocimiento")
    class AgregarReconocimiento {

        @Test
        void informeNoEncontrado_lanzaInformeNotFound() {
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.empty());

            var req = new AgregarReconocimientoRequest(SOCIO_ID, TipoReconocimiento.DESTACADO, "Buen trabajo");
            var ex = assertThrows(BusinessException.class,
                    () -> service.agregarReconocimiento(SALIDA_ID, req, CURRENT_ID));
            assertEquals(ErrorCode.INFORME_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void socioNoParticipante_lanzaSocioNotParticipant() {
            InformeSalida informe = informeConSalida(SALIDA_ID);
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.of(informe));
            when(dignidadAsignadaRepository.countBySalidaSocioYDignidad(any(), any(), any()))
                    .thenReturn(1L);
            when(participanteRepository.existsBySalidaIdAndSocioId(SALIDA_ID, SOCIO_ID))
                    .thenReturn(false);

            var req = new AgregarReconocimientoRequest(SOCIO_ID, TipoReconocimiento.DESTACADO, "Buen trabajo");
            var ex = assertThrows(BusinessException.class,
                    () -> service.agregarReconocimiento(SALIDA_ID, req, CURRENT_ID));
            assertEquals(ErrorCode.SOCIO_NOT_PARTICIPANT, ex.getErrorCode());
        }
    }

    // ── eliminarReconocimiento ────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminarReconocimiento")
    class EliminarReconocimiento {

        @Test
        void informeNoEncontrado_lanzaInformeNotFound() {
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarReconocimiento(SALIDA_ID, 1L, CURRENT_ID));
            assertEquals(ErrorCode.INFORME_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void reconocimientoNoEncontrado_lanzaNotFound() {
            InformeSalida informe = informeConSalida(SALIDA_ID);
            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.of(informe));
            when(dignidadAsignadaRepository.countBySalidaSocioYDignidad(any(), any(), any()))
                    .thenReturn(1L);
            when(reconocimientoRepository.findById(1L)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarReconocimiento(SALIDA_ID, 1L, CURRENT_ID));
            assertEquals(ErrorCode.RECONOCIMIENTO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void reconocimientoDeOtroInforme_lanzaNotFound() {
            InformeSalida informe = informeConSalida(SALIDA_ID);
            InformeSalida otroInforme = informeConSalida(UUID.randomUUID());
            InformeSalidaReconocimiento rec = new InformeSalidaReconocimiento();
            rec.setInforme(otroInforme);

            when(informeRepository.findBySalidaId(SALIDA_ID)).thenReturn(Optional.of(informe));
            when(dignidadAsignadaRepository.countBySalidaSocioYDignidad(any(), any(), any()))
                    .thenReturn(1L);
            when(reconocimientoRepository.findById(1L)).thenReturn(Optional.of(rec));

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarReconocimiento(SALIDA_ID, 1L, CURRENT_ID));
            assertEquals(ErrorCode.RECONOCIMIENTO_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── obtenerPendientesJefe ─────────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerPendientesJefe")
    class ObtenerPendientesJefe {

        @Test
        void sinPendientes_devuelveListaVacia() {
            when(informeRepository.findSalidasPendientesJefe(any())).thenReturn(List.of());

            var result = service.obtenerPendientesJefe(SOCIO_ID);

            assertTrue(result.isEmpty());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void setAuth(String role) {
        var auth = new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }

    private Socio socio(UUID id) {
        Socio s = new Socio();
        s.setId(id);
        s.setNombre("Juan");
        s.setApellido("Pérez");
        return s;
    }

    private InformeSalida informeConSalida(UUID salidaId) {
        Salida salida = Salida.builder()
                .id(salidaId)
                .nombre("Salida Test")
                .fechaInicio(LocalDate.of(2025, 6, 1))
                .horaEncuentroClub(LocalTime.of(4, 0))
                .fechaFin(LocalDate.of(2025, 6, 2))
                .estado(EstadoSalida.REALIZADA)
                .creadoPor(currentUser)
                .build();

        InformeSalida informe = new InformeSalida();
        informe.setId(UUID.randomUUID());
        informe.setSalida(salida);
        informe.setSeRealizo(true);
        informe.setAlquiloGuia(false);
        informe.setAlquiloRefugio(false);
        informe.setAcampo(false);
        return informe;
    }
}
