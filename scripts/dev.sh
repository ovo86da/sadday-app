#!/usr/bin/env bash
#
# Estado del entorno de desarrollo local: qué corre, con qué código, y qué hay
# que reiniciar.
#
#   ./scripts/dev.sh                estado (por defecto)
#   ./scripts/dev.sh --restart-backend
#   ./scripts/dev.sh --restart-frontend
#
# Salidas: 0 todo listo · 1 algo requiere acción · 2 no se pudo comprobar
#
# Por qué existe: lo más repetido al desarrollar no es compilar ni mirar
# alertas, es averiguar si el entorno está vivo y si tiene el código actual.
# Y la pregunta que más caro sale es la última: el backend NO recarga en
# caliente, así que tras tocar un .java sigue sirviendo el anterior sin que
# nada lo indique. Eso ya provocó dar por buena una prueba contra código viejo.
#
# Regla compartida con check.sh: lo que no se puede interpretar cuenta como
# fallo, nunca como "todo bien".

set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)" || { echo "FALLO: no es un repo git" >&2; exit 2; }

ACCION="${1:-estado}"
PUERTO_BACK=8080
PUERTO_FRONT=5173
AVISOS=0

responde() { curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$1" 2>/dev/null; }
pid_en()   { lsof -nP -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | tail -1 | awk '{print $2}'; }
mtime()    { stat -f %m "$1" 2>/dev/null || stat -c %Y "$1" 2>/dev/null; }

# Momento en que arrancó un proceso, en epoch. Vacío si no se puede saber.
arranque_de() {
    [ -z "${1:-}" ] && return
    local ls; ls=$(ps -o lstart= -p "$1" 2>/dev/null) || return
    [ -z "$ls" ] && return
    date -j -f "%a %b %d %T %Y" "$ls" +%s 2>/dev/null \
      || date -d "$ls" +%s 2>/dev/null
}

mas_nuevo() {   # fichero más reciente bajo un directorio, con un patrón
    find "$1" -name "$2" -type f -exec stat -f '%m' {} \; 2>/dev/null \
      || find "$1" -name "$2" -type f -printf '%T@\n' 2>/dev/null
}

# ── Acciones ────────────────────────────────────────────────────────────────
case "$ACCION" in
  --restart-backend)
      echo "parando backend…"; pkill -f "spring-boot:run" 2>/dev/null
      for _ in $(seq 1 15); do sleep 2; pid_en $PUERTO_BACK >/dev/null 2>&1 || break; done
      ( cd backend && nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
          > /tmp/sadday-backend.log 2>&1 & )
      echo "arrancando… (log: /tmp/sadday-backend.log)"
      for i in $(seq 1 60); do
          [ "$(responde http://localhost:$PUERTO_BACK/actuator/health)" = "200" ] && {
              echo "backend listo tras ${i}0s"; exit 0; }
          grep -qE "APPLICATION FAILED|BUILD FAILURE" /tmp/sadday-backend.log 2>/dev/null && {
              echo "FALLÓ al arrancar — últimas líneas:"; tail -6 /tmp/sadday-backend.log; exit 1; }
          sleep 10
      done
      echo "FALLO: no respondió en 10 minutos" >&2; exit 2 ;;
  --restart-frontend)
      pkill -f "vite" 2>/dev/null; sleep 2
      ( cd frontend && nohup pnpm dev > /tmp/sadday-frontend.log 2>&1 & )
      for i in $(seq 1 20); do
          sleep 2
          [ "$(responde http://localhost:$PUERTO_FRONT)" = "200" ] && { echo "frontend listo"; exit 0; }
      done
      echo "FALLO: el frontend no respondió" >&2; exit 2 ;;
  estado) ;;
  -h|--help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) echo "opción desconocida: $ACCION" >&2; exit 2 ;;
esac

# ── Git ─────────────────────────────────────────────────────────────────────
RAMA=$(git branch --show-current 2>/dev/null || echo "?")
SUCIO=$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')
echo "rama $RAMA · $SUCIO fichero(s) sin commitear"

# ── Infraestructura ─────────────────────────────────────────────────────────
if ! docker compose ps --format '{{.Service}} {{.State}}' >/tmp/.dev_infra 2>/dev/null; then
    echo "FALLO: no se pudo consultar docker compose" >&2; exit 2
