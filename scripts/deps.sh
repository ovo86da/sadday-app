#!/usr/bin/env bash
# =============================================================================
# deps.sh — compara el árbol de dependencias runtime del backend contra otra
#           referencia de git y avisa de degradaciones.
#
#   ./scripts/deps.sh [ref-base] [ref-nueva]
#
#   ref-base   con qué comparar          (por defecto: origin/develop)
#   ref-nueva  qué comparar              (por defecto: el working tree)
#
# Con dos refs no toca el working tree ni cambia de rama, así que sirve para
# evaluar un PR sin hacerle checkout:
#   git fetch origin refs/pull/152/head:refs/remotes/pr/152
#   ./scripts/deps.sh origin/develop refs/remotes/pr/152
#
# Existe por una trampa real: al subir Spring Boot a 4.0.8, el override de
# netty que teníamos puesto habría BAJADO la dependencia de 4.2.17 a 4.2.13,
# devolviendo CVEs ya cerrados. El pom compilaba, los tests pasaban y nada lo
# habría señalado.
#
# Sale con 1 si detecta cualquier versión que retroceda.
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${1:-origin/develop}"
HEAD_REF="${2:-}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/sadday-deps-XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

for r in "$BASE" ${HEAD_REF:+"$HEAD_REF"}; do
    git -C "$ROOT" rev-parse --verify --quiet "$r" >/dev/null || {
        echo "referencia desconocida: $r" >&2; exit 2; }
done

resolve() { # pom, salida
    ( cd "$ROOT/backend" && ./mvnw -B -q -f "$1" \
        dependency:list -DincludeScope=runtime -DoutputFile="$2" ) >/dev/null 2>&1
    grep -oE '[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:jar:[a-zA-Z0-9._-]+' "$2" 2>/dev/null \
        | awk -F: '{print $1":"$2" "$4}' | sort -u
}

mkdir -p "$WORK/base"
git -C "$ROOT" show "$BASE:backend/pom.xml" > "$WORK/base/pom.xml" 2>/dev/null || {
    echo "no hay backend/pom.xml en $BASE" >&2; exit 2; }

resolve "$WORK/base/pom.xml" "$WORK/base.raw" > "$WORK/base.txt"

if [[ -n "$HEAD_REF" ]]; then
    mkdir -p "$WORK/head"
    git -C "$ROOT" show "$HEAD_REF:backend/pom.xml" > "$WORK/head/pom.xml" 2>/dev/null || {
        echo "no hay backend/pom.xml en $HEAD_REF" >&2; exit 2; }
    resolve "$WORK/head/pom.xml" "$WORK/head.raw" > "$WORK/head.txt"
else
    resolve "$ROOT/backend/pom.xml" "$WORK/head.raw" > "$WORK/head.txt"
fi

[[ -s "$WORK/head.txt" ]] || { echo "⚠ el pom actual no resuelve — revisar el build" >&2; exit 1; }
[[ -s "$WORK/base.txt" ]] || { echo "⚠ el pom de $BASE no resuelve" >&2; exit 1; }

# ── ¿la rama a comparar está muy por detrás de la base? ──────────────────────
# Comparar una rama vieja contra la base muestra TODO lo que la base ha ganado
# desde entonces como "degradación". Es cierto pero engañoso: no es que el PR
# baje nada, es que su punto de partida es antiguo. Sin este aviso, un PR de
# hace meses parece una regresión masiva.
if [[ -n "$HEAD_REF" ]]; then
    MB="$(git -C "$ROOT" merge-base "$BASE" "$HEAD_REF" 2>/dev/null || true)"
    if [[ -n "$MB" ]]; then
        DETRAS="$(git -C "$ROOT" rev-list --count "$MB..$BASE" 2>/dev/null || echo 0)"
        T_MB="$(git -C "$ROOT" log -1 --format=%ct "$MB" 2>/dev/null || echo 0)"
        T_BASE="$(git -C "$ROOT" log -1 --format=%ct "$BASE" 2>/dev/null || echo 0)"
        DIAS=$(( (T_BASE - T_MB) / 86400 ))
        if (( DETRAS > 20 || DIAS > 14 )); then
            echo "⚠  La rama parte de un punto $DETRAS commits / $DIAS días por detrás de $BASE."
            echo "   Las degradaciones de abajo son probablemente ANTIGÜEDAD, no una regresión:"
            echo "   reflejan lo que la base ha avanzado desde entonces. Rebasa el PR y vuelve"
            echo "   a comparar antes de sacar conclusiones."
            echo
        fi
    fi
fi

echo "base:  $BASE ($(git -C "$ROOT" rev-parse --short "$BASE"))"
echo "nueva: ${HEAD_REF:-working tree}${HEAD_REF:+ ($(git -C "$ROOT" rev-parse --short "$HEAD_REF"))}"
echo "artefactos: $(wc -l < "$WORK/base.txt" | tr -d ' ') → $(wc -l < "$WORK/head.txt" | tr -d ' ')"
echo

python3 - "$WORK/base.txt" "$WORK/head.txt" <<'PY'
import re, sys
def load(p):
    d = {}
    for line in open(p):
        k, _, v = line.strip().partition(' ')
        if k: d[k] = v
    return d
def vt(v): return tuple(int(x) for x in re.findall(r'\d+', v)) or (0,)

base, head = load(sys.argv[1]), load(sys.argv[2])
nuevos   = sorted(set(head) - set(base))
quitados = sorted(set(base) - set(head))
sube, baja = [], []
for k in sorted(set(base) & set(head)):
    if base[k] != head[k]:
        (sube if vt(head[k]) > vt(base[k]) else baja).append((k, base[k], head[k]))

if baja:
    print(f"DEGRADACIONES ({len(baja)}) — esto no debería pasar:")
    for k, b, h in baja: print(f"   ⚠ {k}  {b} → {h}")
    print()
if nuevos:
    print(f"nuevos ({len(nuevos)}):")
    for k in nuevos[:15]: print(f"   + {k} {head[k]}")
    if len(nuevos) > 15: print(f"   … y {len(nuevos)-15} más")
    print()
if quitados:
    print(f"retirados ({len(quitados)}):")
    for k in quitados[:10]: print(f"   - {k} {base[k]}")
    print()
if sube:
    print(f"subidas ({len(sube)}):")
    for k, b, h in sube[:15]: print(f"   ↑ {k}  {b} → {h}")
    if len(sube) > 15: print(f"   … y {len(sube)-15} más")
    print()
if not (baja or nuevos or quitados or sube):
    print("sin cambios en el árbol de dependencias")
    print()
print("HAY DEGRADACIONES" if baja else "SIN DEGRADACIONES")
sys.exit(1 if baja else 0)
PY
