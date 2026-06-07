# Threat Model — Sadday App

> **Metodología:** STRIDE + OWASP Top 10 + OWASP API Security Top 10 + OWASP MASVS  
> **Última revisión:** 2026-06-07  
> **Revisión anterior:** 2026-04-17  
> **Cambios en esta revisión:** Módulo FR-021 (gestión documental, datos médicos, documentos legales, retiro de socio); dos tablas de auditoría; secciones nuevas Mobile (MASVS), MCP/API Keys; corrección de política de contraseñas (12 ≠ 10); resolución de A-11 (emergency reset 2FA); corrección B-07 (EstadoRuta enum); corrección 5.1 (CSP frontend ya implementado); corrección rate limit table (faltaba change-password).

---

## 1. Alcance y Activos

### Componentes del sistema

| Componente | Descripción | Estado |
|---|---|---|
| Cloudflare | WAF, CDN, DDoS mitigation | Sin cambios |
| Firewall VPS | UFW/iptables en AWS Lightsail | Sin cambios |
| Nginx (host) | Reverse proxy, TLS termination | Sin cambios |
| Frontend container | nginx:alpine sirviendo React SPA | Sin cambios |
| Spring Boot API | Lógica de negocio, autenticación, autorización | Nuevos módulos FR-021 |
| PostgreSQL | Persistencia de datos | 7 tablas nuevas (V11–V17) |
| MinIO (staging) / AWS S3 (prod) | Almacenamiento de PDFs | Sin cambios |
| AWS SES / SMTP | Notificaciones transaccionales | Sin cambios |
| GitHub Actions | CI/CD, build, scan, deploy | Sin cambios |
| GHCR (ghcr.io) | Registro de imágenes Docker | Sin cambios |
| Staging VM (Proxmox) | Entorno de validación pre-producción | Sin cambios |
| Mobile Flutter (iOS/Android) | App nativa — full feature parity con web | En dev/staging |
| MCP Server (Node.js/TS) | Asistente IA — herramientas de solo lectura | API keys read-only |

### Activos a proteger

| Activo | Sensibilidad | Impacto si comprometido |
|---|---|---|
| Credenciales (passwords, tokens) | Crítico | Acceso no autorizado total |
| TOTP secrets (cifrados AES-256) | Crítico | Bypass de 2FA |
| PII de socios (cédula, fecha nac, contacto emergencia) | Alto | Fraude, exposición de datos personales |
| **Datos de salud** (`socio_medical_info`) | **Crítico** | Categoría especial LOPDP Art. 23 — discriminación, impacto en seguros, privacidad médica |
| **Aceptaciones legales** (`legal_document_acceptances`, `activity_risk_acceptances`) | **Alto** | Pérdida de evidencia legal de consentimiento — impacto regulatorio |
| Tabla `audit_log` (módulo documental) | Crítico | Pérdida de trazabilidad de eventos sensibles |
| Tabla `auditoria` (seguridad general) | Crítico | Pérdida de trazabilidad, encubrimiento |
| PDFs de informes y actas | Medio | Exposición de información interna del club |
| Configuración del sistema (`configuracion_sistema`) | Alto | Modificación de reglas de negocio y seguridad |
| Tabla `acceso_ruta_por_nivel` | **Crítico** | Socios no calificados en rutas peligrosas → riesgo físico real |
| Claves JWT RSA | Crítico | Forja de tokens, suplantación de identidad |
| Secrets de CI/CD (GitHub Actions) | Crítico | Acceso total al servidor de producción |
| Imágenes Docker en GHCR | Alto | Análisis de código, vectores de ataque |
| API Keys del MCP | Medio | Acceso de solo lectura a datos del club |
| Refresh tokens mobile (Keychain/Keystore) | Alto | Persistencia de sesión sin acceso físico al servidor |

### Actores (Threat Actors)

| Actor | Capacidad | Motivación |
|---|---|---|
| Atacante externo no autenticado | Media | Acceso a datos, denegación de servicio |
| Socio autenticado malicioso | Baja-Media | Escalar privilegios, ver datos de otros socios |
| Directivo comprometido | Alta | Modificar umbrales de riesgo, acceder a PII |
| Admin comprometido | Muy Alta | Control total del sistema |
| Insider (secretaria/admin) | Alta | Acceso masivo a PII y datos médicos de todos los socios |
| Atacante de infraestructura | Alta | Comprometer servidor o base de datos |
| Atacante de supply chain | Alta | Comprometer imagen Docker o dependencia en CI/CD |
| Atacante de repositorio / CI | Media | Comprometer secrets de GitHub Actions → acceso al servidor |
| **Atacante físico (dispositivo mobile)** | Media | Robo de refresh token de Keychain/Keystore en device rooteado |

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
| A-06 | Forja de JWT | S | 🔴 Crítico | Baja | ✅ | Firma RS256 con clave privada RSA. Clave nunca expuesta. Verificación via `JwtDecoder` de Spring Security. |
| A-07 | JWT con rol elevado en claims | E | 🔴 Crítico | Baja | ✅ | En operaciones críticas, rol re-validado desde DB. No se confía únicamente en claims del token. Access token expira en 15 min; revocar todas las sesiones invalida inmediatamente los refresh tokens. |
| A-08 | Replay de TOTP (bypass 2FA) | S | 🟠 Alto | Baja | ✅ | Ventana ±30s. Código marcado como usado tras primer uso. Intentos fallidos cuentan hacia lockout. Desactivar 2FA requiere código válido. |
| A-09 | Sesión activa tras logout | T | 🟠 Alto | Media | ✅ | Refresh token revocado en logout y logout-all. Access token expira en ≤15 min. |
| A-10 | Contraseña en texto claro en logs | I | 🔴 Crítico | Media | ✅ | Request bodies nunca logueados. Logback con filtros para campos: `password`, `token`, `secret`, `authorization`. DevDataInitializer redirige solo a log.warn sin exponer contraseñas. |
| A-11 | Sin mecanismo de recuperación para 2FA | D | 🟠 Alto | Media | ✅ | **Resuelto:** `POST /admin/socios/{id}/emergency-reset` (solo ADMIN). Llama a `PasswordResetService.initiateEmergencyReset()` que envía email de reset con token de un solo uso, deshabilita TOTP y registra `EMERGENCY_RESET_2FA` en auditoría. |
| A-12 | Rate limiter en memoria — multi-instancia | D | 🟡 Medio | Baja | ⚠️ | Rate limiting actual usa `ConcurrentHashMap` vía Caffeine en memoria. Con más de una instancia del backend, los contadores no se comparten. **Pendiente:** Redis-backed Bucket4j para producción escalable. |
| A-13 | Forgery de `X-Sadday-Client` header | S | 🟡 Bajo | Media | ✅ | El header diferencia comportamiento web (`spa`) vs mobile (`mobile`): la cookie HttpOnly solo se emite si `X-Sadday-Client: spa`; en mobile el refresh token va en el body JSON. Un atacante mobile que envíe `spa` recibiría la cookie sin la capacidad de usarla (no tiene DOM). Un atacante web que envíe `mobile` recibiría el token en el body, que es su propio token. No hay escalada de privilegios. |

