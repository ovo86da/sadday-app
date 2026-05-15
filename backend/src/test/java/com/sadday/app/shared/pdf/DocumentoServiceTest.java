package com.sadday.app.shared.pdf;

import com.sadday.app.shared.entity.Documento;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.repository.DocumentoRepository;
import com.sadday.app.shared.storage.StorageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentoService — Unit Tests")
class DocumentoServiceTest {

    @Mock StorageService      storageService;
    @Mock DocumentoRepository documentoRepository;

    @InjectMocks DocumentoService service;

    private static final byte[]  PDF_BYTES  = "fake-pdf-content".getBytes(StandardCharsets.UTF_8);
    private static final String  OBJECT_KEY = "informes/uuid/informe.pdf";
    private static final String  FILENAME   = "informe.pdf";

    // ── guardar ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("guardar")
    class Guardar {

        @Test
        void storageDisponible_guardaYRetornaDocumento() {
            Documento saved = Documento.builder().objectKey(OBJECT_KEY).filename(FILENAME).build();
            when(storageService.upload(any(), eq(OBJECT_KEY), eq("application/pdf"))).thenReturn("etag-abc");
            when(documentoRepository.save(any())).thenReturn(saved);

            Documento result = service.guardar(PDF_BYTES, OBJECT_KEY, FILENAME);

            assertNotNull(result);
            verify(storageService).upload(PDF_BYTES, OBJECT_KEY, "application/pdf");
            verify(documentoRepository).save(any());
        }

        @Test
        void storageNoDisponible_retornaNull() {
            when(storageService.upload(any(), any(), any()))
                    .thenThrow(new RuntimeException("MinIO not available"));

            Documento result = service.guardar(PDF_BYTES, OBJECT_KEY, FILENAME);

            assertNull(result);
            verify(documentoRepository, never()).save(any());
        }
    }

    // ── guardarArchivo ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("guardarArchivo")
    class GuardarArchivo {

        @Test
        void archivoDemasiadoGrande_lanzaFileTooLarge() {
            byte[] bigContent = new byte[(int) (11L * 1024 * 1024)];
            MultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", bigContent);

            var ex = assertThrows(BusinessException.class, () -> service.guardarArchivo(file, OBJECT_KEY));
            assertEquals(ErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
        }

        @Test
        void extensionInvalida_lanzaInvalidFileType() {
            MultipartFile file = new MockMultipartFile("file", "test.exe", "application/octet-stream",
                    PDF_BYTES);

            var ex = assertThrows(BusinessException.class, () -> service.guardarArchivo(file, OBJECT_KEY));
            assertEquals(ErrorCode.INVALID_FILE_TYPE, ex.getErrorCode());
        }

        @Test
        void contentTypeInvalido_lanzaInvalidFileType() {
            MultipartFile file = new MockMultipartFile("file", "test.pdf", "image/jpeg", PDF_BYTES);

            var ex = assertThrows(BusinessException.class, () -> service.guardarArchivo(file, OBJECT_KEY));
            assertEquals(ErrorCode.INVALID_FILE_TYPE, ex.getErrorCode());
        }

        @Test
        void contentTypeNull_lanzaInvalidFileType() {
            MultipartFile file = new MockMultipartFile("file", "test.pdf", null, PDF_BYTES);

            var ex = assertThrows(BusinessException.class, () -> service.guardarArchivo(file, OBJECT_KEY));
            assertEquals(ErrorCode.INVALID_FILE_TYPE, ex.getErrorCode());
        }

        @Test
        void storageNoDisponible_lanzaInternalError() {
            MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);
            when(storageService.upload(any(), any(), any()))
                    .thenThrow(new RuntimeException("Storage error"));

            var ex = assertThrows(BusinessException.class, () -> service.guardarArchivo(file, OBJECT_KEY));
            assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
        }

        @Test
        void archivoValido_guardaYRetornaDocumento() {
            MultipartFile file = new MockMultipartFile("file", "informe.pdf", "application/pdf", PDF_BYTES);
            Documento saved = Documento.builder().objectKey(OBJECT_KEY).build();
            when(storageService.upload(any(), eq(OBJECT_KEY), eq("application/pdf"))).thenReturn("etag-xyz");
            when(documentoRepository.save(any())).thenReturn(saved);

            Documento result = service.guardarArchivo(file, OBJECT_KEY);

            assertNotNull(result);
            verify(documentoRepository).save(any());
        }

        @Test
        void archivoDocx_esValido() {
            byte[] content = "docx-content".getBytes(StandardCharsets.UTF_8);
            MultipartFile file = new MockMultipartFile("file", "acta.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);
            when(storageService.upload(any(), any(), any())).thenReturn("etag");
            when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() -> service.guardarArchivo(file, "docs/acta.docx"));
        }
    }

    // ── descargar ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("descargar")
    class Descargar {

        @Test
        void delegaAStorage() {
            Documento doc = Documento.builder().objectKey(OBJECT_KEY).build();
            when(storageService.download(OBJECT_KEY)).thenReturn(PDF_BYTES);

            byte[] result = service.descargar(doc);

            assertArrayEquals(PDF_BYTES, result);
            verify(storageService).download(OBJECT_KEY);
        }
    }

    // ── eliminarDelStorage ────────────────────────────────────────────────────

    @Nested
    @DisplayName("eliminarDelStorage")
    class EliminarDelStorage {

        @Test
        void storageDisponible_eliminaObjeto() {
            service.eliminarDelStorage(OBJECT_KEY);

            verify(storageService).delete(OBJECT_KEY);
        }

        @Test
        void storageNoDisponible_fallaEnSilencio() {
            doThrow(new RuntimeException("Storage error")).when(storageService).delete(any());

            assertDoesNotThrow(() -> service.eliminarDelStorage(OBJECT_KEY));
        }
    }
}
