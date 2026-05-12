# Gestión de secretos con Infisical — Sadday App

**Última actualización:** 2026-05-06
**Audiencia:** Desarrolladores y administrador del sistema.

Este documento describe cómo integrar Infisical como fuente centralizada de todos los secretos y variables de entorno del proyecto, reemplazando los archivos `.env` locales.

---

## Por qué Infisical

- Secretos centralizados por ambiente (`development`, `staging`, `production`) con un solo lugar de verdad
- Sin archivos `.env` con credenciales reales en disco ni en git
- Inyección automática en Docker Compose, GitHub Actions y producción
- Historial de cambios y auditoría de accesos a secretos

---

## Mapeo de ambientes

| Infisical | Spring profile | Dónde corre |
|-----------|---------------|-------------|
| `development` | `local` | Máquina local del desarrollador |
| `staging` | `staging` | VM Proxmox |
| `production` | `prod` | AWS Lightsail |

> Los nombres de Infisical (`development`, `staging`, `production`) y los Spring profiles (`local`, `staging`, `prod`) están alineados. Los Spring profiles corresponden a archivos de configuración en el código: `application-local.yml`, `application-staging.yml`, `application-prod.yml`.

---

## Fase 1 — Setup del proyecto en Infisical

1. Crear cuenta en [app.infisical.com](https://app.infisical.com)
2. Crear proyecto: `sadday-app`
3. Infisical crea por defecto los environments `development`, `staging`, `production` — usar esos nombres
4. Instalar la CLI:

```bash
# Debian / Ubuntu
curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh' | sudo -E bash
sudo apt install infisical

# macOS
brew install infisical/get-cli/infisical
```

5. Autenticarse:

```bash
infisical login
```

6. Inicializar el proyecto en la raíz del monorepo:

```bash
cd sadday-app/
infisical init    # genera .infisical.json — ya está en .gitignore ✅
```

El `.infisical.json` resultante debe quedar con `defaultEnvironment` en `development`:

```json
{
    "workspaceId": "<id-del-proyecto>",
    "defaultEnvironment": "development",
    "gitBranchToEnvironmentMapping": null
}
```

---

## Fase 2 — Inventario de secretos

### Cómo cargar el ambiente `development`

Subir el archivo `backend/.env.example` directamente desde la UI de Infisical — tiene todos los nombres de variables con valores de desarrollo ya listos. Después del import, eliminar manualmente las dos variables de ruta de claves (no se necesitan en development, ver explicación abajo):

- Eliminar `JWT_PRIVATE_KEY_LOCATION`
- Eliminar `JWT_PUBLIC_KEY_LOCATION`

### Secretos reales (valores distintos por ambiente)

| Secret | `development` | `staging` | `production` |
|--------|--------------|-----------|--------------|
| `DB_PASSWORD` | `sadday_password_local123` | valor staging | contraseña fuerte |
| `DB_USER` | `sadday_admin` | idem | idem |
| `DB_HOST` | `localhost` / `postgres` | host staging | host prod |
| `DB_NAME` | `sadday_app` | idem | idem |
| `JWT_PRIVATE_KEY` ⚠️ | — no aplica — | contenido del `private.pem` | par prod exclusivo |
| `JWT_PUBLIC_KEY` ⚠️ | — no aplica — | contenido del `public.pem` | par prod exclusivo |
| `JWT_PRIVATE_KEY_LOCATION` | — no aplica — | `file:/app/keys/private.pem` | `file:/app/keys/private.pem` |
| `JWT_PUBLIC_KEY_LOCATION` | — no aplica — | `file:/app/keys/public.pem` | `file:/app/keys/public.pem` |
| `TOTP_ENCRYPTION_KEY` | `AAAA...` (ceros, solo dev) | clave staging real | clave prod real |
| `MAIL_USERNAME` | _(vacío)_ | _(vacío)_ | credencial SMTP SES |
| `MAIL_PASSWORD` | _(vacío)_ | _(vacío)_ | credencial SMTP SES |
| `MAIL_HOST` | `localhost` | `localhost` | `email-smtp.us-east-1.amazonaws.com` |
| `MAIL_FROM` | `noreply@sadday-local.test` | `noreply@staging.sadday.local` | `noreply@el-sadday.com` |
| `S3_ACCESS_KEY` | `minioadmin` | `minioadmin` | IAM Access Key real |
| `S3_SECRET_KEY` | `minioadmin` | `minioadmin` | IAM Secret Key real |
| `S3_BUCKET` | `sadday-local` | `sadday-staging` | `sadday-pdfs` |
| `S3_ENDPOINT` | `http://localhost:9000` | `http://minio:9000` | _(vacío = AWS real)_ |
| `APP_URL` | `http://localhost:5173` | `http://staging.sadday.local` | `https://app.el-sadday.com` |
| `ADMIN_INITIAL_PASSWORD` | _(no aplica — DevDataInitializer)_ | contraseña staging | contraseña fuerte prod |

> ⚠️ `JWT_PRIVATE_KEY` y `JWT_PUBLIC_KEY` son **variables nuevas**, no existen en el `.env.example`. Se agregan manualmente solo en `staging` y `production`. Ver Fase 3 para el detalle.

> Generar claves AES-256 con: `openssl rand -base64 32`

### Variables de configuración (no secretas, conveniente tenerlas en Infisical)

| Variable | `development` | `staging` | `production` |
|----------|--------------|-----------|--------------|
| `DB_PORT` | `5432` | `5432` | `5432` |
| `MAIL_PORT` | `1025` | `1025` | `587` |
| `JWT_ISSUER` | `sadday-app` | idem | idem |
| `JWT_ACCESS_EXPIRATION` | `900` | idem | idem |
| `JWT_REFRESH_EXPIRATION` | `2592000` | idem | idem |
| `COOKIE_SECURE` | `false` | `false` | `true` |
| `ADMIN_USERNAME` | `admin` | `admin` | `admin` |

---

## Fase 3 — Caso especial: claves RSA (archivos PEM)

### Por qué `development` no necesita estas variables

`application-local.yml` tiene la ruta de las claves **hardcodeada en el YAML**:

```yaml
# application-local.yml
sadday:
  jwt:
    private-key-location: classpath:keys/private.pem   # valor fijo, no usa ${...}
    public-key-location:  classpath:keys/public.pem
```

Spring lee los archivos directamente desde `src/main/resources/keys/` sin pasar por ninguna variable de entorno. Por eso en Infisical `development` no hay nada que configurar para las claves RSA.

En `staging` y `production`, el YAML usa `${JWT_PRIVATE_KEY_LOCATION}` como placeholder que sí requiere la variable de entorno, y las claves no viven en el classpath del contenedor.

### Variables necesarias en `staging` y `production`

Para que las claves funcionen en contenedor se necesitan **4 variables** por ambiente:

| Variable | Descripción |
|----------|-------------|
| `JWT_PRIVATE_KEY` | Contenido completo del `private.pem` (multi-línea) |
| `JWT_PUBLIC_KEY` | Contenido completo del `public.pem` (multi-línea) |
| `JWT_PRIVATE_KEY_LOCATION` | `file:/app/keys/private.pem` |
| `JWT_PUBLIC_KEY_LOCATION` | `file:/app/keys/public.pem` |

### Cómo guardar los PEM en Infisical

En la consola de Infisical, crear dos secretos de tipo multi-línea en `staging` y `production`:

- `JWT_PRIVATE_KEY` → pegar el contenido completo del `private.pem`:
  ```
  -----BEGIN RSA PRIVATE KEY-----
  MIIEowIBAAKCAQEA...
  -----END RSA PRIVATE KEY-----
  ```
- `JWT_PUBLIC_KEY` → pegar el contenido completo del `public.pem`:
  ```
  -----BEGIN PUBLIC KEY-----
  MIIBIjANBgkqhkiG9w0B...
  -----END PUBLIC KEY-----
  ```

### Crear `backend/entrypoint.sh`

Este script escribe los PEM files antes de que Spring Boot arranque. Spring Boot sigue leyendo desde `file:/app/keys/private.pem` sin ningún cambio de configuración.

```bash
#!/bin/sh
set -e

# Escribir claves RSA desde variables de entorno
mkdir -p /app/keys
printf '%s' "$JWT_PRIVATE_KEY" > /app/keys/private.pem
printf '%s' "$JWT_PUBLIC_KEY"  > /app/keys/public.pem
chmod 600 /app/keys/private.pem

exec java -jar /app/app.jar
```

### Actualizar el `Dockerfile` del backend

Reemplazar el `CMD` o `ENTRYPOINT` existente:

```dockerfile
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
```

---

## Fase 4 — Integración por ambiente

### Development (desde IDE o terminal)

```bash
# Arrancar backend con secretos inyectados
infisical run --env=development -- ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# O con Docker Compose completo
infisical run --env=development -- docker-compose up --build
```

**Alternativa — Plugin de IntelliJ:**
Instalar [Infisical Secrets Manager](https://plugins.jetbrains.com/plugin/22817-infisical) en IntelliJ IDEA. Permite sincronizar secretos directamente en las Run Configurations sin tocar la terminal.

### GitHub Actions (CI/CD)

1. Crear una **Machine Identity** en Infisical (Project Settings → Machine Identities)
2. Guardar `INFISICAL_CLIENT_ID` y `INFISICAL_CLIENT_SECRET` como secrets en GitHub (Settings → Secrets)
3. Agregar al workflow:

```yaml
- name: Fetch secrets from Infisical
  uses: Infisical/secrets-action@v1
  with:
    client-id: ${{ secrets.INFISICAL_CLIENT_ID }}
    client-secret: ${{ secrets.INFISICAL_CLIENT_SECRET }}
    env-slug: "production"
    project-slug: "sadday-app"
```

Los secretos quedan disponibles como variables de entorno para los pasos siguientes.

### Producción (Lightsail)

Instalar la CLI de Infisical en el servidor y usar una **Machine Identity** (no tokens personales) para autenticación desatendida.

```bash
# Instalar CLI en el servidor (una sola vez)
curl -1sLf 'https://dl.cloudsmith.io/public/infisical/infisical-cli/setup.deb.sh' | sudo -E bash
sudo apt install infisical

# Levantar con secretos inyectados
infisical run --env=production \
  --projectId=<id-del-proyecto> \
  --token=$INFISICAL_TOKEN \
  -- docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

> **Alternativa recomendada a largo plazo:** usar el [Infisical Agent](https://infisical.com/docs/infisical-agent/overview), un proceso daemon que mantiene secretos sincronizados como archivos en el servidor. Útil si los secretos rotan con frecuencia.

---

## Fase 5 — Limpieza final

Una vez que Infisical está funcionando en los tres ambientes:

1. **Generar un nuevo par RSA** para staging y production (los actuales solo estuvieron en la máquina local, nunca en git, pero es buena práctica tener pares exclusivos por ambiente):
   ```bash
   bash scripts/generate-keys.sh   # genera nuevo par
   ```
   Subir el contenido de cada archivo a Infisical en el ambiente correspondiente.

2. **Eliminar los archivos `.env` locales** que hayas creado — ya no son necesarios.

3. **Mantener los `.env.example`** — documentan qué variables existen; son útiles como referencia sin contener valores reales.

4. **Actualizar `secret-rotation.md`**: los pasos que dicen "actualizar `.env`" pasan a decir "actualizar el secreto en Infisical y reiniciar el servicio".

---

## Relación con otros documentos

| Documento | Relación |
|-----------|----------|
| [`secret-rotation.md`](secret-rotation.md) | Cuándo y cómo rotar cada secreto — sus pasos de "actualizar `.env`" aplican ahora sobre Infisical |
| [`production-deployment-checklist.md`](production-deployment-checklist.md) | Lista de verificación previa al deploy — incluir "secretos cargados en Infisical production" |
| [`backend/.env.example`](../../../backend/.env.example) | Referencia de qué variables existen; no contiene valores reales |
