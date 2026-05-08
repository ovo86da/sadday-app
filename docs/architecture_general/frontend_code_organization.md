# Organización del Código Frontend

Este documento explica la estructura de carpetas del frontend de Sadday, los patrones de diseño utilizados y el rol de cada capa. Está orientado a **nuevos desarrolladores** que necesiten entender el proyecto rápidamente.

---

## Patrón de Organización: Package by Layer

A diferencia del backend (que usa *Package by Feature*), el frontend usa **"Package by Layer"** — está organizado por **tipo de archivo**, no por funcionalidad.

**¿Por qué?** En React, los hooks, types y components se comparten mucho más entre pantallas que en el backend. Por ejemplo, el hook `use-socios.ts` lo usa la pantalla de Socios, pero también la de Salidas (para buscar participantes), la de Actas (para buscar asistentes), y la de Estadísticas (para filtrar por socio). Agrupar por tipo facilita encontrar y reutilizar piezas.

```
frontend/src/
├── pages/         ← Todas las pantallas (actas, socios, salidas...)
├── hooks/         ← Todas las conexiones al backend
├── types/         ← Todos los "DTOs" de TypeScript
├── components/    ← Todos los componentes reutilizables
├── stores/        ← Estado global (sesión del usuario)
├── lib/           ← Utilidades (cliente HTTP, helpers)
├── config/        ← Configuración (menú de navegación)
├── test/          ← Tests unitarios
└── assets/        ← Imágenes y archivos estáticos
```

---

## Estructura de Carpetas Completa

```
frontend/
│
│   # ── Archivos de configuración del proyecto ──
├── index.html              ← Página HTML raíz (punto de entrada del navegador)
├── vite.config.ts          ← Configuración de Vite (bundler, proxy al backend)
├── tsconfig.json           ← Configuración de TypeScript
├── eslint.config.js        ← Reglas de linting (calidad de código)
├── package.json            ← Dependencias y scripts (dev, build, test)
├── playwright.config.ts    ← Configuración de tests E2E
├── nginx.conf              ← Configuración de Nginx para producción
├── Dockerfile              ← Imagen Docker para despliegue
├── .env.local              ← Variables de entorno locales
│
│   # ── Código fuente ──
├── src/
│   ├── main.tsx            ← Punto de entrada de React (monta la app en el DOM)
│   ├── App.tsx             ← Componente raíz (proveedores globales + router)
│   ├── router.tsx          ← Definición de todas las rutas de la aplicación
│   ├── index.css           ← Estilos globales y variables CSS
│   │
│   ├── pages/              ← Pantallas de la aplicación
│   ├── hooks/              ← Conexión con la API del backend
│   ├── types/              ← Contratos de datos (DTOs en TypeScript)
│   ├── components/         ← Componentes reutilizables
│   ├── stores/             ← Estado global (Zustand)
│   ├── lib/                ← Utilidades transversales
│   ├── config/             ← Configuración de la app
│   ├── test/               ← Tests unitarios (Vitest)
│   └── assets/             ← Imágenes, SVGs, fuentes
│
│   # ── Tests E2E ──
├── e2e/                    ← Tests de integración con Playwright
│
│   # ── Generados (no se tocan) ──
├── dist/                   ← Archivos compilados para producción
└── node_modules/           ← Dependencias instaladas
```

---

## Las 6 Capas del Frontend

### 1. 📺 `pages/` — Las Pantallas

Cada archivo o carpeta aquí representa **una pantalla completa** de la aplicación. Es lo que el usuario ve y con lo que interactúa directamente.

**Pantallas simples** (un solo archivo):
```
pages/
├── dashboard.tsx           ← Panel principal con estadísticas y widgets
├── perfil.tsx              ← Mi perfil, 2FA, contraseña, sesiones
├── login.tsx               ← Formulario de inicio de sesión
├── forgot-password.tsx     ← Recuperación de contraseña
├── reset-password.tsx      ← Restablecer contraseña (desde link del correo)
├── registro-completar.tsx  ← Completar registro de nuevo socio
├── forbidden.tsx           ← Página 403 (sin permisos)
└── not-found.tsx           ← Página 404
```

