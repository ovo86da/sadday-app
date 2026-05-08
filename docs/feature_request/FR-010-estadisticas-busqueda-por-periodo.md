# FR-010 — Estadísticas: Búsqueda de actividad por período

## Contexto

En Estadísticas → Búsqueda Avanzada existen dos secciones orientadas a socios (historial individual
y explorar participantes). Sin embargo, no hay forma de responder preguntas orientadas al calendario:
¿qué salidas hicimos este mes?, ¿a qué montañas fuimos este año?, ¿qué rutas se recorrieron en los
últimos dos años?

## Comportamiento esperado

Se añade un tercer panel en el tab **Búsqueda Avanzada** llamado **"Actividad por Período"**.

### Filtros disponibles

| Filtro         | Descripción                                           | Valores                                |
|----------------|-------------------------------------------------------|----------------------------------------|
| Tipo de búsqueda | Qué entidad se quiere listar                        | Salidas / Montañas / Rutas             |
| Categoría       | Filtro opcional por tipo de actividad               | Todas / Alpinismo / Escalada / Trekking / Ciclismo |
| Desde / Hasta   | Rango de fechas (fecha de la salida)                | Inputs tipo fecha                      |

### Accesos rápidos de período

Botones que rellenan automáticamente los campos de fecha:
- **Este mes** — desde el 1° del mes corriente hasta hoy
- **Este año** — desde el 1° de enero del año corriente hasta hoy
- **Últimos 6 meses** — desde hace 6 meses hasta hoy
- **Últimos 2 años** — desde hace 2 años hasta hoy

### Resultados según tipo

**Salidas:** tabla con Nombre · Fecha · Categoría · Montaña · Ruta · Estado · Participantes · Cumbre

**Montañas:** tabla con Montaña · Región · Altitud · Salidas en el período · Participantes · Primera fecha · Última fecha

**Rutas:** tabla con Ruta · Montaña · Categoría · Salidas en el período · Participantes · Primera fecha · Última fecha

## Impacto técnico

| Capa      | Cambios    | Detalle                                                                 |
|-----------|------------|-------------------------------------------------------------------------|
| Backend   | 2 archivos | `EstadisticaController` + `EstadisticaService` — 3 endpoints nuevos    |
| DTOs      | 3 nuevos   | `SalidaPeriodoItem`, `MontanaPeriodoItem`, `RutaPeriodoItem`            |
| Frontend  | 2 archivos | `use-estadisticas.ts` (tipos + hooks), `estadisticas-page.tsx` (panel) |

## Endpoints nuevos

| Endpoint                                    | Params                                     | Respuesta               |
|---------------------------------------------|--------------------------------------------|-------------------------|
| `GET /v1/estadisticas/periodo/salidas`      | `fechaDesde`, `fechaHasta`, `tipoActividad?` | `List<SalidaPeriodoItem>`  |
| `GET /v1/estadisticas/periodo/montanas`     | `fechaDesde`, `fechaHasta`, `tipoActividad?` | `List<MontanaPeriodoItem>` |
| `GET /v1/estadisticas/periodo/rutas`        | `fechaDesde`, `fechaHasta`, `tipoActividad?` | `List<RutaPeriodoItem>`    |

## Estado: Implementado — 2026-04-29
