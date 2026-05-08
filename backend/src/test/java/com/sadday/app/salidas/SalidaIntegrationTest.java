package com.sadday.app.salidas;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.salidas.dto.*;
import com.sadday.app.salidas.entity.EstadoInscripcion;
import com.sadday.app.salidas.entity.EstadoSalida;
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
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del módulo Salidas.
 *
 * <p>Carga socios-test-data.sql y salidas-test-data.sql.
 * Cada test se revierte por {@link Transactional}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql", "/sql/salidas-test-data.sql"})
@DisplayName("Salidas — Integration Tests")
class SalidaIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID     = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID DIRECTIVO_ID = UUID.fromString("00000000-0000-4000-b000-000000000002");
    private static final UUID SOCIO_ID     = UUID.fromString("00000000-0000-4000-b000-000000000003");

    private static final UUID SALIDA_PLANIFICADA = UUID.fromString("aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa");
    private static final UUID SALIDA_LLENA       = UUID.fromString("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb");

    private static final Integer RUTA_ID = 5000;

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
    @DisplayName("GET /salidas/lookups — autenticado → 200 con catálogos")
    void lookups_authenticated_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/salidas/lookups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicosObjetivo").isArray())
                .andExpect(jsonPath("$.data.formatosSalida").isArray())
                .andExpect(jsonPath("$.data.dignidades").isArray())
                .andExpect(jsonPath("$.data.estadosSalida").isArray())
                .andExpect(jsonPath("$.data.estadosInscripcion").isArray());
    }

    // =========================================================================
    // Listar salidas
    // =========================================================================

    @Test
    @DisplayName("GET /salidas — autenticado → 200 paginado")
    void listar_salidas_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/salidas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /salidas?estado=PLANIFICADA — filtra por estado")
    void listar_filtroEstado() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/salidas?estado=PLANIFICADA")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].estado").value("PLANIFICADA"));
    }

    @Test
    @DisplayName("GET /salidas?q=Planificada — filtra por nombre")
    void listar_filtroQ() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/salidas?q=Planificada")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].nombre").value("Salida Test Planificada"));
    }

    // =========================================================================
    // Crear salida
    // =========================================================================

    @Test
    @DisplayName("POST /salidas — Directivo → 201")
    void crear_salida_directivo_returns201() throws Exception {
        String token = obtenerToken("directivo.test");
        CreateSalidaRequest request = nuevaSalidaRequest("Nueva Salida Test");

        mockMvc.perform(post("/api/v1/salidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nombre").value("Nueva Salida Test"))
                .andExpect(jsonPath("$.data.estado").value("PLANIFICADA"));
    }

    @Test
    @DisplayName("POST /salidas — Socio regular → 403")
    void crear_salida_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        CreateSalidaRequest request = nuevaSalidaRequest("No Permitido");

        mockMvc.perform(post("/api/v1/salidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /salidas — body inválido → 422")
    void crear_salida_bodyInvalido_returns422() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(post("/api/v1/salidas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(422));
    }

    // =========================================================================
    // Obtener / actualizar / cambiar estado / eliminar
    // =========================================================================

    @Test
    @DisplayName("GET /salidas/{id} — Admin → 200 con participantes")
    void obtener_salida_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/salidas/" + SALIDA_PLANIFICADA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SALIDA_PLANIFICADA.toString()))
                .andExpect(jsonPath("$.data.nombre").value("Salida Test Planificada"))
                .andExpect(jsonPath("$.data.participantes").isArray());
    }

    @Test
    @DisplayName("GET /salidas/{id} — Socio regular → 200 (cualquier autenticado puede ver detalles)")
    void obtener_salida_socioRegular_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/salidas/" + SALIDA_PLANIFICADA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nombre").value("Salida Test Planificada"));
    }

    @Test
    @DisplayName("GET /salidas/{id} — ID inexistente → 404")
    void obtener_salida_noExiste_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/salidas/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /salidas/{id} — Admin → 200")
    void actualizar_salida_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateSalidaRequest request = new UpdateSalidaRequest(
                "Salida Actualizada", LocalDate.of(2026, 6, 5), LocalTime.of(5, 30),
                LocalDate.of(2026, 6, 7), null, RUTA_ID, "ALPINISMO", "PO001", "FS001", "SO002", (short) 10);

        mockMvc.perform(put("/api/v1/salidas/" + SALIDA_PLANIFICADA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nombre").value("Salida Actualizada"));
    }

    @Test
    @DisplayName("PATCH /salidas/{id}/estado — Directivo → 200")
    void cambiarEstado_directivo_returns200() throws Exception {
        String token = obtenerToken("directivo.test");
        CambiarEstadoSalidaRequest request = new CambiarEstadoSalidaRequest(EstadoSalida.CANCELADA);

        mockMvc.perform(patch("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /salidas/{id}/estado — Socio regular → 403")
    void cambiarEstado_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        CambiarEstadoSalidaRequest request = new CambiarEstadoSalidaRequest(EstadoSalida.CANCELADA);

        mockMvc.perform(patch("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /salidas/{id} — Admin → 204 (salida PLANIFICADA, requiere motivo)")
    void eliminar_salida_admin_returns204() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(delete("/api/v1/salidas/" + SALIDA_PLANIFICADA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Eliminación de prueba\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /salidas/{id} — Socio regular → 403")
    void eliminar_salida_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(delete("/api/v1/salidas/" + SALIDA_PLANIFICADA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Eliminación de prueba\"}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Inscripciones
    // =========================================================================

    @Test
    @DisplayName("POST /inscripciones — Socio inscribe a sí mismo → 201")
    void inscribir_socioRegular_returns201() throws Exception {
        String token = obtenerToken("socio.test");
        InscribirRequest request = new InscribirRequest(SOCIO_ID);

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.socioId").value(SOCIO_ID.toString()))
                .andExpect(jsonPath("$.data.estadoInscripcion").value("INSCRITO"));
    }

    @Test
    @DisplayName("POST /inscripciones — Socio intenta inscribir a otro → 403")
    void inscribir_otroSocio_sinPermisos_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        // Socio regular intenta inscribir a Admin (quien ya está inscrito, pero el 403 llega antes del 409)
        InscribirRequest request = new InscribirRequest(DIRECTIVO_ID);

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /inscripciones — ya inscrito → 409")
    void inscribir_yaInscrito_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        // Admin ya está inscrito en SALIDA_PLANIFICADA (via SQL test data)
        InscribirRequest request = new InscribirRequest(ADMIN_ID);

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /inscripciones — salida llena → 409")
    void inscribir_salidaLlena_returns409() throws Exception {
        String token = obtenerToken("socio.test");
        // SALIDA_LLENA tiene capacidad 1 y Admin ya está inscrito
        InscribirRequest request = new InscribirRequest(SOCIO_ID);

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_LLENA + "/inscripciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /inscripciones/{pid} — Socio cancela su propia inscripción → 204")
    void cancelarInscripcion_propia_returns204() throws Exception {
        // Paso 1: Socio se inscribe
        String token = obtenerToken("socio.test");
        InscribirRequest inscribirReq = new InscribirRequest(SOCIO_ID);

        MvcResult inscribirResult = mockMvc.perform(
                        post("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(inscribirReq)))
                .andExpect(status().isCreated())
                .andReturn();

        // Paso 2: Obtener el participanteId del response
        String body = inscribirResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        Long participanteId = ((Number) data.get("id")).longValue();

        // Paso 3: Cancelar
        mockMvc.perform(delete("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones/" + participanteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /inscripciones/{pid} — Socio cancela inscripción ajena → 403")
    void cancelarInscripcion_ajena_returns403() throws Exception {
        // Admin está inscrito en SALIDA_PLANIFICADA. Socio intenta borrar su inscripción.
        String adminToken = obtenerToken("admin.test");
        String socioToken = obtenerToken("socio.test");

        // Obtener el participanteId del Admin desde el detalle
        MvcResult detalleResult = mockMvc.perform(
                        get("/api/v1/salidas/" + SALIDA_PLANIFICADA)
                                .header("Authorization", "Bearer " + adminToken))
                .andReturn();

        String body = detalleResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> participantes = (java.util.List<Map<String, Object>>) data.get("participantes");
        Long adminParticipanteId = ((Number) participantes.get(0).get("id")).longValue();

        // Socio intenta cancelar la inscripción del Admin
        mockMvc.perform(delete("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones/" + adminParticipanteId)
                        .header("Authorization", "Bearer " + socioToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Estado inscripción / Jefe de salida / Dignidades
    // =========================================================================

    @Test
    @DisplayName("PATCH /inscripciones/{pid}/estado — Admin → 200")
    void cambiarEstadoInscripcion_admin_returns200() throws Exception {
        String adminToken = obtenerToken("admin.test");
        Long participanteId = obtenerAdminParticipanteId(adminToken);

        CambiarEstadoInscripcionRequest request = new CambiarEstadoInscripcionRequest(EstadoInscripcion.CONFIRMADO);

        mockMvc.perform(patch("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones/" + participanteId + "/estado")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estadoInscripcion").value("CONFIRMADO"));
    }

    @Test
    @DisplayName("PATCH /inscripciones/{pid}/jefe — Directivo → 200")
    void designarJefeSalida_directivo_returns200() throws Exception {
        String adminToken = obtenerToken("admin.test");
        String directivoToken = obtenerToken("directivo.test");
        Long participanteId = obtenerAdminParticipanteId(adminToken);

        mockMvc.perform(patch("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones/" + participanteId + "/jefe")
                        .header("Authorization", "Bearer " + directivoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.esJefeSalida").value(true));
    }

    @Test
    @DisplayName("POST /inscripciones/{pid}/dignidades — Directivo → 201")
    void agregarDignidad_directivo_returns201() throws Exception {
        String adminToken = obtenerToken("admin.test");
        String directivoToken = obtenerToken("directivo.test");
        Long participanteId = obtenerAdminParticipanteId(adminToken);

        // Dignidad 1 = "Jefe de Salida" (seeded en V8)
        AgregarDignidadRequest request = new AgregarDignidadRequest(1);

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones/" + participanteId + "/dignidades")
                        .header("Authorization", "Bearer " + directivoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dignidades[0].dignidadNombre").value("Jefe de Salida"));
    }

    @Test
    @DisplayName("POST /inscripciones/{pid}/dignidades — Socio regular → 403")
    void agregarDignidad_socioRegular_returns403() throws Exception {
        String adminToken = obtenerToken("admin.test");
        String socioToken = obtenerToken("socio.test");
        Long participanteId = obtenerAdminParticipanteId(adminToken);

        AgregarDignidadRequest request = new AgregarDignidadRequest(1);

        mockMvc.perform(post("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones/" + participanteId + "/dignidades")
                        .header("Authorization", "Bearer " + socioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /inscripciones/{pid}/dignidades/{did} — Admin → 204")
    void eliminarDignidad_admin_returns204() throws Exception {
        String adminToken = obtenerToken("admin.test");
        String directivoToken = obtenerToken("directivo.test");
        Long participanteId = obtenerAdminParticipanteId(adminToken);

        // Agregar dignidad primero
        AgregarDignidadRequest addReq = new AgregarDignidadRequest(1);
        MvcResult addResult = mockMvc.perform(
                        post("/api/v1/salidas/" + SALIDA_PLANIFICADA + "/inscripciones/" + participanteId + "/dignidades")
                                .header("Authorization", "Bearer " + directivoToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andReturn();

        // Obtener ID de la dignidad asignada
        String body = addResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> dignidades = (java.util.List<Map<String, Object>>) data.get("dignidades");
        Long dignidadAsignadaId = ((Number) dignidades.get(0).get("id")).longValue();

        // Eliminar
        mockMvc.perform(delete("/api/v1/salidas/" + SALIDA_PLANIFICADA
                        + "/inscripciones/" + participanteId
                        + "/dignidades/" + dignidadAsignadaId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private Long obtenerAdminParticipanteId(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/salidas/" + SALIDA_PLANIFICADA)
                                .header("Authorization", "Bearer " + adminToken))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> participantes = (java.util.List<Map<String, Object>>) data.get("participantes");
        return ((Number) participantes.get(0).get("id")).longValue();
    }

    private CreateSalidaRequest nuevaSalidaRequest(String nombre) {
        return new CreateSalidaRequest(
                nombre,
                LocalDate.of(2026, 9, 1),
                LocalTime.of(5, 0),
                LocalDate.of(2026, 9, 3),
                null,
                RUTA_ID,
                "ALPINISMO",
                "PO001",
                "FS001",
                "SO002",
                (short) 20
        );
    }
}
