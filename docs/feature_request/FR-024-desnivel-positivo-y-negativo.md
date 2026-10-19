# FR-024 — Desnivel positivo y negativo por separado

**Fecha de solicitud:** 2026-10-19
**Fecha de implementación:** 2026-10-19
**Estado:** Implementado
**Prioridad:** Media
**Área:** Montañas y rutas (base de datos, backend, frontend, mobile, MCP)

---

## Contexto y motivación

Una ruta guarda hoy un solo número de desnivel, `rutas.desnivel_m`, y en ningún
sitio está escrito si es la subida, la bajada o la suma. El MCP es el único que
lo declara, de pasada, en la descripción de un parámetro: «Desnivel positivo
mínimo en metros». Así que la semántica existe pero vive en un `describe()` de
Zod.

El problema apareció al calibrar FR-023 contra los tracks reales del club:

| track | D+ | D− | inicio → fin | forma |
|---|---|---|---|---|
| antes-de-Papallacta-rocas | 758 | 741 | 3696 → 3729 | ida y vuelta |
| morurco-360-y-cumbre | 872 | 883 | 4014 → 3983 | circular |
| sincholagua-2025 | 1112 | 1244 | 3628 → 3465 | travesía |
| sincholagua-camping | 1038 | **0** | 3827 → 4894 | travesía, solo subida |
| **Travesía Mojanda San Pablo** | **333** | **1248** | 3721 → 2768 | travesía |

*(D+ y D− con el umbral de 40 m que fija FR-023.)*

Las dos últimas filas son las que obligan al cambio. La travesía Mojanda–San
Pablo baja 1248 m y sube 333: con un solo campo la ruta se describe como «333 m
de desnivel», y lo que de verdad define su dificultad —y lo que castiga las
rodillas— es el descenso. `sincholagua-camping` es el caso inverso: sube 1038 y
no baja nada, porque termina en el campamento y el regreso es otro día.

Tres de las cinco muestras son punto a punto, no circulares. En una ruta
circular D+ y D− son casi iguales y un solo número basta; en una travesía no.
Y el club hace travesías.

## Qué se pide

Guardar, mostrar y poder filtrar **desnivel positivo** y **desnivel negativo**
como dos magnitudes independientes, en todas las capas: base de datos, API,
web, móvil y MCP. Es lo que hacen Wikiloc, Komoot, Strava, AllTrails y GaiaGPS,
así que es también lo que un socio espera ver.

## La decisión de fondo: renombrar o añadir

**Opción A — dejar `desnivel_m` como «el positivo» y añadir
`desnivel_negativo_m`.** Cero migración de datos, cero cambios en los filtros
existentes. El coste es permanente: un campo que significa «positivo» sin
decirlo, al lado de otro que sí lo dice. Quien lea el esquema en 2028 tiene que
adivinarlo, igual que ahora.

**Opción B — renombrar a `desnivel_positivo_m` y añadir
`desnivel_negativo_m`.** Toca 16 ficheros y los filtros de la API, pero el
esquema queda explícito y simétrico.

**Recomendación: B.** El argumento decisivo es que **no hay nada en
producción**. Los 70 registros de `rutas` los siembra `V2__seed_data.sql`, no
hay usuarios reales, y el coste del renombrado es una tarde. Dentro de seis
meses con datos reales, la opción A se vuelve permanente por inercia. Es ahora
o nunca.

## Trampas concretas encontradas al reconocer el terreno

**El CHECK actual rechaza el cero.** `V1__schema.sql` tiene
`CHECK (desnivel_m > 0)`. Un D− de cero es legítimo —`sincholagua-camping`— así
que las columnas nuevas van con `>= 0`, no `> 0`. Si se copia el check tal cual,
esa ruta no se puede guardar y el error saldrá como una violación de constraint
sin explicación.

**Nulo y cero no son lo mismo, y aquí importa.** `NULL` es «no lo sabemos»;
`0` es «no baja nada». La travesía al campamento tiene D− igual a 0 de verdad.
Las rutas sembradas que hoy tienen un desnivel sin signo conocido deberían
quedar con el positivo donde está y **el negativo en NULL**, no en 0: afirmar
que no bajan nada sería inventarse un dato.

**Los filtros se quedan sobre el positivo.** `desnivelMin` / `desnivelMax` ya
significan «positivo» según el MCP. Se renombran a `desnivelPositivoMin/Max`
para que lo digan, y **no** se añade filtro por negativo hasta que alguien lo
pida: un filtro que nadie usa es superficie que hay que mantener.

**El móvil no cachea rutas en local.** Verificado: no hay sqflite, hive, isar ni
preferencias en `features/rutas`. El renombrado no arrastra migración de caché
en el cliente.

## Alcance — ficheros que toca

| Capa | Ficheros |
|---|---|
| Base de datos | `V21__desnivel_positivo_y_negativo.sql` (nueva) |
| Backend | `entity/Ruta.java`, `dto/{CreateRuta,UpdateRuta}Request.java`, `dto/{RutaResponse,RutaSummaryResponse}.java`, `controller/RutaController.java`, `service/RutaService.java` |
| Frontend | `types/rutas.ts`, `hooks/use-rutas.ts`, `pages/rutas/{ruta-form-dialog,ruta-detail-dialog,rutas-page}.tsx` |
| Mobile | `features/rutas/domain/models/ruta_model.dart`, `presentation/screens/{ruta_detail_screen,rutas_screen}.dart` |
| MCP | `tools/rutas.ts` |
| Docs | `docs/db/`, `endpoints.md`, `docs/flujos/` donde se describa una ruta |

