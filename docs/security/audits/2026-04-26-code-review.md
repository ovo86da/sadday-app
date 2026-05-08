# Auditoría de seguridad y calidad de código — Sadday App

**Fecha:** 2026-04-26
**Alcance:** Backend (Spring Boot / Java 21), Frontend (React 19 / Vite / TypeScript), Docker, CI/CD
**Metodología:** Revisión estática de código, análisis de configuración, análisis de flujos de autenticación y autorización

---

## Resumen ejecutivo

No se encontraron vulnerabilidades CRÍTICAS explotables directamente en producción con una configuración correcta. La base de seguridad es sólida: JWT RS256, Argon2id con parámetros OWASP 2026, TOTP cifrado con AES-256-GCM, refresh tokens rotativos con detección de robo, CSP estricto, headers de seguridad, pipeline CI/CD con Trivy + cosign + SBOM. Se encontraron **9 issues ALTO**, **14 MEDIO** y **20 BAJO**.

**Estado de resolución de cada issue indicado en el campo `Estado`.**

---

## ALTO

### A1 — Validación de IP spoofeada: headers confiados sin verificar el peer [RESUELTO]

**Archivos:** `ClientIpExtractor.java:54-72`, `RateLimitFilter.java:74`, `SecurityProperties.java`
**Descripción:** `extractIp` leía `CF-Connecting-IP` y `X-Forwarded-For` siempre que tuvieran formato de IP válida, sin verificar que `getRemoteAddr()` viniera de un rango de proxy confiable. Si el backend quedaba expuesto directamente, un atacante podía:
- Falsear su IP en logs y auditoría.
- Eludir el rate limiting rotando IPs falsas.
- Enmascarar bruteforce de login.

**Solución aplicada (2026-04-26):**
- `ClientIpExtractor` convertido a `@Component` Spring con inyección de configuración.
- Nuevo campo `sadday.security.trusted-proxy-cidrs` en `SecurityProperties`.
- Los headers `CF-Connecting-IP` / `X-Forwarded-For` solo se leen si `getRemoteAddr()` pertenece a un CIDR de la allowlist.
- `application.yml` define el default `[127.0.0.1/32, ::1/128]`.
- `application-prod.yml` agrega todos los rangos IPv4/IPv6 publicados de Cloudflare.

---

### A2 — `TOTP_ENCRYPTION_KEY` default de 32 bytes cero propagado a local [PENDIENTE]

**Archivos:** `.env.example:26`, `docker-compose.yml:90`, `application-local.yml:28`
**Descripción:** El valor `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=` (32 bytes de ceros) es el default en el perfil `local`. Si por descuido se activa `local` en producción, todos los TOTP secrets son trivialmente descifrable si la BD se filtra.
**Recomendación:** Eliminar el default y agregar un guard de arranque que rechace claves de baja entropía.

---

### A3 — Contraseña inicial débil en QA [PENDIENTE]

**Archivo:** `application-qa.yml:34`
```yaml
initial-password: ${ADMIN_INITIAL_PASSWORD:Admin123!}
```
**Descripción:** Si QA queda expuesto o si la configuración se promueve a prod por error, `Admin123!` es trivial de adivinar.
**Recomendación:** Eliminar el default; obligar a setear la variable de entorno.

---

### A4 — `forgotPassword` filtra existencia de cuenta vía timing attack [RESUELTO]

**Archivos:** `PasswordResetService.java`, `PasswordResetMailSender.java` (nuevo)
**Descripción:** El envío de email ocurría sincrónicamente dentro del request. Cuando el correo existía, la respuesta tardaba más (SMTP); cuando no existía, salía inmediatamente. Un atacante midiendo tiempos podía enumerar correos registrados.

**Solución aplicada (2026-04-26):**
- Creado `PasswordResetMailSender` — `@Component` independiente con `@Async send()`.
  Separación en bean propio requerida: `@Async` en auto-invocación dentro del mismo bean es ignorado por Spring (no pasa por el proxy AOP).
- `PasswordResetService` inyecta `PasswordResetMailSender` y llama `mailSender.send()` al final de `initiate()`, tras todas las operaciones de BD.
- El envío SMTP ocurre en el thread pool de `@EnableAsync`; el HTTP response retorna inmediatamente.
- El `log.info` del envío se movió al `PasswordResetMailSender` para que refleje el momento real del envío, no el de la encola.

---

