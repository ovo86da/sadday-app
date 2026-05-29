# Flujo 21 — Notificaciones y Alertas

## ¿Qué es este módulo?

El sistema **no usa notificaciones push** (no hay Firebase, FCM ni notificaciones del sistema operativo). Las alertas son **in-app únicamente**: se consultan al servidor cuando el usuario abre la pantalla correspondiente o carga el dashboard.

Hay tres tipos de alertas, cada una con su propio origen y audiencia:

| Alerta | ¿Dónde aparece? | ¿Quién la ve? |
|--------|:---------------:|:-------------:|
| Aprobaciones de inscripción pendientes | Pantalla Notificaciones | Directivo, Admin (todas); Jefe de Salida (las suyas) |
| Salidas sin Jefe de Salida | Pantalla Notificaciones | Admin, Secretaria, Directivo |
| Cumpleaños del día | Dashboard | Cualquier socio autenticado |

---

## Alerta 1 — Aprobaciones de inscripción pendientes

Se activa cuando un socio se inscribe en una salida con nivel técnico insuficiente y la inscripción queda en `PENDIENTE_APROBACION` (ver [Flujo 8 — Aprobaciones de Inscripción](./08-aprobaciones-inscripcion.md)).

### ¿Qué muestra?

Por cada inscripción pendiente se muestra:

- Nombre del socio inscrito (tappable → abre un panel con su historial de salidas y cumbres logradas)
- Salida y fecha de la salida (tappable → navega al detalle de la salida)
- Nivel técnico del socio vs. nivel mínimo requerido por la salida
- Estado de cada firma de aprobación:
  - `Jefe de Montaña` — si el slot de Directivo/Admin ya fue firmado
  - `Jefe de Salida` — si el slot del Jefe de Salida ya fue firmado

### ¿Quién ve qué?

| Rol | Qué inscripciones aparecen |
|-----|---------------------------|
| Admin / Directivo | Todas las inscripciones en `PENDIENTE_APROBACION` del sistema donde su slot (Directivo) aún no fue firmado |
| Jefe de Salida | Solo las inscripciones pendientes en sus propias salidas donde su slot aún no fue firmado |
| Secretaria / Socio | No ven esta sección |

> Un mismo directivo que también es Jefe de Salida de una salida verá las pendientes de esa salida en ambas capacidades.

### Acciones disponibles

Directamente desde la tarjeta de alerta, el usuario puede:

- **Aprobar** — requiere ingresar un motivo (obligatorio, hasta 500 caracteres)
- **Negar** — requiere ingresar un motivo (obligatorio, hasta 500 caracteres)

Al aprobar o negar, la lista se refresca automáticamente. El flujo completo de estados se documenta en el [Flujo 8](./08-aprobaciones-inscripcion.md).

---

## Alerta 2 — Salidas sin Jefe de Salida

Se muestra cuando una salida que estaba `PLANIFICADA` ya tenía un Jefe de Salida asignado y ese socio se retiró del rol, quedando la salida sin responsable.

### ¿Qué muestra?

Por cada salida afectada:

- Nombre de la salida (tappable → navega al detalle de la salida)
- Fecha de la salida
- Nombre del socio que se retiró como Jefe de Salida (si aplica)

### ¿Quién la ve?

Solo Admin, Secretaria y Directivo. Los socios regulares no ven esta sección.

### Acción esperada

Entrar al detalle de la salida y designar un nuevo Jefe de Salida antes de que se realice.

---

## Alerta 3 — Cumpleaños del día

Cada vez que se carga el Dashboard, el sistema consulta qué socios cumplen años hoy. Se muestran como un aviso de celebración en la pantalla principal.

### ¿Qué muestra?

- Nombre y apellido de cada socio que cumple años hoy
- Edad que cumple

### ¿Quién la ve?

Cualquier socio autenticado. Excluye socios con estado `EX-MIEMBRO` y `PENDIENTE DE REGISTRO`.

### ¿Hay acción?

No. Es solo informativa — un recordatorio para que el club felicite al socio.

---

## Modelo de entrega: polling bajo demanda

No hay suscripción, socket ni polling automático en segundo plano. Las alertas se cargan:

- **Pantalla Notificaciones** — al abrir la pantalla y al hacer pull-to-refresh.
- **Dashboard** — al cargar la pantalla principal tras el login.

Si el usuario no abre la pantalla de Notificaciones, no se entera de las aprobaciones pendientes hasta que lo haga.

```
Usuario abre pantalla Notificaciones
  │
  ├── GET /v1/salidas/aprobaciones-pendientes  → lista de inscripciones pendientes
  └── GET /v1/salidas/alertas-sin-jefe         → salidas sin jefe (si es privilegiado)

Usuario carga Dashboard
  └── GET /v1/notificaciones/cumpleanos        → socios con cumpleaños hoy
```

---

## Tabla de permisos

| Acción | Admin | Secretaria | Directivo | Jefe de Salida | Socio |
|--------|:-----:|:----------:|:---------:|:--------------:|:-----:|
| Ver aprobaciones pendientes (todas) | ✅ | ❌ | ✅ | — | ❌ |
| Ver aprobaciones pendientes (sus salidas) | — | — | — | ✅ | ❌ |
| Aprobar / negar inscripción pendiente | ✅ | ❌ | ✅ | ✅ (sus salidas) | ❌ |
| Ver alertas de salidas sin jefe | ✅ | ✅ | ✅ | ❌ | ❌ |
| Ver cumpleaños del día | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## Mobile

### Pantalla Notificaciones

Accesible desde el menú lateral (Drawer) únicamente para Admin, Secretaria y Directivo. Los socios con rol Socio no ven la entrada "Notificaciones" en el menú.

La pantalla tiene dos secciones:

1. **Salidas sin Jefe de Salida** (solo si el usuario es Admin, Secretaria o Directivo) — tarjetas con el nombre de la salida y quién se retiró; tap navega al detalle de la salida.

2. **Aprobaciones pendientes** (cualquier rol privilegiado o Jefe de Salida) — tarjetas con botones Aprobar / Negar integrados. Al pulsar se abre un diálogo para ingresar el motivo antes de confirmar.

El historial de salidas del socio solicitante se puede consultar sin salir de la pantalla, desplegando un panel inferior (bottom sheet) con total de participaciones y cumbres logradas.

No hay badge de contador sobre el ícono de notificaciones en el menú — el usuario debe entrar para ver cuántas hay pendientes.

### Dashboard

El aviso de cumpleaños aparece como una tarjeta en la pantalla principal, visible para todos los roles. Si no hay cumpleaños ese día, la sección muestra "Ningún socio cumple años hoy".

### Sin notificaciones push

La app no solicita permisos de notificaciones del sistema operativo ni usa Firebase Cloud Messaging. No hay alertas cuando la app está cerrada o en segundo plano.
