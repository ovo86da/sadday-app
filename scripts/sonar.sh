#!/usr/bin/env bash
# =============================================================================
# sonar.sh — estado del análisis de SonarCloud, resumido.
#
#   ./scripts/sonar.sh [--issues]     --issues añade los problemas abiertos
#   ./scripts/sonar.sh --pr 245        el gate de ESE PR, no el del proyecto
#
# Devuelve el quality gate, las condiciones que fallan y las métricas clave,
# en lugar de mandarte al navegador.
#
# --pr existe porque sin él el script no servía justo cuando hacía falta: el
# gate de un PR se mide sobre CÓDIGO NUEVO y puede estar en rojo con el proyecto
# entero en verde. Además lista los ficheros del PR con su cobertura nueva, que
# es lo que dice dónde escribir los tests que faltan; la web no lo pone junto.
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

PR=""
ISSUES_ON=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        # `issues` sin guiones es como lo documenta la skill (/sonar issues);
        # se acepta para no romper lo que ya estaba escrito.
        --issues|issues) ISSUES_ON=1; shift ;;
        --pr)     PR="${2:-}"; shift 2 || true ;;
        --pr=*)   PR="${1#--pr=}"; shift ;;
        *) echo "uso: $0 [--issues] [--pr <numero>]" >&2; exit 2 ;;
    esac
done
if [[ -n "$PR" && ! "$PR" =~ ^[0-9]+$ ]]; then
    echo "--pr espera un número de PR, no '$PR'" >&2; exit 2
fi

TOKEN="${SONAR_TOKEN:-}"
if [[ -z "$TOKEN" ]] && command -v security >/dev/null 2>&1; then
    TOKEN="$(security find-generic-password -a "$USER" -s sadday-sonar-token -w 2>/dev/null || true)"
fi
AUTH=(); [[ -n "$TOKEN" ]] && AUTH=(-u "$TOKEN:")

# ${AUTH[@]+...} evita el "unbound variable" de bash 3.2 con arrays vacíos
get() { curl -s --max-time 20 ${AUTH[@]+"${AUTH[@]}"} "$API/$1"; }

SCOPE=""; [[ -n "$PR" ]] && SCOPE="&pullRequest=$PR"

QG="$(get "qualitygates/project_status?projectKey=$PROJECT$SCOPE")"
if ! echo "$QG" | python3 -c "import json,sys; json.load(sys.stdin)['projectStatus']" >/dev/null 2>&1; then
    if [[ -n "$PR" ]]; then
        echo "no se pudo consultar el PR #$PR en SonarCloud." >&2
        echo "Si el PR es reciente, puede que el análisis no haya terminado todavía." >&2
    else
        echo "no se pudo consultar SonarCloud (proyecto '$PROJECT')" >&2
    fi
    echo "$QG" | head -c 200 >&2; echo >&2
    exit 2
fi

MEAS="$(get "measures/component?component=$PROJECT$SCOPE&metricKeys=coverage,bugs,vulnerabilities,code_smells,security_hotspots,duplicated_lines_density,ncloc")"

# En un PR, lo útil no son las métricas del proyecto: es qué ficheros traen
# código nuevo sin cubrir. Es la pregunta que sigue a un gate rojo por cobertura.
FICHEROS=""
if [[ -n "$PR" ]]; then
    FICHEROS="$(get "measures/component_tree?component=$PROJECT$SCOPE&metricKeys=new_lines,new_lines_to_cover,new_uncovered_lines,new_conditions_to_cover,new_uncovered_conditions&qualifiers=FIL&ps=100")"
fi

ISSUES=""
[[ -n "$ISSUES_ON" ]] && ISSUES="$(get "issues/search?componentKeys=$PROJECT$SCOPE&resolved=false&ps=20&s=SEVERITY&asc=false")"

QG="$QG" MEAS="$MEAS" ISSUES="$ISSUES" FICHEROS="$FICHEROS" PR="$PR" TOKENSET="$([[ -n "$TOKEN" ]] && echo si || echo no)" python3 <<'PY'
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

pr = os.environ.get("PR") or ""
estado = qg["status"]
print(f"quality gate{f' del PR #{pr}' if pr else ''}: {estado}")

fallan = [c for c in qg.get("conditions", []) if c["status"] != "OK"]
if fallan:
    print()
    for c in fallan:
        k = NOMBRE.get(c["metricKey"], c["metricKey"])
        print(f"   ✗ {k}: {c.get('actualValue','?')} "
              f"(umbral {CMP.get(c['comparator'],c['comparator'])} {c.get('errorThreshold','?')})")

def val(m):
    v = m.get("value")
    if v is None:
        v = (m.get("period") or {}).get("value")
    if v is None and m.get("periods"):
        v = m["periods"][0].get("value")
    return v

if pr:
    # Un gate rojo por cobertura no dice dónde faltan tests. Esto sí.
    raw = os.environ.get("FICHEROS") or ""
    filas = []
    if raw.strip():
        try:
            for c in json.loads(raw).get("components", []):
                m = {x["metric"]: val(x) or "0" for x in c.get("measures", [])}
                lc, lu = int(m.get("new_lines_to_cover", 0)), int(m.get("new_uncovered_lines", 0))
                cc, cu = int(m.get("new_conditions_to_cover", 0)), int(m.get("new_uncovered_conditions", 0))
                if lc + cc:
                    filas.append((lu + cu, c["path"], lc - lu, lc, cc - cu, cc))
        except Exception:
            print("\n(no se pudieron leer los ficheros del PR)")
    if filas:
        filas.sort(reverse=True)
        print("\ncódigo nuevo por fichero (lo más descubierto primero):")
        for _, path, lcov, lc, ccov, cc in filas:
            print(f"   {path}")
            print(f"      líneas {lcov}/{lc}   condiciones {ccov}/{cc}")
        tot_c = sum(f[2] + f[4] for f in filas)
        tot_t = sum(f[3] + f[5] for f in filas)
        if tot_t:
            print(f"\n   cubierto {tot_c} de {tot_t} elementos nuevos → {100*tot_c/tot_t:.1f}%")
    elif raw.strip():
        print("\nel PR no trae código nuevo que Sonar mida para cobertura")
    print()
    if os.environ["TOKENSET"] == "no":
        print("(sin token: lectura anónima, el proyecto es público)")
    sys.exit(0 if estado == "OK" else 1)

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
