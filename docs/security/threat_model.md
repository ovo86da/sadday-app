# Threat Model — Sadday App

> **Metodología:** STRIDE + OWASP Top 10 + OWASP API Security Top 10  
> **Última revisión:** 2026-04-17  
> **Revisión anterior:** 2026-03-06  
> **Cambios en esta revisión:** Frontend containerizado, pipeline CI/CD completo (Trivy + cosign + SBOM), entorno staging (Proxmox), AdminBootstrap, revisión de código real de seguridad.

---

## 1. Alcance y Activos

### Componentes del sistema

| Componente | Descripción | Cambios desde revisión anterior |
|---|---|---|
| Cloudflare | WAF, CDN, DDoS mitigation | Sin cambios |
| Firewall VPS | UFW/iptables en AWS Lightsail | Sin cambios |
| Nginx (host) | Reverse proxy, TLS termination | Sin cambios |
| Frontend container | nginx:alpine sirviendo React SPA | **Nuevo** |
| Spring Boot API | Lógica de negocio, autenticación, autorización | Sin cambios |
| PostgreSQL | Persistencia de datos | Sin cambios |
| MinIO (staging) / AWS S3 (prod) | Almacenamiento de PDFs | MinIO añadido para staging |
| AWS SES / SMTP | Notificaciones transaccionales | Sin cambios |
| GitHub Actions | CI/CD, build, scan, deploy | **Nuevo** |
| GHCR (ghcr.io) | Registro de imágenes Docker | **Nuevo** |
| Staging VM (Proxmox) | Entorno de validación pre-producción | **Nuevo** |

### Activos a proteger

| Activo | Sensibilidad | Impacto si comprometido |
|---|---|---|
| Credenciales (passwords, tokens) | Crítico | Acceso no autorizado total |
| TOTP secrets (cifrados AES-256) | Crítico | Bypass de 2FA |
| PII de socios (cédula, fecha nac, contacto emergencia) | Alto | Fraude, exposición de datos personales |
| Datos de salud (tipo_sangre) | Alto | Privacidad, discriminación |
| Registro de auditoría | Crítico | Pérdida de trazabilidad, encubrimiento |
| PDFs de informes y actas | Medio | Exposición de información interna del club |
| Configuración del sistema (`configuracion_sistema`) | Alto | Modificación de reglas de negocio y seguridad |
| Tabla `acceso_ruta_por_nivel` | **Crítico** | Socios no calificados en rutas peligrosas → riesgo físico real |
| Claves JWT RSA-4096 | Crítico | Forja de tokens, suplantación de identidad |
| Secrets de CI/CD (GitHub Actions) | Crítico | Acceso total al servidor de producción |
| Imágenes Docker en GHCR | Alto | Análisis de código, vectores de ataque, SSRF |
| Credenciales de staging | Medio | Información de arquitectura, pivoting a prod |

### Actores (Threat Actors)

| Actor | Capacidad | Motivación |
|---|---|---|
| Atacante externo no autenticado | Media | Acceso a datos, denegación de servicio |
| Socio autenticado malicioso | Baja-Media | Escalar privilegios, ver datos de otros socios |
| Directivo comprometido | Alta | Modificar umbrales de riesgo, acceder a PII |
| Admin comprometido | Muy Alta | Control total del sistema |
| Insider (secretaria/admin) | Alta | Acceso masivo a PII de todos los socios |
| Atacante de infraestructura | Alta | Comprometer servidor o base de datos |
| Atacante de supply chain | Alta | Comprometer imagen Docker o dependencia en CI/CD |
| Atacante de repositorio / CI | Media | Comprometer secrets de GitHub Actions → acceso al servidor |

---

## 2. Análisis STRIDE por Componente

