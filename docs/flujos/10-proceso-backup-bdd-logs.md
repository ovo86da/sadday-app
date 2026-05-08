# 10 — Proceso de Backup: Base de Datos y Logs

Este documento describe qué se respalda, cómo hacerlo, cómo automatizarlo con cron y cómo restaurar en caso de necesidad.

---

## 1. Qué se respalda y qué no

| Qué | Cómo se respalda | Por qué |
|-----|------------------|---------|
| **Base de datos PostgreSQL** | `pg_dump` dentro del contenedor | Contiene todos los datos del club |
| **Logs de la aplicación** | Copia del volumen Docker de logs | Evidencia de auditoría y errores históricos |
| **Archivos de configuración y secretos** | Copia manual segura | `.env` con contraseñas, claves JWT |
| **PDFs de documentos** | — | Ya están en AWS S3, que tiene su propia redundancia |
| **Código fuente** | — | Está en GitHub/ghcr.io, no necesita backup local |
| **Imagen Docker** | — | Se reconstruye desde GitHub Actions cuando se necesita |

---

## 2. Estructura de archivos en el servidor

Antes de empezar, crear esta estructura en el servidor donde corre la aplicación:

```bash
mkdir -p /home/ubuntu/backups/bdd
mkdir -p /home/ubuntu/backups/logs
mkdir -p /home/ubuntu/backups/config
mkdir -p /home/ubuntu/scripts
```

Los backups se guardan en `/home/ubuntu/backups/`. Deben copiarse además a un lugar externo (ver sección 6).

---

## 3. Backup de la base de datos

### 3.1 Por qué se hace con `pg_dump` y no copiando el volumen

En producción, el contenedor de PostgreSQL **no expone el puerto al exterior** (así está configurado en `docker-compose.prod.yml`). No se puede conectar directamente desde fuera del servidor. Por eso el backup se hace con `docker exec`, que corre `pg_dump` dentro del contenedor y escribe el resultado en el host.

`pg_dump` genera un respaldo lógico (SQL): es portable, comprimible, y permite restaurar en cualquier versión de PostgreSQL igual o superior.

### 3.2 Comando manual de backup

```bash
# Variables — ajustar con los valores reales del .env de producción
DB_USER=sadday_admin
DB_NAME=sadday_app
DB_PASSWORD=la_contraseña_real

# Ejecutar pg_dump dentro del contenedor de PostgreSQL y comprimir
docker exec sadday-db \
  pg_dump -U "$DB_USER" -d "$DB_NAME" --no-password \
  | gzip > /home/ubuntu/backups/bdd/sadday_$(date +%Y-%m-%d).sql.gz

# Verificar que el archivo fue creado y tiene tamaño razonable
ls -lh /home/ubuntu/backups/bdd/
```

> **Nota de seguridad:** Para evitar que PostgreSQL pida contraseña interactivamente, crear un archivo `.pgpass` en el servidor host:
> ```bash
> echo "localhost:5432:sadday_app:sadday_admin:LA_CONTRASEÑA" >> ~/.pgpass
> chmod 600 ~/.pgpass
> ```
> Pero para el comando via `docker exec`, no hace falta `.pgpass` porque la autenticación de PostgreSQL en Docker por defecto confía en el usuario local dentro del contenedor (método `trust` o `peer` en `pg_hba.conf`). Verificar que el comando funciona antes de automatizarlo.

### 3.3 Verificar que el backup es válido

```bash
# Ver las primeras líneas del dump sin descomprimir:
zcat /home/ubuntu/backups/bdd/sadday_2026-04-28.sql.gz | head -20

# Debe mostrar algo como:
# -- PostgreSQL database dump
# -- Dumped from database version 16.x
# SET statement_timeout = 0;
# ...

# Ver el tamaño del archivo (un dump de pocos KB es sospechoso — indica que está vacío):
ls -lh /home/ubuntu/backups/bdd/sadday_2026-04-28.sql.gz
```

### 3.4 Cuánto espacio ocupa

Un dump de PostgreSQL comprimido es típicamente entre el 10% y 30% del tamaño de la base de datos en disco. Para una base de datos de club mediano (socios, salidas, historial), esperar entre 1 MB y 50 MB por backup semanal.

---

## 4. Backup de los logs

