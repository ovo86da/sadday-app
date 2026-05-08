# Flujo 13 — Alertas de infraestructura al administrador

## ¿Qué es este flujo?

El sistema envía automáticamente un email al administrador cuando detecta condiciones anómalas en la infraestructura interna — específicamente cuando la base de datos de geolocalización (GeoLite2) no existe o lleva demasiado tiempo sin actualizarse.

Esto complementa el monitoreo externo (UptimeRobot) que solo detecta si el servidor está caído. Este flujo detecta **fallas silenciosas**: servicios que funcionan pero están degradados.

---

## Componentes

| Componente | Rol |
|---|---|
| `SchedulerService` | Ejecuta los checks periódicos y al arrancar |
| `GeoIpService.getLastModified()` | Retorna cuándo fue modificado el `.mmdb` por última vez |
| `AdminAlertMailSender` | Envía el email de alerta al admin de forma asíncrona |
| `ADMIN_ALERT_EMAIL` | Env var con el email del destinatario (opcional) |

---

## Cuándo se ejecuta el check de GeoIP

El check corre en **dos momentos**:

1. **Al arrancar la aplicación** — detecta problemas inmediatamente sin esperar al lunes.
2. **Cada lunes a las 09:00** — revisión semanal continua.

---

## Lógica del check

```
doVerificarFrescuraGeoIp()
        │
        ├─ GEOIP_DB_PATH no configurado
        │       └─ log debug, termina (feature desactivada)
        │
        ├─ archivo no existe  →  sendGeoIpMissingAlert()
        │       └─ Email: "GeoLite2 no encontrado — verificar geoip-updater"
        │
        ├─ archivo existe, pero lastModified > 14 días
        │       └─  sendGeoIpStaleAlert(días)
        │               └─ Email: "GeoLite2 lleva N días sin actualizar"
        │
        └─ archivo existe y fresco  →  log debug "OK"
```

---

## Contenido de los emails

### GeoIP faltante

**Asunto:** `[Sadday] ALERTA — Base de datos GeoIP no encontrada`

Indica que el contenedor `geoip-updater` nunca escribió el archivo, o que `GEOIP_DB_PATH` apunta a un lugar equivocado. Incluye los pasos a revisar: logs del contenedor, credenciales MaxMind, montaje del volumen.

### GeoIP desactualizado

**Asunto:** `[Sadday] ALERTA — Base de datos GeoIP desactualizada (N días)`

Indica que el archivo existe pero `geoipupdate` dejó de actualizarlo. Incluye los pasos: logs del contenedor, conectividad con `updates.maxmind.com`, validez de la license key.

---

## Configuración

### Variable de entorno

| Variable | Descripción | Requerida |
|---|---|---|
| `ADMIN_ALERT_EMAIL` | Email del administrador que recibe las alertas | No — si no está configurada, los checks corren pero no envían email |

### `application.yml`

```yaml
sadday:
  admin:
    alert-email: ${ADMIN_ALERT_EMAIL:}
```

### Comportamiento si no está configurado

Si `ADMIN_ALERT_EMAIL` está vacío:
- El check **sí se ejecuta** (detecta el problema).
- El email **no se envía** (log de debug indicando que está omitido).
- No se lanza ninguna excepción — el sistema sigue funcionando.

Esto permite correr la app en local sin necesidad de configurar un email de admin.

---

## Estrategia de monitoreo completa (3 capas)

| Capa | Herramienta | Qué detecta | Configuración |
|---|---|---|---|
| 1 | UptimeRobot (externo, gratuito) | Servidor caído, backend no responde | Registrarse en uptimerobot.com, apuntar a `/actuator/health` |
| 2 | `AdminAlertMailSender` (este flujo) | Fallas internas silenciosas (GeoIP, etc.) | `ADMIN_ALERT_EMAIL` en `.env` de producción |
| 3 | Logs del servidor | Diagnóstico detallado post-falla | `docker-compose logs api --tail=200` |