### 2.1 Autenticación (Login / Tokens)

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| A-01 | Credential stuffing / brute force | S | 🔴 Crítico | Alta | ✅ | Lockout configurable (`MAX_INTENTOS_LOGIN`, default 3 → 24h). Rate limiting Bucket4j: 10 req/min en `/login`. Auto-desbloqueo tras expiración. |
| A-02 | Enumeración de usuarios | I | 🟠 Alto | Alta | ✅ | Respuesta genérica idéntica para usuario inexistente y password incorrecta. Auditado silenciosamente. |
| A-03 | Timing attack en comparación de passwords | I | 🟡 Medio | Baja | ✅ | **Argon2id** con parámetros OWASP 2026 (16-byte salt, 32-byte hash, 19 MB memoria, 2 iteraciones). Tiempo constante por diseño. |
| A-04 | Robo de access token (XSS) | I | 🔴 Crítico | Media | ✅ | Access token solo en memoria JS (Zustand store). Nunca en `localStorage` ni cookie. Expiración 15 min. |
| A-05 | Robo y reuso de refresh token | I | 🔴 Crítico | Baja | ✅ | Almacenado como SHA-256 en DB. Rotación en cada uso. Reuso de token revocado → revoca **todos** los tokens del usuario (token theft detection). |
| A-06 | Forja de JWT | S | 🔴 Crítico | Baja | ✅ | Firma RS256 con clave privada RSA-4096. Clave nunca expuesta. Verificación via `JwtDecoder` de Spring Security. |
| A-07 | JWT con rol elevado en claims | E | 🔴 Crítico | Baja | ✅ | En operaciones críticas, rol re-validado desde DB. No se confía únicamente en claims del token. |
| A-08 | Replay de TOTP (bypass 2FA) | S | 🟠 Alto | Baja | ✅ | Ventana ±30s. Código marcado como usado tras primer uso. Intentos fallidos cuentan hacia lockout. Desactivar 2FA requiere código válido. |
| A-09 | Sesión activa tras logout | T | 🟠 Alto | Media | ✅ | Refresh token revocado en logout y logout-all. Access token expira en ≤15 min. |
| A-10 | Contraseña en texto claro en logs | I | 🔴 Crítico | Media | ✅ | Request bodies nunca logueados. Logback con filtros para campos: `password`, `token`, `secret`, `authorization`. |
| A-11 | Sin códigos de recuperación para 2FA | D | 🟠 Alto | Media | ⚠️ | Si un socio pierde el dispositivo TOTP sin backup, queda bloqueado. **Pendiente:** implementar backup codes o flujo de reset 2FA por email + confirmación Admin. |
| A-12 | Rate limiter en memoria — multi-instancia | D | 🟡 Medio | Baja | ⚠️ | Rate limiting actual usa `ConcurrentHashMap` en memoria. Con más de una instancia del backend, los contadores no se comparten. **Pendiente:** Redis-backed Bucket4j para producción escalable. |

**Cookie del refresh token (confirmado en código):**
```
HttpOnly=true  |  Secure=true (prod) / false (staging)
SameSite=Strict  |  Path=/api/v1/auth  |  MaxAge=configurable
```

---

### 2.2 Autorización y Control de Acceso

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| B-01 | IDOR: acceso al perfil de otro socio | I | 🔴 Crítico | Alta | ✅ | `socio_id` validado en cada endpoint contra el token JWT. DTOs distintos por rol (Socio no ve cédula/tipo_sangre de otros). |
| B-02 | Escalada de Jefe de Salida a otra salida | E | 🔴 Crítico | Media | ✅ | `es_jefe_salida` verificado contra la `salida_id` exacta del request. Nunca confiado desde el cliente. |
| B-03 | Socio modificando su propio nivel técnico | E | 🟠 Alto | Alta | ✅ | Solo Directivo/Admin/Secretaria pueden modificar `nivel_tecnico_id`. `@PreAuthorize` server-side. |
| B-04 | Secretaria asignando rol Admin | E | 🔴 Crítico | Baja | ✅ | Solo Admin puede asignar rol Admin. RBAC estricto. Auditado. |
| B-05 | Permisos de Jefe de Salida post-estado | E | 🟡 Medio | Media | ✅ | Estado de salida verificado antes de otorgar permisos de Jefe. |
| B-06 | Mass assignment (campos no permitidos) | T | 🟠 Alto | Alta | ✅ | DTOs estrictos en todos los endpoints. Entidades JPA nunca expuestas directamente. |
| B-07 | Socio ve rutas no aprobadas | I | 🟡 Medio | Media | ✅ | Filtro `WHERE aprobada = true` en consultas de Socio. Solo Directivos/Admin ven no aprobadas. |
| B-08 | Socio inhabilitado inscribiéndose | E | 🟠 Alto | Media | ✅ | Verificación de `estado_habilitacion` en cada inscripción. Respeta config `BLOQUEAR_INSCRIPCION_INHABILITADOS` (default: `true`). |
| B-09 | Modificación de `configuracion_sistema` para deshabilitar bloqueo | T | 🔴 Crítico | Baja | ✅ | Solo Admin puede modificar (`@PreAuthorize("hasRole('ADMIN')")`). `ConfiguracionSistemaService.actualizar()` auditado con `@Auditable` y snapshot antes/después via `auditService.registrar()`. |

