package com.sadday.app;

import com.sadday.app.auth.dto.LoginRequest;
import com.sadday.app.auth.service.SecurityEventService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clase base para tests de integración.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Importa {@link TestcontainersConfig} que levanta PostgreSQL como bean de Spring.
 *       El contenedor queda ligado al ciclo de vida del contexto cacheado, evitando que
 *       se detenga y reinicie entre clases de test (lo que causaría {@code CannotCreateTransaction}).</li>
 *   <li>Genera un par de claves RSA-2048 en archivos temporales para JWT (una sola vez por JVM).</li>
 * </ul>
 *
 * <p>La conexión a PostgreSQL es configurada automáticamente por {@code @ServiceConnection}
 * en {@link TestcontainersConfig} — no se necesita {@code @DynamicPropertySource} para la BD.
 *
 * <p>{@code SecurityEventService} se mockea porque usa {@code Propagation.REQUIRES_NEW},
 * lo que abre una transacción independiente que no ve los datos insertados por {@code @Sql}
 * en la transacción del test, provocando violaciones de FK en {@code security_events.socio_id}.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    /** Mock para evitar FK violations por Propagation.REQUIRES_NEW en tests transaccionales. */
    @MockitoBean
    SecurityEventService securityEventService;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Contraseña compartida por todos los usuarios de test. */
    protected static final String TEST_PASSWORD = "TestPassword123!";

    /**
     * Hace login con el usuario dado y devuelve el access token JWT.
     * Requiere que el usuario exista en usuarios_auth antes de llamar.
     */
    protected String obtenerToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) objectMapper
                .readValue(body, Map.class).get("data");
        return (String) data.get("accessToken");
    }

    // RSA keys generadas una sola vez para toda la sesión de tests
    private static volatile boolean keysInitialized = false;
    private static Path privateKeyFile;
    private static Path publicKeyFile;

    @DynamicPropertySource
    static void configureRsaKeys(DynamicPropertyRegistry registry) {
        ensureRsaKeysExist();
        registry.add("sadday.jwt.private-key-location",
                () -> "file:" + privateKeyFile.toAbsolutePath());
        registry.add("sadday.jwt.public-key-location",
                () -> "file:" + publicKeyFile.toAbsolutePath());
    }

    private static synchronized void ensureRsaKeysExist() {
        if (keysInitialized) return;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            Path tempDir = Files.createTempDirectory("sadday-test-jwt-");

            privateKeyFile = tempDir.resolve("test-private.pem");
            publicKeyFile  = tempDir.resolve("test-public.pem");

            // JwtConfig.readPem() filtra líneas "-----" y une el resto → Base64 puro
            Files.writeString(privateKeyFile,
                    "-----BEGIN PRIVATE KEY-----\n" +
                    Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()) +
                    "\n-----END PRIVATE KEY-----");

            Files.writeString(publicKeyFile,
                    "-----BEGIN PUBLIC KEY-----\n" +
                    Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) +
                    "\n-----END PUBLIC KEY-----");

            keysInitialized = true;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron generar las claves RSA de test", e);
        }
    }
}
