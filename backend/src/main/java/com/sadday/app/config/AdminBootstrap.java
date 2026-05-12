package com.sadday.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Crea el usuario admin inicial en prod/staging si no existe ningún usuario en el sistema.
 *
 * La contraseña se lee de la variable de entorno ADMIN_INITIAL_PASSWORD.
 * Tras el primer despliegue, el admin debe cambiarla desde la aplicación.
 *
 * Solo se ejecuta si la tabla usuarios_auth está vacía.
 */
@Slf4j
@Component
@Profile({"prod", "staging"})
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;

    @Value("${sadday.admin.initial-password}")
    private String adminInitialPassword;

    @Value("${sadday.admin.username:admin}")
    private String adminUsername;

    @Override
    public void run(String... args) {
        long existingUsers = jdbcClient
                .sql("SELECT COUNT(*) FROM usuarios_auth")
                .query(Long.class)
                .single();

        if (existingUsers > 0) {
            log.info("AdminBootstrap: ya existen {} usuario(s) — omitiendo creación.", existingUsers);
            return;
        }

        Integer rolAdminId = jdbcClient
                .sql("SELECT id FROM roles_sistema WHERE nombre = 'Admin'")
                .query(Integer.class)
                .single();

        Integer tipoSocioId = jdbcClient
                .sql("SELECT id FROM tipo_socio_club WHERE nombre = 'Socio Activo'")
                .query(Integer.class)
                .single();

        Integer estadoHabilitadoId = jdbcClient
                .sql("SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'")
                .query(Integer.class)
                .single();

        UUID socioId = UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO socios (id, nombre, apellido, cedula, correo, telefono,
                    fecha_nacimiento, fecha_ingreso, estado_habilitacion_id,
                    tipo_socio_id, rol_sistema_id)
                VALUES (:id, 'Admin', 'Sistema', '0000000000', 'admin@sadday.local',
                    '0000000000', :fechaNac, :fechaIng, :estadoId, :tipoId, :rolId)
                """)
                .param("id", socioId)
                .param("fechaNac", LocalDate.of(1990, 1, 1))
                .param("fechaIng", LocalDate.now())
                .param("estadoId", estadoHabilitadoId)
                .param("tipoId", tipoSocioId)
                .param("rolId", rolAdminId)
                .update();

        jdbcClient.sql("""
                INSERT INTO usuarios_auth (socio_id, username, password_hash, password_must_change)
                VALUES (:socioId, :username, :passwordHash, true)
                """)
                .param("socioId", socioId)
                .param("username", adminUsername)
                .param("passwordHash", passwordEncoder.encode(adminInitialPassword))
                .update();

        log.warn("╔══════════════════════════════════════════════════════════╗");
        log.warn("║  ADMIN INICIAL CREADO — CAMBIA LA CONTRASEÑA YA         ║");
        log.warn("║  Usuario: {}                                         ║", adminUsername);
        log.warn("╚══════════════════════════════════════════════════════════╝");
    }
}
