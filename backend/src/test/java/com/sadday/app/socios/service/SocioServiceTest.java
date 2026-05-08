package com.sadday.app.socios.service;

import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.auth.service.EmailVerificationService;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.dto.*;
import com.sadday.app.socios.entity.*;
import com.sadday.app.socios.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link SocioService}.
 *
 * <p>Nota: la autorización (@PreAuthorize) y auditoría (@Auditable) son aspectos AOP
 * y no se activan en tests unitarios — se prueban en los tests de integración.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SocioService — Unit Tests")
class SocioServiceTest {

    @Mock SocioRepository                   socioRepository;
    @Mock TipoSocioClubRepository           tipoSocioRepo;
    @Mock EstadoHabilitacionRepository      estadoHabRepo;
    @Mock RolSistemaRepository              rolSistemaRepo;
    @Mock ClasificacionSocioRepository      clasifSocioRepo;
    @Mock EstadoCuotaRepository             cuotaRepository;
    @Mock EmailVerificationService          emailVerificationService;
    @Mock UsuarioAuthRepository             usuarioAuthRepository;
    @Mock SocioHabilitacionLogRepository    habilitacionLogRepo;

    @InjectMocks SocioService socioService;

    // Lookup entities de referencia
    private EstadoHabilitacion estadoHabilitado;
    private EstadoHabilitacion estadoInhabilitado;
    private EstadoAcceso estadoAccesoActivo;
    private TipoSocioClub tipoPendiente;
    private TipoSocioClub tipoSocioActivo;
    private RolSistema rolSocio;
    private RolSistema rolAdmin;

    private static final UUID SOCIO_UUID = UUID.randomUUID();
    private static final UUID ADMIN_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        estadoHabilitado   = EstadoHabilitacion.builder().id((short) 1).nombre("Habilitado").descripcion("").build();
        estadoInhabilitado = EstadoHabilitacion.builder().id((short) 2).nombre("Inhabilitado").descripcion("").build();
        estadoAccesoActivo = EstadoAcceso.builder().id((short) 1).codigo("ACTIVE").nombre("Activo").build();
        tipoPendiente      = TipoSocioClub.builder().id((short) 6).nombre("Pendiente Registro").descripcion("").build();
        tipoSocioActivo    = TipoSocioClub.builder().id((short) 1).nombre("Socio Activo").descripcion("").build();
        rolSocio           = RolSistema.builder().id((short) 4).nombre("Socio").descripcion("").build();
        rolAdmin           = RolSistema.builder().id((short) 1).nombre("Admin").descripcion("").build();