**Cookie del refresh token (web):**
```
HttpOnly=true  |  Secure=true (prod) / false (staging)
SameSite=Strict  |  Path=/api/v1/auth  |  MaxAge=configurable
```

**Protección CSRF adicional en `/refresh`:** se valida que `X-Sadday-Client` esté presente con valor `spa` o `mobile`. Un request CSRF desde otro origen no puede setear headers custom (bloqueado por CORS preflight).

---

### 2.2 Autorización y Control de Acceso

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| B-01 | IDOR: acceso al perfil de otro socio | I | 🔴 Crítico | Alta | ✅ | `socio_id` validado en cada endpoint contra el token JWT. DTOs distintos por rol. |
| B-02 | Escalada de Jefe de Salida a otra salida | E | 🔴 Crítico | Media | ✅ | `es_jefe_salida` verificado contra la `salida_id` exacta del request. Nunca confiado desde el cliente. |
| B-03 | Socio modificando su propio nivel técnico | E | 🟠 Alto | Alta | ✅ | Solo Directivo/Admin/Secretaria pueden modificar `nivel_tecnico_id`. `@PreAuthorize` server-side. |
| B-04 | Secretaria asignando rol Admin | E | 🔴 Crítico | Baja | ✅ | Solo Admin puede asignar rol Admin. RBAC estricto. Auditado. |
| B-05 | Permisos de Jefe de Salida post-estado | E | 🟡 Medio | Media | ✅ | Estado de salida verificado antes de otorgar permisos de Jefe. |
| B-06 | Mass assignment (campos no permitidos) | T | 🟠 Alto | Alta | ✅ | DTOs estrictos en todos los endpoints. Entidades JPA nunca expuestas directamente. |
| B-07 | Socio ve rutas no aprobadas | I | 🟡 Medio | Media | ✅ | Filtro `WHERE estado = 'APROBADA'` (`EstadoRuta` enum) en consultas de Socio. Solo Directivos/Admin/Secretaria ven rutas en otros estados (`PROPUESTA`, `EN_REVISION`, `RECHAZADA`). ~~Antes era un campo booleano `aprobada`~~ — migrado a enum en V8. |
| B-08 | Socio inhabilitado o con perfil incompleto inscribiéndose | E | 🟠 Alto | Media | ✅ | Verificación de `estado_habilitacion` Y `ProfileCompletionService.canEnrollActivities()` en cada inscripción. Requiere: 2 contactos emergencia, info médica, 3 documentos legales aceptados. Respeta config `BLOQUEAR_INSCRIPCION_INHABILITADOS`. |
| B-09 | Modificación de `configuracion_sistema` para deshabilitar bloqueo | T | 🔴 Crítico | Baja | ✅ | Solo Admin puede modificar. `ConfiguracionSistemaService.actualizar()` auditado con snapshot antes/después. |
| B-10 | IDOR en resumen médico de salida (Jefe de Salida) | I | 🟠 Alto | Media | ✅ | `GET /v1/salidas/{id}/participantes/medical-summary` valida en backend que `SecurityContext.socioId` coincida con `jefe_salida_id` de esa salida exacta (no es suficiente ser jefe de otra salida). Acceso auditado con `MEDICAL_INFO_VIEWED` en `audit_log`. |
| B-11 | Confirmación de retiro de socio forjada (sin texto exacto) | T | 🔴 Crítico | Baja | ✅ | El backend valida `confirmationText == "Si, deseo eliminar al socio {nombre} {apellido}"` (case-insensitive) independientemente del frontend. Rechaza con 400 si no coincide. |

---

### 2.3 Datos de Socios (PII y Datos Sensibles)

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| C-01 | Over-fetching de PII | I | 🟠 Alto | Alta | ✅ | DTOs específicos por rol. Socio recibe datos propios. Datos médicos en tabla separada, nunca incluidos en listados generales. |
| C-02 | Exposición de contactos de emergencia | I | 🟠 Alto | Media | ✅ | Tabla separada `socio_emergency_contacts`. Acceso solo para el propio socio, ADMIN y SECRETARIA. Nunca en listados. |
| C-03 | SQL injection | T | 🔴 Crítico | Media | ✅ | Solo queries JPA/Hibernate con parámetros nombrados. Nunca concatenación de strings de usuario en SQL. |
| C-04 | Stack traces / info DB en respuestas de error | I | 🟠 Alto | Alta | ✅ | Handler global de excepciones. En prod (`show-details: never`), solo mensaje genérico. |
| C-05 | Exportación masiva sin autorización | I | 🟠 Alto | Baja | ✅ | Paginación obligatoria. `SocioExportService` capeado en 2.000 filas via `PageRequest`. Rate limiting en búsquedas. Solo Admin puede exportar. |
| C-06 | Cédula/correo duplicado para cuenta falsa | S | 🟡 Medio | Baja | ✅ | Constraint `UNIQUE` en `cedula` y `correo`. Registro solo por Secretaria/Admin. |
| C-07 | CSV injection en importación | T | 🟠 Alto | Media | ✅ | Validación estricta: rechazo de fórmulas (`=`, `+`, `-`, `@`, `\t`), bytes nulos, UTF-8 inválido, tamaño máx 500 KB, 1000 filas. |
| C-08 | Datos médicos en tabla `socios` (legacy) | I | 🔴 Crítico | N/A | ✅ | **Resuelto por FR-021:** `tipo_sangre` migrado a `socio_medical_info.blood_type` (V13+V17), `emergency_contact_*` migrados a `socio_emergency_contacts` (V12+V16). La tabla `socios` ya no contiene datos médicos ni de contacto de emergencia. |
| C-09 | Datos médicos no eliminados al retirar socio | T | 🔴 Crítico | Baja | ✅ | `SocioDataRetentionService.retireSocio()` hace DELETE de `socio_emergency_contacts` y soft-delete de `socio_medical_info` como parte de la misma transacción del retiro. Auditado con `SENSITIVE_DATA_DELETED`. |
| C-10 | Datos médicos no eliminados si socio tiene deuda | T | 🟠 Alto | Baja | ✅ | Por diseño: datos médicos y contactos de emergencia se eliminan igualmente si hay deuda. Solo se conservan nombre, cédula, correo (si deuda), teléfono y registros financieros. |