### A5 — PII en `EmailVerificationToken` guardada en claro 72 horas [PENDIENTE]

**Archivos:** `EmailVerificationService.java:113-124`, `EmailVerificationService.java:165-198`
**Descripción:** Los tokens de pre-registro guardan `cedula`, `correo`, `telefono`, `nombre`, `apellido` en claro en BD. El endpoint público `getTokenInfo` devuelve toda esa PII con solo conocer el token.
**Recomendación:** Cifrar campos PII con el mismo patrón AES-GCM de TOTP, o limitar `getTokenInfo` a devolver solo flags booleanos.

---

### A6 — CSRF en `/refresh` cuando `SameSite=Strict` falla [RESUELTO]

**Archivos:** `AuthController.java`, `CorsConfig.java`, `frontend/src/lib/api.ts`
**Descripción:** CSRF globalmente desactivado. `/auth/refresh` acepta el refresh token desde una cookie HttpOnly (no Bearer). Si `SameSite=Strict` no se aplica (browsers antiguos, downgrade), el endpoint era vulnerable a CSRF.

**Por qué solo `/refresh` y no `/logout`:** `/logout` requiere `@PreAuthorize("isAuthenticated()")`, que exige un Bearer JWT válido en el header `Authorization`. El browser no envía ese header automáticamente en un ataque CSRF (está en memoria JS, no en una cookie), por lo que `JwtAuthFilter` devuelve 401 antes de ejecutar la lógica. Solo `/refresh` era el endpoint verdaderamente expuesto.

**Solución aplicada (2026-04-26) — custom header CORS pattern:**
- `AuthController.java`: constantes `CSRF_HEADER_NAME = "X-Sadday-Client"` y `CSRF_HEADER_VALUE = "spa"`. El método `refresh()` verifica el header antes de procesar la cookie; devuelve 400 si está ausente o incorrecto.
- `CorsConfig.java`: `X-Sadday-Client` agregado a `allowedHeaders` para que el preflight CORS lo permita desde el frontend legítimo.
- `api.ts`: `"X-Sadday-Client": "spa"` añadido a los headers por defecto de la instancia Axios — se envía en todas las peticiones automáticamente.

**Por qué funciona:** un atacante que intente CSRF no puede setear headers personalizados sin pasar por un preflight OPTIONS. CORS ya bloquea ese preflight para orígenes no permitidos. El header custom es una segunda barrera independiente de `SameSite=Strict`.

---

### A7 — El rol del JWT no se revalida contra BD durante la vida del access token [ACEPTADO — DECISIÓN DE DISEÑO]

**Archivo:** `JwtAuthFilter.java:60-73`
**Descripción:** El filtro construye el `GrantedAuthority` directamente desde el claim `rol` del JWT sin re-consultar la BD. Un admin degradado a Socio puede seguir actuando como admin hasta 15 minutos después. La revocación de refresh tokens no invalida el access token activo.

**Decisión arquitectónica (2026-04-27):** Este comportamiento es el patrón estándar de **arquitectura stateless con JWT**. La ventaja central de JWT es precisamente eliminar la consulta a BD en cada request: Spring Security resuelve la autorización leyendo el claim `rol` del token firmado, sin round-trip a base de datos. Revalidar el rol en cada petición transformaría el sistema en una arquitectura de sesiones tradicional pero más lenta — se perdería la principal ventaja de escalar sin estado.

**Ventana de exposición acotada:** El access token expira en 15 minutos. En caso de cambio de rol sensible (ej. degradar un admin comprometido), el operador puede revocar todas las sesiones del socio desde el panel de administración (`POST /admin/usuarios-auth/{id}/cerrar-sesion`), lo que invalida los refresh tokens de inmediato. El access token activo sobrevive máximo 15 minutos — ventana aceptable para este tipo de aplicación.

**¿Por qué no hace falta validar el formato del rol extraído del JWT?** El token usa RS256. Si la firma es válida, los claims son auténticos — fueron emitidos por el propio backend con la clave privada. Un atacante no puede inyectar un rol arbitrario sin romper la firma. Si el claim `rol` estuviera malformado por un bug interno en `JwtService`, Spring Security simplemente no matchearía ningún `@PreAuthorize` y el acceso fallaría de forma segura (sin escalada de privilegios). La firma RS256 ya garantiza integridad de forma más fuerte que cualquier validación de formato.

---

### A8 — `actuator/health` público en todos los perfiles; detalles en QA [RESUELTO]

