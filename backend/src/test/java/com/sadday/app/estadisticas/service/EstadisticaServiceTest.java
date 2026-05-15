package com.sadday.app.estadisticas.service;

import com.sadday.app.estadisticas.dto.*;
import com.sadday.app.informes.entity.InformeSalida;
import com.sadday.app.informes.repository.InformeSalidaRepository;
import com.sadday.app.mountains.entity.Mountain;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.repository.MountainRepository;
import com.sadday.app.salidas.entity.*;
import com.sadday.app.salidas.repository.*;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EstadisticaService — Unit Tests")
class EstadisticaServiceTest {

    @Mock SocioRepository                      socioRepository;
    @Mock SalidaParticipanteRepository         participanteRepository;
    @Mock SalidaParticipanteDignidadRepository dignidadRepository;
    @Mock SalidaRepository                     salidaRepository;
    @Mock InformeSalidaRepository              informeRepository;
    @Mock MountainRepository                   mountainRepository;
    @Mock JdbcClient                           jdbcClient;

    @Mock JdbcClient.StatementSpec             statementSpec;
    @Mock JdbcClient.MappedQuerySpec<Object>   objectQuerySpec;
    @Mock JdbcClient.MappedQuerySpec<Integer>  intQuerySpec;
    @Mock JdbcClient.MappedQuerySpec<Double>   doubleQuerySpec;

    @InjectMocks EstadisticaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setupJdbc() {
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.params(any(Map.class))).thenReturn(statementSpec);
        when(statementSpec.query(any(RowMapper.class))).thenReturn(objectQuerySpec);
        when(statementSpec.query(Integer.class)).thenReturn(intQuerySpec);
        when(statementSpec.query(Double.class)).thenReturn(doubleQuerySpec);
        when(objectQuerySpec.list()).thenReturn(Collections.emptyList());
        when(intQuerySpec.single()).thenReturn(0);
        when(intQuerySpec.list()).thenReturn(Collections.emptyList());
        when(doubleQuerySpec.single()).thenReturn(0.0);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── obtenerHistorialSocio ─────────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerHistorialSocio")
    class ObtenerHistorialSocio {

        @Test
        void socioNoEncontrado_lanzaNotFound() {
            UUID socioId = UUID.randomUUID();
            when(socioRepository.findById(socioId)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.obtenerHistorialSocio(socioId, UUID.randomUUID()));
            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void sinParticipaciones_retornaResponseConCeros() {
            UUID socioId = UUID.randomUUID();
            Socio socio = socioConNombre(socioId, "Juan", "Pérez");
            when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
            when(participanteRepository.findBySocioIdAndEstadoInscripcionNotFetch(
                    socioId, EstadoInscripcion.CANCELADO)).thenReturn(List.of());

            SocioHistorialResponse response = service.obtenerHistorialSocio(socioId, UUID.randomUUID());

            assertEquals(0, response.totalParticipaciones());
            assertEquals(0, response.totalCumbresLogradas());
            assertEquals(0, response.vecesJefeSalida());
            assertTrue(response.historial().isEmpty());
            assertTrue(response.conteosDignidades().isEmpty());
        }

        @Test
        void conParticipacion_construyeHistorialCorrectamente() {
            UUID socioId = UUID.randomUUID();
            Socio socio = socioConNombre(socioId, "Ana", "López");
            when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));

            Mountain mountain = Mountain.builder().id(1).nombre("Cotopaxi")
                    .region("Sierra").altitud(5897).pais("EC").build();
            Ruta ruta = mock(Ruta.class);
            when(ruta.getId()).thenReturn(10);
            when(ruta.getNombre()).thenReturn("Ruta Normal");
            when(ruta.getMountain()).thenReturn(mountain);

            UUID salidaId = UUID.randomUUID();
            Salida salida = Salida.builder()
                    .id(salidaId).nombre("Cotopaxi Dic")
                    .fechaInicio(LocalDate.of(2024, 12, 1))
                    .horaEncuentroClub(LocalTime.of(4, 0))
                    .ruta(ruta).estado(EstadoSalida.REALIZADA).build();

            SalidaParticipante participante = SalidaParticipante.builder()
                    .id(100L).socio(socio).salida(salida)
                    .estadoInscripcion(EstadoInscripcion.CONFIRMADO).build();

