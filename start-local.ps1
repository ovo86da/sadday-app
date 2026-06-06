# =============================================================================
# start-local.ps1 — Levanta todo el stack de Sadday App en local (Windows)
# =============================================================================
#
# Uso (PowerShell):
#   .\start-local.ps1              # Levanta todo
#   .\start-local.ps1 -Clean       # Limpia volúmenes y reconstruye desde cero
#   .\start-local.ps1 -Stop        # Detiene todos los servicios
#
# Requisitos:
#   - Docker Desktop para Windows
#   - openssl (incluido en Git for Windows)
#
# Si PowerShell bloquea la ejecución, usa:
#   Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
#
# =============================================================================

param(
    [switch]$Stop,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

# ---- Helpers ----------------------------------------------------------------
function Write-Info    { param($msg) Write-Host "  ▶ $msg" -ForegroundColor Cyan }
function Write-Success { param($msg) Write-Host "  ✓ $msg" -ForegroundColor Green }
function Write-Warn    { param($msg) Write-Host "  ⚠ $msg" -ForegroundColor Yellow }
function Write-Fail    { param($msg) Write-Host "  ✗ $msg" -ForegroundColor Red; exit 1 }

# ---- Asegurarse de estar en la raíz del monorepo ---------------------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $ScriptDir

if (-not (Test-Path "docker-compose.yml")) {
    Write-Fail "No se encontró docker-compose.yml. Ejecuta este script desde la raíz del proyecto."
}

# ---- Banner -----------------------------------------------------------------
Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════╗" -ForegroundColor White
Write-Host "║        🏔  Sadday App — Setup Local  🏔          ║" -ForegroundColor White
Write-Host "╚═══════════════════════════════════════════════════╝" -ForegroundColor White
Write-Host ""

# ---- --Stop: Detener servicios -----------------------------------------------
if ($Stop) {
    Write-Info "Deteniendo todos los servicios..."
    docker compose down
    Write-Success "Servicios detenidos."
    exit 0
}

# ---- --Clean: Limpiar todo y reconstruir -------------------------------------
if ($Clean) {
    Write-Warn "Esto eliminará TODOS los volúmenes locales (base de datos, archivos, etc.)"
    $confirm = Read-Host "  ¿Estás seguro? (s/N)"
    if ($confirm -ne "s" -and $confirm -ne "S") {
        Write-Info "Cancelado."
        exit 0
    }
    Write-Info "Deteniendo servicios y eliminando volúmenes..."
    docker compose down -v --remove-orphans
    Write-Success "Limpieza completa."
    Write-Host ""
}

# =============================================================================
# 1. Verificar requisitos
# =============================================================================
Write-Host "[1/4] Verificando requisitos..." -ForegroundColor White

# Docker
try {
    $dockerVersion = docker --version
    Write-Success "Docker: $dockerVersion"
} catch {
    Write-Fail "Docker no encontrado. Instala Docker Desktop: https://www.docker.com/products/docker-desktop/"
}

# Docker Compose
try {
    $composeVersion = docker compose version
    Write-Success "Compose: $composeVersion"
} catch {
    Write-Fail "Docker Compose no encontrado. Asegúrate de tener Docker Desktop instalado."
}

# Verificar que Docker está corriendo
try {
    docker info 2>$null | Out-Null
} catch {
    Write-Fail "Docker no está corriendo. Abre Docker Desktop e inténtalo de nuevo."
}

# openssl
try {
    $opensslVersion = openssl version 2>$null
    Write-Success "openssl disponible"
} catch {
    Write-Warn "openssl no encontrado en PATH. Se buscará en Git for Windows..."
    $gitOpenssl = "C:\Program Files\Git\usr\bin\openssl.exe"
    if (Test-Path $gitOpenssl) {
        $env:PATH = "C:\Program Files\Git\usr\bin;$env:PATH"
        Write-Success "openssl encontrado en Git for Windows"
    } else {
        Write-Fail "openssl no encontrado. Instala Git for Windows (https://git-scm.com) o agrega openssl al PATH."
    }
}

# =============================================================================
# 2. Generar claves JWT si no existen
# =============================================================================
Write-Host ""
Write-Host "[2/4] Claves JWT (RS256)..." -ForegroundColor White

$keysDir = "backend\src\main\resources\keys"
$privateKey = "$keysDir\private.pem"
$publicKey = "$keysDir\public.pem"

if ((Test-Path $privateKey) -and (Test-Path $publicKey)) {
    Write-Success "Claves JWT ya existen en $keysDir\"
} else {
    Write-Info "Generando claves JWT..."
    if (-not (Test-Path $keysDir)) {
        New-Item -ItemType Directory -Path $keysDir -Force | Out-Null
    }
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out $privateKey
    openssl rsa -in $privateKey -pubout -out $publicKey
    Write-Success "Claves JWT generadas en $keysDir\"
}

# =============================================================================
# 3. Verificar puertos disponibles
# =============================================================================
Write-Host ""
Write-Host "[3/4] Verificando puertos..." -ForegroundColor White

function Test-Port {
    param($port, $service)
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($listener) {
        Write-Warn "Puerto $port ($service) ya está en uso. Puede causar conflictos."
    } else {
        Write-Success "Puerto $port ($service) disponible"
    }
}

Test-Port 5432 "PostgreSQL"
Test-Port 8080 "API Backend"
Test-Port 3000 "Frontend"
Test-Port 9000 "MinIO S3"
Test-Port 9001 "MinIO Console"
Test-Port 8025 "Mailpit Web"
Test-Port 1025 "Mailpit SMTP"

# =============================================================================
# 4. Levantar servicios
# =============================================================================
Write-Host ""
Write-Host "[4/4] Levantando servicios con Docker Compose..." -ForegroundColor White
Write-Info "Esto puede tomar varios minutos la primera vez (descarga de imágenes + build del backend)."
Write-Host ""

docker compose up --build -d

# =============================================================================
# Esperar que la API esté lista
# =============================================================================
Write-Host ""
Write-Info "Esperando que la API esté lista..."

$maxWait = 120
$elapsed = 0
$apiReady = $false

while ($elapsed -lt $maxWait) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $apiReady = $true
            break
        }
    } catch {
        # API aún no lista
    }
    Start-Sleep -Seconds 3
    $elapsed += 3
    Write-Host "." -NoNewline
}
Write-Host ""