---

### 2.3 Datos de Socios (PII)

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| C-01 | Over-fetching de PII | I | 🟠 Alto | Alta | ✅ | DTOs específicos por rol. Socio recibe datos propios sin cédula/tipo_sangre de otros. |
| C-02 | Exposición de contacto de emergencia | I | 🟠 Alto | Media | ✅ | Visible solo para el propio socio, Directivos, Admin y Secretaria. |
| C-03 | SQL injection | T | 🔴 Crítico | Media | ✅ | Solo queries JPA/Hibernate con parámetros nombrados. Nunca concatenación de strings de usuario en SQL. |
| C-04 | Stack traces / info DB en respuestas de error | I | 🟠 Alto | Alta | ✅ | Handler global de excepciones. En prod (`show-details: never`), solo mensaje genérico. |
| C-05 | Exportación masiva sin autorización | I | 🟠 Alto | Baja | ✅ | Paginación obligatoria. Rate limiting en búsquedas. Solo Admin puede exportar sin paginación. |
| C-06 | Cédula/correo duplicado para cuenta falsa | S | 🟡 Medio | Baja | ✅ | Constraint `UNIQUE` en `cedula` y `correo`. Registro solo por Secretaria/Admin. |
| C-07 | CSV injection en importación de actas/habilitación | T | 🟠 Alto | Media | ✅ | Validación estricta: rechazo de fórmulas (`=`, `+`, `-`, `@`, `\t`), bytes nulos, UTF-8 inválido, tamaño máx 500 KB, 1000 filas. Socios vitalicios protegidos de modificación masiva. |

---

### 2.4 Rutas y Validación de Nivel (Seguridad Física)

> ⚠️ Sección de mayor impacto potencial: un fallo aquí puede poner en riesgo la integridad física de los socios.

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| D-01 | Bypass del check de nivel en inscripción | E | 🔴 Crítico | Media | ✅ | Validación exclusivamente server-side. El frontend nunca decide la elegibilidad. |
| D-02 | Modificación no autorizada de `acceso_ruta_por_nivel` | T | 🔴 Crítico | Baja | ✅ | Solo Directivos/Admin. Cambios auditados con snapshot antes/después via `@Aspect`. |
| D-03 | Directivo bajando umbrales sin control adicional | T | 🔴 Crítico | Baja | ⚠️ | Auditado. **Pendiente:** evaluar confirmación doble (segundo Directivo o Admin) para bajadas de umbral. |
| D-04 | Aprobación falsa de riesgo (forjar aprobadores) | T | 🔴 Crítico | Baja | ✅ | `riesgo_aprobado_por_directivo` y `riesgo_aprobado_por_jefe` se llenan server-side desde el JWT, nunca del body del request. |
| D-05 | Ruta no aprobada usada en salida | T | 🟠 Alto | Baja | ✅ | Al crear salida: verificación `rutas.aprobada = true` a nivel de servicio. |
| D-06 | Modificación de dificultad de ruta en salida activa | T | 🔴 Crítico | Baja | ✅ | Verificación de impacto si la ruta está en salida PLANIFICADA o EN_CURSO. Auditado. |

---