**Archivo:** `SecurityConfig.java:134-137`, `application-qa.yml:46`
**Descripción:** En todos los perfiles `actuator/health` es público. En QA `show-details: always` filtraba estado de BD, disco y conexiones si QA era accesible externamente.

**Solución aplicada (2026-04-27):** `show-details: always` cambiado a `show-details: never` en `application-qa.yml`. El endpoint ahora responde solo `{"status":"UP/DOWN"}` en todos los perfiles — sin datos de BD, disco ni conexiones. El endpoint público es correcto por diseño (lo usan load balancers y herramientas de monitoreo sin autenticación); el riesgo era exclusivamente el `show-details: always`.

---

### A9 — Race condition del refresher de axios entre tabs [RESUELTO]

**Archivo:** `frontend/src/lib/api.ts:89`
**Descripción:** `isRefreshing` y `failedQueue` son estado de módulo singleton. Si el mismo usuario abre dos tabs, ambas pueden intentar refrescar simultáneamente; si una completa primero, la segunda usa un refresh token ya rotado, activando la detección de robo en el backend y revocando **todas** las sesiones.

**Solución aplicada (2026-04-27) — localStorage lock + BroadcastChannel:**
- Nuevo módulo `frontend/src/lib/auth-broadcast.ts` con tres primitivas:
  - `acquireRefreshLock()` — lock en `localStorage` con TTL de 10 s (cubre caídas de tab).
  - `broadcastRefreshDone/Failed()` — emite el resultado al canal `sadday-auth`.
  - `waitForRefreshResult()` — escucha el canal con timeout de 12 s.
- `api.ts` (interceptor 401): antes de hacer `/auth/refresh`, intenta adquirir el lock. Si otra tab lo tiene, espera el broadcast y actualiza el store local sin tocar el backend; luego reintenta la petición original.
- `auth-initializer.tsx`: mismo patrón en la restauración de sesión al cargar la app.
- La cookie HttpOnly (refresh token) la actualiza el browser automáticamente para todas las tabs vía `Set-Cookie` — solo el `accessToken` en memoria necesita coordinarse.

---

## MEDIO

### M1 — Cookie de refresh con `Path=/api/v1/auth` [INFORMATIVO]

**Archivo:** `AuthController.java:264`
El path restringido es correcto por diseño. Verificar que `VITE_API_BASE_URL` concatene siempre `/v1/auth/refresh` correctamente al cambiar entornos.

---

### M2 — Plataforma detectada desde User-Agent controlable por el cliente [PENDIENTE]

**Archivo:** `AuthService.java:198-205`
La restricción de doble sesión por plataforma usa `PlatformDetector.detect(userAgent)`. Como el User-Agent es totalmente controlable, un atacante puede abrir N sesiones cambiando el UA. Es un control de UX, no de seguridad — documentarlo como tal.

---

### M3 — Rate limit solo por IP, no por username (password-spraying) [PENDIENTE]

**Archivo:** `RateLimitFilter.java:77-91`
Un atacante con múltiples IPs puede probar un password común contra muchas cuentas distintas: cada cuenta recibe un solo intento fallido (bajo el umbral de bloqueo) y la IP no acumula lo suficiente para el bucket.
**Recomendación:** Agregar rate limit adicional por username normalizado en `/auth/login`.

---

### M4 — Cédula y correo logueados en INFO (PII bajo LOPDP Ecuador) [PENDIENTE]

**Archivos:** `EmailVerificationService.java:127,153,197,342,361`, `SocioService.java:75`, `PasswordResetService.java:107`
**Recomendación:** Enmascarar PII en INFO/WARN (`co***@dominio.com`, `0**********4`); dejarla solo en DEBUG.

---

### M5 — Detección de robo de refresh token no notifica al usuario [PENDIENTE]

**Archivo:** `AuthService.java:238-245`
Al reutilizar un refresh token revocado, se borran todas las sesiones correctamente, pero el usuario no recibe email ni notificación explicando el posible compromiso de su cuenta.

---

### M6 — Validación de datos personales manual fuera de Bean Validation [PENDIENTE]

**Archivos:** `CompleteRegistroRequest.java`, `EmailVerificationService.java:368-401`
Si en el futuro se añade un nuevo flujo y no se llama `validarDatosPersonales`, se cuelan registros inválidos. Migrar a `@Valid` con grupos o DTO sealed.

