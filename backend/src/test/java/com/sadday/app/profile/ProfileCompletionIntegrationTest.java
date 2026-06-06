package com.sadday.app.profile;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql", "/sql/profile-completion-test-data.sql"})
@DisplayName("ProfileCompletion — Integration Tests")
class ProfileCompletionIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID SOCIO_ID = UUID.fromString("00000000-0000-4000-b000-000000000003");

    @BeforeEach
    void setUpUsuarios() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(ADMIN_ID).username("admin.test").passwordHash(hash).build());
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(SOCIO_ID).username("socio.test").passwordHash(hash).build());
    }

    // =========================================================================
    // GET /me/profile-completion-status
    // =========================================================================

    @Test
    @DisplayName("GET /me/profile-completion-status — admin con perfil completo → canEnrollActivities=true")
    void getMiStatus_completeProfile_returnsTrue() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/me/profile-completion-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEnrollActivities").value(true))
                .andExpect(jsonPath("$.data.profileComplete").value(true))
                .andExpect(jsonPath("$.data.missingRequirements").isEmpty());
    }

    @Test
    @DisplayName("GET /me/profile-completion-status — socio con perfil incompleto → canEnrollActivities=false")
    void getMiStatus_incompleteProfile_returnsFalse() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/me/profile-completion-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEnrollActivities").value(false))
                .andExpect(jsonPath("$.data.missingRequirements").isArray())
                .andExpect(jsonPath("$.data.missingRequirements.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("GET /me/profile-completion-status — sin auth → 401")
    void getMiStatus_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile-completion-status"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /admin/socios/{id}/profile-completion-status
    // =========================================================================

    @Test
    @DisplayName("GET /admin/socios/{id}/profile-completion-status — admin → 200")
    void getAdminStatus_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + SOCIO_ID + "/profile-completion-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEnrollActivities").value(false));
    }

    @Test
    @DisplayName("GET /admin/socios/{id}/profile-completion-status — socio → 403")
    void getAdminStatus_socio_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + SOCIO_ID + "/profile-completion-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/socios/{id}/profile-completion-status — ID inexistente → 404")
    void getAdminStatus_notFound_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + UUID.randomUUID() + "/profile-completion-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
