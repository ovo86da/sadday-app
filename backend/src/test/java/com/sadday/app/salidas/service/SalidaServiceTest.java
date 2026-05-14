package com.sadday.app.salidas.service;

import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.repository.RutaRepository;
import com.sadday.app.mountains.service.RutaDocumentoService;
import com.sadday.app.salidas.dto.*;
import com.sadday.app.salidas.entity.*;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.entity.ConfiguracionSistema;
import com.sadday.app.salidas.repository.*;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.repository.ConfiguracionSistemaRepository;
import com.sadday.app.socios.entity.*;
import com.sadday.app.socios.repository.ClasificacionSocioRepository;
import com.sadday.app.socios.repository.SocioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SalidaService — Unit Tests")
class SalidaServiceTest {

    @Mock SalidaRepository                    salidaRepository;
    @Mock SalidaParticipanteRepository        participanteRepository;
    @Mock SalidaParticipanteDignidadRepository dignidadRepository;
    @Mock PublicoObjetivoRepository           publicoObjetivoRepo;
    @Mock FormatoSalidaRepository             formatoSalidaRepo;
    @Mock DignidadRepository                  dignidadRepo;
    @Mock RutaRepository                      rutaRepository;
    @Mock SocioRepository                     socioRepository;
    @Mock ClasificacionSocioRepository        clasifSocioRepo;
    @Mock ConfiguracionSistemaRepository      configRepo;
    @Mock InformeSalidaRepository             informeRepository;
    @Mock AuditService                        auditService;
    @Mock RutaDocumentoService                rutaDocumentoService;

    @InjectMocks SalidaService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── obtenerLookups ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerLookups")
    class ObtenerLookups {

        @Test
        void retornaCatalogosCombinados() {
            PublicoObjetivo po = PublicoObjetivo.builder().id("FAM").nombre("Familiar").build();
            FormatoSalida fs = FormatoSalida.builder().id("GRP").nombre("Grupal").build();
            Dignidad d = Dignidad.builder().id(1).nombre("Cronista").descripcion("desc").build();

            when(publicoObjetivoRepo.findAllByOrderByOrdenAsc()).thenReturn(List.of(po));
            when(formatoSalidaRepo.findAllByOrderByOrdenAsc()).thenReturn(List.of(fs));
            when(dignidadRepo.findAll()).thenReturn(List.of(d));

            SalidaLookupsResponse response = service.obtenerLookups();

            assertNotNull(response);
            assertEquals(1, response.publicosObjetivo().size());
            assertEquals(1, response.formatosSalida().size());
            assertEquals(1, response.dignidades().size());
            assertFalse(response.estadosSalida().isEmpty());
            assertFalse(response.estadosInscripcion().isEmpty());
        }
    }

    // ── verificarSolapamiento ─────────────────────────────────────────────────

    @Nested
    @DisplayName("verificarSolapamiento")
    class VerificarSolapamiento {

        @Test
        void sinSolapadas_retornaListaVacia() {
            when(salidaRepository.findSolapadas(any(), any(), any(), any())).thenReturn(List.of());

            List<SolapamientoResponse> result = service.verificarSolapamiento(
                    LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5), null);

            assertTrue(result.isEmpty());
        }

        @Test
        void conSolapadas_retornaLista() {
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            when(salidaRepository.findSolapadas(any(), any(), any(), any())).thenReturn(List.of(salida));

            List<SolapamientoResponse> result = service.verificarSolapamiento(
                    LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 5), null);

