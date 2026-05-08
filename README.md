# Sadday App — Monorepo

Sistema de gestión para el **Club de Montaña Sadday** (`el-sadday.com`).

Cubre el ciclo completo de la operación del club: gestión de socios, planificación de salidas, inscripciones, informes post-salida, actas de reunión, estadísticas y administración.

---

## Estructura del monorepo

```
sadday-app/
├── backend/        # API REST — Java 21 + Spring Boot 4
├── frontend/       # Web — React 19 + TypeScript + Vite
├── mcp/            # Servidor MCP — asistente IA (Model Context Protocol)
├── mobile/         # App móvil — Flutter (pendiente)
├── docs/           # Documentación técnica, diagramas, esquema BD
│   ├── db/         # esquema_bdd.md — diagrama ER actualizado
│   └── security/   # Threat model, diagramas STRIDE
├── scripts/        # Generación de claves RSA, prueba de endpoints
├── avances_y_pendientes.md  # Seguimiento del proyecto sesión a sesión
└── endpoints.md    # Referencia completa de la API (70+ endpoints)
```

---

## Estado del proyecto

| Módulo | Stack | Estado |
|--------|-------|--------|
| `backend/` | Java 21 · Spring Boot 4.0.3 · PostgreSQL 16 | **Completo** ✅ |
| `frontend/` | React 19 · TypeScript · Vite · TailwindCSS | **En progreso** 🔄 |
| `mcp/` | Node.js · TypeScript · @modelcontextprotocol/sdk | **Completo** ✅ |
| `mobile/` | Flutter · Dart | Pendiente |

---

## Stack tecnológico

### Backend
| Capa | Tecnología |
|------|-----------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.3 |
| Base de datos | PostgreSQL 16 (JSONB, TSVECTOR, ENUM nativos) |
| Migraciones | Flyway (V1 schema + V2 seed data) |
| ORM | Spring Data JPA / Hibernate |
| Seguridad | Spring Security · JWT RS256 · Argon2id · 2FA TOTP |
| Email | Spring Mail · Amazon SES (SMTP) |
| Storage | AWS S3 / Lightsail Object Storage (PDFs) |
| Tests | JUnit 5 · Mockito · Testcontainers — **195 tests, 0 fallos** |
| Documentación | SpringDoc OpenAPI 3 (Swagger UI) |
| CI/CD | GitHub Actions (build · test · SonarCloud · Semgrep · Snyk · deploy) |

### Frontend
| Capa | Tecnología |
|------|-----------|
| Lenguaje | TypeScript 5.9 |
| Framework | React 19 + Vite 7 |
| Estilos | TailwindCSS 4 |
| Componentes | Radix UI · shadcn/ui · Lucide icons |
| Estado global | Zustand 5 |
| Data fetching | TanStack Query 5 · Axios |
| Formularios | React Hook Form 7 · Zod 4 |
| Gráficos | Recharts 3 |
| Routing | React Router 7 |

---

## Módulos del sistema

| Módulo | Descripción |
|--------|-------------|
| **Auth** | Login, JWT (RS256), refresh tokens rotativos, 2FA TOTP, recuperación de contraseña, registro por invitación |
| **Socios** | CRUD completo, roles (Admin/Secretaria/Directivo/Socio), nivel técnico, habilitación/inhabilitación individual y masiva (CSV), historial de cambios |
| **Montañas y Rutas** | 40+ montañas del Ecuador, rutas multi-actividad (Alpinismo / Escalada / Trekking / Ciclismo), acceso por nivel técnico, planificador de rutas |
| **Salidas** | Planificación, inscripciones con control de nivel y habilitación, dignidades (Jefe de Salida, Conductor…), aprobación de riesgo, scheduler de transición de estados |
| **Informes** | Informe post-salida con segmentos de viaje, contactos, costos, alojamiento, reconocimientos (AMONESTADO/DESTACADO), generación y descarga de PDF |
| **Actas de reunión** | CRUD de actas con Full Text Search, importación desde archivo `.md`, asistentes, informes vinculados, generación y descarga de PDF |
| **Estadísticas** | Rankings de salidas y reuniones, historial por socio, estadísticas por montaña, actividad total combinada |
| **Notificaciones** | Cumpleaños del día, promoción automática Juvenil → Socio Activo al cumplir 18 años |
| **Administración** | Gestión de usuarios, auditoría de acciones, desbloqueo de cuentas, niveles de acceso por nivel técnico |
| **Contactos** | Directorio global de contactos (guías, transportistas, refugios) reutilizables entre salidas |
| **Asistente IA (MCP)** | Servidor Model Context Protocol para Claude Desktop/Code — 12 herramientas de solo lectura: montañas, rutas, salidas, informes y actas. Autenticado con API Keys (`sk-sadday-...`) |

---

## Inicio rápido

### Prerequisitos

