#!/usr/bin/env bash
#
# Estado de los workflows y, para un run concreto, qué falló y por qué.
#
#   ./scripts/ci.sh                 estado del último run de cada workflow
#   ./scripts/ci.sh RUN_ID          jobs y pasos fallidos de ese run, con el error
#   ./scripts/ci.sh --last          lo mismo, para el run fallido más reciente
#   ./scripts/ci.sh --ref RAMA ...  cambiar de rama (por defecto develop)
#
# Salidas: 0 nada falló · 1 hay fallos · 2 no se pudo consultar
#
# Por qué existe: el log de un job son ~30 KB de fontanería de git, bloques
# env y secuencias ANSI. `gh run view --log | grep <job>` además arrastra la
# limpieza del post-job, que menciona el nombre del job y no tiene nada que
# ver con el fallo. Esto deja solo las líneas de error con su contexto.

set -uo pipefail

REF="develop"
TARGET=""

while [ $# -gt 0 ]; do
  case "$1" in
    --ref)  REF="${2:?--ref necesita una rama}"; shift 2 ;;
    --last) TARGET="last"; shift ;;
    -h|--help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    [0-9]*) TARGET="$1"; shift ;;
    *) echo "opción desconocida: $1" >&2; exit 2 ;;
  esac
done

command -v gh >/dev/null || { echo "FALLO: hace falta gh" >&2; exit 2; }
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null) \
  || { echo "FALLO: gh no pudo identificar el repo" >&2; exit 2; }

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

# ── Vista general: último run de cada workflow ───────────────────────────────
if [ -z "$TARGET" ]; then
  if ! gh run list --repo "$REPO" --branch "$REF" --limit 40 \
        --json workflowName,databaseId,status,conclusion,createdAt,event \
        > "$TMP/runs.json" 2>"$TMP/err"; then
    echo "FALLO: no se pudo listar los runs de $REPO ($REF)" >&2
    head -3 "$TMP/err" >&2; exit 2
  fi
  N=$(jq 'length' "$TMP/runs.json" 2>/dev/null) || { echo "FALLO: respuesta ilegible" >&2; exit 2; }
  if [ "$N" -eq 0 ]; then
    echo "FALLO: sin runs para la rama '$REF' — ¿existe?" >&2; exit 2
  fi

  echo "WORKFLOWS — $REF"
  echo
  # El formato se hace en jq y no en awk a propósito: el `length` de awk cuenta
  # bytes, así que un nombre con guión largo («CI — Build…») descoloca la
  # columna. El de jq cuenta codepoints.
  jq -r '
    def pad($s; $w): $s + ((" " * ($w - ($s|length))) // "");
    group_by(.workflowName) | map(sort_by(.createdAt) | last) | sort_by(.workflowName)
    | .[] | "  "
      + pad((if .status != "completed" then "…" elif .conclusion == "success" then "ok" else "FALLO" end); 6)
      + pad(.workflowName; 26)
      + pad((.conclusion // .status); 10)
      + (.createdAt | .[0:16] | sub("T"; " "))
      + "  \(.databaseId) (\(.event))"
  ' "$TMP/runs.json"
  echo

  BAD=$(jq -r 'group_by(.workflowName) | map(sort_by(.createdAt)|last)
               | map(select(.conclusion != "success" and .status == "completed")) | .[].databaseId' "$TMP/runs.json")
  if [ -n "$BAD" ]; then
    echo "para ver el detalle de un fallo:"
    for r in $BAD; do echo "  ./scripts/ci.sh $r"; done
    exit 1
  fi
  exit 0
fi

# ── Detalle de un run ────────────────────────────────────────────────────────
if [ "$TARGET" = "last" ]; then
  TARGET=$(gh run list --repo "$REPO" --branch "$REF" --status failure --limit 1 \
            --json databaseId -q '.[0].databaseId' 2>/dev/null)
  [ -n "$TARGET" ] || { echo "sin runs fallidos en $REF"; exit 0; }
fi

if ! gh run view "$TARGET" --repo "$REPO" \
      --json workflowName,headBranch,conclusion,status,jobs > "$TMP/run.json" 2>"$TMP/err"; then
  echo "FALLO: no se pudo leer el run $TARGET" >&2; head -3 "$TMP/err" >&2; exit 2
fi

jq -r --arg id "$TARGET" '"RUN \($id) — \(.workflowName) · \(.headBranch) · \(.conclusion // .status)"' "$TMP/run.json"
echo

FAILED=$(jq -r '.jobs[] | select(.conclusion == "failure") | "\(.databaseId)\t\(.name)"' "$TMP/run.json")
if [ -z "$FAILED" ]; then
  jq -r '.jobs[] | "  ok     \(.name)"' "$TMP/run.json"
  echo
  echo "ningún job falló"
  exit 0
fi

while IFS=$'\t' read -r jid jname; do
  echo "  FALLO  $jname"
  jq -r --arg j "$jname" '.jobs[] | select(.name == $j) | .steps[]
    | select(.conclusion == "failure") | "         paso: \(.name)"' "$TMP/run.json"

  # El log viene con secuencias ANSI. El eco del script que ejecuta cada paso
  # va coloreado con 36;1m: se descarta ANTES de limpiar el color, que es la
  # forma fiable de distinguirlo de la salida real del comando.
  if gh api --allow-escape-sequences "repos/$REPO/actions/jobs/$jid/logs" > "$TMP/log" 2>/dev/null; then
    sed 's/^[0-9T:.Z-]\{20,\} //' "$TMP/log" \
      | grep -v $'\x1b\[36;1m' \
      | sed 's/\x1b\[[0-9;]*m//g' \
      | sed -n '1,/^Post job cleanup\./p' \
      | grep -vE '^\[command\]/usr/bin/git|^##\[(group|endgroup)\]|^(Requesting|Download|Getting|Prepare|Evaluate|Cleaning|Post job|Temporarily overriding|Adding repository|Removing |Secret source|Complete job|shell: |env:|  [A-Z_]+: )' \
      | awk '
          /^\s*$/ { next }
          { buf[NR%12] = $0; order[NR%12] = NR }
          /##\[error\]|^ *ERROR|^Status: *[45][0-9][0-9]/ {
            n = NR
            for (i = n-11; i <= n; i++) if (i > 0) for (k in order) if (order[k] == i) print "           " buf[k]
            print ""
          }
        ' | head -24
  else
    echo "           (no se pudo descargar el log de este job)"
  fi
  echo
done <<< "$FAILED"

exit 1
