package com.sadday.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Test de arranque de la aplicación.
 *
 * <p>Verifica que:
 * <ul>
 *   <li>El contexto de Spring Boot carga correctamente.</li>
 *   <li>Flyway ejecuta todas las migraciones (V1–V8) sin errores contra PostgreSQL real.</li>
 *   <li>Hibernate valida el esquema JPA contra la BD sin discrepancias.</li>
 *   <li>Todos los beans se crean e inyectan correctamente.</li>
 * </ul>
 *
 * <p>Extiende {@link AbstractIntegrationTest} para obtener:
 * <ul>
 *   <li>Testcontainers con PostgreSQL 16.</li>
 *   <li>Par de claves RSA generadas en memoria para JWT.</li>
 *   <li>Propiedades dinámicas registradas antes de cargar el contexto.</li>
 * </ul>
 *
 * <p>{@link JavaMailSender} se mockea para no requerir servidor SMTP.
 */
@SpringBootTest
@DisplayName("Arranque de la aplicación")
class SaddayAppApplicationTests extends AbstractIntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Test
    @DisplayName("El contexto Spring Boot carga correctamente y Flyway ejecuta todas las migraciones")
    void contextLoads() {
        // Si el contexto arranca sin excepciones, el test pasa.
        // Esto verifica: Flyway migrations, Hibernate schema validation, bean wiring.
    }
}
