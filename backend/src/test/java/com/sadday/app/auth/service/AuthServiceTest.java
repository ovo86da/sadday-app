package com.sadday.app.auth.service;

import com.sadday.app.auth.dto.*;
import com.sadday.app.auth.entity.MfaChallengeToken;
import com.sadday.app.auth.entity.RefreshToken;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.CountryChallengeTokenRepository;
import com.sadday.app.auth.repository.MfaChallengeTokenRepository;
import com.sadday.app.auth.repository.RefreshTokenRepository;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.config.SecurityProperties;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.security.jwt.JwtProperties;
import com.sadday.app.security.jwt.JwtService;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.repository.ConfiguracionSistemaRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService — Unit Tests")
class AuthServiceTest {

    @Mock private UsuarioAuthRepository           usuarioAuthRepository;
    @Mock private RefreshTokenRepository          refreshTokenRepository;
    @Mock private MfaChallengeTokenRepository     mfaChallengeTokenRepository;
    @Mock private CountryChallengeTokenRepository countryChallengeTokenRepository;
    @Mock private JwtService                      jwtService;
    @Mock private JwtProperties                   jwtProperties;
    @Mock private PasswordEncoder                 passwordEncoder;
    @Mock private TotpService                     totpService;
    @Mock private AuditService                    auditService;
    @Mock private SecurityProperties              securityProperties;
    @Mock private ConfiguracionSistemaRepository  configRepo;
    @Mock private SecurityEventService            securityEventService;
    @Mock private GeoIpService                    geoIpService;
    @Mock private SecurityAlertMailSender         alertMailSender;
    @Mock private SocioRepository                 socioRepository;
    @Mock private PasswordResetService            passwordResetService;

    @InjectMocks
    private AuthService authService;

    private static final UUID   SOCIO_ID   = UUID.fromString("00000000-0000-4000-a000-000000000001");
    private static final UUID   USUARIO_ID = UUID.fromString("00000000-0000-4000-a000-000000000002");
    private static final String USERNAME   = "test.user";
    private static final String PASSWORD   = "TestPassword123!";
    private static final String IP         = "127.0.0.1";
    private static final String UA         = "TestBrowser/1.0";

    private UsuarioAuth    testUsuario;
    private SocioAuthView  testSocioView;

