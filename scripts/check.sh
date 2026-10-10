#!/usr/bin/env bash
# =============================================================================
# check.sh — corre las verificaciones del monorepo y devuelve un resumen corto
#
#   ./scripts/check.sh [backend|frontend|mobile|all]   (por defecto: all)
#
# El output completo va a ficheros de log; por stdout salen solo el veredicto y
# los fallos. Pensado para que un agente lea cinco líneas en lugar de dos mil,
# pero igual de útil en una terminal.
#
# Sale con 0 si todo pasa, 1 si algo falla o si no se pudo interpretar un
# resultado — "no sé" cuenta como fallo, nunca como OK.
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOGDIR="$(mktemp -d "${TMPDIR:-/tmp}/sadday-check-XXXXXX")"
TARGET="${1:-all}"
case "$TARGET" in
    backend|frontend|mobile|all) ;;
    *) echo "target inválido: '$TARGET' (usa backend|frontend|mobile|all)" >&2; exit 2 ;;
esac
FAILED=0

# Sin color: las secuencias ANSI rompen el parseo
export NO_COLOR=1 FORCE_COLOR=0 CI=true
strip() { sed -E 's/\x1b\[[0-9;]*[a-zA-Z]//g' "$1"; }

report() { # etiqueta, resumen (vacío = no interpretable)
    printf '%-22s %s\n' "$1" "${2:-⚠ sin resultado interpretable}"
    [[ -z "$2" ]] && FAILED=1
    return 0
}

run() { # fichero_log, comando...
    local log="$1"; shift
    "$@" > "$log" 2>&1 || FAILED=1
}

# ── backend ──────────────────────────────────────────────────────────────────
if [[ "$TARGET" == "all" || "$TARGET" == "backend" ]]; then
    L="$LOGDIR/backend.log"
    run "$L" bash -c "cd '$ROOT/backend' && ./mvnw -B test"
    s=$(strip "$L" | grep -E "^\[INFO\] Tests run:.*Skipped: [0-9]+$" | tail -1 \
        | sed -E 's/.*Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+).*/\1 tests · \2 fallos · \3 errores/')
    report "backend (maven)" "$s"
    strip "$L" | grep -E "^\[ERROR\].*<<< (FAILURE|ERROR)" | sed 's/^/     /' | head -10
fi

# ── frontend ─────────────────────────────────────────────────────────────────
if [[ "$TARGET" == "all" || "$TARGET" == "frontend" ]]; then
    L="$LOGDIR/frontend.log"
    run "$L" bash -c "cd '$ROOT/frontend' && pnpm test"
    s=$(strip "$L" | grep -E "^ *Tests +[0-9]" | tail -1 | sed -E 's/^ *Tests +//;s/ +$//')
    report "frontend (vitest)" "$s"
    strip "$L" | grep -E "^ *(FAIL|×) " | sed 's/^/     /' | head -10
fi

# ── mobile ───────────────────────────────────────────────────────────────────
if [[ "$TARGET" == "all" || "$TARGET" == "mobile" ]]; then
    L="$LOGDIR/mobile.log"
    run "$L" bash -c "cd '$ROOT/mobile' && flutter test"
    s=$(strip "$L" | grep -oE "\+[0-9]+( -[0-9]+)?: (All tests passed|Some tests failed)" | tail -1)
    report "mobile (flutter test)" "$s"
    strip "$L" | sed -n '/^Failing tests:/,$p' | tail -n +2 | sed 's/^/     /' | head -10

    L2="$LOGDIR/analyze.log"
    run "$L2" bash -c "cd '$ROOT/mobile' && flutter analyze"
    s=$(strip "$L2" | grep -oE "No issues found|[0-9]+ issues? found" | tail -1)
    report "mobile (analyze)" "$s"
    strip "$L2" | grep -E "^ *(error|warning) •" | sed 's/^/     /' | head -10
fi

echo
if [[ $FAILED -eq 0 ]]; then
    echo "TODO OK"
    rm -rf "$LOGDIR"
else
    echo "HAY FALLOS — log completo en $LOGDIR"
fi
exit $FAILED
