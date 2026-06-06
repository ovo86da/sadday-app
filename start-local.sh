#!/usr/bin/env bash
# =============================================================================
# start-local.sh — Levanta todo el stack de Sadday App en local
# =============================================================================
#
# Uso:
#   chmod +x start-local.sh
#   ./start-local.sh              # Levanta todo
#   ./start-local.sh --clean      # Limpia volúmenes y reconstruye desde cero
#   ./start-local.sh --stop       # Detiene todos los servicios
#
# Requisitos:
#   - Docker Desktop (o Docker Engine + Docker Compose plugin)
#   - openssl (para generar claves JWT)
#
# =============================================================================

set -euo pipefail

# ---- Colores ----------------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ---- Helpers ----------------------------------------------------------------
info()    { echo -e "${CYAN}  ▶${RESET} $*"; }
success() { echo -e "${GREEN}  ✓${RESET} $*"; }
warn()    { echo -e "${YELLOW}  ⚠${RESET} $*"; }
fail()    { echo -e "${RED}  ✗${RESET} $*"; exit 1; }

# ---- Asegurarse de estar en la raíz del monorepo ---------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -f "docker-compose.yml" ]]; then
    fail "No se encontró docker-compose.yml. Ejecuta este script desde la raíz del proyecto."
fi

# ---- Banner -----------------------------------------------------------------
echo ""
echo -e "${BOLD}╔═══════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║        🏔  Sadday App — Setup Local  🏔          ║${RESET}"
echo -e "${BOLD}╚═══════════════════════════════════════════════════╝${RESET}"
echo ""

# ---- Manejar flags ----------------------------------------------------------
ACTION="start"
if [[ "${1:-}" == "--stop" ]]; then
    ACTION="stop"
elif [[ "${1:-}" == "--clean" ]]; then
    ACTION="clean"
fi

# ---- --stop: Detener servicios -----------------------------------------------
if [[ "$ACTION" == "stop" ]]; then
    info "Deteniendo todos los servicios..."
    docker compose down
    success "Servicios detenidos."
    exit 0
fi

# ---- --clean: Limpiar todo y reconstruir -------------------------------------
if [[ "$ACTION" == "clean" ]]; then
    warn "Esto eliminará TODOS los volúmenes locales (base de datos, archivos, etc.)"
    read -rp "  ¿Estás seguro? (s/N): " confirm
    if [[ "$confirm" != "s" && "$confirm" != "S" ]]; then
        info "Cancelado."
        exit 0
    fi
    info "Deteniendo servicios y eliminando volúmenes..."
    docker compose down -v --remove-orphans
    success "Limpieza completa."
    echo ""
fi

# =============================================================================
# 1. Verificar requisitos
# =============================================================================
echo -e "${BOLD}[1/4] Verificando requisitos...${RESET}"

# Docker
if ! command -v docker &>/dev/null; then
    fail "Docker no encontrado. Instala Docker Desktop: https://www.docker.com/products/docker-desktop/"
fi

# Docker Compose (plugin v2)
if ! docker compose version &>/dev/null; then
    fail "Docker Compose no encontrado. Asegúrate de tener Docker Desktop o el plugin 'docker compose'."
fi

# Verificar que Docker está corriendo
if ! docker info &>/dev/null 2>&1; then
    fail "Docker no está corriendo. Abre Docker Desktop e inténtalo de nuevo."
fi

DOCKER_VERSION=$(docker --version | head -1)
COMPOSE_VERSION=$(docker compose version | head -1)
success "Docker: $DOCKER_VERSION"
success "Compose: $COMPOSE_VERSION"

# openssl
if ! command -v openssl &>/dev/null; then
    fail "openssl no encontrado. Instálalo con tu package manager (brew install openssl / sudo apt install openssl)."
fi
success "openssl disponible"

# =============================================================================
# 2. Generar claves JWT si no existen
# =============================================================================
echo ""
echo -e "${BOLD}[2/4] Claves JWT (RS256)...${RESET}"

