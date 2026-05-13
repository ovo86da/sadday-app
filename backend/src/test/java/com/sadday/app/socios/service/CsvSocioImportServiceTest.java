package com.sadday.app.socios.service;

import com.sadday.app.auth.service.EmailVerificationService;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.dto.CsvSocioImportPreviewResponse;
import com.sadday.app.socios.dto.CsvSocioImportPreviewResponse.FilaValida;
import com.sadday.app.socios.dto.CsvSocioImportResultResponse;
import com.sadday.app.socios.entity.ClasificacionSocio;
import com.sadday.app.socios.entity.TipoSocioClub;
import com.sadday.app.socios.repository.ClasificacionSocioRepository;
import com.sadday.app.socios.repository.SocioRepository;
import com.sadday.app.socios.repository.TipoSocioClubRepository;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CsvSocioImportService — Unit Tests")
class CsvSocioImportServiceTest {

    @Mock SocioRepository              socioRepository;
    @Mock TipoSocioClubRepository      tipoSocioRepo;
    @Mock ClasificacionSocioRepository clasifSocioRepo;
    @Mock EmailVerificationService     emailVerificationService;

    @InjectMocks CsvSocioImportService service;

    private static final String CSV_HEADER = "Cedula,Nombre,Apellido,Correo,Telefono,TipoSocio,NivelTecnico\n";
    private static final String FILA_VALIDA = "0102030405,Juan,Pérez,juan@test.com,0991234567,Socio Activo,Intermedio\n";

    @BeforeEach
    void setUp() {
        TipoSocioClub tipo = mock(TipoSocioClub.class);
        when(tipo.getNombre()).thenReturn("Socio Activo");
        when(tipoSocioRepo.findAll()).thenReturn(List.of(tipo));

        ClasificacionSocio clasif = mock(ClasificacionSocio.class);
        when(clasif.getNombre()).thenReturn("Intermedio");
        when(clasifSocioRepo.findAll()).thenReturn(List.of(clasif));

        when(socioRepository.existsByCedula(anyString())).thenReturn(false);
        when(socioRepository.existsByCorreo(anyString())).thenReturn(false);
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("preview")
    class Preview {

        @Test
        void filaValida_retornaEnListaValidas() throws Exception {
            MultipartFile file = mockFile("socios.csv", (CSV_HEADER + FILA_VALIDA).getBytes());

            CsvSocioImportPreviewResponse response = service.preview(file);

            assertEquals(1, response.validas().size());
            assertEquals(0, response.errores().size());
            assertEquals("0102030405", response.validas().get(0).cedula());
        }

        @Test
        void cedulaVacia_retornaEnErrores() throws Exception {
            String csv = CSV_HEADER + ",Juan,Pérez,juan@test.com,,,\n";
            MultipartFile file = mockFile("socios.csv", csv.getBytes());

            CsvSocioImportPreviewResponse response = service.preview(file);

            assertEquals(1, response.errores().size());
            assertTrue(response.errores().get(0).motivo().contains("cédula"));
        }

        @Test
        void correoInvalido_retornaEnErrores() throws Exception {
            String csv = CSV_HEADER + "0102030405,Juan,Pérez,correo-invalido,,,\n";
            MultipartFile file = mockFile("socios.csv", csv.getBytes());

            CsvSocioImportPreviewResponse response = service.preview(file);

            assertEquals(1, response.errores().size());
            assertTrue(response.errores().get(0).motivo().contains("Correo inválido"));
        }

        @Test
        void cedulaDuplicadaEnCsv_retornaSegundaComoError() throws Exception {
            String csv = CSV_HEADER + FILA_VALIDA + "0102030405,Ana,López,ana@test.com,,,\n";
            MultipartFile file = mockFile("socios.csv", csv.getBytes());

            CsvSocioImportPreviewResponse response = service.preview(file);

            assertEquals(1, response.validas().size());
            assertEquals(1, response.errores().size());
            assertTrue(response.errores().get(0).motivo().contains("duplicada"));
        }

        @Test
        void cedulaYaExisteEnBD_retornaEnErrores() throws Exception {
            when(socioRepository.existsByCedula("0102030405")).thenReturn(true);
            MultipartFile file = mockFile("socios.csv", (CSV_HEADER + FILA_VALIDA).getBytes());

            CsvSocioImportPreviewResponse response = service.preview(file);

            assertEquals(1, response.errores().size());
            assertTrue(response.errores().get(0).motivo().contains("registrada"));
        }

        @Test
        void tipoSocioInvalido_retornaEnErrores() throws Exception {
            String csv = CSV_HEADER + "0102030405,Juan,Pérez,juan@test.com,,TipoInvalido,\n";
            MultipartFile file = mockFile("socios.csv", csv.getBytes());

            CsvSocioImportPreviewResponse response = service.preview(file);

            assertEquals(1, response.errores().size());
            assertTrue(response.errores().get(0).motivo().contains("Tipo de socio no reconocido"));
        }

        @Test
        void sinColumnaCedula_lanzaValidationError() throws Exception {
            String csv = "Nombre,Apellido,Correo\nJuan,Pérez,j@t.com\n";
            MultipartFile file = mockFile("socios.csv", csv.getBytes());

            var ex = assertThrows(BusinessException.class, () -> service.preview(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void archivoVacio_lanzaValidationError() throws Exception {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);
            when(file.getSize()).thenReturn(0L);
            when(file.getOriginalFilename()).thenReturn("socios.csv");

            var ex = assertThrows(BusinessException.class, () -> service.preview(file));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── Confirmar ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmar")
    class Confirmar {

        @Test
        void filasValidas_llamaEmailServicePorCadaFila() {
            List<FilaValida> filas = List.of(
                    new FilaValida(2, "001", "Juan", "Pérez", "j@t.com", "099", "Activo", "Básico"),
                    new FilaValida(3, "002", "Ana", "López", "a@t.com", "098", "Activo", "Básico")
            );

            CsvSocioImportResultResponse result = service.confirmar(filas);

            assertEquals(2, result.importados());
            assertEquals(0, result.errores().size());
            verify(emailVerificationService, times(2))
                    .sendCsvImportInvitation(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void emailFalla_registraErrorYContinua() {
            doThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "SMTP error"))
                    .when(emailVerificationService)
                    .sendCsvImportInvitation(eq("001"), any(), any(), any(), any(), any(), any());

            List<FilaValida> filas = List.of(
                    new FilaValida(2, "001", "Juan", "Pérez", "j@t.com", null, null, null),
                    new FilaValida(3, "002", "Ana", "López", "a@t.com", null, null, null)
            );

            CsvSocioImportResultResponse result = service.confirmar(filas);

            assertEquals(1, result.importados());
            assertEquals(1, result.errores().size());
        }

        @Test
        void listaVacia_lanzaValidationError() {
            var ex = assertThrows(BusinessException.class,
                    () -> service.confirmar(List.of()));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void listaNula_lanzaValidationError() {
            var ex = assertThrows(BusinessException.class,
                    () -> service.confirmar(null));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MultipartFile mockFile(String filename, byte[] bytes) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn("text/csv");
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.isEmpty()).thenReturn(bytes.length == 0);
        when(file.getBytes()).thenReturn(bytes);
        return file;
    }
}
