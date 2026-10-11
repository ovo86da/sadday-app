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

## Alcance y roles

| Acción | Quién puede hoy | Quién debería poder |
|---|---|---|
| Subir documento a una ruta | ADMIN, SECRETARIA, DIRECTIVO | **Decisión abierta** (ver abajo) |
| Descargar documento | cualquier socio autenticado | sin cambios |
| Eliminar documento | ADMIN, SECRETARIA, DIRECTIVO | sin cambios |
| Editar enlaces de una ruta | — (campos de la ruta, mismo permiso que editarla) | sin cambios |

## Decisiones abiertas

Estas cuatro no bloquean empezar, pero hay que responderlas antes de cerrar el FR:

1. **¿Quién puede adjuntar un track?** Hoy subir documentos es solo de ADMIN, SECRETARIA y
   DIRECTIVO. Un track lo suele tener quien hizo la ruta, que puede ser un socio cualquiera o un
   Guía. Abrirlo a más roles es coherente con el propósito, pero habría que decidir si el
   documento queda visible de inmediato o pendiente de revisión, como ya ocurre con las rutas
   (`EstadoRuta`).
2. **¿Varios GPX por ruta, o uno solo?** Una ruta puede tener variantes —aproximación distinta,
   descenso alternativo— y cada una su track. Permitir varios no cuesta nada; restringir a uno
   obliga a decidir qué pasa al subir el segundo.
3. **¿Se eliminan las columnas antiguas, y cuándo?** La propuesta es dejarlas en V20 y borrarlas
   en una migración posterior tras verificar en staging.
4. **¿La lista de apps recomendadas se fija en el código o se configura?** La propuesta es fijarla
   en el frontend: son cuatro nombres y cambiarlos es un despliegue de frontend, no vale la pena
   una tabla de configuración.

## Impacto técnico previsto

| Capa | Qué cambia |
|---|---|
| **BD** | Migración **V20**: columna `tipo` en `ruta_documentos`, tabla `ruta_enlace`, copia de los dos campos de enlace existentes |
| **Backend** | Enum `TipoDocumentoRuta`; `tipo` en `RutaDocumento` y en sus DTO; `gpx` en `DocumentoService`; CRUD de `ruta_enlace`; endpoints de enlaces en `RutaController` |
| **Frontend** | Selector de tipo al subir; documentos agrupados por tipo en la ficha de la ruta; editor de enlaces; aviso al descargar un GPX |
| **Mobile** | Sin cambios en esta entrega. La ficha de ruta en mobile mostraría los enlaces y documentos cuando se aborde |
| **MCP** | Sin cambios |

No hay cambios en la lógica de inscripción, aprobaciones ni niveles de acceso: este FR solo
añade material adjunto a la ruta.

## Registro de implementación

_Pendiente. Se rellena al terminar, resumiendo qué se tocó en cada capa._
