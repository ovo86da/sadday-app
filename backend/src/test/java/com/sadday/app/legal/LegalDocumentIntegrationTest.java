package com.sadday.app.legal;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.legal.dto.CreateLegalDocumentRequest;
import com.sadday.app.legal.dto.NewVersionRequest;
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
@Sql({"/sql/socios-test-data.sql", "/sql/legal-documents-test-data.sql"})
@DisplayName("Documentos Legales — Integration Tests")
class LegalDocumentIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID      = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID SECRETARIA_ID = UUID.fromString("00000000-0000-4000-b000-000000000004");
    private static final UUID SOCIO_ID      = UUID.fromString("00000000-0000-4000-b000-000000000003");

    private static final String DOC_ACTIVE_ID   = "dddddddd-dddd-4ddd-bddd-dddddddddd01";
    private static final String DOC_INACTIVE_ID = "dddddddd-dddd-4ddd-bddd-dddddddddd02";

    @BeforeEach
    void setUpUsuarios() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(ADMIN_ID).username("admin.test").passwordHash(hash).build());
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(SECRETARIA_ID).username("secretaria.test").passwordHash(hash).build());
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(SOCIO_ID).username("socio.test").passwordHash(hash).build());
    }

    // =========================================================================
    // Endpoints públicos (sin auth)
    // =========================================================================

    @Test
    @DisplayName("GET /legal-documents/active — sin auth → 200 con documentos activos")
    void getActiveDocuments_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/legal-documents/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /legal-documents/active?stage=REGISTRATION — sin auth → 200 filtrado")
    void getActiveDocuments_byStage_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/legal-documents/active").param("stage", "REGISTRATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requiredStage").value("REGISTRATION"));
    }

    @Test
    @DisplayName("GET /legal-documents/TEST_POLICY/active — sin auth → 200 con documento")
    void getActiveDocumentByCode_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/legal-documents/TEST_POLICY/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("TEST_POLICY"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.content").isString());
    }

    @Test
    @DisplayName("GET /legal-documents/INEXISTENTE/active — sin auth → 404")
    void getActiveDocumentByCode_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/legal-documents/INEXISTENTE/active"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Aceptar documento
    // =========================================================================

    @Test
    @DisplayName("POST /legal-documents/{id}/accept — socio autenticado → 201")
    void acceptDocument_authenticated_returns201() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(post("/api/v1/legal-documents/" + DOC_ACTIVE_ID + "/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documentCode").value("TEST_POLICY"))
                .andExpect(jsonPath("$.data.accepted").value(true));
    }

    @Test
    @DisplayName("POST /legal-documents/{id}/accept — sin auth → 401")
    void acceptDocument_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/legal-documents/" + DOC_ACTIVE_ID + "/accept"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /legal-documents/{id}/accept — documento inactivo → 409")
    void acceptDocument_inactive_returns409() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(post("/api/v1/legal-documents/" + DOC_INACTIVE_ID + "/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /legal-documents/{id}/accept — doble aceptación → 409")
    void acceptDocument_duplicate_returns409() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(post("/api/v1/legal-documents/" + DOC_ACTIVE_ID + "/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/legal-documents/" + DOC_ACTIVE_ID + "/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Mis aceptaciones
    // =========================================================================

    @Test
    @DisplayName("GET /me/legal-acceptances — autenticado → 200 con lista")
    void getMyAcceptances_authenticated_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/me/legal-acceptances")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /me/legal-acceptances — sin auth → 401")
    void getMyAcceptances_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/legal-acceptances"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Admin — listar
    // =========================================================================

    @Test
    @DisplayName("GET /admin/legal-documents — admin → 200 con todos los documentos")
    void getAllDocuments_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/legal-documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /admin/legal-documents — socio → 403")
    void getAllDocuments_socio_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/admin/legal-documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Admin — crear documento
    // =========================================================================

    @Test
    @DisplayName("POST /admin/legal-documents — admin → 201 con nuevo documento")
    void createDocument_admin_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        CreateLegalDocumentRequest req = new CreateLegalDocumentRequest(
                "NEW_TEST_DOC", "Nuevo documento de prueba", "descripción",
                "NEW_TEST_DOC", "PROFILE_COMPLETION", "Contenido del nuevo documento", true, true);

        mockMvc.perform(post("/api/v1/admin/legal-documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("NEW_TEST_DOC"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.contentHash").isString());
    }

    @Test
    @DisplayName("POST /admin/legal-documents — código ya existente → 409")
    void createDocument_duplicateCode_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        CreateLegalDocumentRequest req = new CreateLegalDocumentRequest(
                "TEST_POLICY", "Ya existe", null, "TEST_POLICY",
                "REGISTRATION", "Contenido", true, true);

        mockMvc.perform(post("/api/v1/admin/legal-documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Admin — nueva versión
    // =========================================================================

    @Test
    @DisplayName("POST /admin/legal-documents/{id}/new-version — secretaria → 201")
    void createNewVersion_secretaria_returns201() throws Exception {
        String token = obtenerToken("secretaria.test");
        NewVersionRequest req = new NewVersionRequest("Contenido actualizado versión 3");

        mockMvc.perform(post("/api/v1/admin/legal-documents/" + DOC_ACTIVE_ID + "/new-version")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    // =========================================================================
    // Admin — activar versión (solo ADMIN)
    // =========================================================================

    @Test
    @DisplayName("PATCH /admin/legal-documents/{id}/activate — admin → 200 activo")
    void activateVersion_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(patch("/api/v1/admin/legal-documents/" + DOC_INACTIVE_ID + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("PATCH /admin/legal-documents/{id}/activate — secretaria → 403")
    void activateVersion_secretaria_returns403() throws Exception {
        String token = obtenerToken("secretaria.test");

        mockMvc.perform(patch("/api/v1/admin/legal-documents/" + DOC_INACTIVE_ID + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Admin — estado legal de un socio
    // =========================================================================

    @Test
    @DisplayName("GET /admin/socios/{id}/legal-status — admin → 200 con estado")
    void getSocioLegalStatus_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + SOCIO_ID + "/legal-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.socioId").value(SOCIO_ID.toString()))
                .andExpect(jsonPath("$.data.documents").isArray());
    }

    // =========================================================================
    // Admin — socios bloqueados
    // =========================================================================

    @Test
    @DisplayName("GET /admin/socios/blocked-for-activities — admin → 200")
    void getSociosBlocked_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/blocked-for-activities")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================================
    // Admin — pendientes de aceptación
    // =========================================================================

    @Test
    @DisplayName("GET /admin/legal-documents/pending-acceptances — admin → 200")
    void getPendingAcceptances_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/legal-documents/pending-acceptances")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
