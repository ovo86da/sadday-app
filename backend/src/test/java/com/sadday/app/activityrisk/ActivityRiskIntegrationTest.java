package com.sadday.app.activityrisk;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.activityrisk.dto.CreateActivityRiskDocumentRequest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
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
@Sql({"/sql/socios-test-data.sql", "/sql/salidas-test-data.sql", "/sql/activity-risk-test-data.sql"})
@DisplayName("Riesgos por Actividad — Integration Tests")
class ActivityRiskIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID SOCIO_ID = UUID.fromString("00000000-0000-4000-b000-000000000003");

    private static final String SALIDA_ID    = "aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa";
    private static final String RISK_DOC_ID  = "cccccccc-cccc-4ccc-bccc-cccccccccccc";

    @BeforeEach
    void setUpUsuarios() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(ADMIN_ID).username("admin.test").passwordHash(hash).build());
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(SOCIO_ID).username("socio.test").passwordHash(hash).build());
    }

    // =========================================================================
    // GET documento de riesgos activo
    // =========================================================================

    @Test
    @DisplayName("GET /salidas/{id}/risk-document — autenticado, doc activo → 200")
    void getActivo_autenticado_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/salidas/" + SALIDA_ID + "/risk-document")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(RISK_DOC_ID))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("GET /salidas/{id}/risk-document — sin auth → 401")
    void getActivo_sinAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/salidas/" + SALIDA_ID + "/risk-document"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /salidas/{id}/risk-document — salida sin documento → 404")
    void getActivo_sinDocumento_returns404() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/salidas/bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb/risk-document")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // POST aceptar documento de riesgos
    // =========================================================================

    @Test
    @DisplayName("POST /salidas/{id}/risk-document/accept — socio acepta → 201")
    void aceptar_socioRegular_returns201() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_ID + "/risk-document/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.socioId").value(SOCIO_ID.toString()))
                .andExpect(jsonPath("$.data.activityId").value(SALIDA_ID))
                .andExpect(jsonPath("$.data.documentVersion").value(1));
    }

    @Test
    @DisplayName("POST /salidas/{id}/risk-document/accept — admin acepta de nuevo → 409 (ya aceptó)")
    void aceptar_yaAceptado_returns409() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_ID + "/risk-document/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /salidas/{id}/risk-document/accept — sin auth → 401")
    void aceptar_sinAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_ID + "/risk-document/accept"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // POST crear nuevo documento de riesgos (admin)
    // =========================================================================

    @Test
    @DisplayName("POST /admin/salidas/{id}/risk-document — admin crea documento → 201")
    void crear_admin_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        CreateActivityRiskDocumentRequest req = new CreateActivityRiskDocumentRequest(
                "Descripción detallada de los riesgos de la actividad.");

        mockMvc.perform(post("/api/v1/admin/salidas/" + SALIDA_ID + "/risk-document")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.contentHash").isNotEmpty());
    }

    @Test
    @DisplayName("POST /admin/salidas/{id}/risk-document — socio regular → 403")
    void crear_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        CreateActivityRiskDocumentRequest req = new CreateActivityRiskDocumentRequest("Contenido");

        mockMvc.perform(post("/api/v1/admin/salidas/" + SALIDA_ID + "/risk-document")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/salidas/{id}/risk-document — contenido vacío → 422")
    void crear_contenidoVacio_returns422() throws Exception {
        String token = obtenerToken("admin.test");
        CreateActivityRiskDocumentRequest req = new CreateActivityRiskDocumentRequest("");

        mockMvc.perform(post("/api/v1/admin/salidas/" + SALIDA_ID + "/risk-document")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    // =========================================================================
    // Inscripción bloqueada sin aceptación del documento de riesgos
    // =========================================================================

    @Test
    @DisplayName("POST inscribir — socio sin aceptar doc de riesgos → 403")
    void inscribir_sinAceptarRiesgo_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        String body = objectMapper.writeValueAsString(
                new com.sadday.app.salidas.dto.InscribirRequest(SOCIO_ID));

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_ID + "/inscripciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
