# FR-012 — Alertas de infraestructura al administrador por email

**Fecha de solicitud:** 2026-05-01
**Fecha de implementación:** 2026-05-01
**Estado:** Implementado (ver [Registro de implementación](#registro-de-implementación))
**Prioridad:** Media
**Área:** Infraestructura / Observabilidad

---

## Contexto y motivación

El sistema no tiene ningún mecanismo para notificar al administrador cuando algo falla en la infraestructura interna. En producción, los errores silenciosos (ej: `geoipupdate` sin credenciales, BD de GeoIP desactualizada) solo se detectan revisando logs manualmente.

La capa de monitoreo externo (UptimeRobot) cubre que el servidor esté UP, pero no detecta fallas internas que no tumban el servicio — como que la geolocalización lleve semanas sin actualizarse.

**Objetivo:** aprovechar el `JavaMailSender` ya configurado para enviar emails proactivos al admin cuando el scheduler detecte condiciones anómalas, sin depender de revisión manual de logs.

---

## Cambios solicitados

### 1. Configuración: email del administrador

Nueva propiedad `sadday.admin.alert-email` leída del entorno `ADMIN_ALERT_EMAIL`. Si está vacío, las alertas se omiten silenciosamente (el sistema no falla).

### 2. `AdminAlertMailSender` (nuevo componente)

Componente `@Async` análogo a `SecurityAlertMailSender` pero para alertas de infraestructura dirigidas al admin (no a usuarios).

Alertas implementadas:

| Método | Cuándo se usa |
|---|---|
| `sendGeoIpMissingAlert()` | El archivo `.mmdb` no existe — nunca se descargó |
| `sendGeoIpStaleAlert(days)` | El archivo tiene más de 14 días sin actualizarse |

### 3. Chequeo semanal de frescura del GeoIP (`SchedulerService`)

Nueva tarea `@Scheduled` que corre cada **lunes a las 9:00** y también al **arrancar la aplicación**:

1. Si `GEOIP_DB_PATH` no está configurado → no hace nada (feature desactivada).
2. Si el archivo no existe → `sendGeoIpMissingAlert()`.
3. Si el archivo existe pero tiene más de **14 días** sin modificarse → `sendGeoIpStaleAlert(days)`.
4. Si está fresco → solo log de debug.

### 4. `GeoIpService`: exponer fecha de última actualización

Nuevo método `getLastModified()` que retorna el `Instant` de la última modificación del `.mmdb`, o `Optional.empty()` si el archivo no existe o no hay path configurado.

---

## Flujo de alerta

```
[SchedulerService] — cada lunes 9:00 / al arrancar
        │
        ▼  GeoIpService.getLastModified()
        │
        ├─ Optional.empty() → sendGeoIpMissingAlert()
        │
        └─ días > 14  → sendGeoIpStaleAlert(días)
                │
                ▼  @Async
        [AdminAlertMailSender]
                │
                ▼  JavaMailSender.send()
        [Email al ADMIN_ALERT_EMAIL]
```

---

## Archivos afectados

### Backend

| Archivo | Cambio |
|---------|--------|
| `application.yml` | Nueva propiedad `sadday.admin.alert-email: ${ADMIN_ALERT_EMAIL:}` |
| `auth/service/AdminAlertMailSender.java` | Nuevo componente — alertas de infraestructura al admin |
| `auth/service/GeoIpService.java` | Nuevo método `getLastModified(): Optional<Instant>` |
| `scheduler/SchedulerService.java` | Nueva tarea `verificarFrescuraGeoIp()` — `@Scheduled` lunes 9h + `@EventListener` startup |

### Infraestructura

| Archivo | Cambio |
|---------|--------|
| `docker-compose.prod.yml` | Nueva env var `ADMIN_ALERT_EMAIL` en servicio `api` |

---

## Variables de entorno

| Variable | Descripción | Obligatoria |
|---|---|---|
| `ADMIN_ALERT_EMAIL` | Email del administrador que recibe alertas de infra | No — si no está, alertas desactivadas |

---

## Notas de diseño

- Si `ADMIN_ALERT_EMAIL` no está configurado, no se lanza ninguna excepción — las alertas se omiten con un log de debug.
- El chequeo al arrancar permite detectar problemas en el primer boot del servidor, sin esperar al lunes.
- El umbral de 14 días (2 semanas) es conservador: MaxMind publica cada ~2 semanas, así que una BD de 14 días está exactamente en el límite.
- Las alertas son `@Async` para no bloquear el hilo del scheduler.
- Extensible: se pueden agregar más métodos a `AdminAlertMailSender` para futuros checks (espacio en disco, errores de S3, etc.).

---

## Registro de implementación

- `application.yml`: propiedad `sadday.admin.alert-email`.
- `AdminAlertMailSender.java`: componente nuevo con `sendGeoIpMissingAlert()` y `sendGeoIpStaleAlert()`.
- `GeoIpService.java`: método `getLastModified()`.
- `SchedulerService.java`: tarea `verificarFrescuraGeoIp()` con `@Scheduled` + `@EventListener`.
- `docker-compose.prod.yml`: env var `ADMIN_ALERT_EMAIL` en servicio `api`.
