# Organización del Código Backend

Este documento explica la estructura de carpetas del backend de Sadday, los patrones de diseño utilizados y el rol de cada capa. Está orientado a **nuevos desarrolladores** que necesiten entender el proyecto rápidamente.

---

## Patrón de Arquitectura: Package by Feature

El backend sigue el patrón **"Package by Feature"** (Organización por Funcionalidad). Cada módulo de negocio (ej. `actas`, `socios`, `salidas`) tiene su propia carpeta que agrupa **toda** la lógica relacionada con esa funcionalidad.

### ¿Por qué no "Package by Layer"?

Existe una alternativa llamada "Package by Layer" donde se agrupan todas las clases del mismo tipo juntas (todos los controllers en una carpeta, todos los services en otra, etc.). Sadday **no** usa este enfoque porque a medida que el proyecto crece, encontrar todo lo relacionado a una funcionalidad obliga a saltar entre múltiples carpetas.

**Comparación:**

```
# ❌ Package by Layer (NO se usa en Sadday)
com.sadday.app/
├── controller/
│   ├── ActaController.java
│   ├── SocioController.java
│   └── SalidaController.java
├── service/
│   ├── ActaService.java
│   └── SocioService.java
└── repository/
    └── ActaRepository.java

# ✅ Package by Feature (Sadday)
com.sadday.app/
├── actas/          ← TODO lo de Actas está aquí
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── dto/
└── socios/         ← TODO lo de Socios está aquí
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/
    └── dto/
```

---

## Estructura de Carpetas del Backend

```
backend/src/main/java/com/sadday/app/
│
├── SaddayAppApplication.java     ← Punto de entrada de Spring Boot
│
│   # ── Módulos de Negocio (Package by Feature) ──
├── actas/
├── admin/
├── estadisticas/
├── informes/
├── mountains/
├── notificaciones/
├── planificador/
├── salidas/
└── socios/
│
│   # ── Módulos Transversales (sirven a TODOS los módulos) ──
├── auth/           ← Autenticación y autorización (login, JWT, 2FA, reset de contraseña)
├── config/         ← Configuración global de Spring (CORS, beans, datos de inicio)
├── scheduler/      ← Tareas programadas automáticas (cron jobs)
├── security/       ← Filtros HTTP, auditoría, rate limiting, JWT parsing
└── shared/         ← Utilidades comunes (respuestas API, excepciones, PDF, S3, validaciones)
```

---

## Las 5 Capas Internas de Cada Módulo

Cada módulo de negocio (ej. `actas/`) contiene las mismas subcarpetas. Aquí se explica el rol de cada una usando el módulo `actas` como ejemplo:

### 1. 📄 `entity/` — El Espejo de la Base de Datos

Las **Entidades JPA** representan tablas en PostgreSQL. Cada variable en la clase corresponde a una columna en la tabla. Hibernate las usa para leer y escribir datos sin que el desarrollador escriba SQL manualmente.

```java
@Entity
@Table(name = "actas")
public class Acta {
    @Id UUID id;
    String titulo;
    LocalDate fechaRealizacion;
    EstadoActa estado;

    @ManyToOne
    Salida salida;          // Relación con la tabla "salidas"
}
```

**¿Cuántas Entities hay?** Depende de cuántas tablas necesite el módulo. Si "Actas" necesita una tabla para el Acta en sí y otra para los Participantes del Acta, habrá 2 Entities.

> **⚠️ Regla de oro:** Las Entities **nunca** se devuelven directamente al frontend. Para eso existen los DTOs.

---

### 2. 📨 `dto/` — Los Sobres de Mensajería

DTO significa *Data Transfer Object*. Son objetos simples (sin lógica) que definen exactamente qué datos entran y salen de la API. Funcionan como "contratos" entre el Frontend (React) y el Backend.

**¿Por qué hay más DTOs que Entities?**

Porque la misma información se necesita en formatos diferentes según el contexto:

| Clase DTO | Cuándo se usa |
|---|---|
| `ActaCreateRequest.java` | Cuando React **crea** un acta (solo manda los campos necesarios) |
| `ActaListItem.java` | Cuando React pide la **lista** de actas (datos resumidos: título, fecha, estado) |
| `ActaDetailResponse.java` | Cuando React pide el **detalle** de un acta (todos los campos + participantes) |
| `ActaAprobacionRequest.java` | Cuando el Admin **aprueba** un acta (solo manda el estado nuevo) |

**¿Por qué NO usar la Entity directamente?**

