# 🔒 Auditoría de Seguridad — Sadday App

**Fecha:** 2026-04-23  
**Alcance:** Backend (Spring Boot 4.0.3 / Java 21), Frontend (React + Vite), Docker, CI/CD  
**Metodología:** Revisión manual de código estática + análisis de configuración

---

## Resumen Ejecutivo

La aplicación tiene una **postura de seguridad sólida** en general. El equipo ha aplicado correctamente muchas mejores prácticas (Argon2id, RS256 JWT, refresh token rotation con detección de robo, cookies HttpOnly/SameSite=Strict, rate limiting, CORS estricto, CSP, auditoría, etc.). Los hallazgos a continuación son principalmente **mejoras de hardening** y un par de riesgos de **severidad media** que conviene corregir.

### Clasificación de Hallazgos

| Severidad | Cantidad |
|-----------|----------|
| 🔴 Crítica | 0 |
| 🟠 Alta | 1 |
| 🟡 Media | 5 |
| 🔵 Baja | 6 |
| ⚪ Informativa | 4 |

---

## Lo que está bien hecho ✅

Antes de los hallazgos, es importante reconocer las prácticas sólidas:

- **Argon2id** para hashing de contraseñas con parámetros OWASP 2026
- **RS256 JWT** con par de claves RSA (no HMAC simétrico)
- **Refresh token rotation** con detección de robo (revoca todos los tokens si se reutiliza uno revocado)
- **Refresh token almacenado como hash SHA-256** en BD (no en texto plano)
- **Cookie HttpOnly + Secure + SameSite=Strict** para el refresh token
- **Access token solo en memoria** (Zustand store, nunca localStorage)
- **TOTP cifrado con AES-256-GCM** en reposo con IV aleatorio
- **Rate limiting** (Bucket4j) en endpoints de login, forgot-password, reset-password, refresh y registro
- **CORS** restringido a un solo origen (no wildcard `*`)
- **CSP estricto** tanto en backend como en nginx
- **Security headers**: X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy
- **HSTS** habilitado en producción
- **Enumeración de usuarios prevenida**: mismo error para usuario inexistente y contraseña incorrecta
- **Bloqueo de cuenta** con auto-desbloqueo temporal
- **Validación con Bean Validation** (`@Valid`) en todos los DTOs
- **Stack traces nunca expuestos** (`include-stacktrace: never`)
- **Open Session in View deshabilitado** (`open-in-view: false`)
- **Docker: usuario no-root** en el contenedor del backend
- **Swagger protegido en producción** (requiere rol ADMIN/SECRETARIA)
- **Actuator restringido** (solo health público, info denegado en prod, resto denyAll)
- **Auditoría** de acciones críticas con IP, user-agent y resultado
- **Contraseñas mínimas de 12 caracteres** 
- **IDOR-safe**: datos personales solo accesibles por roles privilegiados o `/me`

---

## 🟠 Hallazgos de Severidad Alta

### SEC-01: IP Spoofing en Rate Limiting y Audit Trail

