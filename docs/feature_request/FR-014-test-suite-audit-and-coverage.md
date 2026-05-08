# FR-014: Auditoría de Test Suite y Cobertura de Código

**Fecha:** 2026-05-07  
**Estado:** En progreso  
**Módulo:** Backend — Testing & QA  
**Prioridad:** Alta

---

## 1. Resumen Ejecutivo

Se realizó una auditoría completa de la suite de tests del backend Sadday App, incluyendo:
- Análisis cualitativo de los 197 tests existentes
- Generación del primer reporte JaCoCo de cobertura de código
- Identificación y corrección de tests rotos por campos faltantes (`estado_acceso_id`)
- Mapa de cobertura por módulo con recomendaciones priorizadas

### Números Clave (JaCoCo — Mayo 2026)

| Métrica | Valor |
|---------|-------|
| **Instruction coverage** | 18,036 / 30,888 → **58.4%** |
| **Branch coverage** | 1,563 / 2,039 → **76.7%** |
| **Line coverage** | 3,586 / 6,129 → **58.5%** |
| **Method coverage** | 1,539 / 2,179 → **70.6%** |
| **Class coverage** | 607 / 1,151 → **52.7%** |

---

## 2. Tests Corregidos en Esta Auditoría

### 2.1 SQL Test Data — `estado_acceso_id` Faltante

La tabla `socios` tiene una columna `estado_acceso_id NOT NULL` (FK a `estado_acceso`) desde la migración V1. Los archivos SQL de datos de prueba nunca incluyeron esta columna, causando que **todos los tests de integración fallaran** con `ScriptStatementFailed`.

**Archivos corregidos:**
- `src/test/resources/sql/socios-test-data.sql`
- `src/test/resources/sql/auth-test-data.sql`
- `src/test/resources/sql/notificaciones-test-data.sql`

**Cambio:** Se añadió `estado_acceso_id` con subquery `(SELECT id FROM estado_acceso WHERE codigo = 'ACTIVE')` a cada INSERT.

### 2.2 SocioServiceTest — NPE en `estadoAcceso`

`SocioService.toResponse()` accede a `socio.getEstadoAcceso().getId()` y `.getCodigo()`. El mock helper `mockSocio()` nunca incluía `estadoAcceso`, causando `NullPointerException`.

**Cambio:** Se añadió `EstadoAcceso` al `setUp()` y al builder de `mockSocio()`.

### 2.3 AuthServiceTest — Mocks Faltantes

`AuthService` adquirió 6 nuevas dependencias que no tenían `@Mock` correspondientes:

| Dependencia | Motivo |
|------------|--------|
| `SecurityEventService` | Registro de eventos de seguridad |
| `GeoIpService` | Detección de país por IP |
| `SecurityAlertMailSender` | Alertas de nuevo dispositivo/país |
| `SocioRepository` | Reset de emergencia 2FA |
| `PasswordResetService` | Flujo de reset de contraseña |
| `CountryChallengeTokenRepository` | Verificación de país nuevo |

Además, `mockSocioView()` no incluía `getEstadoAcceso()` → `completarLogin()` asumía estado no-ACTIVE → `ACCESO_SISTEMA_BLOQUEADO`.

### 2.4 AbstractIntegrationTest — SecurityEventService con REQUIRES_NEW

`SecurityEventService.record()` usa `Propagation.REQUIRES_NEW`, abriendo una transacción independiente que **no puede ver los datos insertados por `@Sql`** en la transacción del test. Esto causaba `FK violation` en `security_events.socio_id`.

**Solución:** Se añadió `@MockitoBean SecurityEventService` a `AbstractIntegrationTest`, con documentación del motivo.

---

## 3. Cobertura JaCoCo por Servicio/Clase

### Servicios de Negocio

