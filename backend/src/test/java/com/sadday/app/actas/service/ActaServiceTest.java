package com.sadday.app.actas.service;

import com.sadday.app.actas.dto.*;
import com.sadday.app.actas.entity.*;
import com.sadday.app.actas.repository.*;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
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
@DisplayName("ActaService — Unit Tests")
class ActaServiceTest {

    @Mock ActaReunionRepository       actaRepository;
    @Mock AsistenteReunionRepository  asistenteRepository;
    @Mock ActaInformeSalidaRepository informeLinkRepository;
    @Mock SocioRepository             socioRepository;
    @Mock InformeSalidaRepository     informeSalidaRepository;
    @Mock ActaMdParser                actaMdParser;

    @InjectMocks ActaService service;

    private final UUID ACTA_ID    = UUID.randomUUID();
    private final UUID SOCIO_ID   = UUID.randomUUID();
    private final UUID CURRENT_ID = UUID.randomUUID();

    private Socio currentUser;
    private ActaReunion actaSocios;
    private ActaReunion actaDirectiva;

    @BeforeEach
    void setUp() {
        currentUser = socio(CURRENT_ID);
        actaSocios    = acta(ACTA_ID, TipoActa.SOCIOS);
        actaDirectiva = acta(ACTA_ID, TipoActa.DIRECTIVA);

        when(socioRepository.findById(CURRENT_ID)).thenReturn(Optional.of(currentUser));
        when(asistenteRepository.findByActaId(any())).thenReturn(List.of());
        when(informeLinkRepository.findByActaId(any())).thenReturn(List.of());
        when(asistenteRepository.countByActaId(any())).thenReturn(0);

        setAuth("ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── listar ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        void adminPuedeFiltrarDirectiva() {
            when(actaRepository.buscarConFts(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(actaDirectiva)));

            var result = service.listar(null, TipoActa.DIRECTIVA, Pageable.unpaged());

            assertFalse(result.isEmpty());
        }

        @Test
        void socioRegularConTipoDirectiva_lanzaAccessDenied() {
            setAuth("ROLE_SOCIO");

            var ex = assertThrows(BusinessException.class,
                    () -> service.listar(null, TipoActa.DIRECTIVA, Pageable.unpaged()));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        void socioRegularSinFiltro_veActasSocios() {
            setAuth("ROLE_SOCIO");
            when(actaRepository.buscarConFts(eq("SOCIOS"), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(actaSocios)));

            var result = service.listar(null, null, Pageable.unpaged());

            assertFalse(result.isEmpty());
        }
    }

    // ── obtener ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("obtener")
    class Obtener {

        @Test
        void idInexistente_lanzaActaNotFound() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> service.obtener(ACTA_ID));
            assertEquals(ErrorCode.ACTA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void socioRegularVeActaSocios() {
            setAuth("ROLE_SOCIO");
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));

            var result = service.obtener(ACTA_ID);

            assertNotNull(result);
        }