**Pantallas complejas** (carpeta con sub-componentes):
```
pages/actas/
├── actas-page.tsx           ← La pantalla principal (tabla de actas)
├── acta-form-dialog.tsx     ← Formulario para crear/editar (se abre como modal)
├── acta-import-dialog.tsx   ← Diálogo para importar actas desde .md
└── index.ts                 ← Re-exporta el componente principal

pages/salidas/
├── salidas-page.tsx         ← Tabla de salidas
├── salida-form-dialog.tsx   ← Crear/editar salida
├── salida-detail-dialog.tsx ← Detalle completo de una salida
├── salida-socio-view.tsx    ← Vista de salida para socios (no admin)
└── index.ts
```

**Regla:** Las pages son el "cerebro visual". Manejan el estado local de la pantalla (qué modal está abierto, qué fila seleccionó el usuario, filtros activos, etc.) y llaman a los **hooks** para obtener datos del backend. **Nunca** hacen llamadas HTTP directas.

---

### 2. 🪝 `hooks/` — La Conexión con el Backend

Los hooks encapsulan **toda la comunicación con la API REST del backend**. Usan TanStack React Query para manejar automáticamente:

- **Caché**: Si los datos ya se pidieron hace poco, no vuelve a llamar al servidor.
- **Estados de carga**: `isLoading`, `isError`, `data` — la pantalla siempre sabe qué mostrar.
- **Invalidación automática**: Después de crear o editar algo, refresca las listas relacionadas.

Hay **un archivo de hooks por módulo de negocio**, similar a cómo hay un Service por módulo en el backend:

| Hook | Módulo | Operaciones |
|---|---|---|
| `use-actas.ts` | Actas | Listar, crear, editar, borrar, importar .md, generar PDF |
| `use-socios.ts` | Socios | CRUD, importar CSV, habilitar/inhabilitar |
| `use-salidas.ts` | Salidas | CRUD, inscripción, gestión de participantes |
| `use-informes.ts` | Informes | CRUD de informes de salida |
| `use-mountains.ts` | Montañas | Listar, CRUD |
| `use-rutas.ts` | Rutas | CRUD de rutas |
| `use-estadisticas.ts` | Estadísticas | Consultas de reportes y gráficos |
| `use-admin.ts` | Admin | Operaciones administrativas (config sistema, auditoría) |
| `use-contactos.ts` | Contactos | CRUD de contactos del club |
| `use-planificador.ts` | Planificador | Planificación de salidas futuras |

**Ejemplo real** (`use-actas.ts`):

```typescript
// Listar actas con paginación
export function useActasList(params) {
  return useQuery({
    queryKey: ["actas", "list", params],      // ← Clave única de caché
    queryFn: () =>
      api.get("/v1/actas", { params })        // ← Llama al backend
         .then(r => r.data.data),
  })
}

// Crear un acta nueva
export function useCreateActa() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req) =>
      api.post("/v1/actas", req)              // ← Envía datos al backend
         .then(r => r.data),
    onSuccess: () =>
      qc.invalidateQueries({                  // ← Después de crear,
        queryKey: ["actas", "list"]            //   refresca la tabla automáticamente
      }),
  })
}
```

**¿Cómo se usa en una página?**

```typescript
// pages/actas/actas-page.tsx
function ActasPage() {
  const { data, isLoading } = useActasList({ page: 0, size: 20 })

  if (isLoading) return <Skeleton />          // ← Muestra animación de carga
  return <Table data={data.content} />        // ← Renderiza la tabla con datos
}
```

**¿Por qué usar hooks y no llamar a `api.get()` directamente en la pantalla?**

1. **Caché automático**: Sin hooks, cada vez que el usuario vuelve a la página se haría otra llamada al servidor. Con React Query, si los datos tienen menos de X minutos, los muestra al instante del caché.
2. **Reutilización**: Si varias pantallas necesitan la lista de socios (Socios, Salidas, Actas), todas usan `useSociosList()` sin duplicar código.
3. **Consistencia**: Si creas un acta en un modal, el hook automáticamente invalida la tabla y la refresca. Sin esto, tendrías que refrescar manualmente en cada lugar donde uses la lista.

---

### 3. 📝 `types/` — Los DTOs del Frontend