### 2.5 Registro de Auditoría

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| E-01 | Borrado de registros de auditoría | T | 🔴 Crítico | Baja | ✅ | Usuario de app (`sadday_app`) sin permiso `DELETE` en tabla `auditoria`. Solo append via `JdbcClient` directo (bypasa JPA para garantizar append-only). |
| E-02 | Modificación de registros de auditoría | T | 🔴 Crítico | Baja | ✅ | Usuario de app sin permiso `UPDATE`. `created_at` inmutable. |
| E-03 | Repudiación de acciones administrativas | R | 🔴 Crítico | Media | ✅ | Toda acción registrada con `socio_id`, `ip_address`, `user_agent`, `timestamp`, `datos_anteriores`, `datos_nuevos`. Escritura asíncrona (`@Async`). |
| E-04 | DoS por overflow de auditoría | D | 🟡 Medio | Baja | ⚠️ | Índices en `created_at`. **Pendiente:** política formal de retención (archivo > 2 años). |
| E-05 | Sin auditoría en cambios de `configuracion_sistema` | R | 🔴 Crítico | Media | ✅ | `ConfiguracionSistemaService.actualizar()` registra snapshot JSON antes/después via `auditService.registrar()` y `@Auditable`. Endpoint PATCH `/admin/config/{clave}` restringido a ADMIN. |
| E-06 | Falta de auditoría en cambio de umbrales de riesgo | R | 🔴 Crítico | Baja | ✅ | `@Aspect` en `AccesoRutaPorNivelService` captura siempre antes/después. |

---

### 2.6 Infraestructura y Red

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| F-01 | Acceso directo a API sin Cloudflare | S | 🔴 Crítico | Media | ✅ | Firewall: solo IPs de Cloudflare en puertos 80/443. API en `127.0.0.1:8080`. Frontend en `127.0.0.1:3000`. |
| F-02 | PostgreSQL expuesto a internet | I | 🔴 Crítico | Baja | ✅ | Postgres solo en red interna Docker. Puerto 5432 no expuesto en prod (`ports: []`). |
| F-03 | Robo de secrets en variables de entorno | I | 🔴 Crítico | Media | ✅ | Docker secrets / vars de entorno en servidor. Nunca en código. `.env` en `.gitignore`. `ADMIN_INITIAL_PASSWORD` como GitHub Secret. |
| F-04 | Vulnerabilidades en dependencias | T | 🟠 Alto | Alta | ✅ | Snyk (backend + frontend) + OWASP Dependency Check + Dependabot en CI/CD. Ejecutado en cada push a main. |
| F-05 | Contenedor corriendo como root | E | 🟠 Alto | Media | ✅ | Backend: `USER sadday` (non-root) en Dockerfile. Frontend: `nginx:alpine` (root por defecto en nginx — ver F-09). |
| F-06 | Inyección de secrets en logs | I | 🟠 Alto | Media | ✅ | Logback con filtros para: `password`, `token`, `secret`, `authorization`. |
| F-07 | SSRF desde la API | T | 🟡 Medio | Baja | ✅ | URLs externas restringidas al endpoint S3 conocido. |
| F-08 | Inyección de headers HTTP maliciosos | T | 🟠 Alto | Media | ✅ | Nginx stripea `X-Forwarded-For` del cliente. Solo `CF-Connecting-IP` es confiado. |
| F-09 | Frontend nginx:alpine corriendo como root | E | 🟡 Medio | Baja | ⚠️ | **Nuevo hallazgo:** La imagen `nginx:alpine` necesita root para bind en puerto 80. **Pendiente:** migrar a puerto no privilegiado (8080) con usuario no-root, o usar `nginx:unprivileged`. |
| F-10 | MinIO en staging con credenciales por defecto | I | 🟠 Alto | Alta | ⚠️ | **Nuevo hallazgo:** `application-qa.yml` define `minioadmin/minioadmin` como credenciales por defecto. Si el puerto MinIO queda expuesto accidentalmente en la VM de staging, es trivialmente explotable. **Pendiente:** credenciales no-default en staging y verificar que puerto 9000/9001 no es accesible desde fuera. |

---