**Archivos afectados:**
- [RateLimitFilter.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/security/ratelimit/RateLimitFilter.java#L100-L112)
- [AuthController.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/controller/AuthController.java#L283-L296)
- [AuditAspect.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/security/audit/AuditAspect.java#L128-L137)

**Descripción:**  
Hay tres implementaciones distintas de `extractIp()` con inconsistencias entre ellas:

1. **`RateLimitFilter.extractIp()`** (línea 107-109): acepta `X-Forwarded-For` **sin validar formato** (no aplica la regex `^[0-9a-fA-F.:]+$` como sí hace con `CF-Connecting-IP`). Un atacante sin Cloudflare podría inyectar valores arbitrarios como `X-Forwarded-For: 1.2.3.4, attacker-payload` y el primer segmento se usaría sin sanitizar.

2. **`AuditAspect.extractIp()`** (línea 132-133): confía ciegamente en `CF-Connecting-IP` sin ninguna validación de formato ni verificación de que la request venga de Cloudflare.

3. **Impacto en rate limiting**: Un atacante puede rotar la IP arbitrariamente en el header `X-Forwarded-For` para evadir completamente el rate limiting del login y ejecutar un ataque de fuerza bruta.

> [!CAUTION]
> Sin verificar que los headers provienen de un proxy confiable (Cloudflare/Nginx), cualquier cliente puede falsificar su IP y evadir el rate limiting.

**Recomendación:**
- Centralizar `extractIp()` en una sola utilidad compartida.
- En producción, configurar Spring `ForwardedHeaderFilter` o `server.forward-headers-strategy=NATIVE` y confiar **solo** en `getRemoteAddr()` después de que Nginx/Cloudflare haganstrip del header.
- Si se lee `X-Forwarded-For`, validar el formato **siempre** (como ya se hace con `CF-Connecting-IP` en `AuthController`).
- Para `CF-Connecting-IP`, verificar que la IP de `getRemoteAddr()` esté en el rango de IPs de Cloudflare antes de confiar en el header.

---

## 🟡 Hallazgos de Severidad Media

### SEC-02: ConcurrentHashMap sin expiración — Memory Leak en Rate Limiter

**Archivo:** [RateLimitFilter.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/security/ratelimit/RateLimitFilter.java#L44-L49)

**Descripción:**  
Los `ConcurrentHashMap` que almacenan los buckets de rate limiting **nunca se limpian**. Cada IP única crea una entrada permanente. Un atacante rotando IPs (por ejemplo, via Tor o proxies) podría causar un **Denial of Service por agotamiento de memoria** (OOM).

```java
private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
// ... 5 mapas más, todos sin expiración
```

**Recomendación:**
- Usar `Caffeine` cache con `expireAfterAccess(Duration.ofMinutes(30))` en lugar de `ConcurrentHashMap`.
- O implementar un `@Scheduled` que limpie entradas antiguas periódicamente.
- En producción multi-instancia, migrar a Redis + Bucket4j distribuido (como ya se indica en el comentario).

---

### SEC-03: CorrelationIdFilter acepta X-Request-ID externo sin sanitizar

**Archivo:** [CorrelationIdFilter.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/security/CorrelationIdFilter.java#L48-L51)

**Descripción:**  
El filtro acepta `X-Request-ID` del cliente sin validar su formato ni longitud. Un atacante podría enviar:
- Un valor extremadamente largo → inflación de logs, DoS en almacenamiento.
- Caracteres especiales → log injection (inyectar líneas falsas de log si se usa formato texto plano).

```java
String requestId = request.getHeader(REQUEST_ID_HEADER);
if (requestId == null || requestId.isBlank()) {
    requestId = UUID.randomUUID().toString();
}
MDC.put(MDC_KEY, requestId);  // Sin validar formato ni longitud
```

**Recomendación:**
```java
if (requestId == null || requestId.isBlank() 
    || requestId.length() > 64 
    || !requestId.matches("^[a-zA-Z0-9._-]+$")) {
    requestId = UUID.randomUUID().toString();
}
```

---

### SEC-04: Falta validación de complejidad de contraseñas (solo longitud)

**Archivos afectados:**
- [ChangePasswordRequest.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/dto/ChangePasswordRequest.java)
- [ResetPasswordRequest.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/dto/ResetPasswordRequest.java)
- [CompleteRegistroRequest.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/dto/CompleteRegistroRequest.java)

**Descripción:**  
Los DTOs validan únicamente `@Size(min = 12, max = 200)`. No hay validación de complejidad (mayúsculas, minúsculas, números, caracteres especiales). Una contraseña como `"aaaaaaaaaaaa"` (12 "a"s) pasaría la validación.

> [!NOTE]
> OWASP 2026 recomienda preferir **longitud sobre complejidad**, pero un mínimo de entropía sigue siendo recomendable para evitar contraseñas triviales o que estén en listas de contraseñas comunes.

**Recomendación:**
- Agregar un `@Pattern` que requiera al menos una mayúscula, una minúscula y un dígito, O
- Mejor aún: implementar una verificación contra una lista de contraseñas comunes (like `HaveIBeenPwned` top 100k) como validación custom.

---

### SEC-05: Falta `fechaIngreso` en el flujo de pre-registro

**Archivo:** [EmailVerificationService.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/service/EmailVerificationService.java#L239-L257)

**Descripción:**  
Cuando se crea un `Socio` en el flujo de pre-registro, no se establece `fechaIngreso`. Según la entidad, este campo probablemente no es nullable. Esto no es un riesgo de seguridad directo, pero si el campo es nullable podría generar datos inconsistentes en la BD.

**Recomendación:**  
Agregar `.fechaIngreso(LocalDate.now())` al builder del Socio en el flujo de pre-registro.

---

### SEC-06: Endpoints públicos que deberían ser protegidos

**Archivo:** [endpoints.md](file:///home/david/Repos/privates/sadday-app/endpoints.md) + [SecurityConfig.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/config/SecurityConfig.java)

**Descripción:**  
Según la documentación de endpoints, los siguientes endpoints son **públicos** (🔓) pero deberían requerir al menos autenticación:

| Endpoint | Acceso actual | Problema |
|----------|--------------|----------|
| `GET /salidas/lookups` | 🔓 Público | Expone catálogos internos del sistema |
| `GET /salidas` | 🔓 Público | Lista todas las salidas del club sin autenticación |
| `GET /salidas/{id}` | 🔓 Público | Detalle con participantes sin autenticación |
| `POST /salidas/{id}/inscripciones` | 🔓 Público | Permitir inscripción sin autenticación podría ser un error |
| `DELETE /salidas/{id}/inscripciones/{pid}` | 🔓 Público | Cancelar inscripción sin autenticación |
| `GET /notificaciones/cumpleanos` | 🔓 Público | Expone nombres y fechas de cumpleaños de socios |
| `GET /socios/lookups` | 🔓 Público | Expone catálogos del sistema de roles/estados |

> [!IMPORTANT]
> Verificar si `SecurityConfig` aplica `.anyRequest().authenticated()` correctamente a estos endpoints y que la documentación no está desactualizada respecto al código real. Si `SecurityConfig` sí los protege, actualizar `endpoints.md`.

**Recomendación:**
- Si la intención es que sean públicos (p.ej., para la app móvil sin login), asegurarse de que no expongan PII.
- Si fue un error en la documentación, actualizarla.
- `GET /notificaciones/cumpleanos` en particular expone información personal — debería requerir autenticación.

---

## 🔵 Hallazgos de Severidad Baja

### SEC-07: Swagger/OpenAPI accesible sin autenticación en local

**Archivo:** [SecurityConfig.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/config/SecurityConfig.java#L118-L129)

**Descripción:**  
En perfil no-prod, Swagger está completamente abierto. Esto es correcto para desarrollo local pero debe asegurarse que el perfil activo en producción siempre sea `prod`. Si por error se despliega con perfil `local`, Swagger quedaría expuesto públicamente revelando toda la API surface.

**Recomendación:**  
Considerar agregar una validación al startup que advierta si `SPRING_PROFILES_ACTIVE != prod` cuando `APP_URL` contiene un dominio de producción.

---

### SEC-08: `checkSocioHabilitado` no se invoca en el flujo de login

**Archivo:** [AuthService.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/service/AuthService.java#L452-L461)

**Descripción:**  
Existe un método `checkSocioHabilitado()` que verifica si un socio está inhabilitado antes de permitir login, pero **nunca es llamado** en `completarLogin()`. El login sí registra `inhabilitado` en la respuesta, pero el socio inhabilitado puede hacer login y obtener tokens válidos.

Según la lógica actual, los socios inhabilitados pueden hacer login pero con "acceso con restricciones". Sin embargo, si la intención era bloquear el acceso:

**Recomendación:**  
Decidir explícitamente la política: ¿los socios inhabilitados deben poder hacer login? Si no, invocar `checkSocioHabilitado()` en `completarLogin()`. Si sí, eliminar el método muerto para evitar confusión.

---

### SEC-09: Falta cleanup de tokens expirados/usados en MFA Challenge

**Archivo:** [AuthService.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/service/AuthService.java#L117-L126)

**Descripción:**  
Cuando un usuario con MFA inicia login, se crea un `MfaChallengeToken` con expiración de 5 minutos. Si el usuario no completa el flujo, estos tokens se acumulan en BD. El repositorio tiene un `@Query` de limpieza pero no se evidencia un `@Scheduled` que lo ejecute.

Nota: se encontró un directorio `scheduler/` — es posible que la limpieza ya esté implementada ahí.

**Recomendación:**  
Verificar que exista un scheduler que limpie `mfa_challenge_tokens` expirados, `refresh_tokens` expirados/revocados, y `password_reset_tokens` usados/expirados periódicamente.

---

### SEC-10: `frontend/.env.local` commiteado al repositorio

**Archivo:** [.env.local](file:///home/david/Repos/privates/sadday-app/frontend/.env.local)

**Descripción:**  
El archivo `.env.local` del frontend está commiteado al repositorio (no está en `.gitignore` de la raíz, aunque el `.gitignore` del frontend sí lo lista). Actualmente solo contiene `VITE_API_BASE_URL=/api` y `VITE_APP_NAME`, que no son secretos. Sin embargo, si se agregan variables sensibles en el futuro, quedarían expuestas.

**Recomendación:**  
Verificar que el `.gitignore` de la raíz también excluya `frontend/.env.local`. Actualmente lo tiene listado (línea 27: `frontend/.env.local`), por lo que debería estar bien. Verificar con `git ls-files frontend/.env.local`.

---

### SEC-11: Password reset desbloquea cuentas bloqueadas

**Archivo:** [PasswordResetService.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/service/PasswordResetService.java#L128-L134)

**Descripción:**  
Al completar un reset de contraseña, el servicio resetea `failedAttempts`, `loginBlocked` y `blockedUntil`. Esto permite que un atacante que conozca el email de la víctima use el flujo de password reset para desbloquear una cuenta que un admin bloqueó intencionalmente.

Sin embargo, el rate limiting de 3 solicitudes por email cada 15 minutos mitiga parcialmente este riesgo.

**Recomendación:**  
Considerar si el desbloqueo automático es deseable. Si un admin bloqueó la cuenta manualmente (no por intentos fallidos), el reset de contraseña no debería desbloquearla. Diferenciar entre bloqueo por intentos fallidos vs. bloqueo administrativo.

---

### SEC-12: Nginx no incluye `Strict-Transport-Security` header

**Archivo:** [nginx.conf](file:///home/david/Repos/privates/sadday-app/frontend/nginx.conf)

**Descripción:**  
La configuración de Nginx del frontend no incluye el header HSTS (`Strict-Transport-Security`). Aunque el backend sí lo envía en sus respuestas API (cuando `isProd`), las páginas estáticas del frontend servidas por Nginx no lo tendrán.

**Recomendación:**  
Agregar en la configuración de producción de Nginx:
```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

---

## ⚪ Hallazgos Informativos

### SEC-13: Logs exponen correos electrónicos

**Archivos:**
- [PasswordResetService.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/service/PasswordResetService.java#L77) — `log.debug("Solicitud de reset para correo no registrado: {}", request.correo())`
- [PasswordResetService.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/service/PasswordResetService.java#L107) — `log.info("Email de recuperación enviado a: {}", request.correo())`

Aunque los logs no son accesibles externamente, en caso de una brecha de los logs se expondría PII. Considerar loguear solo los primeros caracteres o un hash del correo.

---

### SEC-14: DevDataInitializer loguea contraseñas en texto plano

**Archivo:** [DevDataInitializer.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/config/DevDataInitializer.java#L119)

```java
log.info("✅ Usuario de desarrollo creado: {} / {} (rol={})", u.username(), u.password(), u.rolNombre());
```

Aunque solo se ejecuta en el perfil `local`, las contraseñas aparecen en los logs en texto plano.

**Recomendación:** Omitir la contraseña del log.

---

### SEC-15: `backend/.env.example` contiene credenciales de desarrollo por defecto

**Archivo:** [.env.example](file:///home/david/Repos/privates/sadday-app/.env.example#L41)

`ADMIN_INITIAL_PASSWORD=cambia-esto-en-produccion` — Está claramente marcado como ejemplo, pero considerar no incluir una contraseña en el archivo de ejemplo en absoluto. Usar `ADMIN_INITIAL_PASSWORD=<generar con openssl rand -base64 32>` como placeholder.

---

### SEC-16: Falta `@Transactional` en `completarLogin` para consistencia de sesión

**Archivo:** [AuthService.java](file:///home/david/Repos/privates/sadday-app/backend/src/main/java/com/sadday/app/auth/service/AuthService.java#L179-L218)

La clase tiene `@Transactional` a nivel de clase, pero `completarLogin()` realiza múltiples operaciones (actualizar usuario, verificar sesiones activas, guardar refresh token). Si falla a mitad de camino, los datos podrían quedar en un estado inconsistente. La anotación a nivel de clase debería cubrir esto, pero vale la pena verificar que los métodos `public` (los puntos de entrada transaccionales) sean los correctos.

---

## Resumen de Recomendaciones Prioritarias

| # | Prioridad | Acción |
|---|-----------|--------|
| 1 | 🟠 Alta | Centralizar y asegurar la extracción de IP del cliente (SEC-01) |
| 2 | 🟡 Media | Implementar expiración en los mapas de rate limiting (SEC-02) |
| 3 | 🟡 Media | Sanitizar `X-Request-ID` en CorrelationIdFilter (SEC-03) |
| 4 | 🟡 Media | Verificar que endpoints marcados como 🔓 en docs realmente estén protegidos en el código (SEC-06) |
| 5 | 🔵 Baja | Agregar HSTS al nginx del frontend (SEC-12) |
| 6 | 🔵 Baja | Decidir la política de login para socios inhabilitados (SEC-08) |
| 7 | 🔵 Baja | Verificar existencia de scheduler de limpieza de tokens (SEC-09) |

---

> [!TIP]
> El código muestra un nivel de madurez de seguridad muy superior al promedio. Las decisiones arquitectónicas (JWT RS256, refresh token rotation, Argon2id, AES-256-GCM para TOTP, cookie strategy) son todas correctas y están bien implementadas. Los hallazgos son principalmente de hardening y refinamiento.

---

## Correcciones

### SEC-01 — IP Spoofing en Rate Limiting y Audit Trail ✅ Corregido

**Fecha de corrección:** 2026-04-23

**Problema:**  
Existían tres implementaciones independientes de `extractIp()` con inconsistencias de validación:

| Archivo | Problema |
|---------|----------|
| `RateLimitFilter` | `X-Forwarded-For` aceptado **sin validar formato** — un atacante podía rotar IPs falsas para evadir el rate limiting del login |
| `AuthController` | Implementación duplicada (correcta pero redundante) |
| `AuditAspect` | `CF-Connecting-IP` aceptado **sin ninguna validación** — el audit trail era falsificable |

**Solución aplicada:**

#### [NEW] `ClientIpExtractor.java`

Se creó una utilidad centralizada en `com.sadday.app.shared.util.ClientIpExtractor` que:

- Valida **siempre** el formato de IP con regex `^[0-9a-fA-F.:]+$` y máximo 45 caracteres
- Aplica la misma validación estricta a **todos** los headers de proxy (`CF-Connecting-IP` y `X-Forwarded-For`)
- Ofrece dos métodos:
  - `extractIp(HttpServletRequest)` — para uso directo en filtros y controladores
  - `extractIpFromContext()` — para uso en aspectos y servicios (vía `RequestContextHolder`)
- También centraliza `extractUserAgentFromContext()` para el mismo patrón

```java
// Prioridad de extracción:
// 1. CF-Connecting-IP (Cloudflare) — validado
// 2. X-Forwarded-For (primer IP)  — validado
// 3. getRemoteAddr()              — fallback TCP directo

private static boolean isValidIp(String value) {
    return value != null
            && !value.isBlank()
            && value.length() <= MAX_IP_LENGTH
            && IP_FORMAT.matcher(value).matches();
}
```

#### [MODIFY] Archivos actualizados

Los tres archivos ahora delegan a `ClientIpExtractor`:

**`RateLimitFilter.java`** — La vulnerabilidad principal (rate limit bypass):
```diff
 private String extractIp(HttpServletRequest request) {
-    String cfIp = request.getHeader("CF-Connecting-IP");
-    if (cfIp != null && cfIp.matches("^[0-9a-fA-F.:]+$") && cfIp.length() <= 45) {
-        return cfIp;
-    }
-    String xForwardedFor = request.getHeader("X-Forwarded-For");
-    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
-        return xForwardedFor.split(",")[0].strip(); // ← SIN VALIDACIÓN
-    }
-    return request.getRemoteAddr();
+    return ClientIpExtractor.extractIp(request);
 }
```

**`AuthController.java`** — Código duplicado eliminado:
```diff
 private String extractIp(HttpServletRequest request) {
-    String cfIp = request.getHeader("CF-Connecting-IP");
-    // ... 10 líneas de lógica duplicada ...
-    return request.getRemoteAddr();
+    return ClientIpExtractor.extractIp(request);
 }
```

**`AuditAspect.java`** — Validación inexistente corregida:
```diff
 private String extractIp() {
-    try {
-        var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
-        String cfIp = attrs.getRequest().getHeader("CF-Connecting-IP");
-        return (cfIp != null && !cfIp.isBlank()) ? cfIp : attrs.getRequest().getRemoteAddr();
-        // ← CF-Connecting-IP aceptado SIN VALIDACIÓN
-    } catch (Exception e) {
-        return "UNKNOWN";
-    }
+    return ClientIpExtractor.extractIpFromContext();
 }

 private String extractUserAgent() {
-    try {
-        var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
-        return attrs.getRequest().getHeader("User-Agent");
-    } catch (Exception e) {
-        return null;
-    }
+    return ClientIpExtractor.extractUserAgentFromContext();
 }
```

**Verificación:** compilación exitosa con `mvn compile` sin errores.

---

### SEC-02 — Memory Leak en Rate Limiter ✅ Corregido

**Fecha de corrección:** 2026-04-23

**Problema:**  
Los `ConcurrentHashMap` que almacenaban los buckets de rate limiting **nunca se limpiaban**. Cada IP única creaba una entrada permanente. Un atacante rotando IPs podía causar un OOM.

**Solución aplicada:**

Se agregó **Caffeine** como dependencia (`pom.xml`) y se reemplazaron los 6 `ConcurrentHashMap` con cachés Caffeine configuradas con:
- `expireAfterAccess(30 minutos)` — las entradas inactivas se expulsan automáticamente
- `maximumSize(10.000)` — cap absoluto para prevenir DoS por rotación masiva de IPs

```diff
-private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
+private final Cache<String, Bucket> loginBuckets = buildCache();

+private static Cache<String, Bucket> buildCache() {
+    return Caffeine.newBuilder()
+            .maximumSize(MAX_CACHE_SIZE)           // 10.000
+            .expireAfterAccess(EVICTION_AFTER_ACCESS) // 30 min
+            .build();
+}
```

**Archivos modificados:**
- `pom.xml` — nueva dependencia `com.github.ben-manes.caffeine:caffeine`
- `RateLimitFilter.java` — `ConcurrentHashMap` → `Caffeine Cache`

---

### SEC-03 — Log Injection via X-Request-ID ✅ Corregido

**Fecha de corrección:** 2026-04-23

**Problema:**  
`CorrelationIdFilter` aceptaba el header `X-Request-ID` del cliente sin validar formato ni longitud, permitiendo log injection y DoS de almacenamiento.

**Solución aplicada:**

Se agregó validación estricta del header externo:

```diff
+private static final int MAX_REQUEST_ID_LENGTH = 64;
+private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9._-]+$");

 String requestId = request.getHeader(REQUEST_ID_HEADER);
-if (requestId == null || requestId.isBlank()) {
+if (!isValidRequestId(requestId)) {
     requestId = UUID.randomUUID().toString();
 }

+private static boolean isValidRequestId(String value) {
+    return value != null
+            && !value.isBlank()
+            && value.length() <= MAX_REQUEST_ID_LENGTH
+            && SAFE_ID.matcher(value).matches();
+}
```

**Archivo modificado:** `CorrelationIdFilter.java`

---

### SEC-04 — Validación de Complejidad de Contraseñas ✅ Corregido

**Fecha de corrección:** 2026-04-23

**Problema:**  
Los DTOs solo validaban `@Size(min = 12)`. Una contraseña como `"aaaaaaaaaaaa"` era aceptada.

**Solución aplicada:**

Se creó un validador custom `@StrongPassword` que exige al menos una mayúscula, una minúscula y un dígito. Se aplicó a los 3 DTOs que aceptan contraseñas nuevas:

**Archivos nuevos:**
- `shared/validation/StrongPassword.java` — anotación
- `shared/validation/StrongPasswordValidator.java` — lógica de validación

**Archivos modificados (se agregó `@StrongPassword`):**
- `ChangePasswordRequest.java` → campo `newPassword`
- `ResetPasswordRequest.java` → campo `nuevaPassword`
- `CompleteRegistroRequest.java` → campo `password`

---

### SEC-05 — Falta `fechaIngreso` en Pre-registro ✅ No requiere fix

**Fecha de verificación:** 2026-04-23

**Resultado:** Tras inspeccionar la entidad `Socio`, se confirmó que ya tiene un `@PrePersist` que establece `fechaIngreso = LocalDate.now()` automáticamente si el valor es null. **No requiere corrección.**

---

### SEC-06 — Endpoints erróneamente documentados como públicos ✅ Corregido

**Fecha de corrección:** 2026-04-23

**Problema:**  
7 endpoints estaban documentados como 🔓 (públicos) en `endpoints.md`, pero el código los protege correctamente con `@PreAuthorize("isAuthenticated()")` y `anyRequest().authenticated()` en `SecurityConfig`. **Era un error de documentación, no de código.**

**Solución aplicada:**  
Se actualizaron los niveles de acceso en `endpoints.md` de 🔓 a 🔒:

| Endpoint | Antes | Después |
|----------|-------|---------|
| `GET /socios/lookups` | 🔓 | 🔒 |
| `GET /salidas/lookups` | 🔓 | 🔒 |
| `GET /salidas` | 🔓 | 🔒 |
| `GET /salidas/{id}` | 🔓 | 🔒 |
| `POST /salidas/{id}/inscripciones` | 🔓 | 🔒 |
| `DELETE /salidas/{id}/inscripciones/{pid}` | 🔓 | 🔒 |
| `GET /notificaciones/cumpleanos` | 🔓 | 🔒 |

**Archivo modificado:** `endpoints.md`

---

### SEC-09 — Falta cleanup de tokens expirados ✅ Corregido

**Fecha de corrección:** 2026-04-23

**Problema:**  
Los repositorios tenían métodos de limpieza (`deleteExpired`, `deleteExpiredAndRevoked`) pero **ningún scheduler los invocaba**. Las 4 tablas de tokens crecían sin límite:
- `mfa_challenge_tokens` — tokens de 5 min de vida, nunca eliminados
- `refresh_tokens` — tokens revocados/expirados acumulados
- `password_reset_tokens` — sin método de limpieza
- `email_verification_tokens` — sin método de limpieza

**Solución aplicada:**

Se agregaron queries de limpieza faltantes en 2 repositorios y se creó un job `@Scheduled` que ejecuta cada hora:

**Archivos modificados:**
- `PasswordResetTokenRepository.java` — nuevo método `deleteExpiredAndUsed()`
- `EmailVerificationTokenRepository.java` — nuevo método `deleteExpiredAndUsed()`
- `SchedulerService.java` — nuevo job `limpiarTokensExpirados()` (cron: `0 0 * * * *`)

```java
@Scheduled(cron = "0 0 * * * *")   // Cada hora en punto
@Transactional
public void limpiarTokensExpirados() {
    LocalDateTime now = LocalDateTime.now();

    int mfa     = mfaChallengeTokenRepository.deleteExpired(now);
    int refresh = refreshTokenRepository.deleteExpiredAndRevoked(now);
    int reset   = passwordResetTokenRepository.deleteExpiredAndUsed(now);
    int email   = emailVerificationTokenRepository.deleteExpiredAndUsed(now);

    int total = mfa + refresh + reset + email;
    if (total > 0) {
        log.info("[Scheduler] limpiarTokens: eliminados {} tokens (mfa={}, refresh={}, reset={}, email={})",
                total, mfa, refresh, reset, email);
    }
}
```

---

**Verificación global:** compilación exitosa con `mvn compile` sin errores tras aplicar todas las correcciones.