| Clase | Instrucciones | Cobertura | Notas |
|-------|--------------|-----------|-------|
| `AdminService` | 804/808 | **100%** | ✅ Excelente |
| `PlanificadorService` | 438/438 | **100%** | ✅ Excelente |
| `CsvHabilitacionService` | 838/848 | **99%** | ✅ Excelente |
| `CsvSocioImportService` | 766/776 | **99%** | ✅ Excelente |
| `SecurityAlertMailSender` | 279/283 | **99%** | ✅ Excelente |
| `PdfInformeService` | 554/564 | **98%** | ✅ Excelente |
| `PasswordResetService` | 237/241 | **98%** | ✅ Excelente |
| `PdfActaService` | 506/519 | **97%** | ✅ Excelente |
| `ContactoService` | 390/416 | **94%** | ✅ Muy bueno |
| `RutaDocumentoService` | 195/209 | **93%** | ✅ Muy bueno |
| `EmailVerificationService` | 763/921 | **83%** | ✅ Bueno |
| `ActaMdParser` | 703/843 | **83%** | ✅ Bueno |
| `EstadisticaService` | 1560/1977 | **79%** | ✅ Bueno |
| `AuthService` | 954/1996 | **48%** | ⚠️ Requiere mejora |
| `SalidaService` | 1120/2423 | **46%** | ⚠️ Requiere mejora |
| `SocioService` | 553/1287 | **43%** | ⚠️ Requiere mejora |
| `ActaService` | 367/913 | **40%** | ⚠️ Requiere mejora |
| `RutaService` | 495/1378 | **36%** | ⚠️ Requiere mejora |
| `InformeService` | 448/1366 | **33%** | ⚠️ Requiere mejora |
| `MountainService` | 88/696 | **13%** | 🔴 Crítico |
| `TotpService` | 19/352 | **5%** | 🔴 Crítico |

### Clases de Seguridad

| Clase | Instrucciones | Cobertura | Notas |
|-------|--------------|-----------|-------|
| `RateLimitFilter` | 185/185 | **100%** | ✅ |
| `ApiKeyAuthFilter` | 179/194 | **92%** | ✅ |
| `SecurityEventService` | 304/384 | **79%** | ✅ (mockeado en ITs) |
| `CorrelationIdFilter` | 14/49 | **29%** | ⚠️ |
| `AuditService` | 27/103 | **26%** | ⚠️ |
| `SecurityConfig` | 62/291 | **21%** | ⚠️ (config, OK) |
| `AuditAspect` | 22/161 | **14%** | ⚠️ |
| `JwtAuthFilter` | 7/100 | **7%** | 🔴 |
| `JwtService` | 4/88 | **5%** | 🔴 |
| `JwtProperties` | 0/12 | **0%** | 🔴 |
| `SaddayAuthDetails` | 0/9 | **0%** | 🔴 |

---

## 4. Estructura y Calidad de Tests Existentes

### ✅ Lo que está bien

| Aspecto | Evaluación |
|---------|-----------|
| `AbstractIntegrationTest` como base | Centraliza Testcontainers, RSA keys, perfil `test` |
| `TestcontainersConfig` con `@ServiceConnection` | Moderno, PostgreSQL 16 real |
| `@Transactional` rollback | Aislamiento correcto entre tests |
| SQL fixtures separadas por módulo | 7 archivos bien organizados |
| `@DisplayName` en español | Consistente y legible |
| `@Nested` en unit tests | `AuthServiceTest` agrupa por operación |
| Helpers `obtenerToken()` | Reutilizables para auth en cada IT |
| Tests de seguridad RBAC | 3 roles probados en cada módulo |
| Detección de robo de token | Probado end-to-end |
| Cookie HttpOnly/Path/Max-Age | Verificado en AuthIT |

### ⚠️ Oportunidades de mejora

| Problema | Impacto | Esfuerzo |
|----------|---------|----------|
| `obtenerToken()` duplicado en 8 archivos | Mantenibilidad | 30min |
| `Strictness.LENIENT` en unit tests | Oculta stubs innecesarios | 1h |
| `TotpServiceTest.computeTotpCode()` brute-force (hasta 1M iteraciones) | Performance de tests | 1h |

---

## 5. Tests de Integración con Fallas Pre-existentes

Los siguientes 16 tests fallan por **cambios en la API** que no fueron sincronizados con los tests. No están relacionados con la auditoría actual:

