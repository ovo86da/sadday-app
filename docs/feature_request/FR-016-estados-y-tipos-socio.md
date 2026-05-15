# FR-016: Extensión de Estados de Habilitación y Tipos de Socio

**Fecha:** 2026-05-15
**Estado:** En progreso
**Módulo:** Backend + Frontend — Socios / Administración
**Prioridad:** Media
**Rama Git:** `feature/estados-y-tipos-socio`

---

## 1. Resumen Ejecutivo

Ampliar los catálogos de `estado_habilitacion` y `tipo_socio_club` para reflejar situaciones reales del club que hoy no tienen representación en el sistema. Se agregan dos nuevos estados de habilitación ("Licencia" y "Re-inscripción") y un nuevo tipo de socio ("Ausente"). Además, se refactoriza la UI de edición del socio para consolidar el cambio de estado junto al resto de los campos, eliminando los botones separados de habilitar/inhabilitar.

---

## 2. Cambios en los Catálogos

### 2.1 Estados de Habilitación (de 3 → 5)

| ID | Nombre | Descripción | Puede inscribirse en salidas |
|---|---|---|---|
| 1 | Habilitado | Socio activo con cuotas al día o declarado vitalicio | ✅ |
| 2 | Inhabilitado | Sancionado o con deuda de cuotas | ❌ (configurable) |
| 3 | Socio Vitalicio | Exento del pago de cuotas por antigüedad | ✅ |
| 4 | Licencia | Solicitó permiso de ausencia temporal | ❌ (configurable) |
| 5 | Re-inscripción | Inactivo prolongado; debe pagar re-inscripción para volver | ❌ (configurable) |

### 2.2 Tipos de Socio (de 5 → 6)

| ID | Nombre | Descripción |
|---|---|---|
| 1 | Socio Activo | Socio regular del club |
| 2 | Aspirante | Pendiente de tomar el curso de montaña |
| 3 | Ex-socio | Presentó renuncia o fue expulsado |
| 4 | Sancionado | Sancionado por la directiva |
| 5 | Juvenil | Menor de edad (calculado automáticamente desde fecha de nacimiento) |
| 6 | Ausente | Socio que se ha ausentado del club por un período |

---

## 3. Cambio de UI — Formulario de Edición del Socio

### 3.1 Antes (actual)
- Botón separado **"Habilitar"** / **"Inhabilitar"** en la ficha del socio
- No existe opción en la UI para asignar "Vitalicio", "Licencia" ni "Re-inscripción"
- El tipo de socio sí se edita en el formulario

### 3.2 Después (nuevo)
- El campo **Estado de habilitación** se agrega al formulario de edición (`PUT /v1/socios/{id}`), al mismo nivel que Tipo de Socio y Nivel Técnico
- Se muestra un `<Select>` con los 5 estados disponibles
- Los botones separados de habilitar/inhabilitar se eliminan
- Cada cambio de estado sigue registrándose en el historial de habilitación (`habilitacion_log`)

**Nota de restricción existente:** el backend impide cambiar el estado de un socio con rol ADMIN o SECRETARIA desde este flujo (aplica a los estados Inhabilitado, Licencia y Re-inscripción — no aplica a Vitalicio).

---

## 4. Configuración — Bloqueo de Inscripciones por Estado

En **Administración → Configuración del sistema** se agregan dos nuevos parámetros, independientes entre sí y del parámetro existente de Inhabilitado:

| Clave de configuración | Descripción | Default |
|---|---|---|
| `bloquear_inscripciones_inhabilitado` | (existente) Bloquea inscripciones de socios Inhabilitados | `true` |
| `bloquear_inscripciones_licencia` | Bloquea inscripciones de socios en Licencia | `true` |
| `bloquear_inscripciones_reinscripcion` | Bloquea inscripciones de socios en Re-inscripción | `true` |

La lógica en `SalidaService` se expande para evaluar los tres parámetros de forma independiente.

---

## 5. CSV de Habilitación Masiva

El archivo CSV de habilitación masiva (`POST /v1/socios/habilitacion/csv`) se expande para aceptar los 5 estados:

| Valor en CSV (case-insensitive) | Estado resultante |
|---|---|
| `habilitado` | Habilitado |
| `inhabilitado` / `deshabilitado` | Inhabilitado |
| `vitalicio` / `socio vitalicio` | Socio Vitalicio |
| `licencia` | Licencia |
| `reinscripcion` / `re-inscripcion` / `re inscripcion` | Re-inscripción |

Error cuando el valor no coincide con ninguno: `"Estado inválido: '{valor}'. Use: Habilitado, Inhabilitado, Vitalicio, Licencia, Re-inscripción."` HTTP 422.

---

## 6. Reglas de Negocio

- **Socio Vitalicio:** no puede ser cambiado a ningún otro estado (restricción existente, se mantiene)
- **Licencia:** bloquea inscripciones si el parámetro `bloquear_inscripciones_licencia` = `true`
- **Re-inscripción:** bloquea inscripciones si el parámetro `bloquear_inscripciones_reinscripcion` = `true`
- **Admin/Secretaria:** no pueden ser inhabilitados ni puestos en Licencia/Re-inscripción desde el flujo estándar (restricción existente, se extiende a los nuevos estados)
- **Historial:** todo cambio de estado — incluyendo los nuevos — se registra en `habilitacion_log` con la misma mecánica actual (actor, estado anterior, estado nuevo, timestamp, fuente)

---

## 7. Impacto en Backend