### 2.7 Emails y Tokens de Un Solo Uso

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| G-01 | Enumeración de emails en reset de password | I | 🟠 Alto | Alta | ✅ | Respuesta idéntica independientemente de si el email existe. |
| G-02 | Reutilización de token de reset | T | 🟠 Alto | Media | ✅ | Token marcado `used = true` en el primer uso. Al enviar nuevo token, el anterior queda invalidado. |
| G-03 | Token de reset expuesto en referrer | I | 🟡 Medio | Media | ✅ | HTTPS obligatorio. Expiración corta (1h). `Referrer-Policy: no-referrer` configurado. |
| G-04 | Email spoofing / phishing | S | 🟡 Medio | Media | ⚠️ | **Pendiente:** verificar que SPF, DKIM y DMARC estén configurados en el dominio del club una vez apuntado a AWS SES. |
| G-05 | Token de invitación de registro no expirado | T | 🟡 Medio | Baja | ✅ | Expiración de 72h. Nuevo envío invalida el anterior. |
| G-06 | Sin rate limiting en `/forgot-password` | D | 🟠 Alto | Media | ✅ | Rate limiting Bucket4j: 5 requests / 5 minutos por IP. |

---

### 2.8 PDFs y Archivos

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| H-01 | Acceso no autorizado a PDF por URL adivinada | I | 🟠 Alto | Baja | ✅ | Nombres de archivo = UUID aleatorio. Acceso solo via pre-signed URL con expiración de 15 min. |
| H-02 | Tampering del PDF en S3 | T | 🟡 Medio | Baja | ✅ | Hash SHA-256 almacenado en DB. S3 versionado habilitado. |
| H-03 | Inyección de contenido en PDF | T | 🟡 Medio | Media | ✅ | Renderizado con **Thymeleaf** (auto-escape activado por defecto). Templates solo en classpath, no user-supplied. |
| H-04 | Bucket S3 público accidentalmente | I | 🔴 Crítico | Baja | ✅ | Bucket privado. ACL público bloqueado. Solo pre-signed URLs. |
| H-05 | XXE en generación PDF (Flying Saucer / OpenPDF) | T | 🟠 Alto | Baja | ✅ | `PdfRenderService` parsea el XHTML con un `DocumentBuilder` hardened (external-general-entities, external-parameter-entities y load-external-dtd deshabilitados + EntityResolver vacío). Se pasa el DOM ya construido a `ITextRenderer.setDocument()`, omitiendo el parser interno de Flying Saucer. |

---

### 2.9 Pipeline CI/CD (GitHub Actions) — NUEVA SECCIÓN

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| I-01 | Compromiso de `LIGHTSAIL_SSH_KEY` en GitHub Secrets | S | 🔴 Crítico | Baja | ✅ | Secret almacenado en entorno `production` de GitHub (requiere aprobación manual para deploy). Acceso restringido a branch `main`. |
| I-02 | Supply chain: versión maliciosa de un GitHub Action | T | 🟠 Alto | Baja | ⚠️ | Los workflows usan `@v3/v4` floating tags. **Pendiente:** hacer pin de Actions por SHA de commit (`uses: actions/checkout@abc1234`) para inmutabilidad. Dependabot ya monitorea Actions. |
| I-03 | Imagen Docker comprometida entre push y deploy | T | 🔴 Crítico | Baja | ✅ | Imágenes firmadas con cosign keyless (OIDC). `cosign verify` corre en el runner de GHA antes del deploy; el servidor recibe la imagen por digest (`@sha256:…`), no por tag, eliminando posibilidad de swap. |
| I-04 | `GHCR_READ_TOKEN` filtrado → pull de imágenes privadas | I | 🟠 Alto | Baja | ✅ | Token con scope mínimo (`read:packages` únicamente). Si se filtra, el atacante puede analizar las imágenes pero no modificarlas. Imágenes escaneadas con Trivy antes del push. |
| I-05 | `ADMIN_INITIAL_PASSWORD` no rotada tras primer deploy | S | 🔴 Crítico | Alta | ✅ | `AdminBootstrap` inserta `password_must_change = true`. `AuthService.login()` y `refresh()` devuelven el flag; el frontend debe redirigir al cambio de contraseña. `changePassword()` limpia el flag. |
| I-06 | Imagen de staging con datos de prod filtrados | I | 🟠 Alto | Baja | ✅ | Staging usa la misma imagen que prod (sin datos reales), solo difieren las variables de entorno. La BD de staging es independiente. |
| I-07 | Entorno staging como pivot hacia producción | T | 🟡 Medio | Baja | ⚠️ | Staging en VM Proxmox separada. Sin acceso directo a prod. **Pendiente:** verificar que las llaves SSH de staging y prod son distintas (no compartir `LIGHTSAIL_SSH_KEY` para staging). |
| I-08 | SBOM expone inventario de dependencias vulnerables | I | 🟡 Medio | Baja | ✅ | SBOMs generados como artifacts en GitHub (acceso requiere auth). Adjuntados como cosign attestation al digest. No públicamente accesibles. |

