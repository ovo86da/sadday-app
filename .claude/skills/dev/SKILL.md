---
name: dev
description: Estado del entorno de desarrollo local — qué servicios corren, si el backend sirve código viejo, y si las migraciones cuadran con la rama actual. Usar antes de probar algo en el navegador, al retomar el trabajo, y siempre que la aplicación se comporte de forma rara sin motivo aparente.
allowed-tools: Bash(./scripts/dev.sh *) Read
arguments: [accion]
---

# Entorno de desarrollo

!`./scripts/dev.sh ${1:-}`

## Cómo interpretarlo

Cada línea es una pieza del entorno. `TODO LISTO` significa que se puede
probar; cualquier `⚠` o `✗` trae debajo el comando que lo arregla.

## Las tres cosas que comprueba, y por qué

**Que los servicios estén vivos.** Postgres, MinIO, Mailpit, el backend y el
frontend. Obvio, pero es la mitad de las veces que algo «no funciona».

**Que el backend sirva el código actual.** Esta es la razón de ser de la
skill. El backend **no recarga en caliente**: tras editar un `.java` sigue
sirviendo el anterior y nada lo indica — la API responde, los tests pasan, y
lo que se prueba en el navegador es código viejo.

La comprobación correcta no es si hace falta recompilar: tras un
`mvnw compile` las clases están frescas pero **la JVM en marcha sigue con las
anteriores cargadas**. Lo que se compara es la fecha del fuente más reciente
contra **la hora de arranque del proceso**.

El frontend no necesita esto: Vite recarga en caliente y basta refrescar el
navegador.

**Que las migraciones cuadren con la rama.** Si la base está en una versión
más alta que la que trae el código —lo normal al volver a una rama anterior—
**Flyway se niega a arrancar**, y el síntoma es un backend que no levanta sin
motivo aparente. El aviso lo dice antes de que pase.

## Acciones

| Comando | Qué hace |
|---|---|
| `/dev` | solo informa; no toca nada |
| `./scripts/dev.sh --restart-backend` | para y vuelve a levantar el backend, y espera a que responda |
| `./scripts/dev.sh --restart-frontend` | ídem con Vite |

Los reinicios **no** se invocan desde la skill a propósito: parar un proceso
es una acción con efecto, y además el backend tarda en arrancar. Se lanzan a
mano cuando el informe lo pide.

## Por qué existe

Averiguar «¿está vivo y tiene mi código?» eran tres o cuatro comandos cada vez,
y se repite constantemente mientras se desarrolla.

Pero lo que la justifica no es el ahorro, es el error que evita: **dar por
buena una prueba hecha contra código viejo.** Pasó el 2026-10-18 — se anunció
que algo estaba listo para probar cuando el backend llevaba horas sirviendo
una versión anterior, y lo que el usuario probó no era lo que se había
cambiado.

## Salidas

| Código | Significado |
|---|---|
| 0 | todo en marcha y al día |
| 1 | algo requiere acción — las líneas marcadas dicen cuál |
| 2 | no se pudo comprobar: no es un repo git, o Docker no responde |

Siguiendo la regla de `check.sh`: **lo que no se puede determinar cuenta como
aviso, nunca como visto bueno.** Si el backend responde pero no se puede saber
si su código está al día, lo dice en vez de callarse — porque un «ok» falso es
justo lo que esta skill existe para no producir.
