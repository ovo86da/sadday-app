# Rotación de secretos — Sadday App

**Última actualización:** 2026-05-01
**Audiencia:** Administrador del sistema.

Este documento describe cómo rotar cada secreto crítico, cuándo hacerlo y qué impacto tiene en usuarios activos. Ejecutar siempre con backup previo de la BD.

---

## Cuándo rotar

| Evento | Secretos a rotar |
|---|---|
| Sospecha de compromiso de cualquier secreto | El secreto afectado + todos los relacionados |
| Rotación periódica programada (cada 6-12 meses) | JWT keys, TOTP key |
| Empleado/colaborador con acceso al servidor abandona | Todos |
| Acceso no autorizado detectado al servidor | Todos |
| Cambio de proveedor de email/S3 | Credenciales del proveedor afectado |

---

## 1. Claves RSA para JWT

**Impacto en usuarios:** Todos los access tokens activos quedan inválidos inmediatamente. Los refresh tokens siguen siendo válidos (están en BD, no firmados con RSA). Los usuarios verán un error 401 en la próxima petición — el cliente renueva automáticamente via `/auth/refresh` y el usuario no nota la interrupción si el refresh token es válido.

**Impacto en operación:** Breve ventana (~segundos) donde el sistema rechaza tokens hasta que la nueva clave carga.

### Pasos

```bash
# 1. Generar nuevo par de claves
openssl genrsa -out private_new.pem 4096
openssl rsa -in private_new.pem -pubout -out public_new.pem

# 2. Reemplazar en el servidor
sudo cp /opt/sadday/keys/private.pem /opt/sadday/keys/private_old.pem  # backup
sudo cp /opt/sadday/keys/public.pem  /opt/sadday/keys/public_old.pem   # backup
sudo mv private_new.pem /opt/sadday/keys/private.pem
sudo mv public_new.pem  /opt/sadday/keys/public.pem
sudo chmod 600 /opt/sadday/keys/private.pem

# 3. Reiniciar el backend para que cargue las nuevas claves
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart api

# 4. Verificar que el servicio arrancó correctamente
docker compose logs api --tail=20 | grep -E "Started|ERROR"

# 5. Eliminar el backup de la clave antigua (una vez verificado)
sudo rm /opt/sadday/keys/private_old.pem /opt/sadday/keys/public_old.pem
```

**Nota:** Si se quiere cero downtime absoluto, implementar soporte multi-clave (JWK Set con `kid`). Para este app, el breve impacto de renovar access tokens es aceptable.

---

## 2. Clave de cifrado TOTP (`TOTP_ENCRYPTION_KEY`)

**Impacto en usuarios:** ⚠️ **Crítico — requiere planeación cuidadosa.**

La clave cifra los `totp_secret` de todos los usuarios con 2FA activo. Si se cambia sin migrar los datos, todos los usuarios con 2FA quedan bloqueados permanentemente — no pueden hacer login (el secret descifrado con la nueva clave produce basura).

### Pasos (con migración de datos)

```bash
# ANTES de cambiar la clave, es obligatorio ejecutar una migración que:
# 1. Descifre todos los totp_secret con la clave ANTIGUA
# 2. Los re-cifre con la clave NUEVA
# Esto debe hacerse en una transacción atómica.

# 1. Generar nueva clave
NEW_KEY=$(openssl rand -base64 32)
echo "Nueva clave: $NEW_KEY"  # guardar en lugar seguro

# 2. Ejecutar script de migración (implementar antes de rotar)
# java -jar sadday-migrate-totp.jar --old-key=$OLD_KEY --new-key=$NEW_KEY

# 3. Una vez migrada la BD, actualizar .env
# TOTP_ENCRYPTION_KEY=NEW_KEY

# 4. Reiniciar el backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart api

# 5. Verificar que usuarios con 2FA pueden hacer login
```

⚠️ **El script de migración de TOTP no está implementado actualmente.** Antes de rotar esta clave en producción, se debe implementar y probar en staging. Mientras tanto, si se sospecha compromiso de la clave TOTP, la acción inmediata es **deshabilitar 2FA a todos los usuarios** desde la BD y obligarles a re-configurarlo.

```sql
-- Acción de emergencia: deshabilitar 2FA a todos los usuarios
UPDATE usuarios_auth SET totp_enabled = false, totp_secret = null;
```

