# FR-028 — Que el nivel del socio se valide de verdad al inscribirse

**Fecha de solicitud:** 2026-10-27
**Fecha de implementación:** —
**Estado:** Implementado
**Prioridad:** Alta
**Área:** Salidas / Montañas y rutas / Reglamento de seguridad

---

## Lo que se creía que pasaba, y lo que pasa

La idea con la que se construyó el sistema era: *una ruta tiene una dificultad
según las tablas; con esa dificultad y el nivel del socio, puede inscribirse o
no a una salida para esa ruta.*

**Eso no ocurre.** Verificado en el código el 2026-10-27:

**1. La matriz no la lee nadie.** `acceso_ruta_por_nivel` —la dificultad máxima
que permite cada nivel— solo se usa en `MountainService`, y solo para mostrarla
y actualizarla. Ningún flujo la consulta para permitir o impedir nada.

**2. El nivel mínimo de la salida se teclea a mano.** No se deriva de la ruta:

```java
resolverNivelMinimo(request.nivelMinimoRequeridoId())   // viene de la petición
```

Nadie garantiza que corresponda a la dificultad de la ruta. Se puede crear una
salida a una ruta de hielo y ponerle nivel 1. Y es **opcional**: si se deja
vacío, no hay nada contra lo que comparar.

**3. Lo único que hay es una marca, y no impide nada.**

```java
boolean nivelInsuficiente = nivelMinimo != null
        && (nivelSocio == null || nivelSocio.getNivel() < nivelMinimo.getNivel());
```

Al inscribirse se comprueba que las inscripciones estén abiertas, que no esté ya
inscrito, que haya cupo, que su acceso esté activo y que esté al día de cuotas.
**El nivel no se mira.** La marca se le enseña a quien aprueba, y ya.

## Lo que se pide

Que la validación **venga de la tabla**, y que un directivo pueda aprobar
igualmente si lo decide — pero de forma explícita y registrada, no por omisión.

1. El nivel mínimo de la salida **se deriva de la ruta**, no se teclea.
2. Al aprobar una inscripción, el sistema **compara la dificultad de la ruta con
   lo que permite el nivel del socio**, usando la matriz.
3. Si no da, la inscripción **no se aprueba sin más**: hace falta que un
   directivo lo apruebe **a sabiendas**, dejando constancia.

## El obstáculo: la matriz solo cubre alpinismo

Esto hay que resolverlo antes de construir nada, y no es un detalle:

| Tipo de ruta | Sus escalas | ¿Las tiene la matriz? |
|---|---|---|
| **ALPINISMO** | IFAS, roca, hielo, compromiso, Yosemite, técnico y físico Sadday | **Las siete, exactas** |
| **ESCALADA** | roca, tipo de escalada, cintas, altura | Solo **roca** |
| **TREKKING** | dificultad de senderismo | **Ninguna** |
| **CICLISMO** | tipo de bici, dificultad técnica, superficie, ciclabilidad | **Ninguna** |
| **INTEGRAL** | las de la disciplina dominante (`dificultadMaxTipo`) | Según cuál sea |

La matriz se escribió para alpinismo y encaja exactamente con sus siete escalas.
Para una ruta de trekking **no hay nada que comparar**.

Así que «conectar la matriz» sin más dejaría la validación **ausente en dos de
los cuatro tipos**, sin avisar. Y decir «el sistema valida el nivel» cuando solo
lo hace en alpinismo es peor que no decir nada, porque la gente confía.

**Propuesta: extender la matriz** con una columna para senderismo y otra para
ciclismo. Cuesta una migración, dos columnas en la pantalla de umbrales, y que
la directiva rellene seis niveles × dos escalas una vez. A cambio, la frase «el
sistema valida el nivel» es verdad para todo.

La alternativa —validar solo donde la matriz sabe— es más barata y **deja un
agujero que nadie ve**. Se descarta salvo que el club prefiera lo contrario.

## Cómo debe comportarse, en concreto

**No se bloquea la inscripción del socio.** Puede apuntarse; lo que cambia es
qué pasa después. Rechazarle en la puerta con un mensaje de máquina es peor
trato del que da hoy el club, y además esconde el caso de quien tiene criterio
para ir aunque su nivel diga otra cosa.

**La aprobación es la que se detiene.** Quien aprueba ve que la ruta excede lo
que permite el nivel del socio, **y en qué escala concreta** —«la ruta es WI004
y su nivel llega a WI001»—, no un «nivel insuficiente» a secas. Sin saber en qué
se queda corto no se puede decidir.