---

### 2.10 Frontend SPA (React) — NUEVA SECCIÓN

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| J-01 | XSS por contenido de usuario renderizado en React | T | 🟠 Alto | Media | ✅ | React escapa HTML por defecto. No se usa `dangerouslySetInnerHTML`. Access token solo en memoria (Zustand), no en DOM. |
| J-02 | Clickjacking en la SPA | T | 🟡 Medio | Baja | ✅ | `X-Frame-Options: DENY` configurado en Spring Security. `Content-Security-Policy: frame-ancestors 'none'`. |
| J-03 | Tokens en `localStorage` o `sessionStorage` | I | 🔴 Crítico | Baja | ✅ | Access token solo en memoria JS. Refresh token en cookie `HttpOnly` (no accesible desde JS). |
| J-04 | CORS mal configurado permitiendo orígenes arbitrarios | T | 🔴 Crítico | Baja | ✅ | CORS restringido a `APP_URL` (single origin desde config). Sin wildcards. `allowCredentials: true` solo para ese origen. |
| J-05 | Open redirect en routing de la SPA | T | 🟡 Medio | Baja | ✅ | Redirecciones post-login usan rutas relativas internas. No se aceptan URLs absolutas de parámetros externos. |
| J-06 | CSP insuficiente para scripts inline | T | 🟠 Alto | Media | ✅ | CSP completa en `nginx.conf`: `script-src 'self'` (sin `unsafe-inline`; polyfill de Vite deshabilitado en `vite.config.ts`), `style-src 'self' 'unsafe-inline'` (requerido por Radix UI/Recharts/Sonner), `img-src data: blob:`, `connect-src 'self'`. Headers `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` añadidos también. |
| J-07 | Enumeración de rutas internas por observación de red | I | 🟡 Bajo | Alta | ✅ | SPA: todas las rutas sirven el mismo `index.html`. No hay endpoints que expongan la estructura interna. |

---

## 3. OWASP API Security Top 10 — Estado Actual

| OWASP API | Amenaza | Estado | Control Implementado |
|---|---|---|---|
| API1 - Broken Object Level Auth | Acceso a recursos de otro socio | ✅ | `socio_id` validado en cada endpoint contra JWT. DTOs por rol. |
| API2 - Broken Authentication | Brute force, token theft | ✅ | Lockout, Argon2id, tokens en memoria, refresh como hash con rotación. |
| API3 - Broken Object Property Level Auth | Over-fetching de campos | ✅ | DTOs estrictos por rol. Entidades JPA nunca expuestas. |
| API4 - Unrestricted Resource Consumption | DoS por llamadas masivas | ✅ | Rate limiting Bucket4j (por endpoint) + Cloudflare + paginación obligatoria. ⚠️ Rate limiter en memoria (no distribuido). |
| API5 - Broken Function Level Auth | Socio llamando endpoints admin | ✅ | `@PreAuthorize` en todos los endpoints sensibles. Protección a nivel de ruta para `/admin/**`. |
| API6 - Unrestricted Access to Business Flows | Inscripción masiva o bypass de nivel | ✅ | Validación de nivel server-side, capacidad máxima, estado de habilitación, estado de salida. |
| API7 - Server Side Request Forgery | SSRF via URLs de usuario | ✅ | Whitelist de dominios para URLs externas. Solo S3 endpoint conocido. |
| API8 - Security Misconfiguration | Headers inseguros, stack traces | ✅ | Headers de seguridad en Spring Security. Handler global de errores. Actuator restringido. ⚠️ CSP del frontend pendiente. |
| API9 - Improper Inventory Management | Endpoints no documentados | ✅ | OpenAPI/Swagger protegido en prod (solo Admin/Secretaria). Actuator: solo `/health` público. |
| API10 - Unsafe Consumption of APIs | Dependencias vulnerables | ✅ | Snyk (backend + frontend) + OWASP Dependency Check + Trivy (imágenes) en CI/CD. |