Con las capas 1 y 2 activas, el administrador es notificado por email en todos los escenarios relevantes sin necesidad de revisar logs proactivamente.

---

## Extensibilidad

`AdminAlertMailSender` está diseñado para crecer. Se pueden agregar más métodos para futuros checks:

- Error persistente al escribir en S3/MinIO
- BD de PostgreSQL con espacio en disco bajo (via custom `HealthIndicator`)
- Certificado SSL próximo a vencer
- Cualquier condición que el scheduler pueda detectar

El patrón es siempre el mismo: el scheduler detecta → llama a `AdminAlertMailSender` → email asíncrono al admin.

---

## Cómo probar en desarrollo

El proyecto ya incluye **Mailpit** en el docker-compose local — atrapa todos los emails salientes y los muestra en una bandeja web en `http://localhost:8025`. No necesitas una cuenta de correo real para probar.

### Setup base (una sola vez)

Agrega estas variables a tu `.env` local:

```bash
ADMIN_ALERT_EMAIL=cualquier@cosa.com   # Mailpit atrapa todo, el valor no importa
```

Hay un endpoint exclusivo para disparar el check manualmente sin esperar al lunes:

```
POST /api/v1/admin/diagnostico/geoip
Autorización: Bearer <token de usuario con rol ADMIN>
```

---

### Escenario 1 — Archivo faltante

Simula que `geoip-updater` nunca descargó el archivo.

```bash
# .env local
GEOIP_DB_PATH=/tmp/geoip/GeoLite2-City.mmdb   # ruta inexistente
ADMIN_ALERT_EMAIL=cualquier@cosa.com
```

Reinicia el backend (el check corre al arrancar) o llama al endpoint:

```bash
curl -X POST http://localhost:8080/api/v1/admin/diagnostico/geoip \
  -H "Authorization: Bearer <token>"
```

**Resultado esperado en Mailpit:** email con asunto `[Sadday] ALERTA — Base de datos GeoIP no encontrada`.

---

### Escenario 2 — Archivo desactualizado

Simula que `geoipupdate` dejó de actualizar hace más de 14 días.

```bash
# Crea el archivo con fecha antigua
mkdir -p /tmp/geoip
touch /tmp/geoip/GeoLite2-City.mmdb
touch -t 202504010000 /tmp/geoip/GeoLite2-City.mmdb   # 1 abril 2026 (~30 días atrás)
```

```bash
# .env local
GEOIP_DB_PATH=/tmp/geoip/GeoLite2-City.mmdb
ADMIN_ALERT_EMAIL=cualquier@cosa.com
```

Llama al endpoint → **Resultado esperado en Mailpit:** email con asunto `[Sadday] ALERTA — Base de datos GeoIP desactualizada (30 días)`.

---

### Escenario 3 — Todo OK (sin alerta)

Verifica que cuando el archivo es fresco no se envía nada.

```bash
touch /tmp/geoip/GeoLite2-City.mmdb   # fecha actual = fresco
```

Llama al endpoint → **Resultado esperado:** ningún email en Mailpit, solo un log de debug en el backend (`verificarGeoIp: GeoLite2 actualizado hace 0 días — OK`).

---

### Verificar los logs del backend

Para ver qué está ocurriendo en el scheduler mientras pruebas:

```bash
docker-compose logs api -f | grep -i geoip
```

---

## Dónde está implementado

| Componente | Archivo |
|---|---|
| Check semanal + startup | `scheduler/SchedulerService.java` → `verificarFrescuraGeoIp()` / `verificarFrescuraGeoIpAlArrancar()` |
| Envío de alertas | `auth/service/AdminAlertMailSender.java` |
| Fecha de último update | `auth/service/GeoIpService.java` → `getLastModified()` |
| Config email admin | `application.yml` → `sadday.admin.alert-email` |
| Env var producción | `docker-compose.prod.yml` → `ADMIN_ALERT_EMAIL` |