Los types son el **equivalente exacto de los DTOs del backend**, pero escritos en TypeScript. Definen la forma exacta de los objetos JSON que vienen del servidor y los que se envían.

Hay **un archivo por módulo de negocio**:

| Archivo | Contenido |
|---|---|
| `actas.ts` | `ActaSummary`, `ActaDetail`, `CreateActaRequest`, `UpdateActaRequest`, etc. |
| `socios.ts` | `SocioListItem`, `SocioDetail`, `ApiResponse<T>`, `PageResponse<T>`, etc. |
| `salidas.ts` | `SalidaSummary`, `SalidaDetail`, `InscripcionRequest`, etc. |
| `informes.ts` | `InformeSummary`, `InformeDetail`, `CreateInformeRequest`, etc. |
| `mountains.ts` | `MontanaListItem`, `MontanaDetail`, etc. |
| `rutas.ts` | `RutaListItem`, `RutaDetail`, `CreateRutaRequest`, etc. |
| `admin.ts` | `ConfigSistema`, `AuditLogItem`, etc. |
| `contactos.ts` | `ContactoItem`, etc. |

**Ejemplo** (`types/actas.ts`):

```typescript
// Lo que llega cuando pides la LISTA de actas (datos resumidos)
export interface ActaSummary {
  id: string
  tipoActa: "DIRECTIVA" | "SOCIOS"
  fecha: string
  totalAsistentes: number
  creadaPorNombre: string
}

// Lo que llega cuando pides el DETALLE de un acta (todos los campos)
export interface ActaDetail {
  id: string
  tipoActa: "DIRECTIVA" | "SOCIOS"
  fecha: string
  asistentes: AsistenteResponse[]
  informes: InformeLinkResponse[]
  observaciones: string | null
  // ... muchos más campos
}

// Lo que se ENVÍA para crear un acta (solo campos necesarios)
export interface CreateActaRequest {
  tipoActa: "DIRECTIVA" | "SOCIOS"
  fecha: string
  hora: string
  asistentesIds?: string[]
}
```

**¿Por qué están separados de los hooks?** Porque son contratos compartidos. La page `actas-page.tsx` necesita `ActaSummary` para la tabla, el diálogo `acta-form-dialog.tsx` necesita `CreateActaRequest` para el formulario, y el hook `use-actas.ts` necesita ambos para tipar las peticiones. Ponerlos en un solo lugar evita duplicación.

> **Correspondencia con el backend:** Cada `interface` en TypeScript corresponde a un `record` o `class` DTO en Java. Si el backend tiene `ActaCreateRequest.java` con campos `tipoActa` y `fecha`, el frontend tiene `CreateActaRequest` en TypeScript con exactamente los mismos campos.

---

### 4. 🧩 `components/` — Piezas Reutilizables

Se divide en tres niveles:

#### `components/ui/` — Los "Átomos" del Diseño (Shadcn/UI)

Son los componentes base más pequeños y genéricos. **No saben nada del negocio del club.** Se usan en **todas** las páginas sin modificación:

```
ui/
├── button.tsx          ← Botones (primario, secundario, destructivo...)
├── input.tsx           ← Campos de texto
├── textarea.tsx        ← Áreas de texto grandes
├── select.tsx          ← Selectores desplegables
├── checkbox.tsx        ← Casillas de verificación
├── switch.tsx          ← Interruptores on/off
├── dialog.tsx          ← Modales/ventanas emergentes
├── sheet.tsx           ← Paneles laterales deslizantes
├── table.tsx           ← Tablas de datos
├── tabs.tsx            ← Pestañas
├── badge.tsx           ← Etiquetas (ej. "Activo", "Pendiente")
├── avatar.tsx          ← Círculos con iniciales o foto de perfil
├── tooltip.tsx         ← Tooltips al pasar el mouse
├── label.tsx           ← Etiquetas de formulario
├── separator.tsx       ← Líneas divisorias
├── popover.tsx         ← Contenedores emergentes
├── command.tsx         ← Buscador de comandos
└── dropdown-menu.tsx   ← Menús desplegables contextuales
```

Estos componentes vienen de la librería **Shadcn/UI** (que usa **Radix UI** por debajo para la accesibilidad). Se copian directamente al proyecto en vez de instalarse como dependencia, lo que permite personalizarlos libremente.

