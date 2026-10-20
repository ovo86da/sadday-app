# Ficheros de conformidad para el análisis de GPX

Estos ficheros existen para que **la web y el móvil den el mismo número**. La
fórmula de FR-023 está implementada dos veces —TypeScript y Dart— y dos
implementaciones de lo mismo se desincronizan en silencio. Aquí es donde eso se
convierte en un fallo de CI en vez de en una diferencia que nadie nota.

`esperado.json` tiene los valores que **las dos** implementaciones deben
producir. Lo leen `frontend/src/lib/analizar-gpx.conformidad.test.ts` y
`mobile/test/unit/analizar_gpx_test.dart`.

## Son sintéticos a propósito

Los tracks reales con los que se calibró FR-023 **no están en el repositorio**:
están excluidos en `.git/info/exclude` y deben seguir fuera. Son rutas reales
del club, con coordenadas y horas de personas concretas, y el repo es público a
propósito. Sirven para calibrar a mano, no para la CI.

Los de aquí no son rutas de nadie. Las coordenadas están alrededor de
`0.1, -78.2` y los desplazamientos son múltiplos exactos de metros sobre el
meridiano, de modo que los valores esperados se pueden comprobar a mano.

## Qué cubre cada uno

| fichero | para qué |
|---|---|
| `subida-simple.gpx` | caso base: sube y no baja. Verifica que un D− de cero es **0** y no «sin dato» |
| `travesia.gpx` | predominio de bajada, como la travesía de Mojanda: D− mucho mayor que D+ |
| `ruido-bajo-umbral.gpx` | oscilaciones de 10 m que **no** deben sumar desnivel |
| `pasos-cortos.gpx` | puntos a 5 m que **no** deben alargar la distancia |
| `sin-tiempos.gpx` | una ruta planificada: sin `<time>`, la duración se queda vacía |
| `varios-dias.gpx` | más de 24 h: se rellenan días y las horas quedan vacías |
| `umbral-justo.gpx` | **canario del umbral de desnivel**: oscila 45 m, que cuenta con 40 y no con 50 |
| `movimiento-justo.gpx` | **canario del filtro de distancia**: va y vuelve 13 m, que cuenta con 12 y no con 15 |

## Por qué hacen falta los dos canarios

Sin ellos, cambiar un umbral **no rompía nada**, y eso se descubrió probándolo.
El motivo es que en una subida monótona el umbral **no cambia el total**, solo
cómo se agrupa: con escalones de 45 m y un umbral de 50, la base no se mueve en
el primer punto pero sí en el segundo (90 ≥ 50), así que el total sale igual.

Lo que el umbral filtra es el **vaivén**, que es para lo que existe. Por eso los
canarios oscilan en vez de subir: así un cambio de umbral los rompe y obliga a
regenerar los esperados y a mirar los números nuevos.

## Al añadir un caso

Añade el fichero **y** su entrada en `esperado.json`, y comprueba que los dos
tests siguen pasando. Un fichero sin valores esperados no prueba nada.
