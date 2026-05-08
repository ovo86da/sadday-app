#!/usr/bin/env bash
# =============================================================================
# generate-keys.sh — Genera el par de claves RSA-4096 para JWT RS256
# =============================================================================
#
# Uso (ejecutar desde la raíz del monorepo):
#   chmod +x scripts/generate-keys.sh
#   ./scripts/generate-keys.sh              # Genera en backend/src/main/resources/keys/
#   ./scripts/generate-keys.sh --test       # Genera en backend/src/test/resources/keys/
#
# Las claves generadas NUNCA deben commitearse al repositorio.
# El .gitignore ya excluye backend/src/main/resources/keys/
#
# En producción, las claves deben proveerse via variables de entorno o
# secretos del orquestador (Docker secrets, AWS Secrets Manager, etc.)
# =============================================================================

set -euo pipefail

# Directorio destino según modo
if [[ "${1:-}" == "--test" ]]; then
    OUTPUT_DIR="backend/src/test/resources/keys"
    KEY_SIZE=2048
    PREFIX="test-"
    echo "[INFO] Modo test: generando claves RSA-${KEY_SIZE} en ${OUTPUT_DIR}/"
else
    OUTPUT_DIR="backend/src/main/resources/keys"
    KEY_SIZE=4096
    PREFIX=""
    echo "[INFO] Generando claves RSA-${KEY_SIZE} en ${OUTPUT_DIR}/"
fi

PRIVATE_KEY="${OUTPUT_DIR}/${PREFIX}private.pem"
PUBLIC_KEY="${OUTPUT_DIR}/${PREFIX}public.pem"

# Crear directorio si no existe
mkdir -p "${OUTPUT_DIR}"

# Verificar si openssl está disponible
if ! command -v openssl &> /dev/null; then
    echo "[ERROR] openssl no encontrado. Instálalo con: sudo apt install openssl"
    exit 1
fi

# Advertencia si las claves ya existen
if [[ -f "${PRIVATE_KEY}" ]]; then
    echo "[WARN] Ya existe ${PRIVATE_KEY}. Sobreescribiendo..."
fi

# 1. Generar clave privada RSA (PKCS#8 sin cifrar)
#    Se usa PKCS#8 para compatibilidad con Spring Security oauth2-resource-server
openssl genpkey \
    -algorithm RSA \
    -pkeyopt rsa_keygen_bits:${KEY_SIZE} \
    -out "${PRIVATE_KEY}"

# 2. Extraer la clave pública en formato PKCS#8
openssl rsa \
    -in "${PRIVATE_KEY}" \
    -pubout \
    -out "${PUBLIC_KEY}"

# 3. Restringir permisos — solo lectura para el propietario
chmod 600 "${PRIVATE_KEY}"
chmod 644 "${PUBLIC_KEY}"

echo ""
echo "[OK] Claves generadas:"
echo "     Privada : ${PRIVATE_KEY}  (permisos: 600)"
echo "     Pública : ${PUBLIC_KEY}  (permisos: 644)"
echo ""
echo "[IMPORTANTE] Verifica que ${OUTPUT_DIR}/ está en .gitignore"
echo "             NUNCA commitees la clave privada al repositorio."
echo ""

# 4. Verificar que el .gitignore tiene la entrada correcta
if grep -q "backend/src/main/resources/keys/" .gitignore 2>/dev/null; then
    echo "[OK] .gitignore contiene la exclusión de keys/"
else
    echo "[WARN] No se encontró 'backend/src/main/resources/keys/' en .gitignore"
    echo "       Añádelo manualmente para evitar committear claves privadas."
fi