- Docker y Docker Compose
- Java 21 (solo si corres el backend desde el IDE)
- Node.js 20+ con pnpm (solo para el frontend)
- [Infisical CLI](https://infisical.com/docs/cli/overview) — gestión de secretos

### Setup inicial (una sola vez)

```bash
git clone <repo>
cd sadday-app

# Generar claves RSA para JWT
bash scripts/generate-keys.sh

# Autenticarse en Infisical (obtener acceso al equipo primero)
infisical login
```

Genera `backend/src/main/resources/keys/private.pem` y `public.pem`. Los secretos (DB, mail, S3, etc.) se obtienen automáticamente de Infisical.

### Imágenes Docker

El proyecto construye **2 imágenes propias** (con `--build`):

| Contenedor | Dockerfile | Descripción |
|---|---|---|
| `sadday-api` | `backend/Dockerfile` | API REST — Spring Boot, JRE 21 |
| `sadday-frontend` | `frontend/Dockerfile` | React compilado con Vite, servido por Nginx |

El resto son imágenes públicas que se usan sin modificación:

| Contenedor | Imagen | Uso |
|---|---|---|
| `sadday-db` | `postgres:16-alpine` | Base de datos |
| `sadday-minio` | `minio/minio` | Storage S3-compatible local |
| `sadday-minio-init` | `minio/mc` | Crea el bucket al iniciar (one-shot) |
| `sadday-mailpit` | `axllent/mailpit` | Servidor SMTP + bandeja web para dev |
| `sadday-geoip-updater` | `ghcr.io/maxmind/geoipupdate` | Actualiza base GeoIP (perfil `geoip`, opcional) |

### Desarrollo local completo (todo con Docker Compose)

```bash
docker-compose up --build
```

Servicios disponibles tras levantar:

| Servicio | URL |
|---|---|
| API REST | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Frontend (dev server) | `http://localhost:5173` |
| Consola MinIO (storage local) | `http://localhost:9001` (minioadmin / minioadmin) |
| Mailpit (Testing de correos) | `http://localhost:8025` (Bandeja web) |
| PostgreSQL | `localhost:5432` (sadday_admin / sadday_password_local123) |

### Desarrollo parcial (infraestructura en Docker, app desde el IDE)

```bash
# Solo PostgreSQL, MinIO y Mailpit
docker-compose up -d postgres minio minio-init mailpit

# Backend (desde backend/) — Infisical inyecta los secretos del entorno dev
infisical run --env=dev -- ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Frontend (desde frontend/)
pnpm dev
```

---

## Base de datos local

| Parámetro | Valor |
|-----------|-------|
| Contenedor | `sadday-db` |
| Imagen | `postgres:16-alpine` |
| Host / Puerto | `localhost:5432` |
| Base de datos | `sadday_app` |
| Usuario | `sadday_admin` |
| Contraseña | `sadday_password_local123` |
| Volumen | `sadday-backend_sadday-pgdata` (persistente) |

```bash
# Conectarse a la BD
docker exec -it sadday-db psql -U sadday_admin -d sadday_app

# Ver estado del contenedor
docker ps --filter name=sadday-db

# Reiniciar sin perder datos
docker restart sadday-db
```

Los tests usan **Testcontainers** — PostgreSQL efímero separado, no afectan el contenedor de desarrollo.

---

## Seguridad

### Consideraciones generales

| Área | Decisión |
|------|----------|
| **Autenticación** | JWT RS256 con claves asimétricas (private/public PEM). Access token de 15 min + refresh token rotativo. Detección de robo: si se reutiliza un refresh revocado, se invalidan todas las sesiones del usuario |
| **Contraseñas** | Argon2id con parámetros OWASP 2026 (salt 16 bytes, hash 32 bytes, memoria 19 MB, 2 iteraciones). Nunca se almacena en claro ni en logs |
| **2FA** | TOTP (Google Authenticator / Authy). Secret cifrado en BD con AES-256-GCM antes de persistir |
| **API Keys (MCP)** | Generadas con `SecureRandom` (32 bytes, base64url). Solo se almacena el hash SHA-256 — el raw se muestra una sola vez al crearse. Scope forzado a solo lectura (`SCOPE_readonly`). Máximo 5 keys activas por usuario |
| **Tokens y hashes** | Refresh tokens, password reset tokens y API keys: siempre SHA-256 en BD, nunca el valor real |
| **Cabeceras HTTP** | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Content-Security-Policy`, `Referrer-Policy: no-referrer`. HSTS habilitado solo en producción |
| **CORS** | Origen permitido configurado explícitamente por entorno (`APP_URL`). No hay wildcard |
| **Rate limiting** | Límite por IP en endpoints de autenticación (`/auth/login`, `/auth/refresh`, etc.) |
| **Auditoría** | Tabla `auditoria` append-only. Registra login, logout, cambios de contraseña, uso de API keys, acciones administrativas. El usuario de la app no tiene permisos de UPDATE/DELETE sobre ella |
| **Anti-enumeración** | Recursos ajenos devuelven `404` en lugar de `403` para no revelar su existencia |
| **Secretos** | Gestionados con Infisical. Nunca en el repositorio ni en variables de entorno hardcodeadas |
| **TLS** | En producción el filtro `ApiKeyAuthFilter` rechaza requests sin HTTPS (`X-Forwarded-Proto`) |

### Escaneos de seguridad (CI/CD)

El pipeline de GitHub Actions ejecuta los siguientes escaneos automáticamente:

| Herramienta | Cuándo | Qué analiza |
|-------------|--------|-------------|
| **SonarCloud** | Cada push a `main` | Calidad de código, bugs, code smells y cobertura de tests |
| **Semgrep** | Cada push a `main` | SAST — análisis estático de vulnerabilidades en el código fuente |
| **Snyk** | Cada push a `main` + lunes semanalmente | Vulnerabilidades CVE en dependencias Maven (backend) y pnpm (frontend) |
| **OWASP Dependency Check** | Lunes semanalmente | Dependencias Maven contra base NVD/CVE |
| **Trivy** | En cada deploy | Escaneo de las imágenes Docker (`sadday-api`, `sadday-frontend`) antes de hacer push al registry |

Los resultados de Semgrep, Snyk y Trivy se suben como SARIF al panel **GitHub Code Scanning** del repositorio.

### Documentación de seguridad

Ver [`docs/security/`](docs/security/) para el threat model completo, diagramas STRIDE y flujos de autenticación detallados.

---

## Producción (AWS Lightsail)

### Arquitectura de red

```
Internet → Cloudflare WAF → Firewall VPS (solo IPs Cloudflare)
       → Nginx (SSL termination) → Spring Boot :8080 (localhost)
       → PostgreSQL (red interna Docker)
```

### Variables de entorno requeridas en producción

Ver [`backend/.env.example`](backend/.env.example) para la lista completa. Las críticas:

```bash
# BD
DB_HOST=...  DB_NAME=sadday_app  DB_USER=...  DB_PASSWORD=...

# JWT
JWT_PRIVATE_KEY_LOCATION=file:/app/keys/private.pem
JWT_PUBLIC_KEY_LOCATION=file:/app/keys/public.pem
TOTP_ENCRYPTION_KEY=<openssl rand -base64 32>

# Email — Amazon SES
MAIL_HOST=email-smtp.us-east-1.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=<SES SMTP user>
MAIL_PASSWORD=<SES SMTP password>
MAIL_FROM=noreply@el-sadday.com
APP_URL=https://app.el-sadday.com

# Storage — S3 / Lightsail Object Storage
S3_BUCKET=sadday-pdfs
S3_REGION=us-east-1
S3_ACCESS_KEY=<IAM access key>
S3_SECRET_KEY=<IAM secret key>
```

### Levantar en producción

```bash
# Crear volumen externo (solo la primera vez — protege contra docker-compose down -v)
docker volume create sadday-backend_sadday-pgdata

# Levantar
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

---

## Email — Amazon SES

La app envía correos en dos casos:
- **Invitación de registro** → cuando la Secretaria da de alta a un nuevo socio
- **Recuperación de contraseña** → flujo de reset por link

Ambos usan Spring Mail (cliente SMTP) apuntando a Amazon SES.

Para configurar SES en `el-sadday.com`:
1. AWS Console → SES → Verified identities → verificar dominio `el-sadday.com`
2. Agregar registros DKIM (CNAME ×3) en Cloudflare DNS
3. Agregar `TXT @ "v=spf1 include:amazonses.com ~all"` en Cloudflare
4. SES → SMTP settings → Create SMTP credentials → copiar usuario y contraseña
5. SES → Account dashboard → Request production access (para salir del sandbox)

---

## Tests

```bash
cd backend
./mvnw test                              # todos (195 tests)
./mvnw test -Dtest=ActaIntegrationTest   # una clase concreta
```

Requiere Docker daemon activo (Testcontainers levanta PostgreSQL automáticamente).

---

## Documentación

| Recurso | Descripción |
|---|---|
| [`backend/README.md`](backend/README.md) | Setup detallado del backend, variables de entorno, logs, seguridad |
| [`frontend/README.md`](frontend/README.md) | Setup del frontend, rutas, estructura de componentes |
| [`endpoints.md`](endpoints.md) | Referencia completa de los endpoints de la API |
| [`docs/db/esquema_bdd.md`](docs/db/esquema_bdd.md) | Diagrama ER completo (Mermaid) |
| [`docs/security/`](docs/security/) | Threat model, diagramas STRIDE y flujos de autenticación |
| [`avances_y_pendientes.md`](avances_y_pendientes.md) | Historial de sesiones y estado de cada módulo |
| `http://localhost:8080/swagger-ui.html` | Swagger UI interactivo (con la app corriendo) |
