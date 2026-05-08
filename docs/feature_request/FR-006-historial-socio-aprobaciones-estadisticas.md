# FR-006 — Historial de montañas/rutas por socio en Aprobaciones y Búsqueda Avanzada

## Contexto

Cuando un Directivo o Jefe de Montaña revisa una inscripción pendiente de aprobación, necesita
evaluar si el socio tiene experiencia real en terreno para la salida solicitada. Actualmente solo
ve el nivel técnico registrado, pero no puede ver el historial concreto de montañas y rutas que
el socio ya ha hecho.

Adicionalmente, en Estadísticas → Búsqueda Avanzada solo se puede filtrar por montaña/ruta/dignidad
para obtener una lista de socios, pero no se puede hacer el camino inverso: partir de un socio
específico y ver qué montañas y rutas ya realizó.

## Comportamiento esperado

### A — Historial desde Aprobaciones

- En la pantalla de **Aprobaciones pendientes**, el nombre del socio en cada card de inscripción
  es un botón con estilo de enlace.
- Al hacer clic se abre un modal con el historial completo de ese socio:
  - Resumen: total de salidas, total de cumbres logradas.
  - Tabla: Montaña · Altitud · Ruta · Fecha · ¿Llegó a cumbre?
- El modal es de solo lectura; los botones de Aprobar/Denegar siguen en la card original.
- Visible para ADMIN, DIRECTIVO (con flag JM) y Jefes de Salida (quienes también aprueban).

### B — Búsqueda por socio en Estadísticas → Búsqueda Avanzada

- Se añade una sección **"Buscar por socio"** al inicio del tab Búsqueda Avanzada.
- Campo de texto con debounce: busca por nombre, apellido o cédula usando el endpoint
  `/v1/socios/buscar` (ya existente).
- Los resultados aparecen como lista de selección. Al seleccionar un socio se despliega
  un panel con su historial de montañas/rutas (mismo componente que en el modal de aprobaciones).
- El historial del socio seleccionado convive con los filtros existentes de búsqueda avanzada
  (están en secciones independientes, no se bloquean entre sí).

## Impacto técnico

| Capa     | Cambios       | Detalle                                                                 |
|----------|---------------|-------------------------------------------------------------------------|
| Backend  | Ninguno       | Endpoints existentes cubren toda la funcionalidad requerida             |
| Frontend | 2 archivos    | `aprobaciones-page.tsx`, `estadisticas-page.tsx`                        |
| Tipos    | Ninguno       | `SocioHistorialResponse` ya tipado en `use-estadisticas.ts`             |

## Endpoints usados (todos existentes)

| Endpoint                              | Uso                                      |
|---------------------------------------|------------------------------------------|
| `GET /v1/estadisticas/socios/:id`     | Historial completo de montañas y rutas   |
| `GET /v1/socios/buscar?q=...`         | Búsqueda de socios por nombre/cédula     |

## Componente compartido: `SocioHistorialPanel`

Componente interno (definido en cada archivo que lo usa) que recibe `socioId: string` y renderiza:
- Estadísticas resumidas (total salidas, cumbres).
- Tabla de historial: Montaña | Altitud | Ruta | Fecha | Cumbre.

## Estado: Implementado — 2026-04-28
