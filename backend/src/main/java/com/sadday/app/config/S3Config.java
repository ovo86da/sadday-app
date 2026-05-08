package com.sadday.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Configuración del cliente AWS S3.
 *
 * <p>Si se proporcionan {@code sadday.s3.access-key} y {@code sadday.s3.secret-key}
 * (variables de entorno {@code S3_ACCESS_KEY} / {@code S3_SECRET_KEY}), se usan credenciales
 * estáticas (útil en dev local con MinIO o Lightsail Object Storage).
 *
 * <p>En producción con IAM Role o ECS Task Role, dejar las variables vacías: el SDK
 * resuelve credenciales automáticamente via {@link DefaultCredentialsProvider}.
 */
@Configuration
public class S3Config {

    @Value("${sadday.s3.region:us-east-1}")
    private String region;

    @Value("${sadday.s3.access-key:}")
    private String accessKey;

    @Value("${sadday.s3.secret-key:}")
    private String secretKey;

    /** Endpoint personalizado para MinIO u otros S3-compatibles. Vacío = AWS S3 estándar. */
    @Value("${sadday.s3.endpoint:}")
    private String endpoint;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder().region(Region.of(region));

        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }

        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true);   // Requerido para MinIO y Lightsail
        }

        return builder.build();
    }
}
