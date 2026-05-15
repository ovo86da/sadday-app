package com.sadday.app.admin.service;

import com.sadday.app.admin.dto.AuditoriaFiltroRequest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.RefreshTokenRepository;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.auth.service.SecurityEventService;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.entity.EstadoAcceso;
import com.sadday.app.socios.entity.RolSistema;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.EstadoAccesoRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminService — Unit Tests")
class AdminServiceTest {

    @Mock JdbcClient             jdbcClient;
    @Mock UsuarioAuthRepository  usuarioAuthRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock AuditService           auditService;
    @Mock SecurityEventService   securityEventService;
    @Mock SocioRepository        socioRepository;
    @Mock EstadoAccesoRepository estadoAccesoRepository;

    @InjectMocks AdminService service;

    private final UUID SOCIO_ID  = UUID.randomUUID();
    private final String ACTOR   = "admin@test.com";

    // ── desbloquearUsuario ────────────────────────────────────────────────────

    @Nested
    @DisplayName("desbloquearUsuario")
    class DesbloquearUsuario {

        @Test
        void usuarioNoEncontrado_lanzaSocioNotFound() {
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.desbloquearUsuario(SOCIO_ID, ACTOR));
            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void usuarioNoBloqueado_noHaceNada() {
            UsuarioAuth usuario = UsuarioAuth.builder()
                    .loginBlocked(false)
                    .failedAttempts((short) 0)
                    .build();
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(usuario));

            service.desbloquearUsuario(SOCIO_ID, ACTOR);

            verify(usuarioAuthRepository, never()).save(any());
        }

        @Test
        void usuarioBloqueado_desbloqueaYAudita() {
            UsuarioAuth usuario = UsuarioAuth.builder()
                    .loginBlocked(true)
                    .failedAttempts((short) 5)
                    .blockedUntil(LocalDateTime.now().plusMinutes(30))
                    .build();
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(usuario));
            when(usuarioAuthRepository.save(any())).thenReturn(usuario);

            service.desbloquearUsuario(SOCIO_ID, ACTOR);

