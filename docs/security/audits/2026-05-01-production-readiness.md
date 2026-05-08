# Revisión de readiness para producción — Sadday App

**Fecha:** 2026-05-01
**Alcance:** Gaps de seguridad y operación identificados al preparar el primer despliegue a producción. Complementa la auditoría de código del 2026-04-26.
**Metodología:** Revisión cruzada entre código, docker-compose, documentación existente y mejores prácticas de despliegue (OWASP, CIS Benchmarks).

---

## Resumen ejecutivo

La base de seguridad del código es sólida (ver auditoría 2026-04-26). Este documento se enfoca en los **gaps de infraestructura, operación y documentación** que deben resolverse antes del primer deploy a producción, y en los issues pendientes de auditorías anteriores que permanecen abiertos.

Se identificaron **3 bloqueantes**, **5 ALTO** y **4 MEDIO**. Ninguno es un fallo del código en sí — son gaps de configuración, procedimiento o documentación que en producción crean riesgos operativos reales.

Los bloqueantes deben resolverse antes del go-live. Los ALTO pueden resolverse en la primera semana post-deploy. Los MEDIO son mejoras para el primer mes.

---

## BLOQUEANTES (deben resolverse antes del primer deploy)

### P-01 — Sin procedimiento de rotación de secretos [NUEVO]

**Descripción:** No existe documentación sobre cómo rotar los secretos críticos del sistema: claves JWT (RSA), `TOTP_ENCRYPTION_KEY`, contraseña de BD, credenciales de S3. En producción, la rotación de secretos es una operación periódica y también de respuesta a incidentes.

Sin un procedimiento documentado:
- Una rotación mal ejecutada puede dejar el sistema inutilizable (tokens inválidos, 2FA quebrado).
- Ante un incidente (leak de credenciales), el tiempo de respuesta se alarga por falta de pasos claros.

**Resolución:** Crear `docs/security/architecture/secret-rotation.md` con comandos exactos y consideraciones de downtime para cada secreto.
**Estado:** RESUELTO (ver documento creado)

---

### P-02 — Sin guía de respuesta a incidentes [NUEVO]

**Descripción:** No existe ningún procedimiento documentado para escenarios de seguridad activos:
- `REFRESH_TOKEN_STOLEN` detectado en `security_events`
- Cuenta admin comprometida
- Brecha de base de datos

El sistema ya tiene los mecanismos (revocación de tokens, logout-all, auditoría), pero sin una guía el operador no sabe qué hacer ni en qué orden bajo presión.

**Resolución:** Crear `docs/security/architecture/incident-response.md`.
**Estado:** RESUELTO (ver documento creado)

---

### P-03 — Sin checklist de pre-deploy consolidado [NUEVO]

**Descripción:** Los pasos necesarios para el primer deploy a producción están dispersos en múltiples documentos (docker-compose, security-architecture, flujos) o directamente ausentes. No hay una lista unificada que un operador pueda seguir de principio a fin.

Riesgo: un paso omitido (por ejemplo, no crear el volumen `sadday-geoip-data` antes de levantar el stack) causa fallos en el arranque que son difíciles de diagnosticar bajo presión del go-live.

**Resolución:** Crear `docs/security/architecture/production-deployment-checklist.md`.
**Estado:** RESUELTO (ver documento creado)

---

## ALTO

### A-P01 — SPF / DKIM / DMARC no verificados en el dominio del club [PENDIENTE]

**Referencia:** G-04 (threat model 2026-04-26)
**Descripción:** Los emails de invitación, reset de contraseña y alertas de seguridad se envían desde AWS SES con el dominio del club. Sin SPF, DKIM y DMARC correctamente configurados:
- Los emails pueden terminar en spam, rompiendo flujos críticos (invitaciones, reset de password).
- Un atacante puede suplantar el dominio del club para phishing dirigido a los socios.

**Recomendación:**
1. Verificar en el DNS del dominio que existan registros SPF incluyendo `include:amazonses.com`.
2. Activar DKIM en la consola de AWS SES y agregar el registro CNAME al DNS.
3. Agregar registro DMARC: `v=DMARC1; p=quarantine; rua=mailto:admin@dominio.com`.
4. Verificar con `dig TXT dominio.com` y herramientas como MXToolbox.

