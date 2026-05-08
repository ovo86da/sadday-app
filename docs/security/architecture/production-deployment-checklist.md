# Checklist de despliegue a producción — Sadday App

**Última actualización:** 2026-05-01
**Audiencia:** Operador que ejecuta el primer deploy o un re-deploy mayor.

Sigue este documento en orden. Marca cada item antes de continuar al siguiente. Un item omitido puede causar fallos en el arranque o vulnerabilidades en producción.

---

## Fase 1 — Preparación del servidor

### 1.1 Sistema operativo y Docker

- [ ] Ubuntu 22.04 LTS o Debian 12 (recomendado). Actualizaciones de seguridad aplicadas: `apt update && apt upgrade -y`
- [ ] Docker Engine instalado (no Docker Desktop). Versión mínima: 24.x
- [ ] `docker compose` plugin instalado (no el binario legacy `docker-compose`)
- [ ] El usuario de deploy pertenece al grupo `docker` — no ejecutar como root
- [ ] Firewall configurado: solo puertos 22 (SSH), 80 (HTTP→redirect), 443 (HTTPS) abiertos al exterior. Puerto 8080, 5432, 9000/9001 **cerrados**

### 1.2 Log rotation del daemon Docker

Crear o editar `/etc/docker/daemon.json`:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "50m",
    "max-file": "5"
  }
}
```

Aplicar: `sudo systemctl restart docker`

- [ ] `/etc/docker/daemon.json` configurado con límites de log

---

## Fase 2 — Secretos y claves

### 2.1 Claves RSA para JWT

```bash
# Generar par de claves RSA-4096
openssl genrsa -out private.pem 4096
openssl rsa -in private.pem -pubout -out public.pem

# Mover a ruta segura fuera del repo
sudo mkdir -p /opt/sadday/keys
sudo mv private.pem public.pem /opt/sadday/keys/
sudo chmod 600 /opt/sadday/keys/private.pem
sudo chmod 644 /opt/sadday/keys/public.pem
sudo chown root:docker /opt/sadday/keys/private.pem
```

- [ ] Claves RSA generadas y almacenadas en `/opt/sadday/keys/` (fuera del repo)
- [ ] `private.pem` con permisos `600` — no legible por otros usuarios
- [ ] Las claves NO están en el repositorio Git

### 2.2 Clave de cifrado TOTP

```bash
# Genera 32 bytes aleatorios en Base64 (AES-256)
openssl rand -base64 32
```

- [ ] `TOTP_ENCRYPTION_KEY` generada con `openssl rand -base64 32` — no usar el valor por defecto de ceros
- [ ] Guardada solo en el `.env` del servidor, nunca en el repo

### 2.3 Contraseña de base de datos

```bash
openssl rand -base64 32
```

- [ ] `DB_PASSWORD` generada con alta entropía
- [ ] Distinta a cualquier contraseña usada en staging o local

### 2.4 Contraseña inicial del admin

```bash
openssl rand -base64 16
```

- [ ] `ADMIN_INITIAL_PASSWORD` seteada en el `.env` — nunca usar el default `Admin123!`
- [ ] Anotada de forma segura (gestor de contraseñas) — se usará solo en el primer login

### 2.5 Credenciales S3 / AWS

- [ ] IAM user creado con permisos mínimos: solo `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject` sobre el bucket específico
- [ ] `AWS_ACCESS_KEY_ID` y `AWS_SECRET_ACCESS_KEY` (o equivalentes Lightsail) seteados en `.env`
- [ ] Bucket S3 con bloqueo de acceso público activado

### 2.6 Credenciales MaxMind GeoIP

- [ ] Cuenta gratuita creada en maxmind.com
- [ ] `MAXMIND_ACCOUNT_ID` y `MAXMIND_LICENSE_KEY` seteados en `.env`

### 2.7 Email del administrador para alertas

- [ ] `ADMIN_ALERT_EMAIL` seteado en `.env` con el email real del administrador del sistema

### 2.8 Verificación del `.env`

```bash
# Variables obligatorias que deben tener valor (no vacías)
grep -E "^(DB_PASSWORD|DB_USER|DB_NAME|JWT_PRIVATE_KEY_PATH|JWT_PUBLIC_KEY_PATH|TOTP_ENCRYPTION_KEY|ADMIN_INITIAL_PASSWORD|APP_URL|MAIL_FROM|S3_BUCKET|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|JWT_ISSUER)=" .env | grep "=$"
# El comando anterior no debe mostrar ninguna línea (ninguna variable vacía)
```

- [ ] Todas las variables obligatorias tienen valor

---

## Fase 3 — Volúmenes Docker

Los volúmenes son externos (persisten entre deploys). Deben existir antes del primer `docker compose up`.

```bash
docker volume create sadday-backend_sadday-pgdata
docker volume create sadday-backend_sadday-logs
docker volume create sadday-backend_sadday-geoip-data
```

- [ ] Volumen `sadday-backend_sadday-pgdata` creado
- [ ] Volumen `sadday-backend_sadday-logs` creado
- [ ] Volumen `sadday-backend_sadday-geoip-data` creado

Verificar: `docker volume ls | grep sadday`

---

## Fase 4 — Nginx

### 4.1 Configuración

- [ ] Nginx instalado en el host (no en contenedor) y configurado como proxy reverso
- [ ] Configuración del bloque `server` para el backend (`proxy_pass http://127.0.0.1:8080`)
- [ ] Configuración del bloque `server` para el frontend (`proxy_pass http://127.0.0.1:3000`)
- [ ] Redirect HTTP → HTTPS configurado
- [ ] Headers de seguridad en Nginx (ver `security-architecture.md` sección 6)

