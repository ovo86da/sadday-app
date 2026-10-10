---
name: check
description: Corre las verificaciones del monorepo (tests de backend, frontend y mobile, más flutter analyze) y devuelve solo el veredicto y los fallos. Usar antes de commitear, antes de abrir un PR, o siempre que haga falta saber si algo está roto. Acepta un módulo concreto como argumento.
allowed-tools: Bash(./scripts/check.sh *) Read
arguments: [target]
---

# Verificar el monorepo

Resultado de `./scripts/check.sh $1`:

!`./scripts/check.sh ${1:-all}`

## Cómo interpretarlo

Cada línea es un módulo. `TODO OK` al final significa que todo pasó y el script
salió con 0.

Si aparece `HAY FALLOS`:

- Debajo de cada módulo que falló están sus tests rotos, hasta 10.
- La última línea da la ruta del log completo. **Leerlo solo si el resumen no
  basta para entender el fallo** — son miles de líneas y por eso no salen aquí.
- `⚠ sin resultado interpretable` significa que el comando no produjo un
  resumen reconocible: casi siempre es un error de compilación, no un test
  fallando. Ahí sí hay que mirar el log.

## Qué cubre

| Módulo | Comando |
|---|---|
| backend | `./mvnw test` — 877 tests |
| frontend | `pnpm test` (vitest) — 20 tests |
| mobile | `flutter test` — 104 tests, y `flutter analyze` |

El MCP no tiene tests; solo `tsc` vía `pnpm build`.

Para acotar a un módulo, pasarlo como argumento: `backend`, `frontend`,
`mobile`. Sin argumento corre todo, que tarda alrededor de dos minutos por el
backend con Testcontainers.

## Por qué existe este skill

El output crudo de esos cuatro comandos son ~49.000 tokens. Este resumen son
~47. No es una optimización menor: correr los tests sin esto consume una
fracción apreciable de la ventana de contexto para acabar diciendo
"877 pasaron".

Si se cambia el script, mantener la regla de la que depende todo esto: **lo que
no se puede interpretar cuenta como fallo, nunca como OK.** Un resumidor que
oculta un problema es peor que no tenerlo.
