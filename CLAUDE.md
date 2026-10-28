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
  `docker run --rm -v sadday-app_sadday-minio-data:/d alpine chmod -R g+w /d`
  si se quiere conservar lo que haya dentro. Es `g+w`, no `chown`: la imagen
  deja el directorio como `root:root` con escritura de grupo, y el proceso
  corre con uid 1001 **y gid 0**. Docker solo copia los permisos de la imagen
  dentro de un volumen *vacío*; por eso falla al reutilizar uno con datos.
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

**Usar los scripts, no los comandos crudos.** Dan la misma respuesta resumida,
y la salida cruda de estas herramientas consume una fracción apreciable de la
ventana de contexto para acabar diciendo "877 pasaron".

| Script / skill | Responde a | Crudo → resumen |
|---|---|---|
| `./scripts/dev.sh` · `/dev` | ¿está vivo el entorno y **sirve el backend mi código**? | 820 → 57 |
| `./scripts/check.sh [módulo]` · `/check` | ¿está roto algo? (backend, frontend, mobile, analyze) | 49.000 → 47 |
| `./scripts/ci.sh [run_id]` · `/ci` | ¿qué workflow está en rojo y por qué? | 121.000 → 250 |
| `./scripts/alerts.sh` · `/alerts` | ¿qué alertas de seguridad hay, y qué es nuevo? | 145.000 → 290 |
| `./scripts/sonar.sh [issues\|--pr N]` · `/sonar` | ¿qué dice SonarCloud? Con `--pr N`, el gate de **ese** PR y qué ficheros traen código nuevo sin cubrir | — |
| `./scripts/mutar.sh` · `/mutar` | ¿los tests detectan de verdad que el código se rompa? | — |
| `./scripts/deps.sh [base] [rama]` · `/deps` | ¿este PR de dependencias degrada alguna versión? | — |
| `./scripts/envcheck.sh` · `/envcheck` | ¿producción hereda algún valor de desarrollo? | — |

Y dos sin skill, porque se usan una vez cada mucho:

```bash
node scripts/generar-umbrales.mjs              # umbrales del GPX → TS y Dart
cd frontend && RECALC_CLAVE='…' node --experimental-strip-types \
  scripts/recalcular-desniveles.mjs --usuario admin   # solo informa
```

**Los umbrales del análisis de GPX se editan en `shared/umbrales.json`,** nunca
en los `umbrales.g.*`, que se generan. La CI lo verifica. La fórmula está
implementada dos veces —TypeScript y Dart, porque el cálculo ocurre en el
dispositivo— y los ficheros de `test-fixtures/gpx/` existen para que no se
desincronicen. Dos de ellos son canarios que **oscilan** a propósito: en una
subida monótona el umbral no cambia el total, solo cómo se agrupa, así que una
escalera no detecta un cambio de umbral. Se descubrió probándolo.

Cada uno lleva su propia documentación en `.claude/skills/<nombre>/SKILL.md`,
incluido **por qué existe** y cómo interpretar su salida.

Y tres subagentes, en `.claude/agents/`:

| Agente | Para | |
|---|---|---|
| `dep-review` | triar PRs de dependencias | solo lectura |
| `sec-review` | revisar una rama buscando fallos de control de acceso, IDOR y validación que los escáneres no ven | solo lectura |
| `e2e` | escribir, arreglar y **ejecutar** los tests de Playwright | escribe en `frontend/e2e/` |

`sec-review` existe porque Snyk, Semgrep y Sonar son malos en A01 de OWASP: los
dos fallos reales del proyecto —un socio borrando documentos de otro, y la
descarga sin cabecera `Authorization`— no los vio ninguna herramienta. `e2e`
existe porque los tests escritos sin ejecutarlos no funcionan, y aquí ya entró
una suite con tres `fixme` y un test que se saltaba en silencio.

Los comandos crudos siguen ahí cuando el resumen no basta —`./mvnw test`,
`flutter test`, `flutter analyze`, `docker compose … config`— y cada script
dice en qué fichero dejó el log completo.

**Regla compartida por todos:** lo que no se puede interpretar cuenta como
**fallo**, nunca como "todo bien". Un resumidor que oculta un problema es peor
que no tenerlo. Si se modifica alguno, mantenerla.

`mutar.sh` es esa regla aplicada a los tests: **un test que pasa no prueba nada
hasta que se comprueba que se pone rojo** al romper el código que vigila. Aplica
una mutación, exige que los tests fallen y restaura el fichero. Si la mutación no
entra, es fallo —no «no aplicó»—, porque hacerlo a mano ya dio dos veces un
resultado falso en la dirección cómoda. Las mutaciones de un FR grande se dejan
escritas en `docs/mutaciones/`.

El panel de problemas del IDE **no** es fuente de verdad: `.vscode/settings.json`
silencia los falsos positivos y aun así quedan avisos informativos. El build y
la CI mandan.

**El backend no recarga en caliente.** Tras editar un `.java` sigue sirviendo
el anterior, y nada lo indica: la API responde y los tests pasan. `/dev` lo
detecta comparando la fecha de los fuentes con la hora de arranque del proceso.
Ya provocó dar por buena una prueba contra código viejo.

**Antes de mergear un cambio en un workflow, lanzarlo sobre su propia rama:**
`gh workflow run <fichero>.yml --ref <rama>`. Mergear y esperar a ver qué pasa
costó tres intentos fallidos en `security.yml`.

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