**La excepción se registra.** Si el directivo aprueba igualmente, se guarda quién
lo aprobó, cuándo y **por qué**. El motivo es obligatorio: una excepción sin
motivo es indistinguible de un descuido, y es justo lo que luego hay que poder
revisar.

**Y queda visible.** En la lista de participantes se ve quién entró por
excepción. No para señalar a nadie: para que el jefe de salida sepa a quién
lleva, que es información de seguridad.

## Qué pasa cuando no hay ruta

`salida.ruta_id` es **opcional**: hay salidas sin ruta asociada. Sin ruta no hay
dificultad que comparar.

Propuesta: en ese caso **se mantiene el campo manual** de nivel mínimo, que es lo
que hay hoy. El campo no desaparece, cambia de papel: deja de ser lo normal y
pasa a ser el recurso para las salidas sin ruta.

## Decisiones

1. ~~¿Se extiende la matriz a senderismo y ciclismo?~~ **Decidido el 2026-10-27:
   sí se extiende.** Con eso la frase «el sistema valida el nivel» es verdad
   para los cuatro tipos y no solo para alpinismo. Implica que la directiva
   rellene seis niveles × dos escalas una vez, y que FR-026 cubra también esas
   dos columnas: cambiarlas es cambiar un umbral de acceso como cualquier otro.
2. **¿Quién puede aprobar la excepción?** Propuesta: **los mismos que aprueban
   inscripciones hoy.** Añadir un cargo especial para esto multiplicaría las
   reglas sin que nadie lo haya pedido.
3. **¿Qué pasa con las salidas ya creadas** cuyo nivel mínimo se tecleó a mano y
   no corresponde a la ruta? Propuesta: **no tocarlas.** Se derivan solo las
   nuevas; las existentes conservan lo que tienen, y la pantalla avisa si el
   valor manual no coincide con lo que daría la ruta.
4. **¿La derivación es automática o sugerida?** Propuesta: **automática, y
   editable hacia arriba.** Exigir más nivel del que pide la ruta es decisión
   legítima del que organiza; exigir menos es justo lo que esto evita.

**Las tres de arriba (2, 3 y 4) se dan por decididas tal como están propuestas**,
salvo objeción al revisar. Son operativas y de bajo riesgo; la que importaba era
la primera.

## Relación con FR-026

FR-026 pone doble aprobación para cambiar la matriz. Hasta ahora protegía una
tabla **que no tenía efecto sobre nada**. Con este FR la matriz pasa a decidir
quién entra a qué salida, y FR-026 pasa a proteger una puerta de verdad.

Conviene hacer **FR-026 primero**: abrir la puerta antes de poner la cerradura
deja una ventana en medio.

## Impacto técnico previsto

| Capa | Qué |
|---|---|
| Base de datos | Dos columnas en `acceso_ruta_por_nivel` (senderismo, ciclismo); en `salida_participantes`, quién aprobó la excepción, cuándo y el motivo |
| Backend | Derivar el nivel mínimo de la ruta; comparar dificultad contra la matriz por tipo de actividad; la aprobación con excepción motivada |
| Frontend | La pantalla de aprobación dice en qué escala se queda corto y pide motivo para la excepción; marca a quien entró por excepción |
| Mobile | Lo mismo en la pantalla de participantes de la salida |
| Docs | `docs/flujos/08-aprobaciones-inscripcion.md`, `19-montanas-y-rutas.md` y el threat model |

## Registro de implementación

### 1.ª entrega (2026-10-27): la matriz cubre los cuatro tipos

Se parte en dos porque la frontera es natural: **extender la matriz es
prerrequisito** de validar con ella, y se puede revisar por separado.

`V23__matriz_completa_cuatro_tipos.sql` crea el catálogo de dificultad técnica
de ciclismo (S0–S4 con `rank`) y añade `max_senderismo_id` y `max_ciclismo_id` a
la matriz **y a la tabla de cambios pendientes** de FR-026 —cambiar esos máximos
es cambiar un umbral de acceso como cualquier otro—.

**Ciclismo necesitaba catálogo.** Era un `varchar(5)` con un CHECK de S0–S4 y
sin orden explícito, mientras las otras seis escalas son catálogos con `rank`.
Comparar necesita ese orden, y deducirlo del texto —«S3 va después de S2»—
funciona hasta que alguien añade un grado que no encaja con el alfabeto. Los ids
del catálogo son los mismos valores que ya guarda `rutas_ciclismo`, así que esa
tabla sigue funcionando sin tocarla; convertir su columna en clave foránea es un
cambio pequeño y aparte.

