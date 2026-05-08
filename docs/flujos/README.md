# Documentación de Flujos — Sadday App

Bienvenido a la documentación funcional de la aplicación del club de montaña. Aquí encontrarás cómo funciona cada proceso del sistema, desde el ingreso de un nuevo socio hasta la gestión de salidas.

## ¿Para quién es esta documentación?

Esta documentación está escrita tanto para el equipo técnico como para las secretarias, directivos y administradores que usan el sistema. Cuando un concepto sea técnico, se explicará con palabras simples.

---

## Índice

| # | Documento | ¿Qué explica? |
|---|-----------|----------------|
| 1 | [Invitación y Registro de Socios](./01-invitacion-y-registro.md) | Cómo se da de alta a un nuevo socio en el sistema |
| 2 | [Acceso al Sistema (Login)](./02-acceso-al-sistema.md) | Cómo inicia sesión un socio, qué pasa si falla, el 2FA |
| 3 | [Estados de Acceso](./03-estados-de-acceso.md) | Los cinco estados que controlan quién puede entrar al sistema |
| 4 | [Roles y Permisos](./04-roles-y-permisos.md) | Qué puede hacer cada rol (Admin, Secretaria, Directivo, Socio) |
| 5 | [Gestión de Socios](./05-gestion-socios.md) | Edición de perfil, cuotas, habilitación/inhabilitación |
| 6 | [Salidas e Inscripciones](./06-salidas-e-inscripciones.md) | Crear una salida, inscribirse, confirmar asistencia, generar informe |
| 7 | [Cargas Masivas desde CSV](./07-cargas-masivas-csv.md) | Importar socios existentes y actualizar habilitación masivamente via CSV |
| 8 | [Aprobaciones de Inscripción](./08-aprobaciones-inscripcion.md) | Qué pasa cuando un socio se inscribe sin el nivel técnico requerido |
| 9 | [Manejo y Revisión de Logs](./09-manejo-y-revision-logs.md) | Auditoría de acciones y logs de seguridad |
| 10 | [Proceso de Backup de BD y Logs](./10-proceso-backup-bdd-logs.md) | Cómo se respaldan la base de datos y los logs del sistema |
| 11 | [Limpieza Automática de Tokens de Seguridad](./11-limpieza-tokens-seguridad.md) | Cómo y cuándo se eliminan los tokens expirados o ya usados |
| 12 | [Actualización Automática de GeoIP](./12-actualizacion-automatica-geoip.md) | Cómo se actualiza la BD de IPs semanalmente sin reiniciar el backend |
| 13 | [Alertas de Infraestructura al Administrador](./13-alertas-infraestructura-admin.md) | Emails automáticos al admin cuando GeoIP falla o está desactualizado |

---

## Conceptos clave del sistema

### Dos capas de clasificación de un socio

El sistema separa dos cosas que a veces se confunden:

- **Estado de acceso** — controla si el socio puede o no entrar al sistema (iniciar sesión).
- **Tipo de socio** — clasificación del club (Activo, Aspirante, Ex-Socio, etc.), usada para estadísticas.

Un socio puede ser "Ex-Socio" del club (tipo de socio) pero aún tener acceso al sistema hasta que la secretaria cambie su estado de acceso a `EX_MEMBER`. Son dos cosas independientes.

### Auditoría

Toda acción importante queda registrada automáticamente: quién la hizo, cuándo, y qué cambió. Esto se puede consultar en **Administración → Auditoría**.