1. **Seguridad (Overposting):** Si el Controller recibe la Entity `Usuario` directamente desde React, un hacker podría enviar `{"correo": "hacker@mail.com", "es_admin": true}` y el sistema lo guardaría. Con un DTO `RegistroSocioDTO` que solo tiene `correo`, ese campo `es_admin` simplemente se ignora.

2. **Bucles infinitos en JSON:** Las Entities tienen relaciones bidireccionales (`Acta` → tiene `Socios` → que tienen `Actas` → ...). Si Spring intenta convertir eso a JSON, entra en un bucle infinito que tumba el servidor. Los DTOs rompen ese ciclo.

3. **Claridad del contrato:** El DTO documenta exactamente qué espera recibir la API y qué devuelve. Es mucho más legible que revisar todas las columnas de una tabla.

---

### 3. 💾 `repository/` — El Almacenista

Los Repositories son **interfaces** que hablan directamente con la base de datos. Spring Data JPA implementa automáticamente las operaciones básicas (`findById`, `save`, `delete`, etc.). Para consultas más complejas se añaden métodos personalizados con `@Query`.

```java
public interface ActaRepository extends JpaRepository<Acta, UUID> {

    // Spring genera el SQL automáticamente basándose en el nombre del método:
    List<Acta> findByEstadoOrderByFechaRealizacionDesc(EstadoActa estado);

    // Consulta personalizada en JPQL (lenguaje de Spring) para casos complejos:
    @Query("SELECT a FROM Acta a WHERE a.salida.id = :salidaId AND a.estado = 'APROBADA'")
    List<Acta> findActasAprobadasDeSalida(@Param("salidaId") UUID salidaId);
}
```

**¿Cuántos Repositories hay?** Generalmente uno por Entity principal. No se crean Repositories para Entities secundarias (tablas de detalle) que siempre se guardan a través de su Entity padre.

---

### 4. 🧠 `service/` — El Cerebro

Los Services contienen toda la **lógica de negocio** de la aplicación. Son la capa más importante: aquí se toman las decisiones, se hacen los cálculos y se coordinan las operaciones.

```java
@Service
@RequiredArgsConstructor
public class ActaService {

    private final ActaRepository actaRepository;
    private final SocioRepository socioRepository;

    @Transactional
    public void aprobarActa(UUID actaId, UUID adminId) {
        // 1. Valida que el acta exista
        Acta acta = actaRepository.findById(actaId)
            .orElseThrow(() -> new ActaNotFoundException(actaId));

        // 2. Aplica la lógica de negocio
        acta.setEstado(EstadoActa.APROBADA);

        // 3. Calcula y asigna puntos a los participantes
        for (SocioActa participante : acta.getParticipantes()) {
            participante.getSocio().sumarPuntos(acta.getMontana().getPuntos());
        }

        // 4. Guarda todo en la base de datos en una sola transacción
        actaRepository.save(acta);
    }
}
```

**¿Por qué normalmente hay solo un Service por módulo?** Porque centralizar la lógica hace el código predecible. Si hay un bug en la lógica de aprobación de actas, sabes exactamente dónde buscar: en `ActaService.java`. No en 3 archivos distintos.

---

### 5. 🚪 `controller/` — El Mesero (Puerta de Entrada)

Los Controllers exponen los **endpoints HTTP** de la API REST. Su único trabajo es:
1. Recibir la petición HTTP de React.
2. Convertirla en un DTO.
3. Pasarla al Service.
4. Devolver la respuesta.

```java
@RestController
@RequestMapping("/api/v1/actas")
@RequiredArgsConstructor
public class ActaController {

    private final ActaService actaService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ActaDetailResponse>> getActa(@PathVariable UUID id) {
        ActaDetailResponse detalle = actaService.getDetalle(id);
        return ResponseEntity.ok(ApiResponse.success(detalle));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ActaDetailResponse>> crear(
            @Valid @RequestBody ActaCreateRequest request) {
        ActaDetailResponse nueva = actaService.crear(request);
        return ResponseEntity.status(201).body(ApiResponse.success(nueva));
    }
}
```

El Controller **no tiene lógica de negocio**. Si el Controller está haciendo cálculos o consultando la base de datos directamente, eso es una señal de que esa lógica debería estar en el Service.

---

## El Flujo Completo de una Petición

