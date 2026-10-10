#!/usr/bin/env bash
# =============================================================================
# envcheck.sh — verifica que la configuración efectiva de cada entorno es la
#               esperada, y en particular que producción no hereda ni un valor
#               de desarrollo.
#
#   ./scripts/envcheck.sh
#
# Existe por un fallo real: los tres entornos cargan docker-compose.yml, y
# Compose fusiona los bloques `environment` clave por clave. Toda clave que el
# fichero del entorno no mencionara llegaba a producción con su default de
# desarrollo — incluida la clave AES de los secretos TOTP, que está commiteada
# en un repo público.
#
# Se renderiza prod SIN variables de entorno a propósito: es el peor caso real
# (Infisical no provee nada) y es justo cuando los defaults se manifiestan.
#
# Sale con 1 si producción contiene algún valor de desarrollo.
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.."
cd "$ROOT"

render() { env -i PATH="$PATH" HOME="$HOME" docker compose "$@" config 2>/dev/null; }

docker info >/dev/null 2>&1 || { echo "Docker no está corriendo" >&2; exit 2; }

for f in docker-compose.yml docker-compose.override.yml docker-compose.staging.yml docker-compose.prod.yml; do
    [[ -f "$f" ]] || { echo "falta $f" >&2; exit 2; }
done

render                                                        > /tmp/envcheck.local.yml
render -f docker-compose.yml -f docker-compose.staging.yml    > /tmp/envcheck.staging.yml
render -f docker-compose.yml -f docker-compose.prod.yml       > /tmp/envcheck.prod.yml

python3 - <<'PY'
import re, sys

# Marcadores de desarrollo. Si cualquiera aparece en el entorno del servicio
# api de producción, algo del compose base se está filtrando.
DEV = {
    "minioadmin":                 "credenciales de MinIO",
    "mailpit":                    "servidor SMTP de desarrollo",
    "sadday_password_local123":   "contraseña de BD local",
    "6zwG8t6FdMeIGdSLpsAZ":       "clave TOTP de desarrollo (commiteada)",
    "classpath:keys/":            "claves JWT del classpath",
    "sadday-local":               "bucket local",
    "sadday.local":               "dominio de correo local",
    "sadday-app-local":           "issuer JWT local",
    "localhost:":                 "URL local",
}

def api_env(path):
    s = open(path).read()
    m = re.search(r'^  api:\n(.*?)(?=^  \w|\Z)', s, re.S | re.M)
    if not m: return None, None
    blk = m.group(1)
    em = re.search(r'^    environment:\n(.*?)(?=^    \w|\Z)', blk, re.S | re.M)
    env = {}
    if em:
        for line in em.group(1).splitlines():
            if ':' in line:
                k, _, v = line.strip().partition(':')
                env[k] = v.strip().strip('"')
    dm = re.search(r'^    depends_on:\n(.*?)(?=^    \w|\Z)', blk, re.S | re.M)
    deps = sorted(re.findall(r'^      ([\w-]+):', dm.group(1), re.M)) if dm else []
    return env, deps

fallos = 0
for nombre, path, perfil in (("local",   "/tmp/envcheck.local.yml",   "local"),
                             ("staging", "/tmp/envcheck.staging.yml", "staging"),
                             ("prod",    "/tmp/envcheck.prod.yml",    "prod")):
    env, deps = api_env(path)
    if env is None:
        print(f"{nombre:9} ⚠ no se pudo leer el servicio api"); fallos += 1; continue

    encontrados = sorted({d for k, v in env.items() for d, d2 in DEV.items() if d in str(v)})
    activo = env.get("SPRING_PROFILES_ACTIVE", "?")
    marca = "✗" if activo != perfil else " "
    if activo != perfil: fallos += 1

    print(f"{nombre:9} perfil={activo}{marca}  depends_on={','.join(deps) or '—'}  "
          f"valores de desarrollo: {len(encontrados)}")

    if nombre == "prod" and encontrados:
        fallos += 1
        for d in encontrados:
            claves = [k for k, v in env.items() if d in str(v)]
            print(f"             ⚠ {DEV[d]} → {', '.join(claves)}")
    elif nombre == "prod":
        # invariantes adicionales que solo aplican a producción
        if "minio" in deps:
            print("             ⚠ prod depende de MinIO; debe usar AWS S3"); fallos += 1
        if env.get("S3_ENDPOINT", "x") != "":
            print(f"             ⚠ S3_ENDPOINT debería ir vacío, está '{env.get('S3_ENDPOINT')}'"); fallos += 1

print()
print("CONFIGURACIÓN CORRECTA" if fallos == 0 else f"PROBLEMAS: {fallos}")
sys.exit(1 if fallos else 0)
PY
