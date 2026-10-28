#!/usr/bin/env bash
# =============================================================================
# mutar.sh — rompe el código a propósito y exige que los tests lo detecten.
#
#   ./scripts/mutar.sh <fichero> <busca> <reemplaza> -- <orden de test...>
#   ./scripts/mutar.sh --lote <fichero.mutaciones>
#
# Un test que pasa no prueba nada si no se pone rojo cuando el código se rompe.
# Esto aplica una mutación, corre los tests, EXIGE que fallen y restaura el
# fichero, pase lo que pase.
#
# Existe porque hacerlo a mano sale mal de formas silenciosas. En este repo ya
# ocurrió dos veces: un bucle que leía el código de salida de `grep` en vez del
# de `psql` y daba todo por roto, y una comprobación hecha DESPUÉS de restaurar
# el fichero, que informó «no aplicó» de una mutación que sí aplicó y sí fue
# detectada. Las dos veces el resultado impreso era mentira.
#
# De ahí las tres reglas:
#
#   1. Si la mutación no cambia el fichero, es FALLO, no «no aplicó». Una
#      mutación que no entra no prueba nada y no puede pasar por éxito.
#   2. Si los tests PASAN con el código roto, es FALLO: los tests no cubren eso.
#   3. El fichero se restaura siempre —éxito, fallo, Ctrl-C o error— y al final
#      se verifica que quedó idéntico al original. Si no, se dice a gritos.
#
# El criterio es el de todos los scripts de este repo: lo que no se puede
# interpretar cuenta como fallo, nunca como «todo bien».
#
# Ejemplos
# --------
#   # una escala que excede se deja de detectar
#   ./scripts/mutar.sh \
#       backend/src/main/java/com/sadday/app/mountains/service/NivelRutaService.java \
#       'rank.apply(exigeRuta) > rank.apply(permiteNivel)' \
#       'rank.apply(exigeRuta) < rank.apply(permiteNivel)' \
#       -- ./mvnw -q -Dtest=NivelRutaServiceTest test
#
#   # varias de golpe, desde un fichero (ver --lote abajo)
#   ./scripts/mutar.sh --lote docs/mutaciones/fr-028.mutaciones
#
# Formato de --lote: bloques separados por una línea en blanco. Cada bloque:
#
#   # un comentario que describe qué se rompe
#   fichero: ruta/al/fichero.java
#   busca: texto exacto a sustituir
#   pon: texto de reemplazo
#   test: la orden completa, tal como se escribiría en la terminal
#
# La orden de test se ejecuta desde la carpeta del módulo que contiene el
# fichero (backend/, frontend/ o mobile/), que es donde viven mvnw y pnpm.
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOGDIR="$(mktemp -d "${TMPDIR:-/tmp}/sadday-mutar-XXXXXX")"

VERDES=0   # mutaciones detectadas (bien)
ROJAS=0    # mutaciones NO detectadas, o que no se pudieron aplicar (mal)