---

### 2.4 Rutas y Validación de Nivel (Seguridad Física)

> ⚠️ Sección de mayor impacto potencial: un fallo aquí puede poner en riesgo la integridad física de los socios.

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| D-01 | Bypass del check de nivel en inscripción | E | 🔴 Crítico | Media | ✅ | Validación exclusivamente server-side. El frontend nunca decide la elegibilidad. |
| D-02 | Modificación no autorizada de `acceso_ruta_por_nivel` | T | 🔴 Crítico | Baja | ✅ | Solo Directivos/Admin. Cambios auditados con snapshot antes/después via `@Aspect`. |
| D-03 | Directivo bajando umbrales sin control adicional | T | 🔴 Crítico | Baja | ⚠️ | Auditado. **Pendiente:** evaluar confirmación doble (segundo Directivo o Admin) para bajadas de umbral. |
| D-04 | Aprobación falsa de riesgo (forjar aprobadores) | T | 🔴 Crítico | Baja | ✅ | `riesgo_aprobado_por_directivo` y `riesgo_aprobado_por_jefe` se llenan server-side desde el JWT, nunca del body del request. |
| D-05 | Ruta no aprobada usada en salida | T | 🟠 Alto | Baja | ✅ | Al crear salida: verificación `ruta.getEstado() != EstadoRuta.APROBADA` a nivel de servicio (`PlanificadorService`). |
| D-06 | Modificación de dificultad de ruta en salida activa | T | 🔴 Crítico | Baja | ✅ | Verificación de impacto si la ruta está en salida PLANIFICADA o EN_CURSO. Auditado. |

---

### 2.5 Registros de Auditoría

> El sistema mantiene **dos tablas de auditoría separadas** con propósitos distintos. Ambas son append-only.

| Tabla | Servicio | Propósito | Eventos |
|-------|----------|-----------|---------|
| `auditoria` | `AuditService` | Seguridad general, auth, admin | LOGIN_*, LOGOUT, DELETE_SOCIO, CHANGE_PASSWORD, RESET_PASSWORD, CAMBIAR_ROL_SOCIO, EMERGENCY_RESET_2FA, cambios en configuracion_sistema, etc. |
| `audit_log` | `DocumentAuditService` | Módulo documental (FR-021) | LEGAL_DOCUMENT_ACCEPTED, LEGAL_DOCUMENT_CREATED, LEGAL_DOCUMENT_ACTIVATED, MEDICAL_INFO_VIEWED, MEDICAL_INFO_UPDATED, EMERGENCY_CONTACT_UPDATED, SOCIO_RETIRED, SENSITIVE_DATA_DELETED, ACTIVITY_RISK_ACCEPTED, ACTIVITY_ENROLLMENT_BLOCKED |

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| E-01 | Borrado de registros de auditoría | T | 🔴 Crítico | Baja | ✅ | Usuario de app sin permiso `DELETE` en `auditoria` ni en `audit_log`. Ambas usan `JdbcClient` directo (append-only, bypasa JPA). |
| E-02 | Modificación de registros de auditoría | T | 🔴 Crítico | Baja | ✅ | Usuario de app sin permiso `UPDATE` en ninguna tabla de auditoría. `created_at` / `accepted_at` inmutables. |
| E-03 | Repudiación de acciones administrativas | R | 🔴 Crítico | Media | ✅ | Toda acción crítica registrada con `actor_id/username`, `ip_address`, `user_agent`, `timestamp`, datos antes/después. |
| E-04 | DoS por overflow de auditoría | D | 🟡 Medio | Baja | ⚠️ | Índices en `created_at`. **Pendiente:** política formal de retención para `auditoria` y `audit_log` (archivo > 2 años). |
| E-05 | Sin auditoría en cambios de `configuracion_sistema` | R | 🔴 Crítico | Media | ✅ | `ConfiguracionSistemaService.actualizar()` registra snapshot JSON antes/después. |
| E-06 | Falta de auditoría en cambio de umbrales de riesgo | R | 🔴 Crítico | Baja | ✅ | `@Aspect` en `AccesoRutaPorNivelService` captura siempre antes/después. |
| E-07 | Acceso a datos médicos sin trazabilidad | R | 🔴 Crítico | Media | ✅ | `DocumentAuditService` registra `MEDICAL_INFO_VIEWED` con `actorUserId`, IP, user-agent y lista de `participanteIds` consultados cada vez que el Jefe de Salida accede al resumen médico. |

---

