# Checklist de setup del entorno Staging — Sadday App

**Última actualización:** 2026-05-12
**Audiencia:** Operador que configura el servidor Proxmox por primera vez.

Sigue este documento en orden. El pipeline CI/CD (GitHub Actions) despliega automáticamente a este servidor en cada push a `develop` — este checklist cubre el setup inicial que el pipeline asume que ya existe.

---

## Fase 1 — Servidor Proxmox (Ubuntu)

### 1.1 Sistema operativo y Docker

```bash
sudo apt update && sudo apt upgrade -y

# Docker Engine (no Docker Desktop)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

- [ ] Docker Engine instalado. Versión mínima: 24.x (`docker --version`)
- [ ] `docker compose` plugin disponible (`docker compose version`)
- [ ] Usuario del runner en el grupo `docker` — no ejecutar como root

### 1.2 Infisical CLI

```bash
curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh' | sudo -E bash
sudo apt install -y infisical
infisical --version
```

- [ ] Infisical CLI instalado

### 1.3 Directorio de la aplicación

```bash
sudo mkdir -p /opt/sadday/keys
sudo chown -R $USER:docker /opt/sadday
```

- [ ] `/opt/sadday` creado con permisos para el usuario del runner

---

## Fase 2 — Claves JWT

Genera un par de claves RSA-4096 exclusivas para staging. Cada ambiente debe tener su propio par.

```bash
openssl genpkey \
    -algorithm RSA \
    -pkeyopt rsa_keygen_bits:4096 \
    -out /opt/sadday/keys/private.pem

openssl rsa \
    -in /opt/sadday/keys/private.pem \
    -pubout \
    -out /opt/sadday/keys/public.pem

chmod 600 /opt/sadday/keys/private.pem
chmod 644 /opt/sadday/keys/public.pem
```

- [ ] `private.pem` generado en `/opt/sadday/keys/` con permisos `600`
- [ ] `public.pem` generado en `/opt/sadday/keys/` con permisos `644`
- [ ] Las claves **no** están en el repositorio Git

---

## Fase 3 — GitHub Actions Runner (self-hosted)

### 3.1 Registrar el runner

En GitHub → Settings → Actions → Runners → **New self-hosted runner**:

- OS: Linux
- Sigue las instrucciones de instalación que muestra GitHub

### 3.2 Labels del runner

Al configurar el runner, asignar exactamente estas labels:

```
self-hosted, staging, proxmox
```

El workflow usa `runs-on: [self-hosted, staging, proxmox]` — si los labels no coinciden, el job queda en cola indefinidamente.

- [ ] Runner registrado y en estado **Idle** en GitHub → Settings → Actions → Runners
- [ ] Labels del runner: `self-hosted`, `staging`, `proxmox`
- [ ] Runner configurado como servicio para que sobreviva reinicios:

```bash
sudo ./svc.sh install
sudo ./svc.sh start
```

---

## Fase 4 — Infisical

### 4.1 Crear el proyecto

1. Cuenta en [app.infisical.com](https://app.infisical.com)
2. Crear proyecto: `sadday-app`
3. Ir al environment `staging`

### 4.2 Cargar los secretos

Importar el archivo `.infisical.staging.env.example` (en la raíz del repo) con valores reales:

**Infisical → proyecto → staging → Add Secrets → Import .env**

Variables requeridas:

| Variable | Descripción |
|---|---|
| `DB_NAME` | Nombre de la base de datos (ej: `sadday_staging`) |
| `DB_USER` | Usuario PostgreSQL |
| `DB_PASSWORD` | Password generado con `openssl rand -hex 24` |
| `MINIO_ROOT_USER` | Usuario MinIO (no usar `minioadmin`) |
| `MINIO_ROOT_PASSWORD` | Password generado con `openssl rand -hex 24` |
| `S3_BUCKET` | Nombre del bucket (ej: `sadday-staging`) |
| `S3_REGION` | `us-east-1` |
| `JWT_PRIVATE_KEY_PATH` | `/opt/sadday/keys/private.pem` |
| `JWT_PUBLIC_KEY_PATH` | `/opt/sadday/keys/public.pem` |
| `JWT_ISSUER` | `sadday-app-staging` |
| `TOTP_ENCRYPTION_KEY` | Generado con `openssl rand -base64 32`. **No cambiar después del primer deploy** — si se cambia, todos los usuarios con 2FA activado pierden acceso |
| `APP_URL` | `http://<IP_PROXMOX>:3000` |
| `MAIL_FROM` | `noreply@sadday.staging` |
| `ADMIN_USERNAME` | `admin` |
| `ADMIN_INITIAL_PASSWORD` | Generado con `openssl rand -hex 16` |
| `ADMIN_ALERT_EMAIL` | Email del administrador (puede quedar vacío) |

