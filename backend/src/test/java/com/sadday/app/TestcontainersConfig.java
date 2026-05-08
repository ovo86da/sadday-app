package com.sadday.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Configura el contenedor de PostgreSQL como un bean de Spring, ligando su ciclo de vida
 * al contexto del test.
 *
 * <p>Con {@link ServiceConnection}, Spring Boot registra automáticamente las propiedades
 * de conexión (URL, usuario, contraseña) sin necesitar {@code @DynamicPropertySource} manual.
 *
 * <p>Al ser un bean de Spring, el contenedor NO se detiene entre clases de test que comparten
 * el mismo contexto cacheado, resolviendo el problema de "CannotCreateTransaction" cuando
 * múltiples clases de integración usan el mismo contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    @SuppressWarnings({"deprecation", "resource"})
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("sadday_test")
                .withUsername("sadday_test")
                .withPassword("test_password");
    }
}