### 2.6 Infraestructura y Red

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| F-01 | Acceso directo a API sin Cloudflare | S | 🔴 Crítico | Media | ✅ | Firewall: solo IPs de Cloudflare en puertos 80/443. API en `127.0.0.1:8080`. |
| F-02 | PostgreSQL expuesto a internet | I | 🔴 Crítico | Baja | ✅ | Postgres solo en red interna Docker. Puerto 5432 no expuesto en prod. |
| F-03 | Robo de secrets en variables de entorno | I | 🔴 Crítico | Media | ✅ | Docker secrets / vars de entorno en servidor. Nunca en código. `.env` en `.gitignore`. |
| F-04 | Vulnerabilidades en dependencias | T | 🟠 Alto | Alta | ✅ | Snyk + OWASP Dependency Check + Dependabot + Trivy en CI/CD. |
| F-05 | Contenedor corriendo como root | E | 🟠 Alto | Media | ✅ | Backend: `USER sadday` (non-root). Frontend: nginx:alpine en puerto 80 (ver F-09). |
| F-06 | Inyección de secrets en logs | I | 🟠 Alto | Media | ✅ | Logback con filtros para: `password`, `token`, `secret`, `authorization`. |
| F-07 | SSRF desde la API | T | 🟡 Medio | Baja | ✅ | URLs externas restringidas al endpoint S3 conocido. |
| F-08 | Inyección de headers HTTP maliciosos | T | 🟠 Alto | Media | ✅ | Nginx stripea `X-Forwarded-For` del cliente. Solo `CF-Connecting-IP` es confiado. `ClientIpExtractor` solo lee headers si `getRemoteAddr() == 127.0.0.1`. |
| F-09 | Frontend nginx:alpine corriendo como root | E | 🟡 Medio | Baja | ⚠️ | La imagen `nginx:alpine` necesita root para bind en puerto 80. **Pendiente:** migrar a puerto no privilegiado (8080) con usuario no-root, o usar `nginx:unprivileged`. |
| F-10 | MinIO en staging con credenciales por defecto | I | 🟠 Alto | Alta | ✅ | Credenciales reales configuradas en Infisical (`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`) para el environment staging. Docker Compose las inyecta sin fallback — si Infisical no está configurado, el contenedor no arranca. El fallback `minioadmin` en `application-staging.yml` solo aplica si el backend corre fuera de Docker, lo cual no ocurre en staging. |

---

### 2.7 Emails y Tokens de Un Solo Uso

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| G-01 | Enumeración de emails en reset de password | I | 🟠 Alto | Alta | ✅ | Respuesta idéntica independientemente de si el email existe. Envío de email via `@Async` (`PasswordResetMailSender`). |
| G-02 | Reutilización de token de reset | T | 🟠 Alto | Media | ✅ | Token marcado `used = true` en el primer uso. Nuevo token invalida el anterior. |
| G-03 | Token de reset expuesto en referrer | I | 🟡 Medio | Media | ✅ | HTTPS obligatorio. Expiración corta (15 min). `Referrer-Policy: no-referrer`. |
| G-04 | Email spoofing / phishing | S | 🟡 Medio | Media | ⚠️ | **Pendiente:** verificar SPF, DKIM y DMARC en el dominio del club al configurar AWS SES. |
| G-05 | Token de invitación de registro no expirado | T | 🟡 Medio | Baja | ✅ | Expiración 48h (individual) / 72h (CSV). Nuevo envío invalida el anterior. |
| G-06 | Rate limiting en endpoints de email | D | 🟠 Alto | Media | ✅ | `/forgot-password`: 5 req/5 min por IP. `/auth/reset-password`: 5 req/5 min por IP. Además limit por socio (3 req/15 min, silencioso). |
| G-07 | PII en `EmailVerificationToken` en texto claro | I | 🟠 Alto | Baja | ⚠️ | Los tokens de pre-registro almacenan cédula, correo, nombre, apellido en claro 72h. El endpoint público `getTokenInfo` devuelve PII si el atacante conoce el token. **Pendiente:** cifrar campos PII con AES-GCM o limitar respuesta a flags booleanos. |

---

### 2.8 PDFs y Archivos

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| H-01 | Acceso no autorizado a PDF por URL adivinada | I | 🟠 Alto | Baja | ✅ | Nombres de archivo = UUID aleatorio. Pre-signed URL con expiración de 15 min. |
| H-02 | Tampering del PDF en S3 | T | 🟡 Medio | Baja | ✅ | Hash SHA-256 almacenado en DB. S3 versionado habilitado. |
| H-03 | Inyección de contenido en PDF | T | 🟡 Medio | Media | ✅ | Renderizado con **Thymeleaf** (auto-escape). Templates solo en classpath, no user-supplied. |
| H-04 | Bucket S3 público accidentalmente | I | 🔴 Crítico | Baja | ✅ | Bucket privado. ACL público bloqueado. Solo pre-signed URLs. |
| H-05 | XXE en generación PDF (Flying Saucer / OpenPDF) | T | 🟠 Alto | Baja | ✅ | `PdfRenderService` con `DocumentBuilder` hardened (external entities deshabilitados + EntityResolver vacío). DOM pre-construido pasado a `ITextRenderer.setDocument()`. |

---

### 2.9 Pipeline CI/CD (GitHub Actions)

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| I-01 | Compromiso de `LIGHTSAIL_SSH_KEY` en GitHub Secrets | S | 🔴 Crítico | Baja | ✅ | Secret en entorno `production` (requiere aprobación manual). Acceso restringido a branch `main`. |
| I-02 | Supply chain: versión maliciosa de un GitHub Action | T | 🟠 Alto | Baja | ⚠️ | Los workflows usan floating tags (`@v3/v4`). **Pendiente:** pin por SHA de commit. Dependabot monitorea Actions. |
| I-03 | Imagen Docker comprometida entre push y deploy | T | 🔴 Crítico | Baja | ✅ | Imágenes firmadas con cosign keyless (OIDC). `cosign verify` corre antes del deploy; el servidor recibe imagen por digest (`@sha256:…`). |
| I-04 | `GHCR_READ_TOKEN` filtrado | I | 🟠 Alto | Baja | ✅ | Token con scope mínimo (`read:packages`). Imágenes escaneadas con Trivy antes del push. |
| I-05 | `ADMIN_INITIAL_PASSWORD` no rotada tras primer deploy | S | 🔴 Crítico | Alta | ✅ | `AdminBootstrap` inserta `password_must_change = true`. El frontend redirige al cambio de contraseña. `changePassword()` limpia el flag. |
| I-06 | Imagen de staging con datos de prod | I | 🟠 Alto | Baja | ✅ | Staging usa la misma imagen que prod con variables de entorno distintas. BD de staging es independiente. |
| I-07 | Staging como pivot hacia producción | T | 🟡 Medio | Baja | ⚠️ | Staging en VM Proxmox separada. **Pendiente:** verificar que las llaves SSH de staging y prod sean distintas. |
| I-08 | SBOM expone inventario de dependencias | I | 🟡 Medio | Baja | ✅ | SBOMs en artifacts de GitHub (acceso requiere auth). Adjuntados como cosign attestation. No públicos. |

---

