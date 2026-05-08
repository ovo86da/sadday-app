#!/usr/bin/env bash
# =============================================================================
# Sadday App — Script de prueba de endpoints con datos mock
# =============================================================================
# Uso:
#   bash scripts/test-api.sh [opciones]
#
# Opciones:
#   -H HOST     Base URL (default: http://localhost:8080)
#   -u USER     Username de admin (default: admin)
#   -p PASS     Password (default: Admin123!)
#   -v          Modo verbose (muestra respuesta completa)
#   -h          Mostrar ayuda
#
# Ejemplos:
#   bash scripts/test-api.sh
#   bash scripts/test-api.sh -H http://mi-servidor.com -v
#   bash scripts/test-api.sh -u mi.admin -p MiClave456!
# =============================================================================

set -euo pipefail

# ---- Colores ----------------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ---- Defaults ---------------------------------------------------------------
BASE_URL="http://localhost:8080/api/v1"
USERNAME="admin"
PASSWORD="Admin123!"
VERBOSE=false
PASS_COUNT=0
FAIL_COUNT=0
TOKEN=""
SOCIO_ID=""
MOUNTAIN_ID=""
RUTA_ID=""
SALIDA_ID=""
PARTICIPANTE_ID=""

# ---- Opciones ---------------------------------------------------------------
while getopts "H:u:p:vh" opt; do
  case $opt in
    H) BASE_URL="$OPTARG/api/v1" ;;
    u) USERNAME="$OPTARG" ;;
    p) PASSWORD="$OPTARG" ;;
    v) VERBOSE=true ;;
    h) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Opción inválida. Usa -h para ayuda."; exit 1 ;;
  esac
done

# ---- Helpers ----------------------------------------------------------------
ok()   { echo -e "${GREEN}  ✓${RESET} $*"; ((PASS_COUNT++)) || true; }
fail() { echo -e "${RED}  ✗${RESET} $*"; ((FAIL_COUNT++)) || true; }
info() { echo -e "${CYAN}  ▶${RESET} $*"; }
section() { echo -e "\n${BOLD}${YELLOW}═══ $* ═══${RESET}"; }

# Ejecuta curl y devuelve el body. Si VERBOSE=true, imprime el body completo.
call() {
  local method=$1; shift
  local url=$1; shift
  local extra_args=("$@")

  local response
  response=$(curl -s -X "$method" "$BASE_URL$url" \
    -H "Content-Type: application/json" \
    "${extra_args[@]}" 2>&1)

  if $VERBOSE; then
    echo -e "    ${CYAN}← $(echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response")${RESET}"
  fi
  echo "$response"
}

# Versión con token de autorización
auth_call() {
  local method=$1; shift
  local url=$1; shift
  call "$method" "$url" -H "Authorization: Bearer $TOKEN" "$@"
}

# Verifica que el campo JSON existe y no es null/vacío
assert_field() {
  local label=$1
  local response=$2
  local field=$3

  local value
  value=$(echo "$response" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    keys = '$field'.split('.')
    v = d
    for k in keys:
        v = v[k]
    print(v if v is not None else '')
except:
    print('')
" 2>/dev/null)

  if [[ -n "$value" && "$value" != "None" && "$value" != "null" ]]; then
    ok "$label — campo '$field' = $value"
    echo "$value"
  else
    fail "$label — campo '$field' no encontrado o vacío"
    echo ""
  fi
}

# Verifica que el status HTTP o el campo status == ok
assert_ok() {
  local label=$1
  local response=$2
  local status
  status=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null)
  if [[ "$status" == "ok" ]]; then
    ok "$label"
  else
    fail "$label — respuesta: $(echo "$response" | head -c 200)"
  fi
}

