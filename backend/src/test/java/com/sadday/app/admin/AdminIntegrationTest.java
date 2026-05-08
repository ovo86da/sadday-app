package com.sadday.app.admin;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.admin.dto.UpdateConfigRequest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql("/sql/admin-test-data.sql")
@DisplayName("Admin — Integration Tests")
class AdminIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;
    @PersistenceContext EntityManager entityManager;

    private static final UUID ADMIN_ID      = UUID.fromString("00000000-0000-4000-d000-000000000001");
    private static final UUID SECRETARIA_ID = UUID.fromString("00000000-0000-4000-d000-000000000002");
    private static final UUID SOCIO_ID      = UUID.fromString("00000000-0000-4000-d000-000000000003");
    private static final UUID TARGET_ID     = UUID.fromString("00000000-0000-4000-d000-000000000010");

    @BeforeEach
    void setUpUsuarios() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);

        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(ADMIN_ID).username("admin.test").passwordHash(hash).build());

        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(SECRETARIA_ID).username("secretaria.test").passwordHash(hash).build());

        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(SOCIO_ID).username("socio.test").passwordHash(hash).build());

        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(TARGET_ID).username("target.test").passwordHash(hash).build());
    }

    // =========================================================================
    // Log de auditoría
    // =========================================================================

    @Nested
    @DisplayName("GET /admin/auditoria")
    class AuditoriaTests {

        @Test
        @DisplayName("admin → 200 con página de resultados")
        void admin_returns200() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(get("/api/v1/admin/auditoria")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("secretaria → 200")
        void secretaria_returns200() throws Exception {
            String token = obtenerToken("secretaria.test");
            mockMvc.perform(get("/api/v1/admin/auditoria")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("socio regular → 403")
        void socio_returns403() throws Exception {
            String token = obtenerToken("socio.test");
            mockMvc.perform(get("/api/v1/admin/auditoria")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sin autenticación → 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/auditoria"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Eventos de seguridad
    // =========================================================================

    @Nested
    @DisplayName("GET /admin/security-events")
    class SecurityEventsTests {

        @Test
        @DisplayName("admin → 200 con página de resultados")
        void admin_returns200() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(get("/api/v1/admin/security-events")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("socio regular → 403")
        void socio_returns403() throws Exception {
            String token = obtenerToken("socio.test");
            mockMvc.perform(get("/api/v1/admin/security-events")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // Usuarios Auth
    // =========================================================================

    @Nested
    @DisplayName("GET /admin/usuarios-auth")
    class UsuariosAuthTests {

        @Test
        @DisplayName("admin → lista de cuentas (al menos las 4 del setUp)")
        void listar_admin_returnsList() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(get("/api/v1/admin/usuarios-auth")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(4)));
        }

        @Test
        @DisplayName("admin → cuenta específica de admin por socioId")
        void obtenerPorSocioId_admin_returns200() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(get("/api/v1/admin/usuarios-auth/" + ADMIN_ID)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("admin.test"))
                    .andExpect(jsonPath("$.data.loginBlocked").value(false));
        }

        @Test
        @DisplayName("socio → 403")
        void listar_socio_returns403() throws Exception {
            String token = obtenerToken("socio.test");
            mockMvc.perform(get("/api/v1/admin/usuarios-auth")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // Desbloquear cuenta
    // =========================================================================

    @Nested
    @DisplayName("POST /admin/usuarios-auth/{socioId}/desbloquear")
    class DesbloquearTests {

        @Test
        @DisplayName("admin desbloquea cuenta bloqueada → 200 y estado reset")
        void admin_desbloqueaCuentaBloqueada_returns200() throws Exception {
            // Bloquear via JPA para mantener el cache consistente
            UsuarioAuth target = usuarioAuthRepository.findBySocioId(TARGET_ID).orElseThrow();
            target.setLoginBlocked(true);
            target.setFailedAttempts((short) 3);
            usuarioAuthRepository.saveAndFlush(target);
            entityManager.clear(); // forzar recarga desde BD en la llamada del servicio

            String token = obtenerToken("admin.test");
            mockMvc.perform(post("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/desbloquear")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            entityManager.flush(); // asegurar que el save del servicio llegó a la BD
            entityManager.clear();

            UsuarioAuth updated = usuarioAuthRepository.findBySocioId(TARGET_ID).orElseThrow();
            org.junit.jupiter.api.Assertions.assertFalse(updated.isLoginBlocked());
            org.junit.jupiter.api.Assertions.assertEquals(0, updated.getFailedAttempts());
        }

        @Test
        @DisplayName("secretaria → 403 (solo Admin puede desbloquear)")
        void secretaria_returns403() throws Exception {
            String token = obtenerToken("secretaria.test");
            mockMvc.perform(post("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/desbloquear")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("socio regular → 403")
        void socio_returns403() throws Exception {
            String token = obtenerToken("socio.test");
            mockMvc.perform(post("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/desbloquear")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // Cambiar estado de acceso
    // =========================================================================

    @Nested
    @DisplayName("PATCH /admin/usuarios-auth/{socioId}/estado-acceso")
    class CambiarEstadoAccesoTests {

        @Test
        @DisplayName("admin bloquea target → 200")
        void admin_bloqueaTarget_returns200() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(patch("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/estado-acceso")
                            .param("codigo", "BLOCKED")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("admin intenta bloquearse a sí mismo (rol Admin protegido) → 400")
        void admin_intentaBloquearse_returns400() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(patch("/api/v1/admin/usuarios-auth/" + ADMIN_ID + "/estado-acceso")
                            .param("codigo", "BLOCKED")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("secretaria → 200 (puede cambiar estado de socios)")
        void secretaria_returns200() throws Exception {
            String token = obtenerToken("secretaria.test");
            mockMvc.perform(patch("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/estado-acceso")
                            .param("codigo", "BLOCKED")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("socio regular → 403")
        void socio_returns403() throws Exception {
            String token = obtenerToken("socio.test");
            mockMvc.perform(patch("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/estado-acceso")
                            .param("codigo", "BLOCKED")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // Forzar cierre de sesión
    // =========================================================================

    @Nested
    @DisplayName("POST /admin/usuarios-auth/{socioId}/cerrar-sesion")
    class CerrarSesionTests {

        @Test
        @DisplayName("admin → 200")
        void admin_returns200() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(post("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/cerrar-sesion")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("secretaria → 200 (puede forzar cierre de sesión)")
        void secretaria_returns200() throws Exception {
            String token = obtenerToken("secretaria.test");
            mockMvc.perform(post("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/cerrar-sesion")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("socio regular → 403")
        void socio_returns403() throws Exception {
            String token = obtenerToken("socio.test");
            mockMvc.perform(post("/api/v1/admin/usuarios-auth/" + TARGET_ID + "/cerrar-sesion")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // Configuración del sistema
    // =========================================================================

    @Nested
    @DisplayName("GET /admin/config")
    class ConfigListarTests {

        @Test
        @DisplayName("admin → 200 con claves del sistema")
        void admin_returns200() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(get("/api/v1/admin/config")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("socio regular → 403")
        void socio_returns403() throws Exception {
            String token = obtenerToken("socio.test");
            mockMvc.perform(get("/api/v1/admin/config")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /admin/config/{clave}")
    class ConfigObtenerTests {

        @Test
        @DisplayName("clave válida → 200 con valor")
        void claveValida_returns200() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(get("/api/v1/admin/config/MAX_INTENTOS_LOGIN")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.clave").value("MAX_INTENTOS_LOGIN"))
                    .andExpect(jsonPath("$.data.valor").isNotEmpty());
        }

        @Test
        @DisplayName("clave inexistente → 404")
        void claveInexistente_returns404() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(get("/api/v1/admin/config/NO_EXISTE")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /admin/config/{clave}")
    class ConfigActualizarTests {

        @Test
        @DisplayName("admin actualiza valor → 200 con nuevo valor")
        void admin_actualiza_returns200() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(patch("/api/v1/admin/config/MAX_INTENTOS_LOGIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateConfigRequest("5")))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.clave").value("MAX_INTENTOS_LOGIN"))
                    .andExpect(jsonPath("$.data.valor").value("5"));
        }

        @Test
        @DisplayName("secretaria actualiza valor → 200")
        void secretaria_actualiza_returns200() throws Exception {
            String token = obtenerToken("secretaria.test");
            mockMvc.perform(patch("/api/v1/admin/config/HORAS_BLOQUEO_LOGIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateConfigRequest("48")))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valor").value("48"));
        }

        @Test
        @DisplayName("valor en blanco → 422")
        void valorBlanco_returns422() throws Exception {
            String token = obtenerToken("admin.test");
            mockMvc.perform(patch("/api/v1/admin/config/MAX_INTENTOS_LOGIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"valor\":\"\"}")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().is(422));
        }
    }
}
