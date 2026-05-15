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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    // listar
    // =========================================================================

    @Test
    @DisplayName("listar — sin filtros → retorna página con socios")
    @SuppressWarnings("unchecked")
    void listar_sinFiltros_retornaPagina() {
        Socio s = mockSocio(SOCIO_UUID);
        Page<Socio> page = new PageImpl<>(List.of(s));
        when(socioRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(usuarioAuthRepository.findSocioIdsWithAccount(any())).thenReturn(Set.of());

        Page<SocioSummaryResponse> result = socioService.listar(null, null, null, null, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("listar — página vacía sin socios")
    @SuppressWarnings("unchecked")
    void listar_paginaVacia_retornaSinElementos() {
        Page<Socio> page = new PageImpl<>(List.of());
        when(socioRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<SocioSummaryResponse> result = socioService.listar(null, null, null, null, PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
    }

    // =========================================================================
    // buscarMinimal
    // =========================================================================

    @Test
    @DisplayName("buscarMinimal — con query → retorna lista minimal")
    @SuppressWarnings("unchecked")
    void buscarMinimal_conQuery_retornaListaMinimal() {
        Socio s = mockSocio(SOCIO_UUID);
        Page<Socio> page = new PageImpl<>(List.of(s));
        when(socioRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        List<SocioMinimalResponse> result = socioService.buscarMinimal("Juan", 5);

        assertEquals(1, result.size());
        assertEquals(SOCIO_UUID, result.get(0).id());
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
        when(estadoHabRepo.findById((short) 1)).thenReturn(Optional.of(estadoHabilitado));

        UpdateSocioRequest request = new UpdateSocioRequest(
                "NuevoNombre", "NuevoApellido",
                "1234567890", "nuevo@test.local",
                null, null,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2020, 1, 1),
                null, null,
                null, null, null,
                null, null, null,
                (short) 1, null, (short) 1
        );

        SocioResponse response = socioService.actualizar(SOCIO_UUID, request);

        assertNotNull(response);
        assertEquals("NuevoNombre", response.nombre());
        assertEquals("NuevoApellido", response.apellido());
    }

    @Test
    @DisplayName("actualizar — cédula duplicada → lanza SOCIO_ALREADY_EXISTS")
    void actualizar_cedulaDuplicada_lanzaConflict() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(socioRepository.existsByCedulaAndIdNot("9999999999", SOCIO_UUID)).thenReturn(true);

        UpdateSocioRequest request = new UpdateSocioRequest(
                "Nombre", "Apellido", "9999999999", "x@test.local",
                null, null, LocalDate.of(1990, 1, 1), LocalDate.of(2020, 1, 1),
                null, null, null, null, null, null, null, null, (short) 1, null, (short) 1
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.actualizar(SOCIO_UUID, request));
        assertEquals(ErrorCode.SOCIO_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("actualizar — correo duplicado → lanza SOCIO_ALREADY_EXISTS")
    void actualizar_correoDuplicado_lanzaConflict() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(socioRepository.existsByCedulaAndIdNot(anyString(), eq(SOCIO_UUID))).thenReturn(false);
        when(socioRepository.existsByCorreoAndIdNot("otro@test.local", SOCIO_UUID)).thenReturn(true);

        UpdateSocioRequest request = new UpdateSocioRequest(
                "Nombre", "Apellido", "1234567890", "otro@test.local",
                null, null, LocalDate.of(1990, 1, 1), LocalDate.of(2020, 1, 1),
                null, null, null, null, null, null, null, null, (short) 1, null, (short) 1
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.actualizar(SOCIO_UUID, request));
        assertEquals(ErrorCode.SOCIO_ALREADY_EXISTS, ex.getErrorCode());
    }

    // =========================================================================
    // actualizarMiPerfil
    // =========================================================================

    @Test
    @DisplayName("actualizarMiPerfil — correo nuevo único → actualiza correo")
    void actualizarMiPerfil_correoNuevo_actualizaCorreo() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(socioRepository.existsByCorreoAndIdNot("nuevo@test.local", SOCIO_UUID)).thenReturn(false);

        UpdateMiPerfilRequest request = new UpdateMiPerfilRequest(
                "nuevo@test.local", null, null, null,
                null, null, null, null, null, null
        );

        SocioResponse response = socioService.actualizarMiPerfil(SOCIO_UUID, request);

        assertEquals("nuevo@test.local", socio.getCorreo());
        assertNotNull(response);
    }

    @Test
    @DisplayName("actualizarMiPerfil — correo duplicado → lanza SOCIO_ALREADY_EXISTS")
    void actualizarMiPerfil_correoDuplicado_lanzaConflict() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(socioRepository.existsByCorreoAndIdNot("dup@test.local", SOCIO_UUID)).thenReturn(true);

        UpdateMiPerfilRequest request = new UpdateMiPerfilRequest(
                "dup@test.local", null, null, null,
                null, null, null, null, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.actualizarMiPerfil(SOCIO_UUID, request));
        assertEquals(ErrorCode.SOCIO_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("actualizarMiPerfil — campos null → no modifica el correo existente")
    void actualizarMiPerfil_camposNull_noModificaCorreo() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        UpdateMiPerfilRequest request = new UpdateMiPerfilRequest(
                null, null, null, null,
                null, null, null, null, null, null
        );

        socioService.actualizarMiPerfil(SOCIO_UUID, request);

        assertEquals("test@sadday.local", socio.getCorreo());
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
    @DisplayName("habilitar — socio vitalicio → lanza VALIDATION_ERROR")
    void habilitar_socioVitalicio_lanzaValidationError() {
        EstadoHabilitacion vitalicio = EstadoHabilitacion.builder()
                .id((short) 3).nombre("Socio Vitalicio").descripcion("").build();
        Socio socio = mockSocio(SOCIO_UUID);
        socio.setEstadoHabilitacion(vitalicio);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.habilitar(SOCIO_UUID, ADMIN_UUID));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("habilitar — socio ya habilitado → no registra log")
    void habilitar_socioYaHabilitado_noRegistraLog() {
        Socio socio = mockSocio(SOCIO_UUID);
        // estadoHabilitado id=1, findEstadoByNombre("Habilitado") también id=1 → retorno temprano
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        socioService.habilitar(SOCIO_UUID, ADMIN_UUID);

        verify(habilitacionLogRepo, never()).save(any());
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

    @Test
    @DisplayName("inhabilitar — socio vitalicio → lanza VALIDATION_ERROR")
    void inhabilitar_socioVitalicio_lanzaValidationError() {
        EstadoHabilitacion vitalicio = EstadoHabilitacion.builder()
                .id((short) 3).nombre("Socio Vitalicio").descripcion("").build();
        Socio socio = mockSocio(SOCIO_UUID);
        socio.setEstadoHabilitacion(vitalicio);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.inhabilitar(SOCIO_UUID, ADMIN_UUID));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("inhabilitar — socio ya inhabilitado → no registra log")
    void inhabilitar_socioYaInhabilitado_noRegistraLog() {
        Socio socio = mockSocio(SOCIO_UUID);
        socio.setEstadoHabilitacion(estadoInhabilitado);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        socioService.inhabilitar(SOCIO_UUID, ADMIN_UUID);

        verify(habilitacionLogRepo, never()).save(any());
    }

    // =========================================================================
    // listarHabilitacionLog
    // =========================================================================

    @Test
    @DisplayName("listarHabilitacionLog — socio existe → retorna lista de entradas")
    void listarHabilitacionLog_socioExiste_retornaLista() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        SocioHabilitacionLog log = mockHabilitacionLog(socio);
        when(habilitacionLogRepo.findBySocioIdOrderByCambiadoEnDesc(SOCIO_UUID))
                .thenReturn(List.of(log));

        List<HabilitacionLogResponse> result = socioService.listarHabilitacionLog(SOCIO_UUID);

        assertEquals(1, result.size());
        assertEquals("Inhabilitado", result.get(0).estadoAnterior());
        assertEquals("Habilitado", result.get(0).estadoNuevo());
    }

    @Test
    @DisplayName("listarHabilitacionLog — socio no existe → lanza SOCIO_NOT_FOUND")
    void listarHabilitacionLog_socioNoExiste_lanzaNotFound() {
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.listarHabilitacionLog(SOCIO_UUID));
        assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // actualizarNivelTecnico
    // =========================================================================

    @Test
    @DisplayName("actualizarNivelTecnico — nivel válido → actualiza nivel")
    void actualizarNivelTecnico_nivelValido_actualizaNivel() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        ClasificacionSocio nivel = mock(ClasificacionSocio.class);
        when(nivel.getId()).thenReturn("INTERMEDIO");
        when(nivel.getNombre()).thenReturn("Intermedio");
        when(clasifSocioRepo.findById("INTERMEDIO")).thenReturn(Optional.of(nivel));

        SocioResponse response = socioService.actualizarNivelTecnico(SOCIO_UUID,
                new UpdateNivelTecnicoRequest("INTERMEDIO"));

        assertNotNull(response);
        verify(socioRepository).save(socio);
    }

    @Test
    @DisplayName("actualizarNivelTecnico — nivelId null → quita nivel del socio")
    void actualizarNivelTecnico_nivelNull_quitaNivel() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        socioService.actualizarNivelTecnico(SOCIO_UUID, new UpdateNivelTecnicoRequest(null));

        assertNull(socio.getNivelTecnico());
        verify(socioRepository).save(socio);
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

    @Test
    @DisplayName("cambiarRol — único Admin activo → lanza VALIDATION_ERROR")
    void cambiarRol_unicoAdmin_lanzaValidationError() {
        Socio socio = mockSocio(SOCIO_UUID);
        socio.setRolSistema(rolAdmin);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(rolSistemaRepo.findById((short) 4)).thenReturn(Optional.of(rolSocio));
        when(socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Admin", "ACTIVE")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.cambiarRol(SOCIO_UUID, new UpdateRolRequest((short) 4)));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("cambiarRol — única Secretaria activa → lanza VALIDATION_ERROR")
    void cambiarRol_unicaSecretaria_lanzaValidationError() {
        RolSistema rolSecretaria = RolSistema.builder().id((short) 2).nombre("Secretaria").descripcion("").build();
        Socio socio = mockSocio(SOCIO_UUID);
        socio.setRolSistema(rolSecretaria);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(rolSistemaRepo.findById((short) 4)).thenReturn(Optional.of(rolSocio));
        when(socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Secretaria", "ACTIVE")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.cambiarRol(SOCIO_UUID, new UpdateRolRequest((short) 4)));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("cambiarRol — ya hay 3 Admins activos → lanza VALIDATION_ERROR")
    void cambiarRol_maximoAdmins_lanzaValidationError() {
        Socio socio = mockSocio(SOCIO_UUID); // rolSistema = "Socio"
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(rolSistemaRepo.findById((short) 1)).thenReturn(Optional.of(rolAdmin));
        when(socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Admin", "ACTIVE")).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.cambiarRol(SOCIO_UUID, new UpdateRolRequest((short) 1)));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("cambiarRol — ya hay 2 Secretarias activas → lanza VALIDATION_ERROR")
    void cambiarRol_maximoSecretarias_lanzaValidationError() {
        RolSistema rolSecretaria = RolSistema.builder().id((short) 2).nombre("Secretaria").descripcion("").build();
        Socio socio = mockSocio(SOCIO_UUID); // rolSistema = "Socio"
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(rolSistemaRepo.findById((short) 2)).thenReturn(Optional.of(rolSecretaria));
        when(socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Secretaria", "ACTIVE")).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.cambiarRol(SOCIO_UUID, new UpdateRolRequest((short) 2)));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    // =========================================================================
    // listarCuotas
    // =========================================================================

    @Test
    @DisplayName("listarCuotas — socio existe → retorna lista de cuotas")
    void listarCuotas_socioExiste_retornaLista() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        EstadoCuota cuota = EstadoCuota.builder()
                .id(1L).socio(socio)
                .valor(new BigDecimal("50.00"))
                .fecha(LocalDate.of(2024, 1, 1))
                .estado("PAGADO")
                .registradoPor(socio)
                .build();
        when(cuotaRepository.findBySocioIdOrderByFechaDesc(SOCIO_UUID)).thenReturn(List.of(cuota));

        List<CuotaResponse> result = socioService.listarCuotas(SOCIO_UUID);

        assertEquals(1, result.size());
        assertEquals("PAGADO", result.get(0).estado());
    }

    @Test
    @DisplayName("listarCuotas — socio no existe → lanza SOCIO_NOT_FOUND")
    void listarCuotas_socioNoExiste_lanzaNotFound() {
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.listarCuotas(SOCIO_UUID));
        assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // registrarCuota
    // =========================================================================

    @Test
    @DisplayName("registrarCuota — datos válidos → guarda y retorna cuota")
    void registrarCuota_datosValidos_guardaCuota() {
        Socio socio = mockSocio(SOCIO_UUID);
        Socio admin = mockSocio(ADMIN_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(socioRepository.findById(ADMIN_UUID)).thenReturn(Optional.of(admin));

        EstadoCuota cuotaGuardada = EstadoCuota.builder()
                .id(1L).socio(socio)
                .valor(new BigDecimal("50.00"))
                .fecha(LocalDate.of(2024, 1, 1))
                .estado("PAGADO")
                .registradoPor(admin)
                .build();
        when(cuotaRepository.save(any())).thenReturn(cuotaGuardada);

        CuotaResponse response = socioService.registrarCuota(SOCIO_UUID,
                new CreateCuotaRequest(new BigDecimal("50.00"), LocalDate.of(2024, 1, 1), "PAGADO"),
                ADMIN_UUID);

        assertNotNull(response);
        assertEquals("PAGADO", response.estado());
        verify(cuotaRepository).save(any());
    }

    // =========================================================================
    // eliminarCuota
    // =========================================================================

    @Test
    @DisplayName("eliminarCuota — cuota válida → elimina")
    void eliminarCuota_cuotaValida_elimina() {
        Socio socio = mockSocio(SOCIO_UUID);
        EstadoCuota cuota = EstadoCuota.builder()
                .id(1L).socio(socio)
                .valor(new BigDecimal("50.00"))
                .fecha(LocalDate.of(2024, 1, 1))
                .estado("PAGADO").build();
        when(cuotaRepository.findById(1L)).thenReturn(Optional.of(cuota));

        socioService.eliminarCuota(SOCIO_UUID, 1L);

        verify(cuotaRepository).delete(cuota);
    }

    @Test
    @DisplayName("eliminarCuota — cuota de otro socio → lanza RESOURCE_NOT_FOUND")
    void eliminarCuota_cuotaDeOtroSocio_lanzaResourceNotFound() {
        Socio otroSocio = mockSocio(UUID.randomUUID());
        EstadoCuota cuota = EstadoCuota.builder()
                .id(1L).socio(otroSocio)
                .valor(new BigDecimal("50.00"))
                .fecha(LocalDate.of(2024, 1, 1))
                .estado("PAGADO").build();
        when(cuotaRepository.findById(1L)).thenReturn(Optional.of(cuota));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.eliminarCuota(SOCIO_UUID, 1L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("eliminarCuota — cuota no existe → lanza RESOURCE_NOT_FOUND")
    void eliminarCuota_cuotaNoExiste_lanzaResourceNotFound() {
        when(cuotaRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.eliminarCuota(SOCIO_UUID, 99L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // obtenerLookups
    // =========================================================================

    @Test
    @DisplayName("obtenerLookups — retorna todos los catálogos")
    void obtenerLookups_retornaTodosLosCatalogos() {
        when(tipoSocioRepo.findAll()).thenReturn(List.of(tipoSocioActivo));
        when(estadoHabRepo.findAll()).thenReturn(List.of(estadoHabilitado, estadoInhabilitado));
        when(rolSistemaRepo.findAll()).thenReturn(List.of(rolSocio, rolAdmin));

        ClasificacionSocio clasif = mock(ClasificacionSocio.class);
        when(clasif.getId()).thenReturn("BASICO");
        when(clasif.getNivel()).thenReturn((short) 1);
        when(clasif.getNombre()).thenReturn("Básico");
        when(clasif.getDescripcion()).thenReturn("");
        when(clasifSocioRepo.findAll()).thenReturn(List.of(clasif));

        LookupsResponse response = socioService.obtenerLookups();

        assertEquals(1, response.tiposSocio().size());
        assertEquals(2, response.estadosHabilitacion().size());
        assertEquals(2, response.rolesSistema().size());
        assertEquals(1, response.clasificaciones().size());
    }

    // =========================================================================
    // reenviarInvitacion
    // =========================================================================

    @Test
    @DisplayName("reenviarInvitacion — socio ya activó cuenta → lanza VALIDATION_ERROR")
    void reenviarInvitacion_yaActivoCuenta_lanzaValidationError() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(usuarioAuthRepository.existsBySocioId(SOCIO_UUID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.reenviarInvitacion(SOCIO_UUID));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("reenviarInvitacion — socio sin cuenta → envía invitación")
    void reenviarInvitacion_sinCuenta_enviaInvitacion() {
        Socio socio = mockSocio(SOCIO_UUID);
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));
        when(usuarioAuthRepository.existsBySocioId(SOCIO_UUID)).thenReturn(false);

        socioService.reenviarInvitacion(SOCIO_UUID);

        verify(emailVerificationService).sendInvitation(SOCIO_UUID, "test@sadday.local");
    }

    @Test
    @DisplayName("reenviarInvitacion — socio no existe → lanza SOCIO_NOT_FOUND")
    void reenviarInvitacion_socioNoExiste_lanzaNotFound() {
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.reenviarInvitacion(SOCIO_UUID));
        assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // setJefeMontana
    // =========================================================================

    @Test
    @DisplayName("setJefeMontana — rol no DIRECTIVO → lanza VALIDATION_ERROR")
    void setJefeMontana_noDirectivo_lanzaValidationError() {
        Socio socio = mockSocio(SOCIO_UUID); // rolSistema = "Socio"
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> socioService.setJefeMontana(SOCIO_UUID, true));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("setJefeMontana — DIRECTIVO → activa flag jefe de montaña")
    void setJefeMontana_directivo_activaFlag() {
        RolSistema rolDirectivo = RolSistema.builder().id((short) 3).nombre("DIRECTIVO").descripcion("").build();
        Socio socio = Socio.builder()
                .id(SOCIO_UUID).nombre("Test").apellido("Socio")
                .cedula("1234567890").correo("test@sadday.local")
                .fechaNacimiento(LocalDate.of(1990, 1, 1))
                .fechaIngreso(LocalDate.of(2020, 1, 1))
                .estadoHabilitacion(estadoHabilitado)
                .estadoAcceso(estadoAccesoActivo)
                .tipoSocio(tipoSocioActivo)
                .rolSistema(rolDirectivo)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        socioService.setJefeMontana(SOCIO_UUID, true);

        assertTrue(socio.isEsJefeMontana());
    }

    @Test
    @DisplayName("setJefeMontana — DIRECTIVO valor false → desactiva flag")
    void setJefeMontana_directivo_desactivaFlag() {
        RolSistema rolDirectivo = RolSistema.builder().id((short) 3).nombre("DIRECTIVO").descripcion("").build();
        Socio socio = Socio.builder()
                .id(SOCIO_UUID).nombre("Test").apellido("Socio")
                .cedula("1234567890").correo("test@sadday.local")
                .fechaNacimiento(LocalDate.of(1990, 1, 1))
                .fechaIngreso(LocalDate.of(2020, 1, 1))
                .estadoHabilitacion(estadoHabilitado)
                .estadoAcceso(estadoAccesoActivo)
                .tipoSocio(tipoSocioActivo)
                .rolSistema(rolDirectivo)
                .esJefeMontana(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(socioRepository.findById(SOCIO_UUID)).thenReturn(Optional.of(socio));

        socioService.setJefeMontana(SOCIO_UUID, false);

        assertFalse(socio.isEsJefeMontana());
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

    private SocioHabilitacionLog mockHabilitacionLog(Socio socio) {
        SocioHabilitacionLog log = mock(SocioHabilitacionLog.class);
        when(log.getId()).thenReturn(1L);
        when(log.getEstadoAnterior()).thenReturn(estadoInhabilitado);
        when(log.getEstadoNuevo()).thenReturn(estadoHabilitado);
        when(log.getCambiadoPor()).thenReturn(socio);
        when(log.getCambiadoEn()).thenReturn(OffsetDateTime.now());
        when(log.getFuente()).thenReturn("MANUAL");
        when(log.getNotas()).thenReturn(null);
        return log;
    }
}