Los logs viven en el volumen Docker `sadday-backend_sadday-logs`. Se puede hacer el backup copiando desde dentro del contenedor de la API (que ya tiene ese volumen montado en `/app/logs`).

### 4.1 Backup de todos los logs

```bash
# Comprimir y copiar toda la carpeta de logs desde el contenedor:
docker exec sadday-api \
  tar czf - /app/logs \
  > /home/ubuntu/backups/logs/logs_$(date +%Y-%m-%d).tar.gz

# Verificar:
ls -lh /home/ubuntu/backups/logs/
zcat /home/ubuntu/backups/logs/logs_2026-04-28.tar.gz | tar -t | head -20
```

### 4.2 Backup solo de logs históricos (archivos comprimidos anteriores)

Los archivos `.log` activos cambian constantemente. Los archivos `.gz` históricos son inmutables. Para backups eficientes, respaldar solo los históricos:

```bash
# Solo los .gz de días anteriores (no el activo del día de hoy):
docker exec sadday-api \
  find /app/logs -name "*.gz" \
  | docker exec -i sadday-api tar czf - -T - \
  > /home/ubuntu/backups/logs/logs_historicos_$(date +%Y-%m-%d).tar.gz
```

> En la práctica, hacer el backup completo (sección 4.1) es más simple y garantiza que no se pierde nada.

---

## 5. Backup de configuración y secretos

Los archivos de configuración contienen las contraseñas, claves JWT y demás secretos. Son irreemplazables — si se pierden, no se puede arrancar la aplicación.

### 5.1 Qué copiar

```bash
# Desde el directorio raíz del proyecto en el servidor:
cp /home/ubuntu/sadday-app/.env /home/ubuntu/backups/config/env_$(date +%Y-%m-%d)

# Claves JWT (si están en el servidor como archivos):
cp ${JWT_PRIVATE_KEY_PATH} /home/ubuntu/backups/config/jwt_private_$(date +%Y-%m-%d).pem
cp ${JWT_PUBLIC_KEY_PATH}  /home/ubuntu/backups/config/jwt_public_$(date +%Y-%m-%d).pem
```

### 5.2 Cifrar el archivo de configuración antes de guardarlo

Los secretos **no deben viajar sin cifrar** a ningún destino de backup. Usar `gpg` para cifrarlos:

```bash
# Cifrar con una passphrase (guardar la passphrase en un lugar seguro, por ejemplo un gestor de contraseñas):
gpg --symmetric --cipher-algo AES256 \
    --output /home/ubuntu/backups/config/env_$(date +%Y-%m-%d).gpg \
    /home/ubuntu/backups/config/env_$(date +%Y-%m-%d)

# Eliminar la versión sin cifrar:
rm /home/ubuntu/backups/config/env_$(date +%Y-%m-%d)

# Para descifrar cuando se necesite:
gpg --decrypt /home/ubuntu/backups/config/env_2026-04-28.gpg > .env
```

---

## 6. Script de backup automatizado

Guardar este archivo en `/home/ubuntu/scripts/backup.sh` en el servidor. Ajustar las variables al inicio.