### 2.10 Frontend SPA (React)

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| J-01 | XSS por contenido de usuario renderizado en React | T | 🟠 Alto | Media | ✅ | React escapa HTML por defecto. No se usa `dangerouslySetInnerHTML`. Access token solo en memoria. |
| J-02 | Clickjacking | T | 🟡 Medio | Baja | ✅ | `X-Frame-Options: DENY`. `Content-Security-Policy: frame-ancestors 'none'`. |
| J-03 | Tokens en `localStorage` o `sessionStorage` | I | 🔴 Crítico | Baja | ✅ | Access token solo en memoria JS. Refresh token en cookie `HttpOnly` (no accesible desde JS). |
| J-04 | CORS mal configurado | T | 🔴 Crítico | Baja | ✅ | CORS restringido a `APP_URL`. Sin wildcards. `allowCredentials: true` solo para ese origen. |
| J-05 | Open redirect en routing | T | 🟡 Medio | Baja | ✅ | Redirecciones post-login usan rutas relativas internas. |
| J-06 | CSP insuficiente para scripts inline | T | 🟠 Alto | Media | ✅ | CSP completa en `frontend/nginx.conf`: `script-src 'self' 'unsafe-eval'` (Recharts requiere `new Function()`), `style-src 'self' 'unsafe-inline'` (Radix UI/Sonner), `font-src https://fonts.gstatic.com`. Headers adicionales: `Permissions-Policy`, `Referrer-Policy: strict-origin-when-cross-origin`. |
| J-07 | Race condition de refresh entre múltiples tabs | T | 🟠 Alto | Media | ✅ | `localStorage` lock + `BroadcastChannel` (`auth-broadcast.ts`). La tab que pierde el lock espera el broadcast en lugar de llamar al backend de nuevo, evitando presentar el token ya rotado. |
| J-08 | Enumeración de rutas internas | I | 🟡 Bajo | Alta | ✅ | SPA: todas las rutas sirven el mismo `index.html`. |

---

### 2.11 Mobile (Flutter) — NUEVA SECCIÓN

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| K-01 | Robo de refresh token en device rooteado/jailbroken | I | 🟠 Alto | Baja | ⚠️ | Refresh token en Keychain (iOS) / EncryptedSharedPreferences (Android). En device rooteado, el Keystore puede ser accesible para apps con root. `flutter_jailbreak_detection ^1.10.0` está declarado en `pubspec.yaml` pero **nunca integrado** — ningún método se llama en el código. **Pendiente:** integrar en `main_prod.dart` o eliminar la dependencia. |
| K-02 | Sin certificate pinning en Dio | I | 🟡 Medio | Baja | ⚠️ | No hay pinning de certificado. Riesgo aceptado para este perfil de amenaza: el backend usa CA pública (Cloudflare). El pinning añadiría complejidad operativa sin beneficio significativo para un club de montaña. Revisar si el perfil de amenaza cambia. |
| K-03 | Deep links sin `app-site-association` | S | 🟡 Bajo | Baja | ⚠️ | No existe `apple-app-site-association` ni `assetlinks.json`. Mitigado por diseño del servidor: los tokens son de un solo uso y expiración corta. Un atacante que consiga el link sigue necesitando el token — no puede fabricar uno. |
| K-04 | Sesión no expira por inactividad | I | 🟡 Medio | Media | ✅ | `InactivityNotifier` con timeout fijo de 10 min. Cualquier `onTap`/`onPanDown` resetea el timer. Al expirar: `AuthLocked`, access token borrado de memoria, refresh token permanece en Keychain para desbloqueo biométrico. |
| K-05 | Captura de pantalla expone datos sensibles | I | 🟠 Alto | Media | ✅ | Android: `FLAG_SECURE` en `MainActivity.kt` (bloquea screenshots y difumina en app switcher). iOS: `_PrivacyOverlay` en `app.dart` cuando la app pasa a `inactive`/`paused`. |
| K-06 | Biométrica como única barrera tras lock | E | 🟡 Medio | Baja | ✅ | 3 fallos biométricos consecutivos → logout completo (refresh token eliminado). Si no hay biométrica disponible, se redirige a login con contraseña. |
| K-07 | Backup de datos de la app (Android) | I | 🟠 Alto | Baja | ✅ | `android:allowBackup="false"` en AndroidManifest. El refresh token del Keystore no se incluye en backups de Android. |
| K-08 | Logging en producción | I | 🟠 Alto | Baja | ✅ | Todos los métodos de log protegidos por `if (!AppConfig.isProd)`. En flavor `prod` no se escribe ningún log. No se loguean tokens ni PII en ningún flavor. |
| K-09 | Validación de contraseña inconsistente en reset mobile | I | 🟡 Bajo | Media | ⚠️ | `ResetPasswordScreen` muestra hint "mínimo 8 caracteres" pero el backend requiere 12 con complejidad. El usuario ve el error del servidor solo al enviar. **Pendiente:** corregir el hint y agregar validación client-side de 12 caracteres (que coincida con la del backend). |
| K-10 | Tráfico en claro en entorno dev | I | 🟡 Bajo | Baja | ✅ | `network_security_config.xml` solo permite cleartext a `10.0.2.2:8080` (emulador Android) en el flavor `dev`. Los flavors `staging` y `prod` no tienen excepciones — `cleartextTrafficPermitted="false"`. |

---

### 2.12 MCP Server / API Keys — NUEVA SECCIÓN

El servidor MCP (Node.js/TypeScript) usa API keys de solo lectura para autenticar al asistente IA. Las keys se almacenan como hash en BD y se autorizan vía `ApiKeyAuthFilter`.

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| L-01 | Brute force de API key | S | 🟠 Alto | Baja | ✅ | API keys son 32 bytes aleatorios (256 bits de entropía) — impracticable por fuerza bruta. Almacenadas como hash SHA-256 en BD. |
| L-02 | Filtración de API key en el servidor MCP | I | 🟠 Alto | Media | ⚠️ | La API key vive en la configuración del servidor MCP (variable de entorno o archivo de config). Si el servidor MCP se compromete, la key queda expuesta. **Mitigación existente:** scope estrictamente de solo lectura (`READ_ONLY`). **Pendiente:** documentar procedimiento de rotación periódica. |
| L-03 | API key usada para operaciones de escritura | E | 🔴 Crítico | Baja | ✅ | `ApiKeyAuthFilter` asigna authority `ROLE_MCP_READ`. Los endpoints de escritura requieren `ROLE_SOCIO`, `ROLE_DIRECTIVO`, `ROLE_SECRETARIA` o `ROLE_ADMIN` — nunca `ROLE_MCP_READ`. |
| L-04 | API key activa indefinidamente | T | 🟡 Medio | Media | ⚠️ | Las API keys no tienen expiración automática. **Pendiente:** implementar expiración configurable o recordatorio de rotación. El Admin puede revocarlas manualmente desde el panel. |
| L-05 | Sin rate limiting en endpoints MCP | D | 🟡 Medio | Baja | ⚠️ | Los endpoints autenticados con API key no están en `RateLimitFilter` (que solo protege endpoints de auth). **Pendiente:** evaluar rate limit por API key para prevenir abuso masivo de lectura. |