#### `components/layout/` — La Estructura Visual

- `app-layout.tsx` — El esqueleto que envuelve **todas** las páginas autenticadas. Contiene:
  - El **sidebar** de navegación izquierdo (con los links a Dashboard, Socios, Salidas, etc.).
  - El **header** superior (nombre del usuario, botón de logout).
  - El **área de contenido** donde se renderiza la página actual.

#### `components/auth/` — Guardias de Seguridad

- `auth-initializer.tsx` — Al abrir la app, intenta automáticamente refrescar el token JWT para mantener la sesión sin que el usuario tenga que volver a iniciar sesión.
- `route-guards.tsx` — Protege las rutas:
  - `PrivateRoute` — Si no estás logueado, redirige a `/login`.
  - `RoleRoute` — Si no tienes el rol correcto (ej. ADMIN), redirige a `/403`.

#### Otros componentes raíz:

- `error-boundary.tsx` — Captura errores inesperados de JavaScript y muestra una pantalla amigable en vez de una página en blanco.
- `informe-pendiente-guard.tsx` — Verifica si el socio tiene informes pendientes de entregar y muestra un aviso.

---

### 5. 🏪 `stores/` — Estado Global

Usa **Zustand** para guardar información que necesitan **múltiples pantallas al mismo tiempo**. Actualmente hay un único store:

**`auth-store.ts`** — Guarda el token JWT y los datos del usuario logueado:

```typescript
export interface User {
  socioId: string
  username: string
  nombre: string
  rol: string               // "ADMIN", "SECRETARIA", "SOCIO", etc.
  nivelTecnico: string | null
  inhabilitado: boolean
  esJefeMontana: boolean
}

// Cualquier componente puede leer quién está logueado:
const { user, isAuthenticated } = useAuthStore()

// Al hacer login, se guarda:
useAuthStore.getState().setAuth({ accessToken: "eyJ...", user: { ... } })

// Al cerrar sesión, se limpia:
useAuthStore.getState().clearAuth()
```

> **Decisión de seguridad:** El access token se guarda **solo en memoria RAM** (nunca en localStorage ni sessionStorage). Si el usuario cierra la pestaña, el token se pierde. Al recargar la página, `auth-initializer.tsx` intenta un refresh automático usando la cookie HttpOnly del refresh token.

---

### 6. 🔧 `lib/` — Utilidades Transversales

Equivalente a `shared/` del backend. Contiene código que usan todos los módulos:

| Archivo | Qué hace |
|---|---|
| `api.ts` | Cliente HTTP (Axios) configurado con interceptores |
| `api-error.ts` | Helper para extraer mensajes de error de las respuestas del backend |
| `auth-broadcast.ts` | Sincronización de sesión entre pestañas del navegador |
| `query-client.tsx` | Configuración de React Query (tiempos de caché, reintentos) |
| `utils.ts` | Funciones utilitarias pequeñas (merge de clases CSS con `cn()`) |
| `salida-tipo.tsx` | Mapeo de tipos de salida a colores e iconos para la UI |

**`api.ts` en detalle** — Es el equivalente al `RestTemplate` o `WebClient` del backend, pero para el frontend:

```typescript
// 1. Crea la instancia de Axios con la URL base del backend
const api = axios.create({
  baseURL: "/api",
  withCredentials: true,        // Envía la cookie del refresh token automáticamente
})

// 2. ANTES de cada petición: añade el token JWT
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 3. DESPUÉS de cada respuesta: si el backend dice 401 (token expirado),
//    automáticamente pide un nuevo token y reintenta la petición original
api.interceptors.response.use(
  (response) => response,      // Si todo bien, pasa de largo
  async (error) => {
    if (error.response?.status === 401) {
      // Refresca el token y reintenta...
    }
  }
)
```

**`auth-broadcast.ts`** — Resuelve un problema real: si el usuario tiene la app abierta en 3 pestañas y hace logout en una, las otras dos también deben cerrarse. Usa `BroadcastChannel` y `localStorage` para coordinar esto entre pestañas.

---

## Archivos Raíz de `src/`