### 4.2 TLS / HTTPS

- [ ] Certificado SSL válido (Cloudflare gestiona TLS en el edge — Nginx puede usar cert autofirmado para el túnel Cloudflare↔Nginx si se usa modo Full Strict)
- [ ] Si se usa Let's Encrypt directamente: `certbot` instalado con renovación automática (`certbot renew --dry-run` sin errores)

### 4.3 Cloudflare

- [ ] Dominio apuntando a la IP del servidor vía Cloudflare (proxy activado — nube naranja)
- [ ] Modo SSL: **Full (strict)** en el panel de Cloudflare
- [ ] WAF activado
- [ ] Regla de firewall: bloquear acceso directo a la IP del servidor (solo Cloudflare puede conectarse a Nginx)

---

## Fase 5 — Email (AWS SES)

- [ ] Dominio verificado en AWS SES
- [ ] Registro **SPF** en DNS: `v=spf1 include:amazonses.com ~all`
- [ ] **DKIM** activado en SES y registro CNAME agregado al DNS
- [ ] Registro **DMARC**: `v=DMARC1; p=quarantine; rua=mailto:ADMIN_EMAIL`
- [ ] Cuenta SES fuera de sandbox (solicitar producción access en consola AWS)
- [ ] Verificar con MXToolbox (`mxtoolbox.com/SuperTool.aspx`) que SPF y DKIM pasan

---

## Fase 6 — Primer arranque

```bash
# Desde el directorio del proyecto
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 6.1 Verificaciones post-arranque

```bash
# Todos los contenedores en estado "running"
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# Backend responde
curl -s https://TU_DOMINIO/actuator/health | python3 -m json.tool

# Logs del backend sin errores de arranque
docker compose logs api --tail=50 | grep -E "ERROR|WARN|Started"

# GeoIP cargado (si el volumen ya tiene el .mmdb)
docker compose logs api --tail=50 | grep -i geoip

# Scheduler corrió al arrancar
docker compose logs api --tail=100 | grep -i scheduler
```

- [ ] `actuator/health` retorna `{"status":"UP"}`
- [ ] No hay errores `ERROR` en los logs del primer arranque
- [ ] Flyway aplicó todas las migraciones sin errores

### 6.2 Primer login y cambio de contraseña

- [ ] Iniciar sesión con el usuario admin y la `ADMIN_INITIAL_PASSWORD`
- [ ] El sistema fuerza cambio de contraseña en el primer login (`password_must_change = true`)
- [ ] Cambiar a contraseña fuerte y guardar en gestor de contraseñas
- [ ] **Activar 2FA en la cuenta admin** — obligatorio antes de usar en producción

---

## Fase 7 — Monitoreo externo (UptimeRobot)

- [ ] Cuenta en uptimerobot.com creada
- [ ] Monitor HTTP creado apuntando a `https://TU_DOMINIO/actuator/health`
- [ ] Intervalo: 5 minutos
- [ ] Alerta por email configurada al email del administrador
- [ ] Test de alerta verificado (pausar el monitor manualmente y confirmar que llega el email)

---

## Fase 8 — Verificación de seguridad post-deploy

### 8.1 Headers HTTP

```bash
curl -sI https://TU_DOMINIO/actuator/health | grep -E "content-security|x-frame|x-content-type|strict-transport"
```

- [ ] `Content-Security-Policy` presente
- [ ] `X-Frame-Options: DENY` presente
- [ ] `X-Content-Type-Options: nosniff` presente
- [ ] `Strict-Transport-Security` presente (HSTS)

### 8.2 Puertos expuestos

```bash
# Desde una máquina externa, verificar que estos puertos NO responden
nc -zv IP_DEL_SERVIDOR 8080   # debe fallar
nc -zv IP_DEL_SERVIDOR 5432   # debe fallar
nc -zv IP_DEL_SERVIDOR 9000   # debe fallar
```

- [ ] Puerto 8080 (API) no accesible desde internet
- [ ] Puerto 5432 (PostgreSQL) no accesible desde internet
- [ ] Puerto 9000/9001 (MinIO — si aplica) no accesible desde internet

### 8.3 Verificación de imagen firmada

```bash
cosign verify \
  --certificate-identity="https://github.com/REPO/.github/workflows/deploy.yml@refs/heads/main" \
  --certificate-oidc-issuer="https://token.actions.githubusercontent.com" \
  "${SADDAY_IMAGE}"
```

- [ ] La imagen desplegada tiene firma cosign verificada

---

## Fase 9 — Backup inicial

- [ ] Ejecutar backup manual de la BD antes de cualquier operación de datos reales:
```bash
docker exec sadday-db pg_dump -U $DB_USER $DB_NAME | gzip > backup-inicial-$(date +%Y%m%d).sql.gz
```
- [ ] Verificar que el proceso automático de backup (ver flujo 10) está activo

---

## Referencias

- Rotación de secretos: `docs/security/architecture/secret-rotation.md`
- Respuesta a incidentes: `docs/security/architecture/incident-response.md`
- Arquitectura de seguridad: `docs/security/architecture/security-architecture.md`
- Flujo de backup: `docs/flujos/10-proceso-backup-bdd-logs.md`
- Monitoreo GeoIP: `docs/flujos/12-actualizacion-automatica-geoip.md`
- Alertas de infraestructura: `docs/flujos/13-alertas-infraestructura-admin.md`
