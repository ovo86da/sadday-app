---
name: envcheck
description: Renderiza la configuración efectiva de los tres entornos de Docker Compose y verifica que producción no hereda ningún valor de desarrollo. Usar al tocar cualquier docker-compose*.yml, antes de desplegar, y al revisar PRs de infraestructura.
allowed-tools: Bash(./scripts/envcheck.sh) Read
---

# Configuración efectiva por entorno

!`./scripts/envcheck.sh`

## Cómo interpretarlo

Una línea por entorno, con el perfil de Spring activo, las dependencias del
servicio `api` y cuántos valores de desarrollo contiene.

Lo que debe verse:

| Entorno | Perfil | depends_on | Valores de desarrollo |
|---|---|---|---|
| local | `local` | minio, postgres | **muchos** — son los suyos |
| staging | `staging` | minio, postgres | **1** (mailpit, a propósito) |
| prod | `prod` | **solo postgres** | **0** |

`PROBLEMAS: n` es un fallo. En producción se listan el valor filtrado y la
clave exacta que lo trae.

Que local tenga muchos valores de desarrollo **es correcto**: significa que
`docker-compose.override.yml` se está cargando. Si bajaran a cero, el entorno
local estaría roto.

## Por qué existe

Los tres entornos cargan `docker-compose.yml`, y Compose **fusiona** los
bloques `environment` clave por clave. Toda clave que el fichero del entorno no
mencionara llegaba a producción con su default de desarrollo.

Lo que producción recibía antes de arreglarlo:

```
TOTP_ENCRYPTION_KEY: 6zwG8t6FdMeIGdSLpsAZ...   ← clave AES en un repo público
S3_ACCESS_KEY: minioadmin
S3_ENDPOINT: http://minio:9000
MAIL_HOST: mailpit
```

Y peor: esos valores **anulaban los defaults correctos** de
`application-prod.yml`, porque la variable sí estaba definida. `MAIL_PORT:587`
nunca aplicaba, porque llegaba un 1025.

Nada lo detectaba. Se encontró leyendo los ficheros a mano.

## Detalle que importa

Prod se renderiza **sin variables de entorno a propósito**. Es el peor caso
real —Infisical no provee nada— y es exactamente cuando los defaults del base
se manifiestan. Si en ese escenario no aparece ningún valor de desarrollo,
tampoco aparecerá cuando Infisical sí funcione.

También comprueba dos invariantes propias de producción: que no dependa de
MinIO (usa AWS S3) y que `S3_ENDPOINT` vaya vacío, que es lo que hace a
`S3Config` usar `DefaultCredentialsProvider` contra S3 real.

## Notas

- Necesita Docker corriendo; si no, sale con 2.
- No arranca nada: solo usa `docker compose config`, que renderiza sin
  levantar contenedores.
