# FR-003: Integración de Mailpit para Simulación de Correos en Local

## Descripción del Problema
Actualmente, el sistema cuenta con funcionalidades que envían correos electrónicos (ej. verificación de login de nuevo país, recuperación de contraseñas, etc.). Sin embargo, en el entorno de desarrollo no existe un servidor SMTP configurado, lo que impide probar estas funcionalidades de forma completa (los correos se pierden o generan errores de conexión).

## Objetivo
Integrar **Mailpit** (una herramienta moderna de testing de correos) dentro del stack de Docker de desarrollo. Mailpit actuará como un servidor SMTP "falso" que atrapará todos los correos salientes de la aplicación y los mostrará en una interfaz web amigable, permitiendo al desarrollador previsualizar el HTML y verificar el funcionamiento sin enviar spam accidentalmente.

## Cambios Propuestos

### 1. Actualización de `docker-compose.yml`
- Añadir un nuevo servicio llamado `mailpit` usando la imagen oficial `axllent/mailpit`.
- Exponer el puerto `1025` para recibir correos (SMTP).
- Exponer el puerto `8025` para la interfaz gráfica web (HTTP).

### 2. Configuración del Backend (`api` en Docker)
- El archivo `application-local.yml` ya tiene parametrizado `MAIL_HOST` y `MAIL_PORT: 1025`.
- Se debe modificar el bloque del servicio `api` en `docker-compose.yml` para cambiar la variable de entorno `MAIL_HOST` de `localhost` a `mailpit` (el nombre del contenedor), asegurando así que el backend envíe los correos al contenedor correcto.

## Criterios de Aceptación
- Al ejecutar `docker-compose up`, Mailpit debe iniciar correctamente.
- Navegar a `http://localhost:8025` debe mostrar la bandeja de entrada vacía.
- Cuando la aplicación backend intente enviar un correo (usando JavaMailSender), este debe aparecer instantáneamente en la bandeja de entrada web de Mailpit.
