package com.sadday.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de OpenAPI 3 / Swagger UI.
 *
 * <p>Disponible en:
 * <ul>
 *   <li>Swagger UI: {@code /swagger-ui.html}</li>
 *   <li>JSON spec:  {@code /v3/api-docs}</li>
 * </ul>
 *
 * <p>Todos los endpoints protegidos requieren un Bearer token JWT en la cabecera
 * {@code Authorization}. Se puede ingresar directamente en el botón "Authorize" de Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${sadday.mail.app-url:http://localhost:8080}")
    private String appUrl;

    @Bean
    public OpenAPI saddayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sadday App API")
                        .description("""
                                API REST del sistema de gestión del Club de Montaña Sadday.

                                **Autenticación:** Bearer JWT (RS256).
                                Obtén tu token en `POST /api/v1/auth/login` y pégalo
                                en el botón **Authorize** de esta página.

                                **Roles del sistema:** Admin · Secretaria · Directivo · Socio
                                """)
                        .version("v1")
                        .contact(new Contact()
                                .name("Club de Montaña Sadday")
                                .email("admin@el-sadday.com"))
                        .license(new License()
                                .name("Privado — uso interno")
                                .url("#")))

                .servers(List.of(
                        new Server().url(appUrl).description("Servidor actual"),
                        new Server().url("http://localhost:8080").description("Local")))

                // Esquema de seguridad global: Bearer JWT
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Pega el access token obtenido en /auth/login")))

                // Aplica Bearer a todos los endpoints por defecto
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
