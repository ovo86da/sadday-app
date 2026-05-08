package com.sadday.app.actas;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.actas.dto.*;
import com.sadday.app.actas.entity.TipoActa;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del módulo Actas de Reunión.
 *
 * <p>Carga socios, salidas, informes y actas de prueba.
 * Cada test se revierte por {@link Transactional}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql",
      "/sql/salidas-test-data.sql",
      "/sql/informes-test-data.sql",
      "/sql/actas-test-data.sql"})
@DisplayName("Actas — Integration Tests")
class ActaIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID     = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID DIRECTIVO_ID = UUID.fromString("00000000-0000-4000-b000-000000000002");
    private static final UUID SOCIO_ID     = UUID.fromString("00000000-0000-4000-b000-000000000003");

    private static final UUID ACTA_ID   = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
    private static final UUID INFORME_ID = UUID.fromString("eeeeeeee-eeee-4eee-beee-eeeeeeeeeeee");

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
    // GET /actas — listar
    // =========================================================================

    @Test
    @DisplayName("GET /actas — autenticado → 200 paginado")
    void listar_autenticado_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/actas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /actas?q=chimborazo — Full Text Search → 200 con resultados")
    void listar_conFts_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/actas?q=chimborazo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /actas?q=termino_inexistente — FTS sin resultados → 200 vacío")
    void listar_ftsSinResultados_returns200Vacio() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/actas?q=xyzterminoinexistente")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /actas — sin token → 401")
    void listar_sinToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/actas"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /actas/{id} — detalle
    // =========================================================================

    @Test
    @DisplayName("GET /actas/{id} — acta existente → 200 con asistentes e informes")
    void obtener_actaExistente_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/actas/" + ACTA_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ACTA_ID.toString()))
                .andExpect(jsonPath("$.data.lugar").value("Sede del club Sadday"))
                .andExpect(jsonPath("$.data.asistentes").isArray())
                .andExpect(jsonPath("$.data.asistentes.length()").value(2))
                .andExpect(jsonPath("$.data.informes").isArray())
                .andExpect(jsonPath("$.data.informes.length()").value(1));
    }

    @Test
    @DisplayName("GET /actas/{id} — id inexistente → 404")
    void obtener_inexistente_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/actas/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // POST /actas — crear
    // =========================================================================

    @Test
    @DisplayName("POST /actas — Admin crea acta → 201")
    void crear_admin_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        CreateActaRequest request = nuevaActaRequest("Sala de reuniones");

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lugar").value("Sala de reuniones"))
                .andExpect(jsonPath("$.data.asistentes.length()").value(1))
                .andExpect(jsonPath("$.data.informes.length()").value(1));
    }

    @Test
    @DisplayName("POST /actas — Directivo (sin permiso) → 403")
    void crear_directivo_returns403() throws Exception {
        String token = obtenerToken("directivo.test");
        CreateActaRequest request = nuevaActaRequest("Sala test");

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /actas — Socio regular → 403")
    void crear_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        CreateActaRequest request = nuevaActaRequest("Sala test");

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /actas — faltan campos obligatorios → 422")
    void crear_sinCamposObligatorios_returns422() throws Exception {
        String token = obtenerToken("admin.test");
        String body = "{\"lugar\":\"algún lugar\"}";

        mockMvc.perform(post("/api/v1/actas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422));
    }

    // =========================================================================
    // PUT /actas/{id} — actualizar
    // =========================================================================

    @Test
    @DisplayName("PUT /actas/{id} — Admin actualiza acta → 200")
    void actualizar_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateActaRequest request = new UpdateActaRequest(
                TipoActa.SOCIOS, null,
                LocalDate.of(2026, 2, 1), LocalTime.of(19, 0), null,
                "Sede actualizada",
                "Nueva actividad realizada",
                null, null, null, null, null, null
        );

        mockMvc.perform(put("/api/v1/actas/" + ACTA_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lugar").value("Sede actualizada"))
                .andExpect(jsonPath("$.data.actividadesRealizadasDesc").value("Nueva actividad realizada"));
    }

    @Test
    @DisplayName("PUT /actas/{id} — Directivo → 403")
    void actualizar_directivo_returns403() throws Exception {
        String token = obtenerToken("directivo.test");
        UpdateActaRequest request = new UpdateActaRequest(
                TipoActa.SOCIOS, null,
                LocalDate.of(2026, 2, 1), LocalTime.of(19, 0), null,
                "nueva sede", null, null, null, null, null, null, null);

        mockMvc.perform(put("/api/v1/actas/" + ACTA_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // DELETE /actas/{id} — eliminar
    // =========================================================================

    @Test
    @DisplayName("DELETE /actas/{id} — Admin → 204")
    void eliminar_admin_returns204() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(delete("/api/v1/actas/" + ACTA_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verificar que ya no existe
        mockMvc.perform(get("/api/v1/actas/" + ACTA_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /actas/{id} — Socio regular → 403")
    void eliminar_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(delete("/api/v1/actas/" + ACTA_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // POST /actas/{id}/asistentes — agregar asistente
    // =========================================================================

    @Test
    @DisplayName("POST /actas/{id}/asistentes — Admin agrega asistente → 201")
    void agregarAsistente_admin_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        AgregarAsistenteRequest request = new AgregarAsistenteRequest(SOCIO_ID);

        mockMvc.perform(post("/api/v1/actas/" + ACTA_ID + "/asistentes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.socioId").value(SOCIO_ID.toString()));
    }

    @Test
    @DisplayName("POST /actas/{id}/asistentes — asistente duplicado → 409")
    void agregarAsistente_duplicado_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        // Admin ya está como asistente en el acta de prueba
        AgregarAsistenteRequest request = new AgregarAsistenteRequest(ADMIN_ID);

        mockMvc.perform(post("/api/v1/actas/" + ACTA_ID + "/asistentes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // DELETE /actas/{id}/asistentes/{aid}
    // =========================================================================

    @Test
    @DisplayName("DELETE /actas/{id}/asistentes/{aid} — Admin elimina asistente → 204")
    void eliminarAsistente_admin_returns204() throws Exception {
        String token = obtenerToken("admin.test");

        // Agregar primero a SOCIO_ID
        AgregarAsistenteRequest addReq = new AgregarAsistenteRequest(SOCIO_ID);
        MvcResult addResult = mockMvc.perform(
                        post("/api/v1/actas/" + ACTA_ID + "/asistentes")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = addResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        Long asistenteId = ((Number) data.get("id")).longValue();

        // Eliminar
        mockMvc.perform(delete("/api/v1/actas/" + ACTA_ID + "/asistentes/" + asistenteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // POST /actas/{id}/informes — vincular informe
    // =========================================================================

    @Test
    @DisplayName("POST /actas/{id}/informes — Admin vincula informe → 201")
    void agregarInforme_admin_returns201() throws Exception {
        String token = obtenerToken("admin.test");

        // Crear una nueva acta sin informes para luego vincular
        CreateActaRequest nuevaActa = new CreateActaRequest(
                TipoActa.DIRECTIVA, null,
                LocalDate.of(2026, 3, 20), LocalTime.of(20, 0), null,
                "Sala nueva",
                null, null, null, null, null,
                null, null, null, null);
        MvcResult crearResult = mockMvc.perform(
                        post("/api/v1/actas")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(nuevaActa)))
                .andExpect(status().isCreated())
                .andReturn();

        String crearBody = crearResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> actaData = (Map<String, Object>) objectMapper.readValue(crearBody, Map.class).get("data");
        String nuevaActaId = (String) actaData.get("id");

        // Vincular informe a la nueva acta
        AgregarInformeActaRequest request = new AgregarInformeActaRequest(INFORME_ID);
        mockMvc.perform(post("/api/v1/actas/" + nuevaActaId + "/informes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.informeId").value(INFORME_ID.toString()));
    }

    @Test
    @DisplayName("POST /actas/{id}/informes — informe duplicado → 409")
    void agregarInforme_duplicado_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        // El informe ya está vinculado al ACTA_ID en el SQL de prueba
        AgregarInformeActaRequest request = new AgregarInformeActaRequest(INFORME_ID);

        mockMvc.perform(post("/api/v1/actas/" + ACTA_ID + "/informes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // DELETE /actas/{id}/informes/{lid}
    // =========================================================================

    @Test
    @DisplayName("DELETE /actas/{id}/informes/{lid} — Admin desvincula informe → 204")
    void eliminarInforme_admin_returns204() throws Exception {
        String token = obtenerToken("admin.test");

        // Obtener linkId del acta
        MvcResult getResult = mockMvc.perform(
                        get("/api/v1/actas/" + ACTA_ID)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = getResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> informes = (java.util.List<Map<String, Object>>) data.get("informes");
        Long linkId = ((Number) informes.get(0).get("id")).longValue();

        mockMvc.perform(delete("/api/v1/actas/" + ACTA_ID + "/informes/" + linkId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CreateActaRequest nuevaActaRequest(String lugar) {
        return new CreateActaRequest(
                TipoActa.DIRECTIVA,
                null,                                     // numeroReunion
                LocalDate.of(2026, 3, 15),
                LocalTime.of(19, 0),
                null,                                     // horaFin
                lugar,
                "Actividades realizadas de prueba",
                "Actividades por realizar de prueba",
                null,                                     // acuerdos
                "Varios de prueba",
                "Observaciones de prueba",
                null,                                     // presidenteReunionId
                null,                                     // secretariaReunionId
                List.of(ADMIN_ID),
                List.of(INFORME_ID)
        );
    }
}
