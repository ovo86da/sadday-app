# Guía de respuesta a incidentes — Sadday App

**Última actualización:** 2026-05-01
**Audiencia:** Administrador del sistema.

Este documento describe las acciones concretas a tomar ante los incidentes de seguridad más probables. Actúa rápido — en un incidente activo, cada minuto cuenta.

---

## Principios generales

1. **Contener primero, investigar después.** Antes de entender qué pasó, corta el acceso del atacante.
2. **No eliminar evidencia.** No borres logs ni tablas antes de copiarlos — son necesarios para el análisis forense.
3. **Comunicar con calma.** Notificar a los afectados solo cuando se tenga claridad de qué fue comprometido.
4. **Documentar todo.** Anotar hora exacta, acciones tomadas y hallazgos durante el incidente.

---

## Incidente 1 — Robo de refresh token detectado

**Señal:** En `security_events` aparece un evento de tipo `REFRESH_TOKEN_STOLEN` para un usuario, o un usuario reporta actividad sospechosa en su cuenta.

El sistema detecta esto automáticamente cuando un refresh token ya revocado es reutilizado → revoca todos los tokens del usuario afectado.

### Acciones inmediatas

```sql
-- 1. Verificar el alcance: ¿cuántas sesiones tuvo el atacante?
SELECT se.event_type, se.ip_address, se.country_code, se.city,
       se.user_agent, se.created_at
FROM security_events se
JOIN socios s ON se.socio_id = s.id
WHERE s.correo = 'correo_del_afectado@example.com'
ORDER BY se.created_at DESC
LIMIT 50;

-- 2. Confirmar que todos los refresh tokens del usuario están revocados
SELECT id, platform, ip_address, revoked, revoked_at, created_at
FROM refresh_tokens
WHERE socio_id = (SELECT id FROM socios WHERE correo = 'correo@example.com');
-- Si alguno tiene revoked = false, revocar manualmente:
UPDATE refresh_tokens
SET revoked = true, revoked_at = now()
WHERE socio_id = (SELECT id FROM socios WHERE correo = 'correo@example.com')
  AND revoked = false;
```

```bash
# 3. Verificar qué acciones realizó el atacante (auditoría)
# En la UI: Admin → Auditoría → filtrar por socio afectado
```

### Si la cuenta afectada es ADMIN

- [ ] Revocar todos los tokens (paso 2 arriba)
- [ ] Cambiar la contraseña del admin desde la consola de BD (no desde la app — el atacante puede tener acceso):
```sql
-- Forzar cambio de contraseña en el próximo login
UPDATE usuarios_auth SET password_must_change = true
WHERE socio_id = (SELECT id FROM socios WHERE correo = 'admin@example.com');
```
- [ ] Rotar las claves JWT (ver `secret-rotation.md`) para invalidar cualquier access token que el atacante pueda haber guardado
- [ ] Revisar cambios en `configuracion_sistema` y roles de socios en las últimas 24h

### Comunicación al usuario afectado

Notificar al usuario que:
- Su sesión fue cerrada por seguridad
- Debe cambiar su contraseña al volver a iniciar sesión
- Si tenía 2FA activo, es recomendable re-configurarlo

---

## Incidente 2 — Cuenta admin comprometida

**Señal:** El administrador reporta que alguien más tiene acceso a su cuenta, o se detectan acciones en auditoría que el admin no realizó.

### Acciones inmediatas (en orden)

```bash
# Paso 1: Forzar cierre de sesión de TODAS las sesiones activas del admin
# Usar el endpoint desde otra cuenta admin, o directamente en BD:
```

```sql
-- Revocar todos los refresh tokens del admin
UPDATE refresh_tokens
SET revoked = true, revoked_at = now()
WHERE socio_id = (SELECT id FROM socios WHERE correo = 'admin@example.com')
  AND revoked = false;

-- Bloquear el login del admin temporalmente
UPDATE usuarios_auth
SET login_blocked = true, blocked_until = now() + interval '24 hours'
WHERE socio_id = (SELECT id FROM socios WHERE correo = 'admin@example.com');
```

```bash
# Paso 2: Cambiar la contraseña del admin directamente
# (No desde la UI — el atacante podría estar viendo)
# Usar el endpoint de reset de contraseña desde el servidor:
curl -X POST https://TU_DOMINIO/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"correo": "admin@example.com"}'
# El enlace de reset llega al email del admin
```

```sql
-- Paso 3: Revisar qué hizo el atacante
SELECT accion, entidad_afectada, entidad_id, datos_anteriores,
       datos_nuevos, ip_address, created_at
FROM auditoria
WHERE actor_username = 'username_del_admin'
  AND created_at > now() - interval '48 hours'
ORDER BY created_at DESC;
```

- [ ] ¿Se modificó `configuracion_sistema`? Revertir si es necesario.
- [ ] ¿Se cambiaron roles de socios? Revertir.
- [ ] ¿Se eliminaron socios? Verificar soft-delete (`eliminada = true`) y revertir si aplica.

```bash
# Paso 4: Desbloquear al admin legítimo una vez asegurada la cuenta
```

```sql
UPDATE usuarios_auth
SET login_blocked = false, blocked_until = null,
    failed_attempts = 0, password_must_change = true
WHERE socio_id = (SELECT id FROM socios WHERE correo = 'admin@example.com');
```

### Si el acceso SSH al servidor también fue comprometido

Ver sección "Klave privada SSH" en `secret-rotation.md` y rotar todos los secretos.

---

## Incidente 3 — Brecha de base de datos

**Señal:** Evidencia de acceso no autorizado a la BD (en logs de PostgreSQL, alertas de AWS, o reporte externo).

