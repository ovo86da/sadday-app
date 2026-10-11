# Sadday App — Monorepo

Sistema de gestión para el **Club de Montaña Sadday** (`el-sadday.com`).

Cubre el ciclo completo de la operación del club: gestión de socios, planificación de salidas, inscripciones con validación de perfil y documentos de riesgo, informes post-salida, actas de reunión, estadísticas, administración y cumplimiento LOPDP (gestión de consentimientos, datos médicos y retención de datos).

---

## Estructura del monorepo

```
sadday-app/
├── backend/        # API REST — Java 21 + Spring Boot 4
├── frontend/       # Web — React 19 + TypeScript + Vite
├── mcp/            # Servidor MCP — asistente IA (Model Context Protocol)
├── mobile/         # App móvil — Flutter
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
| `backend/` | Java 21 · Spring Boot 4.0.8 · PostgreSQL 16 | **Completo** ✅ |
| `frontend/` | React 19 · TypeScript · Vite · TailwindCSS | **Completo** ✅ |
| `mcp/` | Node.js · TypeScript · @modelcontextprotocol/sdk | **Completo** ✅ |
| `mobile/` | Flutter · Dart · fvm 3.44.0 | **Completo** ✅ |

---

## Stack tecnológico

### Backend
| Capa | Tecnología |
|------|-----------|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.8 |
| Base de datos | PostgreSQL 16 (JSONB, TSVECTOR, ENUM nativos) |
| Migraciones | Flyway — 19 migraciones (V1 schema · V2 seed · V3–V10 features · V11–V17 gestión documental · V18–V19 retirada de PII del token) |
| ORM | Spring Data JPA / Hibernate |
| Seguridad | Spring Security · JWT RS256 · Argon2id · 2FA TOTP |
| Email | Spring Mail · Amazon SES (SMTP) |
| Storage | AWS S3 / Lightsail Object Storage (PDFs) |
| Tests | JUnit 5 · Mockito · Testcontainers — **877 tests, 0 fallos** |
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

### Mobile
| Capa | Tecnología |
|------|-----------|
| Lenguaje | Dart 3.12 |
| Framework | Flutter 3.44.0 (fvm) |
| Gestión de estado | Riverpod 2 |
| HTTP client | Dio 5 + interceptores (auth, error, logging) |
| Almacenamiento seguro | flutter_secure_storage 10 (Keychain iOS / Keystore Android) |
| Navegación | go_router 14 |
| Flavors | `dev` · `staging` · `prod` |
| Tests | flutter_test — 87 tests (unit + widget + integration) |
| Seguridad | OWASP MASVS · biometría con fallback a contraseña · inactivity timeout 10 min · screenshot protection (FLAG_SECURE / privacy overlay) |

---

## Módulos del sistema

| Módulo | Descripción |
|--------|-------------|
| **Auth** | Login, JWT (RS256), refresh tokens rotativos, 2FA TOTP, recuperación de contraseña, registro por invitación (wizard de 6 pasos), country challenge (detección de login desde país nuevo) |
| **Seguridad avanzada** | Eventos de seguridad (login, dispositivo nuevo, país nuevo), detección GeoIP (MaxMind), emergency reset 2FA por Admin, gestión de estados de acceso (ACTIVE/BLOCKED/EX_MEMBER/DISABLED) |
| **Socios** | CRUD completo, roles (Admin/Secretaria/Directivo/Socio), nivel técnico, habilitación/inhabilitación individual y masiva (CSV), historial de cambios, cuotas, exportación CSV/PDF/hoja de firmas, retiro con eliminación de datos sensibles |
| **Gestión Documental** | Documentos legales versionados (Markdown) con ciclo de vida (borrador → activo); aceptaciones con trazabilidad legal (hash, IP, user-agent, timestamp); datos médicos por socio (`socio_medical_info`) con control de acceso estricto; contactos de emergencia en tabla separada; completitud de perfil como requisito de inscripción; documentos de riesgo por actividad; cumplimiento LOPDP Art. 23 |
| **Montañas y Rutas** | 40+ montañas del Ecuador, rutas multi-actividad (Alpinismo / Escalada / Trekking / Ciclismo), acceso por nivel técnico, planificador de rutas, documentos de permiso |
| **Salidas** | Planificación, inscripciones con validación de perfil completo y documento de riesgo por actividad, dignidades (Jefe de Salida, Conductor…), resumen médico de emergencia para Jefe de Salida, scheduler de transición de estados |
| **Informes** | Informe post-salida con segmentos de viaje, contactos, costos, alojamiento, reconocimientos (AMONESTADO/DESTACADO), generación y descarga de PDF |
| **Actas de reunión** | CRUD de actas con Full Text Search, importación desde archivo `.md`, asistentes, informes vinculados, generación y descarga de PDF |
| **Estadísticas** | Dashboard con KPIs, rankings de salidas y reuniones, historial por socio, estadísticas por montaña/ruta, búsqueda avanzada de participantes, estadísticas por período |
| **Notificaciones** | Alertas in-app (sin push): aprobaciones de inscripción pendientes, salidas sin jefe asignado, cumpleaños del día. Scheduler: promoción automática Juvenil → Socio Activo al cumplir 18 años |
| **Administración** | Gestión de usuarios y estados de acceso, dos tablas de auditoría append-only (`auditoria` + `audit_log`), eventos de seguridad, desbloqueo de cuentas, niveles de acceso por nivel técnico |
| **Contactos** | Directorio global de contactos (guías, transportistas, refugios) reutilizables entre salidas y rutas |
| **API Keys** | Generación de API keys con hash SHA-256, scope readonly, máximo 5 por usuario, revocación individual |
| **Asistente IA (MCP)** | Servidor Model Context Protocol para Claude Desktop/Code — 12 herramientas de solo lectura: montañas, rutas, salidas, informes y actas. Autenticado con API Keys (`sk-sadday-...`) |

---

## Inicio rápido

### Prerequisitos

- Docker y Docker Compose
- Java 21 (solo si corres el backend desde el IDE)
- Node.js 20+ con pnpm (solo para el frontend)
- [fvm](https://fvm.app) + Flutter 3.44.0 (solo para el mobile)
- [Infisical CLI](https://infisical.com/docs/cli/overview) — **opcional**: solo si quieres
  apuntar a infraestructura real en vez de a los contenedores locales

### Setup inicial (una sola vez)

```bash
git clone <repo>
cd sadday-app