```bash
#!/bin/bash
# =============================================================================
# backup.sh — Backup semanal de BDD y logs de Sadday App
# Ejecutar como: bash /home/ubuntu/scripts/backup.sh
# Programar con cron para ejecución automática (ver sección 7)
# =============================================================================

set -euo pipefail   # Salir inmediatamente si cualquier comando falla

# ── Configuración ─────────────────────────────────────────────────────────────
DB_CONTAINER="sadday-db"
API_CONTAINER="sadday-api"
DB_USER="sadday_admin"
DB_NAME="sadday_app"

BACKUP_BASE="/home/ubuntu/backups"
BDD_DIR="$BACKUP_BASE/bdd"
LOG_DIR="$BACKUP_BASE/logs"

FECHA=$(date +%Y-%m-%d)
SEMANA=$(date +%Y-W%V)          # Ej: 2026-W18 (para backups semanales)

# Cuántos backups conservar localmente
RETENER_BDD_DIAS=60             # 60 días = ~8 semanas
RETENER_LOGS_DIAS=90            # 90 días = ~3 meses

# ── Función de log ─────────────────────────────────────────────────────────────
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# ── 1. Backup de la base de datos ─────────────────────────────────────────────
log "Iniciando backup de BDD..."
ARCHIVO_BDD="$BDD_DIR/sadday_bdd_${FECHA}.sql.gz"

docker exec "$DB_CONTAINER" \
    pg_dump -U "$DB_USER" -d "$DB_NAME" \
    | gzip > "$ARCHIVO_BDD"

TAMANO=$(du -sh "$ARCHIVO_BDD" | cut -f1)
log "BDD backup completado: $ARCHIVO_BDD ($TAMANO)"

# Verificación básica: el archivo debe pesar al menos 10 KB
BYTES=$(stat -c%s "$ARCHIVO_BDD")
if [ "$BYTES" -lt 10240 ]; then
    log "ERROR: El archivo de backup de BDD es sospechosamente pequeño ($BYTES bytes). Revisar manualmente."
    exit 1
fi

# ── 2. Backup de logs ──────────────────────────────────────────────────────────
log "Iniciando backup de logs..."
ARCHIVO_LOGS="$LOG_DIR/sadday_logs_${FECHA}.tar.gz"

docker exec "$API_CONTAINER" \
    tar czf - /app/logs \
    > "$ARCHIVO_LOGS"

TAMANO=$(du -sh "$ARCHIVO_LOGS" | cut -f1)
log "Logs backup completado: $ARCHIVO_LOGS ($TAMANO)"

# ── 3. Limpieza de backups antiguos ───────────────────────────────────────────
log "Limpiando backups de BDD con más de $RETENER_BDD_DIAS días..."
find "$BDD_DIR" -name "*.sql.gz" -mtime +$RETENER_BDD_DIAS -delete

log "Limpiando backups de logs con más de $RETENER_LOGS_DIAS días..."
find "$LOG_DIR" -name "*.tar.gz" -mtime +$RETENER_LOGS_DIAS -delete

# ── 4. Resumen final ───────────────────────────────────────────────────────────
log "=== Backup completado exitosamente ==="
log "Backups de BDD disponibles:"
ls -lh "$BDD_DIR"/*.sql.gz 2>/dev/null || log "  (ninguno)"
log "Backups de logs disponibles:"
ls -lh "$LOG_DIR"/*.tar.gz 2>/dev/null || log "  (ninguno)"
```

Dar permisos de ejecución al script:

```bash
chmod +x /home/ubuntu/scripts/backup.sh

# Probar que funciona manualmente antes de programar el cron:
bash /home/ubuntu/scripts/backup.sh
```

---

## 7. Automatizar con cron

Editar el crontab del usuario `ubuntu` en el servidor:

```bash
crontab -e
```

Agregar las siguientes líneas:

```cron
# Sadday App — Backup semanal los domingos a las 02:00 AM (hora del servidor)
0 2 * * 0 bash /home/ubuntu/scripts/backup.sh >> /home/ubuntu/backups/backup.log 2>&1

# Opcional: backup mensual adicional el día 1 de cada mes a las 03:00 AM
0 3 1 * * bash /home/ubuntu/scripts/backup.sh >> /home/ubuntu/backups/backup.log 2>&1
```

Verificar que el cron se guardó:

```bash
crontab -l
```

Ver el log del cron para confirmar que se ejecuta:

```bash
tail -50 /home/ubuntu/backups/backup.log
```

---

## 8. Copiar los backups fuera del servidor (offsite)

Guardar los backups solo en el mismo servidor es insuficiente: si el servidor falla, se pierde todo junto. Hay dos opciones según el acceso que tengas:

### Opción A — Copiar a AWS S3 (recomendada, ya tienen cuenta)

Instalar la AWS CLI en el servidor si no está instalada:

```bash
sudo apt install awscli -y
aws configure   # ingresar Access Key, Secret Key y región del IAM para backups
```

Agregar al final del `backup.sh`, antes del resumen:

```bash
# ── 5. Copia offsite a S3 ─────────────────────────────────────────────────────
S3_BACKUP_BUCKET="sadday-backups"   # Crear este bucket en AWS S3

log "Subiendo BDD a S3..."
aws s3 cp "$ARCHIVO_BDD" "s3://$S3_BACKUP_BUCKET/bdd/$(basename $ARCHIVO_BDD)"

log "Subiendo logs a S3..."
aws s3 cp "$ARCHIVO_LOGS" "s3://$S3_BACKUP_BUCKET/logs/$(basename $ARCHIVO_LOGS)"

log "Copia a S3 completada."
```

