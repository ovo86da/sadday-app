package com.sadday.app.auth.service;

import com.sadday.app.auth.dto.CompleteRegistroRequest;
import com.sadday.app.auth.entity.EmailVerificationToken;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.EmailVerificationTokenRepository;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.config.AuthProperties;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.socios.entity.*;
import com.sadday.app.socios.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationService")
class EmailVerificationServiceTest {

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private UsuarioAuthRepository            usuarioAuthRepository;
    @Mock private JavaMailSender                   mailSender;
    @Mock private PasswordEncoder                  passwordEncoder;
    @Mock private SocioRepository                  socioRepository;
    @Mock private EstadoHabilitacionRepository     estadoHabRepo;
    @Mock private TipoSocioClubRepository          tipoSocioRepo;
    @Mock private RolSistemaRepository             rolSistemaRepo;
    @Mock private EstadoAccesoRepository           estadoAccesoRepo;
    @Mock private ClasificacionSocioRepository     clasifSocioRepo;

    private final AuthProperties authProperties = new AuthProperties();
    private EmailVerificationService service;

    private static final String RAW_TOKEN = "raw-token-value";
    private static final String USERNAME  = "juan.perez";
    private static final String PASSWORD  = "SecurePass123!";
    private static final String CEDULA    = "1234567890";
    private static final String CORREO    = "juan@club.com";

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                tokenRepository, usuarioAuthRepository, mailSender, passwordEncoder,
                authProperties, socioRepository, estadoHabRepo, tipoSocioRepo,
                rolSistemaRepo, estadoAccesoRepo, clasifSocioRepo);
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@club.com");
        ReflectionTestUtils.setField(service, "appUrl",   "https://app.club.com");
    }

    private EmailVerificationToken validToken(UUID socioId) {
        return EmailVerificationToken.builder()
                .tokenHash("any-hash")
                .socioId(socioId)
                .cedula(CEDULA).correo(CORREO).telefono("0991234567")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }

    private CompleteRegistroRequest manualRequest() {
        return new CompleteRegistroRequest(
                RAW_TOKEN,
                "Juan", "Pérez",
                LocalDate.of(1990, 1, 1), "O+",
                "Calle Principal",
                "Emergencia Uno", "099", "Calle 1",
                null, null, null,
                USERNAME, PASSWORD, PASSWORD
        );
    }

    // =========================================================================
    // complete() — validaciones previas
    // =========================================================================

    @Nested
    @DisplayName("complete() — validaciones")
    class Validaciones {

        @Test
        @DisplayName("contraseñas no coinciden → BusinessException")
        void passwordMismatch() {
            CompleteRegistroRequest req = new CompleteRegistroRequest(
                    RAW_TOKEN, null, null, null, null, null,
                    null, null, null, null, null, null,
                    USERNAME, PASSWORD, "OtraPass999!"
            );
            assertThatThrownBy(() -> service.complete(req))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("token no existe → BusinessException")
        void tokenNotFound() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("token ya usado → BusinessException")
        void tokenUsed() {
            EmailVerificationToken t = validToken(UUID.randomUUID());
            t.setUsed(true);
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("token expirado → BusinessException")
        void tokenExpired() {
            EmailVerificationToken t = EmailVerificationToken.builder()
                    .tokenHash("any-hash").socioId(UUID.randomUUID())
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .build();
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("username ya existe → BusinessException")
        void usernameAlreadyExists() {
            EmailVerificationToken t = validToken(UUID.randomUUID());
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(true);
            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // =========================================================================
    // complete() — flujo legacy (socioId en token)
    // =========================================================================

    @Nested
    @DisplayName("complete() — flujo legacy")
    class FlujoLegacy {

        @Test
        @DisplayName("socio sin credenciales → crea UsuarioAuth y marca token como usado")
        void happyPath() {
            UUID socioId = UUID.randomUUID();
            EmailVerificationToken t = validToken(socioId);
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(usuarioAuthRepository.findBySocioId(socioId)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
            when(usuarioAuthRepository.save(any())).thenReturn(new UsuarioAuth());
            when(tokenRepository.save(any())).thenReturn(t);

            assertThatNoException().isThrownBy(() -> service.complete(manualRequest()));

            verify(usuarioAuthRepository).save(argThat(u ->
                    socioId.equals(u.getSocioId()) && USERNAME.equals(u.getUsername())));
            assertThat(t.isUsed()).isTrue();
        }

        @Test
        @DisplayName("socio ya tiene credenciales → BusinessException")
        void socioYaRegistrado() {
            UUID socioId = UUID.randomUUID();
            EmailVerificationToken t = validToken(socioId);
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(usuarioAuthRepository.findBySocioId(socioId))
                    .thenReturn(Optional.of(new UsuarioAuth()));
            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // =========================================================================
    // complete() — flujo nuevo (socioId == null)
    // =========================================================================

    @Nested
    @DisplayName("complete() — flujo nuevo (sin socioId)")
    class FlujoNuevo {

        private EmailVerificationToken tokenNuevo;

        @BeforeEach
        void setup() {
            tokenNuevo = validToken(null);
        }

        private void stubLookups() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenNuevo));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(socioRepository.existsByCedula(CEDULA)).thenReturn(false);
            when(socioRepository.existsByCorreo(CORREO)).thenReturn(false);
            when(estadoHabRepo.findByNombre("Habilitado"))
                    .thenReturn(Optional.of(new EstadoHabilitacion()));
            when(tipoSocioRepo.findByNombre("Aspirante"))
                    .thenReturn(Optional.of(new TipoSocioClub()));
            when(estadoAccesoRepo.findByCodigo("ACTIVE"))
                    .thenReturn(Optional.of(new EstadoAcceso()));
            when(rolSistemaRepo.findByNombre("Socio"))
                    .thenReturn(Optional.of(new RolSistema()));
            Socio saved = new Socio();
            saved.setId(UUID.randomUUID());
            when(socioRepository.save(any())).thenReturn(saved);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
            when(usuarioAuthRepository.save(any())).thenReturn(new UsuarioAuth());
            when(tokenRepository.save(any())).thenReturn(tokenNuevo);
        }

        @Test
        @DisplayName("flujo manual → crea Socio y UsuarioAuth")
        void manualHappyPath() {
            stubLookups();
            assertThatNoException().isThrownBy(() -> service.complete(manualRequest()));
            verify(socioRepository).save(any(Socio.class));
            assertThat(tokenNuevo.isUsed()).isTrue();
        }

        @Test
        @DisplayName("cédula duplicada → BusinessException")
        void cedulaDuplicada() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenNuevo));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(socioRepository.existsByCedula(CEDULA)).thenReturn(true);
            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("correo duplicado → BusinessException")
        void correoDuplicado() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenNuevo));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(socioRepository.existsByCedula(CEDULA)).thenReturn(false);
            when(socioRepository.existsByCorreo(CORREO)).thenReturn(true);
            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("estado Habilitado no encontrado → BusinessException")
        void estadoNoEncontrado() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenNuevo));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(socioRepository.existsByCedula(CEDULA)).thenReturn(false);
            when(socioRepository.existsByCorreo(CORREO)).thenReturn(false);
            when(estadoHabRepo.findByNombre("Habilitado")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("flujo CSV → usa nombre/apellido del token, crea Socio")
        void csvHappyPath() {
            tokenNuevo.setNombre("Juan");
            tokenNuevo.setApellido("Pérez");
            stubLookups();

            CompleteRegistroRequest csvReq = new CompleteRegistroRequest(
                    RAW_TOKEN, null, null,
                    LocalDate.of(1990, 1, 1), null,
                    "Calle CSV",
                    "Emergencia", "099", "Calle",
                    null, null, null,
                    USERNAME, PASSWORD, PASSWORD
            );
            assertThatNoException().isThrownBy(() -> service.complete(csvReq));
            verify(socioRepository).save(any(Socio.class));
        }

        @Test
        @DisplayName("flujo CSV — fecha de nacimiento nula → BusinessException")
        void csvFechaNula() {
            tokenNuevo.setNombre("Juan");
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenNuevo));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);

            CompleteRegistroRequest csvReq = new CompleteRegistroRequest(
                    RAW_TOKEN, null, null, null, null, null,
                    null, null, null, null, null, null,
                    USERNAME, PASSWORD, PASSWORD
            );
            assertThatThrownBy(() -> service.complete(csvReq))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("flujo manual — nombre vacío → BusinessException")
        void manualNombreVacio() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(tokenNuevo));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);

            CompleteRegistroRequest req = new CompleteRegistroRequest(
                    RAW_TOKEN, "", "Pérez",
                    LocalDate.of(1990, 1, 1), null, null,
                    null, null, null, null, null, null,
                    USERNAME, PASSWORD, PASSWORD
            );
            assertThatThrownBy(() -> service.complete(req))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // =========================================================================
    // resolverTipoSocio — a través del flujo complete()
    // =========================================================================

    @Nested
    @DisplayName("resolverTipoSocio")
    class ResolverTipoSocio {

        private CompleteRegistroRequest csvRequest() {
            return new CompleteRegistroRequest(
                    RAW_TOKEN, null, null,
                    LocalDate.of(1990, 1, 1), null, "Dir",
                    "E", "099", "D", null, null, null,
                    USERNAME, PASSWORD, PASSWORD
            );
        }

        @Test
        @DisplayName("tipoSocioNombre en token → usa ese tipo sin tocar Aspirante")
        void tipoSocioNombreEnToken() {
            EmailVerificationToken t = validToken(null);
            t.setNombre("Juan");
            t.setApellido("Pérez");
            t.setTipoSocioNombre("Activo");

            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(socioRepository.existsByCedula(any())).thenReturn(false);
            when(socioRepository.existsByCorreo(any())).thenReturn(false);
            when(estadoHabRepo.findByNombre("Habilitado")).thenReturn(Optional.of(new EstadoHabilitacion()));
            when(tipoSocioRepo.findByNombre("Activo")).thenReturn(Optional.of(new TipoSocioClub()));
            when(estadoAccesoRepo.findByCodigo("ACTIVE")).thenReturn(Optional.of(new EstadoAcceso()));
            when(rolSistemaRepo.findByNombre("Socio")).thenReturn(Optional.of(new RolSistema()));
            Socio saved = new Socio();
            saved.setId(UUID.randomUUID());
            when(socioRepository.save(any())).thenReturn(saved);
            when(passwordEncoder.encode(any())).thenReturn("h");
            when(usuarioAuthRepository.save(any())).thenReturn(new UsuarioAuth());
            when(tokenRepository.save(any())).thenReturn(t);

            assertThatNoException().isThrownBy(() -> service.complete(csvRequest()));
            verify(tipoSocioRepo).findByNombre("Activo");
            verify(tipoSocioRepo, never()).findByNombre("Aspirante");
        }

        @Test
        @DisplayName("tipoSocioNombre no encontrado → fallback a Aspirante")
        void tipoSocioFallbackAspiranteEnToken() {
            EmailVerificationToken t = validToken(null);
            t.setNombre("Juan");
            t.setApellido("Pérez");
            t.setTipoSocioNombre("Inexistente");

            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(socioRepository.existsByCedula(any())).thenReturn(false);
            when(socioRepository.existsByCorreo(any())).thenReturn(false);
            when(estadoHabRepo.findByNombre("Habilitado")).thenReturn(Optional.of(new EstadoHabilitacion()));
            when(tipoSocioRepo.findByNombre("Inexistente")).thenReturn(Optional.empty());
            when(tipoSocioRepo.findByNombre("Aspirante")).thenReturn(Optional.of(new TipoSocioClub()));
            when(estadoAccesoRepo.findByCodigo("ACTIVE")).thenReturn(Optional.of(new EstadoAcceso()));
            when(rolSistemaRepo.findByNombre("Socio")).thenReturn(Optional.of(new RolSistema()));
            Socio saved = new Socio();
            saved.setId(UUID.randomUUID());
            when(socioRepository.save(any())).thenReturn(saved);
            when(passwordEncoder.encode(any())).thenReturn("h");
            when(usuarioAuthRepository.save(any())).thenReturn(new UsuarioAuth());
            when(tokenRepository.save(any())).thenReturn(t);

            assertThatNoException().isThrownBy(() -> service.complete(csvRequest()));
            verify(tipoSocioRepo).findByNombre("Aspirante");
        }

        @Test
        @DisplayName("Aspirante no encontrado en ninguna rama → BusinessException")
        void aspiranteNoEncontrado() {
            EmailVerificationToken t = validToken(null);
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            when(usuarioAuthRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(socioRepository.existsByCedula(CEDULA)).thenReturn(false);
            when(socioRepository.existsByCorreo(CORREO)).thenReturn(false);
            when(estadoHabRepo.findByNombre("Habilitado")).thenReturn(Optional.of(new EstadoHabilitacion()));
            when(tipoSocioRepo.findByNombre("Aspirante")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.complete(manualRequest()))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // =========================================================================
    // sendMinimalInvitation
    // =========================================================================

    @Nested
    @DisplayName("sendMinimalInvitation")
    class SendMinimalInvitation {

        @Test
        @DisplayName("cédula ya registrada → BusinessException")
        void cedulaExistente() {
            when(socioRepository.existsByCedula(CEDULA)).thenReturn(true);
            assertThatThrownBy(() -> service.sendMinimalInvitation(CEDULA, CORREO, null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("correo ya registrado → BusinessException")
        void correoExistente() {
            when(socioRepository.existsByCedula(CEDULA)).thenReturn(false);
            when(socioRepository.existsByCorreo(CORREO)).thenReturn(true);
            assertThatThrownBy(() -> service.sendMinimalInvitation(CEDULA, CORREO, null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("datos válidos → guarda token y envía email")
        void happyPath() {
            when(socioRepository.existsByCedula(CEDULA)).thenReturn(false);
            when(socioRepository.existsByCorreo(CORREO)).thenReturn(false);
            when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(() ->
                    service.sendMinimalInvitation(CEDULA, CORREO, "099"));

            verify(tokenRepository).save(argThat(t ->
                    CEDULA.equals(t.getCedula()) && CORREO.equals(t.getCorreo())));
            verify(mailSender).send(any(SimpleMailMessage.class));
        }
    }

    // =========================================================================
    // getTokenInfo
    // =========================================================================

    @Nested
    @DisplayName("getTokenInfo")
    class GetTokenInfo {

        @Test
        @DisplayName("token no existe → BusinessException")
        void noExiste() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getTokenInfo("any"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("token inválido (expirado) → BusinessException")
        void invalido() {
            EmailVerificationToken t = EmailVerificationToken.builder()
                    .tokenHash("h").expiresAt(LocalDateTime.now().minusHours(1)).build();
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.getTokenInfo("any"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("token legacy (socioId != null) → requiresPersonalData=false")
        void tokenLegacy() {
            when(tokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(validToken(UUID.randomUUID())));
            assertThat(service.getTokenInfo("raw").requiresPersonalData()).isFalse();
        }

        @Test
        @DisplayName("token nuevo (socioId == null) → requiresPersonalData=true")
        void tokenNuevo() {
            when(tokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(validToken(null)));
            assertThat(service.getTokenInfo("raw").requiresPersonalData()).isTrue();
        }

        @Test
        @DisplayName("token CSV (nombre != null) → fromCsvImport=true")
        void tokenCsv() {
            EmailVerificationToken t = validToken(null);
            t.setNombre("Juan");
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(t));
            assertThat(service.getTokenInfo("raw").fromCsvImport()).isTrue();
        }
    }
}