- [ ] Todos los secretos cargados en Infisical environment `staging`
- [ ] Ningún password usa el valor por defecto (`minioadmin`, `Admin123!`, etc.)

### 4.3 Machine Identity (token para el pipeline)

1. **Infisical → proyecto → Access Control → Machine Identities → Create**
   - Nombre: `sadday-staging-runner`
   - Acceso al environment `staging`, rol `developer`
2. Copiar el **Client Secret** generado (se muestra una sola vez)

- [ ] Machine Identity creada con acceso solo al environment `staging`

---

## Fase 5 — GitHub Secrets y Variables

### Environment `staging`

Crear el environment en GitHub → Settings → Environments → **New environment** → `staging`

| Nombre | Tipo | Valor |
|---|---|---|
| `INFISICAL_TOKEN` | Secret | Client Secret de la Machine Identity (Fase 4.3) |
| `GHCR_READ_TOKEN` | Secret | PAT de GitHub con scope `read:packages` |
| `INFISICAL_PROJECT_ID` | Variable | ID del proyecto en Infisical → Settings → Project ID |

Para crear el `GHCR_READ_TOKEN`: GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → New token → marcar solo `read:packages`.

- [ ] Environment `staging` creado en GitHub
- [ ] Secret `INFISICAL_TOKEN` configurado
- [ ] Secret `GHCR_READ_TOKEN` configurado
- [ ] Variable `INFISICAL_PROJECT_ID` configurada

---

## Fase 6 — Primer deploy

Una vez completadas las fases anteriores, hacer push a `develop` o disparar manualmente:

**GitHub → Actions → Build, Scan & Deploy → Run workflow → staging**

### 6.1 Verificaciones post-arranque

```bash
# Todos los contenedores corriendo
docker compose -f /opt/sadday/docker-compose.yml \
               -f /opt/sadday/docker-compose.staging.yml ps

# API responde
curl -s http://localhost:8080/actuator/health

# Logs sin errores de arranque
docker logs sadday-api --tail=50 | grep -E "ERROR|Started"

# Flyway aplicó migraciones
docker logs sadday-api --tail=100 | grep -i flyway
```

- [ ] Todos los contenedores en estado `running`
- [ ] `actuator/health` retorna `{"status":"UP"}`
- [ ] No hay errores `ERROR` en los logs del primer arranque
- [ ] Flyway aplicó todas las migraciones sin errores

### 6.2 Primer login

- [ ] Iniciar sesión con `admin` y el `ADMIN_INITIAL_PASSWORD` configurado en Infisical
- [ ] El sistema fuerza cambio de contraseña en el primer login
- [ ] Cambiar a contraseña fuerte

---

## Referencias

- Setup de producción (Lightsail): `docs/security/architecture/production-deployment-checklist.md`
- Gestión de secretos con Infisical: `docs/security/architecture/secrets-management.md`
- Pipeline CI/CD: `docs/flujos/15-pipelines-ci-cd.md`
- Estrategia de ramas: `docs/flujos/16-estrategia-git-branching.md`