### Evaluación de daño

| Dato comprometido | Impacto | Acción |
|---|---|---|
| Hashes Argon2id de contraseñas | Bajo — Argon2id con 19MB es extremadamente lento de crackar | Obligar cambio de contraseña a todos los usuarios |
| `totp_secret` cifrados | Bajo si `TOTP_ENCRYPTION_KEY` no fue comprometida | Rotar TOTP key si hubo acceso al servidor |
| `token_hash` de refresh tokens | Medio — los tokens raw no están en BD | Revocar todos los refresh tokens |
| PII de socios (nombre, cédula, correo) | Alto — notificación a afectados requerida | Notificar a socios, reportar según ley aplicable |
| `email_verification_tokens` (cédula, correo en claro) | Alto — tokens recientes | Invalidar todos los tokens pendientes |

### Acciones inmediatas

```sql
-- 1. Revocar TODOS los refresh tokens de TODOS los usuarios
UPDATE refresh_tokens SET revoked = true, revoked_at = now() WHERE revoked = false;

-- 2. Marcar todos los tokens de email como usados (link de invitación invalidado)
UPDATE email_verification_tokens SET used = true WHERE used = false;

-- 3. Forzar cambio de contraseña a todos los usuarios en el próximo login
UPDATE usuarios_auth SET password_must_change = true;
```

```bash
# 4. Rotar TODOS los secretos del sistema
# Ver: docs/security/architecture/secret-rotation.md
# Orden: DB_PASSWORD → JWT keys → TOTP key → S3 → Email SMTP

# 5. Cambiar contraseña de PostgreSQL inmediatamente
docker exec -it sadday-db psql -U postgres -c \
  "ALTER USER $DB_USER PASSWORD '$(openssl rand -base64 32)';"
```

### Notificación

Si hubo acceso a PII de socios (nombres, cédulas, correos, teléfonos):
- Notificar a los socios afectados por email dentro de 72 horas (buena práctica GDPR/LGPD, aunque Ecuador no la exija legalmente aún)
- Indicar: qué datos fueron expuestos, qué acciones se tomaron, qué deben hacer ellos (estar alertas a phishing)

---

## Incidente 4 — Ataque de fuerza bruta en progreso

**Señal:** Múltiples intentos de login fallidos detectados en `security_events` o `auditoria` desde la misma IP o contra la misma cuenta.

### Verificar el estado

```sql
-- Intentos fallidos por IP en la última hora
SELECT ip_address, count(*) as intentos
FROM security_events
WHERE event_type = 'LOGIN_FAILED'
  AND created_at > now() - interval '1 hour'
GROUP BY ip_address
ORDER BY intentos DESC
LIMIT 20;

-- Cuentas con más intentos fallidos
SELECT s.correo, ua.failed_attempts, ua.login_blocked, ua.blocked_until
FROM usuarios_auth ua
JOIN socios s ON ua.socio_id = s.id
WHERE ua.failed_attempts > 0
ORDER BY ua.failed_attempts DESC;
```

### Si el rate limiter ya está actuando

El sistema bloquea automáticamente:
- 10 intentos de login por IP por minuto (Bucket4j)
- 3 intentos fallidos por cuenta → bloqueo de 24h

En ese caso, monitorear y no intervenir manualmente a menos que el ataque escale.

### Si el ataque es masivo (muchas IPs distintas — botnet)

```bash
# Bloquear el rango de IPs en Cloudflare (WAF → IP Rules)
# O activar "I'm Under Attack" mode en Cloudflare temporalmente
```

---

## Incidente 5 — Email del sistema marcado como spam o rebotando

**Señal:** Los emails de invitación o reset de contraseña no llegan a los usuarios.

### Diagnóstico

```bash
# 1. Verificar logs del backend para errores SMTP
docker compose logs api --tail=200 | grep -i "mail\|smtp\|send"

# 2. Probar envío manualmente vía endpoint de diagnóstico
curl -X POST https://TU_DOMINIO/api/v1/admin/diagnostico/geoip \
  -H "Authorization: Bearer TOKEN_ADMIN"
# Si llega el email de alerta GeoIP, el SMTP funciona

# 3. Verificar estado de AWS SES en la consola
# Dashboard → Sending Statistics → Bounces/Complaints
```

### Causas comunes

| Causa | Solución |
|---|---|
| SPF/DKIM/DMARC no configurados | Ver `production-deployment-checklist.md` Fase 5 |
| Cuenta SES en sandbox | Solicitar producción access en AWS Console |
| Credenciales SMTP expiradas | Rotar credenciales (ver `secret-rotation.md`) |
| IP del servidor en lista negra | Verificar en mxtoolbox.com/blacklists.aspx |

---

## Post-incidente — Qué documentar

Después de cada incidente, crear un documento en `docs/security/audits/YYYY-MM-DD-incident-TIPO.md` con:

1. **Cronología:** cuándo se detectó, cuándo se contuvo, cuándo se resolvió
2. **Vector:** cómo entró el atacante o qué falló
3. **Datos afectados:** qué información fue expuesta o modificada
4. **Acciones tomadas:** todos los pasos ejecutados, en orden cronológico
5. **Root cause:** causa raíz del incidente
6. **Mejoras:** qué cambio de configuración, código o proceso evitaría que ocurra de nuevo

---

## Contactos de emergencia

| Rol | Contacto |
|---|---|
| Administrador del sistema | `ADMIN_ALERT_EMAIL` configurado en producción |
| AWS Support | Consola AWS → Support Center |
| Cloudflare Support | dash.cloudflare.com → Support |
| MaxMind | support@maxmind.com |
