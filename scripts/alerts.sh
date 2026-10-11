#!/usr/bin/env bash
#
# Resumen de las alertas de GitHub Code Scanning: qué hay abierto, qué es
# nuevo desde la última consulta, y qué categorías han dejado de actualizarse.
#
#   ./scripts/alerts.sh [--ref RAMA] [--peek] [--all]
#
#     --ref RAMA   rama a consultar (por defecto develop)
#     --peek       no guardar instantánea: la próxima consulta comparará
#                  contra la misma referencia que esta
#     --all        listar todas las alertas nuevas, no solo las 10 primeras
#
# Salidas: 0 consulta correcta · 2 no se pudo consultar
#
# Regla de diseño, igual que check.sh: lo que no se puede interpretar cuenta
# como fallo, nunca como "nada que ver". Un resumidor que oculta un problema
# es peor que no tenerlo.

set -uo pipefail

REF="develop"
PEEK=0
LIMIT=10

while [ $# -gt 0 ]; do
  case "$1" in
    --ref)  REF="${2:?--ref necesita una rama}"; shift 2 ;;
    --peek) PEEK=1; shift ;;
    --all)  LIMIT=9999; shift ;;
    -h|--help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "opción desconocida: $1" >&2; exit 2 ;;
  esac
done

command -v gh >/dev/null || { echo "FALLO: hace falta gh" >&2; exit 2; }
command -v jq >/dev/null || { echo "FALLO: hace falta jq" >&2; exit 2; }

GITDIR=$(git rev-parse --git-dir 2>/dev/null) || { echo "FALLO: no es un repo git" >&2; exit 2; }
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null) \
  || { echo "FALLO: gh no pudo identificar el repo" >&2; exit 2; }
# Una instantánea por rama: comparar las alertas de develop contra las de main
# daría un delta sin sentido. El nombre se sanea porque las ramas llevan "/".
SNAP="$GITDIR/alerts-snapshot-$(printf '%s' "$REF" | tr -c 'A-Za-z0-9._-' '-').json"

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

# La API de alertas devuelve una lista vacía para una rama que no existe, no un
# error. Sin comprobarlo, un fallo de tecleo se presentaría como "0 alertas" —
# justo lo que la regla de diseño prohíbe.
if ! gh api "repos/$REPO/branches/$REF" -q .name >/dev/null 2>&1; then
  echo "FALLO: la rama '$REF' no existe en $REPO" >&2
  exit 2
fi

# ── Datos ────────────────────────────────────────────────────────────────────
if ! gh api "repos/$REPO/code-scanning/alerts?state=open&ref=refs/heads/$REF&per_page=100" \
      --paginate > "$TMP/alerts.json" 2>"$TMP/err"; then
  echo "FALLO: no se pudieron leer las alertas de $REPO ($REF)" >&2
  sed 's/^/  /' "$TMP/err" | head -3 >&2
  exit 2
fi

# --paginate concatena arrays; jq -s los une en uno solo.
jq -s 'add // []' "$TMP/alerts.json" > "$TMP/a.json" 2>/dev/null \
  || { echo "FALLO: respuesta de alertas ilegible" >&2; exit 2; }

TOTAL=$(jq 'length' "$TMP/a.json")
[ -n "$TOTAL" ] || { echo "FALLO: no se pudo contar las alertas" >&2; exit 2; }

echo "ALERTAS ABIERTAS — $REF · $TOTAL en total"
echo

