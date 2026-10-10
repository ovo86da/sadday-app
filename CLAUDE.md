# Sadday App — guía para Claude Code

Sistema de gestión del Club de Montaña Sadday (`el-sadday.com`). Monorepo, todo
implementado, en fase de QA. Nada en producción todavía.

Este fichero se carga en cada sesión: contiene **solo lo que no se deduce
mirando el código**. Lo demás está en `docs/` y se consulta cuando hace falta.

## Módulos

| Ruta | Stack |
|---|---|
| `backend/` | Java 21, Spring Boot 4.0.8, PostgreSQL 16, Flyway (V1–V19), JWT RS256, Argon2id, 2FA TOTP |
| `frontend/` | React 19, TypeScript, Vite, Tailwind, Zustand, TanStack Query, pnpm |
| `mobile/` | Flutter, Riverpod, go_router, dio. Flavors: `dev`, `staging`, `prod` |
| `mcp/` | Servidor MCP en Node/TS, herramientas de solo lectura |

Roles del sistema: `SOCIO` · `DIRECTIVO` · `SECRETARIA` · `ADMIN`

## Levantar en local

```bash
./start-local.sh                              # todo en Docker (sin hot-reload)
docker compose up -d postgres minio mailpit   # solo infra, para desarrollo
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && pnpm dev                       # :5173
```

| | URL |
|---|---|
| Frontend Docker / Vite | `:3000` / `:5173` |
| API · Swagger | `:8080/api/v1` · `:8080/swagger-ui/index.html` |
| Mailpit (captura correos) · MinIO | `:8025` · `:9001` |

Admin local: `admin` / `Admin123!`

**Infisical no hace falta en local** — `application-local.yml` tiene default para
todo (verificado arrancando con el entorno vacío). Se usa cuando se quiere
apuntar a infraestructura real en lugar de a los contenedores:

```bash
infisical run --env=dev -- ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Ojo: lo que Infisical traiga **sobreescribe** los defaults locales. Si su
`MAIL_HOST` apunta a SES, se envían correos de verdad en vez de quedarse en
mailpit; si `CORS_ORIGINS` no incluye `localhost:5173`, el login falla con 403.
Ver qué inyectaría, sin aplicarlo:

```bash
infisical run --env=dev -- printenv | grep -E "DB_|MAIL_|S3_|APP_URL|CORS_"
```

## Trampas que ya han costado tiempo

- **No existe el servicio `minio-init`.** La imagen crea el bucket sola vía
  `MINIO_DEFAULT_BUCKETS`.
- **La imagen de MinIO es `bitnamilegacy/minio`,** no `minio/minio`: MinIO
  retiró sus repos de Docker Hub. No "corregirlo" de vuelta.
- **Si MinIO no arranca con `Permission denied` en `/bitnami/minio/data`,** el
  volumen lo escribió la imagen oficial (como root) y la de Bitnami corre como
  UID 1001. El contenedor sale con 1 y nunca pasa a *healthy*. Se arregla
  empezando de cero: `./start-local.sh --clean`, o bien
  `docker run --rm -v sadday-app_sadday-minio-data:/d alpine chown -R 1001:1001 /d`
  si se quiere conservar lo que haya dentro.
- **Los compose se superponen.** `docker compose up` carga base +
  `override.yml` (valores de desarrollo). Staging y prod pasan `-f` explícito y
  **nunca** cargan el override. No poner valores de entorno en el fichero base.
- **`depends_on` se fusiona, no se reemplaza.** Redefinirlo en un override no
  quita dependencias del base.
- **Overrides del `pom.xml`:** `postgresql.version` está fijado por un CVE.
  Al subir Spring Boot, comprobar si el parent ya trae una versión **mayor** —
  si la trae, el override degrada y hay que quitarlo.
- **El backend no recarga solo** (sin devtools): hay que reiniciar el `mvnw`.
- **Producción usa AWS S3**, no MinIO. Decisión cerrada.

## Flujo de trabajo

`develop` está **protegida**: todo entra por PR y exige el check
`security/snyk (ovo86da)` en verde. Un `git push` directo se rechaza.

```
rama → PR a develop → Snyk + Semgrep → merge → staging → (humano) → main → prod
```

- **Commits y código en español. Issues, PRs y comentarios de GitHub en inglés.**
- **Nunca añadir `Co-Authored-By` de Claude** a los commits.
- Cambios funcionales: ver `docs/feature_request/README.md`. Siguiente FR libre
  lo indica ese índice.
- Bugs y CVEs → issues de GitHub. Cambios de comportamiento → un FR.

## Verificar antes de dar algo por bueno

```bash
cd backend && ./mvnw test        # 877 tests, deben pasar todos
cd mobile  && flutter test       # 104 tests
cd mobile  && flutter analyze    # debe salir "No issues found"
docker compose -f docker-compose.yml -f docker-compose.prod.yml config   # render de prod
```

El panel de problemas del IDE **no** es fuente de verdad: `.vscode/settings.json`
silencia los falsos positivos y aun así quedan avisos informativos. El build y
la CI mandan.

## Documentación

| Para | Dónde |
|---|---|
| Cómo funciona cada proceso | `docs/flujos/` (índice en su README) |
| Cambios funcionales y su estado | `docs/feature_request/` |
| Seguridad, threat model, despliegue | `docs/security/` |
| Esquema de BD y endpoints | `docs/db/`, `endpoints.md` |

## Decisiones cerradas — no volver a plantearlas

- El repo es **público a propósito**. Auditado: sin secretos en el historial,
  sin PII, sin IPs de servidores.
- **Sin certificate pinning** en mobile: la CA del sistema es suficiente.
- Producción en **AWS S3**; MinIO solo en local y staging.
