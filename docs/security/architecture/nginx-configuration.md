# Configuración de Nginx — Sadday App

**Última actualización:** 2026-05-28
**Audiencia:** Operador que despliega o mantiene el servidor de producción o staging.

Hay dos capas de Nginx con roles distintos. Entender la separación es crítico para no romper la cadena de confianza de IP descrita en `security-architecture.md`.

---

## Arquitectura de dos capas

```
Internet
    │ HTTPS (Cloudflare termina TLS en el edge)
    ▼
Cloudflare (WAF + DDoS + proxy)
    │ HTTPS → inyecta CF-Connecting-IP con la IP real del usuario
    ▼
┌────────────────────────────────────────────────┐
│  HOST — Ubuntu 24.04 (AWS Lightsail)           │
│                                                │
│  ① Nginx host :443                             │
│     - Allowlist IPs de Cloudflare              │
│     - Termina TLS (cert Cloudflare / LE)       │
│     - Lee CF-Connecting-IP → X-Real-IP         │
│     - proxy_pass → contenedores en loopback    │
│                        │                       │
│          ┌─────────────┴─────────────┐         │
│          ▼                           ▼         │
│  127.0.0.1:3000             127.0.0.1:8080     │
│  ② Frontend container       API container      │
│     nginx:stable-alpine      Spring Boot       │
│     - Sirve React SPA        - Solo loopback   │
│     - Proxea /api →          - No expone TLS   │
│       api:8080 (Docker net)                    │
└────────────────────────────────────────────────┘
```

**Flujo de un request del browser:**
1. Browser → Cloudflare (TLS termination en el edge)
2. Cloudflare → Host Nginx `:443` con `CF-Connecting-IP: <IP real del usuario>`
3. Host Nginx → Frontend container `:3000` (para rutas SPA y API)
4. Frontend container Nginx → Backend container `:8080` (solo para `/api`)

**Por qué el frontend container también proxea la API:**
El frontend container Nginx maneja internamente los paths `/api` vía red Docker. Esto permite que en desarrollo (`docker compose up`) todo funcione igual sin host Nginx. El backend también queda accesible directamente en `127.0.0.1:8080` desde el host por si el checklist de deploy o el monitoring necesitan accederlo localmente.

---

## ① Host Nginx — configuración de referencia

Este archivo no existe en el repositorio. Debe crearse en `/etc/nginx/sites-available/sadday` en el servidor.

