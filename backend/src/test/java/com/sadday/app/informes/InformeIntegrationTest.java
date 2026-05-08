package com.sadday.app.informes;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import com.sadday.app.informes.dto.*;
import com.sadday.app.informes.entity.TipoReconocimiento;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del módulo Informes.
 *
 * <p>Carga socios-test-data.sql, salidas-test-data.sql e informes-test-data.sql.
 * Cada test se revierte por {@link Transactional}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql", "/sql/salidas-test-data.sql", "/sql/informes-test-data.sql"})
@DisplayName("Informes — Integration Tests")
class InformeIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID     = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID DIRECTIVO_ID = UUID.fromString("00000000-0000-4000-b000-000000000002");
    private static final UUID SOCIO_ID     = UUID.fromString("00000000-0000-4000-b000-000000000003");

    // Salida EN_CURSO (sin informe aún)
    private static final UUID SALIDA_EN_CURSO   = UUID.fromString("cccccccc-cccc-4ccc-bccc-cccccccccccc");
    // Salida PLANIFICADA (sin informe, para test de error de estado)
    private static final UUID SALIDA_PLANIFICADA = UUID.fromString("aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa");
    // Salida REALIZADA (con informe pre-existente)
    private static final UUID SALIDA_REALIZADA   = UUID.fromString("dddddddd-dddd-4ddd-bddd-dddddddddddd");

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
    // GET — obtener informe
    // =========================================================================

    @Test
    @DisplayName("GET /informes/{salidaId} — sin informe → 200 con data:null")
    void obtener_sinInforme_returns200ConDataNull() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/informes/" + SALIDA_EN_CURSO)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET /informes/{salidaId} — informe existente → 200")
    void obtener_informeExistente_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/informes/" + SALIDA_REALIZADA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salidaId").value(SALIDA_REALIZADA.toString()))
                .andExpect(jsonPath("$.data.seRealizo").value(true))
                .andExpect(jsonPath("$.data.condicionesMeterologicas").value("Soleado"))
                .andExpect(jsonPath("$.data.reconocimientos").isArray());
    }

    @Test
    @DisplayName("GET /informes/{salidaId} — sin token → 401")
    void obtener_sinToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/informes/" + SALIDA_REALIZADA))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // POST — crear informe
    // =========================================================================

    @Test
    @DisplayName("POST /informes/{salidaId} — Admin (Jefe de Salida) en EN_CURSO → 201")
    void crear_adminJefeSalida_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        CreateInformeRequest request = nuevoInformeRequest(true);

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_EN_CURSO)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.salidaId").value(SALIDA_EN_CURSO.toString()))
                .andExpect(jsonPath("$.data.seRealizo").value(true))
                .andExpect(jsonPath("$.data.cronica").value("Fue una gran salida"))
                .andExpect(jsonPath("$.data.validadoEn").doesNotExist());
    }

    @Test
    @DisplayName("POST /informes/{salidaId} — Directivo (rol privilegiado) → 201")
    void crear_directivo_returns201() throws Exception {
        String token = obtenerToken("directivo.test");
        CreateInformeRequest request = nuevoInformeRequest(false);

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_EN_CURSO)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.seRealizo").value(false));
    }

    @Test
    @DisplayName("POST /informes/{salidaId} — salida PLANIFICADA → 409")
    void crear_salidaPlanificada_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        CreateInformeRequest request = nuevoInformeRequest(true);

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_PLANIFICADA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /informes/{salidaId} — informe ya existe → 409")
    void crear_duplicado_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        CreateInformeRequest request = nuevoInformeRequest(true);

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_REALIZADA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /informes/{salidaId} — Socio regular (no es Jefe) → 403")
    void crear_socioRegularNoJefe_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        CreateInformeRequest request = nuevoInformeRequest(true);

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_EN_CURSO)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /informes/{salidaId} — sin seRealizo → 422")
    void crear_sinSeRealizo_returns422() throws Exception {
        String token = obtenerToken("admin.test");
        String body = "{\"cronica\":\"algo\"}";

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_EN_CURSO)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422));
    }

    // =========================================================================
    // PUT — actualizar informe
    // =========================================================================

    @Test
    @DisplayName("PUT /informes/{salidaId} — Admin actualiza informe no validado → 200")
    void actualizar_informeNoValidado_returns200() throws Exception {
        String token = obtenerToken("admin.test");
        UpdateInformeRequest request = new UpdateInformeRequest(
                null, null, "Nublado con viento",
                LocalTime.of(5, 30), null, null, null, null, null,
                "Crónica actualizada", null, null,
                null,
                false, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null
        );

        mockMvc.perform(put("/api/v1/informes/" + SALIDA_REALIZADA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.condicionesMeterologicas").value("Nublado con viento"))
                .andExpect(jsonPath("$.data.cronica").value("Crónica actualizada"));
    }

    @Test
    @DisplayName("PUT /informes/{salidaId} — Socio regular → 403")
    void actualizar_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        UpdateInformeRequest request = new UpdateInformeRequest(
                null, null, "nuevo valor", null, null, null, null, null, null, null, null, null,
                null,
                false, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null
        );

        mockMvc.perform(put("/api/v1/informes/" + SALIDA_REALIZADA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PATCH /validar
    // =========================================================================

    @Test
    @DisplayName("PATCH /informes/{salidaId}/validar — Directivo → 200")
    void validar_directivo_returns200() throws Exception {
        String token = obtenerToken("directivo.test");

        mockMvc.perform(patch("/api/v1/informes/" + SALIDA_REALIZADA + "/validar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verificar que quedó validado
        String adminToken = obtenerToken("admin.test");
        mockMvc.perform(get("/api/v1/informes/" + SALIDA_REALIZADA)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validadoEn").isNotEmpty())
                .andExpect(jsonPath("$.data.validadoPorId").value(DIRECTIVO_ID.toString()));
    }

    @Test
    @DisplayName("PATCH /validar — Socio regular → 403")
    void validar_socioRegular_returns403() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(patch("/api/v1/informes/" + SALIDA_REALIZADA + "/validar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /validar — ya validado → 409")
    void validar_yaValidado_returns409() throws Exception {
        String token = obtenerToken("directivo.test");

        // Primera validación — OK
        mockMvc.perform(patch("/api/v1/informes/" + SALIDA_REALIZADA + "/validar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Segunda validación — debe fallar
        mockMvc.perform(patch("/api/v1/informes/" + SALIDA_REALIZADA + "/validar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /informes/{salidaId} — informe validado no puede modificarse → 409")
    void actualizar_informeValidado_returns409() throws Exception {
        // Validar primero
        String directivoToken = obtenerToken("directivo.test");
        mockMvc.perform(patch("/api/v1/informes/" + SALIDA_REALIZADA + "/validar")
                        .header("Authorization", "Bearer " + directivoToken))
                .andExpect(status().isOk());

        // Intentar actualizar
        String adminToken = obtenerToken("admin.test");
        UpdateInformeRequest request = new UpdateInformeRequest(
                null, null, "nuevo valor", null, null, null, null, null, null, null, null, null,
                null,
                false, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null, null, null
        );

        mockMvc.perform(put("/api/v1/informes/" + SALIDA_REALIZADA)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // POST /reconocimientos
    // =========================================================================

    @Test
    @DisplayName("POST /reconocimientos — Admin agrega reconocimiento → 201")
    void agregarReconocimiento_admin_returns201() throws Exception {
        String token = obtenerToken("admin.test");
        // Directivo es participante de la salida REALIZADA
        AgregarReconocimientoRequest request = new AgregarReconocimientoRequest(
                DIRECTIVO_ID, TipoReconocimiento.DESTACADO, "Excelente desempeño"
        );

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_REALIZADA + "/reconocimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tipo").value("DESTACADO"))
                .andExpect(jsonPath("$.data.motivo").value("Excelente desempeño"))
                .andExpect(jsonPath("$.data.socioId").value(DIRECTIVO_ID.toString()));
    }

    @Test
    @DisplayName("POST /reconocimientos — Socio no participante → 409")
    void agregarReconocimiento_socioNoParticipante_returns409() throws Exception {
        String token = obtenerToken("admin.test");
        // SOCIO_ID no está inscrito en SALIDA_REALIZADA
        AgregarReconocimientoRequest request = new AgregarReconocimientoRequest(
                SOCIO_ID, TipoReconocimiento.AMONESTADO, "Motivo"
        );

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_REALIZADA + "/reconocimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /reconocimientos — Socio regular (no Jefe) → 403")
    void agregarReconocimiento_socioRegularNoJefe_returns403() throws Exception {
        String token = obtenerToken("socio.test");
        AgregarReconocimientoRequest request = new AgregarReconocimientoRequest(
                DIRECTIVO_ID, TipoReconocimiento.DESTACADO, "Motivo"
        );

        mockMvc.perform(post("/api/v1/informes/" + SALIDA_REALIZADA + "/reconocimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // DELETE /reconocimientos/{id}
    // =========================================================================

    @Test
    @DisplayName("DELETE /reconocimientos/{id} — Admin elimina reconocimiento → 204")
    void eliminarReconocimiento_admin_returns204() throws Exception {
        String token = obtenerToken("admin.test");

        // Agregar primero
        AgregarReconocimientoRequest addRequest = new AgregarReconocimientoRequest(
                DIRECTIVO_ID, TipoReconocimiento.DESTACADO, "Motivo temporal"
        );
        MvcResult addResult = mockMvc.perform(
                        post("/api/v1/informes/" + SALIDA_REALIZADA + "/reconocimientos")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = addResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper.readValue(body, Map.class).get("data");
        Long reconocimientoId = ((Number) data.get("id")).longValue();

        // Eliminar
        mockMvc.perform(delete("/api/v1/informes/" + SALIDA_REALIZADA + "/reconocimientos/" + reconocimientoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /reconocimientos/{id} — id inexistente → 404")
    void eliminarReconocimiento_inexistente_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(delete("/api/v1/informes/" + SALIDA_REALIZADA + "/reconocimientos/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CreateInformeRequest nuevoInformeRequest(boolean seRealizo) {
        List<SegmentoViajeRequest> segmentos = List.of(
                new SegmentoViajeRequest("Club Sadday", "Parking", false, null, null, null)
        );
        return new CreateInformeRequest(
                seRealizo,
                seRealizo, // lograronCumbre: mismo valor que seRealizo en tests
                "Parcialmente nublado",
                LocalTime.of(5, 30),
                LocalTime.of(8, 0),
                LocalTime.of(11, 30),
                LocalTime.of(12, 0),
                LocalTime.of(15, 0),
                LocalTime.of(17, 30),
                "Fue una gran salida",
                "Nada que mejorar",
                "Sin comentarios adicionales",
                segmentos,
                false, null, null, null,
                false, null, null, null,
                false, null, null, null,
                null, null, null, null, null, null
        );
    }
}
