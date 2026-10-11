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
| `PERMISO` | Autorizaciones de acceso, permisos de parque, documentación oficial |
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

## Alcance y roles

**Decidido el 2026-10-11:** subir tracks y mapas lo puede hacer **cualquier socio
autenticado**. Quien tiene el track es quien caminó la ruta, y restringirlo a la directiva
garantiza que no se suba nunca.

| Acción | Quién puede hoy | Tras este FR |
|---|---|---|
| Subir `TRACK_GPX` o `MAPA` | ADMIN, SECRETARIA, DIRECTIVO | **cualquier socio autenticado** |
| Subir `PERMISO` | ADMIN, SECRETARIA, DIRECTIVO | **pendiente de decidir** (ver abajo) |
| Descargar documento | cualquier socio autenticado | sin cambios |
| Eliminar documento | ADMIN, SECRETARIA, DIRECTIVO | **pendiente de decidir** (ver abajo) |
| Editar enlaces de una ruta | mismo permiso que editar la ruta | sin cambios |

## Decisiones abiertas

Las tres primeras surgen precisamente de abrir la subida a todos los socios, así que conviene
cerrarlas antes de implementar.

1. **¿El permiso también lo sube cualquiera?** La decisión fue «el track y los mapas», y un
   permiso no es lo mismo: es un documento oficial de acceso a un parque o a una propiedad, y lo
   tramita la secretaría. La propuesta es **dejar `PERMISO` restringido** a ADMIN, SECRETARIA y
   DIRECTIVO, y abrir solo `TRACK_GPX`, `MAPA` y `OTRO`. Si se prefiere abrirlo todo, basta
   decirlo.

2. **¿Quién puede borrar?** Hoy borrar es solo de la directiva. Con la subida abierta aparecen
   dos casos nuevos: un socio que subió un track equivocado debería poder quitarlo, y **no**
   debería poder quitar el de otro. La propuesta es: **cada socio borra lo suyo; la directiva
   borra cualquiera.** Es el mínimo que evita tanto el fichero huérfano como el borrado ajeno.

3. **¿Un track visible al instante, o revisado antes?** Esta es la que más importa, y no es una
   cuestión de orden sino de seguridad: **un track equivocado lleva gente por donde no debe
   ir.** Con la subida abierta, cualquier socio publica material de navegación que todos los
   demás ven. Las opciones:

   | # | Opción | Coste | Qué resuelve |
   |---|--------|-------|--------------|
   | a | **Visible al instante.** Queda registrado quién lo subió y cuándo, y cualquiera puede avisar | Cero | Nada preventivo; confía en que el autor esté identificado |
   | b | **Pendiente de revisión** antes de ser visible, como ya ocurre con las rutas (`EstadoRuta`) | Medio — estado nuevo, pantalla de revisión, notificación | Lo evita, a cambio de que nada se publique hasta que alguien lo apruebe |
   | c | **Visible al instante, marcado «sin revisar»** hasta que un Guía o la directiva lo confirme | Bajo — un booleano y una etiqueta | Útil de inmediato, y quien descarga sabe lo que tiene en la mano |

   **Recomendación: la opción c.** Mantiene la utilidad de que cualquiera suba —que es el punto
   de abrirlo— sin que un track sin revisar se presente con la misma autoridad que uno
   verificado. La b es más segura pero reintroduce el cuello de botella que esta decisión quería
   eliminar.

4. **¿Varios tracks de la misma persona?** Si alguien sube un segundo track a la misma ruta,
   ¿se añade o reemplaza el suyo anterior? La propuesta es **añadir**, con la fecha
   distinguiéndolos: una ruta repetida dos años después da un track distinto y ambos valen.

5. **¿Algún límite?** Con la subida abierta a todos los socios, ~N personas pueden adjuntar
   ficheros de hasta 10 MB a cualquier ruta. No es urgente, pero conviene decidir si se pone un
   máximo por ruta o por socio antes de que el bucket crezca sin control.

6. **¿Se eliminan las columnas antiguas, y cuándo?** La propuesta es dejarlas en V20 y borrarlas
   en una migración posterior tras verificar en staging.

7. **¿La lista de apps recomendadas se fija en el código o se configura?** La propuesta es fijarla
   en el frontend: son cuatro nombres y cambiarlos es un despliegue de frontend, no vale la pena
   una tabla de configuración.

## Impacto técnico previsto

| Capa | Qué cambia |
|---|---|
| **BD** | Migración **V20**: columna `tipo` en `ruta_documentos`, tabla `ruta_enlace`, copia de los dos campos de enlace existentes |
| **Backend** | Enum `TipoDocumentoRuta`; `tipo` en `RutaDocumento` y en sus DTO; `gpx` en `DocumentoService`; **cambio de `@PreAuthorize` en `subir`**; CRUD de `ruta_enlace`; endpoints de enlaces en `RutaController` |
| **Frontend** | Selector de tipo al subir; **lista de documentos agrupada por tipo, con quién lo subió, la fecha y el tamaño, y descarga por fila**; editor de enlaces; aviso al descargar un GPX |
| **Mobile** | Sin cambios en esta entrega. La ficha de ruta en mobile mostraría los enlaces y documentos cuando se aborde |
| **MCP** | Sin cambios |

No hay cambios en la lógica de inscripción, aprobaciones ni niveles de acceso: este FR solo
añade material adjunto a la ruta.

**Nada de la parte de «varios tracks por persona» requiere cambios de modelo**: `RutaDocumento`
ya es una lista por ruta con `subidoPor` y `createdAt`, y el DTO ya devuelve el nombre de quien
subió cada fichero. Es trabajo de frontend más el campo `tipo`.

## Registro de implementación

_Pendiente. Se rellena al terminar, resumiendo qué se tocó en cada capa._