> Usar un bucket S3 **separado** del de PDFs para los backups. Activar el versionado del bucket en la consola de AWS como protección adicional.

### Opción B — Copiar a otra máquina con `scp` o `rsync`

```bash
# Agregar al backup.sh:
rsync -avz /home/ubuntu/backups/ usuario@servidor-backup:/backups/sadday/
```

---

## 9. Cómo restaurar la base de datos

Usar este procedimiento solo en caso de pérdida total de datos o desastre. **Detiene la aplicación temporalmente.**

```bash
# 1. Detener la API para que no escriba durante la restauración
docker stop sadday-api

# 2. (Opcional) Eliminar la base de datos actual si está corrupta
docker exec sadday-db \
    psql -U sadday_admin -c "DROP DATABASE IF EXISTS sadday_app;"
docker exec sadday-db \
    psql -U sadday_admin -c "CREATE DATABASE sadday_app;"

# 3. Restaurar desde el archivo comprimido más reciente
ARCHIVO_BDD="/home/ubuntu/backups/bdd/sadday_bdd_2026-04-28.sql.gz"

zcat "$ARCHIVO_BDD" | docker exec -i sadday-db \
    psql -U sadday_admin -d sadday_app

# 4. Verificar que los datos están ahí
docker exec sadday-db \
    psql -U sadday_admin -d sadday_app -c "\dt"   # lista las tablas

docker exec sadday-db \
    psql -U sadday_admin -d sadday_app -c "SELECT COUNT(*) FROM socios;"

# 5. Reiniciar la API
docker start sadday-api
```

> Flyway no necesita correr las migraciones al restaurar: el dump ya incluye la estructura completa de la base de datos con todos los datos. Flyway solo se activa si la tabla `flyway_schema_history` está vacía, lo que no ocurre tras una restauración de un dump completo.

---

## 10. Cómo restaurar los logs

Los logs son de solo lectura (no son datos transaccionales), así que la restauración es simplemente descomprimir:

```bash
# Descomprimir el backup de logs en un directorio temporal para consulta:
mkdir -p /tmp/logs_restore
tar xzf /home/ubuntu/backups/logs/sadday_logs_2026-04-28.tar.gz -C /tmp/logs_restore

# Consultar:
ls /tmp/logs_restore/app/logs/
cat /tmp/logs_restore/app/logs/sadday-app-error.log | jq .
```

---

## 11. Verificación periódica recomendada

Una vez al mes, verificar manualmente que los backups son válidos y restaurables:

```bash
# 1. Confirmar que existen backups recientes:
ls -lht /home/ubuntu/backups/bdd/ | head -5
ls -lht /home/ubuntu/backups/logs/ | head -5

# 2. Confirmar que el dump más reciente no está vacío:
ULTIMO=$(ls -t /home/ubuntu/backups/bdd/*.sql.gz | head -1)
BYTES=$(stat -c%s "$ULTIMO")
echo "Tamaño del último backup: $BYTES bytes"
zcat "$ULTIMO" | head -5   # debe mostrar cabecera SQL de PostgreSQL

# 3. Confirmar que el cron se ejecutó (ver la última entrada del log):
tail -20 /home/ubuntu/backups/backup.log

# 4. Verificar que existe copia en S3 (si se configuró):
aws s3 ls s3://sadday-backups/bdd/ | tail -5
```

---

## 12. Resumen rápido

| Tarea | Frecuencia | Comando/Acción |
|-------|-----------|----------------|
| Backup automático (BDD + logs) | Cada domingo 02:00 AM | Cron → `backup.sh` |
| Backup manual de emergencia | Cuando se necesite | `bash /home/ubuntu/scripts/backup.sh` |
| Copia offsite a S3 | Incluida en el script | Automática tras cada backup |
| Limpieza de backups viejos | Incluida en el script | Automática: 60 días BDD, 90 días logs |
| Verificación de integridad | Mensual | Manual — ver sección 11 |
| Restauración completa | Solo en desastre | Ver sección 9 |
