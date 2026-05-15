package com.sadday.app.actas.service;

import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ActaMdFileValidator — Unit Tests")
class ActaMdFileValidatorTest {

    private ActaMdFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ActaMdFileValidator();
    }

    // ── validarNoVacio ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validarNoVacio")
    class ValidarNoVacio {

        @Test
        void archivoVacio_lanzaValidationError() {
            MultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", new byte[0]);

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── validarExtension ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("validarExtension")
    class ValidarExtension {

        @Test
        void extensionInvalida_lanzaValidationError() {
            MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain",
                    "# Contenido".getBytes(StandardCharsets.UTF_8));

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void nombreNull_lanzaValidationError() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getOriginalFilename()).thenReturn(null);

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void pathTraversal_lanzaValidationError() {
            MultipartFile file = new MockMultipartFile("file", "../test.md", "text/markdown",
                    "# Contenido".getBytes(StandardCharsets.UTF_8));

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── validarTamanio ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validarTamanio")
    class ValidarTamanio {

        @Test
        void archivoDemasiadoGrande_lanzaValidationError() {
            byte[] bigContent = new byte[257 * 1024];
            MultipartFile file = new MockMultipartFile("file", "big.md", "text/markdown", bigContent);

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── validarContentType ────────────────────────────────────────────────────

    @Nested
    @DisplayName("validarContentType")
    class ValidarContentType {

        @Test
        void contentTypeInvalido_lanzaValidationError() {
            MultipartFile file = new MockMultipartFile("file", "test.md", "image/jpeg",
                    "# Contenido".getBytes(StandardCharsets.UTF_8));

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void contentTypeNull_pasaValidacion() {
            // null content type is allowed (no block)
            MultipartFile file = new MockMultipartFile("file", "test.md", null,
                    "# Contenido válido".getBytes(StandardCharsets.UTF_8));

            assertDoesNotThrow(() -> validator.validarYLeer(file));
        }
    }

    // ── leerBytes ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("leerBytes — IOException")
    class LeerBytes {

        @Test
        void ioException_lanzaValidationError() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getOriginalFilename()).thenReturn("test.md");
            when(file.getSize()).thenReturn(10L);
            when(file.getContentType()).thenReturn("text/markdown");
            when(file.getBytes()).thenThrow(new IOException("Error de lectura"));

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── validarSinBytesNulos ──────────────────────────────────────────────────

    @Nested
    @DisplayName("validarSinBytesNulos")
    class ValidarSinBytesNulos {

        @Test
        void contenidoBinarioConNulos_lanzaValidationError() {
            byte[] binario = new byte[]{'#', ' ', 'T', 'e', 's', 't', 0x00, 'e', 'x', 't'};
            MultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", binario);

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── validarUtf8Estricto ───────────────────────────────────────────────────

    @Nested
    @DisplayName("validarUtf8Estricto")
    class ValidarUtf8Estricto {

        @Test
        void utf8Invalido_lanzaValidationError() {
            // bytes inválidos en UTF-8
            byte[] invalido = new byte[]{(byte) 0xFF, (byte) 0xFE, 'T', 'e', 's', 't'};
            MultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", invalido);

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── validarLongitudTexto ──────────────────────────────────────────────────

    @Nested
    @DisplayName("validarLongitudTexto")
    class ValidarLongitudTexto {

        @Test
        void textoDemasiadoLargo_lanzaValidationError() {
            String largo = "A".repeat(50_001);
            MultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown",
                    largo.getBytes(StandardCharsets.UTF_8));

            var ex = assertThrows(BusinessException.class, () -> validator.validarYLeer(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── validarYLeer — happy path ─────────────────────────────────────────────

    @Nested
    @DisplayName("validarYLeer — happy path")
    class HappyPath {

        @Test
        void archivoValidoMd_retornaContenido() {
            String contenido = "# Acta de reunión\n\nTexto del acta.";
            MultipartFile file = new MockMultipartFile("file", "acta.md", "text/markdown",
                    contenido.getBytes(StandardCharsets.UTF_8));

            String result = validator.validarYLeer(file);

            assertEquals(contenido, result);
        }

        @Test
        void extensionMdMayusculas_esValida() {
            String contenido = "# Acta";
            MultipartFile file = new MockMultipartFile("file", "ACTA.MD", "text/markdown",
                    contenido.getBytes(StandardCharsets.UTF_8));

            assertDoesNotThrow(() -> validator.validarYLeer(file));
        }
    }
}
