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
import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("PUT acceso-por-nivel — el cambio queda pendiente y NO se aplica")
    void solicitarCambio_directivo_quedaPendiente() throws Exception {
        // Lo que de verdad prueba FR-026: el umbral vigente no se mueve. Si solo
        // se comprobara que la peticion responde 202, un fallo que aplicara el
        // cambio igualmente pasaria desapercibido.
        String token = obtenerToken("directivo.test");
        String antes = umbralIfasVigente(token, "SO001");

        UpdateAccesoNivelRequest request = new UpdateAccesoNivelRequest(
                "IFAS002", "UIAA-F002", "WI002", "C002", "Y003", "SA002", "SA002");

        mockMvc.perform(put("/api/v1/mountains/acceso-por-nivel/SO001")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.data.escalas").isArray())
                .andExpect(jsonPath("$.data.escalas.length()").value(7));

        assertThat(umbralIfasVigente(token, "SO001"))
                .as("el umbral vigente no debe moverse hasta que se apruebe")
                .isEqualTo(antes);
    }

    @Test
    @DisplayName("quien pide el cambio no puede aprobarlo")
    void aprobar_mismoSolicitante_returns403() throws Exception {
        // Es toda la gracia del FR: sin esto, la doble aprobacion es una sola.
        String token = obtenerToken("directivo.test");
        String cambioId = pedirCambio(token, "SO002", "IFAS003");

        mockMvc.perform(post("/api/v1/mountains/acceso-por-nivel/cambios/" + cambioId + "/aprobar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("otro directivo aprueba y entonces sí se aplica")
    void aprobar_otroDirectivo_aplicaElCambio() throws Exception {
        String deQuienPide = obtenerToken("directivo.test");
        String cambioId = pedirCambio(deQuienPide, "SO003", "IFAS004");

        String deQuienAprueba = obtenerToken("admin.test");
        mockMvc.perform(post("/api/v1/mountains/acceso-por-nivel/cambios/" + cambioId + "/aprobar")
                        .header("Authorization", "Bearer " + deQuienAprueba))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("APROBADO"));

        assertThat(umbralIfasVigente(deQuienPide, "SO003"))
                .as("tras aprobarlo, el umbral vigente sí cambia")
                .isEqualTo("IFAS004");
    }

    @Test
    @DisplayName("endurecer pasa por la misma aprobación que relajar")
    void endurecer_tambienQuedaPendiente() throws Exception {
        // Decision explicita del club, con su consecuencia asumida: arreglar un
        // umbral peligrosamente laxo tambien espera a una segunda persona.
        String token = obtenerToken("directivo.test");
        String antes = umbralIfasVigente(token, "SO004");

        // IFAS001 es el grado mas bajo: esto endurece, no relaja.
        UpdateAccesoNivelRequest request = new UpdateAccesoNivelRequest(
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y001", "SA001", "SA001");

        mockMvc.perform(put("/api/v1/mountains/acceso-por-nivel/SO004")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.estado").value("PENDIENTE"));

        assertThat(umbralIfasVigente(token, "SO004")).isEqualTo(antes);
    }

    @Test
    @DisplayName("un segundo cambio sobre el mismo nivel se rechaza, no se encola")
    void segundoCambioMismoNivel_returns409() throws Exception {
        String token = obtenerToken("directivo.test");
        pedirCambio(token, "SO005", "IFAS003");

        UpdateAccesoNivelRequest otro = new UpdateAccesoNivelRequest(
                "IFAS005", "UIAA-F002", "WI002", "C002", "Y003", "SA002", "SA002");
        mockMvc.perform(put("/api/v1/mountains/acceso-por-nivel/SO005")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otro)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("rechazar sin motivo → 422; con motivo → queda rechazado")
    void rechazar_exigeMotivo() throws Exception {
        String deQuienPide = obtenerToken("directivo.test");
        String cambioId = pedirCambio(deQuienPide, "SO006", "IFAS003");
        String deQuienResuelve = obtenerToken("admin.test");

        mockMvc.perform(post("/api/v1/mountains/acceso-por-nivel/cambios/" + cambioId + "/rechazar")
                        .header("Authorization", "Bearer " + deQuienResuelve)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/mountains/acceso-por-nivel/cambios/" + cambioId + "/rechazar")
                        .header("Authorization", "Bearer " + deQuienResuelve)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"El salto de hielo es demasiado para ese nivel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estado").value("RECHAZADO"));
    }

    /** Pide un cambio y devuelve su id. */
    private String pedirCambio(String token, String nivelId, String ifasId) throws Exception {
        UpdateAccesoNivelRequest req = new UpdateAccesoNivelRequest(
                ifasId, "UIAA-F002", "WI002", "C002", "Y003", "SA002", "SA002");
        String body = mockMvc.perform(put("/api/v1/mountains/acceso-por-nivel/" + nivelId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    /** El umbral de IFAS que está vigente ahora mismo para un nivel. */
    private String umbralIfasVigente(String token, String nivelId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/mountains/acceso-por-nivel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var n : objectMapper.readTree(body).path("data")) {
            if (nivelId.equals(n.path("nivelSocioId").asText())) return n.path("maxIfasId").asText();
        }
        throw new AssertionError("nivel no encontrado: " + nivelId);
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
    @DisplayName("GET /rutas?estado=APROBADA — filtra aprobadas")
    void listar_rutas_filtroEstado() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/rutas?estado=APROBADA")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].estado").value("APROBADA"));
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
                .andExpect(jsonPath("$.data.estado").value("PENDIENTE"));
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
                .andExpect(jsonPath("$.data.estado").value("APROBADA"))
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


    @Test
    @DisplayName("crear ruta con desnivel negativo 0 → 201, y 0 no se confunde con null")
    void crearRuta_desnivelNegativoCero_seAcepta() throws Exception {
        // El CHECK de V1 era `> 0` y los DTOs llevaban @Min(1), asi que un cero
        // se rechazaba. Y un cero es legitimo: sincholagua-camping sube 1038 m y
        // no baja nada porque termina en el campamento. FR-024 lo paso a >= 0.
        //
        // La otra mitad del test es que 0 y null no son lo mismo: 0 afirma que no
        // baja, null dice que no se sabe. Si la serializacion los confundiera, el
        // dato quedaria mal sin que nada fallara.
        String token = obtenerToken("admin.test");

        var conCero = nuevaRutaRequestConDesnivel("Ruta solo subida", MOUNTAIN_ID, 1038, 0);
        mockMvc.perform(post("/api/v1/rutas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conCero)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.desnivelPositivoM").value(1038))
                .andExpect(jsonPath("$.data.desnivelNegativoM").value(0));

        var sinDato = nuevaRutaRequestConDesnivel("Ruta sin desnivel conocido", MOUNTAIN_ID, 1038, null);
        mockMvc.perform(post("/api/v1/rutas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sinDato)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.desnivelNegativoM").doesNotExist());
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private CreateRutaRequest nuevaRutaRequest(String nombre, Integer mountainId) {
        return new CreateRutaRequest(
                nombre, TipoActividad.ALPINISMO, mountainId,
                null, null, null, null, null, null, null, null,
                false, null, null, null,
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null
        );
    }

    /**
     * Igual que {@link #nuevaRutaRequest} pero fijando los dos desniveles, para
     * el caso en que el negativo es cero.
     */
    private CreateRutaRequest nuevaRutaRequestConDesnivel(
            String nombre, Integer mountainId,
            Integer desnivelPositivoM, Integer desnivelNegativoM) {
        return new CreateRutaRequest(
                nombre, TipoActividad.ALPINISMO, mountainId,
                null, null, null, desnivelPositivoM, desnivelNegativoM, null, null, null,
                false, null, null, null,
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null
        );
    }

    private UpdateRutaRequest updateRutaRequest(String nombre, Integer mountainId) {
        return new UpdateRutaRequest(
                nombre, TipoActividad.ALPINISMO, mountainId,
                null, "Sector Norte", null, null, null, null, null, null,
                false, null, null, null,
                "IFAS002", "UIAA-F002", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null
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
                null, "Sector Sur", null, null, null, null, null, null,
                false, null, wikiloc, null,
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null
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
                null, "Sector Este", null, null, null, null, null, null,
                false, null, track, null,
                "IFAS001", "UIAA-F001", "WI001", "C001", "Y002", "SA001", "SA001",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null
        );

        mockMvc.perform(put("/api/v1/rutas/" + RUTA_PENDIENTE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackUrl").value(track));
    }
}
