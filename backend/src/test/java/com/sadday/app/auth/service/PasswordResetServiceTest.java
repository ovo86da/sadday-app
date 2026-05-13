package com.sadday.app.auth.service;

import com.sadday.app.auth.dto.ForgotPasswordRequest;
import com.sadday.app.auth.dto.ResetPasswordRequest;
import com.sadday.app.auth.entity.PasswordResetToken;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.PasswordResetTokenRepository;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.config.AuthProperties;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PasswordResetService — Unit Tests")
class PasswordResetServiceTest {

    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock UsuarioAuthRepository        usuarioAuthRepository;
    @Mock PasswordResetMailSender      mailSender;
    @Mock PasswordEncoder              passwordEncoder;
    @Mock AuthProperties               authProperties;
    @Mock JdbcClient                   jdbcClient;

    @InjectMocks PasswordResetService service;

    private final UUID socioId = UUID.randomUUID();

    @Mock JdbcClient.StatementSpec statementSpec;
    @SuppressWarnings("unchecked")
    @Mock JdbcClient.MappedQuerySpec<UUID> querySpec;

    @BeforeEach
    void setUp() {
        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
        when(statementSpec.query(UUID.class)).thenReturn(querySpec);
        when(authProperties.getPasswordResetTokenExpiryMinutes()).thenReturn(15);
    }

    @Nested
    @DisplayName("initiate")
    class Initiate {

        @Test
        void correoRegistrado_bajoRateLimit_guardaTokenYEnviaEmail() {
            when(querySpec.optional()).thenReturn(Optional.of(socioId));
            when(tokenRepository.countBySocioIdAndCreatedAtAfter(eq(socioId), any())).thenReturn(0L);

            service.initiate(new ForgotPasswordRequest("user@test.com"));

            verify(tokenRepository).invalidateAllBySocioId(socioId);
            verify(tokenRepository).save(any(PasswordResetToken.class));
            verify(mailSender).send(eq("user@test.com"), anyString());
        }

        @Test
        void correoNoRegistrado_noEnviaEmail() {
            when(querySpec.optional()).thenReturn(Optional.empty());

            service.initiate(new ForgotPasswordRequest("noexiste@test.com"));

            verify(mailSender, never()).send(anyString(), anyString());
            verify(tokenRepository, never()).save(any());
        }

        @Test
        void rateLimitSuperado_noEnviaEmail() {
            when(querySpec.optional()).thenReturn(Optional.of(socioId));
            when(tokenRepository.countBySocioIdAndCreatedAtAfter(eq(socioId), any())).thenReturn(3L);

            service.initiate(new ForgotPasswordRequest("user@test.com"));

            verify(mailSender, never()).send(anyString(), anyString());
            verify(tokenRepository, never()).save(any());
        }

        @Test
        void correoRegistrado_invalidaTokensAnteriores() {
            when(querySpec.optional()).thenReturn(Optional.of(socioId));
            when(tokenRepository.countBySocioIdAndCreatedAtAfter(eq(socioId), any())).thenReturn(1L);

            service.initiate(new ForgotPasswordRequest("user@test.com"));

            verify(tokenRepository).invalidateAllBySocioId(socioId);
        }
    }

    @Nested
    @DisplayName("initiateEmergencyReset")
    class InitiateEmergencyReset {

        @Test
        void guardaTokenYEnviaEmailSinRateLimit() {
            service.initiateEmergencyReset(socioId, "admin@test.com");

            verify(tokenRepository).invalidateAllBySocioId(socioId);
            verify(tokenRepository).save(any(PasswordResetToken.class));
            verify(mailSender).send(eq("admin@test.com"), anyString());
        }
    }

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        void tokenValido_actualizaPassword() {
            String rawToken = "sometoken";
            PasswordResetToken token = PasswordResetToken.builder()
                    .socioId(socioId)
                    .tokenHash("hash")
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .used(false)
                    .build();

            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
            UsuarioAuth usuario = new UsuarioAuth();
            when(usuarioAuthRepository.findBySocioId(socioId)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");

            service.complete(new ResetPasswordRequest(rawToken, "NewP@ss1!", "NewP@ss1!"));

            verify(usuarioAuthRepository).save(any(UsuarioAuth.class));
            assertTrue(token.isUsed());
        }

        @Test
        void passwordsNoCoinciden_lanzaValidationError() {
            var ex = assertThrows(BusinessException.class, () ->
                    service.complete(new ResetPasswordRequest("tok", "Pass1!", "Pass2!"))
            );
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        void tokenNoEncontrado_lanzaTokenInvalid() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () ->
                    service.complete(new ResetPasswordRequest("bad", "P@ss1!", "P@ss1!"))
            );
            assertEquals(ErrorCode.TOKEN_INVALID, ex.getErrorCode());
        }

        @Test
        void tokenYaUsado_lanzaTokenInvalid() {
            PasswordResetToken token = PasswordResetToken.builder()
                    .socioId(socioId)
                    .tokenHash("hash")
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .used(true)
                    .build();
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            var ex = assertThrows(BusinessException.class, () ->
                    service.complete(new ResetPasswordRequest("tok", "P@ss1!", "P@ss1!"))
            );
            assertEquals(ErrorCode.TOKEN_INVALID, ex.getErrorCode());
        }

        @Test
        void tokenExpirado_lanzaTokenInvalid() {
            PasswordResetToken token = PasswordResetToken.builder()
                    .socioId(socioId)
                    .tokenHash("hash")
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .used(false)
                    .build();
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            var ex = assertThrows(BusinessException.class, () ->
                    service.complete(new ResetPasswordRequest("tok", "P@ss1!", "P@ss1!"))
            );
            assertEquals(ErrorCode.TOKEN_INVALID, ex.getErrorCode());
        }
    }
}