---

## 4. Registro de Riesgos (Risk Register)

| ID | Amenaza | Severidad | Probabilidad | Riesgo Neto | Estado |
|---|---|---|---|---|---|
| A-01 | Brute force login | 🔴 Crítico | Alta | **CRÍTICO** | ✅ Mitigado |
| A-04 | Robo access token XSS | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| A-06 | Forja de JWT | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| A-11 | Sin backup codes para 2FA | 🟠 Alto | Media | **ALTO** | ⚠️ Pendiente |
| A-12 | Rate limiter no distribuido | 🟡 Medio | Baja | **MEDIO** | ⚠️ Pendiente |
| B-01 | IDOR en perfiles | 🔴 Crítico | Alta | **CRÍTICO** | ✅ Mitigado |
| B-02 | Escalada Jefe de Salida | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| B-09 | Modificación de `configuracion_sistema` sin auditoría | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| C-03 | SQL Injection | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| D-01 | Bypass validación de nivel | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| D-02 | Modificación no autorizada `acceso_ruta_por_nivel` | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| D-03 | Directivo baja umbrales sin control doble | 🔴 Crítico | Baja | **ALTO** | ⚠️ Parcial |
| D-04 | Aprobación falsa de riesgo | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| E-01 | Borrado de auditoría | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| E-05 | Sin auditoría en `configuracion_sistema` | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| F-01 | Bypass Cloudflare | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| F-02 | Postgres expuesto | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| F-03 | Robo de secrets | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| F-10 | MinIO staging con credenciales default | 🟠 Alto | Alta | **ALTO** | ⚠️ Pendiente |
| H-04 | Bucket S3 público | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| H-05 | XXE en generación PDF | 🟠 Alto | Baja | **ALTO** | ✅ Mitigado |
| I-01 | Compromiso de SSH key prod en CI | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| I-02 | Supply chain via Actions sin SHA pin | 🟠 Alto | Baja | **ALTO** | ⚠️ Pendiente |
| I-03 | Imagen comprometida entre push y deploy | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| I-05 | `ADMIN_INITIAL_PASSWORD` no rotada | 🔴 Crítico | Alta | **CRÍTICO** | ✅ Mitigado |
| J-06 | CSP insuficiente en frontend | 🟠 Alto | Media | **ALTO** | ✅ Mitigado |

---

## 5. Controles de Seguridad

### 5.1 Headers HTTP de Seguridad

**Implementados en Spring Security (todos los ambientes):**
```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
Referrer-Policy: no-referrer
Strict-Transport-Security: max-age=31536000; includeSubDomains  (solo prod)
```

**Pendiente — agregar en Nginx para el frontend:**
```nginx
add_header Content-Security-Policy
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
   img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'"
  always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
```

### 5.2 Política de Contraseñas (implementada)
- Mínimo 10 caracteres
- Al menos 1 mayúscula, 1 minúscula, 1 número, 1 carácter especial
- Argon2id con parámetros OWASP 2026

**Pendiente:**
- No reutilizar las últimas 3 contraseñas (guardar hashes anteriores)
- `password_must_change = true` para admin inicial (`AdminBootstrap`)

### 5.3 Pipeline DevSecOps — Estado Actual

| Paso | Herramienta | Estado |
|---|---|---|
| SAST código fuente | SonarCloud + Semgrep | ✅ Implementado |
| Dependencias backend | Snyk + OWASP Dependency Check | ✅ Implementado |
| Dependencias frontend | Snyk (pnpm) | ✅ Implementado |
| Escaneo de imagen | Trivy (CRITICAL/HIGH bloquea) | ✅ Implementado |
| Firma de imagen | cosign keyless (OIDC) | ✅ Implementado |
| SBOM | CycloneDX via syft | ✅ Implementado |
| Verificación de firma en deploy | cosign verify en servidor | ⚠️ Pendiente |
| Pin de GitHub Actions por SHA | Dependabot + manual | ⚠️ Pendiente |
| DAST | OWASP ZAP contra staging | ⚠️ Pendiente |