            when(participanteRepository.findBySocioIdAndEstadoInscripcionNotFetch(
                    socioId, EstadoInscripcion.CANCELADO)).thenReturn(List.of(participante));

            Dignidad dignidad = Dignidad.builder().id(1).nombre("Cronista").descripcion("Lleva crónica").build();
            SalidaParticipanteDignidad digAsignada = SalidaParticipanteDignidad.builder()
                    .id(1L).participante(participante).dignidad(dignidad).build();

            when(dignidadRepository.findByParticipanteIdIn(List.of(100L))).thenReturn(List.of(digAsignada));
            when(dignidadRepository.findByParticipante_SocioId(socioId)).thenReturn(List.of(digAsignada));

            InformeSalida informe = InformeSalida.builder()
                    .id(UUID.randomUUID()).salida(salida).seRealizo(true).lograronCumbre(true).build();
            when(informeRepository.findBySalidaIdIn(List.of(salidaId))).thenReturn(List.of(informe));

            SocioHistorialResponse response = service.obtenerHistorialSocio(socioId, UUID.randomUUID());

            assertEquals(1, response.totalParticipaciones());
            assertEquals(1, response.totalCumbresLogradas());
            assertEquals(0, response.vecesJefeSalida()); // dignidad es Cronista, no Jefe
            assertEquals(1, response.historial().size());
            assertEquals(1, response.conteosDignidades().size());
            assertEquals("Cronista", response.conteosDignidades().get(0).dignidadNombre());
        }

