# FR-027 — Objetivos del socio y competencias acreditadas

**Fecha de solicitud:** 2026-10-27
**Fecha de implementación:** —
**Estado:** Propuesto
**Prioridad:** Media
**Área:** Socios / Formación

---

## Contexto y motivación

Hoy un socio tiene un nivel (`ClasificacionSocio`, seis niveles estáticos) y un
historial de lo que ha hecho (FR-006), pero **no hay ningún sitio donde diga
hacia dónde va**. La directiva no sabe qué quiere aprender cada socio, que es la
mitad del problema de organizar formación.

Lo que se pide no es un formulario de deseos: es **el ciclo completo de aprender
algo en el club**, tal como funciona de verdad en montaña.

## El ciclo, en las palabras con que se pidió

> «Un socio dice *quiero aprender a guiar*. La única forma de aprender es que lo
> haga. Entonces ese socio empieza a guiar varias salidas, con un acompañante
> que sí sabe guiar como respaldo. Cuando haya aprendido o haya hecho varias
> salidas como guía, el mismo socio debe poder decir *estoy listo*, y el jefe de
> montaña puede decir *sí* o *no todavía*.»

Cuatro piezas, y ninguna sobra:

1. **Declarar** el objetivo, de un catálogo cerrado.
2. **Hacerlo**, en salidas reales, con alguien que respalda.
3. **Decir «estoy listo»** — lo dice el socio, no se lo conceden.
4. **Acreditarlo**, y eso sí lo decide el jefe de montaña: *sí* o *no todavía*.

## Las dos clases de habilidad, que no se comportan igual

El catálogo es cerrado y **lo mantiene la directiva**: ella añade y retira
entradas. Pero dentro del catálogo hay dos clases, y la diferencia manda:

| | Ejemplo | Evidencia |
|---|---|---|
| **Ligada a una dignidad** | «guiar» → la dignidad `Guía`, que ya existe | **El sistema la cuenta solo:** sabe en qué salidas el socio llevó esa dignidad, con quién y cuándo |
| **Sin dignidad** | «aprender nudos», «mejorar en hielo» | No hay nada que contar. Depende del criterio de quien acredita |

Esto no es un detalle de implementación: decide **qué puede enseñarle el sistema
al jefe de montaña** cuando alguien dice «estoy listo». En una habilidad ligada
a dignidad puede enseñar los hechos —«seis salidas como guía, cuatro con
respaldo, dos sin él»—. En las demás solo puede enseñar que el socio lo pidió.

Por eso el catálogo lleva un enlace **opcional** a una dignidad. Las que lo
tienen traen evidencia; las que no, dependen del juicio de una persona, y el
documento lo dice en vez de fingir que son lo mismo.

## El acompañante no se modela, y es una decisión

En el club, quien aprende a guiar lo hace **con alguien que ya sabe
respaldándole**. Eso seguirá pasando: lo que no va a haber es un sitio en la
aplicación donde registrarlo. Decidido el 2026-10-27.

Conviene dejar escrito qué cuesta, para que no se descubra al usarlo: **la
evidencia no podrá distinguir una salida guiada con respaldo de una guiada
sola.** El sistema enseñará «seis salidas como guía», no «cuatro con respaldo y
dos sin él». Quien acredita sabe quién fue a cada salida y puede preguntar; el
sistema no se lo dirá.

Es un coste asumible porque la decisión final es de una persona que conoce el
caso, no de un contador. Si algún día se quiere, es **una columna** en
`salida_participante_dignidades` apuntando a otro participante de la misma
salida — no un rediseño.

## «No todavía» no es un rechazo

Se pidió con esas palabras y conviene conservarlas. Un rechazo cierra; «no
todavía» dice que falta recorrido y que se vuelve a pedir. Tres consecuencias:

- El socio **puede volver a pedirlo** sin trámite nuevo.
- El jefe de montaña puede dejar **una nota** de qué falta. Sin eso, «no
  todavía» es un muro sin puerta.