---

### 2.13 Módulo de Gestión Documental (FR-021) — NUEVA SECCIÓN

| ID | Amenaza | STRIDE | Severidad | Probabilidad | Estado | Mitigación |
|---|---|---|---|---|---|---|
| N-01 | Hash de aceptación calculado en cliente | T | 🔴 Crítico | Baja | ✅ | El frontend envía solo `{ documentId, documentVersion }`. El backend recalcula SHA-256 desde el contenido en BD. Ningún dato de trazabilidad (hash, IP, user-agent, timestamp) puede ser manipulado por el cliente. |
| N-02 | Modificación de una aceptación registrada | T | 🔴 Crítico | Baja | ✅ | `legal_document_acceptances` y `activity_risk_acceptances` son append-only. El usuario de la app no tiene `UPDATE` ni `DELETE` sobre estas tablas. No hay endpoint que modifique una aceptación existente. |
| N-03 | Datos médicos guardados sin consentimiento | T | 🟠 Alto | Baja | ✅ | `SocioMedicalInfoService.save()` valida que exista aceptación vigente de `MEDICAL_DATA_CONSENT` antes de persistir. Si no existe, lanza `InsufficientConsentException`. |
| N-04 | Acceso no autorizado a información médica completa | I | 🔴 Crítico | Media | ✅ | `GET /v1/admin/socios/{id}/medical-info` requiere `ROLE_ADMIN` o `ROLE_SECRETARIA`. Jefe de Salida solo accede al resumen de emergencia (3 campos) y solo de los inscritos en su salida. `@PreAuthorize` en controlador + validación de `jefe_salida_id` en servicio. |
| N-05 | Datos médicos en logs o respuestas generales | I | 🔴 Crítico | Baja | ✅ | Entidades médicas sin `@ToString` automático. Mappers de logging excluyen `socio_medical_info`. Endpoints de listado de socios nunca incluyen campos médicos. |
| N-06 | Documento legal con contenido malicioso (XSS en Markdown) | T | 🟠 Alto | Baja | ✅ | El contenido Markdown se renderiza en el frontend con una librería que aplica sanitización. Los documentos solo los crean ADMIN y SECRETARIA (actores de confianza). Si se renderizara HTML arbitrario, los tokens en memoria no serían accesibles (no están en el DOM). |
| N-07 | Activación de versión de documento sin revisión | T | 🟡 Medio | Baja | ✅ | Solo ADMIN puede activar versiones (no Secretaria). La Secretaria puede crear versiones borradores pero no publicarlas. El flujo exige una acción separada de activación. |
| N-08 | Endpoints de documentos públicos sin autenticación | I | 🟡 Bajo | Alta | ✅ | `GET /v1/legal-documents/active` y `GET /v1/legal-documents/*/active` son públicos (necesarios para el wizard de registro pre-login). Solo devuelven contenido Markdown de documentos activos — sin datos de aceptaciones, sin datos de socios. El contenido es información pública del club. |
| N-09 | Requisitos de perfil bypasseados en inscripción | E | 🔴 Crítico | Baja | ✅ | `InscripcionService` llama a `ProfileCompletionService.canEnrollActivities()` server-side en cada inscripción. El frontend no puede modificar el resultado — la validación es exclusivamente backend. |

---

## 3. OWASP API Security Top 10 — Estado Actual

| OWASP API | Amenaza | Estado | Control Implementado |
|---|---|---|---|
| API1 - Broken Object Level Auth | Acceso a recursos de otro socio | ✅ | `socio_id` validado en cada endpoint. DTOs por rol. Resumen médico valida `jefe_salida_id` exacto (B-10). |
| API2 - Broken Authentication | Brute force, token theft | ✅ | Lockout, Argon2id, tokens en memoria, refresh como hash con rotación. Emergency reset 2FA por Admin (A-11 resuelto). |
| API3 - Broken Object Property Level Auth | Over-fetching de campos | ✅ | DTOs estrictos por rol. Datos médicos en tabla separada, nunca en listados. |
| API4 - Unrestricted Resource Consumption | DoS por llamadas masivas | ✅ | Rate limiting Bucket4j (7 endpoints protegidos) + Cloudflare + paginación + cap de 2.000 filas en export. ⚠️ Rate limiter en memoria (no distribuido). |
| API5 - Broken Function Level Auth | Socio llamando endpoints admin | ✅ | `@PreAuthorize` en todos los endpoints sensibles. SecurityFilterChain protege `/admin/**`. `ROLE_MCP_READ` sin acceso a escritura. |
| API6 - Unrestricted Access to Business Flows | Inscripción sin requisitos | ✅ | Profile completion check server-side en inscripciones. Document de riesgo requerido por salida. |
| API7 - Server Side Request Forgery | SSRF via URLs de usuario | ✅ | Whitelist de dominios para URLs externas. Solo S3 endpoint conocido. |
| API8 - Security Misconfiguration | Headers inseguros, stack traces | ✅ | Headers de seguridad en Spring Security y Nginx. Handler global de errores. Actuator restringido. CSP completa en frontend. |
| API9 - Improper Inventory Management | Endpoints no documentados | ✅ | OpenAPI/Swagger protegido en prod. Actuator: solo `/health` público. |
| API10 - Unsafe Consumption of APIs | Dependencias vulnerables | ✅ | Snyk + OWASP Dependency Check + Trivy + Dependabot en CI/CD. |

---

## 4. Registro de Riesgos (Risk Register)

