# FR-025 — Adjuntar y analizar el GPX desde el móvil

**Fecha de solicitud:** 2026-10-19
**Fecha de implementación:** —
**Estado:** Implementado
**Prioridad:** Media
**Área:** Montañas y rutas (mobile, posiblemente backend)

---

## Contexto y motivación

FR-022 dejó la subida de documentos a una ruta **solo en la web**, y FR-023
el autorrellenado desde el GPX **también solo en la web**. Verificado al
intentar añadir allí el aviso de revisión que se pidió el 2026-10-19:
`mobile/lib` no tiene ninguna referencia a GPX, y la pantalla de rutas no está
entre las que suben ficheros. El móvil no puede adjuntar un track, así que no
hay nada que rellenar ni de qué avisar.

Esto importa porque el móvil es donde está la gente cuando vuelve de una ruta,
con el track recién grabado en el teléfono. Es el momento en que proponer la
ruta cuesta menos esfuerzo, y hoy es justo donde no se puede.

## Qué se pide

1. **Adjuntar documentos a una ruta desde el móvil**, con los mismos tipos y
   límites que la web: 1 track GPX, 3 mapas, 3 plantillas de permiso, 3 otros,
   máximo 10 MB, y el diálogo de reemplazo cuando ya hay uno propio del mismo
   tipo. `file_picker` ya está en `pubspec.yaml`, así que la pieza existe.
2. **Analizar el GPX al adjuntarlo** y rellenar distancia, desnivel positivo,
   desnivel negativo y duración, con las mismas reglas de FR-023: filtro de
   12 m, umbral de 40 m, tiempo total, sugerir sin imponer y no pisar lo
   escrito.
3. **El aviso de revisión**, el mismo que la web: los datos se rellenaron
   solos, hay que validarlos, y los campos siguen siendo editables.

## La decisión de fondo: dónde vive el cálculo

Con dos clientes, la fórmula existiría dos veces. Eso no es un detalle: dos
implementaciones de la misma fórmula se desincronizan, y el síntoma es que la
web y el móvil den desniveles distintos para el mismo fichero, que es peor que
no tener la función.

| # | Dónde | Ventajas | Inconvenientes |
|---|---|---|---|
| **a** | Repetir el cálculo en **Dart** | Sin red: el teléfono ya tiene el fichero. Mantiene la propiedad de FR-023 —el servidor no interpreta XML de terceros—. Funciona sin cobertura, que en montaña no es teórico | Dos implementaciones que pueden divergir |
| **b** | Un **endpoint en el backend** que analiza y devuelve las métricas, usado por móvil y web | Una sola implementación | Reintroduce superficie de XXE, hay que endurecer el lector XML. Y en el flujo de proponer ruta el fichero **aún no se ha subido**, así que habría que subir hasta 10 MB solo para ver unos números, por datos móviles y antes de guardar nada |
| c | Endpoint solo para el móvil, la web sigue en el navegador | — | Lo peor de las dos: dos implementaciones **y** la superficie de XXE |

**Recomendación: la a, con una red de seguridad que hoy no existe.**

La duplicación es el precio de no subir 10 MB por datos móviles para rellenar
un formulario, y de que funcione sin cobertura. Lo que hay que atacar no es la
duplicación en sí, sino la deriva: **una suite de conformidad compartida**, con
ficheros de prueba y sus valores esperados, que tanto `vitest` como
`flutter test` tengan que satisfacer. Si alguien toca un umbral en un lado y no
en el otro, falla la CI en vez de fallar en silencio.

Un matiz honesto sobre el argumento de seguridad: el servidor **ya recibe y
guarda** el GPX desde FR-022. La propiedad que FR-023 preserva no es que el
fichero no llegue al servidor, es que el servidor no lo **parsea**. Sigue
valiendo la pena —el parseo es donde está el XXE— pero no es «el servidor no lo
toca».

### Los ficheros de conformidad tienen que ser sintéticos

Los seis tracks con los que se calibró FR-023 **no están en el repo**: están
excluidos en `.git/info/exclude`, y debe seguir así. Son rutas reales del club,
con coordenadas y horas de personas concretas, y el repo es público a propósito.