KEYS_DIR="backend/src/main/resources/keys"
PRIVATE_KEY="$KEYS_DIR/private.pem"
PUBLIC_KEY="$KEYS_DIR/public.pem"

if [[ -f "$PRIVATE_KEY" && -f "$PUBLIC_KEY" ]]; then
    success "Claves JWT ya existen en $KEYS_DIR/"
else
    info "Generando claves JWT..."
    if [[ -f "scripts/generate-keys.sh" ]]; then
        bash scripts/generate-keys.sh
    else
        # Fallback: generar directamente si el script no existe
        mkdir -p "$KEYS_DIR"
        openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out "$PRIVATE_KEY"
        openssl rsa -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY"
        chmod 600 "$PRIVATE_KEY"
        chmod 644 "$PUBLIC_KEY"
    fi
    success "Claves JWT generadas"
fi

# =============================================================================
# 3. Verificar puertos disponibles
# =============================================================================
echo ""
echo -e "${BOLD}[3/4] Verificando puertos...${RESET}"

check_port() {
    local port=$1
    local service=$2
    if lsof -Pi :"$port" -sTCP:LISTEN -t &>/dev/null 2>&1; then
        warn "Puerto $port ($service) ya está en uso. Puede causar conflictos."
    else
        success "Puerto $port ($service) disponible"
    fi
}

check_port 5432 "PostgreSQL"
check_port 8080 "API Backend"
check_port 3000 "Frontend"
check_port 9000 "MinIO S3"
check_port 9001 "MinIO Console"
check_port 8025 "Mailpit Web"
check_port 1025 "Mailpit SMTP"

# =============================================================================
# 4. Levantar servicios
# =============================================================================
echo ""
echo -e "${BOLD}[4/4] Levantando servicios con Docker Compose...${RESET}"
info "Esto puede tomar varios minutos la primera vez (descarga de imágenes + build del backend)."
echo ""

docker compose up --build -d

# =============================================================================
# Esperar que la API esté lista
# =============================================================================
echo ""
info "Esperando que la API esté lista..."

MAX_WAIT=120
ELAPSED=0
API_READY=false

while [[ $ELAPSED -lt $MAX_WAIT ]]; do
    if curl -sf http://localhost:8080/actuator/health &>/dev/null 2>&1; then
        API_READY=true
        break
    fi
    sleep 3
    ELAPSED=$((ELAPSED + 3))
    printf "."
done
echo ""

if $API_READY; then
    success "API lista (${ELAPSED}s)"
else
    warn "La API aún no responde después de ${MAX_WAIT}s. Revisa los logs: docker compose logs api"
fi

# =============================================================================
# Resumen final
# =============================================================================
echo ""
echo -e "${BOLD}╔═══════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║            🎉  ¡Todo listo!  🎉                  ║${RESET}"
echo -e "${BOLD}╠═══════════════════════════════════════════════════╣${RESET}"
echo -e "${BOLD}║${RESET}                                                   ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${CYAN}Frontend${RESET}        http://localhost:3000            ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${CYAN}API Backend${RESET}     http://localhost:8080/api/v1     ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${CYAN}Mailpit${RESET}         http://localhost:8025            ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${CYAN}MinIO Console${RESET}   http://localhost:9001            ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}                                                   ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${YELLOW}Admin:${RESET}  admin / Admin123!                       ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${YELLOW}MinIO:${RESET}  minioadmin / minioadmin                 ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}                                                   ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${GREEN}Detener:${RESET}  ./start-local.sh --stop               ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${GREEN}Logs:${RESET}     docker compose logs -f api            ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}  ${GREEN}Reset:${RESET}    ./start-local.sh --clean              ${BOLD}║${RESET}"
echo -e "${BOLD}║${RESET}                                                   ${BOLD}║${RESET}"
echo -e "${BOLD}╚═══════════════════════════════════════════════════╝${RESET}"
echo ""