```
React (Frontend)
    │
    │  POST /api/v1/actas  { "titulo": "Acta Iliniza", ... }
    ▼
Controller          ← Recibe el JSON, lo convierte en ActaCreateRequest (DTO)
    │
    │  actaService.crear(request)
    ▼
Service             ← Valida, calcula, aplica reglas de negocio
    │
    │  actaRepository.save(nuevaActa)
    ▼
Repository          ← Ejecuta INSERT en PostgreSQL
    │
    ▼
Base de Datos       ← Persiste la fila en la tabla "actas"
    │
    ◄── Service convierte la Entity guardada a un ActaDetailResponse (DTO)
    ◄── Controller envuelve el DTO en ApiResponse y devuelve HTTP 201
    │
React               ← Recibe { "success": true, "data": { "id": "...", "titulo": "Acta Iliniza" } }
```

---

## Módulos Transversales

Estas carpetas no pertenecen a un solo módulo de negocio — le sirven a **todos**:

### `auth/`
Maneja toda la autenticación: login, logout, refresh de tokens JWT, registro, verificación de correo, reset de contraseña, 2FA (TOTP), y eventos de seguridad. Tiene su propia estructura completa (controller, service, entity, repository, dto).

### `config/`
Configuración global de Spring Boot:
- `SecurityConfig.java` — Define qué rutas son públicas y cuáles requieren autenticación.
- `AdminBootstrap.java` — Crea el usuario admin inicial si no existe.
- `DevDataInitializer.java` — Carga datos de prueba en entorno local.

### `security/`
Componentes de seguridad que actúan **antes** de que llegue la petición al Controller:
- `jwt/` — Filtro que lee el token JWT de cada petición y valida su firma.
- `audit/` — Registra en la BD quién hizo qué y cuándo (tabla `audit_log`).
- `ratelimit/` — Limita la cantidad de peticiones por IP para prevenir ataques de fuerza bruta.
- `CorrelationIdFilter.java` — Añade un ID único a cada petición para poder trazarla en los logs.

### `shared/`
Utilidades reutilizables en cualquier módulo:
- `dto/` — Clases genéricas como `ApiResponse<T>` (el envoltorio estándar de todas las respuestas) y `PageResponse<T>` (para listas paginadas).
- `exception/` — `GlobalExceptionHandler.java` captura todas las excepciones y las convierte en respuestas JSON consistentes.
- `pdf/` — Servicios para generar PDFs con Thymeleaf y Flying Saucer.
- `storage/` — `S3StorageService.java` para subir y descargar archivos de Amazon S3 o MinIO.
- `validation/` — Anotaciones de validación personalizadas.

### `scheduler/`
Tareas que se ejecutan automáticamente con cron (sin que nadie las llame manualmente):
- Promover socios juveniles que cumplen 18 años.
- Limpiar tokens de reset de contraseña expirados.
- Actualizar base de datos de GeoIP.

---

## Logs: La Electricidad del Edificio

Los logs **no tienen carpeta propia** porque no son código de negocio — son infraestructura del framework (Spring Boot + Logback).

**Cómo se usan:** Se añade la anotación `@Slf4j` de Lombok en cualquier clase y automáticamente se obtiene una variable `log` lista para usar:

```java
@Slf4j
@Service
public class ActaService {
    public void aprobarActa(UUID id) {
        log.info("Iniciando aprobación del acta {}", id);
        // ...
        log.error("Falló la aprobación del acta {}: {}", id, e.getMessage());
    }
}
```

**Dónde se configura el nivel de detalle:** En los archivos `application-{perfil}.yml`:
```yaml
# application-local.yml → más detallado en desarrollo
logging:
  level:
    com.sadday: DEBUG           # Muestra todo en desarrollo

# application-prod.yml → solo errores importantes en producción
logging:
  level:
    com.sadday: INFO
```

> La diferencia entre `log.info(...)` y `log.debug(...)` es que en producción solo se imprimen los `INFO` y superiores — los `DEBUG` se filtran automáticamente.

---

## Resumen Rápido

| Capa | Carpeta | Hace... | Accede a... |
|---|---|---|---|
| **Controller** | `controller/` | Recibe HTTP, llama al Service | Service |
| **Service** | `service/` | Lógica de negocio, cálculos | Repository, otros Services |
| **Repository** | `repository/` | Consultas a la BD | Base de datos (via JPA) |
| **Entity** | `entity/` | Representa una tabla | — (datos, no lógica) |
| **DTO** | `dto/` | Define qué entra/sale de la API | — (datos, no lógica) |

**Regla de dependencias:** La comunicación **siempre** fluye hacia abajo. Un Controller puede llamar a un Service, pero un Service **nunca** debe llamar a un Controller. Un Repository solo habla con la BD, nunca con un Service.