```nginx
# /etc/nginx/sites-available/sadday
# Enlace simbólico: /etc/nginx/sites-enabled/sadday → este archivo

# ─── IPs de Cloudflare (actualizar periódicamente desde https://www.cloudflare.com/ips/) ───
# IPv4
geo $cloudflare_ip {
    default         0;
    103.21.244.0/22 1;
    103.22.200.0/22 1;
    103.31.4.0/22   1;
    104.16.0.0/13   1;
    104.24.0.0/14   1;
    108.162.192.0/18 1;
    131.0.244.0/22  1;
    141.101.64.0/18 1;
    162.158.0.0/15  1;
    172.64.0.0/13   1;
    173.245.48.0/20 1;
    188.114.96.0/20 1;
    190.93.240.0/20 1;
    197.234.240.0/22 1;
    198.41.128.0/17 1;
}

# ─── Extraer IP real del header de Cloudflare ─────────────────────────────────────────────
# Solo se confía en CF-Connecting-IP si el remoteAddr viene de Cloudflare.
# El backend hace su propia validación (trusted-proxy-cidrs = 127.0.0.1/32).
map $cloudflare_ip $real_client_ip {
    1  $http_cf_connecting_ip;
    0  $remote_addr;
}

# ─── Redirect HTTP → HTTPS ────────────────────────────────────────────────────────────────
server {
    listen 80;
    server_name app.el-sadday.com;

    # Bloquear tráfico que no venga de Cloudflare
    if ($cloudflare_ip = 0) {
        return 444;  # Cerrar conexión sin respuesta
    }

    return 301 https://$host$request_uri;
}

# ─── HTTPS principal ──────────────────────────────────────────────────────────────────────
server {
    listen 443 ssl;
    server_name app.el-sadday.com;
    http2 on;

    # Bloquear tráfico que no venga de Cloudflare
    if ($cloudflare_ip = 0) {
        return 444;
    }

    # ── TLS ───────────────────────────────────────────────────────────────────────────────
    # Con Cloudflare en modo Full (strict):
    #   - Cloudflare termina TLS con el usuario (cert gestionado por Cloudflare en el edge)
    #   - Cloudflare abre una segunda conexión TLS hacia Nginx
    #   - Este cert puede ser autofirmado o de Cloudflare Origin Certificate (recomendado)
    #
    # Opción A — Cloudflare Origin Certificate (recomendado):
    ssl_certificate     /etc/nginx/ssl/cloudflare-origin.crt;
    ssl_certificate_key /etc/nginx/ssl/cloudflare-origin.key;
    #
    # Opción B — Let's Encrypt (si se prefiere cert público):
    # ssl_certificate     /etc/letsencrypt/live/app.el-sadday.com/fullchain.pem;
    # ssl_certificate_key /etc/letsencrypt/live/app.el-sadday.com/privkey.pem;
    # include             /etc/letsencrypt/options-ssl-nginx.conf;
    # ssl_dhparam         /etc/letsencrypt/ssl-dhparams.pem;

    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305;
    ssl_prefer_server_ciphers off;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    # ── Headers de seguridad del host ─────────────────────────────────────────────────────
    # HSTS — instruye al browser a usar HTTPS durante 1 año
    # Precaución: no activar includeSubDomains si hay subdominios sin HTTPS
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # Reenviar IP real del usuario al backend (para rate limiting y auditoría)
    proxy_set_header X-Real-IP         $real_client_ip;
    proxy_set_header X-Forwarded-For   $real_client_ip;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Host              $host;

    # ── Timeouts y buffers ────────────────────────────────────────────────────────────────
    proxy_connect_timeout  10s;
    proxy_send_timeout     30s;
    proxy_read_timeout     30s;

    # Buffers — evitar escrituras a disco para respuestas normales de API
    proxy_buffering    on;
    proxy_buffer_size  4k;
    proxy_buffers      8 4k;

    # ── Gzip ──────────────────────────────────────────────────────────────────────────────
    gzip              on;
    gzip_types        text/plain application/json application/javascript text/css
                      application/xml text/xml application/x-font-ttf font/woff2
                      image/svg+xml;
    gzip_min_length   1024;
    gzip_vary         on;
    gzip_proxied      any;

    # ── Proxy al frontend container ───────────────────────────────────────────────────────
    # Todo el tráfico va al frontend container.
    # El frontend nginx interno maneja el split SPA vs /api.
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # ── Ocultar versión de Nginx en errores ───────────────────────────────────────────────
    server_tokens off;
}
```

### Activar la configuración

```bash
# Enlace simbólico
sudo ln -s /etc/nginx/sites-available/sadday /etc/nginx/sites-enabled/sadday

# Deshabilitar el sitio por defecto si existe
sudo rm -f /etc/nginx/sites-enabled/default

# Verificar sintaxis antes de recargar
sudo nginx -t

# Recargar sin downtime
sudo nginx -s reload
```

### Verificar que solo Cloudflare puede conectarse

```bash
# Desde una máquina externa (NO desde Cloudflare), esto debe retornar vacío o error:
curl -v https://IP_DEL_SERVIDOR -H "Host: app.el-sadday.com"
# Debe cerrar la conexión — nginx devuelve 444
```

---

## ② Frontend container Nginx — `frontend/nginx.conf`

