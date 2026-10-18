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

Calibrado el 2026-10-18 contra dos tracks reales del club grabados con GaiaGPS,
comparando contra los valores que la propia aplicación reporta.

| Dato | Campo | Fiabilidad |
|---|---|---|
| **Distancia** | `longitudKm` | **Alta, pero solo tras filtrar.** En crudo sale un **4–5 % larga**: es geometría pura, sí, pero sobre puntos con ruido, y cada temblor del GPS añade longitud. Descartando movimientos menores de **12 m** el error baja a **menos del 0,5 %** en ambas muestras |
| **Desnivel acumulado** | `desnivelM` | **Media, y no mejorable con un umbral fijo.** En crudo infla entre un **21 % y un 48 %**. El umbral que acierta depende del perfil: 30 m en una ruta de subida y bajada, 70 m en una ondulada de 23 km. El compromiso es **40 m**, con un error del **−1 % al +4 %** |
| **Duración** | `duracionHoras`, `duracionDias` | **Exacta cuando existe.** El tiempo total coincidió al minuto en las dos muestras. Pero solo si el GPX trae marcas de tiempo, y **una ruta planificada en Wikiloc o Komoot y exportada normalmente no las trae**: solo las llevan los tracks grabados en campo |
| Altitud máxima y mínima | — | **Exacta.** Coincidió al metro. Hoy no hay campo donde guardarlas |

### Las dos referencias

| | Distancia | Ascenso | Duración | Alt. máx / mín |
|---|---|---|---|---|
| **Morurco 360 y Cumbre** (Gaia) | 12,9 km | 880 m | 8h 23m | 4.880 / 3.983 |
| calculado | 12,94 (+0,3 %) | 871 (−1 %) | 8h 23m ✅ | 4.887 / 3.983 |
| **Sincholagua 2025** (Gaia) | 22,7 km | 1.065 m | 11h 09m | 4.692 / 3.465 |
| calculado | 22,72 (+0,1 %) | 1.112 (+4 %) | 11h 09m ✅ | 4.692 / 3.465 ✅ |

### Por qué el desnivel no se puede clavar

Se probó suavizar la serie de altitudes con media móvil —ventanas de 5, 11 y 21 puntos— antes de
aplicar el umbral, y **no alcanza**: lo mejor fueron 945 m frente a los 880 de referencia. El
problema no es ruido punto a punto sino acumulación a lo largo de miles de puntos.

Un umbral fijo es un modelo pobre del fenómeno; Gaia casi con seguridad usa algo más elaborado,
o la altitud barométrica del teléfono. **Igualar su número no es el objetivo**: quedarse dentro
de un 5 % y presentarlo como sugerencia editable, sí.

### Tiempo total o tiempo en movimiento

Gaia distingue ambos, y la proporción varía mucho: Morurco estuvo parado 4h10m de 8h23m (50 %),
Sincholagua 2h50m de 11h09m (25 %). El tiempo en movimiento **no** es un sustituto estable.

**Se usa el tiempo total**, que es lo que dura la salida de verdad incluyendo descansos — que es
lo que necesita saber quien planifica.

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

1. ~~**¿Qué umbral de filtrado para el desnivel?**~~ **Resuelto el 2026-10-18:** 40 m para el
   desnivel y descartar movimientos menores de 12 m para la distancia, calibrado contra las dos
   referencias de arriba. Si aparecen más tracks con valores conocidos conviene revisarlo,
   especialmente de rutas onduladas, que son las que peor encajan.

2. **¿Se redondea?** Una distancia de `14,8732 km` es ruido de precisión, más aún sabiendo que el
   método tiene un error del 0,5 %. Proponer un decimal en la distancia y **decenas de metros** en
   el desnivel: dar `1.112 m` sugiere una exactitud que el cálculo no tiene, y `1.110 m` es más
   honesto.

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
