package com.sadday.app.mountains;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.entity.TipoActividad;
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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del módulo Montañas y Rutas.
 *
 * <p>Carga socios-test-data.sql y mountains-test-data.sql para los datos de prueba.
 * Cada test se revierte automáticamente por {@link Transactional}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql", "/sql/mountains-test-data.sql"})
@DisplayName("Mountains — Integration Tests")
class MountainIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID     = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID DIRECTIVO_ID = UUID.fromString("00000000-0000-4000-b000-000000000002");
    private static final UUID SOCIO_ID     = UUID.fromString("00000000-0000-4000-b000-000000000003");

    private static final Integer MOUNTAIN_ID      = 1000;
    private static final Integer MOUNTAIN_TARGET  = 1001;
    private static final Integer RUTA_PENDIENTE   = 2000;
    private static final Integer RUTA_APROBADA    = 2001;

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
    @DisplayName("GET /mountains/lookups — autenticado → 200 con todas las tablas de referencia")
    void lookups_authenticated_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/mountains/lookups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.escalasAlpina").isArray())
                .andExpect(jsonPath("$.data.dificultadesRoca").isArray())
                .andExpect(jsonPath("$.data.dificultadesHielo").isArray())
                .andExpect(jsonPath("$.data.compromisos").isArray())
                .andExpect(jsonPath("$.data.yosemiteClases").isArray())
                .andExpect(jsonPath("$.data.saddayRiesgos").isArray())
                .andExpect(jsonPath("$.data.clasificacionesSocio").isArray())
                .andExpect(jsonPath("$.data.equipos").isArray());
    }

    @Test
    @DisplayName("GET /mountains/lookups — sin autenticación → 401")
    void lookups_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/mountains/lookups"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Listar montañas
    // =========================================================================

    @Test
    @DisplayName("GET /mountains — autenticado → 200 paginado")
    void listar_mountains_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/mountains")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /mountains?q=Target — filtra por nombre")
    void listar_mountains_filtroQ() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/mountains?q=Target")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].nombre").value("Monte Target"));
    }

    // =========================================================================
    // Crear montaña
    // =========================================================================

    @Test
    @DisplayName("POST /mountains — Admin → 201")
    void crear_mountain_admin_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        CreateMountainRequest request = new CreateMountainRequest("Nuevo Pico", "Andes Norte", 5500, "Ecuador");

        mockMvc.perform(post("/api/v1/mountains")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nombre").value("Nuevo Pico"))
                .andExpect(jsonPath("$.data.altitud").value(5500));
    }

    @Test
    @DisplayName("POST /mountains — Directivo → 201")
    void crear_mountain_directivo_returns201() throws Exception {
        String token = obtenerToken("directivo.test");
        CreateMountainRequest request = new CreateMountainRequest("Pico Directivo", "Sierra Sur", 4200, "Ecuador");

        mockMvc.perform(post("/api/v1/mountains")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /mountains — Socio regular → 403")
    void crear_mountain_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        CreateMountainRequest request = new CreateMountainRequest("Pico No Permitido", "Region X", 3000, "Ecuador");

        mockMvc.perform(post("/api/v1/mountains")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /mountains — body inválido → 422")
    void crear_mountain_bodyInvalido_returns422() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(post("/api/v1/mountains")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(422));
    }

    // =========================================================================
    // Obtener / actualizar / eliminar montaña
    // =========================================================================

    @Test
    @DisplayName("GET /mountains/{id} — autenticado → 200")
    void obtener_mountain_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/mountains/" + MOUNTAIN_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(MOUNTAIN_ID))
                .andExpect(jsonPath("$.data.nombre").value("Monte Test"));
    }

    @Test
    @DisplayName("GET /mountains/{id} — ID inexistente → 404")
    void obtener_mountain_noExiste_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/mountains/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /mountains/{id} — Admin → 200")
    void actualizar_mountain_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateMountainRequest request = new UpdateMountainRequest("Monte Actualizado", "Nueva Region", 4600, "Ecuador");

        mockMvc.perform(put("/api/v1/mountains/" + MOUNTAIN_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nombre").value("Monte Actualizado"));
    }

    @Test
    @DisplayName("DELETE /mountains/{id} — Admin → 204")
    void eliminar_mountain_admin_returns204() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(delete("/api/v1/mountains/" + MOUNTAIN_TARGET)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /mountains/{id} — Socio regular → 403")
    void eliminar_mountain_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(delete("/api/v1/mountains/" + MOUNTAIN_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Acceso por nivel
    // =========================================================================

    @Test
    @DisplayName("GET /mountains/acceso-por-nivel — autenticado → 200 con 6 niveles")
    void accesoPorNivel_authenticated_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/mountains/acceso-por-nivel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    @DisplayName("PUT /mountains/acceso-por-nivel/{nivelId} — Directivo → 200")
    void actualizarAcceso_directivo_returns200() throws Exception {
        String token = obtenerToken("directivo.test");
        UpdateAccesoNivelRequest request = new UpdateAccesoNivelRequest(
                "IFAS002", "UIAA-F002", "WI002", "C002", "Y003", "SA002", "SA002");

        mockMvc.perform(put("/api/v1/mountains/acceso-por-nivel/SO001")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nivelSocioId").value("SO001"))
                .andExpect(jsonPath("$.data.maxIfasId").value("IFAS002"));
    }

    @Test
    @DisplayName("PUT /mountains/acceso-por-nivel/{nivelId} — Socio regular → 403")
    void actualizarAcceso_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        UpdateAccesoNivelRequest request = new UpdateAccesoNivelRequest(
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y002", "SA001", "SA001");

        mockMvc.perform(put("/api/v1/mountains/acceso-por-nivel/SO001")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Listar rutas
    // =========================================================================

    @Test
    @DisplayName("GET /rutas — autenticado → 200 paginado")
    void listar_rutas_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/rutas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /rutas — sin autenticación → 401")
    void listar_rutas_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/rutas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /rutas?mountainId=1000 — filtra por montaña")
    void listar_rutas_filtroMountain() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/rutas?mountainId=" + MOUNTAIN_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /rutas?aprobada=true — filtra aprobadas")
    void listar_rutas_filtroAprobada() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/rutas?aprobada=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].aprobada").value(true));
    }

    // =========================================================================
    // Crear ruta
    // =========================================================================

    @Test
    @DisplayName("POST /rutas — Socio regular → 201 (cualquier autenticado puede proponer)")
    void crear_ruta_socioRegular_returns201() throws Exception {
        String token = obtenerToken("socio.test");
        CreateRutaRequest request = nuevaRutaRequest("Ruta Nueva Socio", MOUNTAIN_ID);

        mockMvc.perform(post("/api/v1/rutas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nombre").value("Ruta Nueva Socio"))
                .andExpect(jsonPath("$.data.aprobada").value(false));
    }

    @Test
    @DisplayName("POST /rutas — sin autenticación → 401")
    void crear_ruta_unauthenticated_returns401() throws Exception {
        CreateRutaRequest request = nuevaRutaRequest("Ruta No Auth", MOUNTAIN_ID);

        mockMvc.perform(post("/api/v1/rutas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Obtener ruta
    // =========================================================================

    @Test
    @DisplayName("GET /rutas/{id} — autenticado → 200 con detalle")
    void obtener_ruta_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/rutas/" + RUTA_APROBADA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(RUTA_APROBADA))
                .andExpect(jsonPath("$.data.nombre").value("Ruta Aprobada"))
                .andExpect(jsonPath("$.data.aprobada").value(true))
                .andExpect(jsonPath("$.data.contactos").isArray());
    }

    @Test
    @DisplayName("GET /rutas/{id} — ID inexistente → 404")
    void obtener_ruta_noExiste_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/rutas/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Actualizar / aprobar / eliminar ruta
    // =========================================================================

    @Test
    @DisplayName("PUT /rutas/{id} — Admin → 200")
    void actualizar_ruta_admin_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateRutaRequest request = updateRutaRequest("Ruta Actualizada", MOUNTAIN_ID);

        mockMvc.perform(put("/api/v1/rutas/" + RUTA_PENDIENTE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nombre").value("Ruta Actualizada"));
    }

    @Test
    @DisplayName("PUT /rutas/{id} — Socio regular → 403")
    void actualizar_ruta_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        UpdateRutaRequest request = updateRutaRequest("No Permitido", MOUNTAIN_ID);

        mockMvc.perform(put("/api/v1/rutas/" + RUTA_PENDIENTE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /rutas/{id}/aprobar — Directivo → 200")
    void aprobar_ruta_directivo_returns200() throws Exception {
        String token = obtenerToken("directivo.test");

        mockMvc.perform(patch("/api/v1/rutas/" + RUTA_PENDIENTE + "/aprobar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /rutas/{id}/aprobar — Socio regular → 403")
    void aprobar_ruta_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(patch("/api/v1/rutas/" + RUTA_PENDIENTE + "/aprobar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /rutas/{id} — Admin → 204")
    void eliminar_ruta_admin_returns204() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(delete("/api/v1/rutas/" + RUTA_APROBADA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /rutas/{id} — Socio regular → 403")
    void eliminar_ruta_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(delete("/api/v1/rutas/" + RUTA_PENDIENTE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Contactos
    // =========================================================================

    @Test
    @DisplayName("POST /rutas/{id}/contactos — Directivo → 201")
    void agregarContacto_directivo_returns201() throws Exception {
        // Crear contacto global primero (cualquier autenticado puede crearlo)
        String adminToken = obtenerToken("admin.test");
        MvcResult contactoResult = mockMvc.perform(post("/api/v1/contactos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Carlos Pozo\",\"telefono\":\"0999999999\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        @SuppressWarnings("unchecked")
        Integer contactoId = (Integer) ((Map<String, Object>) objectMapper
                .readValue(contactoResult.getResponse().getContentAsString(), Map.class)
                .get("data")).get("id");

        String token = obtenerToken("directivo.test");
        VincularContactoRutaRequest request = new VincularContactoRutaRequest(contactoId, "GUIA");

        mockMvc.perform(post("/api/v1/rutas/" + RUTA_PENDIENTE + "/contactos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contactoId").value(contactoId))
                .andExpect(jsonPath("$.data.tipoContacto").value("GUIA"));
    }

    @Test
    @DisplayName("POST /rutas/{id}/contactos — Socio regular → 403")
    void agregarContacto_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        VincularContactoRutaRequest request = new VincularContactoRutaRequest(1, "GUIA");

        mockMvc.perform(post("/api/v1/rutas/" + RUTA_PENDIENTE + "/contactos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private CreateRutaRequest nuevaRutaRequest(String nombre, Integer mountainId) {
        return new CreateRutaRequest(
                nombre, TipoActividad.ALPINISMO, mountainId,
                null, null, null, null, null, null, null,
                false, null, null, null,
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    private UpdateRutaRequest updateRutaRequest(String nombre, Integer mountainId) {
        return new UpdateRutaRequest(
                nombre, TipoActividad.ALPINISMO, mountainId,
                null, "Sector Norte", null, null, null, null, null,
                false, null, null, null,
                "IFAS002", "UIAA-F002", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    // =========================================================================
    // Track URL
    // =========================================================================

    @Test
    @DisplayName("POST /rutas con trackUrl — Admin → 201 y trackUrl presente en respuesta")
    void crear_ruta_conTrackUrl_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        String wikiloc = "https://es.wikiloc.com/rutas-senderismo/cotopaxi-ruta-normal-123456";

        CreateRutaRequest request = new CreateRutaRequest(
                "Ruta con Track", TipoActividad.ALPINISMO, MOUNTAIN_ID,
                null, "Sector Sur", null, null, null, null, null,
                false, null, wikiloc, null,
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/rutas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.trackUrl").value(wikiloc))
                .andReturn();

        // Obtener la ruta creada y verificar que trackUrl persiste
        String body = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Integer newId = (Integer) ((Map<String, Object>) objectMapper
                .readValue(body, Map.class).get("data")).get("id");

        mockMvc.perform(get("/api/v1/rutas/" + newId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackUrl").value(wikiloc));
    }

    @Test
    @DisplayName("PUT /rutas/{id} — actualizar trackUrl — Admin → 200")
    void actualizar_ruta_conTrackUrl_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        String track = "https://www.wikiloc.com/rutas-alpinismo/chimborazo-whymper-789";

        UpdateRutaRequest request = new UpdateRutaRequest(
                "Ruta con Track Actualizada", TipoActividad.ALPINISMO, MOUNTAIN_ID,
                null, "Sector Este", null, null, null, null, null,
                false, null, track, null,
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(put("/api/v1/rutas/" + RUTA_PENDIENTE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackUrl").value(track));
    }
}