Esta configuración ya existe en el repositorio y se empaqueta en la imagen Docker del frontend. Se documenta aquí para referencia de las decisiones tomadas.

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # ── Resolver Docker ───────────────────────────────────────────────────────────────────
    # Fuerza re-resolución DNS en cada request al backend.
    # Sin esto Nginx cachea la IP del contenedor "api" al arrancar
    # y devuelve 502 si el contenedor se reinicia y obtiene una nueva IP de Docker.
    resolver 127.0.0.11 valid=10s ipv6=off;

    # ── Security headers ──────────────────────────────────────────────────────────────────
    # IMPORTANTE: add_header en un bloque location hijo ANULA la herencia del padre.
    # Por eso los headers se repiten en el bloque de assets (ver más abajo).
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; style-src-elem 'self' 'unsafe-inline' https://fonts.googleapis.com; style-src-attr 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' https://fonts.gstatic.com; connect-src 'self'; worker-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'" always;
    add_header X-Content-Type-Options  "nosniff"                         always;
    add_header X-Frame-Options         "DENY"                            always;
    add_header Referrer-Policy         "strict-origin-when-cross-origin" always;
    add_header Permissions-Policy      "camera=(), microphone=(), geolocation=(), payment=()" always;

    # ── Proxy al backend ──────────────────────────────────────────────────────────────────
    location /api {
        set $backend http://api:8080;
        proxy_pass         $backend;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }

    # ── SPA routing ───────────────────────────────────────────────────────────────────────
    location / {
        try_files $uri $uri/ /index.html;
    }

    # ── Caché de assets estáticos ─────────────────────────────────────────────────────────
    # Los assets de Vite llevan un hash en el nombre → inmutables → caché de 1 año.
    # add_header aquí anula herencia → se repiten los security headers.
    location ~* \.(?:js|css|woff2?|png|svg|ico|webp)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";

        add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; style-src-elem 'self' 'unsafe-inline' https://fonts.googleapis.com; style-src-attr 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' https://fonts.gstatic.com; connect-src 'self'; worker-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'" always;
        add_header X-Content-Type-Options  "nosniff"                         always;
        add_header X-Frame-Options         "DENY"                            always;
        add_header Referrer-Policy         "strict-origin-when-cross-origin" always;
        add_header Permissions-Policy      "camera=(), microphone=(), geolocation=(), payment=()" always;
    }
}
```

### Por qué cada decisión de la CSP

| Directiva | Valor | Motivo |
|-----------|-------|--------|
| `script-src 'self'` | Sin `'unsafe-inline'` | El polyfill de modulepreload de Vite está deshabilitado en `vite.config.ts`. Todos los scripts vienen del bundle. |
| `script-src 'unsafe-eval'` | Requerido | Recharts usa `new Function()` internamente para renderizar labels dinámicos. Sin esto, los gráficos no cargan. |
| `style-src 'unsafe-inline'` | Requerido | Radix UI, Recharts y Sonner inyectan estilos inline vía JS para posicionamiento dinámico de overlays y tooltips. |
| `style-src-elem` | Explícita | Firefox no hace fallback de `style-src` para elementos `<style>` y `<link>`, cae a `default-src`. Declaración explícita necesaria. |
| `img-src data: blob:` | `data:` para QR codes | `qrcode.react` genera data URIs. `blob:` reservado para posibles previews. |
| `font-src fonts.gstatic.com` | Fuente Inter | La fuente Inter se carga desde Google Fonts CDN. |
| `connect-src 'self'` | Sin dominios externos | Todas las llamadas API van al mismo origen (nginx proxea `/api`). |
| `frame-ancestors 'none'` | Equivalente a `X-Frame-Options: DENY` | Previene clickjacking. Declarado en CSP y como header separado para máxima compatibilidad. |

---

## Actualización periódica de IPs de Cloudflare

Cloudflare publica sus rangos en `https://www.cloudflare.com/ips-v4/` y `https://www.cloudflare.com/ips-v6/`. Deben actualizarse en el bloque `geo` del host Nginx cuando cambien.

```bash
# Verificar si los rangos actuales coinciden con los publicados
curl -s https://www.cloudflare.com/ips-v4/

# Después de actualizar /etc/nginx/sites-available/sadday:
sudo nginx -t && sudo nginx -s reload
```

Cloudflare también publica los rangos vía API JSON:
```
https://api.cloudflare.com/client/v4/ips
```

---

## Verificación post-deploy

```bash
# Headers de seguridad presentes en la respuesta
curl -sI https://app.el-sadday.com | grep -E "strict-transport|content-security|x-frame|x-content-type|referrer"

# HSTS presente
curl -sI https://app.el-sadday.com | grep -i strict-transport-security
# Esperado: Strict-Transport-Security: max-age=31536000; includeSubDomains

# Versión de Nginx NO expuesta
curl -sI https://app.el-sadday.com | grep -i server
# Esperado: ningún output (o solo "server: cloudflare" del edge)

# TLS — solo TLS 1.2 y 1.3
nmap --script ssl-enum-ciphers -p 443 app.el-sadday.com
# SSLv3, TLS 1.0, TLS 1.1 no deben aparecer
```

---

## Relación con otros documentos

| Documento | Relación |
|-----------|----------|
| [`security-architecture.md`](security-architecture.md) | Explica la cadena de confianza IP (Cloudflare → Nginx → backend) y por qué los CIDRs de Cloudflare no van en el backend |
| [`production-deployment-checklist.md`](production-deployment-checklist.md) | Fase 4 — pasos de instalación y verificación de Nginx |
| [`secret-rotation.md`](secret-rotation.md) | No aplica directamente — Nginx no maneja secretos del negocio |
