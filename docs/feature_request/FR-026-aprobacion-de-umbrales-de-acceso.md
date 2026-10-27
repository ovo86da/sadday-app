# FR-026 — Doble aprobación para cambiar los umbrales de acceso a rutas

**Fecha de solicitud:** 2026-10-27
**Fecha de implementación:** —
**Estado:** Propuesto
**Prioridad:** Alta
**Área:** Montañas y rutas / Reglamento de seguridad

---

## Contexto y motivación

La tabla `acceso_ruta_por_nivel` define, para cada uno de los seis niveles de
socio, la dificultad máxima permitida en siete escalas: IFAS, roca, hielo,
compromiso, Yosemite y las dos propias del club. Es lo que decide si un socio de
iniciación puede inscribirse a una ruta de hielo.

Hoy cualquier Directivo o Admin la cambia actuando solo:

```java
@Auditable(accion = "UPDATE_ACCESO_NIVEL", entidad = "acceso_ruta_por_nivel", …)
@PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
public AccesoNivelResponse actualizarAccesoPorNivel(…)
```

El cambio queda auditado —se guarda `updated_by_id` y `updated_at`— pero nada lo
impide en el momento.

**No es un permiso administrativo cualquiera: es un control de seguridad
física.** Si alguien relaja un límite, socios que antes no podían inscribirse a
cierta ruta de pronto pueden, y el coste de equivocarse no es un dato mal
guardado, es una persona en terreno para el que no está preparada. La auditoría
lo cuenta **después**; no lo evita.

El escenario realista no es malicia: es un directivo con prisa, o cediendo a la
insistencia de un socio que quiere ir a una salida concreta.

## Lo decidido (2026-10-27)

**Cualquiera de los 10 directivos puede pedir el cambio, y cualquier otro puede
aprobarlo.** No se distingue por cargo. Los encargados de montaña suelen ser dos
—jefe y subjefe— pero exigir que sean ellos obligaría a modelar cargos dentro de
la directiva, y eso es trabajo que hoy no hace falta (ver «Lo que queda fuera»).

**Mientras está pendiente, el umbral viejo sigue vigente.** Un umbral relajado
sin aprobar es exactamente el riesgo que esto evita, así que el cambio no surte
efecto hasta que alguien lo aprueba.

**Endurecer también requiere dos aprobaciones**, igual que relajar. Decisión
explícita y simétrica.

> Tiene una consecuencia que conviene tener presente y no descubrir después: si
> alguien detecta que un umbral está peligrosamente laxo, **arreglarlo también
> necesita a una segunda persona**, de modo que el estado peligroso dura más. Se
> acepta a cambio de que la regla sea una sola y no haya que explicar por qué
> unos cambios pasan solos y otros no. Si alguna vez molesta, lo que se cambia
> es una condición en un método, no el flujo (ver abajo).

## Qué se pide

1. Cambiar un umbral deja un **cambio pendiente**, no modifica la tabla.
2. Cualquier directivo **distinto del que lo pidió** puede aprobarlo o
   rechazarlo. Al aprobar, se aplica. Al rechazar, se descarta con un motivo.
3. Quien lo pidió puede **cancelarlo** mientras siga pendiente.
4. Se **notifica a los diez directivos** cuando hay un cambio esperando, usando
   el módulo de notificaciones que ya existe.
5. En la pantalla de umbrales se ve **qué hay pendiente y quién lo pidió**, no
   solo el valor vigente.

## Cómo construirlo para que cambiarlo luego sea barato

Tres decisiones de construcción, porque la regla de quién aprueba va a cambiar:

**Un solo punto de decisión.** Un método `puedeAprobar(socio, cambio)` cuyo
cuerpo hoy es «cualquier DIRECTIVO o ADMIN que no sea quien lo pidió». Pasar
mañana a «solo el jefe o el subjefe de montaña» es cambiar **ese método**, no el
flujo, ni la pantalla, ni la base de datos.

