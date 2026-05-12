# Flujo 15 — Pipelines de CI/CD (GitHub Actions)

## ¿Qué es CI/CD?

**CI (Integración Continua)** significa que cada vez que alguien sube código a GitHub, el sistema automáticamente compila, testea y analiza ese código. Si algo falla, se avisa antes de que llegue a producción.

**CD (Entrega Continua)** significa que si el código pasa todas las verificaciones, el sistema lo despliega automáticamente al servidor sin intervención manual.

En este proyecto hay cuatro archivos de configuración en `.github/`:

| Archivo | Cuándo corre | Para qué sirve |
|---|---|---|
| `workflows/ci.yml` | Push o PR al código | Compila, testea y analiza calidad |
| `workflows/deploy.yml` | Push a `main` o `develop` | Construye imágenes Docker y despliega |
| `workflows/security.yml` | Cada lunes + cambios en dependencias | Audita vulnerabilidades |
| `dependabot.yml` | Cada lunes automáticamente | Propone actualizaciones de dependencias |

---

## `ci.yml` — Calidad de código

**Se activa cuando:** se sube código a `main` o `develop`, o cuando se abre un Pull Request hacia `main`, siempre que los cambios sean en `backend/`, `frontend/` o `mcp/`.

**Si dos personas suben código al mismo tiempo:** el pipeline más viejo se cancela automáticamente y solo corre el más nuevo (`concurrency: cancel-in-progress: true`).

### Jobs (tareas) que ejecuta

```
push/PR
  │
  ├── test ─────────────────────── Backend: compilar + tests de integración
  │                                 (levanta PostgreSQL real con Testcontainers)
  │                                 Genera reporte de cobertura JaCoCo
  │
  ├── test-frontend ─────────────── Frontend: type check TypeScript + Vitest + build
  │
  ├── sonar ─────────────────────── (espera a que `test` termine)
  │                                 Sube análisis a SonarCloud: cobertura,
  │                                 bugs, code smells, duplicados
  │
  ├── semgrep ───────────────────── SAST: busca vulnerabilidades OWASP en el
  │                                 código Java, Spring, JavaScript y React.
  │                                 Resultados visibles en Security > Code Scanning
  │
  └── build-mcp ─────────────────── Type check y build del servidor MCP (TypeScript)
```

**Secrets requeridos:** `SONAR_TOKEN`, `SEMGREP_APP_TOKEN` (opcional).
**Variables requeridas:** `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION`.

---

## `deploy.yml` — Build, Scan y Deploy

**Se activa cuando:** se sube código a `main` o `develop` con cambios en `backend/`, `frontend/` o los archivos `docker-compose`.

También se puede disparar manualmente desde GitHub → Actions → *Run workflow*, eligiendo el ambiente (`staging` o `production`) y opcionalmente un tag de imagen específico.

### Flujo completo por rama

```
push a develop                          push a main
      │                                       │
      ▼                                       ▼
build-backend ────────────────── build-frontend (corren en paralelo)
      │                                       │
      │  1. Construye imagen Docker local      │
      │  2. Trivy escanea la imagen           │
      │     (falla si hay CRITICAL o HIGH)    │
      │  3. Push a GHCR                       │
      │  4. cosign firma la imagen            │
      │  5. Genera SBOM (inventario)          │
      │                                       │
      └───────────────┬───────────────────────┘
                      │
              ┌───────┴───────┐
              ▼               ▼
    deploy-staging     deploy (prod)
      (Proxmox VM)      (Lightsail)
      si es develop      si es main
              │               │
              │  1. Verifica firma cosign
              │  2. Copia docker-compose
              │  3. docker pull por digest
              │  4. docker compose up
              │  5. Health check API
```

### ¿Qué es Trivy?

Trivy es un escáner de seguridad que revisa la imagen Docker antes de publicarla. Analiza:
- El sistema operativo base (Alpine Linux) — busca CVEs conocidos
- Las dependencias del proyecto dentro de la imagen

Si encuentra vulnerabilidades `CRITICAL` o `HIGH`, el pipeline falla y **no se publica ni despliega la imagen**.

Los resultados aparecen en GitHub → Security → Code Scanning.

### ¿Qué es cosign?