### 5.4 Gestión de Secretos

| Secreto | Almacenamiento | Rotación |
|---|---|---|
| JWT private key RSA-4096 | Docker secret / filesystem del servidor | Anual |
| DB password (prod) | Variable de entorno servidor | Trimestral |
| TOTP encryption key AES-256 | Variable de entorno servidor | Anual |
| AWS S3 credentials | IAM Role (sin credenciales hardcoded) | N/A |
| SMTP credentials SES | Variable de entorno servidor | Semestral |
| `ADMIN_INITIAL_PASSWORD` | GitHub Secret (entorno `production`) | Primera vez — rotar tras primer login |
| `LIGHTSAIL_SSH_KEY` | GitHub Secret (entorno `production`) | Anual |
| `GHCR_READ_TOKEN` | GitHub Secret (ambos entornos) | Semestral |

### 5.5 Monitoreo y Alertas (pendiente de configurar en CloudWatch)

Alertar cuando:
- Más de 10 intentos fallidos de login en 1 minuto desde una IP
- Cambio en `acceso_ruta_por_nivel` (cualquier modificación)
- Cambio en `configuracion_sistema` (auditado via `ConfiguracionSistemaService` — ver E-05)
- Cambio de rol de cualquier usuario
- 403 repetidos desde una IP (sondeo de endpoints)
- Cualquier operación inusual en tablas críticas (via logs de PostgreSQL)
- Primera ejecución de `AdminBootstrap` en prod (log WARN ya emitido)

---

## 6. Pendientes Priorizados

| Prioridad | ID | Acción |
|---|---|---|
| ✅ P1 | I-05 | ~~Implementar `password_must_change = true` para admin inicial en `AdminBootstrap`~~ — **Hecho** (V38 migration, `AdminBootstrap`, `changePassword()`) |
| ✅ P1 | B-09 / E-05 | ~~Añadir `@Auditable` a cambios en `configuracion_sistema`~~ — **Hecho** (`ConfiguracionSistemaService` + endpoints `/admin/config`) |
| 🔴 P1 | F-10 | Cambiar credenciales MinIO en staging (no usar `minioadmin/minioadmin`) |
| ✅ P2 | I-03 | ~~Añadir `cosign verify` en el script SSH de deploy antes de `docker pull`~~ — **Hecho** (verificación en runner GHA + pull por digest) |
| ✅ P2 | H-05 | ~~Verificar y hardener Flying Saucer contra XXE~~ — **Hecho** (`PdfRenderService` + DocumentBuilder hardened) |
| ✅ P2 | J-06 | ~~Añadir CSP completa en Nginx para el frontend~~ — **Hecho** (`nginx.conf` + `vite.config.ts`) |
| 🟠 P2 | A-11 | Implementar backup codes o flujo de reset 2FA por email + aprobación Admin |
| 🟡 P3 | I-02 | Pin de GitHub Actions por SHA de commit en todos los workflows |
| 🟡 P3 | F-09 | Migrar frontend a `nginx:unprivileged` (usuario no-root) |
| 🟡 P3 | D-03 | Evaluar confirmación doble para bajada de umbrales `acceso_ruta_por_nivel` |
| 🟡 P3 | A-12 | Redis-backed Bucket4j para rate limiting distribuido (si se escala a más de una instancia) |
| 🟡 P3 | G-04 | Verificar SPF, DKIM y DMARC en dominio del club al configurar SES |

---

## 7. Diagramas Referenciados

| Diagrama | Archivo |
|---|---|
| Arquitectura y límites de confianza | `diagramas/01_arquitectura.md` |
| Flujos de autenticación (login, refresh, logout) | `diagramas/02_flujos_autenticacion.md` |
| Flujos de reset de contraseña y registro | `diagramas/03_flujo_reset_password.md` |
| Flujo de inscripción con validación de nivel | `diagramas/04_flujo_inscripcion.md` |
| DFD y clasificación de datos sensibles | `diagramas/05_flujo_datos_dfd.md` |
