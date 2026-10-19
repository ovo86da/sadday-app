# FR-022 — Archivos GPX, PDF de mapas y enlaces de apps en las rutas

**Fecha de solicitud:** 2026-10-11
**Fecha de implementación:** —
**Estado:** Aprobado
**Prioridad:** Media
**Área:** Montañas y rutas (backend, frontend, BD)

---

## Contexto y motivación

Una ruta describe hoy su dificultad, su desnivel y sus peligros, pero no lleva consigo
**el material con el que se navega**. Quien prepara una salida tiene que buscar el track por
fuera de la aplicación, pedirlo por WhatsApp o reconstruirlo. Y cuando alguien sí lo adjunta,
no hay forma de distinguirlo del permiso de acceso ni del mapa.

Lo que existe hoy, verificado sobre el código el 2026-10-11:

| Pieza | Estado actual |
|---|---|
| Subir ficheros a una ruta | **Ya funciona.** `RutaDocumento` → `Documento` → S3, con subida, descarga y borrado |
| PDF | **Ya permitido** en `DocumentoService.ALLOWED_CONTENT_TYPES` |
| GPX | **No permitido.** `ALLOWED_EXTENSIONS = {pdf, doc, docx, xls, xlsx}` |
| Enlaces externos | **Insuficientes.** `Ruta.track_url` y `Ruta.documentacion_url`: un solo enlace cada uno, sin nombre |
| Tipo de documento | **No existe.** Todos los documentos de una ruta son indistinguibles entre sí |

El resultado práctico es que un PDF de mapa ya se puede subir, pero aparece mezclado con el
permiso; que un GPX se rechaza; y que no se pueden guardar a la vez el enlace de Wikiloc, el de
GaiaGPS y el de Komoot, porque hay un único campo para uno.

## Cambios solicitados

### 1. Tipo de documento en la ruta

Se añade un tipo a `RutaDocumento`, para que los documentos de una ruta se puedan separar y
mostrar agrupados:

| Tipo | Para qué |
|---|---|
| `PERMISO` | La **plantilla oficial en blanco** que hay que presentar para acceder (formulario sin rellenar). Cambia con el tiempo, así que importa tener la vigente |
| `MAPA` | PDF con el mapa de la ruta |
| `TRACK_GPX` | Fichero `.gpx` con el track |
| `OTRO` | Cualquier otro adjunto |

Las filas existentes pasan a `PERMISO`: es lo que son hoy —el propio servicio registra
«Documento de permiso subido»— y así la migración no pierde información ni la inventa.

El tipo vive en `RutaDocumento`, que es la asociación entre la ruta y el fichero, **no en
`Documento`**, que es el registro genérico de almacenamiento (proveedor, clave en S3, checksums)
y se comparte con informes, actas y documentos legales.

### 2. Admitir ficheros `.gpx`

Se añade la extensión y el tipo de contenido a `DocumentoService`.

**La aplicación no interpreta el GPX.** No dibuja el track, no calcula desnivel, no lo valida
como XML: lo guarda y lo deja descargar. Esa decisión tiene dos consecuencias que conviene dejar
escritas, porque son la razón de que el cambio sea seguro siendo tan pequeño:

- **No hay riesgo de XXE.** Un GPX es XML, y parsear XML de terceros expone a ataques de
  entidades externas. Al no parsearlo, ese riesgo no existe. Si en el futuro se quisiera dibujar
  el track, habría que revisarlo entonces.
- **Un fichero cualquiera renombrado a `.gpx` no puede hacer daño.** La descarga ya fuerza
  `Content-Disposition: attachment` (`RutaController:210`), así que el navegador nunca lo
  renderiza; y el nombre se sanea a `[a-zA-Z0-9._-]`, así que no puede inyectar nada en la
  cabecera. El riesgo que queda es de almacenamiento, acotado por el límite de 10 MB y por el
  control de rol en la subida.

