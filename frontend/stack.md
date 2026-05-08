# Stack y Decisiones de Arquitectura — Frontend Sadday App

## Stack Tecnológico

| Capa | Tecnología | Versión | Motivo |
|------|-----------|---------|--------|
| Bundler | **Vite** | ^6.x | Dev server rápido, HMR nativo, proxy integrado |
| Framework | **React** | ^19.x | Ecosistema maduro, compatible con el resto del stack |
| Lenguaje | **TypeScript** | ^5.x | Tipado estático, mejor integración con DTOs del backend |
| Fetching / Cache | **TanStack Query v5** | ^5.x | Cache, revalidación, estados loading/error automáticos |
| Estado global | **Zustand** | ^5.x | Ligero, sin boilerplate (user, session, UI global) |
| HTTP Client | **Axios** | ^1.x | Interceptor para renovar access token desde memoria |
| Componentes UI | **shadcn/ui** | latest | Accesible, customizable, sin bundle overhead |
| Estilos | **Tailwind CSS v4** | ^4.x | Utility-first, ideal con shadcn/ui |
| Formularios | **React Hook Form** | ^7.x | Performante, integración nativa con Zod |
| Validación | **Zod** | ^3.x | Schemas compartibles (form + tipos TS) |
| Gráficas | **Recharts** | ^2.x | Composable, bien integrado con React |
| Routing | **React Router v7** | ^7.x | Layouts anidados, rutas protegidas |

## Decisiones de Diseño

- **Tema**: oscuro por defecto (dark mode), con opción de claro si se requiere en el futuro.
- **Logo**: se añade posteriormente.
- **Paleta de colores**: definida via variables CSS de Tailwind (configurada en `frontend/src/styles/`).

## Generación de PDFs

Los PDFs (informes de salida, actas) los **genera el backend** via un endpoint REST que devuelve `application/pdf`.
El frontend solo hace un GET al endpoint y abre/descarga el archivo.
Ventajas: control total del formato, posibilidad de firma digital, envío por correo desde el servidor.

## Estructura del Repositorio

El frontend vive en `/frontend` dentro del mismo monorepo:

```
sadday-app-claude/
├── backend/          # Spring Boot
├── frontend/         # React + Vite  ← aquí
├── mobile/           # Flutter
├── docs/
└── docker-compose.yml
```

## Entorno de Desarrollo Local

**Requisito**: backend corriendo en `localhost:8080` (vía Docker Compose en la raíz).

```bash
# Terminal 1 — levantar backend + base de datos
docker-compose up

# Terminal 2 — dev server del frontend
cd frontend
pnpm install
pnpm dev        # http://localhost:5173
```

El `vite.config.ts` configura un **proxy** que reenvía `/api/*` → `http://localhost:8080`:

```ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
},
```

Esto elimina problemas de CORS en desarrollo sin necesidad de habilitar CORS extra en el backend.

## Gestión del Access Token y Refresh Token

- El **access token** se guarda **en memoria** (Zustand store), nunca en `localStorage`.
- El **refresh token** viaja como cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`:
  - **JavaScript no puede leerlo ni escribirlo** — lo maneja el browser automáticamente.
  - Se envía solo a endpoints bajo `/api/v1/auth/*`.
  - Expira en 30 días (rotativo: cada refresh emite un nuevo token).
- **Interceptor Axios** debe configurar `withCredentials: true` para que el browser envíe la cookie:
  ```ts
  const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    withCredentials: true,  // ← necesario para enviar/recibir cookies
  });
  ```
- Al recibir un 401, el interceptor llama `POST /api/v1/auth/refresh` (la cookie se envía sola), obtiene un nuevo `accessToken` del body JSON, y reintenta la petición original.
- Al cerrar la pestaña el access token se pierde — pero la cookie persiste. Al recargar, la app intenta un refresh automático.
- **Desarrollo local**: `COOKIE_SECURE=false` en el `.env` del backend (sin HTTPS). El proxy de Vite reenvía la cookie correctamente.

## Variables de Entorno

| Variable | Valor local |
|----------|------------|
| `VITE_API_BASE_URL` | `/api` (usa el proxy de Vite) |
| `VITE_APP_NAME` | `Sadday App` |

En producción el frontend se sirve desde Nginx en el mismo host que el backend; el proxy de Vite no es necesario (mismo origen).
