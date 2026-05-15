# Sadday App — Backend API

API REST del sistema de gestión del Club de Montaña Sadday (`el-sadday.com`).

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.3 |
| Base de datos | PostgreSQL 16 (JSONB, TSVECTOR, ENUM nativos) |
| Migraciones | Flyway (V1 schema + V2 seed data) |
| ORM | Spring Data JPA / Hibernate |
| Seguridad | Spring Security · JWT RS256 · Argon2id · 2FA TOTP |
| Email | Spring Mail → Amazon SES (SMTP) |
| Storage | AWS S3 / Lightsail Object Storage |
| Build | Maven 3 (wrapper incluido: `./mvnw`) |
| Tests | JUnit 5 · Mockito · Testcontainers — **743 tests, 0 fallos** |
| Documentación | SpringDoc OpenAPI 3 (Swagger UI) |

---

## Requisitos previos

- Java 21+
- Docker (para PostgreSQL en tests y ejecución local)
- Maven 3.9+ o el wrapper incluido `./mvnw`

---

## Configuración inicial

### 1. Generar claves RSA para JWT

Desde la raíz del monorepo:

```bash
bash scripts/generate-keys.sh
```

Genera `src/main/resources/keys/private.pem` y `public.pem`. Solo se hace una vez.

### 2. Configurar secretos con Infisical

Los secretos de todos los entornos están en **Infisical**. El proyecto usa el archivo `.infisical.json` en la raíz del monorepo para saber a qué proyecto conectarse.

Instala la CLI si no la tienes:
```bash
# macOS / Linux
curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh' | sudo bash
sudo apt install infisical   # Debian/Ubuntu
brew install infisicalhq/tap/infisical   # macOS
```

Luego autentícate:
```bash
infisical login
```

Los secretos del entorno `dev` (desarrollo local) incluyen DB, mail, S3/MinIO, TOTP y APP_URL. No hace falta crear ni mantener un `.env` local.

---

## Ejecución

### Con Docker Compose (desde la raíz del monorepo)

```bash
docker-compose up --build
```

### Solo la infraestructura en Docker, backend desde Maven

```bash
# Desde la raíz del monorepo — levanta PostgreSQL + MinIO + Mailpit
docker-compose up -d postgres minio minio-init mailpit

# Desde backend/ — los secretos los inyecta Infisical
infisical run --env=dev -- ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Con el perfil `local`, el backend conecta a PostgreSQL en `localhost:5432` y MinIO en `localhost:9000`. Infisical inyecta las variables de entorno necesarias antes de que arranque Spring Boot.

### Alternativa sin Infisical (`.env` manual)

Si no tienes acceso a Infisical o prefieres el método tradicional:

```bash
cp .env.example .env
# Editar con los valores reales
```

Valores mínimos para desarrollo local (ya preconfigurados en `.env.example`):

```env
DB_NAME=sadday_app
DB_USER=sadday_admin
DB_PASSWORD=sadday_password_local123
JWT_PRIVATE_KEY_LOCATION=file:src/main/resources/keys/private.pem
JWT_PUBLIC_KEY_LOCATION=file:src/main/resources/keys/public.pem
TOTP_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=dev
MAIL_PASSWORD=dev
MAIL_FROM=noreply@sadday-local.test
APP_URL=http://localhost:5173
S3_BUCKET=sadday-local
S3_REGION=us-east-1
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_ENDPOINT=http://localhost:9000
```

Luego ejecutar directamente:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```


---

## Tests

Usan **Testcontainers** — levanta PostgreSQL automáticamente. Requiere Docker daemon activo.

```bash
# Todos los tests
./mvnw test

# Una clase concreta
./mvnw test -Dtest=ActaIntegrationTest

# Con output detallado en consola
./mvnw test -Dsurefire.useFile=false
```

**Estado actual: 743 tests, 0 fallos.**

---

## Documentación de la API

Con la app corriendo localmente:

| URL | Descripción |
|-----|-------------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI interactivo |
| `http://localhost:8080/v3/api-docs` | Especificación OpenAPI 3 JSON |

Ver [`../endpoints.md`](../endpoints.md) para la referencia completa.

---

## Estructura de paquetes