| ID | Amenaza | Severidad | Probabilidad | Riesgo Neto | Estado |
|---|---|---|---|---|---|
| A-01 | Brute force login | 🔴 Crítico | Alta | **CRÍTICO** | ✅ Mitigado |
| A-04 | Robo access token XSS | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| A-06 | Forja de JWT | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| A-11 | Sin recuperación 2FA | 🟠 Alto | Media | **ALTO** | ✅ Mitigado (emergency-reset) |
| A-12 | Rate limiter no distribuido | 🟡 Medio | Baja | **MEDIO** | ⚠️ Pendiente |
| B-01 | IDOR en perfiles | 🔴 Crítico | Alta | **CRÍTICO** | ✅ Mitigado |
| B-02 | Escalada Jefe de Salida | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| B-09 | Modificación `configuracion_sistema` | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| B-10 | IDOR resumen médico (Jefe Salida) | 🟠 Alto | Media | **ALTO** | ✅ Mitigado |
| B-11 | Confirmación retiro forjada | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| C-03 | SQL Injection | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| C-09 | Datos médicos no eliminados al retiro | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| D-01 | Bypass validación de nivel | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| D-02 | Modificación no autorizada `acceso_ruta_por_nivel` | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| D-03 | Directivo baja umbrales sin control doble | 🔴 Crítico | Baja | **ALTO** | ⚠️ Parcial |
| D-04 | Aprobación falsa de riesgo | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| E-01 | Borrado de auditoría | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| E-05 | Sin auditoría en `configuracion_sistema` | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| F-01 | Bypass Cloudflare | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| F-02 | Postgres expuesto | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| F-03 | Robo de secrets | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| F-10 | MinIO staging con credenciales default | 🟠 Alto | Alta | **ALTO** | ✅ Mitigado (Infisical) |
| G-07 | PII en EmailVerificationToken en claro | 🟠 Alto | Baja | **ALTO** | ⚠️ Pendiente |
| H-04 | Bucket S3 público | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| H-05 | XXE en generación PDF | 🟠 Alto | Baja | **ALTO** | ✅ Mitigado |
| I-01 | Compromiso de SSH key prod en CI | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| I-02 | Supply chain via Actions sin SHA pin | 🟠 Alto | Baja | **ALTO** | ⚠️ Pendiente |
| I-03 | Imagen comprometida entre push y deploy | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| I-05 | `ADMIN_INITIAL_PASSWORD` no rotada | 🔴 Crítico | Alta | **CRÍTICO** | ✅ Mitigado |
| J-06 | CSP insuficiente en frontend | 🟠 Alto | Media | **ALTO** | ✅ Mitigado |
| J-07 | Race condition refresh multi-tab | 🟠 Alto | Media | **ALTO** | ✅ Mitigado |
| K-01 | Jailbreak detection no integrado | 🟠 Alto | Baja | **MEDIO** | ⚠️ Pendiente |
| K-09 | Hint contraseña incorrecto en mobile | 🟡 Bajo | Media | **BAJO** | ⚠️ Pendiente |
| L-03 | API key con acceso de escritura | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| N-01 | Hash de aceptación calculado en cliente | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |
| N-03 | Datos médicos sin consentimiento | 🟠 Alto | Baja | **ALTO** | ✅ Mitigado |
| N-04 | Acceso no autorizado a info médica | 🔴 Crítico | Media | **CRÍTICO** | ✅ Mitigado |
| N-09 | Bypass de requisitos en inscripción | 🔴 Crítico | Baja | **ALTO** | ✅ Mitigado |

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

**Implementados en Nginx del contenedor Frontend:**
```nginx
Content-Security-Policy:
  "default-src 'self';
   script-src 'self' 'unsafe-eval';
   style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;
   style-src-elem 'self' 'unsafe-inline' https://fonts.googleapis.com;
   style-src-attr 'unsafe-inline';
   img-src 'self' data: blob:;
   font-src 'self' https://fonts.gstatic.com;
   connect-src 'self';
   worker-src 'none'; frame-src 'none'; object-src 'none';
   base-uri 'self'; form-action 'self'; frame-ancestors 'none'"

Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()
Referrer-Policy: strict-origin-when-cross-origin
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
```

Notas de implementación:
- `'unsafe-eval'` en `script-src` es requerido por Recharts (usa `new Function()` internamente).
- `'unsafe-inline'` en `style-src` es requerido por Radix UI, Sonner y Recharts para estilos dinámicos.
- `font-src https://fonts.gstatic.com` carga la fuente Inter.
- Los headers están duplicados explícitamente en el bloque de assets estáticos (Nginx anula herencia en bloques `location` hijos).

### 5.2 Política de Contraseñas

**Mínimo 12 caracteres** (validado con `@Size(min=12)` en todos los DTOs de password):

| Requisito | Implementación |
|-----------|---------------|
| Longitud mínima | 12 caracteres (`@Size(min=12)`) |
| Letra minúscula | `StrongPasswordValidator.hasLower` |
| Letra mayúscula | `StrongPasswordValidator.hasUpper` |
| Dígito | `StrongPasswordValidator.hasDigit` |
| Símbolo | `StrongPasswordValidator.hasSymbol` (cualquier char no-letra, no-dígito, no-espacio) |
| Hashing | Argon2id con parámetros OWASP 2026 |

Aplicado en: `CompleteRegistroRequest`, `ChangePasswordRequest`, `ResetPasswordRequest`.

> ⚠️ **Gap K-09:** La pantalla `ResetPasswordScreen` de la app Flutter muestra el hint "mínimo 8 caracteres" — incorrecto. Debe corregirse a 12 con complejidad.

**Pendiente:**
- No reutilizar las últimas N contraseñas (guardar hashes históricos).

### 5.3 Pipeline DevSecOps — Estado Actual

| Paso | Herramienta | Estado |
|---|---|---|
| SAST código fuente | SonarCloud + Semgrep | ✅ Implementado |
| Dependencias backend | Snyk + OWASP Dependency Check | ✅ Implementado |
| Dependencias frontend | Snyk (pnpm) | ✅ Implementado |
| Escaneo de imagen | Trivy (CRITICAL/HIGH bloquea) | ✅ Implementado |
| Firma de imagen | cosign keyless (OIDC) | ✅ Implementado |
| SBOM | CycloneDX via syft | ✅ Implementado |
| Verificación de firma en deploy | cosign verify en runner GHA antes del pull | ✅ Implementado |
| Pin de GitHub Actions por SHA | Dependabot + manual | ⚠️ Pendiente |
| DAST | OWASP ZAP contra staging | ⚠️ Pendiente |