**Estado:** PENDIENTE

---

### A-P02 — Nginx del frontend corre como root [PENDIENTE]

**Referencia:** F-09 (threat model 2026-04-26)
**Archivo:** `frontend/Dockerfile` (imagen `nginx:alpine`)
**Descripción:** `nginx:alpine` requiere root para hacer bind en el puerto 80. Si hay una vulnerabilidad en Nginx o en el servidor de archivos estáticos, el proceso comprometido tiene acceso root al contenedor.

**Recomendación:** Migrar a `nginxinc/nginx-unprivileged` (puerto 8080, usuario no-root) o usar una imagen base personalizada con usuario no privilegiado. Actualizar el `docker-compose.yml` del frontend para mapear el puerto interno correcto.

**Estado:** PENDIENTE

---

### A-P03 — GitHub Actions sin pin por SHA de commit [PENDIENTE]

**Referencia:** I-02 (threat model 2026-04-26)
**Archivos:** `.github/workflows/*.yml`
**Descripción:** Los workflows usan tags flotantes (`actions/checkout@v4`, `docker/build-push-action@v5`). Un tag puede ser movido por el mantenedor (o comprometido en un supply chain attack) apuntando a código malicioso que se ejecuta con los secrets del repositorio en el siguiente push.

**Recomendación:** Hacer pin de cada Action usada por su SHA de commit:
```yaml
# Antes
uses: actions/checkout@v4
# Después
uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
```
Dependabot ya monitorea Actions — con el pin, genera PRs cuando hay versión nueva.

**Estado:** PENDIENTE

---

### A-P04 — `TOTP_ENCRYPTION_KEY` con valor cero por defecto en local [PENDIENTE]

**Referencia:** A2 (auditoría 2026-04-26)
**Archivos:** `docker-compose.yml:101`, `.env.example`
**Descripción:** El default `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=` (32 bytes de ceros) facilita el desarrollo local sin configuración. Si por error se activa el perfil `local` en producción o si el `.env` de producción no define la variable, todos los TOTP secrets en BD son trivialmente descifrables con la clave cero.

**Recomendación:** Agregar un guard de arranque en `TotpService` o `SecurityProperties` que rechace la clave de baja entropía (todos-ceros o longitud incorrecta) en el perfil `prod`. Lanzar `IllegalStateException` antes de que la app esté lista.

**Estado:** PENDIENTE

---

### A-P05 — Log rotation no configurada [NUEVO]

**Descripción:** Los logs de la app se escriben en `/app/logs` (volumen `sadday-logs`). Docker también mantiene su propio log por contenedor (stdout/stderr). Sin rotación configurada, ambos crecen indefinidamente y pueden agotar el disco del servidor en producción.

**Recomendación:**
1. **Docker daemon:** Configurar `log-driver` y `max-size` / `max-file` en `/etc/docker/daemon.json`:
```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "50m",
    "max-file": "5"
  }
}
```
2. **App logs (`/app/logs`):** Configurar `logback-spring.xml` con `RollingFileAppender` (tamaño máximo + retención de 30 días).
3. Documentar la política de retención de logs (ver también E-04 del threat model: auditoría > 2 años).

**Estado:** PENDIENTE — documentado en checklist de pre-deploy

---

## MEDIO

### M-P01 — Sin códigos de recuperación para 2FA [PENDIENTE]

**Referencia:** A-11 (threat model 2026-04-26)
**Descripción:** Si el administrador pierde el dispositivo TOTP sin haber hecho backup del secret, queda bloqueado fuera del sistema permanentemente. Actualmente existe un flujo de "emergency reset 2FA" (FR-005) pero requiere un segundo admin para ejecutarlo. Si solo hay un admin, hay un punto único de fallo.

**Recomendación:** Implementar backup codes en el setup de 2FA (8-10 códigos de un solo uso). Prioridad alta para cuentas ADMIN, media para el resto.

**Estado:** PENDIENTE

---

### M-P02 — PII en `email_verification_tokens` en claro [PENDIENTE]