Dieciséis ficheros y una migración.

## Presentación

Las dos cifras, siempre juntas y con signo, en todas partes: **↑ 333 m ·
↓ 1248 m**.

Donde hay sitio de sobra no hay nada que discutir. Y resulta que tampoco lo hay
donde parecía que sí, porque el terreno es más estrecho de lo que se supone:

- **La tabla de rutas de la web no muestra el desnivel.** Sus columnas son Ruta,
  Lugar, Dificultad, Duración, Estado y Acciones. No hay decisión que tomar.
- **El único sitio apretado es el chip de la tarjeta del móvil**, que hoy pinta
  `'+${ruta.desnivel!.round()} m'` → «+1038 m». Pasa a «+1038 / −0 m», o para
  Mojanda «+333 / −1248 m». Caben.

Se descartó mostrar «el mayor de los dos» cuando no cabe todo. Parece listo y es
peor: el número significaría una cosa en una fila y otra en la siguiente, de
modo que el listado dejaría de servir para comparar rutas de un vistazo, que es
para lo único que se mira un listado.

El formulario pide los dos campos, los dos opcionales. Quien solo sepa uno
rellena uno.

## Relación con FR-023

FR-023 pasa a derivar **las dos** cifras del GPX con el mismo umbral de 40 m, en
vez de una. No cambia nada más de ese FR: el filtro de 12 m para la distancia,
el tiempo total para la duración y la regla de sugerir sin imponer siguen igual.

Conviene hacer **este FR primero**: si FR-023 se implementa antes, hay que
tocar dos veces el formulario y los tipos.

## Decisiones abiertas

1. ¿Se muestra la forma de la ruta (circular / ida y vuelta / travesía)? Sale
   gratis de la distancia entre el primer y el último punto del track, y para
   planificar una salida importa —una travesía necesita transporte en dos
   sitios—. Pero es un campo más y no lo has pedido. **Propuesta: dejarlo
   fuera de este FR** y anotarlo como posible FR-025.
2. ¿Se rellena el negativo de las 70 rutas sembradas? **Propuesta: no.**
   Quedan en NULL y se completan cuando alguien suba un track o edite la ruta.
3. ~~En listados estrechos, ¿positivo siempre, o el mayor de los dos?~~
   **Resuelta.** La pregunta daba por hecho que el listado de la web muestra el
   desnivel, y no lo muestra. El único sitio estrecho es el chip del móvil, y
   ahí caben las dos cifras. Ver «Presentación».

## Registro de implementación

**2026-10-19.** Implementado tal como se aprobó, con la opción B (renombrado).

`V21__desnivel_positivo_y_negativo.sql` quita el `CHECK` viejo, renombra
`desnivel_m` a `desnivel_positivo_m`, añade `desnivel_negativo_m` y pone los dos
checks a `>= 0`. Las dos columnas guardan **magnitudes**: la bajada de la
travesía Mojanda–San Pablo se guarda como 1248, no como −1248. El signo lo pone
la presentación; guardarlo en la base obligaría a recordar el convenio en cada
consulta y a decidir qué hacer con un −0.

Al aplicarla, el backend dejó de arrancar con
`Schema validation: missing column [desnivel_m]`. Es el orden normal de un
renombrado —la base primero, el código detrás— pero conviene saberlo: entre
la migración y el cambio de la entidad, el backend no levanta.

Lo que costó más no fue el renombrado sino **el cero**:

- Los DTOs llevaban `@Min(1)`, igual que el `CHECK` llevaba `> 0`. Las dos
  cosas rechazan un desnivel de bajada de cero, que es legítimo.
- El formulario web hacía `form.desnivelM ? Number(...) : undefined`, y en
  JavaScript `0` es falso, así que un cero se enviaba como «sin dato».
- El móvil pintaba `if (ruta.desnivel != null)` sobre un getter que devolvía
  `desnivelM?.toDouble()`; al pasar a dos cifras, la misma lógica aplicada a
  cada mitad habría ocultado el cero.

Los tres sitios comprueban ahora contra `null`, no contra la veracidad del
número. Hay dos tests dedicados a eso: uno de integración
(`crearRuta_desnivelNegativoCero_seAcepta`, que además verifica que `0` y
`null` no se confunden al serializar) y seis unitarios sobre
`formatearDesnivel`.

El formateo vive en `frontend/src/lib/formatear-desnivel.ts` y en el getter
`Ruta.desnivel` del móvil, con la misma regla: `↑ 333 m · ↓ 1248 m`, y si solo
se conoce una mitad, solo esa.

Los tests de `MountainIntegrationTest` y `RutaServiceTest` construyen los DTOs
con **39 argumentos posicionales**, casi todos `null`. Añadir un componente al
record obliga a insertarlo en la octava posición de dieciséis constructores; a
mano es un desplazamiento garantizado, y con tantos `null` compilaría sin
quejarse asignando valores a los campos equivocados. Se hizo parseando la lista
de argumentos, no con una expresión regular.

El negativo de las 70 rutas sembradas queda en `NULL`, como se acordó.

**Pendiente de este FR:** nada. **Siguiente:** FR-023 pasa a derivar las dos
cifras del GPX en vez de una.