```
src/main/java/com/sadday/app/
├── auth/           # Login, JWT, 2FA, recuperación password, country challenge, api keys
├── socios/         # CRUD socios, roles, habilitación individual y masiva (CSV), historial
├── mountains/      # Montañas, rutas multi-actividad, contactos, acceso por nivel técnico
├── salidas/        # Planificación, inscripciones, dignidades, aprobación de riesgo
├── informes/       # Informes post-salida, segmentos de viaje, reconocimientos, PDF
├── actas/          # Actas de reunión, importación .md, FTS, asistentes, PDF
├── estadisticas/   # Rankings, historial por socio, estadísticas por montaña y reuniones
├── notificaciones/ # Cumpleaños del día, alertas activas
├── admin/          # Auditoría, eventos de seguridad, gestión de usuarios, emergency reset
├── scheduler/      # Transición automática estados salida, promoción tipo Juvenil
├── config/         # Security, JWT, CORS, S3, OpenAPI, DevDataInitializer
└── shared/         # ApiResponse, ErrorCode, BusinessException, ApiPaths, StorageService
```

---

## Migraciones de base de datos

El esquema se gestiona con **Flyway**. Las migraciones viven en `src/main/resources/db/migration/`.

| Archivo | Contenido |
|---------|-----------|
| `V1__schema.sql` | Esquema completo: extensiones, ENUMs, 48 tablas, índices, FK constraints, trigger FTS |
| `V2__seed_data.sql` | Datos de referencia: catálogos, 41 montañas, 69 rutas (Alpinismo/Escalada/Trekking/Ciclismo), configuración |

Los datos de prueba (usuarios de desarrollo) los inserta `DevDataInitializer` al arrancar con perfil `local`.

Ver [`../docs/db/esquema_bdd.md`](../docs/db/esquema_bdd.md) para el diagrama ER completo.

---

## Variables de entorno

| Variable | Descripción | Default local |
|----------|-------------|---------------|
| `DB_NAME` | Nombre de la base de datos | `sadday_app` |
| `DB_USER` | Usuario de PostgreSQL | `sadday_admin` |
| `DB_PASSWORD` | Contraseña de PostgreSQL | `sadday_password_local123` |
| `DB_HOST` | Host de PostgreSQL | `localhost` |
| `DB_PORT` | Puerto de PostgreSQL | `5432` |
| `JWT_PRIVATE_KEY_LOCATION` | Ruta al PEM privado RSA | — |
| `JWT_PUBLIC_KEY_LOCATION` | Ruta al PEM público RSA | — |
| `JWT_ISSUER` | Issuer del JWT | `sadday-app` |
| `JWT_ACCESS_EXPIRATION` | Duración access token (seg) | `900` (15 min) |
| `JWT_REFRESH_EXPIRATION` | Duración refresh token (seg) | `2592000` (30 días) |
| `TOTP_ENCRYPTION_KEY` | Clave AES-256 Base64 para cifrar secrets TOTP | — |
| `MAX_LOGIN_ATTEMPTS` | Intentos fallidos antes de bloquear cuenta | `3` |
| `LOCKOUT_DURATION_HOURS` | Horas de bloqueo tras exceder intentos | `24` |
| `PWD_RESET_EXPIRY` | Validez del link de reset (minutos) | `15` |
| `EMAIL_VERIFY_EXPIRY` | Validez del link de invitación (horas) | `72` |
| `COOKIE_SECURE` | Refresh token cookie solo por HTTPS | `false` (local) |
| `MAIL_HOST` | Servidor SMTP | `localhost` |
| `MAIL_PORT` | Puerto SMTP | `587` |
| `MAIL_USERNAME` | Usuario SMTP | `dev` |
| `MAIL_PASSWORD` | Contraseña SMTP | `dev` |
| `MAIL_FROM` | Dirección remitente | `noreply@sadday-local.test` |
| `APP_URL` | URL pública del frontend (en links de emails) | `http://localhost:5173` |
| `S3_BUCKET` | Bucket para PDFs | `sadday-local` |
| `S3_REGION` | Región AWS | `us-east-1` |
| `S3_ACCESS_KEY` | Access Key de S3/Lightsail | `minioadmin` (local) |
| `S3_SECRET_KEY` | Secret Key de S3/Lightsail | `minioadmin` (local) |
| `S3_ENDPOINT` | Endpoint S3 (vacío = AWS real; MinIO local = `http://localhost:9000`) | `http://localhost:9000` |

---

## Email — Amazon SES (producción)

En producción, la app envía correos a través de **Amazon SES via SMTP**:

- **Invitación de registro**: cuando la Secretaria registra un nuevo socio
- **Recuperación de contraseña**: link de reset con validez de 15 minutos

Configuración de producción:

```bash
MAIL_HOST=email-smtp.us-east-1.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=<SES SMTP user — AWS Console → SES → SMTP settings → Create SMTP credentials>
MAIL_PASSWORD=<SES SMTP password>
MAIL_FROM=noreply@el-sadday.com
APP_URL=https://app.el-sadday.com
```

Para desarrollo local se puede usar **Mailpit** (intercepta emails sin enviarlos). Está incluido en el `docker-compose.yml` del monorepo:

```bash
# Bandeja de entrada en: http://localhost:8025
docker-compose up -d mailpit
```

---

## Perfiles de Spring

| Perfil | Archivo | Cuándo se usa |
|--------|---------|---------------|
| `local` | `application-local.yml` | Desarrollo local (IDE o Docker Compose local) |
| `prod` | `application-prod.yml` | AWS Lightsail (producción) |
| (base) | `application.yml` | Compartido por todos los perfiles |
| `test` | `application-test.yml` | Tests de integración con Testcontainers |

---

## Seguridad

- Contraseñas: **Argon2id** (parámetros OWASP)
- JWT: **RS256** con par de claves RSA-4096
- Refresh tokens: almacenados como hash SHA-256, nunca el raw
- Secrets TOTP: cifrados con **AES-256-GCM** en reposo
- Rate limiting: máx. 3 intentos de login → bloqueo 24 h
- Uploads: validación en 6-7 capas (extensión, path traversal, MIME, bytes nulos, UTF-8 estricto, tamaño)
- Auditoría: tabla `auditoria` append-only via Spring AOP (`@Auditable`)
- Red: Cloudflare WAF → Firewall VPS → Nginx → app (localhost únicamente)

---

## Logs — Dónde están y cómo usarlos

### Archivos de log (producción)

| Archivo | Contenido |
|---------|-----------|
| `/app/logs/sadday-app.log` | INFO + WARN (rotación diaria, 30 días) |
| `/app/logs/sadday-app-error.log` | Solo ERROR (rotación diaria, 90 días) |

En desarrollo local los logs van solo a consola (`docker-compose logs -f api`).

### Formato JSON (producción)

```json
{
  "@timestamp": "2026-04-16T19:45:12.345Z",
  "level":      "ERROR",
  "logger":     "com.sadday.app.auth.service.AuthService",
  "message":    "Login fallido para usuario: jperez",
  "requestId":  "a3f1c2d4-...",
  "app":        "sadday-app",
  "env":        "prod"
}
```

### Consultas de uso frecuente

```bash
# Buscar todos los logs de una petición por requestId
cat /app/logs/sadday-app.log | jq 'select(.requestId == "a3f1c2d4-xxxx")'

# Errores de hoy
cat /app/logs/sadday-app-error.log | jq -r '[@timestamp, .level, .message] | @tsv'

# Últimos 30 min
cat /app/logs/sadday-app.log | jq 'select(.level == "ERROR") | {time: .["@timestamp"], msg: .message}'

# Logs históricos comprimidos
zcat /app/logs/sadday-app-error.2026-04-15.gz | jq '.message'
```

### Consultas de auditoría (BD)

```sql
-- Actividad reciente
SELECT created_at, actor_username, accion, entidad_afectada, resultado
FROM auditoria ORDER BY created_at DESC LIMIT 50;

-- Intentos de login fallidos en las últimas 24 h
SELECT created_at, actor_username, ip_address, detalle
FROM auditoria
WHERE accion IN ('LOGIN_FAILED','LOGIN_BLOCKED')
  AND created_at >= NOW() - INTERVAL '24 hours'
ORDER BY created_at DESC;

-- Historial de cambios sobre un socio específico
SELECT created_at, actor_username, accion, datos_anteriores, datos_nuevos
FROM auditoria
WHERE entidad_afectada = 'socios' AND entidad_id = '<uuid>'
ORDER BY created_at;

-- IPs con más fallos (posible brute force)
SELECT ip_address, COUNT(*) intentos
FROM auditoria
WHERE accion = 'LOGIN_FAILED' AND created_at >= NOW() - INTERVAL '1 hour'
GROUP BY ip_address ORDER BY intentos DESC;
```

### Conectarse a la BD en producción

```bash
# Desde el servidor (dentro de la red Docker)
docker exec -it sadday-db psql -U sadday_admin -d sadday_app

# Desde fuera con túnel SSH
ssh -L 5432:localhost:5432 usuario@servidor
psql -h localhost -U sadday_admin -d sadday_app
```