# Generar claves RSA para JWT (si vas a usar la Opción B)
bash scripts/generate-keys.sh

# Opcional: solo si vas a apuntar a infraestructura real
infisical login
```

Genera `backend/src/main/resources/keys/private.pem` y `public.pem`.

**Para desarrollo local no hace falta Infisical.** `application-local.yml` tiene
valor por defecto para todo —base de datos, correo, storage— y los contenedores
usan esos mismos valores. Infisical se usa cuando se quiere apuntar a algo real,
y lo que inyecte **sobreescribe** los defaults locales:

```bash
infisical run --env=dev -- ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

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
| `sadday-minio` | `bitnamilegacy/minio` | Storage S3-compatible local. Crea el bucket al arrancar vía `MINIO_DEFAULT_BUCKETS` |
| `sadday-mailpit` | `axllent/mailpit` | Servidor SMTP + bandeja web para dev |
| `sadday-geoip-updater` | `ghcr.io/maxmind/geoipupdate` | Actualiza base GeoIP (perfil `geoip`, opcional) |

### 🏃‍♂️ Opción A: Showcase / Entorno de Pruebas (Todo en Docker)

Ideal para desarrolladores nuevos, QA, o simplemente para probar la app completa sin configurar entornos de desarrollo.
**Nota:** Este modo compila el código una sola vez. **No soporta hot-reload**, por lo que no es apto para programar activamente.

```bash
# Mac / Linux
./start-local.sh

# Windows (PowerShell)
.\start-local.ps1
```