---

### M7 — `actualizarMiPerfil` no valida formato de correo [PENDIENTE]

**Archivo:** `SocioService.java:180-187`
Verificar que `UpdateMiPerfilRequest` tenga `@Email` en el campo correo; si no, un usuario podría poner el correo de otra víctima y luego pedir reset password contra ese correo.

---

### M8 — QA sin TLS: `cookieSecure=false` y refresh token en claro [PENDIENTE]

**Archivo:** `application-qa.yml:26`
Si QA está en red corporativa o comparte dominio raíz con prod, basta interceptar tráfico LAN para robar la cookie de refresh.

---

### M9 — Templates PDF podrían usar `th:utext` con contenido de usuario [PENDIENTE]

**Archivo:** `PdfRenderService.java` + templates en `templates/pdf/`
Thymeleaf escapa por defecto. Revisar todos los templates que rendericen campos libres (`cronica`, `observaciones`, etc.) para confirmar que no usen `th:utext`.

---

### M10 — `S3StorageService.delete` sin verificación de ownership [PENDIENTE]

**Archivo:** `S3StorageService.java:53-62`
Acepta cualquier `objectKey`. Protección depende exclusivamente del controlador que lo llama. Si en el futuro un endpoint expone el `objectKey` en un parámetro y otro lo borra sin validar dueño, hay borrado arbitrario (IDOR).

---

### M11 — CORS confía en una sola URL sin validación del formato [PENDIENTE]

**Archivo:** `CorsConfig.java:29`
Si `appUrl` tiene trailing slash o protocolo inconsistente, CORS se rompe silenciosamente. Agregar validación al arranque.

---

### M12 — `cambiarRol` no serializa estado anterior/nuevo en auditoría [PENDIENTE]

**Archivo:** `SocioService.java:273-314`
La acción `CAMBIAR_ROL_SOCIO` se audita pero `datos_anteriores`/`datos_nuevos` quedan vacíos. Para forensia conviene capturar el rol antes y después del cambio.

---

### M13 — `actuator/info` filtra metadata en local y QA [PENDIENTE]

**Archivo:** `SecurityConfig.java:135-136`
En prod está correctamente deshabilitado. Asegurar que QA no sea accesible externamente con `show-details: always`.

---

### M14 — `DevDataInitializer` con contraseñas hardcoded en el classpath de prod [PENDIENTE]

**Archivo:** `DevDataInitializer.java:51-58`
Aunque `@Profile("local")` lo desactiva, el JAR de producción lo incluye. Si se arranca con `SPRING_PROFILES_ACTIVE=local` por error, se crean usuarios con contraseñas triviales.
**Recomendación:** Mover el initializer a un módulo Maven separado de dev.

---

## BAJO

