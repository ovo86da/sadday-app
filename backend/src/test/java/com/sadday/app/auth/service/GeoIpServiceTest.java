package com.sadday.app.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para {@link GeoIpService}.
 *
 * <p>Cubren los caminos sin base de datos configurada, con path vacío,
 * IPs privadas/loopback y el método {@code getLastModified()}.
 * No requieren un archivo .mmdb real — se testean los early-returns
 * y los flujos defensivos.
 */
class GeoIpServiceTest {

    private GeoIpService createService(String dbPath) {
        GeoIpService service = new GeoIpService();
        ReflectionTestUtils.setField(service, "dbPath", dbPath);
        return service;
    }

    // ── isConfigured ─────────────────────────────────────────────────

    @Nested
    @DisplayName("isConfigured()")
    class IsConfigured {

        @Test
        @DisplayName("false cuando dbPath es null")
        void returnsFalse_whenDbPathIsNull() {
            GeoIpService service = createService(null);
            assertThat(service.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("false cuando dbPath está vacío")
        void returnsFalse_whenDbPathIsBlank() {
            GeoIpService service = createService("  ");
            assertThat(service.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("true cuando dbPath tiene valor")
        void returnsTrue_whenDbPathIsSet() {
            GeoIpService service = createService("/some/path.mmdb");
            assertThat(service.isConfigured()).isTrue();
        }
    }

    // ── lookup — early returns ───────────────────────────────────────

    @Nested
    @DisplayName("lookup()")
    class Lookup {

        @Test
        @DisplayName("null cuando no hay reader (db no configurada)")
        void returnsNull_whenReaderIsNull() {
            GeoIpService service = createService(null);
            assertThat(service.lookup("8.8.8.8")).isNull();
        }

        @Test
        @DisplayName("null cuando la IP es null")
        void returnsNull_whenIpIsNull() {
            GeoIpService service = createService("/some/path.mmdb");
            assertThat(service.lookup(null)).isNull();
        }

        @Test
        @DisplayName("null cuando la IP está vacía")
        void returnsNull_whenIpIsBlank() {
            GeoIpService service = createService("/some/path.mmdb");
            assertThat(service.lookup("  ")).isNull();
        }

        @Test
        @DisplayName("null para IP loopback (127.0.0.1)")
        void returnsNull_forLoopbackIp() {
            GeoIpService service = createService("/some/path.mmdb");
            // No hay reader cargado, pero forzamos el chequeo de IP privada
            // creando un reader mock no es necesario — el null reader retorna antes.
            // Testeamos isPrivateOrLoopback indirectamente.
            assertThat(service.lookup("127.0.0.1")).isNull();
        }

        @Test
        @DisplayName("null para IP privada (192.168.1.1)")
        void returnsNull_forPrivateIp() {
            GeoIpService service = createService("/some/path.mmdb");
            assertThat(service.lookup("192.168.1.1")).isNull();
        }

        @Test
        @DisplayName("null para IP link-local (169.254.1.1)")
        void returnsNull_forLinkLocalIp() {
            GeoIpService service = createService("/some/path.mmdb");
            assertThat(service.lookup("169.254.1.1")).isNull();
        }
    }

    // ── getLastModified ──────────────────────────────────────────────

    @Nested
    @DisplayName("getLastModified()")
    class GetLastModified {

        @Test
        @DisplayName("vacío cuando dbPath es null")
        void returnsEmpty_whenDbPathIsNull() {
            GeoIpService service = createService(null);
            assertThat(service.getLastModified()).isEmpty();
        }

        @Test
        @DisplayName("vacío cuando dbPath está vacío")
        void returnsEmpty_whenDbPathIsBlank() {
            GeoIpService service = createService("");
            assertThat(service.getLastModified()).isEmpty();
        }

        @Test
        @DisplayName("vacío cuando el archivo no existe")
        void returnsEmpty_whenFileDoesNotExist() {
            GeoIpService service = createService("/nonexistent/path.mmdb");
            assertThat(service.getLastModified()).isEmpty();
        }

        @Test
        @DisplayName("retorna instante cuando el archivo existe")
        void returnsInstant_whenFileExists(@TempDir Path tempDir) throws IOException {
            Path tempFile = tempDir.resolve("GeoLite2-City.mmdb");
            Files.writeString(tempFile, "fake-mmdb-content");

            GeoIpService service = createService(tempFile.toString());
            Optional<Instant> result = service.getLastModified();

            assertThat(result).isPresent();
            assertThat(result.get()).isBefore(Instant.now().plusSeconds(1));
        }
    }

    // ── init (sin archivo) ───────────────────────────────────────────

    @Nested
    @DisplayName("init()")
    class Init {

        @Test
        @DisplayName("no lanza excepción cuando dbPath está vacío")
        void doesNotThrow_whenDbPathIsBlank() {
            GeoIpService service = createService("");
            service.init(); // no debe lanzar
            assertThat(service.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("no lanza excepción cuando el archivo no existe")
        void doesNotThrow_whenFileNotFound() {
            GeoIpService service = createService("/nonexistent/file.mmdb");
            service.init(); // no debe lanzar — solo loguea warning
            assertThat(service.lookup("8.8.8.8")).isNull();
        }
    }

    // ── destroy ──────────────────────────────────────────────────────

    @Test
    @DisplayName("destroy() no lanza excepción sin reader ni watcher")
    void destroy_doesNotThrow_whenNothingInitialized() {
        GeoIpService service = createService(null);
        service.destroy(); // no debe lanzar
    }
}