El script se encarga de todo: verifica Docker, genera claves JWT faltantes, comprueba puertos y levanta todos los servicios.

Servicios disponibles tras levantar:

| Servicio | URL |
|---|---|
| Frontend (React) | `http://localhost:3000` |
| API REST | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Consola MinIO (storage local) | `http://localhost:9001` (minioadmin / minioadmin) |
| Mailpit (Testing de correos) | `http://localhost:8025` (Bandeja web) |

### 📁 Cómo están organizados los ficheros de Compose

| Fichero | Quién lo carga | Qué contiene |
|---|---|---|
| `docker-compose.yml` | los tres entornos | Definición base: servicios, puertos, volúmenes, healthchecks. **Ningún valor de entorno** |
| `docker-compose.override.yml` | solo local, **automáticamente** | Los valores de desarrollo: credenciales de MinIO, clave TOTP de dev, mailpit como SMTP |
| `docker-compose.staging.yml` | staging, con `-f` explícito | Valores de staging; secretos desde Infisical |
| `docker-compose.prod.yml` | producción, con `-f` explícito | Valores de producción; secretos desde Infisical |

Compose auto-carga `override.yml` junto al base cuando ejecutas `docker compose up` **sin flags** — por eso clonar el repo y levantarlo funciona sin configurar nada. Cuando se pasa `-f` explícito, Compose **no** carga el override, así que ningún valor de desarrollo puede llegar a staging ni a producción.

Para comprobar qué recibiría un entorno:

```bash
docker compose config                                                    # local
docker compose -f docker-compose.yml -f docker-compose.prod.yml config   # producción
```

### 🛠 Opción B: Desarrollo Activo (Híbrido con Hot-Reload)

Este es el flujo de trabajo para programar. La infraestructura corre en Docker, pero el código fuente lo ejecutas tú directamente en tu máquina.

**1. Levantar infraestructura base:**
```bash
docker compose up -d postgres minio mailpit
```

