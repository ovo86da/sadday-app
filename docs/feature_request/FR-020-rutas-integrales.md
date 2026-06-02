# FR-020: Rutas Integrales (Multi-cumbre)

**Fecha:** 2026-06-02
**Estado:** En progreso
**Módulo:** Backend + Frontend + Mobile — Montañas / Rutas
**Prioridad:** Media
**Rama Git:** `feature/FR-020-rutas-integrales`

---

## 1. Resumen Ejecutivo

Agregar soporte para **Integrales**: salidas en las que se encadenan varias cumbres (de una misma montaña o de montañas distintas cercanas) en un único recorrido continuo. Estas rutas no encajan en ninguno de los cuatro tipos de actividad existentes porque pueden mezclar tramos de trekking y alpinismo, y porque no están ligadas a una sola montaña sino a un conjunto ordenado de cumbres.

La solución añade `INTEGRAL` como quinto valor de `TipoActividad`, introduce una tabla `ruta_cumbres` para registrar las montañas que componen la integral (con su orden), y agrega los campos específicos necesarios en la entidad `Ruta`.

---

## 2. Modelo de Datos

### 2.1 Cambio en `TipoActividad` (enum)

```
ALPINISMO | ESCALADA | TREKKING | CICLISMO | INTEGRAL   ← nuevo
```

### 2.2 Nueva tabla `ruta_cumbres`

| Columna | Tipo | Notas |
|---|---|---|
| `ruta_id` | `INT` FK → `rutas.id` | ON DELETE CASCADE |
| `mountain_id` | `INT` FK → `mountains.id` | |
| `secuencia` | `SMALLINT` NOT NULL | Orden de ascensión (1, 2, 3…) |

PK compuesta: `(ruta_id, secuencia)`.

Solo se usa cuando `tipo_actividad = 'INTEGRAL'`.

### 2.3 Campos nuevos en tabla `rutas`

| Columna | Tipo | Notas |
|---|---|---|
| `dificultad_maxima_descripcion` | `VARCHAR(200)` | Escala e.g. "AD (IFAS) — tramo norte". Solo en INTEGRAL. |
| `descripcion_itinerario` | `TEXT` | Detalle del recorrido entre cumbres. Solo en INTEGRAL. |

> El campo `mountain_id` existente queda `NULL` en integrales (ya es nullable por diseño).
> Los campos de distancia, desnivel y duración ya existentes se usan como **totales** del recorrido completo.

---

## 3. Reglas de Negocio

- Una integral debe tener **al menos 2 cumbres** en `ruta_cumbres`.
- Las cumbres deben ser montañas distintas **o** cimas distintas identificadas por `mountain_id` diferente. No se permiten duplicados en `(ruta_id, mountain_id)`.
- `secuencia` debe ser contigua (1, 2, 3…) sin saltos.
- `mountain_id` en la cabecera de `rutas` debe ser `NULL` para integrales.
- La dificultad global (`dificultad_maxima_descripcion`) es texto libre: el creador indica la escala del tramo más exigente.
- El `nivelMinimoSocio` sigue aplicando igual que en otros tipos de ruta.
- El estado de la ruta (`PROPUESTA → EN_REVISION → APROBADA / RECHAZADA`) no cambia.

---

## 4. Impacto en Backend

### 4.1 Migración Flyway — `V8__rutas_integrales.sql`

```sql
-- Nueva tabla
CREATE TABLE ruta_cumbres (
    ruta_id   INT NOT NULL REFERENCES rutas(id) ON DELETE CASCADE,
    mountain_id INT NOT NULL REFERENCES mountains(id),
    secuencia SMALLINT NOT NULL,
    PRIMARY KEY (ruta_id, secuencia),
    UNIQUE (ruta_id, mountain_id)
);

-- Nuevos campos en rutas
ALTER TABLE rutas
    ADD COLUMN dificultad_maxima_descripcion VARCHAR(200),
    ADD COLUMN descripcion_itinerario         TEXT;

-- INTEGRAL ya queda como valor válido del enum en JPA (EnumType.STRING, sin DDL propio)
```

### 4.2 Entidad `Ruta.java`

Agregar:
```java
@Column(name = "dificultad_maxima_descripcion", length = 200)
private String dificultadMaximaDescripcion;

@Column(name = "descripcion_itinerario", columnDefinition = "TEXT")
private String descripcionItinerario;

@OneToMany(mappedBy = "ruta", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("secuencia ASC")
private List<RutaCumbre> cumbres = new ArrayList<>();
```

### 4.3 Nueva entidad `RutaCumbre.java`

Clave compuesta embebida `RutaCumbreId(rutaId, secuencia)`. Campos: `ruta`, `mountain`, `secuencia`.

### 4.4 `CreateRutaRequest` / `UpdateRutaRequest`

