package com.sadday.app.auth.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Geolocalización de IPs usando MaxMind GeoLite2 (base de datos local, sin
 * llamadas externas).
 *
 * <p>Si la base de datos no está configurada o no existe, todos los lookups
 * devuelven null sin lanzar excepciones. Las IPs privadas/loopback también
 * devuelven null.
 *
 * <p>Hot-reload: un hilo daemon monitorea el archivo con {@link WatchService}.
 * Cuando {@code geoipupdate} reemplaza el {@code .mmdb}, el reader se recarga
 * automáticamente sin reiniciar el backend.
 *
 * <p>Para habilitar: descargar GeoLite2-City.mmdb de maxmind.com (cuenta gratuita)
 * y configurar {@code sadday.geo.db-path} con la ruta al archivo.
 */
@Slf4j
@Component
public class GeoIpService {

    @Value("${sadday.geo.db-path:}")
    private String dbPath;

    private final AtomicReference<DatabaseReader> readerRef = new AtomicReference<>();
    private Thread watcherThread;

    @PostConstruct
    public void init() {
        if (dbPath == null || dbPath.isBlank()) {
            log.info("GeoIpService: sadday.geo.db-path no configurado — geolocalización deshabilitada");
            return;
        }
        loadReader();
        startWatcher();
    }

    @PreDestroy
    public void destroy() {
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        DatabaseReader reader = readerRef.get();
        if (reader != null) {
            try { reader.close(); } catch (Exception ignored) {}
        }
    }

    public record GeoLocation(String countryCode, String city) {}

    /**
     * Realiza el lookup de la IP.
     *
     * @return {@link GeoLocation} con country_code + city, o {@code null} si la IP
     *         es privada, la BD no está disponible, o el lookup falla.
     */
    public GeoLocation lookup(String ipAddress) {
        DatabaseReader reader = readerRef.get();
        if (reader == null || ipAddress == null || ipAddress.isBlank()) return null;
        if (isPrivateOrLoopback(ipAddress)) return null;
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            CityResponse response = reader.city(addr);
            String countryCode = response.getCountry().getIsoCode();
            String city = response.getCity().getName();
            return new GeoLocation(countryCode, city);
        } catch (Exception e) {
            log.debug("GeoIpService: lookup fallido para {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }

    /**
     * Retorna el instante de la última modificación del archivo .mmdb,
     * o vacío si el path no está configurado o el archivo no existe.
     * Usado por el scheduler para verificar frescura.
     */
    public Optional<Instant> getLastModified() {
        if (dbPath == null || dbPath.isBlank()) return Optional.empty();
        File f = new File(dbPath);
        if (!f.exists()) return Optional.empty();
        return Optional.of(Instant.ofEpochMilli(f.lastModified()));
    }

    public boolean isConfigured() {
        return dbPath != null && !dbPath.isBlank();
    }

    // ── Carga y hot-reload ────────────────────────────────────────────────────

    private void loadReader() {
        File dbFile = new File(dbPath);
        if (!dbFile.exists()) {
            log.warn("GeoIpService: archivo GeoLite2 no encontrado en '{}' — geolocalización deshabilitada", dbPath);
            return;
        }
        try {
            DatabaseReader newReader = new DatabaseReader.Builder(dbFile).build();
            DatabaseReader old = readerRef.getAndSet(newReader);
            if (old != null) {
                try { old.close(); } catch (Exception ignored) {}
                log.info("GeoIpService: base de datos GeoLite2 recargada desde '{}'", dbPath);
            } else {
                log.info("GeoIpService: base de datos GeoLite2 cargada desde '{}'", dbPath);
            }
        } catch (Exception e) {
            log.error("GeoIpService: error cargando GeoLite2 '{}': {}", dbPath, e.getMessage(), e);
        }
    }

    private void startWatcher() {
        Path filePath = Paths.get(dbPath).toAbsolutePath();
        Path dir      = filePath.getParent();
        if (dir == null) return;

        String filename = filePath.getFileName().toString();

        watcherThread = new Thread(() -> {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                dir.register(watcher,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);
                log.info("GeoIpService: vigilando cambios en '{}'", dir);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key;
                    try {
                        key = watcher.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    boolean changed = key.pollEvents().stream()
                            .map(e -> ((Path) e.context()).getFileName().toString())
                            .anyMatch(filename::equals);

                    key.reset();

                    if (changed) {
                        log.info("GeoIpService: cambio detectado en '{}' — recargando en 2 s...", filename);
                        try {
                            Thread.sleep(2_000); // espera a que geoipupdate termine de escribir
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        loadReader();
                    }
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.error("GeoIpService: error en file watcher: {}", e.getMessage(), e);
                }
            }
        }, "geoip-watcher");

        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private boolean isPrivateOrLoopback(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }
}
