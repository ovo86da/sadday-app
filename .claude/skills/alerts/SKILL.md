---
name: alerts
description: Resume las alertas de seguridad de GitHub Code Scanning — cuántas hay abiertas por herramienta y severidad, qué ha cambiado desde la última consulta, qué categorías han dejado de actualizarse y el detalle de las críticas. Usar al revisar el estado de seguridad, tras un escaneo, o para saber si un cambio de dependencias arregló algo.
allowed-tools: Bash(./scripts/alerts.sh *) Read
arguments: [rama]
---

# Alertas de seguridad

!`./scripts/alerts.sh ${1:+--ref }${1:-}`

## Cómo interpretarlo

**El resumen por herramienta** dice quién reporta qué. Cada escáner mira una
cosa distinta y las severidades no son comparables entre ellos: una "alta" de
Trivy (imagen de contenedor) y una "alta" de Snyk (dependencia de la app) no
tienen el mismo alcance.

**`CAMBIOS`** es lo que casi siempre se quiere saber: qué ha aparecido desde la
última vez que se miró. Se compara contra una instantánea guardada en
`.git/alerts-snapshot-<rama>.json` — **una por rama**, porque comparar las
alertas de `develop` contra las de `main` da un delta sin sentido. Está dentro
de `.git/`, así que nunca se versiona.

Cada consulta **reescribe** la instantánea: por eso la segunda consulta
seguida dice "0 nuevas". Para mirar sin perder la referencia anterior,
`--peek`.

**`CATEGORÍAS MUERTAS`** es la parte menos obvia y la que más tiempo ahorra.
Code Scanning cierra una alerta cuando un análisis **nuevo de la misma
categoría** deja de reportarla. Si la categoría cambia de nombre —porque el
escáner renombró el proyecto, o se renombró un job— las alertas viejas se
quedan abiertas para siempre y el total deja de significar nada. Se detecta
agrupando los análisis por `analysis_key` (el job): la categoría del análisis
más reciente de cada job es la viva; cualquier otra con alertas abiertas está
muerta.

Limpiarlas es **irreversible** (borra el análisis y sus alertas), así que el
script da el comando pero no lo ejecuta.

**`CRÍTICAS`** se listan siempre enteras, sin recortar. Son pocas por
definición; si son muchas, eso ya es la información.

## Argumento

Sin argumento consulta `develop`. Con uno, esa rama: `/alerts main`.

Útil sobre una rama de PR tras lanzar el escaneo con
`gh workflow run security.yml --ref <rama>`: muestra el efecto del cambio
antes de mergearlo.

## Por qué existe

El JSON crudo de las alertas son ~145.000 tokens. Este resumen son ~290.

Y responde a algo que la web no: **qué ha cambiado**. El panel de Code Scanning
muestra un estado, no un delta, y con ~200 alertas abiertas distinguir a ojo lo
nuevo de lo que ya estaba es impracticable.

## Salidas

| Código | Significado |
|---|---|
| 0 | consulta correcta |
| 2 | no se pudo consultar: rama inexistente, falta `gh`/`jq`, o red caída |

La rama se valida antes de consultar. Sin esa comprobación la API devuelve una
lista vacía para una rama que no existe, y un error de tecleo se presentaría
como "0 alertas, todo bien" — además de sobrescribir la instantánea buena.