---

## 3. Contraseña de base de datos (`DB_PASSWORD`)

**Impacto en usuarios:** Si el backend no puede conectar a la BD, el servicio cae. La ventana es de segundos si los pasos se siguen en orden.

### Pasos

```bash
# 1. Generar nueva contraseña
NEW_DB_PASS=$(openssl rand -base64 32)

# 2. Cambiar la contraseña en PostgreSQL
docker exec -it sadday-db psql -U postgres -c \
  "ALTER USER $DB_USER PASSWORD '$NEW_DB_PASS';"

# 3. Actualizar .env en el servidor
# DB_PASSWORD=NEW_DB_PASS

# 4. Reiniciar el backend (para que tome la nueva contraseña)
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart api

# 5. Verificar conectividad
curl -s https://TU_DOMINIO/actuator/health
```

---

## 4. Credenciales S3 / AWS (`AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY`)

**Impacto en usuarios:** Subida/descarga de documentos (PDFs) falla mientras el backend usa credenciales inválidas. La ventana es de segundos si los pasos se siguen en orden.

### Pasos

```bash
# 1. En la consola de AWS IAM:
#    a. Crear nuevas access keys para el IAM user de Sadday
#    b. Copiar las nuevas credenciales

# 2. Actualizar .env en el servidor con las nuevas credenciales
# AWS_ACCESS_KEY_ID=NEW_KEY_ID
# AWS_SECRET_ACCESS_KEY=NEW_SECRET

# 3. Reiniciar el backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart api

# 4. Verificar que S3 funciona (subir un documento de prueba desde la UI)

# 5. Desactivar las credenciales antiguas en la consola de AWS IAM
#    (NO eliminar inmediatamente — esperar 24h para confirmar que nada las usa)
```

---

## 5. Credenciales de email (AWS SES SMTP)

**Impacto en usuarios:** Emails de login, invitaciones y alertas dejan de enviarse mientras las credenciales son inválidas.

### Pasos

```bash
# 1. En la consola de AWS SES → SMTP Settings → Create SMTP credentials
#    (Genera un nuevo IAM user con permisos de SES)

# 2. Actualizar .env
# MAIL_USERNAME=NEW_SMTP_USER
# MAIL_PASSWORD=NEW_SMTP_PASS

# 3. Reiniciar el backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart api

# 4. Verificar: ejecutar el endpoint de test de alerta GeoIP para provocar un email
curl -X POST https://TU_DOMINIO/api/v1/admin/diagnostico/geoip \
  -H "Authorization: Bearer TOKEN_ADMIN"
# Verificar que llega el email
```

---

## 6. Credenciales MaxMind GeoIP

**Impacto en usuarios:** Ninguno inmediato. El `.mmdb` existente sigue funcionando. Solo afecta las actualizaciones futuras de la base de datos.

### Pasos

```bash
# 1. En maxmind.com → Manage License Keys → Generate new key

# 2. Actualizar .env
# MAXMIND_LICENSE_KEY=NEW_KEY

# 3. Reiniciar el contenedor geoip-updater (no el backend completo)
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart geoip-updater

# 4. Verificar en logs que geoipupdate se autenticó correctamente
docker compose logs geoip-updater --tail=20
```

---

## 7. Clave privada SSH del servidor

Si se sospecha que la clave SSH de acceso al servidor fue comprometida:

```bash
# 1. Desde otra sesión SSH activa (NO cerrar la sesión actual):
#    Agregar la nueva clave pública al archivo authorized_keys
echo "NEW_PUBLIC_KEY" >> ~/.ssh/authorized_keys

# 2. Verificar que puedes hacer login con la nueva clave desde otra terminal

# 3. Eliminar la clave antigua de authorized_keys
# 4. Actualizar la clave en el panel de AWS Lightsail (o donde esté registrada)
# 5. Actualizar el secret LIGHTSAIL_SSH_KEY en GitHub Actions
```

---

## Registro de rotaciones

Mantener un log privado (fuera del repo) de cada rotación:

| Fecha | Secreto rotado | Motivo | Ejecutado por |
|---|---|---|---|
| YYYY-MM-DD | JWT keys | Rotación semestral | nombre |
| YYYY-MM-DD | DB_PASSWORD | Sospecha de compromiso | nombre |