Así que la suite compartida usa **ficheros sintéticos** con valores calculables
a mano, no los tracks reales. Ya hay uno que sirve de modelo:
`frontend/e2e/fixtures/ruta-prueba.gpx`, tres puntos de 4000 a 4120 m sin marcas
de tiempo, que da 0,3 km y 120 m de subida. Harían falta cuatro o cinco que
cubran los casos que ya sabemos que importan:

- bajada de cero (termina más alto de donde empezó)
- predominio de bajada (una travesía)
- oscilaciones por debajo del umbral de 40 m, que no deben sumar
- movimientos por debajo de 12 m, que no deben alargar la distancia
- sin marcas de tiempo
- más de 24 h, para el reparto entre días y horas

Los valores esperados van en un JSON junto a los ficheros, leído por los dos
lenguajes. Los tracks reales siguen sirviendo para calibrar a mano, fuera del
repo.

## Lo que NO se pide

**El mapa.** Igual que en FR-022: el móvil guarda el fichero y deja
descargarlo, no dibuja la traza. Si alguna vez se quiere, es otro FR y otra
dependencia.

**Recalcular los datos de rutas que ya existen.** Hoy el cálculo solo ocurre al
adjuntar un track en el formulario. Un recálculo masivo sobre las rutas ya
guardadas es otra cosa, y tendría que decidir qué hacer cuando el track dice
algo distinto de lo que un socio escribió a mano.

## Decisiones abiertas

1. **¿El límite de puntos?** La web muestrea por encima de 20.000 para no
   bloquear la interfaz. Un teléfono tiene menos margen, así que el tope
   probablemente deba ser menor; conviene medirlo en un dispositivo real antes
   de fijar el número, no copiarlo de la web.
2. **¿Se analiza en un `Isolate`?** Parsear decenas de miles de puntos en el
   hilo de interfaz congela la pantalla. Un `Isolate` lo evita y es la forma
   idiomática en Flutter, pero complica el código. Propuesta: medir primero con
   el fichero más grande que haya (709 KB, 7174 puntos) y decidir con el dato.
3. **¿Entra el móvil en el e2e?** Hoy la suite de Playwright solo cubre la web.
   Los tests de widget de Flutter cubren piezas, no el flujo. Queda fuera de
   este FR, pero conviene saber que el flujo del móvil no tendrá red de
   seguridad de extremo a extremo.

## Impacto técnico previsto

| Capa | Qué |
|---|---|
| Mobile | Selector de ficheros y subida en la pantalla de rutas; analizador de GPX en Dart; aviso de revisión; diálogo de reemplazo |
| Tests | Suite de conformidad compartida: ficheros sintéticos + JSON de valores esperados, consumida por `vitest` y por `flutter test` |
| Frontend | Nada de comportamiento: solo pasar a leer los valores esperados del JSON compartido |
| Backend | Nada, si se elige la opción a |

## Registro de implementación

**2026-10-19.** Implementado el **flujo de proponer ruta** con la opción a: el
análisis en Dart, no en el backend. Falta la gestión de documentos en la
pantalla de detalle, que es la otra mitad y va aparte (ver «Lo que queda» al
final).

### La red contra la deriva, primero

`test-fixtures/gpx/` con seis ficheros sintéticos y `esperado.json`, leídos por
`frontend/src/lib/analizar-gpx.conformidad.test.ts` y por
`mobile/test/unit/analizar_gpx_test.dart`. Los valores esperados se generaron
con la implementación de TypeScript —que ya estaba verificada contra los seis
tracks reales— y se comprobaron a mano uno por uno: `subida-simple` son tres
tramos de 300 m, o sea 900 m y D+ 900; `pasos-cortos` agrupa 99 pasos de 5 m en
tramos de 15, o sea 495 m; `varios-dias` son 36 h, o sea 2 días.

La implementación en Dart **coincidió con la de TypeScript en los seis a la
primera**. Los dos tests comprueban además que haya un fichero por cada entrada
del JSON y al revés: sin eso, un `for` sobre una lista vacía pasaría en verde
sin comprobar nada, que es el modo de fallo que más cuesta detectar.

Son sintéticos porque los tracks reales **no están en el repositorio** —
excluidos en `.git/info/exclude`, y deben seguir fuera: son rutas reales con
coordenadas y horas de personas, y el repo es público.

### El analizador en Dart