**Las dos columnas nuevas son nulas a propósito.** Un valor por defecto sería
inventárselo: uno permisivo abre la puerta que esto cierra, uno restrictivo
bloquea salidas de trekking desde el primer día. Mientras estén nulas el sistema
**no puede** decir si una ruta de ese tipo excede el nivel, y eso se dirá a quien
aprueba en vez de dejarlo pasar en silencio.

El comparador de FR-026 pasó de siete escalas a nueve, y tolera nulos en los dos
lados con dos estados nuevos: `SIN_FIJAR` cuando no hay límite en ninguno, y
`SE_FIJA` cuando se pone por primera vez. Fijar un límite que no existía no es
relajar ni endurecer, y llamarlo de cualquiera de las dos maneras sería mentir.
Quitar uno que existía **sí** cuenta como relajar.

### Dos fallos propios, encontrados al probar

**Capturar la violación del índice único dentro de la misma transacción no da un
409 limpio.** La transacción ya quedó marcada para deshacerse y lo que sale es un
500. Ahora se comprueba antes de insertar **y** el índice sigue como red: hacen
falta las dos, porque sola la comprobación se la saltan dos peticiones
simultáneas.

**Los tests dependían del estado de la ejecución anterior.** El contenedor se
reutiliza, así que un cambio pendiente de la vez pasada chocaba con el índice
«uno por nivel» y el test fallaba por un motivo ajeno a lo que probaba: pasaban
una vez y fallaban la siguiente. Se limpia la tabla antes de cada test, y se
comprobó corriendo la suite **dos veces seguidas**.

### Verificado contra la API en marcha

```
escalas comparadas: 9
  Roca             NA → I               RELAJA
  Técnico Sadday   Moderado → Bajo      ENDURECE
  Senderismo       sin fijar → Moderado SE_FIJA
  Ciclismo         sin fijar → S1       SE_FIJA
```

```
908 del backend · 49 de vitest · 124 del móvil · analyze limpio · e2e 20
```

### Me había inventado una excepción que ya existía

Añadí a `salida_participantes` tres columnas —`excepcion_nivel_por_id`,
`excepcion_nivel_en`, `excepcion_nivel_motivo`— para registrar que un directivo
aprueba pese al nivel. **Están de más: eso está modelado desde V1, y mejor.**

Lo que ya hay: el estado `PENDIENTE_APROBACION`, dos aprobadores
—`riesgo_aprobado_por_directivo` **y** `riesgo_aprobado_por_jefe`, hacen falta
los dos—, un motivo por cada uno, la fecha, una cola de pendientes por rol, una
máquina de estados que solo deja ir a `INSCRITO` o `NEGADO`, y revocación de una
aprobación ya dada.

Mi versión tenía un aprobador y un motivo. Habría quedado un segundo camino para
lo mismo, más pobre, y con dos sitios donde mirar para responder «¿quién dejó
entrar a este socio?». Se quitaron del V23 antes de mergear nada; el fichero se
renombró a `V23__matriz_completa_cuatro_tipos.sql`, que es lo que de verdad
hace, y se verificó aplicando **V1..V23 de cero** sobre una base vacía.

El `CHECK` que en la entrega anterior quedó sin verificar ya no existe, así que
no hay nada que verificar: no se escribió ningún test para cubrirlo porque la
columna que vigilaba se fue con él.

**Lo que esto cambia del plan:** la 2.ª entrega no modela la excepción, la
**alimenta**. Es bastante menos trabajo del que había estimado.

### Dónde está de verdad el hueco

No es que no se valide nada al inscribirse —`inscribir()` ya manda a
`PENDIENTE_APROBACION` a quien no llega—. El hueco es **con qué** se compara:

```java
ClasificacionSocio nivelMinimo = salida.getNivelMinimoRequerido();  // puesto a mano
if (nivelSocio == null || nivelSocio.getNivel() < nivelMinimo.getNivel())
```

`nivelMinimoRequerido` lo escribe quien crea la salida, a mano, y es nulo si no
lo pone. La dificultad real de la ruta no se mira, y la matriz tampoco: con
nueve escalas describiendo qué puede hacer cada nivel, la decisión se toma
comparando dos enteros que alguien tecleó. Si se olvida, **no hay validación y
nada lo dice**.