| Archivo | Rol |
|---|---|
| `main.tsx` | Punto de entrada — monta `<App />` en el elemento `#root` del HTML |
| `App.tsx` | Componente raíz — envuelve la app con React Query Provider, Auth Initializer y el Router |
| `router.tsx` | Define todas las rutas (`/dashboard`, `/socios`, `/actas`, etc.) y qué componente renderiza cada una |
| `index.css` | Estilos globales: variables CSS de colores (modo claro/oscuro), fuentes y utilidades base de Tailwind |

**Orden de arranque al abrir la app:**

```
index.html
  └── main.tsx          → Monta React en el DOM
        └── App.tsx     → Configura proveedores globales
              ├── QueryClientProvider   → Habilita React Query (caché)
              ├── AuthInitializer       → Intenta refresh del token
              └── AppRouter             → Evalúa la URL y renderiza la página
```

---

## El Flujo Completo de una Pantalla

Cuando un socio abre la pantalla de **Actas** (`/actas`):

```
1. El usuario escribe /actas en el navegador
      │
      ▼
2. router.tsx
      → Busca qué componente corresponde a /actas
      → PrivateRoute verifica que esté autenticado
      → AppLayout renderiza el sidebar + header
      │
      ▼
3. pages/actas/actas-page.tsx (la pantalla se monta)
      │
      ▼
4. hooks/use-actas.ts → useActasList() se ejecuta automáticamente
      → ¿Hay datos en caché?
        → SÍ y frescos: los usa directamente (no llama al servidor)
        → NO o expirados: llama a lib/api.ts
      │
      ▼
5. lib/api.ts → GET /api/v1/actas
      → Añade automáticamente: Authorization: Bearer eyJ...
      → El backend responde con JSON
      │
      ▼
6. types/actas.ts → TypeScript valida que el JSON tenga la forma de ActaSummary[]
      │
      ▼
7. La tabla se renderiza con los datos, usando components/ui/table.tsx
      │
      ▼
8. El usuario hace clic en "Crear Acta"
      → Se abre pages/actas/acta-form-dialog.tsx
      │
      ▼
9. El usuario llena el formulario y hace clic en "Guardar"
      → hooks/use-actas.ts → useCreateActa() → POST /api/v1/actas
      → onSuccess: invalida el caché de la lista
      → La tabla se refresca automáticamente con la nueva acta
```

---

## Comparación Backend vs Frontend

| Concepto | Backend (Java) | Frontend (React) |
|---|---|---|
| Organización | Package by Feature | Package by Layer |
| Expone / Consume endpoints | `controller/` | `hooks/` |
| Lógica de negocio | `service/` | `pages/` (estado local + UI) |
| Habla con la BD / API | `repository/` | `lib/api.ts` (Axios) |
| Contratos de datos | `dto/` (Java records) | `types/` (TypeScript interfaces) |
| Modelos de persistencia | `entity/` | — (no aplica, no hay BD local) |
| Configuración global | `config/` | `config/` + `lib/` |
| Estado compartido | — (stateless) | `stores/` (Zustand) |
| Componentes visuales | — (no tiene UI) | `components/` |
| Enrutamiento | `@RequestMapping` | `router.tsx` (React Router) |
| Seguridad de rutas | `SecurityConfig.java` | `route-guards.tsx` |
| Tests unitarios | `src/test/java/` | `src/test/` (Vitest) |
| Tests E2E | — | `e2e/` (Playwright) |

---

## Resumen Rápido

| Capa | Carpeta | Hace... | Usa... |
|---|---|---|---|
| **Pages** | `pages/` | Renderiza pantallas, maneja estado visual | hooks, types, components |
| **Hooks** | `hooks/` | Conecta con el backend, maneja caché | lib/api.ts, types |
| **Types** | `types/` | Define la forma de los datos (DTOs) | — (solo definiciones) |
| **Components** | `components/` | Piezas visuales reutilizables | — (genéricos) |
| **Stores** | `stores/` | Estado global (sesión, usuario) | — |
| **Lib** | `lib/` | Utilidades transversales (HTTP, auth) | stores |

**Regla de dependencias:** Las **pages** pueden usar hooks, types y components. Los **hooks** pueden usar lib y types. Los **components/ui** no dependen de nada del negocio. El **store** solo lo leen las pages, los hooks y lib/api.ts.
