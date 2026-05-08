# Arquitectura de Seguridad en Autenticación

Este documento describe a profundidad los mecanismos criptográficos y las estrategias anti-abuso implementadas en los flujos críticos de inicio de sesión de Sadday, específicamente el **Autenticador 2FA (TOTP)** y el **Reto de País Desconocido (Location Verification)**.

## 1. Reto de Verificación de Ubicación (Country Challenge)

Cuando el sistema detecta un inicio de sesión desde un código de país que no existe en el historial de los últimos 90 días del usuario, y este **no tiene 2FA habilitado**, el flujo de inicio de sesión entra en modo "Pausado" (`202 Accepted`).

Para validar la identidad, se envía un código de un solo uso (OTP) al correo electrónico.

### A. Generación Criptográfica y Almacenamiento

El código OTP enviado al correo no se genera con un generador pseudoaleatorio estándar, sino con entropía criptográfica fuerte para evitar predicciones:

1. **Generación Segura**: Se utiliza `java.security.SecureRandom`. Se genera un número aleatorio entre `0` y `999,999` y se formatea rígidamente a 6 dígitos (`%06d`).
2. **Cero Almacenamiento en Texto Plano**: El código de 6 dígitos **nunca se almacena en la base de datos**. Al generarse, el sistema le aplica una función criptográfica unidireccional (SHA-256) a través de un `TokenHasher`. En la base de datos (`country_challenge_tokens`) únicamente se almacena el `codeHash`.
   - *Ventaja de Seguridad*: Si la base de datos se ve comprometida (volcado de datos), el atacante no puede ver cuáles son los códigos de 6 dígitos que están vivos en ese momento, ya que romper el SHA-256 tomaría más tiempo que el tiempo de vida del propio token.

### B. Prevención contra Fuerza Bruta (Brute-Force)

Puesto que el código tiene solo 6 dígitos (1 millón de combinaciones posibles), un atacante en línea podría intentar enviar 1000 peticiones por segundo para adivinar el código dentro de su ventana de vida útil (15 minutos). 

El sistema mitiga esto mediante dos barreras a nivel de entidad de base de datos:
1. **Contador Estricto de Intentos (`attempts`)**: Cada vez que el código introducido no coincide con el hash, la base de datos incrementa un contador atómico.
2. **Límite y Quema del Token**: El límite estricto está definido por `COUNTRY_CHALLENGE_MAX_ATTEMPTS`. Si el usuario se equivoca el máximo de veces permitidas, el token queda **quemado automáticamente** (`used = true`).
   - El atacante se topa con un `ErrorCode.ACCOUNT_LOCKED` para ese reto. Tendría que iniciar un nuevo proceso de login desde cero, invalidando el esfuerzo computacional de los intentos anteriores y ralentizando la viabilidad matemática de un ataque por fuerza bruta.

### C. Manejo Seguro del Estado de la Base de Datos

En arquitecturas menos robustas, los eventos de seguridad y auditoría se escriben antes de evaluar los retos. Esto puede generar vulnerabilidades de *Race Conditions*. 

En Sadday, el evento de éxito condicional (`NEW_COUNTRY_LOGIN`) **solo se graba en la base de datos** después de que la validación criptográfica de 6 dígitos pasa con éxito (en el método `completeCountryChallenge`). Si se bloquea al usuario, el sistema emite el reto pero marca la auditoría como `COUNTRY_CHALLENGE_ISSUED`, manteniendo el historial geográfico intacto e impidiendo que un simple *refresh* de la página burle la protección.

---

## 2. Autenticación de Dos Factores (MFA / 2FA)

La segunda capa de defensa de Sadday está basada en el estándar industrial de contraseñas de un solo uso basadas en tiempo (**TOTP** - RFC 6238).

### A. Almacenamiento Cifrado de Secretos

El *Shared Secret* (la llave semilla de 16-32 caracteres) se asocia a cada usuario que habilita 2FA.
- **Cifrado en Reposo**: Este secreto **no se guarda en texto plano** en la tabla de usuarios. Es cifrado en la base de datos mediante encriptación simétrica bidireccional (AES-256-GCM) utilizando la variable de entorno maestra `TOTP_ENCRYPTION_KEY`.
- *Ventaja*: Un atacante con acceso a la base de datos no puede clonar la cuenta TOTP de los usuarios en su propio dispositivo sin tener también acceso a la llave maestra ubicada en las variables de entorno del servidor.

### B. Ventana de Tolerancia de Tiempo

El protocolo TOTP es altamente sensible al reloj del servidor y del dispositivo del usuario (desincronizaciones (drift) de apenas unos segundos arruinan el login).
Sadday utiliza una ventana de validación de tiempo (`TotpService`) que comprueba:
- El segmento de tiempo exacto actual.
- Un segmento hacia atrás (-30s).
- Un segmento hacia adelante (+30s).
Esto garantiza una excelente experiencia de usuario (evitando errores cuando el reloj del celular tiene un retraso) manteniendo la superficie de ataque minúscula (los códigos expiran de forma estricta tras un máximo de 90 segundos).

---

## 3. Manejo y Revocación de Sesiones Intermedias

Durante estos retos (Country Challenge o MFA Challenge), el sistema genera un **Challenge Token**.

1. **Tokens Desechables e Identificables**: El *Challenge Token* viaja al frontend, pero de nuevo, su valor viaja seguro y la base de datos solo almacena su Hash.
2. **Pausa Absoluta (Revocación Preventiva)**: En el momento en que se emite un reto, el *Refresh Token* que se acababa de generar para la sesión queda en estado de cuarentena (`revoked = true`).
   - El atacante no tiene acceso al Access Token todavía, y el Refresh Token que se generó de fondo no sirve para nada hasta que no se demuestre identidad.
   - Una vez comprobado el código, el sistema desbloquea o expide un nuevo par de tokens completamente funcionales, logrando una transición segura sin dejar artefactos de sesión huérfanos.
