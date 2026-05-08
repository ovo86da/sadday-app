# Arquitectura General del Proyecto Sadday

Este documento describe la arquitectura tecnológica y las herramientas utilizadas en el proyecto de gestión del Club de Montaña Sadday.

## Frontend (Web)

La aplicación web está construida como una Single Page Application (SPA) moderna, enfocada en rendimiento, tipado estricto y excelente experiencia de desarrollo.

- **Herramienta de Construcción:** Vite (v7.3.1)
- **Librería Principal:** React (v19.2.0)
- **Lenguaje:** TypeScript (v5.9.3)
- **Estilos y UI:**
  - Tailwind CSS (v4.2.1) para utilidades CSS.
  - Radix UI (Componentes base accesibles y sin estilos).
  - Shadcn/UI (Patrón de diseño con `class-variance-authority`, `clsx`, `tailwind-merge`).
  - Íconos: Lucide React (v0.577.0).
- **Gestión de Estado y Datos:**
  - Zustand (v5.0.11) para estado global ligero.
  - TanStack React Query (v5.90.21) para peticiones asíncronas, caché y sincronización de estado del servidor.
  - Axios (v1.13.6) como cliente HTTP.
- **Enrutamiento:** React Router (v7.13.1)
- **Formularios:** React Hook Form (v7.71.2) integrado con Zod (v4.3.6) para la validación estricta de esquemas.
- **Gráficos e Interfaz Extra:**
  - Recharts (v3.8.0) para reportes y estadísticas.
  - Sonner para notificaciones (toast).
- **Testing:** Vitest (v4.0.18), Playwright (E2E), React Testing Library.

## Backend (API Rest)

El backend es una API RESTful desarrollada con el ecosistema de Spring, siguiendo principios de seguridad Stateless y alta escalabilidad.

- **Framework Principal:** Spring Boot (v4.0.3)
- **Lenguaje:** Java 21
- **Persistencia de Datos:**
  - Spring Data JPA / Hibernate.
  - Base de Datos: PostgreSQL (driver nativo).
  - Migraciones de BDD: Flyway (`spring-boot-starter-flyway`).
- **Seguridad:**
  - Spring Security.
  - Autenticación completamente Stateless mediante JWT (vía `spring-boot-starter-oauth2-resource-server` con firmas asimétricas RS256).
  - BCrypt / Argon2 (vía BouncyCastle v1.83) para almacenamiento seguro de contraseñas.
  - Protección de Endpoints y Rate Limiting: Bucket4j apoyado por Caffeine Cache en memoria (para mitigar ataques de fuerza bruta o DoS).
- **Almacenamiento y Archivos:**
  - AWS SDK v2 (`software.amazon.awssdk:s3` v2.42.8) para almacenamiento de objetos en Amazon S3 o MinIO local.
  - Generación de PDFs: Flying Saucer (`flying-saucer-pdf-openpdf`) renderizando plantillas inyectadas con Thymeleaf (`thymeleaf`). Conversión de Markdown a HTML mediante CommonMark.
- **Herramientas de Desarrollo y Compilación:**
  - Lombok (reducción de código boilerplate).
  - MapStruct (v1.6.3) para el mapeo rápido y type-safe entre Entidades JPA y DTOs.
- **Geolocalización:** MaxMind GeoLite2 (`geoip2`) para ubicar orígenes de acceso y detectar cambios de país.
- **Testing y Calidad:**
  - JUnit 5 y Spring Boot Test.
  - Testcontainers (PostgreSQL) para pruebas de integración reales aisladas, conectado mediante `@ServiceConnection`.
  - JaCoCo para la cobertura de código.
  - Sonar Maven Plugin para escaneo de calidad y deuda técnica.

## Infraestructura y Despliegue

- **Docker:** Orquestación local con `docker-compose.yml` (PostgreSQL, y herramientas como Mailpit para simulaciones locales de correo SMTP).
- **Control de Versiones y Git:** Flujo estándar Git (GitHub), exclusiones claras en `.gitignore`.
- **Calidad/CI:** Integración lista para SonarCloud y reporte de métricas JaCoCo.

## App Móvil (Mobile)

Actualmente en fase de planificación (carpeta `mobile/` reservada para uso futuro).
