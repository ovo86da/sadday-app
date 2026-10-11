---
name: ci
description: Estado de los workflows de GitHub Actions y, para un run concreto, qué jobs y pasos fallaron con las líneas de error relevantes. Usar cuando un workflow esté en rojo, tras lanzar un escaneo o un deploy, o para saber si la rama está limpia antes de mergear.
allowed-tools: Bash(./scripts/ci.sh *) Read
arguments: [run_id | --last | --ref rama]
---

# Estado de la CI

!`./scripts/ci.sh ${1:-}`

## Cómo interpretarlo

**Sin argumento** sale el último run de cada workflow en `develop`, con su
conclusión, cuándo fue, el id y el evento que lo disparó. La columna `event`
importa más de lo que parece: `push`, `schedule` y `workflow_dispatch`
significan cosas distintas, y ver `schedule` donde se esperaba `push` revela un
disparador mal configurado.

Si algo falló, el script imprime el comando exacto para ver el detalle y sale
con 1.

**Con un id de run** salen solo los jobs fallidos, sus pasos fallidos y las
líneas de error con contexto. `--last` coge el run fallido más reciente sin
tener que copiar el id.

## Qué filtra, y por qué

El log de un job son ~30 KB de los que casi nada sirve: descarga de acciones,
`git config`, bloques `env:`, limpieza del post-job. Se descarta todo eso y se
conservan las líneas alrededor de cada `##[error]`, `ERROR` o `Status: 4xx/5xx`.

Dos detalles que hacen que funcione:

- **El eco del script de cada paso va coloreado con `36;1m`.** Se descarta
  *antes* de limpiar las secuencias ANSI, que es la única forma fiable de
  distinguir "esto es el comando que iba a ejecutarse" de "esto es su salida".
- **Se corta en `Post job cleanup`.** `gh run view --log | grep <job>` arrastra
  la limpieza del post-job, que menciona el nombre del job y no tiene nada que
  ver con el fallo. Es lo que hace que buscar el error a mano sea tan lento.

## Por qué existe

El log crudo de un run fallido son ~121.000 tokens. Este resumen son ~250.

## Salidas

| Código | Significado |
|---|---|
| 0 | nada falló |
| 1 | hay fallos — el detalle está arriba |
| 2 | no se pudo consultar: rama sin runs, falta `gh`, o red caída |

Una rama sin ningún run sale como **fallo**, no como "todo bien": no haber
corrido nunca no es estar en verde.
