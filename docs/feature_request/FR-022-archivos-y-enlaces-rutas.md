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

Es texto informativo, así que vive **solo en el frontend**: no hace falta ningún cambio en el
backend ni en la API. Se muestra junto al botón de descarga del documento, no en una ventana
modal que haya que cerrar antes de descargar.

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

**Un track por persona y ruta.** Decidido el 2026-10-11: si alguien sube un segundo track a la
misma ruta, **reemplaza el suyo anterior**, no se acumula. Así la lista tiene una entrada por
persona y se lee de un vistazo. El fichero antiguo se elimina también del almacenamiento, para
no dejar huérfanos en S3.

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

**Propuesta: solo quien haya hecho la ruta de verdad.** Es lo que da sentido al contador —«a
medida que la gente vaya yendo»— y es comprobable con los datos que ya existen, verificado el
2026-10-11:

```
socio → salida_participantes (estado CONFIRMADO, no NO_FUE)
      → salidas (estado REALIZADA, ruta_id = la ruta del documento)
```

La alternativa, dejar que confirme cualquier socio autenticado, es más simple pero vacía el
contador de significado: alguien que nunca pisó la ruta puede avalar su track.

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

## Decisiones abiertas

Resueltas el 2026-10-11: quién puede subir (cualquier socio, los tres tipos), quién puede borrar
(cada uno lo suyo, la directiva cualquiera), visibilidad (inmediata y sin validar, con contador
de confirmaciones), reemplazo del track propio, y el límite de 10 MB.

Queda una de fondo y dos menores.

1. **¿La plantilla de permiso va en la ruta, o en la montaña?** Al aclararse que `PERMISO` es la
   **plantilla oficial en blanco**, y no un documento firmado para una salida concreta, aparece
   un problema de duplicación: una plantilla la emite normalmente la autoridad de un parque o de
   una zona, no una ruta. Si se adjunta por ruta, la misma plantilla se sube tantas veces como
   rutas haya en ese parque, y **cuando cambie habrá que actualizarlas todas una por una** — que
   es justo el caso que motivó abrir la subida.

   | # | Opción | Coste | Qué resuelve |
   |---|--------|-------|--------------|
   | a | **Por ruta**, como el resto de documentos | Cero, es lo ya diseñado | Nada; la duplicación queda |
   | b | **Por montaña**, y las rutas de esa montaña la heredan y la muestran | Bajo — misma estructura colgada de `Mountain` | Se sube una vez y se actualiza una vez |
   | c | Por ruta ahora, y se mueve si molesta | Cero ahora, migración después | Aplaza la decisión |

   **Recomendación: la b**, si las plantillas son por parque o por montaña. Pero esto depende de
   cómo funcionen de verdad los permisos del club —puede que haya rutas con permisos propios—, y
   eso lo sabes tú mejor que el código. Si no está claro, la c es razonable: `Mountain` ya existe
   y mover los documentos después es una migración sencilla.

2. **¿Se eliminan las columnas antiguas de enlace, y cuándo?** La propuesta es dejarlas en V20 y
   borrarlas en una migración posterior tras verificar en staging.

3. **¿La lista de apps recomendadas se fija en el código o se configura?** La propuesta es
   fijarla en el frontend: son cuatro nombres y cambiarlos es un despliegue de frontend.

### Sobre el límite de 10 MB

Confirmado como suficiente, y conviene dejar el número escrito para no volver a dudarlo: un
punto de GPX ocupa entre 100 y 150 bytes de XML, así que 10 MB dan para unos **70.000 a 100.000
puntos**. Grabando uno cada cinco segundos son del orden de **cuatro o cinco días continuos** de
track. Un GPX normal de una salida de un día ronda los 100–500 KB.

## Impacto técnico previsto

| Capa | Qué cambia |
|---|---|
| **BD** | Migración **V20**: columna `tipo` en `ruta_documentos`, tabla `ruta_enlace`, tabla `ruta_documento_confirmacion`, copia de los dos campos de enlace existentes |
| **Backend** | Enum `TipoDocumentoRuta`; `tipo` en `RutaDocumento` y en sus DTO; `gpx` en `DocumentoService`; **`@PreAuthorize` de `subir` abierto a socios y de `eliminar` condicionado al autor**; reemplazo del track propio; confirmar/retirar confirmación con la comprobación de que el socio hizo la ruta; CRUD de `ruta_enlace` y sus endpoints |
| **Frontend** | Selector de tipo al subir; **lista agrupada por tipo, con autor, fecha, tamaño, nº de confirmaciones y descarga por fila**; botón de confirmar/retirar; editor de enlaces; aviso al descargar un GPX |
| **Mobile** | Sin cambios en esta entrega. La ficha de ruta en mobile mostraría los enlaces y documentos cuando se aborde |
| **MCP** | Sin cambios |

No hay cambios en la lógica de inscripción, aprobaciones ni niveles de acceso: este FR solo
añade material adjunto a la ruta.

**Nada de la parte de «varios tracks por persona» requiere cambios de modelo**: `RutaDocumento`
ya es una lista por ruta con `subidoPor` y `createdAt`, y el DTO ya devuelve el nombre de quien
subió cada fichero. Es trabajo de frontend más el campo `tipo`.

## Registro de implementación

_Pendiente. Se rellena al terminar, resumiendo qué se tocó en cada capa._
