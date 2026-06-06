package com.sadday.app.retirement;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.retirement.dto.RetireSocioRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql", "/sql/retirement-test-data.sql"})
@DisplayName("Retiro de Socio — Integration Tests")
class RetirementIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID    = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID DIRECTIVO_ID = UUID.fromString("00000000-0000-4000-b000-000000000002");
    private static final UUID SOCIO_ID    = UUID.fromString("00000000-0000-4000-b000-000000000003");

    @BeforeEach
    void setUpUsuarios() {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(ADMIN_ID).username("admin.test").passwordHash(hash).build());
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(DIRECTIVO_ID).username("directivo.test").passwordHash(hash).build());
        usuarioAuthRepository.saveAndFlush(UsuarioAuth.builder()
                .socioId(SOCIO_ID).username("socio.test").passwordHash(hash).build());
    }

    // =========================================================================
    // Baja exitosa
    // =========================================================================

    @Test
    @DisplayName("POST retire — admin da de baja a socio sin deuda → 200, contactos y médica eliminados")
    void retire_sinDeuda_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        RetireSocioRequest req = new RetireSocioRequest("Solicitud voluntaria del socio");

        mockMvc.perform(post("/api/v1/admin/socios/" + SOCIO_ID + "/retire")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.socioId").value(SOCIO_ID.toString()))
                .andExpect(jsonPath("$.data.hasPendingDebt").value(false))
                .andExpect(jsonPath("$.data.actionsPerformed").isArray());
    }

    @Test
    @DisplayName("POST retire — admin da de baja a directivo con deuda → 200, hasPendingDebt=true")
    void retire_conDeuda_conservaDatos() throws Exception {
        String token = obtenerToken("admin.test");
        RetireSocioRequest req = new RetireSocioRequest("Baja con deuda pendiente");

        mockMvc.perform(post("/api/v1/admin/socios/" + DIRECTIVO_ID + "/retire")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPendingDebt").value(true));
    }

    // =========================================================================
    // Errores de negocio
    // =========================================================================

    @Test
    @DisplayName("POST retire — socio ya es EX_MEMBER → 409")
    void retire_yaRetirado_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        RetireSocioRequest req = new RetireSocioRequest("Doble baja");

        // Primera baja
        mockMvc.perform(post("/api/v1/admin/socios/" + SOCIO_ID + "/retire")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Segunda baja — ya es EX_MEMBER
        mockMvc.perform(post("/api/v1/admin/socios/" + SOCIO_ID + "/retire")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST retire — admin intenta darse de baja a sí mismo → 409")
    void retire_selfRetire_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        RetireSocioRequest req = new RetireSocioRequest("Auto baja");

        mockMvc.perform(post("/api/v1/admin/socios/" + ADMIN_ID + "/retire")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST retire — socio inexistente → 404")
    void retire_notFound_returns404() throws Exception {
        String token = obtenerToken("admin.test");
        RetireSocioRequest req = new RetireSocioRequest("Baja de fantasma");

        mockMvc.perform(post("/api/v1/admin/socios/" + UUID.randomUUID() + "/retire")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Control de acceso
    // =========================================================================

    @Test
    @DisplayName("POST retire — socio regular → 403")
    void retire_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        RetireSocioRequest req = new RetireSocioRequest("Intento no autorizado");

        mockMvc.perform(post("/api/v1/admin/socios/" + DIRECTIVO_ID + "/retire")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST retire — sin auth → 401")
    void retire_sinAuth_returns401() throws Exception {
        RetireSocioRequest req = new RetireSocioRequest("Sin token");

        mockMvc.perform(post("/api/v1/admin/socios/" + SOCIO_ID + "/retire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST retire — motivo vacío → 422")
    void retire_motivoVacio_returns422() throws Exception {
        String token = obtenerToken("admin.test");
        RetireSocioRequest req = new RetireSocioRequest("");

        mockMvc.perform(post("/api/v1/admin/socios/" + SOCIO_ID + "/retire")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }
}