        @Test
        void socioRegularNoVeActaDirectiva_lanzaAccessDenied() {
            setAuth("ROLE_SOCIO");
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaDirectiva));

            var ex = assertThrows(BusinessException.class, () -> service.obtener(ACTA_ID));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        void adminVeActaDirectiva() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaDirectiva));

            var result = service.obtener(ACTA_ID);

            assertNotNull(result);
        }
    }

    // ── crear ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("crear")
    class Crear {

        @Test
        void creadaPorNoExiste_lanzaSocioNotFound() {
            when(socioRepository.findById(CURRENT_ID)).thenReturn(Optional.empty());

            var req = minimalCreateRequest();
            var ex = assertThrows(BusinessException.class, () -> service.crear(req, CURRENT_ID));
            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void crearActaMinima_exito() {
            when(actaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var req = minimalCreateRequest();
            var result = service.crear(req, CURRENT_ID);

            assertNotNull(result);
            verify(actaRepository).save(any());
        }
    }

    // ── actualizar ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("actualizar")
    class Actualizar {

        @Test
        void idInexistente_lanzaActaNotFound() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.empty());

            var req = new UpdateActaRequest(TipoActa.SOCIOS, null, null, null, null, null,
                    null, null, null, null, null, null, null);

            var ex = assertThrows(BusinessException.class, () -> service.actualizar(ACTA_ID, req));
            assertEquals(ErrorCode.ACTA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void actualizarCampos_exito() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));

            var req = new UpdateActaRequest(TipoActa.SOCIOS, 5, LocalDate.now(), LocalTime.of(18, 0),
                    null, "Sala", null, null, null, null, null, null, null);

            var result = service.actualizar(ACTA_ID, req);

            assertNotNull(result);
            assertEquals(5, actaSocios.getNumeroReunion());
        }
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminar")
    class Eliminar {

        @Test
        void idInexistente_lanzaActaNotFound() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> service.eliminar(ACTA_ID));
            assertEquals(ErrorCode.ACTA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void actaExistente_elimina() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));

            service.eliminar(ACTA_ID);

            verify(actaRepository).delete(actaSocios);
        }
    }

    // ── agregarAsistente ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("agregarAsistente")
    class AgregarAsistente {

        @Test
        void actaNoEncontrada_lanzaActaNotFound() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.empty());

            var req = new AgregarAsistenteRequest(SOCIO_ID);
            var ex = assertThrows(BusinessException.class, () -> service.agregarAsistente(ACTA_ID, req));
            assertEquals(ErrorCode.ACTA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void asistenteDuplicado_lanzaAlreadyInscribed() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(asistenteRepository.existsByActaIdAndSocioId(ACTA_ID, SOCIO_ID)).thenReturn(true);

            var req = new AgregarAsistenteRequest(SOCIO_ID);
            var ex = assertThrows(BusinessException.class, () -> service.agregarAsistente(ACTA_ID, req));
            assertEquals(ErrorCode.ALREADY_INSCRIBED, ex.getErrorCode());
        }

        @Test
        void agregarNuevo_exito() {
            Socio asistente = socio(SOCIO_ID);
            AsistenteReunion ar = AsistenteReunion.builder()
                    .acta(actaSocios).socio(asistente)
                    .nombreRaw("Juan Pérez").build();

            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(asistenteRepository.existsByActaIdAndSocioId(ACTA_ID, SOCIO_ID)).thenReturn(false);
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(asistente));
            when(asistenteRepository.save(any())).thenReturn(ar);

            var result = service.agregarAsistente(ACTA_ID, new AgregarAsistenteRequest(SOCIO_ID));

            assertNotNull(result);
        }
    }

    // ── eliminarAsistente ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminarAsistente")
    class EliminarAsistente {

        @Test
        void asistenteNoEncontrado_lanzaResourceNotFound() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(asistenteRepository.findById(1L)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarAsistente(ACTA_ID, 1L));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void asistenteDeOtraActa_lanzaResourceNotFound() {
            UUID otraActaId = UUID.randomUUID();
            ActaReunion otraActa = acta(otraActaId, TipoActa.SOCIOS);
            AsistenteReunion ar = AsistenteReunion.builder().acta(otraActa).build();

            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(asistenteRepository.findById(1L)).thenReturn(Optional.of(ar));

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarAsistente(ACTA_ID, 1L));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void eliminarValido_exito() {
            AsistenteReunion ar = AsistenteReunion.builder().acta(actaSocios).build();

            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(asistenteRepository.findById(1L)).thenReturn(Optional.of(ar));

            service.eliminarAsistente(ACTA_ID, 1L);

            verify(asistenteRepository).delete(ar);
        }
    }

    // ── agregarInforme ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("agregarInforme")
    class AgregarInforme {

        @Test
        void informeDuplicado_lanzaAlreadyInscribed() {
            UUID informeId = UUID.randomUUID();
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(informeLinkRepository.existsByActaIdAndInformeId(ACTA_ID, informeId)).thenReturn(true);

            var req = new AgregarInformeActaRequest(informeId);
            var ex = assertThrows(BusinessException.class, () -> service.agregarInforme(ACTA_ID, req));
            assertEquals(ErrorCode.ALREADY_INSCRIBED, ex.getErrorCode());
        }

        @Test
        void informeNoEncontrado_lanzaInformeNotFound() {
            UUID informeId = UUID.randomUUID();
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(informeLinkRepository.existsByActaIdAndInformeId(ACTA_ID, informeId)).thenReturn(false);
            when(informeSalidaRepository.findById(informeId)).thenReturn(Optional.empty());

            var req = new AgregarInformeActaRequest(informeId);
            var ex = assertThrows(BusinessException.class, () -> service.agregarInforme(ACTA_ID, req));
            assertEquals(ErrorCode.INFORME_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── eliminarInforme ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminarInforme")
    class EliminarInforme {

        @Test
        void linkNoEncontrado_lanzaResourceNotFound() {
            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(informeLinkRepository.findById(1L)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarInforme(ACTA_ID, 1L));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void linkDeOtraActa_lanzaResourceNotFound() {
            UUID otraActaId = UUID.randomUUID();
            ActaInformeSalida link = ActaInformeSalida.builder()
                    .acta(acta(otraActaId, TipoActa.SOCIOS)).build();

            when(actaRepository.findById(ACTA_ID)).thenReturn(Optional.of(actaSocios));
            when(informeLinkRepository.findById(1L)).thenReturn(Optional.of(link));

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarInforme(ACTA_ID, 1L));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private ActaReunion acta(UUID id, TipoActa tipo) {
        ActaReunion a = new ActaReunion();
        a.setId(id);
        a.setTipoActa(tipo);
        a.setFecha(LocalDate.now());
        a.setCreadaPor(currentUser == null ? socio(UUID.randomUUID()) : currentUser);
        return a;
    }

    private CreateActaRequest minimalCreateRequest() {
        return new CreateActaRequest(
                TipoActa.SOCIOS, 1, LocalDate.now(), LocalTime.of(18, 0),
                null, "Sala", null, null, null, null, null, null, null, null, null
        );
    }
}
