package com.sadday.app.emergencycontacts;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.emergencycontacts.dto.EmergencyContactRequest;
import com.sadday.app.emergencycontacts.dto.UpsertEmergencyContactsRequest;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql", "/sql/emergency-contacts-test-data.sql"})
@DisplayName("Contactos de Emergencia — Integration Tests")
class EmergencyContactIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

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
    // GET /me/emergency-contacts
    // =========================================================================

    @Test
    @DisplayName("GET /me/emergency-contacts — autenticado → 200 con lista")
    void getMisContactos_authenticated_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/me/emergency-contacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /me/emergency-contacts — sin auth → 401")
    void getMisContactos_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/emergency-contacts"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // PUT /me/emergency-contacts
    // =========================================================================

    @Test
    @DisplayName("PUT /me/emergency-contacts — 1 contacto válido → 200")
    void upsertMisContactos_oneContact_returns200() throws Exception {
        String token = obtenerToken("socio.test");
        UpsertEmergencyContactsRequest req = new UpsertEmergencyContactsRequest(List.of(
                new EmergencyContactRequest((short) 1, "María Pérez", "Madre", "0991234567", "Quito")));

        mockMvc.perform(put("/api/v1/me/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nombreCompleto").value("María Pérez"))
                .andExpect(jsonPath("$.data[0].relacion").value("Madre"));
    }

    @Test
    @DisplayName("PUT /me/emergency-contacts — 2 contactos → reemplaza correctamente")
    void upsertMisContactos_twoContacts_replacesAll() throws Exception {
        String token = obtenerToken("admin.test");
        UpsertEmergencyContactsRequest req = new UpsertEmergencyContactsRequest(List.of(
                new EmergencyContactRequest((short) 1, "Nuevo Uno", "Familiar", "0991111111", null),
                new EmergencyContactRequest((short) 2, "Nuevo Dos", "Amigo", "0992222222", "Guayaquil")));

        mockMvc.perform(put("/api/v1/me/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // Verificar que los anteriores fueron reemplazados
        mockMvc.perform(get("/api/v1/me/emergency-contacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].nombreCompleto").value("Nuevo Uno"))
                .andExpect(jsonPath("$.data[1].nombreCompleto").value("Nuevo Dos"));
    }

    @Test
    @DisplayName("PUT /me/emergency-contacts — lista vacía → 422")
    void upsertMisContactos_emptyList_returns422() throws Exception {
        String token = obtenerToken("socio.test");
        UpsertEmergencyContactsRequest req = new UpsertEmergencyContactsRequest(List.of());

        mockMvc.perform(put("/api/v1/me/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("PUT /me/emergency-contacts — órdenes duplicados → 400")
    void upsertMisContactos_duplicateOrdenes_returns400() throws Exception {
        String token = obtenerToken("socio.test");
        UpsertEmergencyContactsRequest req = new UpsertEmergencyContactsRequest(List.of(
                new EmergencyContactRequest((short) 1, "Uno", "Familiar", null, null),
                new EmergencyContactRequest((short) 1, "Otro", "Amigo", null, null)));

        mockMvc.perform(put("/api/v1/me/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /me/emergency-contacts — sin auth → 401")
    void upsertMisContactos_noAuth_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/me/emergency-contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /admin/socios/{id}/emergency-contacts
    // =========================================================================

    @Test
    @DisplayName("GET /admin/socios/{id}/emergency-contacts — admin → 200")
    void getContactosAdmin_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + ADMIN_ID + "/emergency-contacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /admin/socios/{id}/emergency-contacts — socio → 403")
    void getContactosAdmin_socio_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + ADMIN_ID + "/emergency-contacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/socios/{id}/emergency-contacts — ID inexistente → 404")
    void getContactosAdmin_notFound_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/admin/socios/" + UUID.randomUUID() + "/emergency-contacts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // PUT /admin/socios/{id}/emergency-contacts
    // =========================================================================

    @Test
    @DisplayName("PUT /admin/socios/{id}/emergency-contacts — admin → 200")
    void upsertContactosAdmin_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpsertEmergencyContactsRequest req = new UpsertEmergencyContactsRequest(List.of(
                new EmergencyContactRequest((short) 1, "Admin Contacto", "Hermano", "0993333333", null)));

        mockMvc.perform(put("/api/v1/admin/socios/" + SOCIO_ID + "/emergency-contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nombreCompleto").value("Admin Contacto"));
    }
}