cosign firma criptográficamente cada imagen Docker usando las credenciales de GitHub Actions (OIDC). Esto garantiza que la imagen que llega al servidor es exactamente la misma que construyó y aprobó el pipeline — nadie pudo modificarla en el camino.

### ¿Qué es SBOM?

SBOM (Software Bill of Materials) es un inventario completo de todos los componentes que contiene la imagen: qué librerías, versiones y dependencias. Es útil para auditorías de seguridad y cumplimiento.

### Imágenes Docker

Las imágenes se publican en **GHCR (GitHub Container Registry)**:
- Backend: `ghcr.io/<org>/sadday-app:<sha-del-commit>`
- Frontend: `ghcr.io/<org>/sadday-app-frontend:<sha-del-commit>`

En producción siempre se hace `docker pull` por **digest** (hash exacto de la imagen), no por tag. Esto evita que alguien pueda sustituir la imagen con el mismo tag.

**Secrets requeridos:**
- Entorno `staging`: `GHCR_READ_TOKEN`, `INFISICAL_TOKEN`
- Entorno `staging` (variable): `INFISICAL_PROJECT_ID`
- Entorno `production`: `LIGHTSAIL_SSH_KEY`, `LIGHTSAIL_HOST`, `LIGHTSAIL_USER`, `GHCR_READ_TOKEN`

---

## `security.yml` — Auditoría de dependencias

**Se activa cuando:** se modifica `pom.xml`, `pnpm-lock.yaml` o `package-lock.json`, o todos los lunes a las 07:00 UTC. También se puede disparar manualmente.

A diferencia de `ci.yml`, estos jobs **no bloquean** el merge aunque encuentren problemas — generan visibilidad para que el equipo los revise. Los resultados aparecen en Security → Code Scanning.

| Job | Herramienta | Qué analiza |
|---|---|---|
| `snyk` | Snyk | Dependencias Maven del backend vs base de datos de CVEs de Snyk |
| `snyk-frontend` | Snyk | Dependencias npm/pnpm del frontend |
| `snyk-mcp` | Snyk | Dependencias npm del MCP server |
| `owasp-dep-check` | OWASP Dependency Check | Dependencias Maven vs base NVD (National Vulnerability Database). No requiere cuenta externa. La primera ejecución tarda ~10 min descargando la base. |

**Secrets requeridos:** `SNYK_TOKEN`, `NVD_API_KEY`.

---

## `dependabot.yml` — Actualizaciones automáticas

No es un workflow que se ejecuta, sino una **instrucción para GitHub**. Dependabot revisa cada lunes si hay versiones nuevas de dependencias y abre Pull Requests automáticos con las actualizaciones.

| Ecosistema | Carpeta | PRs máximos por semana |
|---|---|---|
| Maven (backend) | `/backend` | 5 |
| npm/pnpm (frontend) | `/frontend` | 5 |
| npm (MCP) | `/mcp` | 3 |
| GitHub Actions | `/` | 5 |

**Excepción configurada:** Spring Boot no se actualiza automáticamente en versiones major (hay que hacerlo manualmente para controlar el timing del upgrade).

---

## Relación entre los pipelines

```
                 ┌─────────────────────────────────────────┐
  push           │  ci.yml                                 │
  ──────────────▶│  Calidad + Tests + SAST                 │
                 │  (no despliega)                         │
                 └─────────────────────────────────────────┘

                 ┌─────────────────────────────────────────┐
  push           │  deploy.yml                             │
  ──────────────▶│  Docker build + Trivy + Push + Deploy   │
                 │  (corre independientemente de ci.yml)   │
                 └─────────────────────────────────────────┘

  lunes 07:00    ┌─────────────────────────────────────────┐
  ──────────────▶│  security.yml                           │
                 │  Auditoría semanal de dependencias      │
                 └─────────────────────────────────────────┘

  lunes 08:00    ┌─────────────────────────────────────────┐
  ──────────────▶│  dependabot                             │
                 │  PRs automáticos de actualizaciones     │
                 └─────────────────────────────────────────┘
```

`ci.yml` y `deploy.yml` corren **en paralelo e independientemente** al hacer push. No hay dependencia entre ellos. Si quisieras que el deploy solo ocurra si el CI pasa, habría que configurar `workflow_run` como dependencia — por ahora no está así.