        @Test
        void conJefeDeSalida_contabilizaVecesJefe() {
            UUID socioId = UUID.randomUUID();
            Socio socio = socioConNombre(socioId, "Carlos", "Ruiz");
            when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));

            Ruta ruta = mock(Ruta.class);
            when(ruta.getId()).thenReturn(5);
            when(ruta.getNombre()).thenReturn("Ruta Sur");
            when(ruta.getMountain()).thenReturn(null);

            UUID salidaId = UUID.randomUUID();
            Salida salida = Salida.builder()
                    .id(salidaId).nombre("Salida Test")
                    .fechaInicio(LocalDate.of(2024, 3, 15))
                    .horaEncuentroClub(LocalTime.of(5, 0))
                    .ruta(ruta).estado(EstadoSalida.REALIZADA).build();

            SalidaParticipante participante = SalidaParticipante.builder()
                    .id(200L).socio(socio).salida(salida)
                    .estadoInscripcion(EstadoInscripcion.CONFIRMADO).build();

            when(participanteRepository.findBySocioIdAndEstadoInscripcionNotFetch(
                    socioId, EstadoInscripcion.CANCELADO)).thenReturn(List.of(participante));

            Dignidad dignidadJefe = Dignidad.builder().id(1).nombre("Jefe de Salida").descripcion("Lidera").build();
            SalidaParticipanteDignidad digJefe = SalidaParticipanteDignidad.builder()
                    .id(10L).participante(participante).dignidad(dignidadJefe).build();

            when(dignidadRepository.findByParticipanteIdIn(List.of(200L))).thenReturn(List.of(digJefe));
            when(dignidadRepository.findByParticipante_SocioId(socioId)).thenReturn(List.of(digJefe));
            when(informeRepository.findBySalidaIdIn(List.of(salidaId))).thenReturn(List.of());

            SocioHistorialResponse response = service.obtenerHistorialSocio(socioId, UUID.randomUUID());

            assertEquals(1, response.vecesJefeSalida());
        }
    }

    // ── obtenerEstadisticasMountain ───────────────────────────────────────────

    @Nested
    @DisplayName("obtenerEstadisticasMountain")
    class ObtenerEstadisticasMountain {

        @Test
        void mountainNoEncontrado_lanzaNotFound() {
            when(mountainRepository.findById(999)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.obtenerEstadisticasMountain(999));
            assertEquals(ErrorCode.MOUNTAIN_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void sinSalidas_retornaResponseVacia() {
            Mountain mountain = Mountain.builder().id(1).nombre("Test").region("Sierra")
                    .altitud(4000).pais("EC").build();
            when(mountainRepository.findById(1)).thenReturn(Optional.of(mountain));
            when(salidaRepository.findByRuta_MountainId(1)).thenReturn(List.of());

            MountainEstadisticaResponse response = service.obtenerEstadisticasMountain(1);

            assertEquals(0, response.totalSalidas());
            assertEquals(0, response.salidasRealizadas());
            assertNull(response.ultimaSalida());
            assertTrue(response.rutas().isEmpty());
        }

        @Test
        void conSalidas_retornaEstadisticasAgrupadas() {
            Mountain mountain = Mountain.builder().id(1).nombre("Cotopaxi").region("Sierra")
                    .altitud(5897).pais("EC").build();
            when(mountainRepository.findById(1)).thenReturn(Optional.of(mountain));

            Ruta ruta = mock(Ruta.class);
            when(ruta.getId()).thenReturn(10);
            when(ruta.getNombre()).thenReturn("Ruta Normal");

            UUID salidaId = UUID.randomUUID();
            Salida salida = Salida.builder()
                    .id(salidaId).nombre("S1")
                    .fechaInicio(LocalDate.of(2024, 6, 1))
                    .horaEncuentroClub(LocalTime.of(4, 0))
                    .ruta(ruta).build();

            when(salidaRepository.findByRuta_MountainId(1)).thenReturn(List.of(salida));

            InformeSalida informe = InformeSalida.builder()
                    .id(UUID.randomUUID()).salida(salida).seRealizo(true).lograronCumbre(true).build();
            when(informeRepository.findBySalidaIdIn(List.of(salidaId))).thenReturn(List.of(informe));

            MountainEstadisticaResponse response = service.obtenerEstadisticasMountain(1);

            assertEquals(1, response.totalSalidas());
            assertEquals(1, response.salidasRealizadas());
            assertEquals(LocalDate.of(2024, 6, 1), response.ultimaSalida());
            assertEquals(1, response.rutas().size());
        }

        @Test
        void informeSeRealizoFalse_noContaComoRealizada() {
            Mountain mountain = Mountain.builder().id(2).nombre("Pichincha").region("Sierra")
                    .altitud(4784).pais("EC").build();
            when(mountainRepository.findById(2)).thenReturn(Optional.of(mountain));

            Ruta ruta = mock(Ruta.class);
            when(ruta.getId()).thenReturn(20);
            when(ruta.getNombre()).thenReturn("Ruta Sur");

            UUID salidaId = UUID.randomUUID();
            Salida salida = Salida.builder()
                    .id(salidaId).nombre("Pichincha Mar")
                    .fechaInicio(LocalDate.of(2024, 3, 10))
                    .horaEncuentroClub(LocalTime.of(5, 0))
                    .ruta(ruta).build();

            when(salidaRepository.findByRuta_MountainId(2)).thenReturn(List.of(salida));

            InformeSalida informe = InformeSalida.builder()
                    .id(UUID.randomUUID()).salida(salida).seRealizo(false).lograronCumbre(false).build();
            when(informeRepository.findBySalidaIdIn(List.of(salidaId))).thenReturn(List.of(informe));

            MountainEstadisticaResponse response = service.obtenerEstadisticasMountain(2);

            assertEquals(1, response.totalSalidas());
            assertEquals(0, response.salidasRealizadas());
        }
    }

    // ── obtenerRankings ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerRankings")
    class ObtenerRankings {

        @Test
        void llamadaBasica_retornaClubRankingsResponse() {
            ClubRankingsResponse response = service.obtenerRankings(10);

            assertNotNull(response);
            verify(jdbcClient, atLeastOnce()).sql(anyString());
        }

        @Test
        void topFueraDeLimite_seClampea() {
            assertDoesNotThrow(() -> service.obtenerRankings(100));
            assertDoesNotThrow(() -> service.obtenerRankings(0));
        }

        @Test
        void topMinimo_clampea_a1() {
            ClubRankingsResponse response = service.obtenerRankings(-5);

            assertNotNull(response);
        }
    }

    // ── buscarParticipantes ───────────────────────────────────────────────────

    @Nested
    @DisplayName("buscarParticipantes")
    class BuscarParticipantes {

        @Test
        void sinFiltros_ejecutaConsulta() {
            List<ParticipanteFiltradoItem> result =
                    service.buscarParticipantes(null, null, null, null, null, null);

            assertNotNull(result);
            verify(jdbcClient).sql(anyString());
        }

        @Test
        @SuppressWarnings("unchecked")
        void conTodosFiltros_ejecutaConsulta() {
            List<ParticipanteFiltradoItem> result =
                    service.buscarParticipantes(1, 2, 3, "nivel-id", "ESCALADA", "Juan");

            assertNotNull(result);
            verify(statementSpec).params(any(Map.class));
        }

        @Test
        void soloConQ_agregaParametroQ() {
            List<ParticipanteFiltradoItem> result =
                    service.buscarParticipantes(null, null, null, null, null, "Ana");

            assertNotNull(result);
        }

        @Test
        void soloConMountainId_agregaParametroMountain() {
            List<ParticipanteFiltradoItem> result =
                    service.buscarParticipantes(5, null, null, null, null, null);

            assertNotNull(result);
        }

        @Test
        void tipoActividadEnBlanco_noAgregaFiltro() {
            List<ParticipanteFiltradoItem> result =
                    service.buscarParticipantes(null, null, null, null, "   ", null);

            assertNotNull(result);
        }
    }

    // ── obtenerRankingReuniones ───────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerRankingReuniones")
    class ObtenerRankingReuniones {

        @Test
        void llamadaBasica_retornaReunionesRankingResponse() {
            ReunionesRankingResponse response = service.obtenerRankingReuniones(10, 12);

            assertNotNull(response);
            assertEquals(0, response.totalReuniones());
            assertEquals(0.0, response.promedioAsistentesGlobal());
        }

        @Test
        void paramsFueraDeLimite_seClampean() {
            assertDoesNotThrow(() -> service.obtenerRankingReuniones(0, 0));
            assertDoesNotThrow(() -> service.obtenerRankingReuniones(100, 100));
        }

        @Test
        void topMinimo_clampea_a1() {
            ReunionesRankingResponse response = service.obtenerRankingReuniones(-1, -1);

            assertNotNull(response);
        }
    }

    // ── obtenerActividadTotalSocio ────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerActividadTotalSocio")
    class ObtenerActividadTotalSocio {

        @Test
        void otroSocioSinRolPrivilegiado_lanzaAccessDenied() {
            setSecurityContext("ROLE_SOCIO");
            UUID socioId = UUID.randomUUID();
            UUID currentUserId = UUID.randomUUID();

            var ex = assertThrows(BusinessException.class,
                    () -> service.obtenerActividadTotalSocio(socioId, currentUserId));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        void propioSocio_retornaActividad() {
            UUID id = UUID.randomUUID();
            Socio socio = socioConNombre(id, "Juan", "Pérez");
            when(socioRepository.findById(id)).thenReturn(Optional.of(socio));
            when(participanteRepository.countBySocioIdAndEstadoInscripcionNot(id, EstadoInscripcion.CANCELADO))
                    .thenReturn(5);

            ActividadTotalSocioResponse response = service.obtenerActividadTotalSocio(id, id);

            assertNotNull(response);
            assertEquals(5, response.totalSalidasParticipadas());
            assertEquals("Juan", response.nombre());
        }

        @Test
        void adminVeOtroSocio_retornaActividad() {
            setSecurityContext("ROLE_ADMIN");
            UUID socioId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();
            Socio socio = socioConNombre(socioId, "María", "García");
            when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
            when(participanteRepository.countBySocioIdAndEstadoInscripcionNot(socioId, EstadoInscripcion.CANCELADO))
                    .thenReturn(3);

            ActividadTotalSocioResponse response = service.obtenerActividadTotalSocio(socioId, adminId);

            assertNotNull(response);
            assertEquals(3, response.totalSalidasParticipadas());
        }

        @Test
        void directivoVeOtroSocio_retornaActividad() {
            setSecurityContext("ROLE_DIRECTIVO");
            UUID socioId = UUID.randomUUID();
            UUID dirId = UUID.randomUUID();
            Socio socio = socioConNombre(socioId, "Pedro", "Silva");
            when(socioRepository.findById(socioId)).thenReturn(Optional.of(socio));
            when(participanteRepository.countBySocioIdAndEstadoInscripcionNot(socioId, EstadoInscripcion.CANCELADO))
                    .thenReturn(0);

            ActividadTotalSocioResponse response = service.obtenerActividadTotalSocio(socioId, dirId);

            assertNotNull(response);
        }

        @Test
        void socioNoEncontrado_lanzaNotFound() {
            UUID id = UUID.randomUUID();
            when(socioRepository.findById(id)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.obtenerActividadTotalSocio(id, id));
            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── obtenerRankingMontanaRuta ─────────────────────────────────────────────

    @Nested
    @DisplayName("obtenerRankingMontanaRuta")
    class ObtenerRankingMontanaRuta {

        @Test
        void llamadaBasica_retornaRankingMontanaRutaResponse() {
            RankingMontanaRutaResponse response = service.obtenerRankingMontanaRuta(10);

            assertNotNull(response);
            verify(jdbcClient, atLeastOnce()).sql(anyString());
        }

        @Test
        void topFueraDeLimite_seClampea() {
            assertDoesNotThrow(() -> service.obtenerRankingMontanaRuta(0));
            assertDoesNotThrow(() -> service.obtenerRankingMontanaRuta(200));
        }
    }

    // ── buscarMontanaRuta ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("buscarMontanaRuta")
    class BuscarMontanaRuta {

        @Test
        void tipoNull_consultaAmbosMontanaYRuta() {
            List<MontanaRutaBusquedaItem> result = service.buscarMontanaRuta(null, null, false);

            assertNotNull(result);
            verify(jdbcClient, times(2)).sql(anyString());
        }

        @Test
        void tipoMontana_soloConsultaMontana() {
            List<MontanaRutaBusquedaItem> result = service.buscarMontanaRuta("montana", null, false);

            assertNotNull(result);
            verify(jdbcClient, times(1)).sql(anyString());
        }

        @Test
        void tipoRuta_soloConsultaRuta() {
            List<MontanaRutaBusquedaItem> result = service.buscarMontanaRuta("ruta", "Chimborazo", false);

            assertNotNull(result);
            verify(jdbcClient, times(1)).sql(anyString());
        }

        @Test
        void tipoAmbos_consultaDoVeces() {
            List<MontanaRutaBusquedaItem> result = service.buscarMontanaRuta("ambos", "Cotopaxi", false);

            assertNotNull(result);
            verify(jdbcClient, times(2)).sql(anyString());
        }

        @Test
        void sinSalidas_agregaHavingClause() {
            assertDoesNotThrow(() -> service.buscarMontanaRuta("montana", null, true));
        }
    }

    // ── buscarSalidasEnPeriodo ────────────────────────────────────────────────

    @Nested
    @DisplayName("buscarSalidasEnPeriodo")
    class BuscarSalidasEnPeriodo {

        @Test
        void sinTipoActividad_ejecutaConsulta() {
            List<SalidaPeriodoItem> result = service.buscarSalidasEnPeriodo(
                    LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null);

            assertNotNull(result);
            verify(jdbcClient).sql(anyString());
        }

        @Test
        void conTipoActividad_agregaFiltro() {
            List<SalidaPeriodoItem> result = service.buscarSalidasEnPeriodo(
                    LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "escalada");

            assertNotNull(result);
        }

        @Test
        void tipoActividadEnBlanco_noAgregaFiltro() {
            List<SalidaPeriodoItem> result = service.buscarSalidasEnPeriodo(
                    LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "  ");

            assertNotNull(result);
        }
    }

    // ── buscarMontanasEnPeriodo ───────────────────────────────────────────────

    @Nested
    @DisplayName("buscarMontanasEnPeriodo")
    class BuscarMontanasEnPeriodo {

        @Test
        void sinTipoActividad_ejecutaConsulta() {
            List<MontanaPeriodoItem> result = service.buscarMontanasEnPeriodo(
                    LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null);

            assertNotNull(result);
        }

        @Test
        void conTipoActividad_agregaFiltro() {
            List<MontanaPeriodoItem> result = service.buscarMontanasEnPeriodo(
                    LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "escalada");

            assertNotNull(result);
        }
    }

    // ── buscarRutasEnPeriodo ──────────────────────────────────────────────────

    @Nested
    @DisplayName("buscarRutasEnPeriodo")
    class BuscarRutasEnPeriodo {

        @Test
        void sinTipoActividad_ejecutaConsulta() {
            List<RutaPeriodoItem> result = service.buscarRutasEnPeriodo(
                    LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null);

            assertNotNull(result);
        }

        @Test
        void conTipoActividad_agregaFiltro() {
            List<RutaPeriodoItem> result = service.buscarRutasEnPeriodo(
                    LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "trekking");

            assertNotNull(result);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Socio socioConNombre(UUID id, String nombre, String apellido) {
        Socio s = new Socio();
        s.setId(id);
        s.setNombre(nombre);
        s.setApellido(apellido);
        return s;
    }

    private void setSecurityContext(String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }
}
