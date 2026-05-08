package com.sadday.app.auth.service;

import com.sadday.app.auth.dto.ForgotPasswordRequest;
import com.sadday.app.auth.dto.ResetPasswordRequest;
import com.sadday.app.auth.entity.PasswordResetToken;
import com.sadday.app.auth.repository.PasswordResetTokenRepository;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.config.AuthProperties;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestiona el flujo de recuperación de contraseña.
 *
 * <p>Seguridad:
 * <ul>
 *   <li>El endpoint de solicitud siempre responde 200, no revela si el correo existe.</li>
 *   <li>El token expira en {@code sadday.auth.password-reset-token-expiry-minutes} minutos.</li>
 *   <li>Solo el hash SHA-256 del token se almacena en BD.</li>
 *   <li>Antes de emitir un token nuevo, se invalidan los anteriores del mismo socio.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UsuarioAuthRepository        usuarioAuthRepository;
    private final PasswordResetMailSender      mailSender;
    private final PasswordEncoder              passwordEncoder;
    private final AuthProperties               authProperties;
    private final JdbcClient                   jdbcClient;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Inicia el flujo de recuperación: busca al socio por correo, genera token y envía email.
     * Siempre responde sin revelar si el correo está registrado (previene enumeración).
     */
    public void initiate(ForgotPasswordRequest request) {
        // Buscar socio_id por correo — sin lanzar excepción si no existe
        Optional<UUID> socioIdOpt = jdbcClient
                .sql("SELECT id FROM socios WHERE correo = :correo")
                .param("correo", request.correo())
                .query(UUID.class)
                .optional();

        if (socioIdOpt.isEmpty()) {
            log.debug("Solicitud de reset para correo no registrado: {}", request.correo());
            return;  // No revelar que el correo no existe
        }

        UUID socioId = socioIdOpt.get();

        // Rate limiting por correo: máximo 3 solicitudes en los últimos 15 minutos (OWASP)
        long solicitudesRecientes = tokenRepository.countBySocioIdAndCreatedAtAfter(
                socioId, LocalDateTime.now().minusMinutes(15));
        if (solicitudesRecientes >= 3) {
            log.warn("Rate limit de reset password superado para socio_id={}", socioId);
            return;  // Silencioso — no revelar que se superó el límite
        }

        // Invalidar tokens anteriores del mismo socio
        tokenRepository.invalidateAllBySocioId(socioId);

        // Generar nuevo token
        String rawToken  = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = PasswordResetToken.builder()
                .socioId(socioId)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now()
                        .plusMinutes(authProperties.getPasswordResetTokenExpiryMinutes()))
                .build();
        tokenRepository.save(token);

        // Enviar email de forma asíncrona — desacopla el tiempo de respuesta del SMTP
        // y elimina el timing attack de enumeración de correos (A4).
        mailSender.send(request.correo(), rawToken);
    }

    /**
     * Versión para uso administrativo: omite el rate limit y garantiza que se envíe el email.
     * Se usa en el reset de emergencia ejecutado por admin/secretaria.
     */
    public void initiateEmergencyReset(UUID socioId, String correo) {
        tokenRepository.invalidateAllBySocioId(socioId);

        String rawToken  = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = PasswordResetToken.builder()
                .socioId(socioId)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now()
                        .plusMinutes(authProperties.getPasswordResetTokenExpiryMinutes()))
                .build();
        tokenRepository.save(token);

        mailSender.send(correo, rawToken);
        log.info("Email de reset de emergencia enviado a {} para socio_id={}", correo, socioId);
    }

    /**
     * Completa la recuperación: valida el token y actualiza la contraseña.
     */
    public void complete(ResetPasswordRequest request) {
        if (!request.nuevaPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Las contraseñas no coinciden");
        }

        String tokenHash = hashToken(request.token());
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        if (!token.isValid()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // Actualizar contraseña
        usuarioAuthRepository.findBySocioId(token.getSocioId()).ifPresent(usuario -> {
            usuario.setPasswordHash(passwordEncoder.encode(request.nuevaPassword()));
            // Resetear intentos fallidos y desbloquear si estaba bloqueado
            usuario.setFailedAttempts((short) 0);
            usuario.setLoginBlocked(false);
            usuario.setBlockedUntil(null);
            usuarioAuthRepository.save(usuario);
        });

        // Marcar token como usado
        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Contraseña restablecida para socio_id={}", token.getSocioId());
    }

    // =========================================================================

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