**2. Levantar el Backend (con debug):**
Abre la carpeta `backend/` en tu IDE (IntelliJ/Eclipse) y ejecuta la aplicación, o usa la terminal:
```bash
infisical run --env=dev -- ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**3. Levantar el Frontend (con Hot-Reload en puerto 5173):**
```bash
cd frontend
pnpm install
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
| **Autenticación** | JWT RS256 con claves asimétricas (private/public PEM). Access token de 15 min + refresh token rotativo. Claim `aud` validado en cada request. Detección de robo: si se reutiliza un refresh revocado, se invalidan todas las sesiones del usuario |
| **Contraseñas** | Argon2id con parámetros OWASP 2026 (salt 16 bytes, hash 32 bytes, memoria 19 MB, 2 iteraciones). Nunca se almacena en claro ni en logs |
| **2FA** | TOTP (Google Authenticator / Authy). Secret cifrado en BD con AES-256-GCM. Anti-replay: cada OTP solo válido una vez (NIST SP 800-63B §5.1.4.2) |
| **API Keys (MCP)** | Generadas con `SecureRandom` (32 bytes, base64url). Solo se almacena el hash SHA-256 — el raw se muestra una sola vez al crearse. Scope forzado a solo lectura (`SCOPE_readonly`). Máximo 5 keys activas por usuario |
| **Tokens y hashes** | Refresh tokens, password reset tokens y API keys: siempre SHA-256 en BD, nunca el valor real |
| **Cabeceras HTTP** | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Content-Security-Policy`, `Referrer-Policy: no-referrer`. HSTS habilitado solo en producción |
| **CORS** | Origen permitido configurado explícitamente por entorno (`APP_URL`). No hay wildcard |
| **Rate limiting** | Bucket4j + Caffeine — límites por IP en 7 endpoints: `/auth/login` (10/min), `/auth/forgot-password` (5/5min), `/auth/reset-password` (5/5min), `/auth/refresh` (60/min), `/auth/change-password` (5/10min), `/registro/complete` (10/10min), `/registro/token-info` (10/10min) |
| **Auditoría** | Dos tablas append-only: `auditoria` (seguridad general — login, logout, cambios de contraseña, acciones admin) y `audit_log` (módulo documental — aceptaciones legales, acceso a datos médicos, retiro de socio). El usuario de la app no tiene permisos UPDATE/DELETE sobre ninguna |
| **Anti-enumeración** | Recursos ajenos devuelven `404` en lugar de `403` para no revelar su existencia |
| **Secretos** | Gestionados con Infisical. Nunca en el repositorio ni en variables de entorno hardcodeadas |
| **TLS** | En producción el filtro `ApiKeyAuthFilter` rechaza requests sin HTTPS (`X-Forwarded-Proto`) |

### Escaneos de seguridad (CI/CD)

El pipeline de GitHub Actions ejecuta los siguientes escaneos automáticamente:

| Herramienta | Cuándo | Qué analiza |
|-------------|--------|-------------|
| **SonarCloud** | PRs y push a `main` y `develop` | Calidad de código, bugs, code smells y cobertura de tests |
| **Semgrep** | PRs y push a `main` y `develop` | SAST — análisis estático de vulnerabilidades en el código fuente |
| **Snyk** | Cada PR + lunes semanalmente | Vulnerabilidades CVE en dependencias. **Es el único check que la rama `develop` exige en verde para poder mergear** |
| **OWASP Dependency Check** | Lunes semanalmente | Dependencias Maven contra base NVD/CVE |
| **Trivy** | En cada deploy | Escanea las imágenes Docker y **bloquea el despliegue si encuentra un CRITICAL** con parche disponible |

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

Las inyecta Infisical (entorno `production`) en el despliegue.
`docker-compose.prod.yml` las declara **todas sin valor por defecto**: si falta
alguna, el arranque falla en lugar de caer a un valor de desarrollo.

La lista completa y verificable está en
[`docs/security/architecture/production-deployment-checklist.md`](docs/security/architecture/production-deployment-checklist.md).
Las críticas:

```bash
# BD
DB_NAME=sadday_app  DB_USER=...  DB_PASSWORD=...

# JWT
JWT_PRIVATE_KEY_PATH=/app/keys/private.pem
JWT_PUBLIC_KEY_PATH=/app/keys/public.pem
TOTP_ENCRYPTION_KEY=<openssl rand -base64 32>

# Email — Amazon SES
MAIL_HOST=email-smtp.us-east-1.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=<credencial SMTP de SES>
MAIL_PASSWORD=<credencial SMTP de SES>
MAIL_FROM=noreply@el-sadday.com
APP_URL=https://app.el-sadday.com

# Storage — AWS S3
S3_BUCKET=sadday-pdfs
S3_REGION=us-east-1
AWS_ACCESS_KEY_ID=<IAM access key>
AWS_SECRET_ACCESS_KEY=<IAM secret key>

# Admin inicial
ADMIN_INITIAL_PASSWORD=...
```

> **Producción NO usa `S3_ACCESS_KEY` ni `S3_SECRET_KEY`.**
> `docker-compose.prod.yml` las fija a cadena vacía a propósito, igual que
> `S3_ENDPOINT`. Con ellas vacías, `S3Config` usa `DefaultCredentialsProvider`,
> que resuelve las credenciales desde `AWS_*` o desde el IAM role de la
> instancia, contra AWS S3 estándar. Darles valor haría que la app usara
> credenciales estáticas y, en el caso del endpoint, que apuntara a un MinIO
> que allí no existe.

Para ver qué recibiría realmente el contenedor en cada entorno:

```bash
./scripts/envcheck.sh
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

La app envía correos en los siguientes casos:
- **Invitación de registro** → cuando la Secretaria da de alta a un nuevo socio
- **Verificación de email** → al completar registro o cambiar correo
- **Recuperación de contraseña** → flujo de reset por link
- **Alerta de seguridad** → login desde nuevo dispositivo, nuevo país, o actividad sospechosa
- **Country challenge** → código de verificación al detectar login desde país no reconocido