        // Stubs por defecto para lookups
        when(estadoHabRepo.findByNombre("Habilitado")).thenReturn(Optional.of(estadoHabilitado));
        when(estadoHabRepo.findByNombre("Inhabilitado")).thenReturn(Optional.of(estadoInhabilitado));
        when(tipoSocioRepo.findByNombre("Pendiente Registro")).thenReturn(Optional.of(tipoPendiente));
        when(rolSistemaRepo.findByNombre("Socio")).thenReturn(Optional.of(rolSocio));
        // save() devuelve el mismo objeto; simula @GeneratedValue asignando el id si es null
        when(socioRepository.save(any(Socio.class))).thenAnswer(inv -> {
            Socio s = inv.getArgument(0);
            if (s.getId() == null) s.setId(SOCIO_UUID);
            return s;
        });
    }

    // =========================================================================
    // crear
    // =========================================================================

    @Test
    @DisplayName("crear — datos válidos → llama sendMinimalInvitation")
    void crear_datosValidos_llamaSendMinimalInvitation() {
        CreateSocioRequest request = nuevaSolicitud("1234567890", "juan@test.local");

        socioService.crear(request);

        verify(emailVerificationService).sendMinimalInvitation("1234567890", "juan@test.local", null);
    }

    @Test
    @DisplayName("crear — cédula duplicada → lanza SOCIO_ALREADY_EXISTS")
    void crear_cedulaDuplicada_lanzaConflict() {
        CreateSocioRequest request = nuevaSolicitud("1234567890", "juan@test.local");
        doThrow(new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS, "Cédula duplicada"))
                .when(emailVerificationService).sendMinimalInvitation("1234567890", "juan@test.local", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.crear(request));

        assertEquals(ErrorCode.SOCIO_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("crear — correo duplicado → lanza SOCIO_ALREADY_EXISTS")
    void crear_correoDuplicado_lanzaConflict() {
        CreateSocioRequest request = nuevaSolicitud("1234567890", "juan@test.local");
        doThrow(new BusinessException(ErrorCode.SOCIO_ALREADY_EXISTS, "Correo duplicado"))
                .when(emailVerificationService).sendMinimalInvitation("1234567890", "juan@test.local", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.crear(request));

        assertEquals(ErrorCode.SOCIO_ALREADY_EXISTS, ex.getErrorCode());
    }

    // =========================================================================
    // obtener
    // =========================================================================

    @Test
    @DisplayName("obtener — ID válido → retorna SocioResponse")
    void obtener_idValido_retornaResponse() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        SocioResponse response = socioService.obtener(SOCIO_UUID);

        assertNotNull(response);
        assertEquals(SOCIO_UUID, response.id());
    }

    @Test
    @DisplayName("obtener — ID inexistente → lanza SOCIO_NOT_FOUND")
    void obtener_idInexistente_lanzaNotFound() {
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.obtener(SOCIO_UUID));

        assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // actualizar
    // =========================================================================

    @Test
    @DisplayName("actualizar — datos válidos → retorna SocioResponse actualizado")
    void actualizar_datosValidos_retornaResponseActualizado() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(socioRepository.existsByCedulaAndIdNot(anyString(), eq(SOCIO_UUID))).thenReturn(false);
        when(socioRepository.existsByCorreoAndIdNot(anyString(), eq(SOCIO_UUID))).thenReturn(false);
        when(tipoSocioRepo.findById((short) 1)).thenReturn(Optional.of(tipoSocioActivo));

        UpdateSocioRequest request = new UpdateSocioRequest(
                "NuevoNombre", "NuevoApellido",
                "1234567890", "nuevo@test.local",
                null, null,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2020, 1, 1),
                null, null,
                null, null, null,
                null, null, null,
                (short) 1, null
        );

        SocioResponse response = socioService.actualizar(SOCIO_UUID, request);

        assertNotNull(response);
        assertEquals("NuevoNombre", response.nombre());
        assertEquals("NuevoApellido", response.apellido());
    }

    // =========================================================================
    // habilitar / inhabilitar
    // =========================================================================

    @Test
    @DisplayName("habilitar — socio inhabilitado → cambia estado a Habilitado")
    void habilitar_socioInhabilitado_cambiaEstado() {
        Socio socio = mockSocio(SOCIO_UUID);
        socio.setEstadoHabilitacion(estadoInhabilitado);
        Socio admin = mockSocio(ADMIN_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(socioRepository.findById(ADMIN_UUID)).thenReturn(Optional.of(admin));

        socioService.habilitar(SOCIO_UUID, ADMIN_UUID);

        assertEquals("Habilitado", socio.getEstadoHabilitacion().getNombre());
    }

    @Test
    @DisplayName("inhabilitar — socio habilitado → cambia estado a Inhabilitado")
    void inhabilitar_socioHabilitado_cambiaEstado() {
        Socio socio = mockSocio(SOCIO_UUID);
        Socio admin = mockSocio(ADMIN_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(socioRepository.findById(ADMIN_UUID)).thenReturn(Optional.of(admin));

        socioService.inhabilitar(SOCIO_UUID, ADMIN_UUID);

        assertEquals("Inhabilitado", socio.getEstadoHabilitacion().getNombre());
    }

    // =========================================================================
    // cambiarRol
    // =========================================================================

    @Test
    @DisplayName("cambiarRol — rol válido → cambia el rol del socio")
    void cambiarRol_rolValido_cambiaRol() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(rolSistemaRepo.findById((short) 1)).thenReturn(Optional.of(rolAdmin));

        socioService.cambiarRol(SOCIO_UUID, new UpdateRolRequest((short) 1));

        assertEquals("Admin", socio.getRolSistema().getNombre());
    }

    @Test
    @DisplayName("cambiarRol — rol inexistente → lanza RESOURCE_NOT_FOUND")
    void cambiarRol_rolInexistente_lanzaNotFound() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(rolSistemaRepo.findById((short) 99)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.cambiarRol(SOCIO_UUID, new UpdateRolRequest((short) 99)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // eliminar
    // =========================================================================

    @Test
    @DisplayName("eliminar — ID válido → elimina el socio")
    void eliminar_idValido_eliminaSocio() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        socioService.eliminar(SOCIO_UUID);

        verify(socioRepository).delete(socio);
    }

    @Test
    @DisplayName("eliminar — ID inexistente → lanza SOCIO_NOT_FOUND")
    void eliminar_idInexistente_lanzaNotFound() {
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.eliminar(SOCIO_UUID));

        assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private CreateSocioRequest nuevaSolicitud(String cedula, String correo) {
        return new CreateSocioRequest(cedula, correo, null);
    }

    private Socio mockSocio(UUID id) {
        return Socio.builder()
                .id(id)
                .nombre("Test")
                .apellido("Socio")
                .cedula("1234567890")
                .correo("test@sadday.local")
                .fechaNacimiento(LocalDate.of(1990, 1, 1))
                .fechaIngreso(LocalDate.of(2020, 1, 1))
                .estadoHabilitacion(estadoHabilitado)
                .estadoAcceso(estadoAccesoActivo)
                .tipoSocio(tipoSocioActivo)
                .rolSistema(rolSocio)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
