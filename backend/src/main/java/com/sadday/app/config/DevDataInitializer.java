package com.sadday.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Crea usuarios de prueba al iniciar la aplicación en perfil local.
 *
 * <pre>
 * username      contraseña      rol
 * ──────────────────────────────────
 * admin         Admin123!       Admin
 * secretaria    Secret123!      Secretaria
 * directivo     Direct123!      Directivo
 * socio         Socio123!       Socio
 * </pre>
 *
 * Solo se ejecuta si no existen ya los usuarios. Solo activo en el perfil {@code local}.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;

    private record DevUser(
            String username,
            String password,
            String rolNombre,
            String nombre,
            String apellido,
            String cedula,
            String correo,
            String telefono
    ) {}

    @Override
    public void run(String... args) {
        List<DevUser> users = List.of(
                new DevUser("admin",       "Admin123!",    "Admin",      "Admin",      "Sadday",    "0000000001", "admin@sadday.local",       "0991111111"),
                new DevUser("secretaria",  "Secret123!",   "Secretaria", "Ana",        "García",    "0000000002", "secretaria@sadday.local",  "0992222222"),
                new DevUser("directivo",   "Direct123!",   "Directivo",  "Carlos",     "Montero",   "0000000003", "directivo@sadday.local",   "0993333333"),
                new DevUser("directivo2",  "Direct2123!",  "Directivo",  "Sofía",      "Ríos",      "0000000007", "directivo2@sadday.local",  "0997777777"),
                new DevUser("socio",       "Socio123!",    "Socio",      "Luis",       "Torres",    "0000000004", "socio@sadday.local",       "0994444444"),
                new DevUser("socio2",      "Socio2123!",   "Socio",      "María",      "Paredes",   "0000000005", "socio2@sadday.local",      "0995555555"),
                new DevUser("socio3",      "Socio3123!",   "Socio",      "Andrés",     "Vega",      "0000000006", "socio3@sadday.local",      "0996666666")
        );

        Integer tipoSocioId = jdbcClient
                .sql("SELECT id FROM tipo_socio_club WHERE nombre = 'Socio Activo'")
                .query(Integer.class)
                .single();

        Integer estadoHabilitadoId = jdbcClient
                .sql("SELECT id FROM estado_habilitacion WHERE nombre = 'Habilitado'")
                .query(Integer.class)
                .single();

        for (DevUser u : users) {
            boolean exists = jdbcClient
                    .sql("SELECT COUNT(*) FROM usuarios_auth WHERE username = :username")
                    .param("username", u.username())
                    .query(Long.class)
                    .single() > 0;

            if (exists) {
                log.info("Usuario '{}' ya existe — omitido", u.username());
                continue;
            }

            Integer rolId = jdbcClient
                    .sql("SELECT id FROM roles_sistema WHERE nombre = :nombre")
                    .param("nombre", u.rolNombre())
                    .query(Integer.class)
                    .single();

            UUID socioId = UUID.randomUUID();
            jdbcClient.sql("""
                    INSERT INTO socios (id, nombre, apellido, cedula, correo, telefono,
                        fecha_nacimiento, fecha_ingreso, estado_habilitacion_id,
                        tipo_socio_id, rol_sistema_id, estado_acceso_id)
                    VALUES (:id, :nombre, :apellido, :cedula, :correo, :telefono,
                        :fechaNac, :fechaIng, :estadoId, :tipoId, :rolId, :estadoAccesoId)
                    """)
                    .param("id", socioId)
                    .param("nombre", u.nombre())
                    .param("apellido", u.apellido())
                    .param("cedula", u.cedula())
                    .param("correo", u.correo())
                    .param("telefono", u.telefono())
                    .param("fechaNac", LocalDate.of(1990, 1, 1))
                    .param("fechaIng", LocalDate.now())
                    .param("estadoId", estadoHabilitadoId)
                    .param("tipoId", tipoSocioId)
                    .param("rolId", rolId)
                    .param("estadoAccesoId", 1)
                    .update();

            jdbcClient.sql("""
                    INSERT INTO usuarios_auth (socio_id, username, password_hash)
                    VALUES (:socioId, :username, :passwordHash)
                    """)
                    .param("socioId", socioId)
                    .param("username", u.username())
                    .param("passwordHash", passwordEncoder.encode(u.password()))
                    .update();

            log.info("✅ Usuario de desarrollo creado: {} / {} (rol={})", u.username(), u.password(), u.rolNombre());
        }
    }
}