Todos usan Spring Mail (cliente SMTP) apuntando a Amazon SES.

Para configurar SES en `el-sadday.com`:
1. AWS Console → SES → Verified identities → verificar dominio `el-sadday.com`
2. Agregar registros DKIM (CNAME ×3) en Cloudflare DNS
3. Agregar `TXT @ "v=spf1 include:amazonses.com ~all"` en Cloudflare
4. SES → SMTP settings → Create SMTP credentials → copiar usuario y contraseña
5. SES → Account dashboard → Request production access (para salir del sandbox)

---

## Tests y verificación

La forma rápida de saber si algo está roto, en los tres módulos a la vez:

```bash
./scripts/check.sh              # backend + frontend + mobile + flutter analyze
./scripts/check.sh backend      # solo uno
```

Manda el output completo a ficheros de log y por pantalla deja solo el
veredicto y los tests rotos. El output crudo de esos cuatro comandos son unos
49.000 tokens; el resumen, unos 47 — relevante al trabajar con un agente.

```
backend (maven)        877 tests · 0 fallos · 0 errores
frontend (vitest)      20 passed (20)
mobile (flutter test)  +104: All tests passed
mobile (analyze)       No issues found

TODO OK
```

Lo que no se puede interpretar cuenta como fallo, nunca como OK.

Por módulo, directamente:

```bash
cd backend  && ./mvnw test                              # 877 tests
cd backend  && ./mvnw test -Dtest=ActaIntegrationTest   # una clase concreta
cd frontend && pnpm test                                # 20 tests (vitest)
cd mobile   && flutter test                             # 104 tests
```

El backend requiere el daemon de Docker activo: Testcontainers levanta
PostgreSQL automáticamente.

### Otros scripts de verificación

| Script | Para qué |
|---|---|
| `./scripts/deps.sh [base] [rama]` | Compara el árbol de dependencias del backend y **avisa si alguna versión retrocede**. Pensado para revisar PRs de dependencias sin hacerles checkout |
| `./scripts/envcheck.sh` | Renderiza los tres entornos de Compose y verifica que producción no hereda ningún valor de desarrollo |
| `./scripts/sonar.sh [issues]` | Estado de SonarCloud: quality gate, qué condición falla y métricas, sin abrir la web |
| `./scripts/alerts.sh [--ref rama]` | Alertas abiertas de Code Scanning por herramienta y severidad, **qué ha cambiado desde la última consulta**, y qué categorías han dejado de actualizarse (sus alertas no se cierran solas) |
| `./scripts/ci.sh [run_id]` | Último run de cada workflow y, con un id, los jobs y pasos fallidos con solo las líneas de error |

Los seis están además disponibles como skills de Claude Code en
`.claude/skills/`, junto con el subagente `dep-review` que tría PRs de
dependencias. El contexto del proyecto para agentes vive en
[`CLAUDE.md`](CLAUDE.md).

---

## Documentación

| Recurso | Descripción |
|---|---|
| [`backend/README.md`](backend/README.md) | Setup detallado del backend, variables de entorno, logs, seguridad |
| [`frontend/README.md`](frontend/README.md) | Setup del frontend, rutas, estructura de componentes |
| [`mobile/README.md`](mobile/README.md) | Setup de la app Flutter, flavors, build release, firma Android/iOS |
| [`endpoints.md`](endpoints.md) | Referencia completa de los endpoints de la API |
| [`docs/db/esquema_bdd.md`](docs/db/esquema_bdd.md) | Diagrama ER completo (Mermaid) |
| [`docs/security/`](docs/security/) | Threat model, diagramas STRIDE y flujos de autenticación |
| [`docs/flutter-mobile-spec.md`](docs/flutter-mobile-spec.md) | Especificación completa de la app mobile (Flutter), con requisitos OWASP MASVS y MITRE |
| `http://localhost:8080/swagger-ui.html` | Swagger UI interactivo (con la app corriendo) |