**Detalle de implementación que importa:** los navegadores no envían un tipo de contenido
consistente para `.gpx`. Según el sistema y el navegador llega `application/gpx+xml`,
`application/octet-stream`, `text/xml` o vacío. Validar solo por tipo de contenido rechazaría
ficheros legítimos. Se valida **por extensión** y se normaliza el tipo almacenado a
`application/gpx+xml`, de modo que la descarga siempre lo sirva igual.

### 3. Varios enlaces con nombre

Se crea una tabla de enlaces por ruta, que sustituye a los dos campos actuales:

```
ruta_enlace
  id        · identificador
  ruta_id   · ruta a la que pertenece
  etiqueta  · nombre visible: «Wikiloc», «GaiaGPS», «Komoot», «Ficha del parque»…
  url       · la dirección
  orden     · para presentarlos en un orden estable
```

La etiqueta es **texto libre, no un catálogo cerrado**. Las aplicaciones de navegación aparecen
y desaparecen, y un catálogo obligaría a una migración cada vez que el club empiece a usar una
nueva. Como contrapartida se pierde la posibilidad de hacer estadísticas por aplicación, que
hoy nadie ha pedido.

Los datos actuales se migran sin pérdida: `track_url` pasa a un enlace etiquetado «Track» y
`documentacion_url` a otro etiquetado «Documentación», solo cuando tienen valor.

**Las dos columnas antiguas no se eliminan en esta migración.** Se quedan sin uso hasta
comprobar en staging que los enlaces migrados son correctos, y se borran en una migración
posterior. Es el mismo patrón que ya sigue el proyecto: `V16__drop_emergency_contact_columns`,
`V17__drop_tipo_sangre` y `V18__drop_token_pii_columns` son todas eliminaciones hechas por
separado, después de que el código dejara de usar las columnas.

### 4. Aviso al descargar un track

Al descargar un documento de tipo `TRACK_GPX`, la interfaz muestra un aviso recomendando con qué
abrirlo:

> Este fichero es un track GPX. Para seguirlo en el campo, cárgalo en una aplicación de
> navegación como **GaiaGPS**, **CalTopo**, **Komoot** o **Wikiloc**.

Se muestra junto al botón de descarga del documento, no en una ventana modal que haya que cerrar
antes de descargar.

**La lista de apps no se compila: se lee de `configuracion_sistema`** (ver «La lista de apps se
configura, no se compila»). Eso obliga a un cambio pequeño en el backend que conviene dejar
explícito, porque no es evidente: los endpoints de configuración que ya existen viven bajo
`/api/v1/admin/**`, y `SecurityConfig:151` los restringe a ADMIN y SECRETARIA. **Un socio normal
no puede leerlos**, así que no sirven para esto.

Se añade un endpoint nuevo que devuelve, a cualquier usuario autenticado, solo las claves de
configuración marcadas como **públicas** —una lista blanca en el código, empezando por
`APPS_RECOMENDADAS_GPX`.

Es una lista blanca y no la configuración completa a propósito: entre las claves actuales están
`MAX_INTENTOS_LOGIN` y `HORAS_BLOQUEO_LOGIN`, y publicar el umbral de bloqueo le dice a quien
intente un ataque de fuerza bruta exactamente por debajo de qué quedarse. El endpoint sirve
además para las claves que el frontend necesite en el futuro, sin añadir uno nuevo cada vez.

### 5. Varios tracks por ruta, de distintas personas

**Decidido el 2026-10-11:** una misma ruta puede tener varios tracks, subidos por personas
distintas, y quien descarga elige de quién. Un track no es un dato objetivo de la ruta: es el
registro de cómo la hizo alguien, con su propia aproximación, sus desvíos y su GPS. Tener varios
es más útil que tener uno «oficial».

Esto **ya está soportado por el modelo actual**, verificado el 2026-10-11:

| Lo que hace falta | Estado |
|---|---|
| Varios documentos por ruta | **Ya funciona.** `RutaDocumento` es una relación de muchos a uno con `Ruta` |
| Saber quién subió cada uno | **Ya existe.** `RutaDocumento.subidoPor` → `Socio`, y el DTO ya devuelve `subidoPorNombre` |
| Listarlos para elegir | **Ya existe.** `listarPorRuta` devuelve la lista completa |
| Fecha de subida | **Ya existe.** `createdAt` |

