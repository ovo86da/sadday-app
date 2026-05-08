package com.sadday.app.socios;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.socios.dto.CreateSocioRequest;
import com.sadday.app.socios.dto.UpdateRolRequest;
import com.sadday.app.socios.dto.UpdateSocioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del módulo Socios.
 *
 * <p>Usa Testcontainers con PostgreSQL real. Flyway ejecuta todas las migraciones.
 * {@link JavaMailSender} se mockea para no necesitar SMTP.
 *
 * <p>Cada test se revierte automáticamente gracias a {@link Transactional}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql("/sql/socios-test-data.sql")
@DisplayName("Socios — Integration Tests")
class SocioIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID     = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID DIRECTIVO_ID = UUID.fromString("00000000-0000-4000-b000-000000000002");
    private static final UUID SOCIO_ID     = UUID.fromString("00000000-0000-4000-b000-000000000003");
    private static final UUID TARGET_ID    = UUID.fromString("00000000-0000-4000-b000-000000000010");

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
    // Lookups
    // =========================================================================

    @Test
    @DisplayName("GET /socios/lookups — autenticado → 200 con datos de catálogo")
    void lookups_authenticated_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/socios/lookups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tiposSocio").isArray())
                .andExpect(jsonPath("$.data.estadosHabilitacion").isArray())
                .andExpect(jsonPath("$.data.rolesSistema").isArray())
                .andExpect(jsonPath("$.data.clasificaciones").isArray());
    }

    // =========================================================================
    // Mi perfil
    // =========================================================================

    @Test
    @DisplayName("GET /socios/me — autenticado → 200 con datos propios")
    void miPerfil_authenticated_returnsOwnProfile() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/socios/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.data.nombre").value("Admin"));
    }

    @Test
    @DisplayName("GET /socios/me — sin autenticación → 401")
    void miPerfil_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/socios/me"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Listar socios
    // =========================================================================

    @Test
    @DisplayName("GET /socios — Admin → 200 paginado")
    void listar_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/socios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                // Con pageSerializationMode = VIA_DTO, la paginación queda en $.data.page.*
                .andExpect(jsonPath("$.data.page.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /socios — Socio regular → 403")
    void listar_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/socios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /socios?q=Target — filtra por query")
    void listar_conFiltroQ_filtraCorrectamente() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/socios?q=Target")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].nombre").value("Target"));
    }

    // =========================================================================
    // Obtener por ID
    // =========================================================================

    @Test
    @DisplayName("GET /socios/{id} — Admin → 200")
    void obtener_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/socios/" + TARGET_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.data.nombre").value("Target"));
    }

    @Test
    @DisplayName("GET /socios/{id} — ID inexistente → 404")
    void obtener_noExiste_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/socios/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Crear socio
    // =========================================================================

    @Test
    @DisplayName("POST /socios — Admin → 201 con invitación enviada")
    void crear_admin_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        CreateSocioRequest request = nuevaSolicitud("7777777771", "juan.perez@test.local");

        mockMvc.perform(post("/api/v1/socios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /socios — Socio regular → 403")
    void crear_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        CreateSocioRequest request = nuevaSolicitud("7777777772", "maria@test.local");

        mockMvc.perform(post("/api/v1/socios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /socios — cédula duplicada → 409")
    void crear_cedulaDuplicada_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        // Cédula "9000000001" ya existe en target
        CreateSocioRequest request = nuevaSolicitud("9000000001", "otro@test.local");

        mockMvc.perform(post("/api/v1/socios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /socios — correo duplicado → 409")
    void crear_correoDuplicado_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        // Correo "target@sadday.local" ya existe
        CreateSocioRequest request = nuevaSolicitud("7777777779", "target@sadday.local");

        mockMvc.perform(post("/api/v1/socios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /socios — body inválido → 422")
    void crear_bodyInvalido_returns422() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(post("/api/v1/socios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(422));
    }

    // =========================================================================
    // Actualizar socio
    // =========================================================================

    @Test
    @DisplayName("PUT /socios/{id} — Admin → 200")
    void actualizar_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateSocioRequest request = updateSolicitud("TargetActualizado", "Socio");

        mockMvc.perform(put("/api/v1/socios/" + TARGET_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nombre").value("TargetActualizado"));
    }

    // =========================================================================
    // Habilitar / Inhabilitar
    // =========================================================================

    @Test
    @DisplayName("PATCH /socios/{id}/habilitar — Admin → 200 (target estaba inhabilitado)")
    void habilitar_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(patch("/api/v1/socios/" + TARGET_ID + "/habilitar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /socios/{id}/inhabilitar — Directivo → 200")
    void inhabilitar_directivo_returns200() throws Exception {
        String token = obtenerToken("directivo.test");

        mockMvc.perform(patch("/api/v1/socios/" + TARGET_ID + "/inhabilitar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /socios/{id}/habilitar — Socio regular → 403")
    void habilitar_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(patch("/api/v1/socios/" + TARGET_ID + "/habilitar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Cambiar rol
    // =========================================================================

    @Test
    @DisplayName("PATCH /socios/{id}/rol — Admin → 200")
    void cambiarRol_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        // Obtener ID del rol "Directivo" desde los lookups
        MvcResult lookupsResult = mockMvc.perform(get("/api/v1/socios/lookups")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper
                .readValue(lookupsResult.getResponse().getContentAsString(), Map.class)
                .get("data");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> roles = (java.util.List<Map<String, Object>>) data.get("rolesSistema");
        Short directivoId = ((Number) roles.stream()
                .filter(r -> "Directivo".equals(r.get("nombre")))
                .findFirst().orElseThrow()
                .get("id")).shortValue();

        UpdateRolRequest request = new UpdateRolRequest(directivoId);

        mockMvc.perform(patch("/api/v1/socios/" + TARGET_ID + "/rol")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Eliminar
    // =========================================================================

    @Test
    @DisplayName("DELETE /socios/{id} — Admin → 204")
    void eliminar_admin_returns204() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(delete("/api/v1/socios/" + TARGET_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /socios/{id} — Socio regular → 403")
    void eliminar_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(delete("/api/v1/socios/" + TARGET_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private CreateSocioRequest nuevaSolicitud(String cedula, String correo) {
        return new CreateSocioRequest(cedula, correo, null);
    }

    private UpdateSocioRequest updateSolicitud(String nombre, String apellido) {
        return new UpdateSocioRequest(
                nombre, apellido,
                "9000000001", "target@sadday.local",
                null, null,
                LocalDate.of(1992, 3, 15),
                LocalDate.of(2023, 1, 1),
                null, null,
                null, null, null,
                null, null, null,
                (short) 1, // Socio Activo
                null
        );
    }
}
