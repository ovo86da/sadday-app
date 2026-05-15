package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.CreateRutaRequest;
import com.sadday.app.mountains.dto.RutaResponse;
import com.sadday.app.mountains.dto.RutaSummaryResponse;
import com.sadday.app.mountains.dto.UpdateRutaRequest;
import com.sadday.app.mountains.entity.*;
import com.sadday.app.mountains.repository.*;
import com.sadday.app.salidas.repository.SalidaRepository;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.ClasificacionSocioRepository;
import com.sadday.app.socios.repository.SocioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RutaService — Unit Tests")
class RutaServiceTest {

    @Mock RutaRepository                  rutaRepository;
    @Mock EquipoMontanaRepository         equipoMontanaRepository;
    @Mock MountainService                 mountainService;
    @Mock SocioRepository                 socioRepository;
    @Mock ContactoService                 contactoService;
    @Mock ClasificacionSocioRepository    clasifSocioRepository;
    @Mock DificultadSenderismoRepository  senderismoRepository;
    @Mock SalidaRepository                salidaRepository;
    @Mock RutaDocumentoService            rutaDocumentoService;

    @InjectMocks RutaService service;

    private final Integer RUTA_ID = 1;
    private final UUID    SOCIO_ID = UUID.randomUUID();

    private Socio socio;