Es decir: no hace falta ningún cambio de modelo ni de API para esto. Lo que falta es que el
**frontend** muestre la lista agrupada por tipo, con el nombre de quien subió cada fichero y la
fecha, y un botón de descarga por fila — en lugar de tratar los documentos como un bloque
indistinto.

Conviene mostrar también el **tamaño**, que el DTO ya devuelve: quien va a descargar un track en
el campo, con cobertura mala, agradece saber si son 80 KB o 8 MB.

**Límites por socio y ruta, y el reemplazo se pregunta.** Decidido el 2026-10-11 y corregido el
2026-10-18 al probarlo.

| Tipo | Máximo por socio |
|---|---|
| `TRACK_GPX` | **1** — es «mi recorrido»; varios del mismo autor no añaden nada |
| `MAPA` · `PERMISO` · `OTRO` | **3** — caben un mapa general y otro de detalle, o versiones de la plantilla |

El límite es **por socio y no por ruta**: la gracia del contador de votos es comparar el track de
uno con el de otro.

**El reemplazo nunca es automático.** La primera versión sustituía el track anterior en silencio,
y al probarlo pasó justo lo que tenía que pasar: se subió un track nuevo, el anterior desapareció
y quien lo subió no se enteró —creyó que convivían los dos—. Borrar algo que alguien subió sin
decírselo no es aceptable, aunque sea suyo.

Ahora, si ya hay alguno propio del mismo tipo, se pregunta nombrando el fichero y su fecha:

- **Track:** «Ya tienes un track subido para esta ruta, del {fecha} ({nombre}). ¿Deseas
  reemplazarlo?» Si dice que no, se le explica que solo cabe uno por ruta.
- **Con sitio libre:** «Ya tienes N, el más reciente del {fecha}. Aceptar: reemplazar ese.
  Cancelar: añadir este como uno más.»
- **En el límite:** solo cabe reemplazar o cancelar.

El límite lo impone el **backend**, no la interfaz: subir sin indicar `reemplazarId` superando el
máximo devuelve 409, y solo se puede reemplazar un documento propio del mismo tipo. El aviso del
frontend existe para no hacer subir un fichero que va a ser rechazado.

El fichero sustituido se borra también del almacenamiento, y sus votos se van con él.

### 6. Contador de confirmaciones

Decidido el 2026-10-11: un documento se publica **visible de inmediato y sin validar**, y los
socios que van haciendo la ruta pueden **confirmar que el track es válido**. El número de
confirmaciones se muestra junto al documento.

La utilidad es directa: entre tres tracks de una misma ruta, el que lleva ocho confirmaciones de
gente que ha ido es más fiable que el que no tiene ninguna. Y no bloquea a nadie, que era el
punto de abrir la subida.

```
ruta_documento_confirmacion
  ruta_documento_id · documento confirmado
  socio_id          · quién confirma
  created_at        · cuándo
  (único por documento + socio)
```

El único por documento y socio es lo que impide que una persona infle el contador confirmando
varias veces.

#### Quién puede confirmar

**Decidido el 2026-10-13: cualquier socio autenticado, sin restricción.** Se deja abierto de
momento.

Se valoró restringirlo a quien hubiera hecho la ruta, que es comprobable con los datos
existentes —participante no ausente en una `salida` con `estado = REALIZADA` y esa `ruta_id`— y
se descartó por dos razones. La primera es que el estado del participante **no es automático**:
`CONFIRMADO` y `NO_FUE` se marcan al rellenar el informe de la salida, así que exigirlo haría
que el contador se quedara a cero si los informes no se rellenan, fallando en cerrado y en
silencio. La segunda es que **quien hizo la ruta por su cuenta**, fuera de una salida del club,
suele ser justo quien tiene el mejor track, y quedaría excluido sin forma de comprobarlo.