if ($apiReady) {
    Write-Success "API lista (${elapsed}s)"
} else {
    Write-Warn "La API aún no responde después de ${maxWait}s. Revisa los logs: docker compose logs api"
}

# =============================================================================
# Resumen final
# =============================================================================
Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════╗" -ForegroundColor White
Write-Host "║            🎉  ¡Todo listo!  🎉                  ║" -ForegroundColor White
Write-Host "╠═══════════════════════════════════════════════════╣" -ForegroundColor White
Write-Host "║                                                   ║" -ForegroundColor White
Write-Host "║  Frontend        http://localhost:3000            ║" -ForegroundColor Cyan
Write-Host "║  API Backend     http://localhost:8080/api/v1     ║" -ForegroundColor Cyan
Write-Host "║  Mailpit         http://localhost:8025            ║" -ForegroundColor Cyan
Write-Host "║  MinIO Console   http://localhost:9001            ║" -ForegroundColor Cyan
Write-Host "║                                                   ║" -ForegroundColor White
Write-Host "║  Admin:  admin / Admin123!                        ║" -ForegroundColor Yellow
Write-Host "║  MinIO:  minioadmin / minioadmin                  ║" -ForegroundColor Yellow
Write-Host "║                                                   ║" -ForegroundColor White
Write-Host "║  Detener:  .\start-local.ps1 -Stop               ║" -ForegroundColor Green
Write-Host "║  Logs:     docker compose logs -f api             ║" -ForegroundColor Green
Write-Host "║  Reset:    .\start-local.ps1 -Clean               ║" -ForegroundColor Green
Write-Host "║                                                   ║" -ForegroundColor White
Write-Host "╚═══════════════════════════════════════════════════╝" -ForegroundColor White
Write-Host ""