    @BeforeEach
    void setUp() {
        testUsuario = UsuarioAuth.builder()
                .id(USUARIO_ID)
                .socioId(SOCIO_ID)
                .username(USERNAME)
                .passwordHash("$argon2id$v=19$m=19456,t=2,p=1$hash")
                .failedAttempts((short) 0)
                .loginBlocked(false)
                .totpEnabled(false)
                .build();

        testSocioView = mockSocioView("Test", "Usuario", "Socio", "Habilitado");

        when(securityProperties.getMaxLoginAttempts()).thenReturn(3);
        when(securityProperties.getLockoutDurationHours()).thenReturn(24);
        when(jwtProperties.getRefreshTokenExpirationSeconds()).thenReturn(2_592_000L);
        when(jwtProperties.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(configRepo.findByClave(any())).thenReturn(Optional.empty());
    }

    // =========================================================================
    // LOGIN — paso 1
    // =========================================================================

    @Nested
    @DisplayName("login() — paso 1")
    class LoginTests {

        @Test
        @DisplayName("Login sin MFA retorna Completed con access y refresh token")
        void login_success_noMfa() {
            givenUserFound();
            when(passwordEncoder.matches(PASSWORD, testUsuario.getPasswordHash())).thenReturn(true);
            when(usuarioAuthRepository.findSocioAuthView(SOCIO_ID)).thenReturn(Optional.of(testSocioView));
            when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("access.token.jwt");
            when(usuarioAuthRepository.save(any())).thenReturn(testUsuario);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoginStepResult step = authService.login(new LoginRequest(USERNAME, PASSWORD), IP, UA);

            assertInstanceOf(LoginStepResult.Completed.class, step);
            LoginResult result = ((LoginStepResult.Completed) step).result();
            assertNotNull(result);
            assertEquals("access.token.jwt", result.response().accessToken());
            assertNotNull(result.rawRefreshToken());
            assertEquals(USERNAME, result.response().username());
            assertEquals("Bearer", result.response().tokenType());
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Login con 2FA activo retorna MfaRequired con challengeToken")
        void login_withTotp_returnsMfaChallenge() {
            testUsuario.setTotpEnabled(true);
            testUsuario.setTotpSecret("encrypted-totp-secret");
            givenUserFound();
            when(passwordEncoder.matches(PASSWORD, testUsuario.getPasswordHash())).thenReturn(true);
            when(mfaChallengeTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoginStepResult step = authService.login(new LoginRequest(USERNAME, PASSWORD), IP, UA);

            assertInstanceOf(LoginStepResult.MfaRequired.class, step);
            MfaChallengeResponse challenge = ((LoginStepResult.MfaRequired) step).challenge();
            assertNotNull(challenge.challengeToken());
            assertFalse(challenge.challengeToken().isBlank());
            assertEquals(300, challenge.expiresIn());
            verify(mfaChallengeTokenRepository).save(any(MfaChallengeToken.class));
            // No debe emitir access token en este paso
            verify(jwtService, never()).generateAccessToken(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Login desbloquea cuenta con lockout expirado")
        void login_autoUnblocksExpiredLockout() {
            testUsuario.setLoginBlocked(true);
            testUsuario.setFailedAttempts((short) 3);
            testUsuario.setBlockedUntil(LocalDateTime.now().minusMinutes(1));
            givenUserFound();
            when(passwordEncoder.matches(PASSWORD, testUsuario.getPasswordHash())).thenReturn(true);
            when(usuarioAuthRepository.findSocioAuthView(SOCIO_ID)).thenReturn(Optional.of(testSocioView));
            when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("jwt");
            when(usuarioAuthRepository.save(any())).thenReturn(testUsuario);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoginStepResult step = authService.login(new LoginRequest(USERNAME, PASSWORD), IP, UA);

            assertInstanceOf(LoginStepResult.Completed.class, step);
            assertFalse(testUsuario.isLoginBlocked());
            assertEquals(0, testUsuario.getFailedAttempts());
            assertNull(testUsuario.getBlockedUntil());
        }

        @Test
        @DisplayName("Usuario inexistente lanza INVALID_CREDENTIALS")
        void login_userNotFound_throwsInvalidCredentials() {
            when(usuarioAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(new LoginRequest(USERNAME, PASSWORD), IP, UA));

            assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
            verify(auditService).registrar(eq(USERNAME), eq("LOGIN_FAILED"), any(), any(), any(), any(), any(), any(), eq("FAILED"), any());
        }

        @Test
        @DisplayName("Contraseña incorrecta incrementa failed_attempts")
        void login_wrongPassword_incrementsFailedAttempts() {
            givenUserFound();
            when(passwordEncoder.matches(anyString(), any())).thenReturn(false);
            when(usuarioAuthRepository.save(any())).thenReturn(testUsuario);

            assertThrows(BusinessException.class,
                    () -> authService.login(new LoginRequest(USERNAME, "wrong"), IP, UA));

            assertEquals(1, testUsuario.getFailedAttempts());
            assertFalse(testUsuario.isLoginBlocked());
        }

        @Test
        @DisplayName("Tercer intento fallido bloquea la cuenta")
        void login_thirdFailedAttempt_locksAccount() {
            testUsuario.setFailedAttempts((short) 2);
            givenUserFound();
            when(passwordEncoder.matches(anyString(), any())).thenReturn(false);
            when(usuarioAuthRepository.save(any())).thenReturn(testUsuario);

            assertThrows(BusinessException.class,
                    () -> authService.login(new LoginRequest(USERNAME, "wrong"), IP, UA));

            assertEquals(3, testUsuario.getFailedAttempts());
            assertTrue(testUsuario.isLoginBlocked());
            assertNotNull(testUsuario.getBlockedUntil());
        }

        @Test
        @DisplayName("Cuenta bloqueada lanza ACCOUNT_LOCKED sin verificar contraseña")
        void login_accountLocked_throwsAccountLocked() {
            testUsuario.setLoginBlocked(true);
            testUsuario.setBlockedUntil(LocalDateTime.now().plusHours(23));
            givenUserFound();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(new LoginRequest(USERNAME, PASSWORD), IP, UA));

            assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
            verify(passwordEncoder, never()).matches(any(), any());
        }
    }

    // =========================================================================
    // MFA LOGIN — paso 2
    // =========================================================================

    @Nested
    @DisplayName("completeMfaLogin() — paso 2")
    class MfaLoginTests {

        @Test
        @DisplayName("Código TOTP válido completa el login y devuelve tokens")
        void completeMfaLogin_validCode_returnsTokens() {
            MfaChallengeToken challenge = validChallenge();
            when(mfaChallengeTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(challenge));
            when(mfaChallengeTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(testUsuario));
            when(usuarioAuthRepository.findSocioAuthView(SOCIO_ID)).thenReturn(Optional.of(testSocioView));
            when(totpService.verify(any(), eq("123456"))).thenReturn(true);
            when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("access.token.jwt");
            when(usuarioAuthRepository.save(any())).thenReturn(testUsuario);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoginResult result = authService.completeMfaLogin(
                    new MfaLoginRequest("raw-challenge", "123456"), IP, UA);

            assertNotNull(result);
            assertEquals("access.token.jwt", result.response().accessToken());
            assertTrue(challenge.isUsed());
        }

        @Test
        @DisplayName("Código TOTP incorrecto lanza MFA_INVALID e incrementa intentos")
        void completeMfaLogin_wrongCode_throwsMfaInvalid() {
            MfaChallengeToken challenge = validChallenge();
            when(mfaChallengeTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(challenge));
            when(mfaChallengeTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(testUsuario));
            when(totpService.verify(any(), eq("000000"))).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.completeMfaLogin(
                            new MfaLoginRequest("raw-challenge", "000000"), IP, UA));

            assertEquals(ErrorCode.MFA_INVALID, ex.getErrorCode());
            assertEquals(1, challenge.getAttempts());
            assertFalse(challenge.isUsed()); // no se marca como usado, puede volver a intentar
        }

        @Test
        @DisplayName("Challenge token expirado lanza TOKEN_INVALID")
        void completeMfaLogin_expiredChallenge_throwsTokenInvalid() {
            MfaChallengeToken expired = MfaChallengeToken.builder()
                    .socioId(SOCIO_ID).tokenHash("hash")
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .build();
            when(mfaChallengeTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.completeMfaLogin(
                            new MfaLoginRequest("raw-challenge", "123456"), IP, UA));

            assertEquals(ErrorCode.TOKEN_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Superar 3 intentos bloquea el challenge y lanza ACCOUNT_LOCKED")
        void completeMfaLogin_maxAttempts_throwsAccountLocked() {
            MfaChallengeToken challenge = validChallenge();
            challenge.setAttempts((short) 3); // ya agotó los intentos
            when(mfaChallengeTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(challenge));
            when(mfaChallengeTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.completeMfaLogin(
                            new MfaLoginRequest("raw-challenge", "123456"), IP, UA));

            assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
            assertTrue(challenge.isUsed());
        }

        @Test
        @DisplayName("Challenge token inexistente lanza TOKEN_INVALID")
        void completeMfaLogin_unknownToken_throwsTokenInvalid() {
            when(mfaChallengeTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> authService.completeMfaLogin(
                            new MfaLoginRequest("unknown", "123456"), IP, UA));
        }
    }

    // =========================================================================
    // REFRESH
    // =========================================================================

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("Refresh exitoso rota el token y devuelve nuevos tokens")
        void refresh_success_rotatesToken() {
            RefreshToken storedToken = validRefreshToken();
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(storedToken));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(testUsuario));
            when(usuarioAuthRepository.findSocioAuthView(SOCIO_ID)).thenReturn(Optional.of(testSocioView));
            when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("new.access.token");

            LoginResult result = authService.refresh("raw-token", IP, UA);

            assertNotNull(result);
            assertEquals("new.access.token", result.response().accessToken());
            assertTrue(storedToken.isRevoked());
            assertNotNull(storedToken.getRevokedAt());
            verify(refreshTokenRepository, times(3)).save(any());
        }

        @Test
        @DisplayName("Token inexistente lanza TOKEN_INVALID")
        void refresh_unknownToken_throwsTokenInvalid() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh("unknown", IP, UA));

            assertEquals(ErrorCode.TOKEN_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Token expirado lanza TOKEN_INVALID")
        void refresh_expiredToken_throwsTokenInvalid() {
            RefreshToken expired = RefreshToken.builder()
                    .id(UUID.randomUUID()).socioId(SOCIO_ID).tokenHash("hash")
                    .expiresAt(LocalDateTime.now().minusHours(1))
                    .build();
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

            assertThrows(BusinessException.class, () -> authService.refresh("raw", IP, UA));
        }

        @Test
        @DisplayName("Token revocado reutilizado activa logout global")
        void refresh_revokedToken_revokesAllAndThrows() {
            RefreshToken revoked = RefreshToken.builder()
                    .id(UUID.randomUUID()).socioId(SOCIO_ID).tokenHash("hash")
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .revoked(true).revokedAt(LocalDateTime.now().minusHours(1))
                    .build();
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

            assertThrows(BusinessException.class, () -> authService.refresh("stolen", IP, UA));

            verify(refreshTokenRepository).revokeAllBySocioId(eq(SOCIO_ID), any());
        }
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Logout revoca el refresh token")
        void logout_success_revokesToken() {
            RefreshToken token = validRefreshToken();
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.logout("raw-token");

            assertTrue(token.isRevoked());
            assertNotNull(token.getRevokedAt());
        }

        @Test
        @DisplayName("Token inexistente no lanza excepción (idempotente)")
        void logout_unknownToken_doesNotThrow() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> authService.logout("nonexistent"));
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // =========================================================================
    // MFA SETUP — setup y confirmación
    // =========================================================================

    @Nested
    @DisplayName("MFA — setup, confirm, disable")
    class MfaSetupTests {

        @Test
        @DisplayName("setupMfa guarda secret cifrado y retorna QR URI y base32")
        void setupMfa_savesSecretAndReturnsResponse() {
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(testUsuario));
            when(usuarioAuthRepository.save(any())).thenReturn(testUsuario);
            when(totpService.generateSecret()).thenReturn(
                    new TotpService.TotpSecret("encrypted-secret", "JBSWY3DPEHPK3PXP"));
            when(totpService.buildOtpAuthUri(any(), any())).thenReturn("otpauth://totp/...");

            MfaSetupResponse response = authService.setupMfa(SOCIO_ID);

            assertNotNull(response);
            assertFalse(response.base32Secret().isBlank());
            assertFalse(response.otpAuthUri().isBlank());
            assertEquals("encrypted-secret", testUsuario.getTotpSecret());
            assertFalse(testUsuario.isTotpEnabled());
        }

        @Test
        @DisplayName("confirmMfa con código válido activa el 2FA")
        void confirmMfa_validCode_enablesTotp() {
            testUsuario.setTotpSecret("encrypted-secret");
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(testUsuario));
            when(totpService.verify("encrypted-secret", "654321")).thenReturn(true);
            when(usuarioAuthRepository.save(any())).thenReturn(testUsuario);

            authService.confirmMfa(SOCIO_ID, "654321");

            assertTrue(testUsuario.isTotpEnabled());
        }

        @Test
        @DisplayName("confirmMfa con código inválido lanza MFA_INVALID")
        void confirmMfa_invalidCode_throwsMfaInvalid() {
            testUsuario.setTotpSecret("encrypted-secret");
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(testUsuario));
            when(totpService.verify("encrypted-secret", "000000")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.confirmMfa(SOCIO_ID, "000000"));
            assertEquals(ErrorCode.MFA_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("disableMfa con código válido desactiva el 2FA")
        void disableMfa_validCode_disablesTotp() {
            testUsuario.setTotpEnabled(true);
            testUsuario.setTotpSecret("encrypted-secret");
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(testUsuario));
            when(totpService.verify("encrypted-secret", "654321")).thenReturn(true);
            when(usuarioAuthRepository.save(any())).thenReturn(testUsuario);

            authService.disableMfa(SOCIO_ID, "654321");

            assertFalse(testUsuario.isTotpEnabled());
            assertNull(testUsuario.getTotpSecret());
        }

        @Test
        @DisplayName("disableMfa cuando 2FA ya está desactivado no hace nada")
        void disableMfa_alreadyDisabled_doesNothing() {
            testUsuario.setTotpEnabled(false);
            when(usuarioAuthRepository.findBySocioId(SOCIO_ID)).thenReturn(Optional.of(testUsuario));

            assertDoesNotThrow(() -> authService.disableMfa(SOCIO_ID, "654321"));
            verify(totpService, never()).verify(any(), any());
            verify(usuarioAuthRepository, never()).save(any());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void givenUserFound() {
        when(usuarioAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(testUsuario));
    }

    private RefreshToken validRefreshToken() {
        return RefreshToken.builder()
                .id(UUID.randomUUID()).socioId(SOCIO_ID).tokenHash("hashed-token")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    private MfaChallengeToken validChallenge() {
        return MfaChallengeToken.builder()
                .id(UUID.randomUUID()).socioId(SOCIO_ID).tokenHash("hashed-challenge")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    private SocioAuthView mockSocioView(String nombre, String apellido, String rol, String estado) {
        SocioAuthView view = mock(SocioAuthView.class);
        when(view.getNombre()).thenReturn(nombre);
        when(view.getApellido()).thenReturn(apellido);
        when(view.getRolNombre()).thenReturn(rol);
        when(view.getEstadoHabilitacion()).thenReturn(estado);
        when(view.getEstadoAcceso()).thenReturn("ACTIVE");
        when(view.getCorreo()).thenReturn("test@sadday.local");
        return view;
    }
}