### 7.1 Base de datos — nueva migración Flyway

**`V{N}__estados_tipos_socio.sql`**

```sql
-- Nuevos estados de habilitación
INSERT INTO estado_habilitacion (id, nombre, descripcion) VALUES
  (4, 'Licencia',        'Socio con permiso de ausencia temporal aprobado por la directiva.'),
  (5, 'Re-inscripción',  'Socio inactivo prolongado; debe pagar cuota de re-inscripción para reactivarse.');

-- Nuevo tipo de socio
INSERT INTO tipo_socio_club (id, nombre, descripcion) VALUES
  (6, 'Ausente', 'Socio que se ha ausentado del club por un período prolongado.');

-- Nuevos parámetros de configuración
INSERT INTO config (clave, valor, descripcion) VALUES
  ('bloquear_inscripciones_licencia',      'true', 'Bloquear inscripciones en salidas de socios en estado Licencia'),
  ('bloquear_inscripciones_reinscripcion', 'true', 'Bloquear inscripciones en salidas de socios en estado Re-inscripción');
```

### 7.2 SocioService

- **Método `habilitar()`/`inhabilitar()`:** se mantienen para compatibilidad (los usan el log CSV y el historial), pero se encapsulan en un método genérico `cambiarEstadoHabilitacion(UUID socioId, Short nuevoEstadoId, UUID realizadoPorId)` que:
  - Valida que el nuevo estado existe en la tabla
  - Aplica la restricción de no modificar Vitalicios
  - Aplica la restricción de no inhabilitar Admin/Secretaria (extendida a Licencia y Re-inscripción)
  - Registra en `habilitacion_log`
- El endpoint `PUT /v1/socios/{id}` (actualizar socio) incluye `estadoHabilitacionId` como campo editable

### 7.3 CsvHabilitacionService

- `resolverEstadoDestino()` se expande para mapear los 5 valores (ver tabla sección 5)
- El mensaje de error en la UI se actualiza para listar los 5 valores válidos

### 7.4 SalidaService

- `validarHabilitacionParaInscripcion()` pasa de evaluar solo "Inhabilitado" a evaluar tres condiciones independientes usando los tres parámetros de config

### 7.5 Endpoints afectados

| Método | Endpoint | Cambio |
|---|---|---|
| `PUT` | `/v1/socios/{id}` | Acepta `estadoHabilitacionId` en el body |
| `POST` | `/v1/socios/habilitacion/csv` | Acepta 5 estados en lugar de 2 |
| `GET` | `/v1/socios/lookups` | Devuelve los 5 estados y 6 tipos automáticamente |
| `GET/PATCH` | `/v1/admin/config` | Nuevas claves de configuración |

Los endpoints `PATCH /{id}/habilitar` y `PATCH /{id}/inhabilitar` se **deprecan** — quedan en el código por compatibilidad pero la UI deja de usarlos.

---

## 8. Impacto en Frontend

### 8.1 Formulario de edición del socio (`socio-form-dialog.tsx`)

- Agregar `<Select>` para `estadoHabilitacionId` poblado desde `lookups.estadosHabilitacion`
- Eliminar los botones separados de Habilitar / Inhabilitar de la ficha del socio (`socios-page.tsx`)

### 8.2 Badges de color (`socios-page.tsx`)

Ampliar `estadoBadgeVariant()` con los nuevos estados:

| Estado | Color / variante |
|---|---|
| Habilitado | `default` (verde/primario) |
| Socio Vitalicio | `secondary` |
| Inhabilitado | `destructive` (rojo) |
| Licencia | `warning` (ámbar) |
| Re-inscripción | `destructive` outline (rojo suave) |

### 8.3 CSV de habilitación masiva — instrucciones (`csv-habilitacion-dialog.tsx`)

Actualizar el texto de ayuda para listar los 5 valores válidos.

### 8.4 Configuración de sistema (`admin-page.tsx` o equivalente)

Agregar los dos nuevos toggles junto al existente de "Bloquear inscripciones inhabilitados".

### 8.5 Cambios automáticos (sin trabajo adicional)

- Filtros de la página de socios (estado, tipo) — se actualizan solos desde lookups
- Exportación CSV/PDF — incluye nuevos valores automáticamente
- Historial de habilitación — sin cambios

---

## 9. Archivos a Modificar / Crear

### Backend

| Archivo | Tipo | Descripción |
|---|---|---|
| `db/migration/V{N}__estados_tipos_socio.sql` | Nuevo | INSERTs para nuevos valores |
| `SocioService.java` | Modificar | Método genérico `cambiarEstadoHabilitacion` + `PUT` acepta estadoId |
| `CsvHabilitacionService.java` | Modificar | `resolverEstadoDestino()` acepta 5 valores |
| `SalidaService.java` | Modificar | Validación de inscripción evalúa 3 parámetros independientes |

### Frontend

| Archivo | Tipo | Descripción |
|---|---|---|
| `socio-form-dialog.tsx` | Modificar | Agregar Select de estado habilitación |
| `socios-page.tsx` | Modificar | Eliminar botones habilitar/inhabilitar + ampliar badge colors |
| `csv-habilitacion-dialog.tsx` | Modificar | Actualizar texto de ayuda |
| `admin-page.tsx` | Modificar | Agregar 2 nuevos toggles de configuración |

---

## 10. Consideraciones de Migración de Datos

No se modifica ningún dato existente. Los nuevos valores de catálogo se agregan sin afectar registros actuales. Socios existentes conservan sus estados y tipos actuales.