# ── Resumen por herramienta y severidad ──────────────────────────────────────
if [ "$TOTAL" -gt 0 ]; then
  jq -r '
    def sev: (.rule.security_severity_level // .rule.severity // "?") | ascii_downcase;
    group_by(.tool.name) | map({
      tool: .[0].tool.name,
      crit: ([.[] | select(sev=="critical")] | length),
      high: ([.[] | select(sev=="high" or sev=="error")] | length),
      med:  ([.[] | select(sev=="medium" or sev=="warning")] | length),
      low:  ([.[] | select(sev=="low" or sev=="note")] | length),
      n: length })
    | sort_by(-.n)
    | (["  herramienta","crít","alta","media","baja","total"] | @tsv),
      (.[] | ["  " + .tool, (.crit|tostring), (.high|tostring), (.med|tostring), (.low|tostring), (.n|tostring)] | @tsv)
  ' "$TMP/a.json" | awk -F'\t' '{printf "%-22s %5s %5s %6s %5s %6s\n", $1, $2, $3, $4, $5, $6}'
  echo
fi

# ── Qué ha cambiado desde la última consulta ─────────────────────────────────
jq -c '[.[].number] | sort' "$TMP/a.json" > "$TMP/now.json"
if [ -f "$SNAP" ] && jq -e . "$SNAP" >/dev/null 2>&1; then
  WHEN=$(jq -r '.ts // "?"' "$SNAP")
  NEW=$(jq -r --slurpfile prev "$SNAP" '. - ($prev[0].numbers // []) | length' "$TMP/now.json")
  GONE=$(jq -r --slurpfile prev "$SNAP" '(($prev[0].numbers // []) - .) | length' "$TMP/now.json")
  echo "CAMBIOS desde $WHEN"
  printf "  nuevas    %s\n  cerradas  %s\n" "$NEW" "$GONE"
  if [ "$NEW" -gt 0 ]; then
    echo
    echo "  nuevas, de más grave a menos:"
    jq -r --slurpfile prev "$SNAP" '
      def sev: (.rule.security_severity_level // .rule.severity // "?") | ascii_downcase;
      def rank: {critical:0, high:1, error:1, medium:2, warning:2, low:3, note:3}[sev] // 4;
      ($prev[0].numbers // []) as $old
      | [.[] | select(.number as $n | ($old | index($n)) == null)]
      | sort_by(rank)
      | .[] | "  #\(.number) \(sev) \(.rule.id // "?" | split("/") | .[-1][0:34]) — \(.most_recent_instance.message.text // "" | .[0:62])"
    ' "$TMP/a.json" | head -"$LIMIT"
    [ "$NEW" -gt "$LIMIT" ] && echo "  … y $((NEW - LIMIT)) más (--all para verlas)"
  fi
else
  echo "CAMBIOS: sin instantánea previa — esta consulta crea la referencia"
fi
echo

# ── Categorías que han dejado de actualizarse ────────────────────────────────
# Las alertas se cierran solas cuando un análisis NUEVO de la MISMA categoría
# deja de reportarlas. Si una categoría muere —porque cambió el nombre de
# proyecto del escáner, o se renombró un job— sus alertas se quedan abiertas
# para siempre. Se detecta agrupando los análisis por analysis_key (el job):
# la categoría del análisis más reciente de cada job es la viva; cualquier
# otra con alertas abiertas está muerta.
if gh api "repos/$REPO/code-scanning/analyses?ref=refs/heads/$REF&per_page=100" \
     --paginate > "$TMP/an.json" 2>/dev/null \
   && jq -s 'add // []' "$TMP/an.json" > "$TMP/n.json" 2>/dev/null; then
  jq -r '
    group_by(.analysis_key)
    | map({key: .[0].analysis_key, live: (sort_by(.created_at) | last | .category)})
    | map("\(.key)\t\(.live)")[]
  ' "$TMP/n.json" > "$TMP/live.tsv"

  jq -r --rawfile live "$TMP/live.tsv" '
    ($live | split("\n") | map(select(length>0) | split("\t")) | map(.[1])) as $alive
    | group_by(.most_recent_instance.category // "?")
    | map(select((.[0].most_recent_instance.category // "?") as $c | ($alive | index($c)) == null))
    | map({cat: (.[0].most_recent_instance.category // "?"), n: length, last: ([.[].updated_at] | max)})
    | sort_by(-.n)
    | .[] | "  \(.n) alertas  \(.cat)  última \(.last[0:10])"
  ' "$TMP/a.json" > "$TMP/dead.txt"

  if [ -s "$TMP/dead.txt" ]; then
    echo "CATEGORÍAS MUERTAS — ya nadie las reporta, sus alertas no se cerrarán solas"
    cat "$TMP/dead.txt"
    echo "  → se limpian borrando el análisis: gh api -X DELETE repos/$REPO/code-scanning/analyses/ID"
    echo
  fi
else
  echo "AVISO: no se pudieron leer los análisis — sin comprobación de categorías muertas"
  echo
fi

# ── Críticas, siempre listadas enteras ───────────────────────────────────────
CRIT=$(jq '[.[] | select((.rule.security_severity_level // "") == "critical")] | length' "$TMP/a.json")
if [ "${CRIT:-0}" -gt 0 ]; then
  echo "CRÍTICAS ($CRIT)"
  jq -r '.[] | select((.rule.security_severity_level // "") == "critical")
    | "  #\(.number) \(.rule.id // "?" | split("/") | .[-1][0:40]) — \(.most_recent_instance.message.text // "" | .[0:60])"' "$TMP/a.json"
  echo
fi

# ── Instantánea ──────────────────────────────────────────────────────────────
if [ "$PEEK" -eq 0 ]; then
  jq -n --argjson n "$(cat "$TMP/now.json")" \
        --arg ts "$(date -u '+%Y-%m-%d %H:%M UTC')" \
        '{ts: $ts, ref: "'"$REF"'", numbers: $n}' > "$SNAP"
  echo "instantánea guardada en ${SNAP#*/.git/} (una por rama)"
else
  echo "(--peek: instantánea no modificada)"
fi
