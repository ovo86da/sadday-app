# FR-007 — Bloqueo/advertencia de cancelación de inscripción para Jefe de Salida + Sección Notificaciones

## Contexto

Actualmente, si un socio designado como Jefe de Salida cancela su inscripción, el sistema lo
permite sin advertencia, dejando la salida sin jefe asignado sin que nadie sea notificado.
Adicionalmente, la sección de "Aprobaciones" pasa a llamarse "Notificaciones", unificando en
un solo lugar tanto las aprobaciones pendientes como las alertas de salidas sin jefe.

## Comportamiento esperado

### A — Socio común designado como Jefe de Salida

Si un socio (sin rol privilegiado) intenta cancelar su inscripción y tiene la dignidad
**Jefe de Salida**, la acción se bloquea. El botón "Cancelar inscripción" desaparece y se
muestra el siguiente mensaje en su lugar:

> "Usted se inscribió en esta salida y fue elegido como Jefe de Salida, por lo que no puede
> cancelar su inscripción. Si pese a eso decide cancelar, por favor contacte al Jefe de Montaña
> para solucionar el inconveniente."

El backend también lanza error con el mismo mensaje como segunda línea de defensa.

### B — Directivo/Admin/Secretaria designado como Jefe de Salida

Un usuario con rol privilegiado (ADMIN, SECRETARIA, DIRECTIVO) **sí puede cancelar** su propia
inscripción aunque sea Jefe de Salida. Al hacerlo:

- La cancelación procede normalmente.
- El nombre del jefe retirado queda registrado en la salida (`jefeAbandonoNombre`).
- El Jefe de Montaña verá una alerta en **Notificaciones** y en el banner del dashboard:

  > "Salida sin Jefe de Salida: [nombre] [apellido], que estaba asignado como Jefe de Salida,
  > se retiró de la salida."

- La alerta se borra automáticamente cuando un nuevo Jefe de Salida es designado.

### C — Rename "Aprobaciones" → "Notificaciones"

- En el menú lateral, la entrada "Aprobaciones" pasa a llamarse **"Notificaciones"**.
- La ruta cambia de `/aprobaciones` a `/notificaciones`.
- El título de la página muestra "Notificaciones".
- La página muestra dos secciones independientes:
  1. **Aprobaciones pendientes** (comportamiento existente).
  2. **Salidas sin Jefe de Salida** (nueva sección, solo visible para JM/Admin/Secretaria).
- El banner del dashboard que hoy dice "Tienes X aprobación(es) pendiente(s)" también considera
  las alertas de sin-jefe para usuarios JM.

## Impacto técnico

| Capa     | Cambios | Archivos                                                                       |
|----------|---------|--------------------------------------------------------------------------------|
| Backend  | 5       | migration SQL, `Salida.java`, `SalidaService.java`, nuevo DTO, `SalidaController.java` |
| Frontend | 6       | `nav.ts`, `router.tsx`, `app-layout.tsx`, `dashboard.tsx`, `use-salidas.ts`, `aprobaciones-page.tsx` |

## Endpoints

| Endpoint                              | Método | Uso                                             |
|---------------------------------------|--------|-------------------------------------------------|
| `GET /v1/salidas/alertas-sin-jefe`    | GET    | Nuevo — lista salidas con jefe retirado (JM/Admin/Sec) |

## Cambios en detalle

### Backend

**`V58__add_jefe_salida_abandono.sql`** (nueva migración)
```sql
ALTER TABLE salidas ADD COLUMN jefe_abandono_nombre VARCHAR(200);
```

**`Salida.java`** — nuevo campo `String jefeAbandonoNombre`

**`SalidaService.cancelarInscripcion()`**
- Si el participante es Jefe de Salida Y **no** es privilegiado → lanzar `BusinessException` con el mensaje de bloqueo.
- Si el participante es Jefe de Salida Y **es** privilegiado → permitir, y guardar `salida.setJefeAbandonoNombre(nombre + " " + apellido)`.

**`SalidaService.designarJefeSalida()`**
- Al asignar un nuevo jefe, limpiar: `salida.setJefeAbandonoNombre(null)`.

**Nuevo DTO `AlertaSinJefeResponse`**
```java
record AlertaSinJefeResponse(String salidaId, String salidaNombre, String fechaSalida, String jefeAbandonoNombre) {}
```

**`SalidaController`** — nuevo endpoint `GET /alertas-sin-jefe`, acceso `ADMIN/SECRETARIA/DIRECTIVO`.

### Frontend

- `nav.ts` → `title: "Notificaciones"`, `href: "/notificaciones"`
- `router.tsx` → `path="/notificaciones"`, actualizar import
- `app-layout.tsx` → referencias a `/aprobaciones` → `/notificaciones`, texto del banner
- `dashboard.tsx` → link a `/notificaciones`
- `use-salidas.ts` → nuevo hook `useAlertasSinJefe()`
- `aprobaciones-page.tsx` → título "Notificaciones", nueva sección "Salidas sin Jefe de Salida"

## Estado: Pendiente — 2026-04-28
