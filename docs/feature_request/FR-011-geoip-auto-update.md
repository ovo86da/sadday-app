# FR-011 — Actualización automática de base de datos GeoIP sin downtime

**Fecha de solicitud:** 2026-05-01
**Fecha de implementación:** 2026-05-01
**Estado:** Implementado (ver [Registro de implementación](#registro-de-implementación))
**Prioridad:** Media
**Área:** Infraestructura / Seguridad

---

## Contexto y motivación

El sistema usa **MaxMind GeoLite2** para geolocalizar las IPs de login y detectar accesos desde países inusuales (`country_challenge`). Esta base de datos es un archivo binario `.mmdb` que MaxMind actualiza dos veces por mes (primer y tercer martes).

**Problemas con el estado anterior:**

1. El archivo `.mmdb` se cargaba una sola vez en `@PostConstruct` — actualizar la BD requería **reiniciar el backend**.
2. No había ningún mecanismo automático de descarga — el archivo debía actualizarse manualmente.
3. Si el archivo no estaba configurado, la geolocalización simplemente no funcionaba sin alerta visible.

Operar con una BD de IPs desactualizada degrada la funcionalidad de detección de país inusual: IPs nuevas o reasignadas pueden dar falsos negativos o positivos.

---

## Cambios solicitados

### 1. Hot-reload sin reinicio (backend)

`GeoIpService` debe detectar automáticamente cuando el archivo `.mmdb` cambia en disco y recargar el `DatabaseReader` sin interrumpir el servicio.

**Requisitos:**
- Hilo daemon (`WatchService`) que monitorea el directorio del archivo.
- Cuando detecta `ENTRY_MODIFY` o `ENTRY_CREATE` sobre el `.mmdb`, recarga el reader.
- Thread-safety: `AtomicReference<DatabaseReader>` en lugar de campo simple.
- Libera el reader anterior (`close()`) al reemplazarlo.
- Delay de 2 s antes de recargar para asegurar que la escritura del archivo esté completa.
- `@PreDestroy` interrumpe el hilo watcher al apagar la app.

### 2. Contenedor de actualización automática (infraestructura)

Agregar el servicio `geoip-updater` a docker-compose usando la imagen oficial `ghcr.io/maxmind/geoipupdate`, que:
- Se autentica con MaxMind usando `MAXMIND_ACCOUNT_ID` + `MAXMIND_LICENSE_KEY`.
- Descarga `GeoLite2-City` al volumen compartido `geoip-data`.
- Se ejecuta cada **168 horas** (7 días) de forma continua (`GEOIPUPDATE_FREQUENCY=168`).
- Escribe el archivo en `/geoip/GeoLite2-City.mmdb` — el mismo path que monta el backend.

**En local (docker-compose.yml):** el servicio usa el perfil `geoip` para ser opcional (los devs no necesitan credenciales MaxMind para correr la app).

**En producción (docker-compose.prod.yml):** el servicio corre siempre con `restart: unless-stopped`.

---

## Flujo completo post-implementación

```
[geoipupdate container] — cada 7 días
        │
        ▼ escribe GeoLite2-City.mmdb en volumen compartido
[geoip-data volume]
        │
        ▼ WatchService detecta ENTRY_MODIFY
[GeoIpService — hilo watcher]
        │
        ▼ recarga DatabaseReader en AtomicReference (sin reinicio)
[API — geolocalización actualizada]
```

---

## Archivos afectados

### Backend

| Archivo | Cambio |
|---------|--------|
| `auth/service/GeoIpService.java` | Reemplaza campo `DatabaseReader` por `AtomicReference`; agrega `loadReader()`, `startWatcher()`, `@PreDestroy destroy()` |

### Infraestructura

| Archivo | Cambio |
|---------|--------|
| `docker-compose.yml` | Servicio `geoip-updater` (perfil `geoip`); volumen `geoip-data`; vars `GEOIP_DB_PATH`, `MAXMIND_*` en `api` |
| `docker-compose.prod.yml` | Servicio `geoip-updater` (always-on); volumen `geoip-data` externo; vars de entorno |

---

## Variables de entorno requeridas en producción

| Variable | Descripción |
|---|---|
| `MAXMIND_ACCOUNT_ID` | ID de cuenta MaxMind (gratuita en maxmind.com) |
| `MAXMIND_LICENSE_KEY` | License key generada en el panel de MaxMind |
| `GEOIP_DB_PATH` | Seteado automáticamente a `/geoip/GeoLite2-City.mmdb` en el compose |

---

## Notas de diseño

- El hot-reload es eventual: hay un delay de ~2 s entre que `geoipupdate` termina de escribir y que el backend recarga. Durante esos 2 s, el reader anterior sigue respondiendo — sin error ni downtime.
- Si MaxMind no tiene una versión nueva (ya está actualizado), `geoipupdate` no descarga nada ni sobreescribe el archivo, por lo que el watcher no dispara.
- En local, sin el perfil `geoip`, la app funciona normalmente sin geolocalización (misma degradación elegante que antes).
- La cuenta GeoLite2 de MaxMind es **gratuita** — solo requiere registro.

---

## Registro de implementación

- Backend: `GeoIpService.java` refactorizado con `AtomicReference`, `WatchService` daemon thread, `@PreDestroy`.
- `docker-compose.yml`: servicio `geoip-updater` (perfil `geoip`), volumen `geoip-data`, env vars en `api`.
- `docker-compose.prod.yml`: servicio `geoip-updater` always-on, volumen externo `sadday-geoip-data`.