Agregar campos opcionales:
```java
List<Integer> cumbresMountainIds,   // ordenadas por ascensión; requerido si tipoActividad = INTEGRAL
String dificultadMaximaDescripcion,
String descripcionItinerario,
```

### 4.5 `RutaService` — validaciones nuevas

```
si tipoActividad == INTEGRAL:
  - cumbresMountainIds != null && size >= 2
  - mountainId debe ser null
  - todos los mountainId de la lista deben existir
  - no duplicados en la lista
```

### 4.6 `RutaResponse` / `RutaSummaryResponse`

- `RutaResponse` incluye lista `cumbres: [{secuencia, mountainId, nombreMontana, alturaM}]`
- `RutaSummaryResponse` incluye `numeroCumbres: int` (para listados)

### 4.7 Endpoints afectados

| Método | Endpoint | Cambio |
|---|---|---|
| `POST` | `/v1/rutas` | Acepta `cumbresMountainIds` + nuevos campos |
| `PUT` | `/v1/rutas/{id}` | Ídem |
| `GET` | `/v1/rutas/{id}` | Devuelve `cumbres[]` |
| `GET` | `/v1/rutas` (listado) | `RutaSummaryResponse` incluye `numeroCumbres` |
| `GET` | `/v1/mountains/lookups` | Sin cambio (ya devuelve lista de montañas) |

---

## 5. Impacto en Frontend

### 5.1 Formulario de creación / edición de ruta

- Agregar opción `Integral` en el `<Select>` de Tipo de Actividad.
- Cuando se selecciona `INTEGRAL`:
  - Ocultar campos específicos de ALPINISMO / ESCALADA / TREKKING / CICLISMO.
  - Mostrar selector múltiple de montañas (drag-to-reorder para definir secuencia).
  - Mostrar campos `Dificultad máxima` y `Descripción del itinerario`.

### 5.2 Detalle de ruta

- Sección "Cumbres" con lista ordenada: `1. Iliniza Sur (5248 m)  2. Iliniza Norte (5126 m)`.

### 5.3 Listados / filtros

- El filtro por `tipoActividad` incluye la opción `Integral`.
- En tarjetas de resumen, mostrar `N cumbres` en lugar del nombre de montaña cuando `tipoActividad = INTEGRAL`.

---

## 6. Impacto en Mobile

### 6.1 Pantalla de detalle de ruta (`RutaDetailScreen`)

- Sección "Cumbres" con lista ordenada cuando `tipoActividad == TipoActividad.integral`.

### 6.2 Filtros de rutas (`RutasScreen`)

- Agregar chip/tab `Integral` al selector de tipo de actividad.

### 6.3 Modelo Dart (`Ruta`)

- Agregar `integral` a `TipoActividad` enum.
- Agregar `List<RutaCumbre>? cumbres` al modelo.

---

## 7. Archivos a Modificar / Crear

### Backend

| Archivo | Tipo | Descripción |
|---|---|---|
| `db/migration/V8__rutas_integrales.sql` | Nuevo | DDL: tabla `ruta_cumbres` + columnas en `rutas` |
| `Ruta.java` | Modificar | Nuevos campos + relación `cumbres` |
| `RutaCumbre.java` | Nuevo | Entidad join con clave compuesta |
| `RutaCumbreId.java` | Nuevo | Clave compuesta embebida |
| `TipoActividad.java` | Modificar | Agregar `INTEGRAL` |
| `CreateRutaRequest.java` | Modificar | Campos de integral |
| `UpdateRutaRequest.java` | Modificar | Campos de integral |
| `RutaResponse.java` | Modificar | Campo `cumbres[]` |
| `RutaSummaryResponse.java` | Modificar | Campo `numeroCumbres` |
| `RutaService.java` | Modificar | Validaciones + persistencia de cumbres |

### Frontend

| Archivo | Tipo | Descripción |
|---|---|---|
| `ruta-form-dialog.tsx` | Modificar | Selector de tipo + campos de integral |
| `ruta-detail-page.tsx` | Modificar | Sección "Cumbres" |
| `rutas-page.tsx` | Modificar | Filtro + tarjeta de resumen |

### Mobile

| Archivo | Tipo | Descripción |
|---|---|---|
| `ruta.dart` | Modificar | Modelo + enum `TipoActividad` |
| `ruta_cumbre.dart` | Nuevo | Modelo `RutaCumbre` |
| `rutas_screen.dart` | Modificar | Chip de filtro `Integral` |
| `ruta_detail_screen.dart` | Modificar | Sección "Cumbres" |

---

## 8. Consideraciones de Migración de Datos

No se modifica ningún dato existente. La migración es aditiva: nueva tabla y nuevas columnas nullable. Las rutas actuales no se ven afectadas.