- No cuenta como fracaso en ninguna parte ni aparece en el historial como
  sanción.

## Lo que una competencia acreditada hace, y lo que no

**No mueve el nivel del socio.** Decidido en D-06 y vale igual aquí: el nivel lo
cambia la directiva a mano.

**Sí alimenta la recomendación** de D-06: una competencia acreditada es una de
las señales por las que el sistema sugiere considerar una subida de nivel. Es la
mitad «por competencias» que allí quedó pendiente de que esto existiera.

**Y queda registrada y visible**, que es lo que de verdad usa el club: saber
quién sabe guiar sirve para organizar cualquier salida.

## Acreditar directamente, sin pasar por el ciclo

La directiva puede acreditar una competencia **sin solicitud previa**, dejándolo
registrado como tal para que se distinga de las que pasaron por el ciclo.

Hace falta por dos motivos, y ninguno es excepcional. El primero es el arranque:
los socios que ya saben guiar deben tenerlo acreditado desde el primer día, no
pedirlo como si fueran aprendices. El segundo es lo que viene de fuera, como un
título de guía federado, que no se adquiere en salidas del club.

## Decisiones cerradas (2026-10-27)

1. **No hace falta un mínimo de salidas** antes de poder decir «estoy listo». El
   socio pide cuando quiera; decide el jefe de montaña viendo la evidencia. Un
   mínimo duro convertiría una decisión de criterio en un contador.
2. **Acreditan las dos personas con la etiqueta de jefe de montaña.** Aquí sí se
   distingue por cargo, al revés que en FR-026, pero no hace falta modelar
   cargos nuevos porque `es_jefe_montana` ya existe.
3. **Las competencias acreditadas son públicas** entre socios; **las solicitudes
   no**. Quién sabe guiar es útil para todo el club; quién pidió y recibió un
   «no todavía» no es asunto de nadie más.
4. **Los objetivos no caducan**, pero se pueden retirar.
5. **No hay itinerarios** en esta entrega.

## Los objetivos sirven para planificar cursos

Añadido el 2026-10-27, y cambia qué hay que construir: los objetivos declarados
no son solo información de cada socio, son **el insumo para que la directiva
planifique formación**. «Ocho socios quieren aprender hielo este año» es una
pregunta que el sistema debe saber contestar.

Dos consecuencias:

- Hace falta una **vista agregada** para la directiva —cuántos socios quieren
  cada habilidad—, no solo el objetivo dentro de la ficha de cada uno.
- Es la razón de peso para que el catálogo sea **cerrado**: con texto libre,
  «hielo», «escalada en hielo» y «WI» serían tres cosas distintas y no se
  podrían contar.

## Impacto técnico previsto

| Capa | Qué |
|---|---|
| Base de datos | Catálogo `habilidad` (con enlace opcional a `dignidades`), `socio_objetivo`, y `socio_competencia` con su estado y quién acreditó. **Nada que tocar en las salidas** |
| Backend | Declarar y retirar objetivos; pedir acreditación; acreditar o aplazar; el cálculo de la evidencia a partir de las dignidades ya asignadas |
| Frontend | Objetivos en la ficha del socio; **vista agregada por habilidad** para planificar cursos; bandeja del jefe de montaña con las solicitudes y **su evidencia**; mantenimiento del catálogo para la directiva |
| Mobile | Declarar objetivos y pedir acreditación. Acreditar desde el móvil: ver la decisión abierta de FR-026 sobre enseñar la evidencia antes del botón |
| Docs | `docs/flujos/` — flujo nuevo |

## Relación con F-02

F-02 («quiero ser guía en esta salida» y el karma) se apoya en esto: pedir una
dignidad en una salida concreta es **una instancia** de un objetivo declarado
aquí. Conviene hacer este FR antes, o las dos cosas acabarán en tablas distintas
que significan lo mismo.

El karma de F-02 sigue siendo social y **no entra en este ciclo**: no se acredita
una competencia por tener karma.

## Registro de implementación

_Pendiente._