# Restaurar es lo único que no puede fallar: si el script muere con el código
# mutado en disco, deja el repo sucio y el siguiente que mire se vuelve loco.
RESPALDOS=()
restaurar_todo() {
    local i
    for ((i = 0; i < ${#RESPALDOS[@]}; i += 2)); do
        [[ -f "${RESPALDOS[i+1]}" ]] && cp "${RESPALDOS[i+1]}" "${RESPALDOS[i]}"
    done
}
trap 'restaurar_todo; echo; echo "interrumpido — ficheros restaurados"; exit 130' INT TERM

modulo_de() {
    case "$1" in
        backend/*)  echo "$ROOT/backend"  ;;
        frontend/*) echo "$ROOT/frontend" ;;
        mobile/*)   echo "$ROOT/mobile"   ;;
        *)          echo "$ROOT"          ;;
    esac
}

# mutar <descripcion> <fichero_relativo> <busca> <pon> <orden...>
mutar() {
    local desc="$1" rel="$2" busca="$3" pon="$4"; shift 4
    local abs="$ROOT/$rel"

    if [[ ! -f "$abs" ]]; then
        printf '  ✗ %s\n      no existe el fichero: %s\n' "$desc" "$rel"
        ROJAS=$((ROJAS + 1)); return
    fi

    local bak; bak="$LOGDIR/$(echo "$rel" | tr '/' '_').bak"
    cp "$abs" "$bak"
    RESPALDOS+=("$abs" "$bak")

    # La sustitución se hace en python para que el texto sea literal: con sed
    # habría que escapar barras, puntos y paréntesis, y un escape mal puesto
    # produce una mutación distinta de la que se cree estar probando.
    if ! BUSCA="$busca" PON="$pon" python3 - "$abs" <<'PY'
import os, sys, pathlib
p = pathlib.Path(sys.argv[1])
s = p.read_text(encoding="utf-8")
busca, pon = os.environ["BUSCA"], os.environ["PON"]
n = s.count(busca)
if n == 0:
    sys.exit(3)       # no se encontró el texto
p.write_text(s.replace(busca, pon), encoding="utf-8")
PY
    then
        printf '  ✗ %s\n      la mutación NO se aplicó: el texto buscado no está en %s\n' "$desc" "$rel"
        printf '      busca: %s\n' "$busca"
        cp "$bak" "$abs"
        ROJAS=$((ROJAS + 1)); return
    fi

    # Regla 1: aunque python haya escrito, se comprueba que el fichero cambió.
    if cmp -s "$abs" "$bak"; then
        printf '  ✗ %s\n      la mutación no cambió nada (¿busca == pon?)\n' "$desc"
        cp "$bak" "$abs"
        ROJAS=$((ROJAS + 1)); return
    fi

    local log="$LOGDIR/$(date +%s%N).log" dir
    dir="$(modulo_de "$rel")"
    ( cd "$dir" && "$@" ) > "$log" 2>&1
    local estado=$?

    cp "$bak" "$abs"

    # Regla 3: el original vuelve, y se verifica. No se da por hecho.
    if ! cmp -s "$abs" "$bak"; then
        printf '  ✗ %s\n      ATENCIÓN: no se pudo restaurar %s — revísalo a mano\n' "$desc" "$rel"
        ROJAS=$((ROJAS + 1)); return
    fi

    if [[ $estado -eq 0 ]]; then
        # Regla 2: los tests pasaron con el código roto.
        printf '  ✗ %s\n      los tests PASAN con el código roto: no cubren esto\n' "$desc"
        printf '      log: %s\n' "$log"
        ROJAS=$((ROJAS + 1))
    else
        printf '  ✓ %s\n' "$desc"
        VERDES=$((VERDES + 1))
    fi
}

# ── --lote ───────────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--lote" ]]; then
    LOTE="${2:-}"
    [[ -f "$LOTE" ]] || { echo "no existe el fichero de lote: ${LOTE:-(ninguno)}" >&2; exit 2; }

    # El fichero se parsea en python y se emite una línea por mutación con
    # campos separados por tabulador, que bash lee sin reinterpretar nada.
    PLAN="$(LOTE="$LOTE" python3 <<'PY'
import os, sys
desc = fich = busca = pon = test = None
bloques, actual = [], {}

def cerrar():
    if actual:
        faltan = [k for k in ("fichero", "busca", "pon", "test") if k not in actual]
        if faltan:
            print(f"bloque incompleto (falta {', '.join(faltan)}): "
                  f"{actual.get('desc', '(sin descripción)')}", file=sys.stderr)
            sys.exit(3)
        bloques.append(dict(actual))
    actual.clear()

for linea in open(os.environ["LOTE"], encoding="utf-8"):
    cruda = linea.rstrip("\n")
    if not cruda.strip():
        cerrar(); continue
    if cruda.lstrip().startswith("#"):
        actual.setdefault("desc", cruda.lstrip().lstrip("#").strip()); continue
    if ":" not in cruda:
        print(f"línea sin clave: {cruda}", file=sys.stderr); sys.exit(3)
    clave, valor = cruda.split(":", 1)
    clave = clave.strip()
    if clave in ("fichero", "busca", "pon", "test"):
        actual[clave] = valor.strip() if clave != "busca" and clave != "pon" else valor.lstrip()
cerrar()

if not bloques:
    print("el fichero de lote no tiene ninguna mutación", file=sys.stderr); sys.exit(3)
for b in bloques:
    print("\t".join([b.get("desc", b["busca"][:40]), b["fichero"], b["busca"], b["pon"], b["test"]]))
PY
)" || exit 2

    echo "mutaciones desde $LOTE"
    echo
    while IFS=$'\t' read -r desc fich busca pon test; do
        [[ -z "${desc:-}" ]] && continue
        # shellcheck disable=SC2086
        mutar "$desc" "$fich" "$busca" "$pon" $test
    done <<< "$PLAN"
else
    # ── un solo uso ──────────────────────────────────────────────────────────
    FICH="${1:-}"; BUSCA="${2:-}"; PON="${3:-}"
    if [[ -z "$FICH" || -z "$BUSCA" || "${4:-}" != "--" ]]; then
        sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
        exit 2
    fi
    shift 4
    [[ $# -gt 0 ]] || { echo "falta la orden de test después de --" >&2; exit 2; }
    mutar "$BUSCA → $PON" "$FICH" "$BUSCA" "$PON" "$@"
fi

restaurar_todo
echo
if [[ $ROJAS -gt 0 ]]; then
    echo "HAY FALLOS — $VERDES detectadas, $ROJAS sin detectar o sin aplicar"
    echo "logs en $LOGDIR"
    exit 1
fi
if [[ $VERDES -eq 1 ]]; then
    echo "la mutación fue detectada"
else
    echo "las $VERDES mutaciones fueron detectadas"
fi
echo "(logs en $LOGDIR)"
