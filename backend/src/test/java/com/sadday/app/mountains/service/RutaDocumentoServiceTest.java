package com.sadday.app.mountains.service;

import com.sadday.app.mountains.dto.RutaDocumentoResponse;
import com.sadday.app.mountains.entity.Ruta;
import com.sadday.app.mountains.entity.RutaDocumento;
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
                    () -> rutaDocumentoService.subir(RUTA_ID, file, SOCIO_ID));

            assertEquals(ErrorCode.RUTA_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("subir — socio no encontrado → lanza SOCIO_NOT_FOUND")
        void subir_socioNoEncontrado_lanzaSocioNotFound() {
            when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(mockRuta()));
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.empty());
            MultipartFile file = mock(MultipartFile.class);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rutaDocumentoService.subir(RUTA_ID, file, SOCIO_ID));

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

            RutaDocumentoResponse resp = rutaDocumentoService.subir(RUTA_ID, file, SOCIO_ID);

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
                    () -> rutaDocumentoService.eliminar(RUTA_ID, DOC_ID));

            assertEquals(ErrorCode.RUTA_DOCUMENTO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("eliminar — documento válido → elimina registro y borra del storage")
        void eliminar_valido_eliminaYBorraDelStorage() {
            Documento doc = mockDocumento();
            RutaDocumento rd = mockRutaDocumento(doc, null);
            when(rutaDocumentoRepository.findByIdAndRutaId(DOC_ID, RUTA_ID))
                    .thenReturn(Optional.of(rd));

            rutaDocumentoService.eliminar(RUTA_ID, DOC_ID);

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
}
