package com.sadday.app.estadisticas;

import com.sadday.app.AbstractIntegrationTest;
import com.sadday.app.auth.entity.UsuarioAuth;
import com.sadday.app.auth.repository.UsuarioAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del módulo Estadísticas.
 *
 * <p>Usa los datos de socios, salidas e informes de prueba.
 * La salida EN_CURSO (cccc...) tiene Admin como Jefe de Salida y Directivo inscrito.
 * La salida REALIZADA (dddd...) tiene informe con seRealizo=true.
 * Ambas usan mountain ID 1 (Chimborazo, seeded en V8).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@Sql({"/sql/socios-test-data.sql",
      "/sql/salidas-test-data.sql",
      "/sql/informes-test-data.sql"})
@DisplayName("Estadísticas — Integration Tests")
class EstadisticaIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired PasswordEncoder       passwordEncoder;
    @Autowired UsuarioAuthRepository usuarioAuthRepository;

    private static final UUID ADMIN_ID     = UUID.fromString("00000000-0000-4000-b000-000000000001");
    private static final UUID DIRECTIVO_ID = UUID.fromString("00000000-0000-4000-b000-000000000002");
    private static final UUID SOCIO_ID     = UUID.fromString("00000000-0000-4000-b000-000000000003");

    // Mountain ID 13 = Chimborazo (seeded en V26, CORDILLERA_OCCIDENTAL; V32 eliminó ID 1 con region='Andes')
    private static final int CHIMBORAZO_ID = 13;

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
    // GET /estadisticas/socios/{socioId}
    // =========================================================================

    @Test
    @DisplayName("GET /estadisticas/socios/{id} — Socio ve su propio historial → 200")
    void historialPropio_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/estadisticas/socios/" + ADMIN_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.socioId").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.data.totalParticipaciones").isNumber())
                .andExpect(jsonPath("$.data.historial").isArray())
                .andExpect(jsonPath("$.data.conteosDignidades").isArray());
    }

    @Test
    @DisplayName("GET /estadisticas/socios/{id} — Admin ve historial de Directivo → 200")
    void historialOtroSocio_adminPuedeVerlo_returns200() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/estadisticas/socios/" + DIRECTIVO_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.socioId").value(DIRECTIVO_ID.toString()));
    }

    @Test
    @DisplayName("GET /estadisticas/socios/{id} — Socio regular puede ver historial ajeno → 200")
    void historialAjeno_socioRegular_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        mockMvc.perform(get("/api/v1/estadisticas/socios/" + ADMIN_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.socioId").value(ADMIN_ID.toString()));
    }

    @Test
    @DisplayName("GET /estadisticas/socios/{id} — sin token → 401")
    void historial_sinToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/estadisticas/socios/" + ADMIN_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /estadisticas/socios/{id} — socio inexistente → 404")
    void historial_socioInexistente_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/estadisticas/socios/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /estadisticas/socios/{id} — Admin inscrito como Jefe: vecesJefeSalida > 0")
    void historial_adminEsJefe_countCorrecto() throws Exception {
        String token = obtenerToken("admin.test");

        // Admin es Jefe de Salida en la salida EN_CURSO (cccc...) cargada vía SQL
        mockMvc.perform(get("/api/v1/estadisticas/socios/" + ADMIN_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vecesJefeSalida").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET /estadisticas/socios/{id} — socio sin participaciones → totales en 0")
    void historial_sinParticipaciones_returns200ConCeros() throws Exception {
        String token = obtenerToken("socio.test");

        // SOCIO_ID no está inscrito en ninguna salida del SQL de prueba
        mockMvc.perform(get("/api/v1/estadisticas/socios/" + SOCIO_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalParticipaciones").value(0))
                .andExpect(jsonPath("$.data.totalCumbresLogradas").value(0))
                .andExpect(jsonPath("$.data.historial").isEmpty());
    }

    // =========================================================================
    // GET /estadisticas/mountains/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /estadisticas/mountains/{id} — Chimborazo con salidas → 200")
    void estadisticasMountain_conSalidas_returns200() throws Exception {
        String token = obtenerToken("socio.test");

        // Chimborazo (ID 1) tiene salidas cargadas via salidas-test-data.sql (ruta 5000)
        mockMvc.perform(get("/api/v1/estadisticas/mountains/" + CHIMBORAZO_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mountainId").value(CHIMBORAZO_ID))
                .andExpect(jsonPath("$.data.nombre").value("Chimborazo"))
                .andExpect(jsonPath("$.data.altitud").isNumber())
                .andExpect(jsonPath("$.data.totalSalidas").isNumber())
                .andExpect(jsonPath("$.data.rutas").isArray());
    }

    @Test
    @DisplayName("GET /estadisticas/mountains/{id} — mountain sin salidas → 200 con totales en 0")
    void estadisticasMountain_sinSalidas_returns200ConCeros() throws Exception {
        String token = obtenerToken("socio.test");

        // Mountain ID 14 (Cotopaxi seeded en V26; V32 eliminó ID 2 con region='Andes') no tiene salidas en los datos de test
        mockMvc.perform(get("/api/v1/estadisticas/mountains/14")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSalidas").value(0))
                .andExpect(jsonPath("$.data.salidasRealizadas").value(0))
                .andExpect(jsonPath("$.data.rutas").isEmpty());
    }

    @Test
    @DisplayName("GET /estadisticas/mountains/{id} — mountain inexistente → 404")
    void estadisticasMountain_inexistente_returns404() throws Exception {
        String token = obtenerToken("admin.test");

        mockMvc.perform(get("/api/v1/estadisticas/mountains/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /estadisticas/mountains/{id} — sin token → 401")
    void estadisticasMountain_sinToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/estadisticas/mountains/" + CHIMBORAZO_ID))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Helpers
    // =========================================================================
}
