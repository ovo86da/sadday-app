# Feature Requests — cómo se gestionan los cambios funcionales

Esta carpeta guarda la especificación de cada cambio funcional del sistema: qué se pidió, por
qué, cómo se resolvió y cuándo. Un FR no es una tarea ni un issue — es el documento que explica
una decisión de producto y lo que se construyó a partir de ella.

Los **errores de código** no van aquí: esos son issues de GitHub. Aquí va lo que cambia el
comportamiento del sistema de cara a quien lo usa.

---

## El recorrido de un cambio

```
  decisión pendiente    →    FR-0XX-slug.md    →    docs/flujos/NN-*.md
  (fuera del repo)           esta carpeta            documentación viva
```

### 1. Antes de decidir

Lo que todavía está en discusión no entra aquí. Se anota en una lista de pendientes local a la
máquina de quien lleva el proyecto, con las opciones sobre la mesa, su coste y las preguntas
que hay que responder para elegir.

Esa lista conserva también **lo que acaba rechazándose, y por qué** — una traza que esta
carpeta no guarda, porque aquí solo llega lo aprobado. Si algún día alguien pregunta "¿por qué
no hicimos X?", la respuesta vive allí.

### 2. Al aprobarse

Se crea `FR-0XX-slug.md` en esta carpeta con la plantilla de abajo. El FR nace con
`Estado: Aprobado` y se actualiza conforme avanza.

El número es correlativo y **no se reutiliza**, ni siquiera si el FR se abandona: así las
referencias cruzadas en commits, PRs y otros documentos nunca apuntan a otra cosa.

### 3. Al implementarse

Dos cosas, no una:

- Se rellena el `## Registro de implementación` del FR, resumiendo qué se tocó en cada capa.
- Se actualiza el flujo correspondiente en [`docs/flujos/`](../flujos/), que es la
  documentación funcional viva — la que leen secretarias y directivos para saber cómo
  funciona el sistema hoy.

El FR cuenta *qué se decidió y por qué*. El flujo cuenta *cómo funciona ahora*. Son documentos
distintos con vidas distintas: el FR se congela al terminar, el flujo se mantiene.

---

## Plantilla

```markdown
# FR-0XX — Título corto y descriptivo

**Fecha de solicitud:** AAAA-MM-DD
**Fecha de implementación:** AAAA-MM-DD  (o `—` mientras no esté)
**Estado:** Aprobado | En progreso | Implementado | Abandonado
**Prioridad:** Alta | Media | Baja
**Área:** Módulo o módulos afectados

---

## Contexto y motivación

Qué problema real existe hoy y a quién afecta. Si el cambio viene de un uso concreto del club,
describirlo: es lo que hace comprensible la decisión dentro de un año.

## Cambios solicitados

El detalle funcional: endpoints, pantallas, reglas de negocio, roles implicados.

## Registro de implementación

Qué se tocó en cada capa (backend, frontend, mobile, BD, MCP). Se rellena al terminar.
```

---

## Índice

| # | Documento | Estado | Área |
|---|-----------|--------|------|
| 001 | [Gestión de sesiones múltiples y eventos de seguridad](./FR-001-session-management-and-security-events.md) | Implementado | Autenticación / Seguridad |
| 002 | [Verificación por email al detectar login desde país desconocido](./FR-002-country-challenge-verification.md) | Implementado | Autenticación / Seguridad |
| 003 | [Integración de Mailpit para simulación de correos en local](./FR-003-mailpit-integration.md) | Implementado | Infraestructura / Desarrollo |
| 004 | [Vista de invitaciones pendientes de aceptación](./FR-004-invitaciones-pendientes.md) | Implementado | Socios / Invitaciones |
| 005 | [Reset de emergencia por pérdida de teléfono (2FA)](./FR-005-emergency-reset-2fa.md) | Implementado | Autenticación / Seguridad |
| 006 | [Historial de montañas y rutas por socio](./FR-006-historial-socio-aprobaciones-estadisticas.md) | Implementado | Estadísticas / Aprobaciones |
| 007 | [Bloqueo de cancelación para Jefe de Salida + notificaciones](./FR-007-bloqueo-cancelacion-jefe-salida.md) | Implementado | Salidas / Notificaciones |
| 008 | [Mejorar el registro de errores en el frontend](./FR-008-mejoras-logs-errores.md) | ⚠️ Parcial | Frontend / Observabilidad |
| 009 | [Mejorar el registro de errores en el backend](./FR-009-mejoras-logs-errores-backend.md) | ⚠️ Parcial | Backend / Observabilidad |
| 010 | [Estadísticas: búsqueda de actividad por período](./FR-010-estadisticas-busqueda-por-periodo.md) | Implementado | Estadísticas |
| 011 | [Actualización automática de GeoIP sin downtime](./FR-011-geoip-auto-update.md) | Implementado | Infraestructura / Seguridad |
| 012 | [Alertas de infraestructura al administrador por email](./FR-012-admin-infrastructure-alerts.md) | Implementado | Infraestructura / Observabilidad |
| 013 | [MCP Server: asistente de planificación de salidas](./FR-013-mcp-server-planificador-salidas.md) | Implementado | Integración / IA |
| 014 | [Auditoría de test suite y cobertura de código](./FR-014-test-suite-audit-and-coverage.md) | Implementado | Backend / Testing & QA |
| 015 | [Exportación de lista de socios (CSV, PDF, hoja de firmas)](./FR-015-exportacion-socios.md) | Implementado | Socios / Reportes |
| 016 | [Extensión de estados de habilitación y tipos de socio](./FR-016-estados-y-tipos-socio.md) | Implementado | Socios |
| 017 | [Flujo nativo de refresh token para mobile](./FR-017-mobile-native-refresh-token.md) | Implementado | Mobile / Autenticación |
| 018 | [Distribución del MCP como binario ejecutable](./FR-018-mcp-binary-distribution.md) | Implementado | MCP / Distribución |
| 019 | [Proceso digital de ingreso de aspirantes](./FR-019-proceso-ingreso-aspirantes.md) | 🔸 Pendiente de diseño | Socios / Ingreso |
| 020 | [Rutas integrales (multi-cumbre)](./FR-020-rutas-integrales.md) | Implementado | Montañas y rutas |
| 021 | [Módulo de gestión documental](./FR-021-gestion-documental.md) | Implementado | Documentos legales |

Estados verificados contra el código el **2026-10-09**. Los dos marcados como parciales y el
pendiente llevan en su documento una `Nota de auditoría` con el detalle exacto de qué falta.

**Siguiente número libre: FR-022.**

---

## Relación con los issues de GitHub

| | Va a issues | Va a un FR |
|---|---|---|
| Un endpoint devuelve 500 | ✅ | |
| Un test falla en `develop` | ✅ | |
| Una dependencia tiene un CVE | ✅ | |
| Hace falta una pantalla nueva | | ✅ |
| Cambia quién puede hacer algo | | ✅ |
| Cambia una regla del reglamento del club | | ✅ |

La frontera práctica: si arreglarlo devuelve el sistema a como *debía* comportarse, es un
issue. Si cambia cómo *debe* comportarse, es un FR.