**No guardar en los datos quién puede aprobar.** La fila del cambio pendiente
guarda *quién lo pidió* y *quién lo aprobó*, nunca *qué cargo puede aprobar*.
Así la regla cambia sin migración y sin invalidar las filas existentes.

**Copiar el patrón que ya existe, no inventarlo.** Las rutas ya funcionan así
—`aprobada`, `aprobada_por_id`, `aprobada_en`, y un rechazo con motivo— y el
`chk_aprobacion` de V1 garantiza la coherencia entre esos campos. Se replica la
misma forma, incluido el check.

## La unidad de cambio es un nivel, no una celda

`acceso_ruta_por_nivel` es **una fila por nivel** con las siete escalas como
columnas, y el endpoint actual recibe el nivel y los siete valores a la vez. Así
que lo que se aprueba es **el conjunto de los siete para un nivel**, no cada
escala por separado. Es la unidad que ya maneja la API y la que ve quien edita.

## Decisiones abiertas

1. **¿Qué pasa si se pide un segundo cambio sobre un nivel que ya tiene uno
   pendiente?** Propuesta: **rechazarlo** con un mensaje que diga que ya hay uno
   esperando y quién lo pidió. Permitir una cola invita a aprobar el de arriba
   sin ver que viene otro detrás.
2. **¿Caducan?** Propuesta: **no caducan, pero se muestran con su antigüedad.**
   Un cambio que caduca solo porque nadie miró es una decisión tomada por el
   reloj.
3. **¿Puede ADMIN saltarse la aprobación?** Propuesta: **no.** Un atajo para
   admin convierte la regla en una recomendación, y el escenario de riesgo —
   prisa, insistencia de un socio— aplica igual a un admin.
4. **¿Se notifica también al aprobar o rechazar, o solo al pedir?** Propuesta:
   las tres cosas. Enterarse de que un umbral cambió importa tanto como
   enterarse de que alguien quiere cambiarlo.

## Lo que queda fuera, y por qué

**Modelar los cargos de la directiva.** Hoy hay dos booleanos en `socios`,
`es_jefe_montana` (V1) y `es_presidenta` (V6 — una migración entera para un
cargo). La forma que escala es un catálogo de cargos más una tabla de unión:
añadir «jefe de material» sería una fila y no una migración.

Pero **medido, no es barato**: esos dos booleanos están en el backend (login,
DTO, servicio de salidas), en el frontend (store de auth, cliente de API, login,
guards y cuatro pantallas, con botones de «Asignar JM» y «Asignar Presidenta») y
en el móvil (modelo de usuario, modelo de socio, detalle de salida y de socio).
Son del orden de **25 ficheros**, el tamaño de FR-024 o mayor.

Y **este FR no lo necesita**: si cualquiera de los diez aprueba, no se distingue
por cargo. Va a su propio FR, para cuando la directiva pida reglas por cargo. Lo
único que conviene mientras tanto es **no añadir más booleanos**: el tercer
cargo que se pida ya debería entrar por el catálogo.

## Impacto técnico previsto

| Capa | Qué |
|---|---|
| Base de datos | Tabla `acceso_nivel_cambio_pendiente` con el nivel, los siete valores propuestos, quién lo pidió, cuándo, el estado y quién lo resolvió; más su `CHECK` de coherencia al estilo de `chk_aprobacion` |
| Backend | `MountainService.actualizarAccesoPorNivel` pasa a crear un pendiente; métodos de aprobar, rechazar y cancelar; `puedeAprobar(socio, cambio)`; notificaciones |
| Frontend | La pantalla de umbrales muestra lo pendiente y permite aprobar, rechazar o cancelar |
| Mobile | Por decidir: hoy el móvil no edita umbrales. Propuesta: **solo mostrar** que hay un cambio pendiente, sin poder resolverlo |
| Docs | `docs/flujos/19-montanas-y-rutas.md` y el threat model en `docs/security/` |

## Registro de implementación

_Pendiente._