| Test | Error | Causa probable |
|------|-------|---------------|
| `AuthIT.login_validCredentials_returns200` | Body contiene campo de contraseña | LoginResponse cambió estructura |
| `AuthIT.login_inhabilitadoSocio_returns403` | Retorna 200 | Lógica de inhabilitación cambió |
| `AuthIT.refresh_*` (4 tests) | 500 | Dependencia de login que falla |
| `AuthIT.logout_validCookie` | 500 | Idem |
| `AuthServiceTest.refresh_success_rotatesToken` | Mock mismatch | `save()` llamado diferente número de veces |
| `SalidaIT.lookups` | JSON path faltante | Estructura de lookups cambió |
| `SalidaIT.eliminar_*` (2 tests) | 500 | API de eliminación cambió |
| `InformeIT.obtener_sinInforme` | 200 en vez de 404 | Lógica de obtención cambió |
| `ActaIT.actualizar_*` (2 tests) | 422 | Validación nueva |
| `EstadisticaIT.historialAjeno_socioRegular` | 200 en vez de 403 | Permisos cambiaron |
| `MountainIT.agregarContacto` | 404 | Endpoint cambió |

> **Recomendación:** Crear un issue separado para corregir estos 16 tests.

---

## 6. Recomendaciones Priorizadas

### Fase 1 — Arreglar tests rotos restantes (P0)

| Tarea | Esfuerzo | Impacto |
|-------|----------|---------|
| Corregir los 16 tests de integración que fallan | 4-6h | Tests verdes |
| Corregir `AuthServiceTest.refresh_success_rotatesToken` | 30min | Tests verdes |

### Fase 2 — Unit tests para clases de seguridad críticas (P0)

| Test a crear | Cobertura actual | Esfuerzo | Tests estimados |
|-------------|-----------------|----------|----------------|
| `JwtServiceTest` | 5% | 2h | ~12 |
| `JwtAuthFilterTest` | 7% | 1.5h | ~8 |
| `AuditServiceTest` | 26% | 1h | ~6 |

### Fase 3 — Mejorar cobertura de servicios principales (P1)

| Test a crear | Cobertura actual | Esfuerzo | Meta |
|-------------|-----------------|----------|------|
| `MountainServiceTest` | 13% | 2h | 60%+ |
| `InformeServiceTest` | 33% | 2h | 60%+ |
| `RutaServiceTest` | 36% | 2h | 60%+ |
| `ActaServiceTest` | 40% | 1.5h | 60%+ |
| `TotpServiceTest` (refactor brute-force) | 5% | 1h | 60%+ |

### Fase 4 — Mejoras de infraestructura (P2)

| Mejora | Esfuerzo |
|--------|----------|
| Mover `obtenerToken()` a `AbstractIntegrationTest` | 30min |
| Cambiar `LENIENT` a `STRICT` en unit tests | 1h |
| Configurar JaCoCo minimum threshold (60%) en CI | 1h |
| Agregar `@MockitoBean SecurityEventService` documentación | ✅ Hecho |

---

## 7. Configuración de JaCoCo

JaCoCo ya está configurado en el `pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

**Para generar el reporte:**
```bash
./mvnw verify -Dmaven.test.failure.ignore=true
# Reporte HTML: target/site/jacoco/index.html
# Reporte CSV:  target/site/jacoco/jacoco.csv
```

### Configuración recomendada para CI (agregar al plugin):

```xml
<execution>
    <id>check</id>
    <goals><goal>check</goal></goals>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.60</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

---

## 8. Archivos Modificados en Esta Auditoría

| Archivo | Cambio |
|---------|--------|
| `src/test/resources/sql/socios-test-data.sql` | Añadido `estado_acceso_id` |
| `src/test/resources/sql/auth-test-data.sql` | Añadido `estado_acceso_id` |
| `src/test/resources/sql/notificaciones-test-data.sql` | Añadido `estado_acceso_id` |
| `src/test/java/.../SocioServiceTest.java` | Añadido mock `EstadoAcceso` |
| `src/test/java/.../AuthServiceTest.java` | Añadido 6 `@Mock` faltantes + `getEstadoAcceso()` |
| `src/test/java/.../AbstractIntegrationTest.java` | Añadido `@MockitoBean SecurityEventService` |
