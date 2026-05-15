package com.sadday.app.auth.service;

import com.sadday.app.auth.dto.ChangePasswordRequest;
import com.sadday.app.auth.dto.CountryChallengeResponse;
import com.sadday.app.auth.dto.CountryChallengeVerifyRequest;
import com.sadday.app.auth.dto.LoginRequest;
import com.sadday.app.auth.dto.LoginResponse;
import com.sadday.app.auth.dto.LoginResult;
import com.sadday.app.auth.dto.LoginStepResult;
import com.sadday.app.auth.dto.MfaChallengeResponse;
import com.sadday.app.auth.dto.MfaLoginRequest;
import com.sadday.app.auth.dto.MfaSetupResponse;
import com.sadday.app.auth.dto.SessionResponse;
import com.sadday.app.auth.dto.SocioAuthView;
import com.sadday.app.auth.entity.CountryChallengeToken;
import com.sadday.app.auth.entity.MfaChallengeToken;
import com.sadday.app.auth.entity.RefreshToken;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.CountryChallengeTokenRepository;
import com.sadday.app.auth.repository.MfaChallengeTokenRepository;
import com.sadday.app.auth.repository.RefreshTokenRepository;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.config.SecurityProperties;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.shared.repository.ConfiguracionSistemaRepository;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import com.sadday.app.security.jwt.JwtProperties;
import com.sadday.app.security.jwt.JwtService;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio central de autenticación.
 *
 * <p>Flujos implementados:
 * <ul>
 *   <li>Login con username/password (+ TOTP opcional)</li>
 *   <li>Refresh rotativo de tokens</li>
 *   <li>Logout (revocación de refresh token)</li>
 *   <li>Logout global (revocar todos los tokens del socio)</li>
 *   <li>Setup y confirmación de 2FA (TOTP)</li>
 *   <li>Desactivación de 2FA</li>
 * </ul>
 *
 * <p>Principios de seguridad aplicados:
 * <ul>
 *   <li>Misma respuesta de error para usuario inexistente y contraseña incorrecta
 *       (previene enumeración de usuarios).</li>
 *   <li>Máximo {@code maxLoginAttempts} intentos fallidos → bloqueo temporal.</li>
 *   <li>Auto-desbloqueo al expirar el periodo (no requiere acción del admin).</li>
 *   <li>Detección de robo de refresh token: si se reutiliza un token revocado,
 *       se revocan TODOS los tokens del socio.</li>
 *   <li>Refresh tokens: solo se almacena el hash SHA-256 en BD.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UsuarioAuthRepository          usuarioAuthRepository;
    private final RefreshTokenRepository         refreshTokenRepository;
    private final MfaChallengeTokenRepository    mfaChallengeTokenRepository;
    private final CountryChallengeTokenRepository countryChallengeTokenRepository;
    private final JwtService                     jwtService;
    private final JwtProperties                  jwtProperties;
    private final PasswordEncoder                passwordEncoder;
    private final TotpService                    totpService;
    private final AuditService                   auditService;
    private final SecurityProperties             securityProperties;
    private final ConfiguracionSistemaRepository configRepo;
    private final SecurityEventService           securityEventService;
    private final GeoIpService                   geoIpService;
    private final SecurityAlertMailSender        alertMailSender;
    private final SocioRepository                socioRepository;
    private final PasswordResetService           passwordResetService;

    private static final int    MFA_CHALLENGE_EXPIRY_SECONDS     = 300;  // 5 minutos
    private static final int    MFA_MAX_ATTEMPTS                 = 3;
    private static final int    COUNTRY_CHALLENGE_EXPIRY_SECONDS = 900;  // 15 minutos
    private static final int    COUNTRY_CHALLENGE_MAX_ATTEMPTS   = 5;
    private static final String ENTIDAD_USUARIOS_AUTH            = "usuarios_auth";
    private static final String RESULTADO_OK                     = "SUCCESS";
    private static final String RESULTADO_FAILED                 = "FAILED";
    private static final String META_SESSIONS_REVOKED            = "sessions_revoked";

    private final SecureRandom secureRandom = new SecureRandom();

    // =========================================================================
    // Login
    // =========================================================================

    /**
     * Paso 1: valida username + contraseña.
     * - Sin 2FA → completa el login y devuelve {@link LoginStepResult.Completed}.
     * - Con 2FA → emite un challenge token y devuelve {@link LoginStepResult.MfaRequired}.
     */
    public LoginStepResult login(LoginRequest request, String ip, String userAgent) {

        // 1. Buscar usuario — mismo error si no existe o si la contraseña es incorrecta
        UsuarioAuth usuario = usuarioAuthRepository.findByUsername(request.username())
                .orElseGet(() -> {
                    auditService.registrar(request.username(), "LOGIN_FAILED", ENTIDAD_USUARIOS_AUTH,
                            null, null, null, ip, userAgent, RESULTADO_FAILED, "Usuario no encontrado");
                    securityEventService.record(SecurityEventService.LOGIN_FAILED,
                            null, request.username(), null, ip, userAgent, null, null);
                    throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        // 2. Verificar bloqueo de cuenta
        checkNotLocked(usuario, ip, userAgent);

        // 3. Verificar contraseña
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            handleFailedAttempt(usuario, ip, userAgent);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 4. Si tiene 2FA activo → emitir challenge token y detenerse aquí
        if (usuario.isTotpEnabled()) {
            String rawChallenge = generateSecureToken();
            MfaChallengeToken challenge = MfaChallengeToken.builder()
                    .socioId(usuario.getSocioId())
                    .tokenHash(hashToken(rawChallenge))
                    .ipAddress(ip)
                    .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                    .expiresAt(LocalDateTime.now().plusSeconds(MFA_CHALLENGE_EXPIRY_SECONDS))
                    .build();
            mfaChallengeTokenRepository.save(challenge);

            auditService.registrar(usuario.getUsername(), "LOGIN_MFA_CHALLENGE", ENTIDAD_USUARIOS_AUTH,
                    usuario.getId(), null, null, ip, userAgent, "PENDING", "Desafío MFA emitido");

            return new LoginStepResult.MfaRequired(
                    new MfaChallengeResponse(rawChallenge, MFA_CHALLENGE_EXPIRY_SECONDS));
        }

        // 5. Sin 2FA → completar login directamente (puede retornar CountryRequired si país nuevo)
        return completarLogin(usuario, ip, userAgent, false);
    }

    /**
     * Paso 2 (solo si hay 2FA): valida el challenge token + código TOTP y completa el login.
     */
    public LoginResult completeMfaLogin(MfaLoginRequest request, String ip, String userAgent) {
        MfaChallengeToken challenge = mfaChallengeTokenRepository
                .findByTokenHash(hashToken(request.challengeToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID,
                        "Token de desafío inválido o expirado"));

        if (!challenge.isValid()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    "El desafío MFA ha expirado. Por favor inicia sesión de nuevo.");
        }

        if (challenge.getAttempts() >= MFA_MAX_ATTEMPTS) {
            challenge.setUsed(true);
            mfaChallengeTokenRepository.save(challenge);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Demasiados intentos incorrectos. Por favor inicia sesión de nuevo.");
        }

        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(challenge.getSocioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        if (!totpService.verify(usuario.getTotpSecret(), request.mfaCode())) {
            challenge.setAttempts((short) (challenge.getAttempts() + 1));
            mfaChallengeTokenRepository.save(challenge);
            auditService.registrar(usuario.getUsername(), "LOGIN_MFA_FAILED", ENTIDAD_USUARIOS_AUTH,
                    usuario.getId(), null, null, ip, userAgent, RESULTADO_FAILED,
                    "Código TOTP inválido (intento " + challenge.getAttempts() + ")");
            throw new BusinessException(ErrorCode.MFA_INVALID);
        }

        // Marcar el challenge como usado (un solo uso)
        challenge.setUsed(true);
        mfaChallengeTokenRepository.save(challenge);

        // Los usuarios con 2FA no pueden llegar a CountryRequired (applyLoginRules no bloquea con MFA)
        LoginStepResult step = completarLogin(usuario, ip, userAgent, false);
        if (step instanceof LoginStepResult.Completed(var result)) return result;
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Error inesperado durante el login con 2FA.");
    }

    /**
     * Paso 2 del country challenge: valida el token + código enviado por email y completa el login.
     */
    public LoginResult completeCountryChallenge(CountryChallengeVerifyRequest request,
                                                String ip, String userAgent) {
        CountryChallengeToken challenge = countryChallengeTokenRepository
                .findByTokenHash(hashToken(request.challengeToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID,
                        "Token de verificación inválido o expirado"));

        if (!challenge.isValid()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    "El código ha expirado. Por favor inicia sesión de nuevo.");
        }

        if (challenge.getAttempts() >= COUNTRY_CHALLENGE_MAX_ATTEMPTS) {
            challenge.setUsed(true);
            countryChallengeTokenRepository.save(challenge);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Demasiados intentos incorrectos. Por favor inicia sesión de nuevo.");
        }

        if (!challenge.getCodeHash().equals(hashToken(request.code()))) {
            short intentos = (short) (challenge.getAttempts() + 1);
            challenge.setAttempts(intentos);
            countryChallengeTokenRepository.save(challenge);
            int restantes = COUNTRY_CHALLENGE_MAX_ATTEMPTS - intentos;
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    "Código incorrecto. " + (restantes > 0 ? "Intentos restantes: " + restantes : "Sin más intentos."));
        }

        challenge.setUsed(true);
        countryChallengeTokenRepository.save(challenge);

        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(challenge.getSocioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        // Registrar NEW_COUNTRY_LOGIN ahora que pasó el reto
        GeoIpService.GeoLocation geo = geoIpService.lookup(ip);
        if (geo != null && geo.countryCode() != null) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("new_country", geo.countryCode());
            String deviceId = computeDeviceId(userAgent, PlatformDetector.detect(userAgent));
            securityEventService.record(SecurityEventService.NEW_COUNTRY_LOGIN, 
                    usuario.getSocioId(), usuario.getUsername(), null, ip, userAgent, deviceId, meta);
        }

        LoginStepResult step = completarLogin(usuario, ip, userAgent, true);
        if (step instanceof LoginStepResult.Completed(var result)) return result;
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Error inesperado al completar la verificación.");
    }

    /** Paso final común: emite access token + refresh token y registra el login exitoso. */
    private LoginStepResult completarLogin(UsuarioAuth usuario, String ip, String userAgent, boolean skipCountryCheck) {
        SocioAuthView socioInfo = usuarioAuthRepository.findSocioAuthView(usuario.getSocioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        // Único check de acceso: solo estado ACTIVE puede iniciar sesión
        if (!"ACTIVE".equals(socioInfo.getEstadoAcceso())) {
            auditService.registrar(usuario.getUsername(), "LOGIN_BLOCKED", ENTIDAD_USUARIOS_AUTH,
                    usuario.getId(), null, null, ip, userAgent, "BLOCKED",
                    "Acceso denegado: estado_acceso=" + socioInfo.getEstadoAcceso());
            securityEventService.record(SecurityEventService.LOGIN_BLOCKED,
                    usuario.getSocioId(), usuario.getUsername(),
                    null, ip, userAgent, null, null);
            throw new BusinessException(ErrorCode.ACCESO_SISTEMA_BLOQUEADO);
        }

        boolean inhabilitado = "Inhabilitado".equals(socioInfo.getEstadoHabilitacion());
        usuario.setFailedAttempts((short) 0);
        usuario.setLoginBlocked(false);
        usuario.setBlockedUntil(null);
        usuario.setLastLogin(LocalDateTime.now());
        usuarioAuthRepository.save(usuario);

        String platform  = PlatformDetector.detect(userAgent);
        String deviceId  = computeDeviceId(userAgent, platform);
        String nombre    = socioInfo.getNombre() + " " + socioInfo.getApellido();
        String accessToken = jwtService.generateAccessToken(
                usuario.getSocioId(), usuario.getUsername(), socioInfo.getRolNombre(), nombre);

        // Revocar sesión anterior del mismo dispositivo + sesiones legacy sin device_id
        LocalDateTime now = LocalDateTime.now();
        if (deviceId != null) {
            refreshTokenRepository.revokeByDeviceId(usuario.getSocioId(), deviceId, now);
            refreshTokenRepository.revokeLegacySessionsBySocioId(usuario.getSocioId(), now);
        }

        String rawRefreshToken = generateSecureToken();
        RefreshToken rt = saveRefreshToken(
                usuario.getSocioId(), hashToken(rawRefreshToken), ip, userAgent, platform, deviceId);

        // Reglas automáticas: nuevo dispositivo, nuevo país
        boolean blockForNewCountry = false;
        if (!skipCountryCheck) {
            blockForNewCountry = securityEventService.applyLoginRules(
                    usuario.getSocioId(), usuario.getUsername(), rt.getId(),
                    ip, userAgent, deviceId,
                    usuario.isTotpEnabled(), socioInfo.getCorreo(), nombre);
        }

        if (!blockForNewCountry) {
            // Registrar LOGIN_SUCCESS solo si el acceso fue completado
            String auditExtra = inhabilitado ? "Socio inhabilitado — acceso con restricciones" : null;
            auditService.registrar(usuario.getUsername(), "LOGIN_SUCCESS", ENTIDAD_USUARIOS_AUTH,
                    usuario.getId(), null, null, ip, userAgent, RESULTADO_OK, auditExtra);
            securityEventService.record(SecurityEventService.LOGIN_SUCCESS,
                    usuario.getSocioId(), usuario.getUsername(),
                    rt.getId(), ip, userAgent, deviceId, null);
        }

        if (blockForNewCountry) {
            // Revocar el token recién creado — el login queda en pausa hasta verificación por email
            rt.setRevoked(true);
            rt.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(rt);

            // Emitir challenge token y enviar código de 6 dígitos por email
            String rawChallenge = generateSecureToken();
            int codeInt = secureRandom.nextInt(1_000_000);
            String code = String.format("%06d", codeInt);

            CountryChallengeToken countryChallenge = CountryChallengeToken.builder()
                    .socioId(usuario.getSocioId())
                    .tokenHash(hashToken(rawChallenge))
                    .codeHash(hashToken(code))
                    .ipAddress(ip)
                    .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                    .expiresAt(LocalDateTime.now().plusSeconds(COUNTRY_CHALLENGE_EXPIRY_SECONDS))
                    .build();
            countryChallengeTokenRepository.save(countryChallenge);

            GeoIpService.GeoLocation geo = geoIpService.lookup(ip);
            String[] parsed = securityEventService.parseUa(userAgent);
            alertMailSender.sendCountryChallengeCode(
                    socioInfo.getCorreo(), nombre, code,
                    geo != null ? geo.countryCode() : null,
                    geo != null ? geo.city() : null,
                    parsed[0]);

            log.info("Country challenge emitido para {} desde país desconocido (ip={})",
                    usuario.getUsername(), ip);
            return new LoginStepResult.CountryRequired(
                    new CountryChallengeResponse(rawChallenge, COUNTRY_CHALLENGE_EXPIRY_SECONDS));
        }

        log.info("Login exitoso: {} (inhabilitado={})", usuario.getUsername(), inhabilitado);
        boolean esJefeMontana = Boolean.TRUE.equals(socioInfo.getEsJefeMontana());
        LoginResponse body = LoginResponse.of(
                accessToken,
                jwtProperties.getAccessTokenExpirationSeconds(),
                usuario.getSocioId(), usuario.getUsername(), nombre,
                socioInfo.getRolNombre(), socioInfo.getNivelTecnico(),
                usuario.isPasswordMustChange(), inhabilitado, esJefeMontana);
        return new LoginStepResult.Completed(new LoginResult(body, rawRefreshToken));
    }

    // =========================================================================
    // Refresh
    // =========================================================================

    public LoginResult refresh(String rawRefreshToken, String ip, String userAgent) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        if (!stored.isValid()) {
            if (stored.isRevoked()) {
                // Token revocado reutilizado: posible robo. Revocar TODOS los tokens del socio.
                log.warn("Posible robo de refresh token — revocando todos los tokens de socio_id={}",
                        stored.getSocioId());
                int revocados = refreshTokenRepository.revokeAllBySocioId(stored.getSocioId(), LocalDateTime.now());
                Map<String, Object> meta = new HashMap<>();
                meta.put("revoked_token_id", stored.getId().toString());
                meta.put(META_SESSIONS_REVOKED, revocados);
                securityEventService.record(SecurityEventService.REFRESH_TOKEN_REUSED,
                        stored.getSocioId(), null, stored.getId(), ip, userAgent, null, meta);
            }
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // Rotación: revocar el token actual antes de emitir uno nuevo
        stored.setRevoked(true);
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        // Cargar datos actualizados del usuario
        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(stored.getSocioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        SocioAuthView socioInfo = usuarioAuthRepository.findSocioAuthView(usuario.getSocioId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        boolean inhabilitado = "Inhabilitado".equals(socioInfo.getEstadoHabilitacion());

        String nombre      = socioInfo.getNombre() + " " + socioInfo.getApellido();
        String accessToken = jwtService.generateAccessToken(
                usuario.getSocioId(), usuario.getUsername(), socioInfo.getRolNombre(), nombre);

        String rawNewToken = generateSecureToken();
        RefreshToken newToken = saveRefreshToken(
                usuario.getSocioId(), hashToken(rawNewToken), ip, userAgent,
                stored.getPlatform(), stored.getDeviceId());
        // Actualizar lastUsedAt del token anterior (ya revocado) para historial
        newToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(newToken);

        boolean esJefeMontana = Boolean.TRUE.equals(socioInfo.getEsJefeMontana());
        LoginResponse body = LoginResponse.of(
                accessToken,
                jwtProperties.getAccessTokenExpirationSeconds(),
                usuario.getSocioId(), usuario.getUsername(), nombre,
                socioInfo.getRolNombre(), socioInfo.getNivelTecnico(),
                usuario.isPasswordMustChange(), inhabilitado, esJefeMontana);
        return new LoginResult(body, rawNewToken);
    }

    // =========================================================================
    // Logout
    // =========================================================================

    public void logout(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
        // Siempre responder 200 — no revelar si el token existía o no
    }

    /** Revoca todos los refresh tokens del socio (logout de todos los dispositivos). */
    public void logoutAll(UUID socioId, String username) {
        int revocados = refreshTokenRepository.revokeAllBySocioId(socioId, LocalDateTime.now());
        securityEventService.record(SecurityEventService.SESSION_REVOKED_ALL,
                socioId, username, null, null, null, null, null);
        log.info("Logout global para {} (socio_id={}): {} tokens revocados", username, socioId, revocados);
    }

    // =========================================================================
    // Gestión de sesiones activas
    // =========================================================================

    /** Lista las sesiones activas del socio. Marca como current la que coincide con el hash dado. */
    public List<SessionResponse> listSessions(UUID socioId, String currentTokenHash) {
        return refreshTokenRepository.findActiveBySocioId(socioId, LocalDateTime.now())
                .stream()
                .map(rt -> {
                    boolean isCurrent = currentTokenHash != null
                            && currentTokenHash.equals(rt.getTokenHash());
                    String[] parsed = securityEventService.parseUa(rt.getUserAgent());
                    GeoIpService.GeoLocation geo = geoIpService.lookup(rt.getIpAddress());
                    return new SessionResponse(
                            rt.getId(),
                            rt.getPlatform(),
                            parsed[0],
                            parsed[1],
                            geo != null ? geo.city() : null,
                            geo != null ? geo.countryCode() : null,
                            rt.getIpAddress(),
                            rt.getCreatedAt(),
                            rt.getLastUsedAt(),
                            isCurrent);
                })
                .toList();
    }

    /** Revoca una sesión específica del socio. Lanza excepción si no pertenece al socio. */
    public void revokeSession(UUID socioId, UUID sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "Sesión no encontrada"));

        if (!token.getSocioId().equals(socioId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (token.isRevoked()) return;

        token.setRevoked(true);
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        Map<String, Object> meta = new HashMap<>();
        meta.put("revoked_session_id", sessionId.toString());
        meta.put("revoked_by", "user");
        securityEventService.record(SecurityEventService.SESSION_REVOKED,
                socioId, null, sessionId, null, null, null, meta);
    }

    /** Revoca todas las sesiones del socio excepto la que corresponde al hash dado. */
    public void revokeOtherSessionsByHash(UUID socioId, String username, String currentTokenHash) {
        UUID currentSessionId = null;
        if (currentTokenHash != null) {
            currentSessionId = refreshTokenRepository.findByTokenHash(currentTokenHash)
                    .map(rt -> rt.getId())
                    .orElse(null);
        }
        int revocados = (currentSessionId != null)
                ? refreshTokenRepository.revokeAllBySocioIdExcept(socioId, currentSessionId, LocalDateTime.now())
                : refreshTokenRepository.revokeAllBySocioId(socioId, LocalDateTime.now());

        Map<String, Object> meta = new HashMap<>();
        meta.put(META_SESSIONS_REVOKED, revocados);
        securityEventService.record(SecurityEventService.SESSION_REVOKED_ALL,
                socioId, null, currentSessionId, null, null, null, meta);
        log.info("Otras sesiones cerradas para {} (socio_id={}): {} revocadas", username, socioId, revocados);
    }

    /** Reporta actividad sospechosa: revoca todas las sesiones y registra el evento. */
    public void reportSuspiciousActivity(UUID socioId, String username,
                                         String ip, String userAgent) {
        int revocados = refreshTokenRepository.revokeAllBySocioId(socioId, LocalDateTime.now());
        Map<String, Object> meta = new HashMap<>();
        meta.put(META_SESSIONS_REVOKED, revocados);
        securityEventService.record(SecurityEventService.SUSPICIOUS_ACTIVITY_REPORTED,
                socioId, username, null, ip, userAgent, null, meta);
        log.warn("Actividad sospechosa reportada por {} (socio_id={}): {} sesiones revocadas", username, socioId, revocados);
    }

    // =========================================================================
    // MFA — Setup y gestión del 2FA
    // =========================================================================

    @Transactional(readOnly = true)
    public boolean isMfaEnabled(UUID socioId) {
        return usuarioAuthRepository.findBySocioId(socioId)
                .map(UsuarioAuth::isTotpEnabled)
                .orElse(false);
    }

    /**
     * Paso 1 del setup MFA: genera el secret TOTP y lo guarda en BD (aún no habilitado).
     * El cliente debe escanear el QR y confirmar con un código válido.
     *
     * @return datos para mostrar el QR al usuario
     */
    public MfaSetupResponse setupMfa(UUID socioId) {
        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        TotpService.TotpSecret secret = totpService.generateSecret();
        // Guardar cifrado en BD pero SIN activar (totpEnabled permanece false)
        usuario.setTotpSecret(secret.encrypted());
        usuario.setTotpEnabled(false);
        usuarioAuthRepository.save(usuario);

        return new MfaSetupResponse(
                totpService.buildOtpAuthUri(usuario.getUsername(), secret.base32()),
                secret.base32());
    }

    /**
     * Paso 2 del setup MFA: confirma con un código TOTP válido y activa el 2FA.
     */
    public void confirmMfa(UUID socioId, String code) {
        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        if (usuario.getTotpSecret() == null) {
            throw new BusinessException(ErrorCode.MFA_INVALID,
                    "No hay secreto TOTP pendiente. Inicia el proceso de configuración primero.");
        }
        if (!totpService.verify(usuario.getTotpSecret(), code)) {
            throw new BusinessException(ErrorCode.MFA_INVALID);
        }
        usuario.setTotpEnabled(true);
        usuarioAuthRepository.save(usuario);
        log.info("2FA activado para socio_id={}", socioId);
    }

    /**
     * Desactiva el 2FA. Requiere un código TOTP válido para confirmar la identidad.
     */
    public void disableMfa(UUID socioId, String code) {
        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        if (!usuario.isTotpEnabled()) {
            return;  // Ya está desactivado, no hacer nada
        }
        if (!totpService.verify(usuario.getTotpSecret(), code)) {
            throw new BusinessException(ErrorCode.MFA_INVALID);
        }
        usuario.setTotpEnabled(false);
        usuario.setTotpSecret(null);
        usuarioAuthRepository.save(usuario);
        log.info("2FA desactivado para socio_id={}", socioId);
    }

    // =========================================================================
    // Reset de emergencia por pérdida de teléfono (admin/secretaria)
    // =========================================================================

    /**
     * Reset de emergencia ejecutado por un admin/secretaria cuando un socio pierde su teléfono.
     *
     * <p>Realiza de forma atómica:
     * <ol>
     *   <li>Desactiva el 2FA del socio target.</li>
     *   <li>Revoca todas sus sesiones activas.</li>
     *   <li>Marca que debe cambiar la contraseña en su próximo acceso.</li>
     *   <li>Envía email de reset de contraseña al correo del socio.</li>
     * </ol>
     *
     * @param targetSocioId socio al que se le aplica el reset
     * @param adminUsername username del admin/secretaria que ejecuta la acción (para auditoría)
     */
    public void emergencyReset(UUID targetSocioId, String adminUsername) {
        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(targetSocioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        if (!usuario.isTotpEnabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El socio no tiene 2FA activo. Use la opción de cerrar sesiones si es necesario.");
        }

        Socio socio = socioRepository.findById(targetSocioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        usuario.setTotpEnabled(false);
        usuario.setTotpSecret(null);
        usuario.setPasswordMustChange(true);
        // Invalidar la contraseña anterior — el socio solo puede acceder mediante el link de reset
        usuario.setPasswordHash(passwordEncoder.encode(generateSecureToken()));
        usuarioAuthRepository.save(usuario);

        refreshTokenRepository.revokeAllBySocioId(targetSocioId, LocalDateTime.now());

        // Usar el método de admin que omite el rate limit y garantiza el envío del email
        passwordResetService.initiateEmergencyReset(targetSocioId, socio.getCorreo());

        auditService.registrar(adminUsername, "EMERGENCY_RESET_2FA", ENTIDAD_USUARIOS_AUTH,
                usuario.getId(), null, null, null, null, RESULTADO_OK,
                "Reset de emergencia ejecutado sobre socio_id=" + targetSocioId);

        log.warn("Reset de emergencia ejecutado por {} sobre socio_id={}", adminUsername, targetSocioId);
    }

    // =========================================================================
    // Cambio de contraseña (autenticado)
    // =========================================================================

    /**
     * Valida que las contraseñas sean correctas sin realizar ningún cambio.
     * Permite al frontend confirmar la identidad antes de solicitar el código TOTP.
     *
     * @return true si el usuario tiene 2FA activo y debe presentar el código TOTP
     */
    @Transactional(readOnly = true)
    public boolean verifyPasswordChange(UUID socioId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La confirmación de contraseña no coincide.");
        }
        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), usuario.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "La contraseña actual es incorrecta.");
        }
        if (passwordEncoder.matches(request.newPassword(), usuario.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La nueva contraseña debe ser diferente a la actual.");
        }
        return usuario.isTotpEnabled();
    }

    /**
     * Cambia la contraseña del usuario autenticado.
     *
     * <p>Requisitos de seguridad:
     * <ul>
     *   <li>Se verifica la contraseña actual antes de permitir el cambio.</li>
     *   <li>La nueva contraseña no puede ser igual a la actual.</li>
     *   <li>Se invalidan todos los refresh tokens activos para forzar re-login en otros dispositivos.</li>
     * </ul>
     */
    public void changePassword(UUID socioId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La confirmación de contraseña no coincide.");
        }

        UsuarioAuth usuario = usuarioAuthRepository.findBySocioId(socioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIO_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), usuario.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "La contraseña actual es incorrecta.");
        }

        if (passwordEncoder.matches(request.newPassword(), usuario.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La nueva contraseña debe ser diferente a la actual.");
        }

        if (usuario.isTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Se requiere el código de autenticación 2FA para cambiar la contraseña.");
            }
            if (!totpService.verify(usuario.getTotpSecret(), request.totpCode())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                        "Código 2FA incorrecto.");
            }
        }

        usuario.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        usuario.setPasswordMustChange(false);
        usuarioAuthRepository.save(usuario);

        // Invalidar todos los refresh tokens para forzar re-login en otros dispositivos
        refreshTokenRepository.revokeAllBySocioId(socioId, LocalDateTime.now());

        auditService.registrar(usuario.getUsername(), "PASSWORD_CHANGED", ENTIDAD_USUARIOS_AUTH,
                usuario.getId(), null, null, null, null, RESULTADO_OK, "Contraseña cambiada exitosamente");
        securityEventService.record(SecurityEventService.PASSWORD_CHANGED,
                socioId, usuario.getUsername(), null, null, null, null, null);

        log.info("Contraseña cambiada para socio_id={}", socioId);
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private void checkNotLocked(UsuarioAuth usuario, String ip, String userAgent) {
        if (!usuario.isLoginBlocked()) return;

        // Auto-desbloqueo si expiró el periodo de bloqueo
        if (usuario.getBlockedUntil() != null
                && LocalDateTime.now().isAfter(usuario.getBlockedUntil())) {
            usuario.setLoginBlocked(false);
            usuario.setFailedAttempts((short) 0);
            usuario.setBlockedUntil(null);
            usuarioAuthRepository.save(usuario);
            return;
        }

        auditService.registrar(usuario.getUsername(), "LOGIN_BLOCKED", ENTIDAD_USUARIOS_AUTH,
                usuario.getId(), null, null, ip, userAgent, "BLOCKED",
                "Cuenta bloqueada hasta: " + usuario.getBlockedUntil());
        throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
    }

    private void handleFailedAttempt(UsuarioAuth usuario, String ip, String userAgent) {
        short intentos = (short) (usuario.getFailedAttempts() + 1);
        usuario.setFailedAttempts(intentos);

        if (intentos >= maxLoginAttempts()) {
            usuario.setLoginBlocked(true);
            usuario.setBlockedUntil(LocalDateTime.now().plusHours(lockoutDurationHours()));
            log.warn("Cuenta bloqueada por {} intentos fallidos: {}", intentos, usuario.getUsername());
        }
        usuarioAuthRepository.save(usuario);

        auditService.registrar(usuario.getUsername(), "LOGIN_FAILED", ENTIDAD_USUARIOS_AUTH,
                usuario.getId(), null, null, ip, userAgent, RESULTADO_FAILED,
                "Intento fallido #" + intentos);
        securityEventService.record(SecurityEventService.LOGIN_FAILED,
                usuario.getSocioId(), usuario.getUsername(), null, ip, userAgent, null, null);
    }

    private int maxLoginAttempts() {
        return configRepo.findByClave("MAX_INTENTOS_LOGIN")
                .map(c -> { try { return Integer.parseInt(c.getValor()); } catch (NumberFormatException e) { return null; } })
                .filter(v -> v != null && v > 0)
                .orElse(securityProperties.getMaxLoginAttempts());
    }

    private int lockoutDurationHours() {
        return configRepo.findByClave("HORAS_BLOQUEO_LOGIN")
                .map(c -> { try { return Integer.parseInt(c.getValor()); } catch (NumberFormatException e) { return null; } })
                .filter(v -> v != null && v > 0)
                .orElse(securityProperties.getLockoutDurationHours());
    }



    private RefreshToken saveRefreshToken(UUID socioId, String tokenHash, String ip,
                                          String userAgent, String platform, String deviceId) {
        String ua = (userAgent != null && userAgent.length() > 500)
                ? userAgent.substring(0, 500) : userAgent;

        RefreshToken token = RefreshToken.builder()
                .socioId(socioId)
                .tokenHash(tokenHash)
                .userAgent(ua)
                .ipAddress(ip)
                .platform(platform)
                .deviceId(deviceId)
                .expiresAt(LocalDateTime.now()
                        .plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds()))
                .build();
        return refreshTokenRepository.save(token);
    }

    /**
     * Calcula el device_id como SHA-256(userAgent + platform) truncado a 32 hex chars.
     * Identifica el binomio dispositivo+browser, no al usuario.
     */
    private String computeDeviceId(String userAgent, String platform) {
        if (userAgent == null || userAgent.isBlank()) return null;
        try {
            String input = userAgent + platform;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

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
