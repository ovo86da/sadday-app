# FR-023 — Rellenar los datos de la ruta desde el GPX

**Fecha de solicitud:** 2026-10-18
**Fecha de implementación:** —
**Estado:** Aprobado
**Prioridad:** Media
**Área:** Montañas y rutas (frontend, posiblemente backend)

---

## Contexto y motivación

El formulario de proponer una ruta pide a mano **longitud, desnivel y duración**. Quien
propone la ruta suele llegar con el track de la salida que acaba de hacer, y ese fichero ya
contiene los tres datos: el GPX es una lista de puntos con latitud, longitud, altitud y —si se
grabó en campo— marca de tiempo.

Pedirle que los teclee cuando ya los está adjuntando es trabajo duplicado, y además invita al
error: calcular el desnivel acumulado a ojo no lo hace nadie bien, así que o se deja vacío o se
pone una estimación.

Este FR se separó de [FR-022](./FR-022-archivos-y-enlaces-rutas.md) a propósito: aquello mantiene
la aplicación sin **interpretar** el GPX, y eso es justo lo que aquí hay que cambiar.

## Qué se puede extraer, y con qué fiabilidad

Esto es lo primero que hay que tener claro, porque no todos los datos valen lo mismo.

| Dato | Campo | Fiabilidad |
|---|---|---|
| **Distancia** | `longitudKm` | **Alta.** Suma de distancias entre puntos consecutivos; es geometría pura sobre coordenadas |
| **Desnivel acumulado** | `desnivelM` | **Media.** La altitud del GPS oscila, y sumar las subidas en crudo **infla el desnivel** de forma apreciable. Hay que filtrar el ruido —ignorar variaciones por debajo de un umbral, del orden de 3 a 5 m— y aun así queda aproximado |
| **Duración** | `duracionHoras`, `duracionDias` | **Baja o nula.** Solo si el GPX trae marcas de tiempo, y **una ruta planificada en Wikiloc o Komoot y exportada normalmente no las trae**: solo las llevan los tracks grabados en campo |
| Altitud máxima y mínima | — | Alta, pero hoy no hay campo donde guardarlas |

**Consecuencia de diseño: se sugiere, no se impone.** `duracionHoras` es la duración que el club
asigna a la ruta, no el tiempo que tardó una persona; el track de alguien rápido no debe fijar la
duración oficial. Lo mismo con el desnivel, que sale aproximado. El formulario se **prerrellena**
y la persona ajusta, viendo de dónde salió cada número.

Y hay que decidir qué pasa cuando el dato no se puede obtener. Si el GPX no trae tiempos, la
duración se deja **vacía**, no en cero: un cero es una afirmación falsa, un vacío es una pregunta
honesta.

## Dónde se analiza el fichero — la decisión de fondo

En el flujo actual (FR-022) **el GPX se elige antes de que la ruta exista**: se encola y se sube
al guardar. Para prerrellenar el formulario hace falta el dato *en el momento de elegir el
fichero*, no después. Eso deja dos arquitecturas, y no son equivalentes.

| # | Dónde | Cómo | Ventajas | Inconvenientes |
|---|---|---|---|---|
| **a** | **En el navegador** | Se lee el fichero con `FileReader` y se parsea con `DOMParser` al seleccionarlo | Respuesta inmediata, sin red. **El servidor nunca parsea XML de terceros**, así que no se abre superficie de XXE en el backend. No necesita que el fichero se suba para ver los números | La lógica no la comparte mobile: habría que repetirla en Dart si algún día se quiere allí |
| **b** | **En el backend** | Un endpoint que recibe el fichero, lo analiza y devuelve las métricas **sin almacenarlo** | Una sola implementación, reutilizable por mobile y por el MCP | Hay que endurecer el lector XML contra entidades externas (XXE), que es estándar pero es trabajo y superficie nueva. Y añade un viaje de red antes de poder teclear |

**Recomendación: la opción a.** El dato se necesita en el formulario, antes de guardar nada, y el
navegador ya tiene el fichero en memoria. Además mantiene la propiedad que hace seguro a FR-022
—el servidor no interpreta el GPX— en lugar de revertirla por una comodidad.

Sobre la seguridad del parseo en el navegador: `DOMParser` **no resuelve entidades externas**, así
que el vector clásico de XXE no aplica. Lo que sí hay que acotar es el coste: un fichero de 10 MB
son decenas de miles de puntos, así que el parseo debe hacerse sin bloquear la interfaz y con un
límite de puntos por encima del cual se muestrea en lugar de recorrer todo.

Si más adelante mobile necesitara lo mismo, se puede añadir la opción b entonces, reutilizando las
fórmulas ya decididas aquí.

## Cambios solicitados

1. **Al adjuntar un fichero de tipo `TRACK_GPX` en el formulario de ruta**, se analiza en el
   navegador y se calculan distancia, desnivel acumulado y, si hay marcas de tiempo, duración.

2. **Los campos vacíos se rellenan con el resultado.** Un campo que la persona ya escribió **no se
   pisa**: se le avisa de que el track propone otro valor y se le ofrece usarlo.

3. **Se indica de dónde viene cada número.** Un texto junto al campo del tipo «calculado del
   track» es suficiente; sin eso, quien revise la ruta después no sabe si el desnivel lo midió
   alguien o lo estimó un algoritmo.

4. **Si el GPX no trae tiempos**, la duración se queda vacía y se dice por qué, para que no
   parezca un fallo.

5. **Quitar el track no revierte los valores.** Una vez en el formulario son datos de la ruta, y
   deshacerlos automáticamente sorprendería más de lo que ayudaría.

## Decisiones abiertas

1. **¿Qué umbral de filtrado para el desnivel?** Entre 3 y 5 m es lo habitual. Afecta
   directamente al número que sale: más bajo infla, más alto subestima en terreno ondulado.
   Conviene fijarlo comparando contra un par de tracks reales del club cuyo desnivel se conozca.

2. **¿Se redondea?** Una distancia de `14,8732 km` es ruido de precisión. Proponer un decimal en
   la distancia y metros enteros en el desnivel.

3. **¿Qué hacer con un GPX de varios días?** `duracionDias` y `duracionHoras` son campos
   distintos. Si el track abarca más de 24 h, lo razonable es rellenar días y dejar horas vacía.

4. **¿Se guarda que el dato vino del track?** Hoy no hay columna para ello. Mostrarlo en el
   formulario no exige guardarlo; registrarlo sí, y sería una migración. Propuesta: no guardarlo
   en esta entrega.

## Impacto técnico previsto

| Capa | Qué cambia |
|---|---|
| **Frontend** | Una utilidad que parsea el GPX y calcula las métricas, con sus tests; integración en el formulario de ruta; textos de procedencia |
| **Backend** | **Ninguno** con la opción a |
| **BD** | **Ninguno** |
| **Mobile / MCP** | Sin cambios |

No hay dependencias nuevas obligatorias: `DOMParser` es del navegador y la distancia entre dos
coordenadas es la fórmula de Haversine, unas pocas líneas. Añadir una librería de geo por esto
sería desproporcionado.

## Registro de implementación

_Pendiente._