`mobile/lib/features/rutas/domain/analizar_gpx.dart`, con las mismas constantes
—12 m de movimiento mínimo, 40 m de umbral— y un aviso en la cabecera de que si
se cambia un número hay que cambiarlo también en el de TypeScript.

Dos diferencias deliberadas con la web:

- **El tope de puntos es 8.000, no 20.000.** Un teléfono tiene menos margen.
  Era una decisión abierta del FR y queda tomada por prudencia, **sin medirla en
  un dispositivo real**: conviene hacerlo antes de darla por buena.
- Se usa el paquete `xml`, declarado ahora como dependencia **directa**. Ya
  estaba en el árbol como transitiva, y usar una transitiva es pedir que
  desaparezca. La alternativa —parsear con expresiones regulares— no añadía
  dependencia pero haría que las dos implementaciones se comportaran distinto
  ante un fichero raro, que es justo lo que la suite de conformidad no puede
  detectar porque sus ficheros están bien formados.

No se usa un `Isolate`. Era la otra decisión abierta, y queda **sin resolver a
propósito**: la propuesta del FR era medir primero, y no se ha medido en un
dispositivo real.

### El flujo de proponer ruta

`crearRuta` devolvía `void` y ahora devuelve el id: hacía falta para colgarle
los documentos justo después, porque al elegir el fichero la ruta todavía no
existe. Si una subida falla **no se tira la ruta** —ya está creada, y perderla
por un fichero sería peor—: se avisa de cuáles no subieron.

El aviso de revisión es el mismo que en la web, arriba y con peso visual, y
dice que los campos se pueden editar. Incluye el matiz del track planificado y
el caso de la unidad de duración que no encaja —días en alpinismo, horas en el
resto—, que no se rellena y se explica en vez de descartarse en silencio.

### Segunda entrega: la gestión de documentos en el detalle

**2026-10-20.** Completada la otra mitad, que era más trabajo que la primera.
`ruta_detail_screen.dart` ya lista los documentos agrupados por tipo, los
descarga, borra los propios, lleva el contador de confirmaciones y tiene el
diálogo de reemplazo.

**Los permisos no se calculan en el móvil, se acatan.** El backend ya devuelve
`puedoEliminar` por documento, así que el cliente solo decide si dibuja el
botón. La comprobación de verdad está en el endpoint, y ocultar el botón es
cortesía: quitarlo del cliente no abriría nada. El modelo **falla cerrado** — si
el servidor dejara de mandar ese campo, el botón desaparece en vez de aparecer
para todo el mundo, y hay un test que lo fija.

**La descarga pasa por dio y acaba en el menú de compartir.** Por dio y no por
una URL abierta en el navegador del sistema, porque una navegación no lleva la
cabecera `Authorization` y el backend responde 401 — el mismo fallo que estuvo
oculto en la web desde el commit inicial. Y en el menú de compartir porque un
GPX que no se puede abrir en GaiaGPS o Komoot no sirve de nada; para un track
el texto del menú lo sugiere.

**La sección se muestra siempre**, también en rutas que no exigen permisos. En
la web estuvo condicionada a `requierePermisos` y por eso un track no se veía en
la mayoría de rutas.

**El diálogo de reemplazo ofrece tres salidas** —reemplazar, añadir otro,
cancelar— por lo mismo que en la web: un Aceptar/Cancelar obliga a adivinar cuál
es cuál. Avisa además de los votos que se pierden al reemplazar.

**El contador lleva texto**, no solo un número: «3 socio(s) confirman que
sirve». Un número suelto no dice qué se está confirmando.

Verificado el contrato contra el backend en marcha: los once campos que el móvil
lee existen con ese nombre, y `puedoEliminar` llega en `false` para un socio
sobre un documento de otra persona. Si un nombre estuviera mal, el modelo caería
en su valor por defecto y el botón no aparecería nunca **sin que nada fallara**;
por eso se comprobó contra la API y no solo con tests.

### Lo que queda, y no se puede hacer desde aquí

**Medir en un dispositivo real** el tope de puntos (hoy 8.000, puesto por
prudencia) y si hace falta un `Isolate`, con el fichero más grande disponible
(709 KB, 7174 puntos). Es lo único de este FR resuelto a ojo y no con un dato.

**El flujo del móvil sigue sin red de extremo a extremo**, como anticipaba la
decisión abierta 3: los tests de widget cubren piezas, no el recorrido.