**Lo que esto implica, y conviene tenerlo presente:** el contador mide «cuántos socios avalan
este fichero», no «cuántos lo han caminado». Es una señal más débil, pero sigue siendo útil para
distinguir entre tres tracks de la misma ruta, y se puede endurecer más adelante sin migrar nada
—la tabla de confirmaciones no cambia, solo la comprobación de quién puede escribir en ella.

Cuando se despliegue, el contador estará a cero en todas partes: no hay historial del que tirar.
Es esperable, no un fallo.

#### Consecuencias que conviene dejar escritas

- **Reemplazar un track reinicia su contador.** Las confirmaciones avalaban *ese fichero*, no a
  la persona. Si alguien con doce confirmaciones sube un track nuevo, el contador vuelve a cero:
  nadie ha confirmado todavía el fichero nuevo. Mantenerlas sería presentar como verificado algo
  que nadie ha visto.
- **Quien sube no puede confirmar lo suyo.** Autoavalarse no aporta información y solo infla el
  número.
- **La confirmación se puede retirar.** Si alguien confirma y después descubre que el track
  llevaba por mal sitio, debe poder quitar su confirmación.
- **El contador vale para los tres tipos**, no solo para los tracks: un mapa desactualizado o una
  plantilla de permiso caducada también se detectan así, y no cuesta nada extra.

## Alcance y roles

**Decidido el 2026-10-11:** subir documentos lo puede hacer **cualquier socio autenticado**,
los tres tipos incluidos. Quien tiene el track es quien caminó la ruta, y restringirlo a la
directiva garantiza que no se suba nunca. El permiso sigue la misma lógica por una razón
distinta: es una plantilla que cambia, y quien se entera de que hay una versión nueva debe poder
actualizarla sin esperar a nadie.

| Acción | Quién puede hoy | Tras este FR |
|---|---|---|
| Subir cualquier tipo de documento | ADMIN, SECRETARIA, DIRECTIVO | **cualquier socio autenticado** |
| Descargar documento | cualquier socio autenticado | sin cambios |
| Eliminar **documento propio** | — | **el socio que lo subió** |
| Eliminar documento de otro | ADMIN, SECRETARIA, DIRECTIVO | sin cambios |
| Confirmar un documento | — | ver sección 6 |
| Editar enlaces de una ruta | mismo permiso que editar la ruta | sin cambios |

El borrado queda así: **cada socio borra lo suyo y la directiva borra cualquiera.** Es el mínimo
que evita a la vez el fichero huérfano —alguien sube un track equivocado y no puede quitarlo— y
el borrado ajeno.

## Decisiones tomadas

Todas cerradas entre el 2026-10-11 y el 2026-10-13.

| Decisión | Resolución |
|---|---|
| Quién sube | Cualquier socio autenticado, los tres tipos incluidos |
| Quién borra | Cada socio lo suyo; la directiva, cualquiera |
| Visibilidad | Inmediata y sin validar, con contador de confirmaciones |
| Quién confirma | Cualquier socio autenticado, sin restricción |
| Varios tracks de una persona | El nuevo reemplaza el suyo anterior |
| Límite de tamaño | 10 MB (ver abajo) |
| Dónde va la plantilla de permiso | **Por ruta** |
| Lista de apps recomendadas | **En `configuracion_sistema`**, no en el código |

### La plantilla de permiso va por ruta

**Decidido el 2026-10-13: por ruta**, como el resto de documentos. Se acepta a sabiendas la
duplicación —la misma plantilla subida en varias rutas del mismo parque, y varias
actualizaciones cuando cambie— porque no está claro que todas las plantillas sean por parque:
puede haber rutas con permiso propio. Mover los documentos a `Mountain` más adelante es una
migración sencilla si la duplicación acaba molestando.

### La lista de apps se configura, no se compila

**Decidido el 2026-10-13.** La propuesta inicial era fijarla en el frontend; se descartó por una
objeción correcta: *¿no es más fácil tomarla de configuración que obligar a todos los socios a
actualizar la app cuando se quiera añadir otra?*

Para el frontend web eso no aplica —es un despliegue y el navegador recoge el cambio al recargar,
nadie «actualiza» nada—, pero **para la app móvil sí**: una lista compilada exigiría una versión
nueva en las tiendas. Y el aviso acabará apareciendo también en mobile.