### 5.4 Rate Limiting (RateLimitFilter — Bucket4j + Caffeine)

| Endpoint | Método | Límite | Ventana |
|----------|--------|--------|---------|
| `/auth/login` | POST | 10 req | 1 min |
| `/auth/forgot-password` | POST | 5 req | 5 min |
| `/auth/reset-password` | POST | 5 req | 5 min |
| `/auth/refresh` | POST | 60 req | 1 min |
| `/registro/complete` | POST | 10 req | 10 min |
| `/registro/token-info` | GET | 10 req | 10 min |
| **`/auth/change-password`** | **POST** | **5 req** | **10 min** |

Todos los buckets usan IP real del cliente (via `ClientIpExtractor`). Caffeine con `maximumSize = 10.000` entradas por tipo de endpoint (previene memory leak por rotación masiva de IPs). Desactivado en perfil `test`.

### 5.5 Gestión de Secretos

| Secreto | Almacenamiento | Rotación |
|---|---|---|
| JWT private key RSA | Docker secret / filesystem del servidor | Anual |
| DB password (prod) | Variable de entorno servidor | Trimestral |
| TOTP encryption key AES-256 | Variable de entorno servidor | Anual |
| AWS S3 credentials | IAM Role (sin credenciales hardcoded) | N/A |
| SMTP credentials SES | Variable de entorno servidor | Semestral |
| `ADMIN_INITIAL_PASSWORD` | GitHub Secret (entorno `production`) | Primera vez — rotar tras primer login |
| `LIGHTSAIL_SSH_KEY` | GitHub Secret (entorno `production`) | Anual |
| `GHCR_READ_TOKEN` | GitHub Secret (ambos entornos) | Semestral |
| API Keys MCP | Generadas en app, hash en BD | Bajo demanda (revocación manual) |

### 5.6 Monitoreo y Alertas (pendiente de configurar en CloudWatch)

Alertar cuando:
- Más de 10 intentos fallidos de login en 1 minuto desde una IP
- Cambio en `acceso_ruta_por_nivel` (cualquier modificación)
- Cambio en `configuracion_sistema`
- Cambio de rol de cualquier usuario
- `EMERGENCY_RESET_2FA` en `auditoria`
- 403 repetidos desde una IP (sondeo de endpoints)
- Accesos `MEDICAL_INFO_VIEWED` fuera del horario habitual
- `SOCIO_RETIRED` + `SENSITIVE_DATA_DELETED` en `audit_log`
- Primera ejecución de `AdminBootstrap` en prod

---

## 6. Pendientes Priorizados

| Prioridad | ID | Acción | Estado |
|---|---|---|---|
| ✅ | A-11 | Implementar recuperación de 2FA — `initiateEmergencyReset()` + `POST /admin/socios/{id}/emergency-reset` | **Hecho** |
| ✅ | B-09/E-05 | `@Auditable` en `configuracion_sistema` | **Hecho** |
| ✅ | I-03 | `cosign verify` antes del deploy | **Hecho** |
| ✅ | H-05 | Hardening XXE en `PdfRenderService` | **Hecho** |
| ✅ | J-06 | CSP completa en Nginx del frontend | **Hecho** |
| ✅ | J-07 | Race condition refresh multi-tab (BroadcastChannel lock) | **Hecho** |
| ✅ | I-05 | `password_must_change = true` para admin inicial | **Hecho** |
| ✅ | F-10 | Credenciales MinIO en staging gestionadas por Infisical | **Hecho** |
| 🔴 P1 | G-07 | Cifrar PII en `EmailVerificationToken` (cédula, correo, nombre en claro 72h) | Pendiente |
| 🟠 P2 | K-01 | Integrar `flutter_jailbreak_detection` en `main_prod.dart` o eliminar la dependencia | Pendiente |
| 🟠 P2 | D-03 | Confirmación doble para bajada de umbrales en `acceso_ruta_por_nivel` | Pendiente |
| 🟡 P3 | I-02 | Pin de GitHub Actions por SHA de commit en todos los workflows | Pendiente |
| 🟡 P3 | F-09 | Migrar frontend a `nginx:unprivileged` (usuario no-root) | Pendiente |
| 🟡 P3 | A-12 | Redis-backed Bucket4j para rate limiting distribuido (si se escala a más de 1 instancia) | Pendiente |
| 🟡 P3 | G-04 | Verificar SPF, DKIM y DMARC en dominio del club al configurar SES | Pendiente |
| 🟡 P3 | L-04 | Expiración automática o recordatorio de rotación para API Keys MCP | Pendiente |
| 🟡 P3 | L-05 | Rate limiting por API key en endpoints de lectura MCP | Pendiente |
| 🟡 P3 | K-09 | Corregir hint "mínimo 8 caracteres" → 12 en `ResetPasswordScreen` Flutter | Pendiente |

---

## 7. Diagramas Referenciados

| Diagrama | Archivo |
|---|---|
| Arquitectura y límites de confianza | `diagramas/01_arquitectura.md` |
| Flujos de autenticación (login, refresh, logout) | `diagramas/02_flujos_autenticacion.md` |
| Flujos de reset de contraseña y registro | `diagramas/03_flujo_reset_password.md` |
| Flujo de inscripción con validación de nivel | `diagramas/04_flujo_inscripcion.md` |
| DFD y clasificación de datos sensibles | `diagramas/05_flujo_datos_dfd.md` |
| Flujo de documentos PDF (actas e informes) | `diagramas/11_flujo_documentos_pdf.md` |
| **Registro multi-paso con documentos legales** | **`diagramas/15_flujo_registro_documental.md`** |
| **Aceptación de documentos y cadena de evidencia** | **`diagramas/16_flujo_aceptacion_documento_legal.md`** |
| **Inscripción con perfil completo y riesgo** | **`diagramas/17_flujo_inscripcion_con_requisitos.md`** |
| **Retiro de socio y eliminación de datos sensibles** | **`diagramas/18_flujo_retiro_socio.md`** |
