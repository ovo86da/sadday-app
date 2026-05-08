# Flujo 12 — Actualización automática de la base de datos GeoIP

## ¿Qué es este flujo?

El sistema detecta desde qué país se conecta cada usuario al hacer login. Para eso necesita una base de datos de IPs (`GeoLite2-City.mmdb`) que mapea direcciones IP a países y ciudades. Esta base de datos la mantiene MaxMind y publica versiones nuevas dos veces por mes.

Este flujo explica cómo el sistema se mantiene actualizado automáticamente sin intervención manual y sin reiniciar el backend.

---

## Componentes involucrados

| Componente | Rol |
|---|---|
| `geoip-updater` (contenedor) | Descarga la BD actualizada de MaxMind cada 7 días |
| `geoip-data` (volumen Docker) | Almacena el archivo `.mmdb` compartido entre contenedores |
| `GeoIpService` (backend) | Carga el archivo al iniciar y lo recarga automáticamente cuando cambia |
| `WatchService` (hilo daemon) | Monitorea el archivo en disco y dispara el hot-reload |

---

## Cómo funciona paso a paso

### 1. Arranque inicial

Cuando el backend inicia, `GeoIpService.init()` hace dos cosas:
1. Carga el archivo `GeoLite2-City.mmdb` del path configurado en `GEOIP_DB_PATH` (`/geoip/GeoLite2-City.mmdb`).
2. Arranca un hilo daemon `geoip-watcher` que monitorea el directorio `/geoip/` usando `WatchService` de Java.

### 2. Descarga semanal automática

El contenedor `geoip-updater` (`ghcr.io/maxmind/geoipupdate`) corre indefinidamente con un ciclo interno de **168 horas** (7 días):
1. Se conecta a la API de MaxMind con las credenciales configuradas.
2. Comprueba si hay una versión más nueva que la que tiene en el volumen.
3. Si la hay: descarga y reemplaza `GeoLite2-City.mmdb` en el volumen compartido.
4. Si ya está actualizada: no descarga nada y espera las próximas 168 h.

### 3. Hot-reload sin downtime

En cuanto `geoipupdate` termina de escribir el nuevo archivo:
1. El `WatchService` detecta el evento `ENTRY_MODIFY` en el directorio `/geoip/`.
2. Espera **2 segundos** para asegurar que la escritura esté completa.
3. Llama a `loadReader()`: instancia un nuevo `DatabaseReader` con el archivo nuevo.
4. Hace `AtomicReference.getAndSet(newReader)`: reemplaza el reader anterior de forma atómica (thread-safe).
5. Cierra el reader anterior (`old.close()`).

Durante el proceso (esos ~2 s), los lookups de IP siguen respondiendo con el reader anterior — **cero downtime, cero errores**.

```
[geoipupdate] escribe GeoLite2-City.mmdb
        │
        ▼  WatchService detecta ENTRY_MODIFY
[hilo geoip-watcher]
        │
        ▼  espera 2 s (escritura completa)
        │
        ▼  new DatabaseReader(file)
        │
        ▼  AtomicReference.getAndSet(newReader)  ← atómico, thread-safe
        │
        ▼  old.close()
[API sirve con BD actualizada]
```

---

## Configuración en producción

Variables de entorno requeridas (en `.env` del servidor):

```bash
MAXMIND_ACCOUNT_ID=123456       # ID de cuenta en maxmind.com (gratuita)
MAXMIND_LICENSE_KEY=xxxx...     # License key generada en el panel
```

El volumen `sadday-geoip-data` debe existir antes del primer deploy:

```bash
docker volume create sadday-backend_sadday-geoip-data
```

En el primer arranque, si `geoipupdate` aún no terminó de descargar, `GeoIpService` arranca sin geolocalización (retorna `null` sin error). Una vez que el contenedor descarga el archivo, el watcher lo detecta y habilita la geolocalización automáticamente.

---

## Configuración en desarrollo (local)

En local la geolocalización no es necesaria para desarrollar. El servicio `geoip-updater` está configurado con un **perfil Docker** opcional llamado `geoip`.

Para activarlo en local (requiere credenciales MaxMind reales en `.env`):

```bash
docker-compose --profile geoip up
```

Sin el perfil, la app funciona normalmente — los campos `country_code` y `city` en `security_events` quedan en `null`.

---

## ¿Qué pasa si MaxMind no está disponible?

- Si `geoipupdate` no puede conectarse a MaxMind (sin internet, credenciales inválidas): el archivo `.mmdb` anterior sigue intacto en el volumen, el watcher no detecta cambios, y la app sigue usando la versión que tiene.
- Si el archivo no existe o está corrupto al arrancar: `GeoIpService` arranca con el reader en `null` — todos los lookups retornan `null`, sin excepciones, sin afectar el login.
- Si el archivo cambia pero está corrupto: `loadReader()` lanza excepción interna, loggea el error, y el `AtomicReference` conserva el reader anterior válido.

---

## Dónde está implementado

| Componente | Archivo |
|---|---|
| Hot-reload (backend) | `auth/service/GeoIpService.java` |
| Servicio actualizador (local) | `docker-compose.yml` → servicio `geoip-updater` (perfil `geoip`) |
| Servicio actualizador (prod) | `docker-compose.prod.yml` → servicio `geoip-updater` |
| Configuración del path | `application.yml` → `sadday.geo.db-path: ${GEOIP_DB_PATH:}` |