            assertEquals(1, result.size());
        }
    }

    // ── cambiarEstado ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cambiarEstado")
    class CambiarEstado {

        @Test
        void salidaNoEncontrada_lanzaNotFound() {
            UUID id = UUID.randomUUID();
            when(salidaRepository.findById(id)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.cambiarEstado(id, new CambiarEstadoSalidaRequest(EstadoSalida.EN_CURSO)));
            assertEquals(ErrorCode.SALIDA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void salidaEncontrada_actualizaEstado() {
            UUID id = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(id);
            when(salidaRepository.findById(id)).thenReturn(Optional.of(salida));

            service.cambiarEstado(id, new CambiarEstadoSalidaRequest(EstadoSalida.EN_CURSO));

            assertEquals(EstadoSalida.EN_CURSO, salida.getEstado());
        }
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminar")
    class Eliminar {

        @Test
        void salidaRealizada_lanzaError() {
            UUID id = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.REALIZADA);
            when(salidaRepository.findById(id)).thenReturn(Optional.of(salida));

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminar(id, new EliminarSalidaRequest("motivo"), UUID.randomUUID()));
            assertEquals(ErrorCode.SALIDA_NOT_PLANIFICADA, ex.getErrorCode());
        }

        @Test
        void salidaPlanificada_seMarcaComoEliminada() {
            UUID id = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            Socio actor = socioConId(actorId);
            when(salidaRepository.findById(id)).thenReturn(Optional.of(salida));
            when(socioRepository.findById(actorId)).thenReturn(Optional.of(actor));
            when(salidaRepository.save(any())).thenReturn(salida);

            service.eliminar(id, new EliminarSalidaRequest("motivo test"), actorId);

            assertTrue(salida.isEliminada());
            assertEquals("motivo test", salida.getMotivoEliminacion());
        }
    }

    // ── cancelar ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelar")
    class Cancelar {

        @Test
        void salidaYaCancelada_lanzaError() {
            UUID id = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.CANCELADA);
            when(salidaRepository.findById(id)).thenReturn(Optional.of(salida));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelar(id, new CancelarSalidaRequest("ya cancelada"), UUID.randomUUID()));
            assertEquals(ErrorCode.SALIDA_CANCELADA, ex.getErrorCode());
        }

        @Test
        void salidaRealizada_lanzaError() {
            UUID id = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.REALIZADA);
            when(salidaRepository.findById(id)).thenReturn(Optional.of(salida));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelar(id, new CancelarSalidaRequest("motivo"), UUID.randomUUID()));
            assertEquals(ErrorCode.SALIDA_NOT_PLANIFICADA, ex.getErrorCode());
        }

        @Test
        void salidaPlanificada_seCancela() {
            UUID id = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            Socio creadoPor = socioConId(actorId);
            Salida salida = salidaCompletaConEstado(EstadoSalida.PLANIFICADA, creadoPor);
            when(salidaRepository.findById(id)).thenReturn(Optional.of(salida));
            when(socioRepository.findById(actorId)).thenReturn(Optional.of(creadoPor));
            when(salidaRepository.save(any())).thenReturn(salida);
            when(participanteRepository.findBySalidaId(any())).thenReturn(List.of());
            when(informeRepository.findSalidaIdsWithInforme(any())).thenReturn(Set.of());

            SalidaResponse response = service.cancelar(id, new CancelarSalidaRequest("motivo test"), actorId);

            assertNotNull(response);
            assertEquals(EstadoSalida.CANCELADA, salida.getEstado());
            assertEquals("motivo test", salida.getMotivoCancelacion());
        }
    }

    // ── cancelarInscripcion ───────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelarInscripcion")
    class CancelarInscripcion {

        @Test
        void participanteNoEncontrado_lanzaNotFound() {
            when(participanteRepository.findById(1L)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelarInscripcion(UUID.randomUUID(), 1L, UUID.randomUUID()));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void otroSocioSinPrivilegios_lanzaAccessDenied() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            UUID propioId = UUID.randomUUID();
            UUID otroId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(propioId);
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, salida, EstadoInscripcion.INSCRITO);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelarInscripcion(salidaId, 1L, otroId));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        void estadoCancelado_lanzaValidationError() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, salida, EstadoInscripcion.CANCELADO);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelarInscripcion(salidaId, 1L, socioId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void estadoConfirmado_lanzaValidationError() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, salida, EstadoInscripcion.CONFIRMADO);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelarInscripcion(salidaId, 1L, socioId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void pendienteAprobacion_propioSocio_cancela() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.PENDIENTE_APROBACION);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepository.existsByParticipanteIdAndDignidad_Nombre(1L, "Jefe de Salida"))
                    .thenReturn(false);

            assertDoesNotThrow(() -> service.cancelarInscripcion(salidaId, 1L, socioId));
            verify(participanteRepository).delete(participante);
        }

        @Test
        void inscripcionesCerradas_sinPrivilegios_lanzaValidationError() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            salida.setInscripcionesCerradas(true);
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.INSCRITO);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelarInscripcion(salidaId, 1L, socioId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void estadoInscrito_fuera48h_lanzaValidationError() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            // Salida que comenzó ayer → ya pasaron las 48h
            Salida salida = Salida.builder()
                    .id(salidaId)
                    .nombre("Salida Test")
                    .fechaInicio(LocalDate.now().minusDays(1))
                    .horaEncuentroClub(LocalTime.of(4, 0))
                    .fechaFin(LocalDate.now())
                    .estado(EstadoSalida.EN_CURSO)
                    .build();
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.INSCRITO);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelarInscripcion(salidaId, 1L, socioId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void esJefeSalida_sinPrivilegios_lanzaValidationError() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.PENDIENTE_APROBACION);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepository.existsByParticipanteIdAndDignidad_Nombre(1L, "Jefe de Salida"))
                    .thenReturn(true);

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelarInscripcion(salidaId, 1L, socioId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void esJefeSalida_conPrivilegios_cancela() {
            setSecurityContext("ROLE_ADMIN");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.PENDIENTE_APROBACION);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepository.existsByParticipanteIdAndDignidad_Nombre(1L, "Jefe de Salida"))
                    .thenReturn(true);
            when(salidaRepository.save(any())).thenReturn(salida);

            assertDoesNotThrow(() -> service.cancelarInscripcion(salidaId, 1L, socioId));
            verify(participanteRepository).delete(participante);
        }
    }

    // ── designarJefeSalida ────────────────────────────────────────────────────

    @Nested
    @DisplayName("designarJefeSalida")
    class DesignarJefeSalida {

        @Test
        void salidaNoEncontrada_lanzaSalidaNotFound() {
            UUID salidaId = UUID.randomUUID();
            when(salidaRepository.findById(salidaId)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.designarJefeSalida(salidaId, 1L));
            assertEquals(ErrorCode.SALIDA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void dignidadNoEncontrada_lanzaResourceNotFound() {
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, salida, EstadoInscripcion.INSCRITO);

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepo.findByNombre("Jefe de Salida")).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.designarJefeSalida(salidaId, 1L));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void designa_jefeExistente_reemplazaYRetornaResponse() {
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, salida, EstadoInscripcion.INSCRITO);

            Dignidad jefeDignidad = mock(Dignidad.class);
            when(jefeDignidad.getId()).thenReturn(10);
            when(jefeDignidad.getNombre()).thenReturn("Jefe de Salida");

            // Otro participante que ya era jefe (diferente id)
            SalidaParticipante otroParticipante = participanteConSocioYSalida(2L, socioConId(UUID.randomUUID()), salida, EstadoInscripcion.INSCRITO);
            SalidaParticipanteDignidad dignidadAnterior = SalidaParticipanteDignidad.builder()
                    .participante(otroParticipante).dignidad(jefeDignidad).build();

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepo.findByNombre("Jefe de Salida")).thenReturn(Optional.of(jefeDignidad));
            when(dignidadRepository.findByParticipante_Salida_IdAndDignidad_Nombre(salidaId, "Jefe de Salida"))
                    .thenReturn(List.of(dignidadAnterior));
            when(dignidadRepository.existsByParticipanteIdAndDignidadId(1L, 10)).thenReturn(false);
            when(dignidadRepository.findByParticipanteId(1L)).thenReturn(List.of());

            // No lanza excepción
            assertDoesNotThrow(() -> service.designarJefeSalida(salidaId, 1L));
            verify(dignidadRepository).delete(dignidadAnterior);
        }
    }

    // ── cambiarEstadoInscripcion ──────────────────────────────────────────────

    @Nested
    @DisplayName("cambiarEstadoInscripcion")
    class CambiarEstadoInscripcion {

        @Test
        void sinRolPrivilegiado_sinJefeSalida_lanzaAccessDenied() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();

            var ex = assertThrows(BusinessException.class, () ->
                    service.cambiarEstadoInscripcion(salidaId, 1L,
                            new CambiarEstadoInscripcionRequest(EstadoInscripcion.CONFIRMADO)));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        void transicionInvalida_lanzaValidationError() {
            setSecurityContext("ROLE_ADMIN");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.CONFIRMADO);

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class, () ->
                    service.cambiarEstadoInscripcion(salidaId, 1L,
                            new CambiarEstadoInscripcionRequest(EstadoInscripcion.INSCRITO)));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void transicionValida_adminConfirmaInscrito() {
            setSecurityContext("ROLE_ADMIN");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(socioId);
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.INSCRITO);

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(participanteRepository.save(any())).thenReturn(participante);
            when(dignidadRepository.findByParticipanteId(1L)).thenReturn(List.of());

            ParticipanteResponse response = service.cambiarEstadoInscripcion(salidaId, 1L,
                    new CambiarEstadoInscripcionRequest(EstadoInscripcion.CONFIRMADO));

            assertNotNull(response);
            assertEquals(EstadoInscripcion.CONFIRMADO, participante.getEstadoInscripcion());
        }
    }

    // ── obtenerAlertasSinJefe ─────────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerAlertasSinJefe")
    class ObtenerAlertasSinJefe {

        @Test
        void sinAlertas_retornaListaVacia() {
            when(salidaRepository.findByJefeAbandonoNombreIsNotNull()).thenReturn(List.of());

            assertTrue(service.obtenerAlertasSinJefe().isEmpty());
        }

        @Test
        void conAlertas_retornaListaMapeada() {
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setJefeAbandonoNombre("Juan Pérez");
            when(salidaRepository.findByJefeAbandonoNombreIsNotNull()).thenReturn(List.of(salida));

            List<AlertaSinJefeResponse> result = service.obtenerAlertasSinJefe();

            assertEquals(1, result.size());
            assertEquals("Juan Pérez", result.get(0).jefeAbandonoNombre());
        }
    }

    // ── toggleInscripcionesCerradas ───────────────────────────────────────────

    @Nested
    @DisplayName("toggleInscripcionesCerradas")
    class ToggleInscripcionesCerradas {

        @Test
        void sinJefeNiPrivilegiado_lanzaAccessDenied() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));

            var ex = assertThrows(BusinessException.class,
                    () -> service.toggleInscripcionesCerradas(salidaId, UUID.randomUUID()));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        void admin_abreYCierraInscripciones() {
            setSecurityContext("ROLE_ADMIN");
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setInscripcionesCerradas(false);
            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(salidaRepository.save(any())).thenReturn(salida);

            boolean result = service.toggleInscripcionesCerradas(salidaId, UUID.randomUUID());

            assertTrue(result);
            assertTrue(salida.isInscripcionesCerradas());
        }
    }

    // ── listar ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        void paginaVacia_retornaPageVacia() {
            Page<Salida> emptyPage = new PageImpl<>(List.of());
            when(salidaRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class))).thenReturn(emptyPage);

            Page<SalidaSummaryResponse> result = service.listar(null, null, null, null, PageRequest.of(0, 10));

            assertTrue(result.isEmpty());
        }
    }

    // ── obtenerAprobacionesPendientes ─────────────────────────────────────────

    @Nested
    @DisplayName("obtenerAprobacionesPendientes")
    class ObtenerAprobacionesPendientes {

        @Test
        void sinPendientes_retornaListaVacia() {
            setSecurityContext("ROLE_ADMIN");
            when(participanteRepository.findPendientesParaDirectivo(EstadoInscripcion.PENDIENTE_APROBACION))
                    .thenReturn(List.of());

            List<AprobacionPendienteResponse> result =
                    service.obtenerAprobacionesPendientes(UUID.randomUUID());

            assertTrue(result.isEmpty());
        }
    }

    // ── eliminarDignidad ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminarDignidad")
    class EliminarDignidad {

        @Test
        void dignidadNoEncontrada_lanzaNotFound() {
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, salida, EstadoInscripcion.INSCRITO);

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepository.findById(99L)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarDignidad(salidaId, 1L, 99L));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void dignidadDeOtroParticipante_lanzaNotFound() {
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, salida, EstadoInscripcion.INSCRITO);

            // Another participante (id=2L)
            SalidaParticipante otroParticipante = participanteConSocioYSalida(2L, socio, salida, EstadoInscripcion.INSCRITO);
            Dignidad dignidad = Dignidad.builder().id(1).nombre("Cronista").descripcion("desc").build();
            SalidaParticipanteDignidad asignada = SalidaParticipanteDignidad.builder()
                    .id(10L).participante(otroParticipante).dignidad(dignidad).build();

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepository.findById(10L)).thenReturn(Optional.of(asignada));

            var ex = assertThrows(BusinessException.class,
                    () -> service.eliminarDignidad(salidaId, 1L, 10L));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void dignidadValida_seElimina() {
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, salida, EstadoInscripcion.INSCRITO);
            Dignidad dignidad = Dignidad.builder().id(1).nombre("Cronista").descripcion("desc").build();
            SalidaParticipanteDignidad asignada = SalidaParticipanteDignidad.builder()
                    .id(10L).participante(participante).dignidad(dignidad).build();

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepository.findById(10L)).thenReturn(Optional.of(asignada));

            assertDoesNotThrow(() -> service.eliminarDignidad(salidaId, 1L, 10L));
            verify(dignidadRepository).delete(asignada);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Salida salidaConEstado(EstadoSalida estado) {
        return Salida.builder()
                .id(UUID.randomUUID())
                .nombre("Salida Test")
                .fechaInicio(LocalDate.of(2025, 6, 1))
                .horaEncuentroClub(LocalTime.of(4, 0))
                .fechaFin(LocalDate.of(2025, 6, 2))
                .estado(estado)
                .build();
    }

    private Salida salidaCompletaConEstado(EstadoSalida estado, Socio creadoPor) {
        return Salida.builder()
                .id(UUID.randomUUID())
                .nombre("Salida Test")
                .fechaInicio(LocalDate.of(2025, 6, 1))
                .horaEncuentroClub(LocalTime.of(4, 0))
                .fechaFin(LocalDate.of(2025, 6, 2))
                .estado(estado)
                .creadoPor(creadoPor)
                .build();
    }

    private Socio socioConId(UUID id) {
        Socio s = new Socio();
        s.setId(id);
        s.setNombre("Juan");
        s.setApellido("Pérez");
        s.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        return s;
    }

    private SalidaParticipante participanteConSocioYSalida(
            Long id, Socio socio, Salida salida, EstadoInscripcion estado) {
        return SalidaParticipante.builder()
                .id(id)
                .socio(socio)
                .salida(salida)
                .estadoInscripcion(estado)
                .build();
    }

    // ── decidirRiesgo ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("decidirRiesgo")
    class DecidirRiesgo {

        @Test
        void participanteNoEnPendiente_lanzaValidationError() {
            setSecurityContextWithSocio("ROLE_ADMIN", UUID.randomUUID());
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.INSCRITO);

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.decidirRiesgo(salidaId, 1L, UUID.randomUUID(),
                            new DecidirRiesgoRequest(true, "ok")));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void sinPermisos_lanzaAccessDenied() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_SOCIO", socioId);
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.PENDIENTE_APROBACION);

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepository.countBySalidaSocioYDignidad(salidaId, socioId, "Jefe de Salida"))
                    .thenReturn(0L);

            var ex = assertThrows(BusinessException.class,
                    () -> service.decidirRiesgo(salidaId, 1L, socioId,
                            new DecidirRiesgoRequest(true, "ok")));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        void adminNiega_setaEstadoNegado() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_ADMIN", socioId);
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.PENDIENTE_APROBACION);

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(participanteRepository.save(any())).thenReturn(participante);
            when(dignidadRepository.findByParticipanteId(1L)).thenReturn(List.of());

            service.decidirRiesgo(salidaId, 1L, socioId,
                    new DecidirRiesgoRequest(false, "nivel insuficiente"));

            assertEquals(EstadoInscripcion.NEGADO, participante.getEstadoInscripcion());
            assertEquals(socioId, participante.getRiesgoAprobadoPorDirectivo());
        }

        @Test
        void adminApruebaConJefeYaAprobado_setaInscrito() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_ADMIN", socioId);
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.PENDIENTE_APROBACION);
            participante.setRiesgoAprobadoPorJefe(UUID.randomUUID()); // jefe ya aprobó

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(participanteRepository.save(any())).thenReturn(participante);
            when(dignidadRepository.findByParticipanteId(1L)).thenReturn(List.of());

            service.decidirRiesgo(salidaId, 1L, socioId,
                    new DecidirRiesgoRequest(true, "aprobado"));

            assertEquals(EstadoInscripcion.INSCRITO, participante.getEstadoInscripcion());
            assertNotNull(participante.getRiesgoAprobadoEn());
        }
    }

    // ── revocarAprobacion ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("revocarAprobacion")
    class RevocarAprobacion {

        @Test
        void sinPermisos_lanzaAccessDenied() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_SOCIO", socioId);
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.INSCRITO);

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(dignidadRepository.countBySalidaSocioYDignidad(salidaId, socioId, "Jefe de Salida"))
                    .thenReturn(0L);

            var ex = assertThrows(BusinessException.class,
                    () -> service.revocarAprobacion(salidaId, 1L));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        void estadoInvalido_lanzaValidationError() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_ADMIN", socioId);
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.NEGADO); // estado inválido

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.revocarAprobacion(salidaId, 1L));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void adminSinAprobacionPrevia_lanzaValidationError() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_ADMIN", socioId);
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.PENDIENTE_APROBACION);
            // riesgoAprobadoPorDirectivo = null

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.revocarAprobacion(salidaId, 1L));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void adminRevocaAprobacionInscrito_vuelvePendiente() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_ADMIN", socioId);
            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);
            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.INSCRITO);
            participante.setRiesgoAprobadoPorDirectivo(UUID.randomUUID()); // tenía aprobación

            when(salidaRepository.findById(salidaId)).thenReturn(Optional.of(salida));
            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));
            when(participanteRepository.save(any())).thenReturn(participante);
            when(dignidadRepository.findByParticipanteId(1L)).thenReturn(List.of());

            service.revocarAprobacion(salidaId, 1L);

            assertNull(participante.getRiesgoAprobadoPorDirectivo());
            assertEquals(EstadoInscripcion.PENDIENTE_APROBACION, participante.getEstadoInscripcion());
        }
    }

    // ── obtenerAprobacionesPendientes con datos ───────────────────────────────

    @Nested
    @DisplayName("obtenerAprobacionesPendientes con datos")
    class ObtenerAprobacionesPendientesConDatos {

        @Test
        void conPendientesDirectivo_mapeoCompleto() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_ADMIN", socioId);

            UUID salidaId = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);

            Socio socio = socioConId(UUID.randomUUID());

            SalidaParticipante p = participanteConSocioYSalida(
                    1L, socio, salida, EstadoInscripcion.PENDIENTE_APROBACION);

            when(participanteRepository.findPendientesParaDirectivo(EstadoInscripcion.PENDIENTE_APROBACION))
                    .thenReturn(List.of(p));

            List<AprobacionPendienteResponse> result = service.obtenerAprobacionesPendientes(socioId);

            assertEquals(1, result.size());
            assertEquals(salida.getId(), result.get(0).salidaId());
        }

        @Test
        void jefeSalida_vePertenecientes() {
            UUID socioId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_SOCIO", socioId);

            when(participanteRepository.findPendientesParaJefe(socioId, EstadoInscripcion.PENDIENTE_APROBACION))
                    .thenReturn(List.of());

            List<AprobacionPendienteResponse> result = service.obtenerAprobacionesPendientes(socioId);

            assertTrue(result.isEmpty());
        }
    }

    // ── isBloqueoInhabilitadosActivo ───────────────────────────────────────────

    @Nested
    @DisplayName("isBloqueoInhabilitadosActivo (via inscribir)")
    class BloqueoInhabilitados {

        @Test
        void configNoEncontrada_bloqueaPorDefecto() {
            setSecurityContext("ROLE_SOCIO");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);

            EstadoHabilitacion inhabilitado = mock(EstadoHabilitacion.class);
            when(inhabilitado.getNombre()).thenReturn("Inhabilitado");

            EstadoAcceso estadoAcceso = mock(EstadoAcceso.class);
            when(estadoAcceso.getCodigo()).thenReturn("ACTIVE");

            Socio socio = socioConId(socioId);
            socio.setEstadoHabilitacion(inhabilitado);
            socio.setEstadoAcceso(estadoAcceso);

            when(salidaRepository.findByIdWithLock(salidaId)).thenReturn(Optional.of(salida));
            when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
            // configRepo returns empty → defaults to true (bloquea)
            when(configRepo.findByClave(anyString())).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.inscribir(salidaId, new InscribirRequest(socioId), socioId));
            assertEquals(ErrorCode.SOCIO_INHABILITADO, ex.getErrorCode());
        }

        @Test
        void configBloqueoPorFalse_permitePasar() {
            setSecurityContext("ROLE_ADMIN");
            UUID salidaId = UUID.randomUUID();
            UUID socioId = UUID.randomUUID();

            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setId(salidaId);

            EstadoHabilitacion inhabilitado = mock(EstadoHabilitacion.class);
            when(inhabilitado.getNombre()).thenReturn("Inhabilitado");

            EstadoAcceso estadoAcceso = mock(EstadoAcceso.class);
            when(estadoAcceso.getCodigo()).thenReturn("ACTIVE");

            Socio socio = socioConId(socioId);
            socio.setEstadoHabilitacion(inhabilitado);
            socio.setEstadoAcceso(estadoAcceso);

            ConfiguracionSistema config = new ConfiguracionSistema();
            config.setValor("false"); // bloqueo desactivado

            when(salidaRepository.findByIdWithLock(salidaId)).thenReturn(Optional.of(salida));
            when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
            when(configRepo.findByClave(anyString())).thenReturn(Optional.of(config));

            // inscribir should proceed past the inhabilitado check
            // but may throw another error (capacity, etc.) — we just verify no VALIDATION_ERROR for inhabilitado
            when(participanteRepository.findBySalidaId(salidaId)).thenReturn(List.of());
            when(participanteRepository.existsBySalidaIdAndSocioId(salidaId, socioId)).thenReturn(false);
            when(participanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(dignidadRepository.findByParticipanteId(anyLong())).thenReturn(List.of());

            // Should not throw VALIDATION_ERROR for inhabilitado
            assertDoesNotThrow(() -> service.inscribir(salidaId, new InscribirRequest(socioId), socioId));
        }
    }

    // ── Helpers extra coverage ────────────────────────────────────────────────

    @Nested
    @DisplayName("actualizar — estado no PLANIFICADA")
    class ActualizarEstadoInvalido {

        @Test
        @DisplayName("salida en estado EN_CURSO lanza SALIDA_NOT_PLANIFICADA")
        void estadoNoPlanificada_lanzaSalidaNotPlanificada() {
            UUID id = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.EN_CURSO);
            when(salidaRepository.findById(id)).thenReturn(Optional.of(salida));

            UpdateSalidaRequest req = new UpdateSalidaRequest(
                    "Nombre", LocalDate.of(2025, 6, 1), LocalTime.of(4, 0),
                    LocalDate.of(2025, 6, 2), null, null, null, null, null, null, null);

            var ex = assertThrows(BusinessException.class, () -> service.actualizar(id, req));
            assertEquals(ErrorCode.SALIDA_NOT_PLANIFICADA, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("findById — salida eliminada")
    class FindByIdEliminada {

        @Test
        @DisplayName("salida eliminada lanza SALIDA_NOT_FOUND")
        void salidaEliminada_lanzaSalidaNotFound() {
            UUID id = UUID.randomUUID();
            Salida salida = salidaConEstado(EstadoSalida.PLANIFICADA);
            salida.setEliminada(true);
            when(salidaRepository.findById(id)).thenReturn(Optional.of(salida));

            UpdateSalidaRequest req = new UpdateSalidaRequest(
                    "Nombre", LocalDate.of(2025, 6, 1), LocalTime.of(4, 0),
                    LocalDate.of(2025, 6, 2), null, null, null, null, null, null, null);

            var ex = assertThrows(BusinessException.class, () -> service.actualizar(id, req));
            assertEquals(ErrorCode.SALIDA_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("findParticipante — salida no coincide")
    class FindParticipanteSalidaErronea {

        @Test
        @DisplayName("participante de otra salida lanza RESOURCE_NOT_FOUND")
        void participanteDeOtraSalida_lanzaResourceNotFound() {
            UUID salidaId = UUID.randomUUID();
            UUID otraSalidaId = UUID.randomUUID();
            setSecurityContextWithSocio("ROLE_SOCIO", UUID.randomUUID());

            Salida otraSalida = salidaConEstado(EstadoSalida.PLANIFICADA);
            // override the id so it doesn't match salidaId
            otraSalida = Salida.builder()
                    .id(otraSalidaId)
                    .nombre("Otra")
                    .fechaInicio(LocalDate.of(2025, 6, 1))
                    .horaEncuentroClub(LocalTime.of(4, 0))
                    .fechaFin(LocalDate.of(2025, 6, 2))
                    .estado(EstadoSalida.PLANIFICADA)
                    .build();

            Socio socio = socioConId(UUID.randomUUID());
            SalidaParticipante participante = participanteConSocioYSalida(1L, socio, otraSalida, EstadoInscripcion.INSCRITO);

            when(participanteRepository.findById(1L)).thenReturn(Optional.of(participante));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cancelarInscripcion(salidaId, 1L, socio.getId()));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setSecurityContext(String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }

    private void setSecurityContextWithSocio(String role, UUID socioId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(role)));
        auth.setDetails(new SaddayAuthDetails(socioId, role.replace("ROLE_", "")));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }
}