| # | Archivo | Descripción | Estado |
|---|---------|-------------|--------|
| B1 | `frontend/src/types/socios.ts:44,210` | `CsvFilaError` declarada dos veces con campos distintos; la segunda sobrescribe la primera | PENDIENTE |
| B2 | `frontend/src/stores/auth-store.ts:26` | Ventana de ~1-2 s tras F5 donde `isAuthenticated=false` redirige a `/403` antes de que termine el refresh automático | PENDIENTE |
| B3 | `error-boundary.tsx:28` | `ErrorBoundary` no envía crashes a un sink externo (Sentry/similar) | PENDIENTE |
| B4 | `JwtConfig.java:76-83` | `readPem` no valida las cabeceras del PEM antes de decodificar; error críptico si el archivo está corrupto | PENDIENTE |
| B5 | `PasswordResetService.java`, `EmailVerificationService.java` | Emails en texto plano sin firma DKIM/SPF en código; configurar en SES | INFORMATIVO |
| B6 | `Socio.java`, `CompleteRegistroRequest.java` | Mezcla de nombres en español e inglés (`tipoSangre` vs `bloodType`, `nombre` vs `emergencyContactName`) | PENDIENTE |
| B7 | `RateLimitFilter.java:44` | Desactivado en `test`; no hay ningún test de integración que cubra el filtro | PENDIENTE |
| B8 | `Documento.java:51` | `checksumMd5` será null si se activa SSE-KMS en S3 (S3 no devuelve MD5 con KMS) | PENDIENTE |
| B9 | `DevDataInitializer.java` | Cédula de prueba `0000000001` no pasa validación de dígito verificador ecuatoriano | PENDIENTE |
| B10 | `application-prod.yml` | `MAX_LOGIN_ATTEMPTS` y `LOCKOUT_DURATION_HOURS` heredados del default; fijarlos explícitamente | PENDIENTE |
| B11 | `ChangePasswordRequest.java:17-19` | `@Size(min=12)` repetido en `confirmPassword`; la validación cruzada ya está en el servicio | PENDIENTE |
| B12 | `AuditService.java:99` | `detalle` se trunca a 1000 chars silenciosamente sin log de aviso | PENDIENTE |
| B13 | `EstadisticaService.java:585-658` | SQL dinámica con StringBuilder; valores protegidos con named params, pero documentar la convención explícitamente | PENDIENTE |
| B14 | `SecurityConfig.java:173` | `Argon2PasswordEncoder(... parallelism=1)`; con cores disponibles, `parallelism=2-4` da más resistencia sin coste perceptible | PENDIENTE |
| B15 | `frontend/.env.local` | Verificar con `git ls-files frontend/.env.local` que no esté trackeado en git | PENDIENTE |
| B16 | `JwtAuthFilter.java:58` | No sobreescribe auth si el contexto ya tiene una; documenta la dependencia de orden de filtros | PENDIENTE |
| B17 | `TotpService.java:100-104` | `catch (Exception e)` retorna `false` silenciosamente; si la clave de cifrado cambia sin remigrar, todos los TOTP fallan sin evidencia | PENDIENTE |
| B18 | `frontend/src/lib/api.ts` | Interceptor no maneja 429 con mensaje específico al usuario | PENDIENTE |
| B19 | `.gitignore` | No cubre `*.crt`, `*.cer`, `*.pfx` | PENDIENTE |
| B20 | `SocioController.java:269` / `SocioService.java:335` | Hard delete de socio no captura snapshot en `datos_anteriores` de auditoría | PENDIENTE |

---

## Fortalezas identificadas

- Argon2id con parámetros OWASP 2026.
- JWT RS256 con separación encoder/decoder y claves cargadas desde archivo.
- Refresh tokens hasheados (SHA-256) con rotación y detección de robo.
- TOTP cifrado AES-256-GCM con IV aleatorio y tag de 128 bits.
- CSP estricto `default-src 'none'; frame-ancestors 'none'` para API sin HTML.
- Headers HSTS, X-Frame-Options DENY, Referrer-Policy NO_REFERRER.
- `error.include-stacktrace: never` y `include-message: never`.
- `GlobalExceptionHandler` enmascara todos los errores genéricos.
- Hardening anti-XXE en `PdfRenderService`.
- Validación estricta de archivos `.md` (extensión, tamaño, MIME, UTF-8, bytes nulos).
- Auditoría asíncrona append-only (tabla sin UPDATE/DELETE).
- CSV import con validación robusta (UTF-8, MIME allowlist, BOM strip, max rows).
- Rate limiter con Caffeine (`maximumSize=10000` previene memory leak).
- Sin `dangerouslySetInnerHTML`, `eval`, `localStorage` para tokens, ni `document.write`.
- Pipeline CI con Trivy + cosign keyless + SBOM CycloneDX + Snyk + OWASP Dependency-Check.
- Despliegue por digest verificado con cosign.
- Spring Security en dos capas (URL + `@PreAuthorize`).
- IDOR mitigado con regex en path y separación `/me` vs `/{id}`.
- Validación de transición de estados en inscripciones.
- Logs no incluyen tokens ni passwords.

---

## Priorización de remediación

| Prioridad | Issue | Esfuerzo estimado |
|-----------|-------|-------------------|
| 1 | A2 — Eliminar defaults de clave TOTP | 15 min |
| 2 | A3 — Eliminar default de contraseña QA | 5 min |
| 3 | **A1 — Allowlist de proxies** | **Resuelto** |
| 4 | **A4 — `forgotPassword` a `@Async`** | **Resuelto** |
| 5 | A5 — Cifrar PII en `EmailVerificationToken` | 2-3 h |
| 6 | **A6 — CSRF token en `/refresh`** | **Resuelto** |
| 7 | **A7 — Revalidación de rol contra BD** | **Aceptado — decisión de diseño** |
| 8 | M3 — Rate limit por username | 1 h |
| 9 | M4 — Enmascarar PII en logs | 1-2 h |
| 10 | B1 — Duplicado `CsvFilaError` en frontend | 5 min |
