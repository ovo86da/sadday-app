---
name: mutar
description: Rompe el código a propósito y comprueba que los tests lo detectan. Usar después de escribir tests —sobre todo de lógica de permisos, validación o comparaciones— para saber si de verdad cubren algo, y antes de dar por buena una suite que pasa. Con --lote corre un fichero de mutaciones ya preparado.
allowed-tools: Bash(./scripts/mutar.sh *) Read
arguments: [fichero] [busca] [pon] [-- orden de test] | --lote [fichero]
---

# Comprobar los tests rompiendo el código

!`./scripts/mutar.sh ${1:---lote docs/mutaciones/fr-028-nivel-en-inscripcion.mutaciones}`

## Por qué existe

Un test que pasa no prueba nada. Prueba algo cuando **se pone rojo al romper el
código que vigila**, y eso hay que comprobarlo, no suponerlo.

En este repo ya entró una suite de Playwright con tres `fixme` y un test que se
saltaba en silencio. El mismo problema en pequeño: una aserción que nunca puede
fallar se lee igual que una que protege algo.

Hacerlo a mano sale mal de formas calladas. Pasó dos veces:

- un bucle que leía el código de salida de `grep` en lugar del de `psql`, y daba
  por roto todo lo que no imprimía nada;
- una comprobación hecha **después** de restaurar el fichero, que informó «no
  aplicó» de una mutación que sí aplicó y sí fue detectada.

Las dos veces la salida impresa era mentira, y en la dirección cómoda. De ahí
las tres reglas del script.

## Las tres reglas

1. **Si la mutación no entra, es fallo.** No «no aplicó», no un aviso: fallo. Una
   mutación que no modifica el fichero no prueba nada y no puede pasar por éxito.
   Incluye el caso de que la ruta esté mal escrita.
2. **Si los tests pasan con el código roto, es fallo.** Eso es el hallazgo, no un
   problema del script: significa que esa línea no está cubierta por nada.
3. **El fichero se restaura siempre** —éxito, fallo, Ctrl-C o error— y al final se
   verifica que quedó idéntico. Si no se pudo, lo dice a gritos en vez de dejar
   el repo sucio en silencio.

Es la regla compartida de todos los scripts del repo: lo que no se puede
interpretar cuenta como fallo, nunca como «todo bien».

## Cómo leer la salida

```
  ✓ la comparación ruta/techo invertida
  ✗ un techo sin fijar se da por bueno
      los tests PASAN con el código roto: no cubren esto
      log: /tmp/sadday-mutar-XXXX/1793219283727269000.log
```

Un `✓` es **bueno**: se rompió el código y los tests lo pillaron. Un `✗` es un
agujero de cobertura con nombre y apellido, y el log dice qué corrió.

El código de salida es 0 solo si todas las mutaciones fueron detectadas.

## Las dos formas de usarlo

Una mutación suelta, para comprobar un test que acabas de escribir:

```bash
./scripts/mutar.sh \
    backend/src/main/java/com/sadday/app/mountains/service/NivelRutaService.java \
    'rank.apply(exigeRuta) > rank.apply(permiteNivel)' \
    'rank.apply(exigeRuta) < rank.apply(permiteNivel)' \
    -- ./mvnw -q -Dtest=NivelRutaServiceTest test
```

O un fichero de mutaciones, que es lo que conviene dejar escrito junto a un FR
grande para poder repetirlo:

```bash
./scripts/mutar.sh --lote docs/mutaciones/fr-028-nivel-en-inscripcion.mutaciones
```

Formato: bloques separados por una línea en blanco, el comentario `#` es la
descripción que sale en la salida.

```
# qué se rompe, en una frase
fichero: backend/src/main/java/.../NivelRutaService.java
busca: texto exacto a sustituir
pon: texto de reemplazo
test: ./mvnw -q -Dtest=NivelRutaServiceTest test
```

La orden de `test` se ejecuta desde la carpeta del módulo que contiene el fichero
(`backend/`, `frontend/`, `mobile/`), que es donde viven `mvnw` y `pnpm`.

## Qué mutaciones valen la pena

Las que convierten el código en algo que **parece funcionar**. No sirve borrar
un método y ver que no compila: eso lo pilla el compilador.

Lo que ha encontrado cosas en este repo:

- **invertir una comparación** — `>` por `<` en un umbral;
- **dar por bueno un caso que no se puede decidir** — tratar un `null` como
  permitido, que es el fallo que deja pasar a alguien sin que nadie mire;
- **dejar de comprobar un caso** — quitar un tipo de un `switch`, que es la forma
  silenciosa de que una validación desaparezca para la mitad de los datos;
- **tratar dos ausencias como distintas** — dos `null` que dejan de ser iguales;
- **quitar un aviso de la interfaz** — que la pantalla deje de decir que algo no
  se pudo comprobar.

En la lógica de permisos y de validación es donde más rinde, porque ahí el fallo
no se ve: el programa responde, los tests pasan y alguien entra donde no debía.

## Límites, dichos

- **`busca` es de una sola línea.** El formato de `--lote` no admite texto
  multilínea; para mutar un bloque, elige una línea característica de dentro.
- **El texto es literal, no una expresión regular.** A propósito: un escape mal
  puesto produce una mutación distinta de la que crees estar probando. Si el
  texto aparece varias veces, se sustituyen todas.
- **No es un mutador automático** tipo PIT. Las mutaciones las eliges tú, que es
  justo lo que lo hace útil: una mutación elegida a mano prueba el riesgo que te
  preocupa, no un catálogo genérico.
