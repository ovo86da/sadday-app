#!/usr/bin/env bash
# =============================================================================
# sonar.sh — estado del análisis de SonarCloud, resumido.
#
#   ./scripts/sonar.sh [--issues]     --issues añade los problemas abiertos
#
# Devuelve el quality gate, las condiciones que fallan y las métricas clave,
# en lugar de mandarte al navegador.
#
# TOKEN (opcional). El proyecto es público y la API responde sin autenticar;
# el token solo hace falta si deja de serlo o para subir el límite de
# peticiones. Se busca en este orden:
#
#   1. $SONAR_TOKEN del entorno
#        → sirve para  infisical run --env=dev -- ./scripts/sonar.sh
#   2. llavero de macOS, servicio "sadday-sonar-token"
#        → guardarlo con:
#          security add-generic-password -a "$USER" -s sadday-sonar-token -w
#
# Nunca se lee de un fichero ni se imprime.
# =============================================================================
set -uo pipefail

PROJECT="${SONAR_PROJECT_KEY:-ovo86da_sadday-app}"
API="https://sonarcloud.io/api"

TOKEN="${SONAR_TOKEN:-}"
if [[ -z "$TOKEN" ]] && command -v security >/dev/null 2>&1; then
    TOKEN="$(security find-generic-password -a "$USER" -s sadday-sonar-token -w 2>/dev/null || true)"
fi
AUTH=(); [[ -n "$TOKEN" ]] && AUTH=(-u "$TOKEN:")

# ${AUTH[@]+...} evita el "unbound variable" de bash 3.2 con arrays vacíos
get() { curl -s --max-time 20 ${AUTH[@]+"${AUTH[@]}"} "$API/$1"; }

QG="$(get "qualitygates/project_status?projectKey=$PROJECT")"
if ! echo "$QG" | python3 -c "import json,sys; json.load(sys.stdin)['projectStatus']" >/dev/null 2>&1; then
    echo "no se pudo consultar SonarCloud (proyecto '$PROJECT')" >&2
    echo "$QG" | head -c 200 >&2; echo >&2
    exit 2
fi

MEAS="$(get "measures/component?component=$PROJECT&metricKeys=coverage,bugs,vulnerabilities,code_smells,security_hotspots,duplicated_lines_density,ncloc")"
ISSUES=""
[[ "${1:-}" == "--issues" ]] && ISSUES="$(get "issues/search?componentKeys=$PROJECT&resolved=false&ps=20&s=SEVERITY&asc=false")"

QG="$QG" MEAS="$MEAS" ISSUES="$ISSUES" TOKENSET="$([[ -n "$TOKEN" ]] && echo si || echo no)" python3 <<'PY'
import json, os, sys

qg = json.loads(os.environ["QG"])["projectStatus"]
meas = {m["metric"]: m.get("value") for m in
        json.loads(os.environ["MEAS"]).get("component", {}).get("measures", [])}

NOMBRE = {
    "new_reliability_rating": "fiabilidad (código nuevo)",
    "new_security_rating": "seguridad (código nuevo)",
    "new_maintainability_rating": "mantenibilidad (código nuevo)",
    "new_coverage": "cobertura (código nuevo)",
    "new_duplicated_lines_density": "duplicación (código nuevo)",
    "new_security_hotspots_reviewed": "hotspots revisados (código nuevo)",
}
CMP = {"GT": ">", "LT": "<"}

estado = qg["status"]
print(f"quality gate: {estado}")

fallan = [c for c in qg.get("conditions", []) if c["status"] != "OK"]
if fallan:
    print()
    for c in fallan:
        k = NOMBRE.get(c["metricKey"], c["metricKey"])
        print(f"   ✗ {k}: {c.get('actualValue','?')} "
              f"(umbral {CMP.get(c['comparator'],c['comparator'])} {c.get('errorThreshold','?')})")

print()
for k, etiqueta in (("coverage", "cobertura"), ("bugs", "bugs"),
                    ("vulnerabilities", "vulnerabilidades"),
                    ("security_hotspots", "hotspots sin revisar"),
                    ("code_smells", "code smells"),
                    ("duplicated_lines_density", "duplicación"),
                    ("ncloc", "líneas de código")):
    if k in meas:
        suf = "%" if k in ("coverage", "duplicated_lines_density") else ""
        print(f"   {etiqueta:22} {meas[k]}{suf}")

raw = os.environ.get("ISSUES") or ""
if raw.strip():
    try:
        iss = json.loads(raw).get("issues", [])
        if iss:
            print(f"\nproblemas abiertos (primeros {len(iss)}):")
            for i in iss:
                comp = i.get("component", "").split(":")[-1]
                print(f"   [{i.get('severity','?'):8}] {comp}:{i.get('line','?')}  {i.get('message','')[:70]}")
    except Exception:
        print("\n(no se pudieron leer los problemas)")

if os.environ["TOKENSET"] == "no":
    print("\n(sin token: lectura anónima, el proyecto es público)")

sys.exit(0 if estado == "OK" else 1)
PY