    @BeforeEach
    void setUp() {
        socio = new Socio();
        socio.setId(SOCIO_ID);
        socio.setNombre("Juan");
        socio.setApellido("Pérez");

        when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(socio));
        when(contactoService.listarContactosRuta(any())).thenReturn(List.of());
        when(rutaDocumentoService.listarPorRuta(any())).thenReturn(List.of());
    }

    // ── listar ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @SuppressWarnings("unchecked")
        void sinFiltros_devuelvePaginaVacia() {
            Page<Ruta> emptyPage = new PageImpl<>(List.of());
            when(rutaRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(emptyPage);

            Page<RutaSummaryResponse> result = service.listar(null, null, null, null, Pageable.unpaged());

            assertTrue(result.isEmpty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void conRutaTrekking_devuelveSummary() {
            Ruta ruta = rutaTrekking();
            Page<Ruta> page = new PageImpl<>(List.of(ruta));
            when(rutaRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            Page<RutaSummaryResponse> result = service.listar(null, null, "TREKKING", null, Pageable.unpaged());

            assertEquals(1, result.getTotalElements());
            assertEquals("Sendero Test", result.getContent().get(0).nombre());
        }

        @Test
        @SuppressWarnings("unchecked")
        void conRutaCiclismo_devuelveSummary() {
            Ruta ruta = rutaCiclismo();
            Page<Ruta> page = new PageImpl<>(List.of(ruta));
            when(rutaRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            Page<RutaSummaryResponse> result = service.listar(null, null, "CICLISMO", null, Pageable.unpaged());

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @SuppressWarnings("unchecked")
        void conRutaEscalada_devuelveSummary() {
            Ruta ruta = rutaEscalada();
            Page<Ruta> page = new PageImpl<>(List.of(ruta));
            when(rutaRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            Page<RutaSummaryResponse> result = service.listar(null, null, "ESCALADA", null, Pageable.unpaged());

            assertEquals(1, result.getTotalElements());
        }
    }

    // ── obtener ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("obtener")
    class Obtener {

        @Test
        void idExistente_devuelveRutaResponse() {
            Ruta ruta = rutaTrekking();
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));

            RutaResponse result = service.obtener(RUTA_ID);

            assertNotNull(result);
            assertEquals("Sendero Test", result.nombre());
        }

        @Test
        void idInexistente_lanzaRutaNotFound() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> service.obtener(RUTA_ID));
            assertEquals(ErrorCode.RUTA_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── crear ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("crear")
    class Crear {

        @Test
        void trekkingValido_creaRuta() {
            DificultadSenderismo dif = new DificultadSenderismo("F", "Fácil", null, (short) 1);
            when(senderismoRepository.findById("F")).thenReturn(Optional.of(dif));

            Ruta saved = rutaTrekking();
            when(rutaRepository.save(any())).thenReturn(saved);
            when(rutaRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

            RutaResponse result = service.crear(trekkingRequest("F"), SOCIO_ID);

            assertNotNull(result);
            verify(rutaRepository).save(any());
        }

        @Test
        void ciclismoValido_creaRuta() {
            Ruta saved = rutaCiclismo();
            when(rutaRepository.save(any())).thenReturn(saved);
            when(rutaRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

            RutaResponse result = service.crear(ciclismoRequest(), SOCIO_ID);

            assertNotNull(result);
        }

        @Test
        void escaladaValida_creaRuta() {
            DificultadRoca roca = mockRoca();
            when(mountainService.findRoca("5a")).thenReturn(roca);

            Ruta saved = rutaEscalada();
            when(rutaRepository.save(any())).thenReturn(saved);
            when(rutaRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

            RutaResponse result = service.crear(escaladaRequest("5a"), SOCIO_ID);

            assertNotNull(result);
        }

        @Test
        void sinMontanyaNiLugar_lanzaValidationError() {
            // mountainId=null, lugarReferencia=null → validation error
            CreateRutaRequest req = new CreateRutaRequest(
                    "Ruta", TipoActividad.TREKKING,
                    null, null,
                    null, null, null, null, null, null, true, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null,
                    "F", false, false, null,
                    null, null, null, null
            );

            var ex = assertThrows(BusinessException.class, () -> service.crear(req, SOCIO_ID));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void trekkingSinDificultad_lanzaValidationError() {
            CreateRutaRequest req = new CreateRutaRequest(
                    "Ruta", TipoActividad.TREKKING,
                    1, "Lugar", null, null, null, null, null, null, false, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null,
                    null, false, false, null,   // dificultadSenderismoId = null → error
                    null, null, null, null
            );

            var ex = assertThrows(BusinessException.class, () -> service.crear(req, SOCIO_ID));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void ciclismoSinTipoBicicleta_lanzaValidationError() {
            CreateRutaRequest req = new CreateRutaRequest(
                    "Ruta", TipoActividad.CICLISMO,
                    1, "Lugar", null, null, null, null, null, null, false, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null,
                    null, false, false, null,
                    null, null, null, null    // tipoBicicleta = null → error
            );

            var ex = assertThrows(BusinessException.class, () -> service.crear(req, SOCIO_ID));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void escaladaSinTipoEscalada_lanzaValidationError() {
            CreateRutaRequest req = new CreateRutaRequest(
                    "Ruta", TipoActividad.ESCALADA,
                    1, "Lugar", null, null, null, null, null, null, false, null, null, null,
                    null, "5a", null, null, null, null, null, null,
                    null, null, null, null,   // tipoEscalada = null → error
                    null, false, false, null,
                    null, null, null, null
            );

            var ex = assertThrows(BusinessException.class, () -> service.crear(req, SOCIO_ID));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void alpinismoSinIfas_lanzaValidationError() {
            CreateRutaRequest req = new CreateRutaRequest(
                    "Ruta", TipoActividad.ALPINISMO,
                    1, "Lugar", null, null, null, null, null, null, false, null, null, null,
                    null, "5a", "PD", null, null, null, null, null,   // escalaAlpinaIfasId=null → error
                    null, null, null, null,
                    null, false, false, null,
                    null, null, null, null
            );

            var ex = assertThrows(BusinessException.class, () -> service.crear(req, SOCIO_ID));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void socioNoEncontrado_lanzaSocioNotFound() {
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.empty());
            when(senderismoRepository.findById("F")).thenReturn(
                    Optional.of(new DificultadSenderismo("F", "Fácil", null, (short) 1)));

            var ex = assertThrows(BusinessException.class, () -> service.crear(trekkingRequest("F"), SOCIO_ID));
            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void conNivelMinimo_asignaClasificacion() {
            ClasificacionSocio clasif = mock(ClasificacionSocio.class);
            when(clasif.getId()).thenReturn("MEDIO");
            when(clasifSocioRepository.findById("MEDIO")).thenReturn(Optional.of(clasif));

            DificultadSenderismo dif = new DificultadSenderismo("F", "Fácil", null, (short) 1);
            when(senderismoRepository.findById("F")).thenReturn(Optional.of(dif));

            Ruta saved = rutaTrekking();
            when(rutaRepository.save(any())).thenReturn(saved);
            when(rutaRepository.findById(saved.getId())).thenReturn(Optional.of(saved));

            CreateRutaRequest req = new CreateRutaRequest(
                    "Sendero", TipoActividad.TREKKING,
                    null, "Lugar Test", null, null, null, null, null, null, false, null, null, "MEDIO",
                    null, null, null, null, null, null, null, null,
                    null, null, null, null,
                    "F", false, false, null,
                    null, null, null, null
            );

            RutaResponse result = service.crear(req, SOCIO_ID);

            assertNotNull(result);
            verify(clasifSocioRepository).findById("MEDIO");
        }
    }

    // ── actualizar ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("actualizar")
    class Actualizar {

        @Test
        void trekkingValido_actualizaRuta() {
            Ruta ruta = rutaTrekking();
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));
            when(senderismoRepository.findById("M"))
                    .thenReturn(Optional.of(new DificultadSenderismo("M", "Moderado", null, (short) 2)));
            when(rutaRepository.save(any())).thenReturn(ruta);

            RutaResponse result = service.actualizar(RUTA_ID, trekkingUpdateRequest("M"));

            assertNotNull(result);
            verify(rutaRepository).save(ruta);
        }

        @Test
        void idInexistente_lanzaRutaNotFound() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.actualizar(RUTA_ID, trekkingUpdateRequest("M")));
            assertEquals(ErrorCode.RUTA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void cambiaTipoAEscalada_limpiaTrekkingSubtipo() {
            Ruta ruta = rutaTrekking();
            DificultadRoca roca = mockRoca();
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));
            when(mountainService.findRoca("5a")).thenReturn(roca);
            when(rutaRepository.save(any())).thenReturn(ruta);

            UpdateRutaRequest req = new UpdateRutaRequest(
                    "Ruta Actualizada", TipoActividad.ESCALADA,
                    null, "Lugar", null, null, null, null, null, null, false, null, null, null,
                    null, "5a", null, null, null, null, null, null,
                    "DEPORTIVA", null, null, null,
                    null, false, false, null,
                    null, null, null, null
            );

            service.actualizar(RUTA_ID, req);

            assertNull(ruta.getTrekking());
        }

        @Test
        void cambiaTipoCiclismo_funcionaCorrectamente() {
            Ruta ruta = rutaCiclismo();
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));
            when(rutaRepository.save(any())).thenReturn(ruta);

            UpdateRutaRequest req = new UpdateRutaRequest(
                    "Ciclismo Update", TipoActividad.CICLISMO,
                    null, "Lugar", null, null, null, null, null, null, false, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null,
                    null, false, false, null,
                    "GRAVEL", "S2", null, null
            );

            service.actualizar(RUTA_ID, req);

            verify(rutaRepository).save(ruta);
        }
    }

    // ── aprobar ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aprobar")
    class Aprobar {

        @Test
        void rutaExistente_marcaAprobada() {
            Ruta ruta = rutaTrekking();
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));

            service.aprobar(RUTA_ID, SOCIO_ID);

            assertTrue(ruta.getAprobada());
            assertEquals(socio, ruta.getAprobadaPor());
            assertNotNull(ruta.getAprobadaEn());
        }

        @Test
        void idInexistente_lanzaRutaNotFound() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> service.aprobar(RUTA_ID, SOCIO_ID));
            assertEquals(ErrorCode.RUTA_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── eliminar ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminar")
    class Eliminar {

        @Test
        void sinSalidas_eliminaRuta() {
            Ruta ruta = rutaTrekking();
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));
            when(salidaRepository.existsByRutaIdAndEliminadaFalse(RUTA_ID)).thenReturn(false);

            service.eliminar(RUTA_ID);

            verify(rutaRepository).delete(ruta);
        }

        @Test
        void conSalidas_lanzaConflict() {
            Ruta ruta = rutaTrekking();
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));
            when(salidaRepository.existsByRutaIdAndEliminadaFalse(RUTA_ID)).thenReturn(true);

            var ex = assertThrows(BusinessException.class, () -> service.eliminar(RUTA_ID));
            assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
            verify(rutaRepository, never()).delete(any(Ruta.class));
        }

        @Test
        void idInexistente_lanzaRutaNotFound() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> service.eliminar(RUTA_ID));
            assertEquals(ErrorCode.RUTA_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── listarEquipos ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listarEquipos")
    class ListarEquipos {

        @Test
        void devuelveTodosLosEquipos() {
            EquipoMontana equipo = new EquipoMontana();
            when(equipoMontanaRepository.findAll()).thenReturn(List.of(equipo));

            var result = service.listarEquipos();

            assertEquals(1, result.size());
        }

        @Test
        void sinEquipos_devuelveListaVacia() {
            when(equipoMontanaRepository.findAll()).thenReturn(List.of());

            var result = service.listarEquipos();

            assertTrue(result.isEmpty());
        }
    }

    // ── findRutaById (public) ─────────────────────────────────────────────────

    @Nested
    @DisplayName("findRutaById")
    class FindRutaById {

        @Test
        void idExistente_devuelveRuta() {
            Ruta ruta = rutaTrekking();
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));

            Ruta result = service.findRutaById(RUTA_ID);

            assertEquals(ruta, result);
        }

        @Test
        void idInexistente_lanzaRutaNotFound() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> service.findRutaById(RUTA_ID));
            assertEquals(ErrorCode.RUTA_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ruta rutaTrekking() {
        DificultadSenderismo dif = new DificultadSenderismo("F", "Fácil", null, (short) 1);
        RutaTrekking trekking = RutaTrekking.builder()
                .dificultad(dif)
                .esCircular(false)
                .fuentesAgua(false)
                .build();

        return Ruta.builder()
                .id(RUTA_ID)
                .nombre("Sendero Test")
                .tipoActividad(TipoActividad.TREKKING)
                .lugarReferencia("Bosque Norte")
                .requierePermisos(false)
                .aprobada(false)
                .propuestaPor(socio)
                .trekking(trekking)
                .build();
    }

    private Ruta rutaCiclismo() {
        RutaCiclismo ciclismo = RutaCiclismo.builder()
                .tipoBicicleta("MTB")
                .dificultadTecnica("S2")
                .build();

        return Ruta.builder()
                .id(RUTA_ID)
                .nombre("Ciclismo Test")
                .tipoActividad(TipoActividad.CICLISMO)
                .lugarReferencia("Parque")
                .requierePermisos(false)
                .aprobada(false)
                .propuestaPor(socio)
                .ciclismo(ciclismo)
                .build();
    }

    private Ruta rutaEscalada() {
        DificultadRoca roca = mockRoca();
        RutaEscalada escalada = RutaEscalada.builder()
                .dificultadRoca(roca)
                .tipoEscalada("DEPORTIVA")
                .build();

        return Ruta.builder()
                .id(RUTA_ID)
                .nombre("Escalada Test")
                .tipoActividad(TipoActividad.ESCALADA)
                .lugarReferencia("Pared")
                .requierePermisos(false)
                .aprobada(false)
                .propuestaPor(socio)
                .escalada(escalada)
                .build();
    }

    private DificultadRoca mockRoca() {
        DificultadRoca roca = mock(DificultadRoca.class);
        when(roca.getId()).thenReturn("5a");
        when(roca.getUiaa()).thenReturn("V");
        when(roca.getFrancesa()).thenReturn("5a");
        return roca;
    }

    /**
     * CreateRutaRequest field positions:
     * 1:nombre 2:tipoActividad 3:mountainId 4:lugarReferencia 5:sectorZona 6:longitudKm
     * 7:desnivelM 8:duracionDias 9:duracionHoras 10:peligrosNotas 11:requierePermisos
     * 12:documentacionUrl 13:trackUrl 14:nivelMinimoSocioId 15:escalaAlpinaIfasId
     * 16:dificultadRocaId 17:dificultadHieloId 18:compromisoId 19:yosemiteId
     * 20:saddayNivelTecnicoId 21:saddayNivelFisicoId 22:equipoMontanaId
     * 23:tipoEscalada 24:numCintas 25:alturaViaM 26:tipoRoca
     * 27:dificultadSenderismoId 28:esCircular 29:fuentesAgua 30:tipoTerreno
     * 31:tipoBicicleta 32:dificultadTecnicaCiclismo 33:superficiePredominante 34:ciclabilidadPct
     */
    private CreateRutaRequest trekkingRequest(String dificultadId) {
        return new CreateRutaRequest(
                "Sendero", TipoActividad.TREKKING,
                null, "Lugar Test", null, null, null, null, null, null, false, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                dificultadId, false, false, null,
                null, null, null, null
        );
    }

    private CreateRutaRequest ciclismoRequest() {
        return new CreateRutaRequest(
                "Ruta Ciclismo", TipoActividad.CICLISMO,
                null, "Lugar Test", null, null, null, null, null, null, false, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                null, false, false, null,
                "MTB", "S1", null, null
        );
    }

    private CreateRutaRequest escaladaRequest(String rocaId) {
        return new CreateRutaRequest(
                "Vía Escalada", TipoActividad.ESCALADA,
                null, "Pared", null, null, null, null, null, null, false, null, null, null,
                null, rocaId, null, null, null, null, null, null,
                "DEPORTIVA", null, null, null,
                null, false, false, null,
                null, null, null, null
        );
    }

    private UpdateRutaRequest trekkingUpdateRequest(String dificultadId) {
        return new UpdateRutaRequest(
                "Sendero Actualizado", TipoActividad.TREKKING,
                null, "Lugar Nuevo", null, null, null, null, null, null, false, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                dificultadId, false, false, null,
                null, null, null, null
        );
    }
}
