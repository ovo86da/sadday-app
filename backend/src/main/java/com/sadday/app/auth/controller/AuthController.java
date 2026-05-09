package com.sadday.app.auth.controller;

import com.sadday.app.auth.dto.ChangePasswordRequest;
import com.sadday.app.auth.dto.CountryChallengeVerifyRequest;
import com.sadday.app.auth.dto.ForgotPasswordRequest;
import com.sadday.app.auth.dto.LoginRequest;
import com.sadday.app.auth.dto.LoginResponse;
import com.sadday.app.auth.dto.LoginResult;
import com.sadday.app.auth.dto.LoginStepResult;

import com.sadday.app.auth.dto.MfaConfirmRequest;
import com.sadday.app.auth.dto.MfaLoginRequest;
import com.sadday.app.auth.dto.MfaSetupResponse;
import com.sadday.app.auth.dto.ResetPasswordRequest;
import com.sadday.app.auth.dto.SessionResponse;
import com.sadday.app.auth.service.AuthService;
import com.sadday.app.auth.service.PasswordResetService;
import com.sadday.app.config.AuthProperties;
import com.sadday.app.security.jwt.JwtProperties;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import com.sadday.app.shared.util.ClientIpExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Controlador de autenticación.
 *
 * <h2>Estrategia de tokens</h2>
 * <ul>
 *   <li><b>Access token</b>: devuelto en el body JSON. El cliente lo guarda <em>en memoria</em>
 *       (variable JS), nunca en localStorage. Expira en 15 min.</li>
 *   <li><b>Refresh token</b>: enviado como cookie {@code HttpOnly; Secure; SameSite=Strict}.
 *       JavaScript nunca puede leerlo. El browser lo envía automáticamente en cada request
 *       a {@code /api/v1/auth/*}. Expira en 30 días (rotativo).</li>
 * </ul>
 *
 * <h2>Endpoints públicos</h2>
 * <ul>
 *   <li>POST /api/v1/auth/login</li>
 *   <li>POST /api/v1/auth/refresh</li>
 *   <li>POST /api/v1/auth/forgot-password</li>
 *   <li>POST /api/v1/auth/reset-password</li>
 * </ul>
 *
 * <h2>Endpoints autenticados</h2>
 * <ul>
 *   <li>POST /api/v1/auth/logout</li>
 *   <li>POST /api/v1/auth/logout-all</li>
 *   <li>POST /api/v1/auth/mfa/setup</li>
 *   <li>POST /api/v1/auth/mfa/confirm</li>
 *   <li>DELETE /api/v1/auth/mfa</li>
 * </ul>
 */
@RestController
@RequestMapping(ApiPaths.AUTH)
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Autenticación y gestión de sesión")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME  = "refresh_token";
    /** Header requerido en /refresh para prevenir CSRF (custom header CORS pattern). */
    public static final String CSRF_HEADER_NAME  = "X-Sadday-Client";
    public static final String CSRF_HEADER_VALUE = "spa";

    private final AuthService         authService;
    private final PasswordResetService passwordResetService;
    private final JwtProperties       jwtProperties;
    private final AuthProperties      authProperties;
    private final ClientIpExtractor   clientIpExtractor;

    // =========================================================================
    // Endpoints públicos
    // =========================================================================

    @PostMapping("/login")
    @Operation(summary = "Paso 1 del login: valida username y contraseña",
               description = "Sin 2FA ni país desconocido: 200 con access token y refresh cookie. " +
                             "Con 2FA: 202 con challengeToken (POST /mfa/login para completar). " +
                             "País desconocido sin 2FA: 202 con countryChallengeToken (POST /country-challenge/verify).")
    public ResponseEntity<Object> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        LoginStepResult step = authService.login(
                request, extractIp(httpRequest), extractUserAgent(httpRequest));

        return switch (step) {
            case LoginStepResult.Completed c -> ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(c.result().rawRefreshToken()).toString())
                    .body(ApiResponse.ok(c.result().response()));
            case LoginStepResult.MfaRequired m -> ResponseEntity.accepted()
                    .body(ApiResponse.ok(m.challenge()));
            case LoginStepResult.CountryRequired cr -> ResponseEntity.accepted()
                    .body(ApiResponse.ok(cr.challenge()));
        };
    }

    @PostMapping("/country-challenge/verify")
    @Operation(summary = "Verificación de país: valida el código enviado por email",
               description = "Completa el login cuando se detectó un país desconocido. " +
                             "Devuelve 200 con access token y refresh cookie.")
    public ResponseEntity<ApiResponse<LoginResponse>> countryChallenge(
            @Valid @RequestBody CountryChallengeVerifyRequest request,
            HttpServletRequest httpRequest) {

        LoginResult result = authService.completeCountryChallenge(
                request, extractIp(httpRequest), extractUserAgent(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.rawRefreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    @PostMapping("/mfa/login")
    @Operation(summary = "Paso 2 del login: valida el código TOTP con el challengeToken",
               description = "Completa el login MFA. Devuelve 200 con access token y refresh cookie.")
    public ResponseEntity<ApiResponse<LoginResponse>> mfaLogin(
            @Valid @RequestBody MfaLoginRequest request,
            HttpServletRequest httpRequest) {

        LoginResult result = authService.completeMfaLogin(
                request, extractIp(httpRequest), extractUserAgent(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.rawRefreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar access token",
               description = "Lee el refresh token desde la cookie HttpOnly (enviada automáticamente " +
                             "por el browser). Devuelve un nuevo access token en el body y rota la " +
                             "cookie de refresh token. Requiere el header X-Sadday-Client: spa.")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        if (!CSRF_HEADER_VALUE.equals(httpRequest.getHeader(CSRF_HEADER_NAME))) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.error("Header de cliente ausente o inválido"));
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Token de sesión ausente o inválido"));
        }

        LoginResult result = authService.refresh(
                refreshToken, extractIp(httpRequest), extractUserAgent(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.rawRefreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Solicitar recuperación de contraseña por email",
               description = "Siempre responde 200 para no revelar si el correo está registrado.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.initiate(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer contraseña con el token recibido por email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.complete(request);
        return ResponseEntity.ok(ApiResponse.ok("Contraseña restablecida correctamente."));
    }

    // =========================================================================
    // Endpoints autenticados
    // =========================================================================

    @PostMapping("/change-password/verify")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verificar contraseñas antes de cambiarlas (preflight)",
               description = "Valida la contraseña actual y la nueva sin aplicar el cambio. " +
                             "Devuelve totpRequired=true si el usuario tiene 2FA activo.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Boolean>>> verifyPasswordChange(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        UUID socioId = extractSocioId(authentication);
        boolean totpRequired = authService.verifyPasswordChange(socioId, request);
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("totpRequired", totpRequired)));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cambiar contraseña (usuario autenticado)",
               description = "Requiere la contraseña actual. Invalida todos los refresh tokens " +
                             "activos para forzar re-login en otros dispositivos.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        UUID socioId = extractSocioId(authentication);
        authService.changePassword(socioId, request);
        return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada correctamente."));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cerrar sesión en el dispositivo actual",
               description = "Revoca el refresh token de la cookie y la elimina.")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {

        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.ok("Sesión cerrada correctamente."));
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cerrar sesión en todos los dispositivos",
               description = "Revoca todos los refresh tokens del usuario y elimina la cookie.")
    public ResponseEntity<ApiResponse<Void>> logoutAll(Authentication authentication) {
        UUID socioId = extractSocioId(authentication);
        authService.logoutAll(socioId, authentication.getName());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.ok("Sesión cerrada en todos los dispositivos."));
    }

    // =========================================================================
    // Gestión de sesiones activas
    // =========================================================================

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar sesiones activas",
               description = "Devuelve todas las sesiones activas del usuario. " +
                             "La sesión actual se identifica comparando el refresh token de la cookie.")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            Authentication authentication) {

        UUID socioId = extractSocioId(authentication);
        String currentHash = refreshToken != null ? hashToken(refreshToken) : null;
        List<SessionResponse> sessions = authService.listSessions(socioId, currentHash);
        return ResponseEntity.ok(ApiResponse.ok(sessions));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Revocar una sesión por ID",
               description = "Cierra la sesión indicada. Solo puede revocar sesiones propias.")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication) {

        authService.revokeSession(extractSocioId(authentication), sessionId);
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada correctamente."));
    }

    @DeleteMapping("/sessions/others")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cerrar todas las demás sesiones",
               description = "Revoca todas las sesiones activas excepto la actual.")
    public ResponseEntity<ApiResponse<Void>> revokeOtherSessions(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            Authentication authentication) {

        UUID socioId = extractSocioId(authentication);
        String currentHash = (refreshToken != null && !refreshToken.isBlank())
                ? hashToken(refreshToken) : null;
        authService.revokeOtherSessionsByHash(socioId, authentication.getName(), currentHash);
        return ResponseEntity.ok(ApiResponse.ok("Otras sesiones cerradas correctamente."));
    }

    @PostMapping("/report-suspicious")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reportar actividad sospechosa",
               description = "Revoca todas las sesiones activas y registra el evento de actividad sospechosa.")
    public ResponseEntity<ApiResponse<Void>> reportSuspicious(
            Authentication authentication,
            HttpServletRequest httpRequest) {

        UUID socioId = extractSocioId(authentication);
        String username = authentication.getName();
        authService.reportSuspiciousActivity(
                socioId, username, extractIp(httpRequest), extractUserAgent(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.ok("Actividad sospechosa registrada. Todas las sesiones han sido cerradas."));
    }

    // =========================================================================
    // 2FA — Gestión del TOTP
    // =========================================================================

    @GetMapping("/mfa/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar si el usuario tiene 2FA activo")
    public ResponseEntity<ApiResponse<java.util.Map<String, Boolean>>> mfaStatus(Authentication authentication) {
        UUID socioId = extractSocioId(authentication);
        boolean enabled = authService.isMfaEnabled(socioId);
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("totpEnabled", enabled)));
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Iniciar configuración de 2FA",
               description = "Genera el secret TOTP y devuelve la URI otpauth:// para el QR. " +
                             "El 2FA NO queda activo hasta confirmar con un código válido.")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa(Authentication authentication) {
        UUID socioId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(authService.setupMfa(socioId)));
    }

    @PostMapping("/mfa/confirm")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Confirmar activación de 2FA con un código TOTP válido")
    public ResponseEntity<ApiResponse<Void>> confirmMfa(
            @Valid @RequestBody MfaConfirmRequest request,
            Authentication authentication) {

        authService.confirmMfa(extractSocioId(authentication), request.code());
        return ResponseEntity.ok(ApiResponse.ok("Autenticación de dos factores activada correctamente."));
    }

    @DeleteMapping("/mfa")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Desactivar 2FA (requiere código TOTP válido para confirmar)")
    public ResponseEntity<ApiResponse<Void>> disableMfa(
            @Valid @RequestBody MfaConfirmRequest request,
            Authentication authentication) {

        authService.disableMfa(extractSocioId(authentication), request.code());
        return ResponseEntity.ok(ApiResponse.ok("Autenticación de dos factores desactivada."));
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Construye la cookie de refresh token con todos los atributos de seguridad.
     *
     * <ul>
     *   <li>{@code HttpOnly} — inaccesible desde JavaScript</li>
     *   <li>{@code Secure} — solo sobre HTTPS (activado en prod via AuthProperties)</li>
     *   <li>{@code SameSite=Strict} — solo en requests del mismo origen (protege contra CSRF)</li>
     *   <li>{@code Path=/api/v1/auth} — solo se envía a los endpoints de auth</li>
     *   <li>{@code Max-Age} — igual a la duración del refresh token (30 días)</li>
     * </ul>
     */
    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .sameSite("Strict")
                .path(ApiPaths.AUTH)
                .maxAge(jwtProperties.getRefreshTokenExpirationSeconds())
                .build();
    }

    /** Emite una cookie vacía con Max-Age=0 para forzar su eliminación en el browser. */
    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .sameSite("Strict")
                .path(ApiPaths.AUTH)
                .maxAge(0)
                .build();
    }

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }

    private String extractIp(HttpServletRequest request) {
        return clientIpExtractor.extractIp(request);
    }

    private String extractUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
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
