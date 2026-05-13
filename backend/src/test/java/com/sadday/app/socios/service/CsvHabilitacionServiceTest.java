package com.sadday.app.socios.service;

import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.storage.StorageService;
import com.sadday.app.socios.dto.CsvHabilitacionResult;
import com.sadday.app.socios.entity.EstadoHabilitacion;
import com.sadday.app.socios.entity.RolSistema;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.EstadoHabilitacionRepository;
import com.sadday.app.socios.repository.SocioHabilitacionLogRepository;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CsvHabilitacionService — Unit Tests")
class CsvHabilitacionServiceTest {

    @Mock SocioRepository                socioRepository;
    @Mock EstadoHabilitacionRepository   estadoHabRepo;
    @Mock SocioHabilitacionLogRepository logRepository;
    @Mock StorageService                 storageService;

    @InjectMocks CsvHabilitacionService service;

    private final UUID adminId = UUID.randomUUID();

    private EstadoHabilitacion estadoHabilitado;
    private EstadoHabilitacion estadoInhabilitado;
    private EstadoHabilitacion estadoVitalicio;

    @BeforeEach
    void setUp() {
        estadoHabilitado   = mockEstado((short) 1, "Habilitado");
        estadoInhabilitado = mockEstado((short) 2, "Inhabilitado");
        estadoVitalicio    = mockEstado((short) 3, "Socio Vitalicio");

        when(estadoHabRepo.findByNombre("Habilitado"))     .thenReturn(Optional.of(estadoHabilitado));
        when(estadoHabRepo.findByNombre("Inhabilitado"))   .thenReturn(Optional.of(estadoInhabilitado));
        when(estadoHabRepo.findByNombre("Socio Vitalicio")).thenReturn(Optional.of(estadoVitalicio));

        Socio admin = socioConRol("ADMIN", estadoHabilitado);
        when(socioRepository.findById(adminId)).thenReturn(Optional.of(admin));
    }

    // ── Validación de archivo ─────────────────────────────────────────────────

    @Nested
    @DisplayName("validarArchivo")
    class ValidarArchivo {