Resuelto con algo que ya existe: la tabla **`configuracion_sistema`** (`clave`, `valor`,
`descripcion`), hoy en uso con cinco claves, con CRUD en `AdminController`
(`GET`/`PUT /admin/config/{clave}`, restringido a ADMIN y SECRETARIA) y pantalla en
`admin-page.tsx`.

Se añade una clave nueva:

```
clave       APPS_RECOMENDADAS_GPX
valor       GaiaGPS, CalTopo, Komoot, Wikiloc
descripcion Apps sugeridas al descargar un track GPX, separadas por comas
```

Así la lista se cambia desde la pantalla de administración **sin despliegue de ningún tipo** —ni
backend, ni frontend, ni actualización de la app— y tanto el frontend como mobile la leen del
mismo sitio.

### Las columnas antiguas de enlace se eliminan en una migración aparte

**Decidido el 2026-10-13.** V20 crea `ruta_enlace` y **copia** a ella el contenido de
`Ruta.track_url` y `Ruta.documentacion_url`, dejando las dos columnas donde están. Una migración
posterior las elimina, una vez verificado en staging que los enlaces copiados son correctos.

El motivo es que una migración de Flyway **ya aplicada no se puede modificar**: su checksum queda
registrado y Flyway se niega a arrancar si cambia. Así que si V20 copiara y borrara a la vez y la
copia saliera mal —un `track_url` con dos URLs pegadas, un texto que no es una URL, un campo con
espacios— el dato original ya no estaría para corregirlo. Habría que recuperarlo de una copia de
seguridad.

Separándolo, entre V20 y la eliminación hay una ventana en la que el dato vive en los dos sitios
y se puede comparar. Cuesta una migración más y no cuesta nada de tiempo de desarrollo.

Es además el patrón que el proyecto ya sigue: `V16__drop_emergency_contact_columns`,
`V17__drop_tipo_sangre` y `V18__drop_token_pii_columns` son las tres eliminaciones separadas,
hechas después de que el código dejara de usar esas columnas.

**Durante esa ventana, el backend lee solo de `ruta_enlace`.** Las columnas antiguas quedan
muertas pero presentes, que es justo lo que las hace servir de red.

### Sobre el límite de 10 MB

Confirmado como suficiente, y conviene dejar el número escrito para no volver a dudarlo: un
punto de GPX ocupa entre 100 y 150 bytes de XML, así que 10 MB dan para unos **70.000 a 100.000
puntos**. Grabando uno cada cinco segundos son del orden de **cuatro o cinco días continuos** de
track. Un GPX normal de una salida de un día ronda los 100–500 KB.

## Impacto técnico previsto

| Capa | Qué cambia |
|---|---|
| **BD** | Migración **V20**: columna `tipo` en `ruta_documentos`, tabla `ruta_enlace`, tabla `ruta_documento_confirmacion`, copia de los dos campos de enlace existentes, clave `APPS_RECOMENDADAS_GPX` en `configuracion_sistema` |
| **Backend** | Enum `TipoDocumentoRuta`; `tipo` en `RutaDocumento` y en sus DTO; `gpx` en `DocumentoService`; **`@PreAuthorize` de `subir` abierto a socios y de `eliminar` condicionado al autor**; reemplazo del track propio; confirmar y retirar confirmación; CRUD de `ruta_enlace` y sus endpoints; **endpoint de configuración pública con lista blanca** |
| **Frontend** | Selector de tipo al subir; **lista agrupada por tipo, con autor, fecha, tamaño, nº de confirmaciones y descarga por fila**; botón de confirmar/retirar; editor de enlaces; aviso al descargar un GPX, con las apps leídas de `configuracion_sistema` |
| **Mobile** | Sin cambios en esta entrega. La ficha de ruta en mobile mostraría los enlaces y documentos cuando se aborde |
| **MCP** | Sin cambios |

No hay cambios en la lógica de inscripción, aprobaciones ni niveles de acceso: este FR solo
añade material adjunto a la ruta.