El javadoc de `Salida` ya lo prometía —«puede ser calculado de la ruta (al cruzar
sus dificultades con `acceso_ruta_por_nivel`)»— describiendo código que no
existe. Eso es peor que no documentarlo.

### 2.ª entrega (2026-10-27): la matriz ya decide

`NivelRutaService` cruza las dificultades de la ruta con el techo del nivel y
devuelve **por qué**, no un sí/no. Un sí/no no sirve para decidir: quien aprueba
necesita saber qué escala no da y por cuánto.

Distingue dos cosas que es tentador juntar:

| | Qué significa | Qué pasa |
|---|---|---|
| `EXCEDE` | la ruta pide más de lo que ese nivel permite | frena, con los dos valores |
| `SIN_COMPROBAR` | falta el dato para saberlo | **frena igual** |

Lo segundo fue una decisión tomada a conciencia, con su coste: hasta que la
directiva fije los máximos de senderismo y ciclismo, **toda inscripción a
trekking o ciclismo necesitará las dos firmas**. Se prefiere ese roce a que el
sistema diga «apto» sobre algo que no ha mirado. Se arregla fijando los
umbrales, y la pantalla dice exactamente cuál falta.

**No hay migración en esta entrega.** No se modela ninguna excepción nueva: se
alimenta la que ya existía desde V1 —`PENDIENTE_APROBACION` con Directivo **y**
Jefe de Salida, un motivo por cada uno—. La evaluación se calcula al leer y no se
guarda en la fila: si la matriz cambia o la ruta se corrige, quien aprueba ve el
estado de ahora, y una copia guardada envejecería en silencio siendo justo el
dato con el que se decide.

### La integral se clasifica como alpinismo

Antes `dificultadMaxTipo` elegía entre los cuatro tipos, y las tres alternativas
dejaban la integral sin las escalas con las que la matriz la compara: una
integral clasificada como «trekking» solo tenía senderismo, y no se habría podido
decir si excede el nivel de nadie. Ahora lleva siempre las siete escalas de
alpinismo. No había ninguna ruta integral creada, así que no hubo nada que
convertir.

### El mínimo tecleado ya no puede bajar del que exige la ruta

Subirlo sí, y a propósito: una salida concreta puede ser más exigente que la
ruta —invierno, grupo grande, poca luz— y eso lo sabe quien la organiza, no la
tabla. La respuesta de la salida lleva además el mínimo **derivado**, para que no
haya que adivinarlo.

### Dos tests que fallaron, y no se ajustaron a la baja

Al conectar esto, dos tests se pusieron rojos. Estaban **demostrando el
agujero**, así que se cambiaron para que afirmen lo correcto en vez de rebajar la
expectativa:

- El socio de prueba es Principiante y la salida le exige exactamente su nivel,
  así que el mínimo manual lo dejaba pasar. La ruta pide roca UIAA-F002 y
  compromiso C002, por encima de su techo. Ahora el test afirma que queda
  retenida **y que se nombran esas dos escalas**.
- El fixture de creación pedía un mínimo que la matriz no permite para esa ruta.
  Ahora hay un test para el 400 y otro para que exigir más siga valiendo.

### Verificado rompiendo el código, no solo viéndolo pasar

Doce mutaciones a lo largo del FR, las doce detectadas. Las de esta entrega:

| Mutación | |
|---|---|
| Dar por bueno un techo sin fijar | detectada |
| Invertir la comparación ruta/techo | detectada |
| Dejar de medir la integral | detectada |
| Los huecos pintados como «excede» (web y móvil) | detectada |
| Quitar el aviso de «no es un visto bueno» | detectada |
| Un tipo desconocido deja de contar como hueco (móvil) | detectada |
| Un json incompleto se da por apto (móvil) | detectada |

```
956 del backend · 54 de vitest · 137 del móvil · analyze limpio
```

### Lo que queda fuera, dicho

El frontend **recalcula el nivel en el navegador** para avisar antes de pulsar
(`nivelInsuficiente` en `salida-socio-view.tsx`), y esa cuenta solo conoce el
mínimo de la salida, no la matriz. Puede decir «todo bien» y luego la
inscripción quedar retenida. No engaña a nadie —el servidor manda y el motivo
real se muestra al volver— pero es un aviso que a veces no avisa. Arreglarlo
pide consultar al servidor antes de inscribirse, y eso es otra entrega.
