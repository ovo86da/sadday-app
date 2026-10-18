package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.RutaDocumentoResponse;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.entity.RutaDocumento;
import com.sadday.app.mountains.entity.RutaDocumentoConfirmacionId;
import com.sadday.app.mountains.entity.TipoDocumentoRuta;
import com.sadday.app.mountains.repository.RutaDocumentoConfirmacionRepository;
import com.sadday.app.mountains.repository.RutaDocumentoRepository;
import com.sadday.app.mountains.repository.RutaRepository;
import com.sadday.app.shared.entity.Documento;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.pdf.DocumentoService;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RutaDocumentoService — Unit Tests")
class RutaDocumentoServiceTest {

    @Mock RutaDocumentoRepository rutaDocumentoRepository;
    @Mock RutaRepository          rutaRepository;
    @Mock SocioRepository         socioRepository;
    @Mock DocumentoService        documentoService;
    @Mock RutaDocumentoConfirmacionRepository confirmacionRepository;

    @InjectMocks RutaDocumentoService rutaDocumentoService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private static final Integer RUTA_ID   = 1;
    private static final UUID    SOCIO_ID  = UUID.randomUUID();
    private static final UUID    DOC_ID    = UUID.randomUUID();

    private Ruta mockRuta() {
        return Ruta.builder().id(RUTA_ID).nombre("Ruta Test").build();
    }

    private Socio mockSocio() {
        return Socio.builder()
                .id(SOCIO_ID)
                .nombre("Juan")
                .apellido("Pérez")
                .build();
    }

    private Documento mockDocumento() {
        Documento doc = mock(Documento.class);
        when(doc.getId()).thenReturn(DOC_ID);
        when(doc.getFilename()).thenReturn("guia.pdf");
        when(doc.getContentType()).thenReturn("application/pdf");
        when(doc.getSizeBytes()).thenReturn(1024L);
        when(doc.getObjectKey()).thenReturn("rutas/1/permisos/test-guia.pdf");
        return doc;
    }

    private RutaDocumento mockRutaDocumento(Documento doc, Socio subidoPor) {
        RutaDocumento rd = mock(RutaDocumento.class);
        when(rd.getId()).thenReturn(DOC_ID);
        when(rd.getDocumento()).thenReturn(doc);
        when(rd.getSubidoPor()).thenReturn(subidoPor);
        when(rd.getRuta()).thenReturn(mockRuta());
        when(rd.getCreatedAt()).thenReturn(LocalDateTime.now());
        return rd;
    }

    // =========================================================================
    // subir
    // =========================================================================

    @Nested
    @DisplayName("subir")
    class Subir {

