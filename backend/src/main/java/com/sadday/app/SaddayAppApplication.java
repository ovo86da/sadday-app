package com.sadday.app;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class SaddayAppApplication {

    /**
     * Zona horaria del club. Se fija antes de arrancar el contexto para que la
     * aplicación no dependa de la zona de la máquina o de la imagen que la
     * ejecuta: la JVM arranca en GMT dentro del contenedor y en la zona local
     * cuando se corre con {@code ./mvnw spring-boot:run}, de modo que los
     * {@code LocalDateTime.now()} del dominio grababan horas distintas según
     * cómo se hubiese levantado el backend.
     *
     * <p>El {@code ENV TZ} del Dockerfile cubre los despliegues; esto cubre
     * además el arranque desde Maven y el jar empaquetado en cualquier máquina.
     */
    private static final String ZONA_CLUB = "America/Guayaquil";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_CLUB));
        SpringApplication.run(SaddayAppApplication.class, args);
    }
}
