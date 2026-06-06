package com.sadday.app.medicalinfo;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.medicalinfo.dto.UpdateMedicalInfoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql", "/sql/medical-info-test-data.sql"})
@DisplayName("Información Médica — Integration Tests")
class SocioMedicalInfoIntegrationTest extends AbstractIntegrationTest {

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
    // GET /me/medical-info
    // =========================================================================

    @Test
    @DisplayName("GET /me/medical-info — admin con registro → 200 con datos")
    void getMiInfo_withRecord_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/me/medical-info")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bloodType").value("O+"))
                .andExpect(jsonPath("$.data.hasRelevantAllergies").value(true))
                .andExpect(jsonPath("$.data.allergiesDetail").value("Alergia a la penicilina"));
    }

    @Test
    @DisplayName("GET /me/medical-info — socio sin registro → 200 vacío")
    void getMiInfo_noRecord_returnsEmpty() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/me/medical-info")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bloodType").doesNotExist());
    }

    @Test
    @DisplayName("GET /me/medical-info — sin auth → 401")
    void getMiInfo_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/medical-info"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // PUT /me/medical-info
    // =========================================================================

    @Test
    @DisplayName("PUT /me/medical-info — admin con consentimiento → 200")
    void updateMiInfo_withConsent_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateMedicalInfoRequest req = new UpdateMedicalInfoRequest(
                "AB+", true, "Polen", false, null, false, null, null);

        mockMvc.perform(put("/api/v1/me/medical-info")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bloodType").value("AB+"))
                .andExpect(jsonPath("$.data.hasRelevantAllergies").value(true));
    }

    @Test
    @DisplayName("PUT /me/medical-info — socio sin consentimiento → 403")
    void updateMiInfo_noConsent_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        UpdateMedicalInfoRequest req = new UpdateMedicalInfoRequest(
                "O-", null, null, null, null, null, null, null);

        mockMvc.perform(put("/api/v1/me/medical-info")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /me/medical-info — tipo de sangre inválido → 422")
    void updateMiInfo_invalidBloodType_returns422() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateMedicalInfoRequest req = new UpdateMedicalInfoRequest(
                "INVALIDO", null, null, null, null, null, null, null);

        mockMvc.perform(put("/api/v1/me/medical-info")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    // =========================================================================
    // GET /admin/socios/{id}/medical-info
    // =========================================================================

    @Test
    @DisplayName("GET /admin/socios/{id}/medical-info — admin → 200")
    void getAdminMedicalInfo_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + ADMIN_ID + "/medical-info")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bloodType").value("O+"));
    }

    @Test
    @DisplayName("GET /admin/socios/{id}/medical-info — socio → 403")
    void getAdminMedicalInfo_socio_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + ADMIN_ID + "/medical-info")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/socios/{id}/medical-info — ID inexistente → 404")
    void getAdminMedicalInfo_notFound_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + UUID.randomUUID() + "/medical-info")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // PUT /admin/socios/{id}/medical-info
    // =========================================================================

    @Test
    @DisplayName("PUT /admin/socios/{id}/medical-info — admin → 200, sin requerir consentimiento")
    void updateAdminMedicalInfo_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateMedicalInfoRequest req = new UpdateMedicalInfoRequest(
                "B+", false, null, true, "Diabetes tipo 2", false, null, "Notas de prueba");

        mockMvc.perform(put("/api/v1/admin/socios/" + SOCIO_ID + "/medical-info")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bloodType").value("B+"))
                .andExpect(jsonPath("$.data.hasRelevantMedicalCondition").value(true))
                .andExpect(jsonPath("$.data.additionalNotes").value("Notas de prueba"));
    }

    // =========================================================================
    // GET /admin/socios/{id}/medical-summary
    // =========================================================================

    @Test
    @DisplayName("GET /admin/socios/{id}/medical-summary — admin → 200 con campos reducidos")
    void getMedicalSummary_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + ADMIN_ID + "/medical-summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.socioId").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.data.bloodType").value("O+"))
                .andExpect(jsonPath("$.data.hasRelevantAllergies").value(true));
    }

    @Test
    @DisplayName("GET /admin/socios/{id}/medical-summary — socio → 403")
    void getMedicalSummary_socio_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + ADMIN_ID + "/medical-summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