assert_created() {
  local label=$1
  local response=$2
  local id_field="${3:-data.id}"
  local id
  id=$(echo "$response" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    keys = '$id_field'.split('.')
    v = d
    for k in keys:
        v = v[k]
    print(v)
except:
    print('')
" 2>/dev/null)
  if [[ -n "$id" && "$id" != "None" ]]; then
    ok "$label (id=$id)"
    echo "$id"
  else
    fail "$label — no se pudo obtener id. Respuesta: $(echo "$response" | head -c 200)"
    echo ""
  fi
}

# =============================================================================
# TESTS
# =============================================================================

echo -e "\n${BOLD}Sadday App — Prueba de endpoints${RESET}"
echo "Base URL: $BASE_URL"
echo "Usuario:  $USERNAME"
echo ""

# -----------------------------------------------------------------------------
section "1. AUTH — Login"
# -----------------------------------------------------------------------------

info "POST /auth/login"
LOGIN_BODY=$(cat <<EOF
{
  "username": "$USERNAME",
  "password": "$PASSWORD",
  "mfaCode": null
}
EOF
)
RESPONSE=$(call POST /auth/login -d "$LOGIN_BODY")
TOKEN=$(echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" 2>/dev/null)
REFRESH_TOKEN=$(echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('refreshToken',''))" 2>/dev/null)

if [[ -n "$TOKEN" ]]; then
  ok "Login exitoso — access token obtenido"
  info "Token: ${TOKEN:0:40}..."
else
  fail "Login fallido. Verifica que el usuario '$USERNAME' existe con contraseña '$PASSWORD'"
  fail "Respuesta: $(echo "$RESPONSE" | head -c 300)"
  echo -e "\n${RED}No se puede continuar sin token. Abortando.${RESET}"
  exit 1
fi

# -----------------------------------------------------------------------------
section "2. AUTH — Refresh token"
# -----------------------------------------------------------------------------
info "POST /auth/refresh"
REF_BODY="{\"refreshToken\": \"$REFRESH_TOKEN\"}"
RESPONSE=$(call POST /auth/refresh -d "$REF_BODY")
assert_ok "Refresh token" "$RESPONSE"

# -----------------------------------------------------------------------------
section "3. SOCIOS"
# -----------------------------------------------------------------------------

info "GET /socios/me"
RESPONSE=$(auth_call GET /socios/me)
assert_ok "GET /socios/me" "$RESPONSE"

info "GET /socios (lista)"
RESPONSE=$(auth_call GET "/socios?size=5")
assert_ok "GET /socios paginado" "$RESPONSE"

info "POST /socios — crear socio de prueba"
TIMESTAMP=$(date +%s)
SOCIO_BODY=$(cat <<EOF
{
  "nombre": "Test",
  "apellido": "ApiScript",
  "cedula": "999${TIMESTAMP: -7}",
  "correo": "test.${TIMESTAMP}@sadday.local",
  "fechaNacimiento": "1995-06-15",
  "fechaIngreso": "2024-01-01",
  "rolId": 4,
  "estadoHabilitacionId": 1,
  "tipoSocioId": 1
}
EOF
)
RESPONSE=$(auth_call POST /socios -d "$SOCIO_BODY")
SOCIO_ID=$(assert_created "POST /socios" "$RESPONSE" "data.id")

if [[ -n "$SOCIO_ID" ]]; then
  info "GET /socios/$SOCIO_ID"
  RESPONSE=$(auth_call GET "/socios/$SOCIO_ID")
  assert_ok "GET /socios/{id}" "$RESPONSE"

  info "PATCH /socios/$SOCIO_ID/inhabilitar"
  RESPONSE=$(auth_call PATCH "/socios/$SOCIO_ID/inhabilitar")
  assert_ok "PATCH inhabilitar" "$RESPONSE"

  info "PATCH /socios/$SOCIO_ID/habilitar"
  RESPONSE=$(auth_call PATCH "/socios/$SOCIO_ID/habilitar")
  assert_ok "PATCH habilitar" "$RESPONSE"
fi

info "GET /socios/lookups"
RESPONSE=$(auth_call GET /socios/lookups)
assert_ok "GET /socios/lookups" "$RESPONSE"

# -----------------------------------------------------------------------------
section "4. MONTAÑAS"
# -----------------------------------------------------------------------------

info "GET /mountains (lista)"
RESPONSE=$(auth_call GET /mountains)
assert_ok "GET /mountains" "$RESPONSE"

info "POST /mountains — crear montaña de prueba"
MOUNTAIN_BODY=$(cat <<EOF
{
  "nombre": "Pichincha Test ${TIMESTAMP}",
  "region": "Sierra Norte",
  "altitudMetros": 4794,
  "latitud": -0.1712,
  "longitud": -78.5517,
  "descripcion": "Montaña de prueba del script"
}
EOF
)
RESPONSE=$(auth_call POST /mountains -d "$MOUNTAIN_BODY")
MOUNTAIN_ID=$(assert_created "POST /mountains" "$RESPONSE" "data.id")

if [[ -n "$MOUNTAIN_ID" ]]; then
  info "GET /mountains/$MOUNTAIN_ID"
  RESPONSE=$(auth_call GET "/mountains/$MOUNTAIN_ID")
  assert_ok "GET /mountains/{id}" "$RESPONSE"

  # -----------------------------------------------------------------------------
  section "5. RUTAS"
  # -----------------------------------------------------------------------------

  info "POST /rutas — proponer ruta"
  RUTA_BODY=$(cat <<EOF
{
  "mountainId": $MOUNTAIN_ID,
  "nombre": "Ruta Normal Test",
  "descripcion": "Ruta de prueba del script de API",
  "distanciaKm": 12.5,
  "duracionHoras": 8,
  "altitudMaxima": 4794,
  "dificultadRocaId": 1,
  "dificultadHieloId": 1,
  "compromisoId": 1,
  "saddayRiesgoId": 1
}
EOF
)
  RESPONSE=$(auth_call POST /rutas -d "$RUTA_BODY")
  RUTA_ID=$(assert_created "POST /rutas" "$RESPONSE" "data.id")

  if [[ -n "$RUTA_ID" ]]; then
    info "PATCH /rutas/$RUTA_ID/aprobar"
    RESPONSE=$(auth_call PATCH "/rutas/$RUTA_ID/aprobar")
    assert_ok "PATCH /rutas/aprobar" "$RESPONSE"

    # -------------------------------------------------------------------------
    section "6. SALIDAS"
    # -------------------------------------------------------------------------

    info "GET /salidas/lookups"
    RESPONSE=$(auth_call GET /salidas/lookups)
    assert_ok "GET /salidas/lookups" "$RESPONSE"

    info "POST /salidas — crear salida"
    FECHA_SALIDA=$(date -d "+30 days" +%Y-%m-%d 2>/dev/null || date -v+30d +%Y-%m-%d)
    SALIDA_BODY=$(cat <<EOF
{
  "nombre": "Salida Test Script",
  "rutaId": $RUTA_ID,
  "tipoSalidaId": 1,
  "fechaSalida": "${FECHA_SALIDA}",
  "lugarEncuentro": "Parque La Carolina",
  "horaEncuentro": "05:00",
  "capacidadMaxima": 15,
  "nivelMinimoId": 1,
  "descripcion": "Salida creada por el script de prueba de API"
}
EOF
)
    RESPONSE=$(auth_call POST /salidas -d "$SALIDA_BODY")
    SALIDA_ID=$(assert_created "POST /salidas" "$RESPONSE" "data.id")

    if [[ -n "$SALIDA_ID" ]]; then
      info "GET /salidas/$SALIDA_ID"
      RESPONSE=$(auth_call GET "/salidas/$SALIDA_ID")
      assert_ok "GET /salidas/{id}" "$RESPONSE"

      if [[ -n "$SOCIO_ID" ]]; then
        info "POST /salidas/$SALIDA_ID/inscripciones — inscribir socio"
        INSCR_BODY="{\"socioId\": \"$SOCIO_ID\"}"
        RESPONSE=$(auth_call POST "/salidas/$SALIDA_ID/inscripciones" -d "$INSCR_BODY")
        PARTICIPANTE_ID=$(assert_created "POST inscripción" "$RESPONSE" "data.participanteId")
      fi
    fi
  fi

  info "GET /mountains/lookups"
  RESPONSE=$(auth_call GET /mountains/lookups)
  assert_ok "GET /mountains/lookups" "$RESPONSE"
fi

# -----------------------------------------------------------------------------
section "7. ACTAS DE REUNIÓN"
# -----------------------------------------------------------------------------

info "GET /actas"
RESPONSE=$(auth_call GET /actas)
assert_ok "GET /actas" "$RESPONSE"

info "POST /actas — crear acta"
FECHA_ACTA=$(date +%Y-%m-%d)
ACTA_BODY=$(cat <<EOF
{
  "fecha": "$FECHA_ACTA",
  "hora": "19:00",
  "lugar": "Sede del Club",
  "asistentesIds": [],
  "informesIds": [],
  "puntosPrincipales": "Revisión de actividades del mes",
  "acuerdos": "Realizar salida al Chimborazo en diciembre",
  "observaciones": "Acta de prueba del script"
}
EOF
)
RESPONSE=$(auth_call POST /actas -d "$ACTA_BODY")
ACTA_ID=$(assert_created "POST /actas" "$RESPONSE" "data.id")

if [[ -n "$ACTA_ID" ]]; then
  info "GET /actas/$ACTA_ID"
  RESPONSE=$(auth_call GET "/actas/$ACTA_ID")
  assert_ok "GET /actas/{id}" "$RESPONSE"

  info "GET /actas?q=chimborazo (Full Text Search)"
  RESPONSE=$(auth_call GET "/actas?q=chimborazo")
  assert_ok "FTS actas" "$RESPONSE"
fi

# -----------------------------------------------------------------------------
section "8. ESTADÍSTICAS"
# -----------------------------------------------------------------------------

# Obtener socioId del admin desde /socios/me
info "GET /socios/me — obtener ID del admin"
RESPONSE=$(auth_call GET /socios/me)
MY_SOCIO_ID=$(echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null)

if [[ -n "$MY_SOCIO_ID" ]]; then
  info "GET /estadisticas/socios/$MY_SOCIO_ID"
  RESPONSE=$(auth_call GET "/estadisticas/socios/$MY_SOCIO_ID")
  assert_ok "GET estadísticas del socio" "$RESPONSE"
fi

info "GET /estadisticas/mountains/1 (Chimborazo)"
RESPONSE=$(auth_call GET /estadisticas/mountains/1)
assert_ok "GET estadísticas de montaña" "$RESPONSE"

# -----------------------------------------------------------------------------
section "9. NOTIFICACIONES"
# -----------------------------------------------------------------------------

info "GET /notificaciones/cumpleanos"
RESPONSE=$(auth_call GET /notificaciones/cumpleanos)
assert_ok "GET cumpleaños hoy" "$RESPONSE"
TOTAL_CUMPLE=$(echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('total','?'))" 2>/dev/null)
info "Socios con cumpleaños hoy: $TOTAL_CUMPLE"

# -----------------------------------------------------------------------------
section "10. AUTH — Logout"
# -----------------------------------------------------------------------------

info "POST /auth/logout"
LOGOUT_BODY="{\"refreshToken\": \"$REFRESH_TOKEN\"}"
RESPONSE=$(auth_call POST /auth/logout -d "$LOGOUT_BODY")
assert_ok "Logout" "$RESPONSE"

# =============================================================================
# RESUMEN
# =============================================================================
echo ""
echo -e "${BOLD}═══════════════════════════════════════${RESET}"
echo -e "${BOLD}  RESULTADO FINAL${RESET}"
echo -e "${GREEN}  ✓ Pasaron: $PASS_COUNT${RESET}"
if [[ $FAIL_COUNT -gt 0 ]]; then
  echo -e "${RED}  ✗ Fallaron: $FAIL_COUNT${RESET}"
else
  echo -e "${GREEN}  ✗ Fallaron: $FAIL_COUNT${RESET}"
fi
echo -e "${BOLD}═══════════════════════════════════════${RESET}"

exit $FAIL_COUNT