fi
for s in postgres minio mailpit; do
    estado=$(awk -v s="$s" '$1==s {print $2}' /tmp/.dev_infra)
    [ "$estado" = "running" ] || { echo "  ✗ $s: ${estado:-no existe}"; AVISOS=1; }
done
CAIDOS=$(for s in postgres minio mailpit; do awk -v s="$s" '$1==s && $2!="running" {print s}' /tmp/.dev_infra; done | wc -l | tr -d ' ')
[ "$CAIDOS" = "0" ] && echo "  ok  postgres · minio · mailpit"

# ── Backend ─────────────────────────────────────────────────────────────────
COD=$(responde "http://localhost:$PUERTO_BACK/actuator/health")
if [ "$COD" = "200" ]; then
    # ¿Sirve código viejo? La pregunta correcta no es si hace falta recompilar
    # —tras un `mvnw compile` las clases estan frescas pero la JVM en marcha
    # sigue con las anteriores cargadas— sino si algun fuente cambio DESPUES de
    # que arrancara el proceso.
    PID=$(pid_en $PUERTO_BACK)
    ARRANQUE=$(arranque_de "$PID")
    SRC=$(mas_nuevo backend/src/main '*.java' | sort -n | tail -1)
    SRC=${SRC%%.*}
    if [ -z "$PID" ] || [ -z "$ARRANQUE" ] || [ -z "$SRC" ]; then
        # No saber es un fallo, no un visto bueno: dar por bueno un backend
        # desactualizado es exactamente el error que esto evita.
        echo "  ⚠ backend :$PUERTO_BACK responde, pero no se pudo saber si su codigo esta al dia"
        AVISOS=1
    elif [ "$SRC" -gt "$ARRANQUE" ]; then
        CUANDO=$(date -r "$SRC" '+%H:%M' 2>/dev/null)
        DESDE=$(date -r "$ARRANQUE" '+%H:%M' 2>/dev/null)
        echo "  ⚠ backend :$PUERTO_BACK — CODIGO VIEJO: arranco a las $DESDE y hay fuentes de las $CUANDO"
        echo "      el backend NO recarga en caliente  →  ./scripts/dev.sh --restart-backend"
        AVISOS=1
    else
        echo "  ok  backend :$PUERTO_BACK (codigo al dia)"
    fi
else
    echo "  ✗ backend :$PUERTO_BACK no responde  →  ./scripts/dev.sh --restart-backend"
    AVISOS=1
fi

# ── Frontend ────────────────────────────────────────────────────────────────
COD=$(responde "http://localhost:$PUERTO_FRONT")
if [ "$COD" = "200" ]; then
    echo "  ok  frontend :$PUERTO_FRONT (recarga en caliente; basta refrescar)"
else
    echo "  ✗ frontend :$PUERTO_FRONT no responde  →  ./scripts/dev.sh --restart-frontend"
    AVISOS=1
fi

# ── Migraciones: la trampa de cambiar de rama ───────────────────────────────
# Flyway se niega a arrancar si la base tiene una migración que el código ya no
# trae. Pasa al volver a una rama anterior, y el síntoma es un backend que no
# levanta sin motivo aparente.
MAX_COD=$(ls backend/src/main/resources/db/migration/ 2>/dev/null \
          | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -1)
MAX_BD=$(docker compose exec -T postgres psql -U sadday_admin -d sadday_app -t -A \
         -c "SELECT max(version::int) FROM flyway_schema_history;" 2>/dev/null \
         | grep -v "^time=" | tr -d ' \r')
if [ -n "$MAX_BD" ] && [ -n "$MAX_COD" ]; then
    if [ "$MAX_BD" -gt "$MAX_COD" ]; then
        echo "  ⚠ migraciones: la BD está en V$MAX_BD y el código solo llega a V$MAX_COD"
        echo "      Flyway se negará a arrancar. ./start-local.sh --clean parte de cero."
        AVISOS=1
    elif [ "$MAX_BD" -lt "$MAX_COD" ]; then
        echo "  ·   migraciones: BD en V$MAX_BD, código en V$MAX_COD — se aplicará al arrancar"
    else
        echo "  ok  migraciones al día (V$MAX_BD)"
    fi
else
    echo "  ⚠ migraciones: no se pudo comparar (¿postgres levantado?)"
    AVISOS=1
fi

[ "$AVISOS" = "0" ] && { echo; echo "TODO LISTO"; exit 0; }
echo; echo "HAY QUE ACTUAR — ver las líneas marcadas arriba"; exit 1