            assertFalse(usuario.isLoginBlocked());
            assertEquals(0, usuario.getFailedAttempts());
            assertNull(usuario.getBlockedUntil());
            verify(usuarioAuthRepository).save(usuario);
            verify(auditService).registrar(eq(ACTOR), eq("DESBLOQUEAR_USUARIO"), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void usuarioConIntentosFallidosSinBloqueo_desbloqueaTambien() {
            UsuarioAuth usuario = UsuarioAuth.builder()
                    .loginBlocked(false)
                    .failedAttempts((short) 3)
                    .build();
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(usuario));
            when(usuarioAuthRepository.save(any())).thenReturn(usuario);

            service.desbloquearUsuario(SOCIO_ID, ACTOR);

            verify(usuarioAuthRepository).save(usuario);
        }
    }

    // ── cambiarEstadoAcceso ────────────────────────────────────────────────────

    @Nested
    @DisplayName("cambiarEstadoAcceso")
    class CambiarEstadoAcceso {

        @Test
        void socioNoEncontrado_lanzaSocioNotFound() {
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.cambiarEstadoAcceso(SOCIO_ID, "BLOCKED", ACTOR));
            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void adminNoPuedeBloquearse_lanzaValidationError() {
            Socio socio = socioConRol("Admin", "ACTIVE");
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(socio));

            var ex = assertThrows(BusinessException.class,
                    () -> service.cambiarEstadoAcceso(SOCIO_ID, "BLOCKED", ACTOR));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void ultimaSecretariaActiva_noPuedeDeshabilitarse() {
            Socio socio = socioConRol("Secretaria", "ACTIVE");
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(socio));
            when(socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Secretaria", "ACTIVE"))
                    .thenReturn(1L);

            var ex = assertThrows(BusinessException.class,
                    () -> service.cambiarEstadoAcceso(SOCIO_ID, "BLOCKED", ACTOR));
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void secretariaConOtraActiva_puedeBloquearse() {
            Socio socio = socioConRol("Secretaria", "ACTIVE");
            EstadoAcceso nuevoEstado = mock(EstadoAcceso.class);
            when(nuevoEstado.getCodigo()).thenReturn("BLOCKED");

            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(socio));
            when(socioRepository.countByRolSistemaNombreAndEstadoAccesoCodigo("Secretaria", "ACTIVE"))
                    .thenReturn(2L);
            when(estadoAccesoRepository.findByCodigo("BLOCKED")).thenReturn(Optional.of(nuevoEstado));
            when(socioRepository.save(any())).thenReturn(socio);
            when(refreshTokenRepository.revokeAllBySocioId(any(), any())).thenReturn(1);

            service.cambiarEstadoAcceso(SOCIO_ID, "BLOCKED", ACTOR);

            verify(socioRepository).save(socio);
            verify(refreshTokenRepository).revokeAllBySocioId(eq(SOCIO_ID), any());
        }

        @Test
        void cambioAActive_noRevocaTokens() {
            Socio socio = socioConRol("Socio", "BLOCKED");
            EstadoAcceso nuevoEstado = mock(EstadoAcceso.class);
            when(nuevoEstado.getCodigo()).thenReturn("ACTIVE");

            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(socio));
            when(estadoAccesoRepository.findByCodigo("ACTIVE")).thenReturn(Optional.of(nuevoEstado));
            when(socioRepository.save(any())).thenReturn(socio);

            service.cambiarEstadoAcceso(SOCIO_ID, "ACTIVE", ACTOR);

            verify(refreshTokenRepository, never()).revokeAllBySocioId(any(), any());
            verify(auditService).registrar(any(), eq("CAMBIAR_ESTADO_ACCESO"), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void estadoNoEncontrado_lanzaResourceNotFound() {
            Socio socio = socioConRol("Socio", "ACTIVE");
            when(socioRepository.findById(SOCIO_ID)).thenReturn(Optional.of(socio));
            when(estadoAccesoRepository.findByCodigo("INVALIDO")).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.cambiarEstadoAcceso(SOCIO_ID, "INVALIDO", ACTOR));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }
    }

    // ── forzarCierreSesion ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("forzarCierreSesion")
    class ForzarCierreSesion {

        @Test
        void usuarioNoEncontrado_lanzaSocioNotFound() {
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class,
                    () -> service.forzarCierreSesion(SOCIO_ID, ACTOR));
            assertEquals(ErrorCode.SOCIO_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void success_revocaTokensYAudita() {
            UsuarioAuth usuario = UsuarioAuth.builder().build();
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(usuario));
            when(refreshTokenRepository.revokeAllBySocioId(any(), any())).thenReturn(3);

            service.forzarCierreSesion(SOCIO_ID, ACTOR);

            verify(refreshTokenRepository).revokeAllBySocioId(eq(SOCIO_ID), any());
            verify(auditService).registrar(eq(ACTOR), eq("FORZAR_CIERRE_SESION"), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    // ── getAuditoria ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAuditoria")
    class GetAuditoria {

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void setupJdbcMock(JdbcClient.StatementSpec sqlSpec,
                                   JdbcClient.MappedQuerySpec countSpec,
                                   JdbcClient.MappedQuerySpec listSpec) {
            when(jdbcClient.sql(anyString())).thenReturn(sqlSpec);
            when(sqlSpec.params(anyMap())).thenReturn(sqlSpec);
            when(sqlSpec.query(Long.class)).thenReturn(countSpec);
            when(countSpec.single()).thenReturn(0L);
            when(sqlSpec.query(any(RowMapper.class))).thenReturn(listSpec);
            when(listSpec.list()).thenReturn(List.of());
        }

        @Test
        void sinFiltros_retornaPaginaVacia() {
            var sqlSpec   = mock(JdbcClient.StatementSpec.class);
            var countSpec = mock(JdbcClient.MappedQuerySpec.class);
            var listSpec  = mock(JdbcClient.MappedQuerySpec.class);
            setupJdbcMock(sqlSpec, countSpec, listSpec);

            var filtro = new AuditoriaFiltroRequest(null, null, null, null, null, null, null, null);
            Page<?> result = service.getAuditoria(filtro, PageRequest.of(0, 20));

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }

        @Test
        void conActorUsername_agregaFiltro() {
            var sqlSpec   = mock(JdbcClient.StatementSpec.class);
            var countSpec = mock(JdbcClient.MappedQuerySpec.class);
            var listSpec  = mock(JdbcClient.MappedQuerySpec.class);
            setupJdbcMock(sqlSpec, countSpec, listSpec);

            var filtro = new AuditoriaFiltroRequest("admin", null, null, null, null, null, null, null);
            Page<?> result = service.getAuditoria(filtro, PageRequest.of(0, 20));

            assertNotNull(result);
        }

        @Test
        void conAccion_agregaFiltro() {
            var sqlSpec   = mock(JdbcClient.StatementSpec.class);
            var countSpec = mock(JdbcClient.MappedQuerySpec.class);
            var listSpec  = mock(JdbcClient.MappedQuerySpec.class);
            setupJdbcMock(sqlSpec, countSpec, listSpec);

            var filtro = new AuditoriaFiltroRequest(null, "LOGIN", null, null, null, null, null, null);
            assertNotNull(service.getAuditoria(filtro, PageRequest.of(0, 20)));
        }

        @Test
        void conOmitirAcciones_agregaFiltro() {
            var sqlSpec   = mock(JdbcClient.StatementSpec.class);
            var countSpec = mock(JdbcClient.MappedQuerySpec.class);
            var listSpec  = mock(JdbcClient.MappedQuerySpec.class);
            setupJdbcMock(sqlSpec, countSpec, listSpec);

            var filtro = new AuditoriaFiltroRequest(null, null, List.of("CAMBIAR_ESTADO_ACCESO", "DESBLOQUEAR"),
                    null, null, null, null, null);
            assertNotNull(service.getAuditoria(filtro, PageRequest.of(0, 20)));
        }

        @Test
        void conResultadoYEntidadAfectadaYFechas_agregaFiltros() {
            var sqlSpec   = mock(JdbcClient.StatementSpec.class);
            var countSpec = mock(JdbcClient.MappedQuerySpec.class);
            var listSpec  = mock(JdbcClient.MappedQuerySpec.class);
            setupJdbcMock(sqlSpec, countSpec, listSpec);

            var filtro = new AuditoriaFiltroRequest(null, null, null, "SUCCESS", "socios", "abc",
                    LocalDate.now().minusDays(7), LocalDate.now());
            assertNotNull(service.getAuditoria(filtro, PageRequest.of(0, 20)));
        }
    }

    // ── getSecurityEvents ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSecurityEvents")
    class GetSecurityEvents {

        @Test
        @SuppressWarnings("unchecked")
        void sinFiltros_retornaPaginaVacia() {
            var sqlSpec   = mock(JdbcClient.StatementSpec.class);
            var countSpec = mock(JdbcClient.MappedQuerySpec.class);
            var listSpec  = mock(JdbcClient.MappedQuerySpec.class);
            when(jdbcClient.sql(anyString())).thenReturn(sqlSpec);
            when(sqlSpec.params(anyMap())).thenReturn(sqlSpec);
            when(sqlSpec.query(Long.class)).thenReturn(countSpec);
            when(countSpec.single()).thenReturn(0L);
            when(sqlSpec.query(any(RowMapper.class))).thenReturn(listSpec);
            when(listSpec.list()).thenReturn(List.of());

            Page<?> result = service.getSecurityEvents(null, null, null, null, null, PageRequest.of(0, 20));

            assertNotNull(result);
        }

        @Test
        @SuppressWarnings("unchecked")
        void conFiltros_agregaFiltrosAQuery() {
            var sqlSpec   = mock(JdbcClient.StatementSpec.class);
            var countSpec = mock(JdbcClient.MappedQuerySpec.class);
            var listSpec  = mock(JdbcClient.MappedQuerySpec.class);
            when(jdbcClient.sql(anyString())).thenReturn(sqlSpec);
            when(sqlSpec.params(anyMap())).thenReturn(sqlSpec);
            when(sqlSpec.query(Long.class)).thenReturn(countSpec);
            when(countSpec.single()).thenReturn(0L);
            when(sqlSpec.query(any(RowMapper.class))).thenReturn(listSpec);
            when(listSpec.list()).thenReturn(List.of());

            Page<?> result = service.getSecurityEvents("user@test.com", "LOGIN_SUCCESS", "200.10.20.30",
                    LocalDate.now().minusDays(1), LocalDate.now(), PageRequest.of(0, 20));

            assertNotNull(result);
        }

        @Test
        @SuppressWarnings({"unchecked", "rawtypes"})
        void rowMapper_mapea_correctamente() throws Exception {
            var sqlSpec   = mock(JdbcClient.StatementSpec.class);
            var countSpec = mock(JdbcClient.MappedQuerySpec.class);
            var listSpec  = mock(JdbcClient.MappedQuerySpec.class);

            when(jdbcClient.sql(anyString())).thenReturn(sqlSpec);
            when(sqlSpec.params(anyMap())).thenReturn(sqlSpec);
            when(sqlSpec.query(Long.class)).thenReturn(countSpec);
            when(countSpec.single()).thenReturn(1L);

            // Capturar el RowMapper para invocarlo manualmente
            ArgumentCaptor<RowMapper> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
            when(sqlSpec.query(mapperCaptor.capture())).thenReturn(listSpec);
            when(listSpec.list()).thenReturn(List.of());
            when(securityEventService.parseUa(any())).thenReturn(new String[]{"Chrome", "Windows"});

            service.getSecurityEvents(null, null, null, null, null, Pageable.ofSize(10));

            // Invocar el RowMapper capturado con un ResultSet mockeado
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("user_agent")).thenReturn("Mozilla/5.0 Chrome/120.0");
            when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
            when(rs.getString("username")).thenReturn("user@test.com");
            when(rs.getString("nombre_completo")).thenReturn("Juan Pérez");
            when(rs.getString("event_type")).thenReturn("LOGIN_SUCCESS");
            when(rs.getString("ip_address")).thenReturn("200.10.20.30");
            when(rs.getString("country_code")).thenReturn("EC");
            when(rs.getString("city")).thenReturn("Quito");
            when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
            when(rs.getString("metadata")).thenReturn("{}");

            Object mapped = mapperCaptor.getValue().mapRow(rs, 0);
            assertNotNull(mapped);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Socio socioConRol(String rolNombre, String estadoCodigo) {
        RolSistema rol = mock(RolSistema.class);
        when(rol.getNombre()).thenReturn(rolNombre);

        EstadoAcceso estado = mock(EstadoAcceso.class);
        when(estado.getCodigo()).thenReturn(estadoCodigo);

        Socio s = new Socio();
        s.setId(SOCIO_ID);
        s.setNombre("Juan");
        s.setApellido("Pérez");
        s.setRolSistema(rol);
        s.setEstadoAcceso(estado);
        return s;
    }
}
