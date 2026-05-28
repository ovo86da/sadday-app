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

Aplicación nativa multiplataforma construida con Flutter, que corre en Android e iOS desde un único codebase.

- **SDK:** Flutter 3.32.x gestionado con **fvm** (Flutter Version Manager)
- **Lenguaje:** Dart
- **Estado:** Riverpod 3 + riverpod_annotation (codegen con `build_runner`)
- **Navegación:** go_router 17 con guards de autenticación
- **HTTP:** Dio 5 + dio_cookie_manager (cookies HttpOnly persistidas en disco)
- **Almacenamiento seguro:** flutter_secure_storage → Keychain (iOS) / Keystore (Android)
- **Formularios:** reactive_forms 18
- **Gráficos / Visualización:** fl_chart 1, flutter_pdfview, flutter_markdown
- **i18n:** flutter_localizations (Español / Inglés)
- **Seguridad:** local_auth (biometría), flutter_jailbreak_detection, logger silenciado en prod
- **Build:** flavors (dev / staging / prod) con entry points separados (`main_dev.dart`, etc.)
- **Tests:** flutter_test, mocktail, integration_test

La arquitectura mobile sigue el patrón **Feature-first** (similar al backend): cada módulo de negocio agrupa sus datos, dominio y presentación en su propia carpeta bajo `lib/features/`. Ver [`mobile_code_organization.md`](mobile_code_organization.md) para el detalle completo.
