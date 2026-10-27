# FR-028 — Que el nivel del socio se valide de verdad al inscribirse

**Fecha de solicitud:** 2026-10-27
**Fecha de implementación:** —
**Estado:** En progreso — 1.ª entrega hecha
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

`V23__matriz_completa_y_excepcion_nivel.sql` crea el catálogo de dificultad
técnica de ciclismo (S0–S4 con `rank`), añade `max_senderismo_id` y
`max_ciclismo_id` a la matriz **y a la tabla de cambios pendientes** de FR-026
—cambiar esos máximos es cambiar un umbral de acceso como cualquier otro—, y
deja preparadas las columnas de la excepción en `salida_participantes`.

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

### 2.ª entrega, pendiente

Lo que falta es el motivo del FR: **que la matriz se consulte al inscribirse**.
Derivar el nivel mínimo de la salida desde la ruta, comparar la dificultad
contra lo que permite el nivel del socio según el tipo de actividad, detener la
aprobación cuando no da, y registrar la excepción con su motivo cuando un
directivo aprueba igualmente. Las columnas de la excepción ya están en la base;
falta usarlas.

**Hasta entonces la matriz sigue sin decidir nada.**