**Referencia:** A5 (auditoría 2026-04-26)
**Archivos:** `EmailVerificationService.java`, tabla `email_verification_tokens`
**Descripción:** Cédula, correo, teléfono, nombre y apellido se almacenan en texto claro durante 72 horas en la tabla de tokens. Si la BD es comprometida durante ese ventana, toda esa PII es legible sin necesidad de descifrado.

**Recomendación:** Cifrar los campos PII con AES-256-GCM (mismo patrón que `totp_secret`). Prioridad media — la ventana de exposición es corta y el scheduler limpia tokens expirados.

**Estado:** PENDIENTE

---

### M-P03 — Política de retención de auditoría no formalizada [PENDIENTE]

**Referencia:** E-04 (threat model 2026-04-26)
**Descripción:** La tabla `auditoria` crece indefinidamente (append-only por diseño). Sin una política de retención definida, en producción acumulará años de registros afectando el rendimiento de queries y el tamaño de backups.

**Recomendación:** Definir política: mantener 2 años en BD activa, archivar a S3 como JSON comprimido, eliminar de BD. Implementar como tarea mensual del scheduler o job manual documentado.

**Estado:** PENDIENTE

---

### M-P04 — DAST no implementado [PENDIENTE]

**Referencia:** audit 2026-04-26 (checklist CI/CD)
**Descripción:** El pipeline tiene SAST (Semgrep), SCA (Snyk, OWASP DC) e imagen scanning (Trivy), pero no tiene DAST (Dynamic Application Security Testing). DAST detecta vulnerabilidades en runtime que el análisis estático no puede ver (headers incorrectos en respuestas reales, redirecciones inseguras, endpoints no documentados).

**Recomendación:** Agregar OWASP ZAP en modo pasivo contra el entorno QA en el pipeline semanal (`security.yml`). El modo pasivo no requiere autenticación y detecta issues comunes sin riesgo de afectar datos.

**Estado:** PENDIENTE

---

## Issues previos aún abiertos (de auditoría 2026-04-26)

| ID | Descripción | Severidad | Estado |
|---|---|---|---|
| A2 | `TOTP_ENCRYPTION_KEY` default cero (ver A-P04 arriba) | ALTO | PENDIENTE |
| A3 | Contraseña inicial débil en QA (`Admin123!`) | ALTO | PENDIENTE |
| A5 | PII en `email_verification_tokens` en claro (ver M-P02 arriba) | ALTO | PENDIENTE |
| M13 | `actuator/info` expone metadata en local/QA | MEDIO | PENDIENTE |
| B20 | Hard delete de socio sin snapshot en `datos_anteriores` | BAJO | PENDIENTE |
| F-09 | Nginx frontend como root (ver A-P02 arriba) | ALTO | PENDIENTE |
| F-10 | MinIO QA con credenciales default | ALTO | PENDIENTE |
| G-04 | SPF/DKIM/DMARC (ver A-P01 arriba) | ALTO | PENDIENTE |
| I-02 | GitHub Actions sin SHA pin (ver A-P03 arriba) | ALTO | PENDIENTE |
| D-03 | Directivo baja umbrales sin doble confirmación | CRÍTICO | PARCIAL |

---

## Fortalezas confirmadas (sin cambios desde auditoría anterior)

- Argon2id con parámetros OWASP 2026 para passwords
- JWT RS256 con claves RSA separadas (encoder/decoder)
- Refresh tokens rotativos con detección de robo (revocación de todos al detectar reuso)
- TOTP cifrado con AES-256-GCM
- Auditoría append-only asíncrona
- Rate limiting Bucket4j + Caffeine (con límite de 10.000 IPs)
- CSRF doble barrera: `SameSite=Strict` + header `X-Sadday-Client`
- CSP estricto en backend y frontend
- Pipeline con Trivy + cosign keyless + SBOM CycloneDX + Snyk + OWASP DC
- Cleanup automático de tokens expirados (scheduler horario + startup)
- GeoIP actualizado automáticamente sin downtime (FR-011, FR-012)
- Alertas de infraestructura al admin por email (FR-012)
