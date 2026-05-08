package com.sadday.app.security;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.admin.dto.UpdateConfigRequest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
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

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración para la infraestructura de seguridad:
 * - {@link CorrelationIdFilter}: propaga X-Request-ID en respuestas
 * - {@link com.sadday.app.security.audit.AuditAspect}: registra operaciones @Auditable en auditoria
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql("/sql/admin-test-data.sql")
@DisplayName("Security Infrastructure — Integration Tests")
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-4000-d000-000000000001");

    @BeforeEach
    void setUpUsuarios() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(ADMIN_ID).username("admin.test").passwordHash(hash).build());
    }

    // =========================================================================
    // CorrelationIdFilter
    // =========================================================================

    @Nested
    @DisplayName("CorrelationIdFilter — X-Request-ID")
    class CorrelationIdTests {

        @Test
        @DisplayName("respuesta siempre incluye X-Request-ID generado")
        void anyRequest_responseIncludesRequestId() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(header().exists(CorrelationIdFilter.REQUEST_ID_HEADER));
        }

        @Test
        @DisplayName("X-Request-ID del cliente válido se propaga en la respuesta")
        void validClientRequestId_propagatedInResponse() throws Exception {
            String customId = "my-request-abc-123";
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(CorrelationIdFilter.REQUEST_ID_HEADER, customId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(header().string(CorrelationIdFilter.REQUEST_ID_HEADER, customId));
        }

        @Test
        @DisplayName("X-Request-ID con caracteres inválidos (log injection) → se genera uno nuevo")
        void invalidRequestId_generatesNewId() throws Exception {
            String invalidId = "invalid\nlog-injection";
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(CorrelationIdFilter.REQUEST_ID_HEADER, invalidId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(header().exists(CorrelationIdFilter.REQUEST_ID_HEADER))
                    .andExpect(header().string(CorrelationIdFilter.REQUEST_ID_HEADER, not(invalidId)));
        }

        @Test
        @DisplayName("X-Request-ID demasiado largo → se genera uno nuevo")
        void tooLongRequestId_generatesNewId() throws Exception {
            String longId = "a".repeat(65);
            mockMvc.perform(post("/api/v1/auth/login")
                            .header(CorrelationIdFilter.REQUEST_ID_HEADER, longId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(header().exists(CorrelationIdFilter.REQUEST_ID_HEADER))
                    .andExpect(header().string(CorrelationIdFilter.REQUEST_ID_HEADER, not(longId)));
        }
    }

    // =========================================================================
    // AuditAspect — @Auditable
    // =========================================================================

    @Nested
    @DisplayName("AuditAspect — @Auditable")
    class AuditAspectTests {

        @Test
        @DisplayName("PATCH /admin/config genera entrada en auditoria con resultado SUCCESS")
        void updateConfig_registradoEnAuditoria() throws Exception {
            String token = obtenerToken("admin.test");

            mockMvc.perform(patch("/api/v1/admin/config/MAX_INTENTOS_LOGIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateConfigRequest("5")))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/admin/auditoria")
                            .param("accion", "UPDATE_CONFIG")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].accion").value("UPDATE_CONFIG"))
                    .andExpect(jsonPath("$.data.content[0].entidadAfectada").value("configuracion_sistema"))
                    .andExpect(jsonPath("$.data.content[0].resultado").value("SUCCESS"));
        }

        @Test
        @DisplayName("operación exitosa es auditada por actor correcto")
        void updateConfig_actorCorrecto_registradoEnAuditoria() throws Exception {
            String token = obtenerToken("admin.test");

            mockMvc.perform(patch("/api/v1/admin/config/HORAS_BLOQUEO_LOGIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateConfigRequest("48")))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/admin/auditoria")
                            .param("accion", "UPDATE_CONFIG")
                            .param("actorUsername", "admin.test")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].actorUsername").value("admin.test"));
        }
    }
}