**Nada de la parte de «varios tracks por persona» requiere cambios de modelo**: `RutaDocumento`
ya es una lista por ruta con `subidoPor` y `createdAt`, y el DTO ya devuelve el nombre de quien
subió cada fichero. Es trabajo de frontend más el campo `tipo`.

## Fuera de alcance

**Extraer los datos de la ruta del propio GPX** —distancia, desnivel, duración— se planteó el
2026-10-13 y **no entra en este FR**. Es valioso y se especifica aparte, porque obliga a
*interpretar* el fichero y eso revierte la decisión que mantiene este cambio pequeño y seguro
(ver sección 2). Queda anotado como `F-04` en la lista de pendientes, para su propio FR.

## Registro de implementación

**Fecha:** 2026-10-13

### Base de datos — `V20__ruta_documentos_tipo_y_enlaces.sql`

Columna `tipo` en `ruta_documentos` (`VARCHAR(20)` con `CHECK`, siguiendo a
`rutas.estado` de V7 y no un enum nativo, para que añadir un tipo no exija
tocar la base). Las filas existentes pasan a `PERMISO`. Tablas `ruta_enlace` y
`ruta_documento_confirmacion`, esta última con clave primaria compuesta
(documento, socio) que es lo que impide confirmar dos veces. Clave
`APPS_RECOMENDADAS_GPX` en `configuracion_sistema`.

Las columnas `track_url` y `documentacion_url` se copian y **no** se borran.
La copia se probó con las cinco formas en que puede estar el campo —normal, con
espacios alrededor, cadena vacía, solo espacios y nulo— verificando que recorta
la segunda y descarta las tres últimas.

### Backend

`TipoDocumentoRuta`, `RutaEnlace`, `RutaDocumentoConfirmacion` con su
`@EmbeddedId`, sus repositorios y DTOs. `gpx` en `DocumentoService`, con
normalización del content-type porque los navegadores no coinciden en lo que
envían para un `.gpx`. Subida abierta a `isAuthenticated()`; borrado también,
con la comprobación dentro del método porque depende de quién subió el fichero.
Un `TRACK_GPX` tiene que ser un `.gpx`, y un segundo track del mismo socio
reemplaza el suyo anterior incluyendo el objeto del storage. Confirmar y
retirar confirmación. CRUD de enlaces. `GET /v1/config/publicas` con lista
blanca.

**Lo que el FR no había previsto:** mobile y el frontend siguen escribiendo
`trackUrl` y `documentacionUrl` desde su formulario de rutas. Si el backend
leyera solo de `ruta_enlace`, lo que escribieran se guardaría en la columna sin
aparecer en ningún sitio. `RutaEnlaceService.sincronizarLegacy` mantiene ambas
representaciones alineadas durante la ventana de transición, y solo toca los
enlaces con las etiquetas que usó la migración.

899 tests (890 + 9 nuevos sobre el comportamiento añadido).

### Frontend

Lista agrupada por tipo con autor, fecha, tamaño y confirmaciones, descarga por
fila, selector de tipo que ajusta el `accept` del input, botón de confirmar y
aviso bajo el grupo de tracks con las apps leídas de configuración.

Dos correcciones de fondo: la sección ya no depende de `requierePermisos` —un
track es útil en cualquier ruta, y antes no se veía si la ruta no exigía
permiso—, y desaparece `canManageDocs`, que duplicaba en el cliente una regla
de autorización que ahora resuelve el backend por fichero.

`documentosPermiso` pasa a `documentos`, verificando antes que mobile no
consume ese campo.

### Mobile y MCP

Sin cambios, como preveía el FR. Mobile sigue funcionando porque `trackUrl`
continúa operativo gracias a la sincronización.

### Pendiente de esta entrega

El **editor** de enlaces no entra: el frontend los muestra y el backend expone
el CRUD completo, pero la pantalla para añadirlos y editarlos a mano queda
para una segunda vuelta. Mientras, los enlaces se gestionan por los campos
`trackUrl` y `documentacionUrl` del formulario de ruta, que siguen
sincronizándose.