        @Test
        void archivoNulo_lanzaValidationError() {
            var ex = assertThrows(BusinessException.class,
                    () -> service.procesarCsv(null, adminId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void archivoVacio_lanzaValidationError() throws Exception {
            MultipartFile file = mockFile("test.csv", "text/csv", new byte[0], true);
            var ex = assertThrows(BusinessException.class,
                    () -> service.procesarCsv(file, adminId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void sinExtensionCsv_lanzaValidationError() throws Exception {
            MultipartFile file = mockFile("archivo.xlsx", "text/csv",
                    "Nombre,Cedula,Estado\nJuan,123,Habilitado".getBytes(), false);
            var ex = assertThrows(BusinessException.class,
                    () -> service.procesarCsv(file, adminId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void contenidoBinario_lanzaValidationError() throws Exception {
            byte[] binario = new byte[]{'N', 'o', 'm', 0x00, 'b', 'r', 'e'};
            MultipartFile file = mockFile("test.csv", "text/csv", binario, false);
            var ex = assertThrows(BusinessException.class,
                    () -> service.procesarCsv(file, adminId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void soloEncabezado_sinDatos_lanzaValidationError() throws Exception {
            byte[] csv = "Nombre,Cedula,Estado\n".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);
            var ex = assertThrows(BusinessException.class,
                    () -> service.procesarCsv(file, adminId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void sinColumnaCedula_lanzaValidationError() throws Exception {
            byte[] csv = "Nombre,X,Estado\nJuan,123,Habilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);
            var ex = assertThrows(BusinessException.class,
                    () -> service.procesarCsv(file, adminId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void pathTraversalEnNombre_lanzaValidationError() throws Exception {
            MultipartFile file = mockFile("../etc/passwd.csv", "text/csv",
                    "Nombre,Cedula,Estado\nJuan,123,Habilitado".getBytes(), false);
            var ex = assertThrows(BusinessException.class,
                    () -> service.procesarCsv(file, adminId));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ── Procesamiento de filas ────────────────────────────────────────────────

    @Nested
    @DisplayName("procesarEnTransaccion")
    class Procesamiento {

        @Test
        void filaValida_habilitaSocio() throws Exception {
            Socio socio = socioConRol("SOCIO", estadoInhabilitado);
            when(socioRepository.findByCedula("0102030405")).thenReturn(Optional.of(socio));
            when(socioRepository.save(any())).thenReturn(socio);

            byte[] csv = "Nombre,Cedula,Estado\nJuan,0102030405,Habilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.habilitados());
            assertEquals(0, result.errores().size());
            verify(logRepository).save(any());
        }

        @Test
        void filaValida_inhabilitaSocio() throws Exception {
            Socio socio = socioConRol("SOCIO", estadoHabilitado);
            when(socioRepository.findByCedula("0102030405")).thenReturn(Optional.of(socio));
            when(socioRepository.save(any())).thenReturn(socio);

            byte[] csv = "Nombre,Cedula,Estado\nJuan,0102030405,Deshabilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.deshabilitados());
        }

        @Test
        void cedulaNoEncontrada_agregaError() throws Exception {
            when(socioRepository.findByCedula("9999999999")).thenReturn(Optional.empty());

            byte[] csv = "Nombre,Cedula,Estado\nJuan,9999999999,Habilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.errores().size());
            assertTrue(result.errores().get(0).motivo().contains("no encontrada"));
        }

        @Test
        void socioVitalicio_agregaError() throws Exception {
            Socio socio = socioConRol("SOCIO", estadoVitalicio);
            when(socioRepository.findByCedula("0102030405")).thenReturn(Optional.of(socio));

            byte[] csv = "Nombre,Cedula,Estado\nJuan,0102030405,Deshabilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.errores().size());
            assertTrue(result.errores().get(0).motivo().contains("Vitalicio"));
        }

        @Test
        void rolAdmin_noSePuedeInhabilitar() throws Exception {
            Socio socio = socioConRol("ADMIN", estadoHabilitado);
            when(socioRepository.findByCedula("0102030405")).thenReturn(Optional.of(socio));

            byte[] csv = "Nombre,Cedula,Estado\nAdmin,0102030405,Deshabilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.errores().size());
            assertTrue(result.errores().get(0).motivo().contains("ADMIN"));
        }

        @Test
        void rolSecretaria_noSePuedeInhabilitar() throws Exception {
            Socio socio = socioConRol("SECRETARIA", estadoHabilitado);
            when(socioRepository.findByCedula("0102030405")).thenReturn(Optional.of(socio));

            byte[] csv = "Nombre,Cedula,Estado\nSec,0102030405,Deshabilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.errores().size());
        }

        @Test
        void mismoEstado_contaSinCambio() throws Exception {
            Socio socio = socioConRol("SOCIO", estadoHabilitado);
            when(socioRepository.findByCedula("0102030405")).thenReturn(Optional.of(socio));

            byte[] csv = "Nombre,Cedula,Estado\nJuan,0102030405,Habilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.sinCambio());
        }

        @Test
        void formulaEnCedula_agregaError() throws Exception {
            byte[] csv = "Nombre,Cedula,Estado\nJuan,=CMD(),Habilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.errores().size());
            assertTrue(result.errores().get(0).motivo().contains("no permitido"));
        }

        @Test
        void estadoInvalido_agregaError() throws Exception {
            byte[] csv = "Nombre,Cedula,Estado\nJuan,0102030405,Invalido".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.errores().size());
            assertTrue(result.errores().get(0).motivo().contains("Estado inválido"));
        }

        @Test
        void s3FallaUpload_continuaProcesamiento() throws Exception {
            Socio socio = socioConRol("SOCIO", estadoInhabilitado);
            when(socioRepository.findByCedula("0102030405")).thenReturn(Optional.of(socio));
            when(socioRepository.save(any())).thenReturn(socio);
            doThrow(new RuntimeException("S3 error")).when(storageService).upload(any(), any(), any());

            byte[] csv = "Nombre,Cedula,Estado\nJuan,0102030405,Habilitado".getBytes();
            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.habilitados());
        }

        @Test
        void csvConBom_seProcesamCorrectamente() throws Exception {
            Socio socio = socioConRol("SOCIO", estadoInhabilitado);
            when(socioRepository.findByCedula("1234567890")).thenReturn(Optional.of(socio));
            when(socioRepository.save(any())).thenReturn(socio);

            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] content = "Nombre,Cedula,Estado\nAna,1234567890,Habilitado".getBytes();
            byte[] csv = new byte[bom.length + content.length];
            System.arraycopy(bom, 0, csv, 0, bom.length);
            System.arraycopy(content, 0, csv, bom.length, content.length);

            MultipartFile file = mockFile("test.csv", "text/csv", csv, false);
            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.habilitados());
        }

        @Test
        void multipleFilas_procesaTodasYContabiliza() throws Exception {
            Socio s1 = socioConRol("SOCIO", estadoInhabilitado);
            Socio s2 = socioConRol("SOCIO", estadoHabilitado);
            when(socioRepository.findByCedula("1111111111")).thenReturn(Optional.of(s1));
            when(socioRepository.findByCedula("2222222222")).thenReturn(Optional.of(s2));
            when(socioRepository.findByCedula("9999999999")).thenReturn(Optional.empty());
            when(socioRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            String csvContent = "Nombre,Cedula,Estado\n" +
                    "Juan,1111111111,Habilitado\n" +
                    "Ana,2222222222,Deshabilitado\n" +
                    "X,9999999999,Habilitado\n";
            MultipartFile file = mockFile("test.csv", "text/csv", csvContent.getBytes(), false);

            CsvHabilitacionResult result = service.procesarCsv(file, adminId);

            assertEquals(1, result.habilitados());
            assertEquals(1, result.deshabilitados());
            assertEquals(1, result.errores().size());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EstadoHabilitacion mockEstado(short id, String nombre) {
        EstadoHabilitacion e = mock(EstadoHabilitacion.class);
        when(e.getId()).thenReturn(id);
        when(e.getNombre()).thenReturn(nombre);
        return e;
    }

    private Socio socioConRol(String rolNombre, EstadoHabilitacion estado) {
        RolSistema rol = mock(RolSistema.class);
        when(rol.getNombre()).thenReturn(rolNombre);
        Socio s = new Socio();
        s.setId(UUID.randomUUID());
        s.setNombre("Juan");
        s.setApellido("Pérez");
        s.setRolSistema(rol);
        s.setEstadoHabilitacion(estado);
        return s;
    }

    private MultipartFile mockFile(String filename, String contentType, byte[] bytes, boolean empty)
            throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.isEmpty()).thenReturn(empty);
        when(file.getBytes()).thenReturn(bytes);
        return file;
    }
}