        @Test
        @DisplayName("subir — ruta no encontrada → lanza RUTA_NOT_FOUND")
        void subir_rutaNoEncontrada_lanzaRutaNotFound() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.empty());
            MultipartFile file = mock(MultipartFile.class);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.PERMISO, SOCIO_ID, null));

            assertEquals(ErrorCode.RUTA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("subir — socio no encontrado → lanza SOCIO_NOT_FOUND")
        void subir_socioNoEncontrado_lanzaSocioNotFound() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(mockRuta()));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.empty());
            MultipartFile file = mock(MultipartFile.class);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.PERMISO, SOCIO_ID, null));

            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("subir — archivo válido → guarda documento y retorna response")
        void subir_archivoValido_guardaYRetornaResponse() {
            Ruta ruta     = mockRuta();
            Socio socio   = mockSocio();
            Documento doc = mockDocumento();

            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(socio));

            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("guia.pdf");

            when(documentoService.guardarArchivo(eq(file), anyString())).thenReturn(doc);

            RutaDocumento rdGuardado = mockRutaDocumento(doc, socio);
            when(rutaDocumentoRepository.save(any(RutaDocumento.class))).thenReturn(rdGuardado);

            RutaDocumentoResponse resp = rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.PERMISO, SOCIO_ID, null);

            assertNotNull(resp);
            assertEquals("guia.pdf", resp.filename());
            assertEquals("application/pdf", resp.contentType());
            assertEquals("Juan Pérez", resp.subidoPorNombre());
            verify(documentoService).guardarArchivo(eq(file), anyString());
            verify(rutaDocumentoRepository).save(any(RutaDocumento.class));
        }
    }

    // =========================================================================
    // eliminar
    // =========================================================================

    @Nested
    @DisplayName("eliminar")
    class Eliminar {

        @Test
        @DisplayName("eliminar — documento no encontrado → lanza RUTA_DOCUMENTO_NOT_FOUND")
        void eliminar_noEncontrado_lanzaRutaDocumentoNotFound() {
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.eliminar(RUTA_ID, DOC_ID, SOCIO_ID));

            assertEquals(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("eliminar — documento válido → elimina registro y borra del storage")
        void eliminar_valido_eliminaYBorraDelStorage() {
            Documento doc = mockDocumento();
            RutaDocumento rd = mockRutaDocumento(doc, mockSocio());
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID))
                    .thenReturn(Optional.of(rd));

            rutaDocumentoService.eliminar(RUTA_ID, DOC_ID, SOCIO_ID);

            verify(rutaDocumentoRepository).delete(rd);
            verify(documentoService).eliminarDelStorage("rutas/1/permisos/test-guia.pdf");
        }
    }

    // =========================================================================
    // listar
    // =========================================================================

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("listar — delega a listarPorRuta")
        void listar_delegaAListarPorRuta() {
            Documento doc = mockDocumento();
            RutaDocumento rd = mockRutaDocumento(doc, null);
            when(rutaDocumentoRepository.findByRutaIdOrderByCreatedAtAsc(RUTA_ID))
                    .thenReturn(List.of(rd));

            List<RutaDocumentoResponse> result = rutaDocumentoService.listar(RUTA_ID);

            assertEquals(1, result.size());
            verify(rutaDocumentoRepository).findByRutaIdOrderByCreatedAtAsc(RUTA_ID);
        }
    }

    // =========================================================================
    // descargar
    // =========================================================================

    @Nested
    @DisplayName("descargar")
    class Descargar {

        @Test
        @DisplayName("descargar — documento no encontrado → lanza RUTA_DOCUMENTO_NOT_FOUND")
        void descargar_noEncontrado_lanzaRutaDocumentoNotFound() {
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.descargar(RUTA_ID, DOC_ID));

            assertEquals(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("descargar — documento válido → retorna bytes, filename y contentType")
        void descargar_valido_retornaBytes() {
            Documento doc = mockDocumento();
            RutaDocumento rd = mockRutaDocumento(doc, null);
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID))
                    .thenReturn(Optional.of(rd));

            byte[] contenido = "contenido-pdf".getBytes();
            when(documentoService.descargar(doc)).thenReturn(contenido);

            RutaDocumentoService.DescargaDocumento descarga =
                    rutaDocumentoService.descargar(RUTA_ID, DOC_ID);

            assertArrayEquals(contenido, descarga.bytes());
            assertEquals("guia.pdf", descarga.filename());
            assertEquals("application/pdf", descarga.contentType());
        }
    }

    // =========================================================================
    // listarPorRuta
    // =========================================================================

    @Nested
    @DisplayName("listarPorRuta")
    class ListarPorRuta {

        @Test
        @DisplayName("listarPorRuta — retorna lista mapeada a responses")
        void listarPorRuta_retornaLista() {
            Documento doc = mockDocumento();
            RutaDocumento rd = mockRutaDocumento(doc, null);
            when(rutaDocumentoRepository.findByRutaIdOrderByCreatedAtAsc(RUTA_ID))
                    .thenReturn(List.of(rd));

            List<RutaDocumentoResponse> result = rutaDocumentoService.listarPorRuta(RUTA_ID);

            assertEquals(1, result.size());
            assertEquals("guia.pdf", result.get(0).filename());
            assertNull(result.get(0).subidoPorNombre());
        }
    }

    // =========================================================================
    // FR-022: tipo de documento, reemplazo del track y confirmaciones
    // =========================================================================

    @Nested
    @DisplayName("subir un track")
    class SubirTrack {

        @Test
        @DisplayName("marcado como TRACK_GPX pero el fichero no es .gpx → INVALID_FILE_TYPE")
        void tipoTrackConFicheroQueNoEsGpx_rechaza() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(mockRuta()));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(mockSocio()));
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("mapa.pdf");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.TRACK_GPX, SOCIO_ID, null));

            assertEquals(ErrorCode.INVALID_FILE_TYPE, ex.getErrorCode());
            // No debe haber tocado el storage si el tipo y el fichero no cuadran
            verify(documentoService, never()).guardarArchivo(any(), anyString());
        }

        @Test
        @DisplayName("un segundo track sin indicar reemplazo → RESOURCE_CONFLICT, no borra nada")
        void segundoTrackSinReemplazar_rechaza() {
            RutaDocumento previo = mock(RutaDocumento.class);
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(mockRuta()));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(mockSocio()));
            when(rutaDocumentoRepository.findByRutaIdAndTipoAndSubidoPorId(
                    RUTA_ID, TipoDocumentoRuta.TRACK_GPX, SOCIO_ID)).thenReturn(List.of(previo));
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("nuevo.gpx");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.TRACK_GPX, SOCIO_ID, null));

            assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
            // Lo importante: el track anterior sigue intacto
            verify(rutaDocumentoRepository, never()).delete(any());
            verify(documentoService, never()).eliminarDelStorage(anyString());
        }

        @Test
        @DisplayName("indicando reemplazarId → borra el anterior y sube el nuevo")
        void conReemplazarId_sustituye() {
            UUID viejoId = UUID.randomUUID();
            Documento docViejo = mock(Documento.class);
            when(docViejo.getObjectKey()).thenReturn("rutas/1/tracks/viejo.gpx");
            RutaDocumento previo = mock(RutaDocumento.class);
            when(previo.getId()).thenReturn(viejoId);
            when(previo.getDocumento()).thenReturn(docViejo);

            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(mockRuta()));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(mockSocio()));
            when(rutaDocumentoRepository.findByRutaIdAndTipoAndSubidoPorId(
                    RUTA_ID, TipoDocumentoRuta.TRACK_GPX, SOCIO_ID)).thenReturn(List.of(previo));
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("nuevo.gpx");
            Documento docNuevo = mockDocumento();
            when(documentoService.guardarArchivo(eq(file), anyString())).thenReturn(docNuevo);
            RutaDocumento guardado = mockRutaDocumento(docNuevo, mockSocio());
            when(rutaDocumentoRepository.save(any(RutaDocumento.class))).thenReturn(guardado);

            rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.TRACK_GPX, SOCIO_ID, viejoId);

            verify(rutaDocumentoRepository).delete(previo);
            verify(documentoService).eliminarDelStorage("rutas/1/tracks/viejo.gpx");
        }

        @Test
        @DisplayName("no se puede reemplazar un documento que no es tuyo")
        void reemplazarAjeno_rechaza() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(mockRuta()));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(mockSocio()));
            when(rutaDocumentoRepository.findByRutaIdAndTipoAndSubidoPorId(
                    RUTA_ID, TipoDocumentoRuta.TRACK_GPX, SOCIO_ID)).thenReturn(List.of());
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("nuevo.gpx");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.TRACK_GPX,
                                                     SOCIO_ID, UUID.randomUUID()));

            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        @DisplayName("los mapas admiten hasta 3 por socio: el tercero entra, el cuarto no")
        void limiteDeMapas() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(mockRuta()));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(mockSocio()));
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("mapa.pdf");
            Documento doc = mockDocumento();
            when(documentoService.guardarArchivo(eq(file), anyString())).thenReturn(doc);
            RutaDocumento guardado = mockRutaDocumento(doc, mockSocio());
            when(rutaDocumentoRepository.save(any(RutaDocumento.class))).thenReturn(guardado);

            // con dos ya subidos, el tercero pasa
            when(rutaDocumentoRepository.findByRutaIdAndTipoAndSubidoPorId(
                    RUTA_ID, TipoDocumentoRuta.MAPA, SOCIO_ID))
                    .thenReturn(List.of(mock(RutaDocumento.class), mock(RutaDocumento.class)));
            assertDoesNotThrow(() ->
                    rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.MAPA, SOCIO_ID, null));

            // con tres, el cuarto no
            when(rutaDocumentoRepository.findByRutaIdAndTipoAndSubidoPorId(
                    RUTA_ID, TipoDocumentoRuta.MAPA, SOCIO_ID))
                    .thenReturn(List.of(mock(RutaDocumento.class), mock(RutaDocumento.class),
                                        mock(RutaDocumento.class)));
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.MAPA, SOCIO_ID, null));
            assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
        }

        @Test
        @DisplayName("el track se guarda bajo rutas/{id}/tracks/, no en permisos")
        void trackSeGuardaEnSuCarpeta() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(mockRuta()));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(mockSocio()));
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("ruta.gpx");
            Documento doc = mockDocumento();
            when(documentoService.guardarArchivo(eq(file), anyString())).thenReturn(doc);
            RutaDocumento guardado = mockRutaDocumento(doc, mockSocio());
            when(rutaDocumentoRepository.save(any(RutaDocumento.class))).thenReturn(guardado);

            rutaDocumentoService.subir(RUTA_ID, file, TipoDocumentoRuta.TRACK_GPX, SOCIO_ID, null);

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(documentoService).guardarArchivo(eq(file), key.capture());
            assertTrue(key.getValue().startsWith("rutas/1/tracks/"),
                    "esperaba carpeta de tracks, fue: " + key.getValue());
        }
    }

    @Nested
    @DisplayName("eliminar segun quien lo subio")
    class EliminarAutor {

        @Test
        @DisplayName("un socio que no lo subio y no es directiva → ACCESS_DENIED")
        void noAutorSinPrivilegios_rechaza() {
            RutaDocumento rd = mockRutaDocumento(mockDocumento(), mockSocio());
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID)).thenReturn(Optional.of(rd));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.eliminar(RUTA_ID, DOC_ID, UUID.randomUUID()));

            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
            verify(rutaDocumentoRepository, never()).delete(any());
            verify(documentoService, never()).eliminarDelStorage(anyString());
        }
    }

    @Nested
    @DisplayName("confirmaciones")
    class Confirmaciones {

        @Test
        @DisplayName("no se puede avalar un documento propio")
        void confirmarPropio_rechaza() {
            RutaDocumento rd = mockRutaDocumento(mockDocumento(), mockSocio());
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID)).thenReturn(Optional.of(rd));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.confirmar(RUTA_ID, DOC_ID, SOCIO_ID));

            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
            verify(confirmacionRepository, never()).save(any());
        }

        @Test
        @DisplayName("confirmar dos veces → RESOURCE_CONFLICT")
        void confirmarDosVeces_rechaza() {
            RutaDocumento rd = mockRutaDocumento(mockDocumento(), mockSocio());
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID)).thenReturn(Optional.of(rd));
            when(confirmacionRepository.existsById(any(RutaDocumentoConfirmacionId.class))).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.confirmar(RUTA_ID, DOC_ID, UUID.randomUUID()));

            assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
            verify(confirmacionRepository, never()).save(any());
        }

        @Test
        @DisplayName("confirmar de otro socio → guarda y devuelve el total")
        void confirmarDeOtro_guardaYDevuelveTotal() {
            UUID otro = UUID.randomUUID();
            RutaDocumento rd = mockRutaDocumento(mockDocumento(), mockSocio());
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID)).thenReturn(Optional.of(rd));
            when(confirmacionRepository.existsById(any(RutaDocumentoConfirmacionId.class))).thenReturn(false);
            when(socioRepository.findById(otro)).thenReturn(Optional.of(mockSocio()));
            when(confirmacionRepository.countByIdRutaDocumentoId(DOC_ID)).thenReturn(3L);

            long total = rutaDocumentoService.confirmar(RUTA_ID, DOC_ID, otro);

            assertEquals(3L, total);
            verify(confirmacionRepository).save(any());
        }

        @Test
        @DisplayName("retirar una confirmacion que no existe → error")
        void retirarSinHaberConfirmado_rechaza() {
            RutaDocumento rd = mockRutaDocumento(mockDocumento(), mockSocio());
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID)).thenReturn(Optional.of(rd));
            when(confirmacionRepository.existsById(any(RutaDocumentoConfirmacionId.class))).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.retirarConfirmacion(RUTA_ID, DOC_ID, UUID.randomUUID()));

            verify(confirmacionRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("retirar la propia confirmacion → la borra y devuelve el total")
        void retirarPropia_borraYDevuelveTotal() {
            UUID otro = UUID.randomUUID();
            RutaDocumento rd = mockRutaDocumento(mockDocumento(), mockSocio());
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID)).thenReturn(Optional.of(rd));
            when(confirmacionRepository.existsById(any(RutaDocumentoConfirmacionId.class))).thenReturn(true);
            when(confirmacionRepository.countByIdRutaDocumentoId(DOC_ID)).thenReturn(1L);

            long total = rutaDocumentoService.retirarConfirmacion(RUTA_ID, DOC_ID, otro);

            assertEquals(1L, total);
            verify(confirmacionRepository).deleteById(any(RutaDocumentoConfirmacionId.class));
        }
    }
}
