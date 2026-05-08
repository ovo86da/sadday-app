package com.sadday.app.auth;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.controller.AuthController;
import com.sadday.app.auth.dto.LoginRequest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del módulo Auth.
 *
 * <p>Valida el flujo basado en cookie HttpOnly para el refresh token:
 * <ul>
 *   <li>Login devuelve el refresh token en {@code Set-Cookie}, no en el body JSON.</li>
 *   <li>Refresh y Logout leen el token de la cookie (no de un body).</li>
 *   <li>Logout elimina la cookie (Max-Age=0).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql("/sql/auth-test-data.sql")
@DisplayName("Auth — Integration Tests")
class AuthIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;
    @Autowired JdbcClient            jdbcClient;

    private static final UUID   SOCIO_ID      =
            UUID.fromString("00000000-0000-4000-a000-000000000001");

    @BeforeEach
    void setUpTestUser() {
        UsuarioAuth usuario = UsuarioAuth.builder()
                .socioId(SOCIO_ID)
                .username("test.user")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .build();
        usuarioAuthRepository.saveAndFlush(usuario);

        UsuarioAuth inhabilitado = UsuarioAuth.builder()
                .socioId(UUID.fromString("00000000-0000-4000-a000-000000000003"))
                .username("inhabilitado.user")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .build();
        usuarioAuthRepository.saveAndFlush(inhabilitado);
    }

    // =========================================================================
    // Login
    // =========================================================================

    @Test
    @DisplayName("POST /auth/login — credenciales correctas → 200, access token en body, refresh en cookie")
    void login_validCredentials_returns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test.user", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("test.user"))
                .andExpect(jsonPath("$.data.rol").value("Socio"))
                // El refresh token NO debe aparecer en el body
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        // El refresh token debe estar en la cookie HttpOnly
        Cookie cookie = result.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
        assertNotNull(cookie, "Debe existir la cookie de refresh token");
        assertFalse(cookie.getValue().isBlank(), "La cookie debe tener valor");
        assertTrue(cookie.isHttpOnly(), "La cookie debe ser HttpOnly");
        assertEquals("/api/v1/auth", cookie.getPath(), "Path debe restringirse a /api/v1/auth");
        assertTrue(cookie.getMaxAge() > 0, "Max-Age debe ser positivo");

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"), "La respuesta no debe contener el hash de contraseña");
    }

    @Test
    @DisplayName("POST /auth/login — contraseña incorrecta → 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test.user", "WrongPassword!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/login — usuario inexistente → 401 (mismo error, sin revelar existencia)")
    void login_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("no.existe", "AnyPassword1!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("POST /auth/login — socio inhabilitado → 200 con inhabilitado:true")
    void login_inhabilitadoSocio_returns200ConFlag() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("inhabilitado.user", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inhabilitado").value(true));
    }

    @Test
    @DisplayName("POST /auth/login — body inválido → 422")
    void login_emptyBody_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(422));
    }

    // =========================================================================
    // Refresh
    // =========================================================================

    @Test
    @DisplayName("POST /auth/refresh — cookie válida → 200 con nuevo access token y cookie rotada")
    void refresh_validCookie_returns200() throws Exception {
        // 1. Login para obtener la cookie de refresh
        String refreshToken = obtenerRefreshTokenDeCookie();

        // 2. Refresh enviando la cookie + header CSRF requerido
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(AuthController.CSRF_HEADER_NAME, AuthController.CSRF_HEADER_VALUE)
                        .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        // 3. La cookie rotada debe ser diferente a la original
        Cookie newCookie = result.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
        assertNotNull(newCookie, "Debe emitirse una nueva cookie tras el refresh");
        assertNotEquals(refreshToken, newCookie.getValue(), "El refresh token debe rotar");
    }

    @Test
    @DisplayName("POST /auth/refresh — sin cookie → 401")
    void refresh_sinCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(AuthController.CSRF_HEADER_NAME, AuthController.CSRF_HEADER_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/refresh — cookie con token inválido → 401")
    void refresh_cookieInvalida_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(AuthController.CSRF_HEADER_NAME, AuthController.CSRF_HEADER_VALUE)
                        .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, "token-falso")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/refresh — reutilizar token ya rotado → 401 (detección de robo)")
    void refresh_reuseRotatedToken_returns401() throws Exception {
        String original = obtenerRefreshTokenDeCookie();

        // Primer uso — rota el token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(AuthController.CSRF_HEADER_NAME, AuthController.CSRF_HEADER_VALUE)
                        .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, original)))
                .andExpect(status().isOk());

        // Segundo uso con el token original ya revocado → 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(AuthController.CSRF_HEADER_NAME, AuthController.CSRF_HEADER_VALUE)
                        .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, original)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Logout
    // =========================================================================

    @Test
    @DisplayName("POST /auth/logout — cookie válida → 200 y cookie eliminada")
    void logout_validCookie_returns200_cookieCleared() throws Exception {
        String accessToken  = obtenerToken("test.user");
        String refreshToken = obtenerRefreshTokenDeCookie();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        // La cookie debe ser eliminada (Max-Age = 0)
        Cookie clearedCookie = result.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
        assertNotNull(clearedCookie, "Debe emitirse la cookie de borrado");
        assertEquals(0, clearedCookie.getMaxAge(), "Max-Age debe ser 0 para eliminar la cookie");

        // Intentar refresh con el token revocado → 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(AuthController.CSRF_HEADER_NAME, AuthController.CSRF_HEADER_VALUE)
                        .cookie(new Cookie(AuthController.REFRESH_COOKIE_NAME, refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/logout — sin autenticación → 401")
    void logout_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Registro
    // =========================================================================

    @Test
    @DisplayName("POST /registro/complete — token inválido → 401")
    void registro_invalidToken_returns401() throws Exception {
        var request = new com.sadday.app.auth.dto.CompleteRegistroRequest(
                "token-inexistente",
                null, null, null, null, null,
                null, null, null, null, null, null,
                "nuevo.usuario", "NuevaPassword123!", "NuevaPassword123!");

        mockMvc.perform(post("/api/v1/registro/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /** Hace login y extrae el valor del refresh token desde la cookie Set-Cookie. */
    private String obtenerRefreshTokenDeCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test.user", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
        assertNotNull(cookie, "El login debe emitir la cookie de refresh token");
        return cookie.getValue();
    }
}
