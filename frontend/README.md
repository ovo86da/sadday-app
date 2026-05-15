# Sadday App — Frontend

Interfaz web del sistema de gestión del Club de Montaña Sadday (`el-sadday.com`).

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Lenguaje | TypeScript 5.9 |
| Framework | React 19 + Vite 7 |
| Estilos | TailwindCSS 4 |
| Componentes | Radix UI · shadcn/ui · Lucide React |
| Estado global | Zustand 5 |
| Data fetching | TanStack Query 5 · Axios |
| Formularios | React Hook Form 7 · Zod 4 |
| Gráficos | Recharts 3 |
| Routing | React Router 7 |
| Tests | Vitest + Testing Library + Playwright |

---

## Requisitos previos

- Node.js 20+
- pnpm (gestor de paquetes)

---

## Configuración inicial

```bash
cd frontend
pnpm install
```

Crear el archivo de variables de entorno:

```bash
cp .env.example .env
```

Variables disponibles:

```env
VITE_API_URL=http://localhost:8080/api/v1
```

---

## Ejecución

```bash
# Servidor de desarrollo con HMR
pnpm dev          # → http://localhost:5173

# Build de producción
pnpm build

# Preview del build de producción
pnpm preview

# Linting
pnpm lint
```

---

## Estructura de directorios

```
src/
├── components/
│   └── ui/           # Componentes base reutilizables (Button, Input, Dialog, Badge…)
├── config/
│   └── nav.ts        # Configuración de la barra de navegación lateral
├── hooks/            # Hooks de TanStack Query por módulo
│   ├── use-actas.ts
│   ├── use-estadisticas.ts
│   ├── use-informes.ts
│   ├── use-montanas.ts
│   ├── use-rutas.ts
│   ├── use-salidas.ts
│   ├── use-socios.ts
│   └── ...
├── lib/
│   ├── api.ts        # Instancia Axios con interceptores (token, refresco automático)
│   └── utils.ts      # Helpers (cn para TailwindCSS, etc.)
├── pages/            # Páginas por módulo
│   ├── actas/        # Lista de actas, formulario, importación .md
│   ├── acceso-nivel/ # Gestión de acceso por nivel técnico
│   ├── admin/        # Panel de administración de usuarios
│   ├── aprobaciones/ # Aprobación de inscripciones de riesgo
│   ├── auditoria/    # Tabla de auditoría de acciones del sistema
│   ├── contactos/    # Directorio global de contactos
│   ├── estadisticas/ # Tabs: Salidas · Reuniones · Búsqueda por socio
│   ├── informes/     # Informe post-salida (jefe de salida)
│   ├── montanas/     # Catálogo de montañas y rutas
│   ├── planificador/ # Planificador de salidas con recomendaciones
│   ├── rutas/        # Rutas multi-actividad con filtros por tipo
│   ├── salidas/      # Salidas, inscripciones, dignidades, detalle
│   ├── socios/       # CRUD socios, habilitación, historial, CSV
│   ├── dashboard.tsx
│   ├── login.tsx
│   ├── perfil.tsx
│   ├── registro-completar.tsx
│   ├── forgot-password.tsx
│   └── reset-password.tsx
├── router.tsx        # Rutas protegidas por rol (RoleRoute)
├── stores/
│   └── auth-store.ts # Estado de autenticación (Zustand)
└── types/            # Tipos TypeScript por módulo
    ├── actas.ts
    ├── mountains.ts
    ├── rutas.ts
    ├── salidas.ts
    ├── socios.ts
    └── ...
```

---

## Páginas implementadas

| Ruta | Página | Roles con acceso |
|------|--------|-----------------|
| `/` | Dashboard | Todos |
| `/login` | Login con 2FA | Público |
| `/registro/completar` | Completar registro (link de invitación) | Público |
| `/forgot-password` | Solicitar reset de contraseña | Público |
| `/reset-password` | Establecer nueva contraseña | Público (token) |
| `/perfil` | Perfil del usuario logueado | Todos |
| `/socios` | Lista y gestión de socios | Admin · Secretaria · Directivo |
| `/montanas` | Catálogo de montañas | Todos |
| `/rutas` | Rutas multi-actividad con filtros | Todos |
| `/salidas` | Salidas — vista admin + vista socio | Todos |
| `/planificador` | Planificador de rutas | Admin · Directivo |
| `/informes` | Informes de salidas | Admin · Secretaria · Directivo · Jefe de Salida |
| `/actas` | Actas de reunión con importación .md | Todos (con permisos) |
| `/estadisticas` | Rankings · Reuniones · Búsqueda por socio | Admin · Secretaria · Directivo |
| `/contactos` | Directorio global de contactos | Admin · Secretaria |
| `/acceso-nivel` | Configuración de niveles de acceso | Admin · Directivo |
| `/aprobaciones` | Aprobación de inscripciones de riesgo | Admin · Directivo |
| `/admin` | Gestión de usuarios y cuentas | Admin |
| `/auditoria` | Registro de auditoría | Admin · Secretaria · Directivo |

---

## Autenticación

El cliente maneja JWT con refresco automático silencioso:

- **Access token** (15 min): almacenado en memoria (Zustand), no en localStorage
- **Refresh token** (30 días): cookie HttpOnly, enviada automáticamente
- **Interceptor Axios**: si el servidor devuelve 401, intenta `/auth/refresh` automáticamente y reintenta la petición original
- **2FA TOTP**: si el usuario tiene 2FA habilitado, el login devuelve `requiresMfa: true` y se muestra el campo de código TOTP
- **Country Challenge**: si se detecta un login desde un país nuevo (GeoIP), devuelve `requiresCountryChallenge: true` y solicita el código enviado por email

---

## Roles y control de acceso

Las rutas se protegen con `RoleRoute` en `router.tsx`. Los roles disponibles:

| Rol | Acceso |
|-----|--------|
| `ADMIN` | Todo |
| `SECRETARIA` | Socios, Actas, Informes, Estadísticas, Contactos |
| `DIRECTIVO` | Rutas, Salidas, Planificador, Estadísticas, Aprobaciones, Auditoría |
| `SOCIO` | Dashboard, Mis Salidas, Montañas, Rutas, Actas (solo tipo SOCIOS), Perfil |

---

## Convenciones

- **Hooks**: un archivo por módulo en `hooks/`, cada hook encapsula una query o mutación de TanStack Query
- **Formularios**: React Hook Form + resolver Zod para validación; los tipos del formulario se definen con `z.infer<typeof schema>`
- **Componentes UI**: basados en Radix UI con estilos Tailwind, sin dependencia de bibliotecas externas de componentes completas
- **Errores de API**: el interceptor Axios extrae `response.data.message` y lo pasa al toast de Sonner
- **Paginación**: se usa el formato Spring Data `Page<T>` — `content[]` + `page.totalElements` + `page.totalPages`
