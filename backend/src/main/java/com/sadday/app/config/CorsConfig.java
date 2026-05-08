package com.sadday.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración CORS.
 *
 * <p>Solo se aceptan peticiones desde los dominios configurados
 * (frontend React y app Flutter en modo web). No se usa wildcard "*".
 */
@Configuration
public class CorsConfig {

    @Value("${sadday.mail.app-url}")
    private String appUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Orígenes permitidos: solo el frontend conocido
        config.setAllowedOrigins(List.of(appUrl));

        // Métodos HTTP permitidos
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Headers permitidos en el request
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Sadday-Client"));

        // Headers expuestos al cliente en la respuesta
        config.setExposedHeaders(List.of("Authorization"));

        // Permitir cookies / Authorization header (necesario para Bearer tokens)
        config.setAllowCredentials(true);

        // Tiempo máximo de caché del preflight en segundos (1 hora)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