---

## Imágenes base y versiones de Alpine

Los Dockerfiles usan imágenes basadas en **Alpine Linux** por ser livianas y tener menos superficie de ataque.

| Imagen | Usada en | Etapa |
|---|---|---|
| `maven:3.9-eclipse-temurin-21-alpine` | `backend/Dockerfile` | Build (compila el JAR) |
| `eclipse-temurin:21-jre-alpine` | `backend/Dockerfile` | Runtime (imagen final) |
| `node:20-alpine3.23` | `frontend/Dockerfile` | Build (compila React) |
| `nginx:stable-alpine3.23` | `frontend/Dockerfile` | Runtime (sirve el HTML/JS) |

Trivy escanea únicamente la imagen final (runtime). Si reporta CVEs en Alpine, se resuelve actualizando la versión del tag (ej: `alpine3.23` → `alpine3.24` cuando esté disponible) o ignorando CVEs no explotables con un archivo `.trivyignore`.

---

## Configuración inicial de secrets y variables

Antes de que los pipelines funcionen completamente, hay que configurar los siguientes secrets y variables en GitHub.

### Dónde configurarlos

- **Secrets y variables de repositorio**: GitHub → Settings → Secrets and variables → Actions
- **Secrets de entorno**: GitHub → Settings → Environments → (staging o production) → Add secret

> Al crear variables, el nombre va **sin** el prefijo `vars.` — ese prefijo es solo la sintaxis del workflow. Por ejemplo, el nombre a ingresar es `SONAR_PROJECT_KEY`, no `vars.SONAR_PROJECT_KEY`.

---

### Secrets y variables de repositorio

Estos son iguales en todos los ambientes y se configuran una sola vez a nivel de repositorio.

| Nombre | Tipo | Para qué sirve | Dónde obtenerlo |
|---|---|---|---|
| `SONAR_TOKEN` | Secret | Autenticación con SonarCloud para subir análisis de calidad | sonarcloud.io → My Account → Security → Generate Token |
| `SONAR_PROJECT_KEY` | Variable | Identificador del proyecto en SonarCloud | SonarCloud → tu proyecto → Project Key |
| `SONAR_ORGANIZATION` | Variable | Nombre de la organización en SonarCloud | SonarCloud → tu organización |
| `SNYK_TOKEN` | Secret | Autenticación con Snyk para escanear vulnerabilidades en dependencias | snyk.io → Account Settings → API Token |
| `NVD_API_KEY` | Secret | Acceso a la base de datos NVD (National Vulnerability Database) del NIST — registro oficial de CVEs del gobierno de EE.UU. Lo usa OWASP Dependency Check. Sin la key funciona pero con rate limiting severo. | nvd.nist.gov/developers/request-an-api-key (gratuito) |
| `SEMGREP_APP_TOKEN` | Secret | Opcional. Conecta Semgrep con el dashboard cloud de semgrep.dev. Sin él, Semgrep igual corre en modo CLI y sube resultados a GitHub Code Scanning. | semgrep.dev → Settings → Tokens |
| `GHCR_READ_TOKEN` | Secret | PAT de GitHub que usan los servidores (Staging y producción) para hacer `docker pull` desde GHCR (GitHub Container Registry). El registro es privado y requiere autenticación. | GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → New token → marcar `read:packages` |

---

### Secrets de entorno — Staging

Configurar en: GitHub → Settings → Environments → `staging`

| Nombre | Qué es |
|---|---|
| `INFISICAL_TOKEN` | Machine identity token de Infisical para inyectar secretos de la app en el deploy |
| `INFISICAL_PROJECT_ID` | (Variable, no secret) ID del proyecto en Infisical |

---

### Secrets de entorno — Production

Configurar en: GitHub → Settings → Environments → `production`

| Nombre | Qué es |
|---|---|
| `LIGHTSAIL_SSH_KEY` | Clave privada SSH (PEM) para conectarse al servidor Lightsail |
| `LIGHTSAIL_HOST` | IP o hostname del servidor Lightsail |
| `LIGHTSAIL_USER` | Usuario SSH del servidor (normalmente `ubuntu`) |

---

> `GITHUB_TOKEN` no requiere configuración manual — GitHub lo genera automáticamente en cada ejecución del pipeline.
