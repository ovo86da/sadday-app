package com.sadday.app.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación.
 *
 * <p>Inyectar este {@link Clock} en lugar de llamar a {@code LocalDateTime.now()}
 * permite fijar la hora en los tests —{@code Clock.fixed(...)}— sin tocar el
 * reloj de la máquina. Es lo que hace testeable cualquier regla que dependa de
 * «hoy»: vigencias de documentos, fechas de salida, caducidad de tokens.
 *
 * <pre>
 *   private final Clock clock;
 *   ...
 *   LocalDateTime ahora = LocalDateTime.now(clock);
 * </pre>
 *
 * <p>No se ha migrado el código existente en bloque, y la razón es medible: de
 * las 120 llamadas sin zona, 61 viven dentro de entidades JPA —en
 * {@code @PrePersist}, {@code @PreUpdate} e inicializadores de campo— que
 * construye Hibernate y no Spring, así que no hay constructor donde inyectar
 * nada. Para esas el camino correcto es la auditoría de Spring Data JPA con un
 * {@code DateTimeProvider}, que es una decisión aparte. Las otras 59 están en
 * beans y se van migrando al tocarlas.
 */
@Configuration
public class TimeConfig {

    /** Zona del club; coincide con la que fija {@code SaddayAppApplication}. */
    public static final ZoneId ZONA_CLUB = ZoneId.of("America/Guayaquil");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA_CLUB);
    }
}
