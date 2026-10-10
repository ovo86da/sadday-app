---
name: deps
description: Compara el árbol de dependencias runtime del backend contra otra rama y avisa de degradaciones, artefactos nuevos y subidas. Usar al revisar cualquier PR que toque backend/pom.xml — en especial los de Dependabot— y después de subir Spring Boot. Acepta una referencia de git como argumento.
allowed-tools: Bash(./scripts/deps.sh *) Read
arguments: [base-ref]
---

# Diferencia del árbol de dependencias

!`./scripts/deps.sh ${1:-origin/develop}`

## Cómo interpretarlo

**`HAY DEGRADACIONES` es un fallo**, no un aviso. Significa que algún artefacto
retrocede de versión respecto a la rama base. Nunca mergear así sin entender
por qué: una versión que baja devuelve los fallos corregidos entre ambas, y
eso incluye CVEs.

El resto es informativo:

- **nuevos** — artefactos que antes no estaban. Normal al añadir una
  dependencia; sospechoso si el PR decía ser solo una subida de versión.
- **retirados** — dejaron de arrastrarse. Suele pasar al subir una librería que
  simplificó sus propias dependencias.
- **subidas** — lo esperable en un PR de dependencias.

## Por qué existe

Al subir Spring Boot de 4.0.6 a 4.0.8, el parent pasó a gestionar netty
4.2.17.Final. El `pom.xml` tenía un override en 4.2.13 puesto semanas antes
para cerrar tres CVEs. Mantenerlo habría **bajado** netty de 4.2.17 a 4.2.13,
devolviendo todo lo corregido entre ambas versiones.

El pom habría compilado. Los 877 tests habrían pasado. Snyk no lo habría
marcado, porque 4.2.13 no tiene CVEs abiertos conocidos — simplemente es peor
que 4.2.17. **Nada en el pipeline lo habría detectado.**

Este script sí: lo prueba su propio caso de prueba, que reintroduce ese
override y verifica que salen las 14 degradaciones de netty y exit 1.

## Notas

- Resuelve el pom de la rama base en un directorio temporal, sin tocar el
  working tree ni cambiar de rama.
- Solo mira **el backend**. El frontend y el MCP usan lockfiles, donde el diff
  del propio PR ya muestra los cambios de versión.
- Tarda cerca de un minuto: son dos resoluciones completas de Maven.
- Una referencia inexistente sale con 2, para no confundirla con "todo bien".
