---
name: sonar
description: Consulta el estado del análisis de SonarCloud — quality gate, condiciones que fallan, cobertura, bugs, vulnerabilidades y code smells. Con el argumento "issues" añade los problemas abiertos más graves. Usar al revisar calidad del código o cuando el job de Sonar falle en CI.
allowed-tools: Bash(./scripts/sonar.sh *) Read
arguments: [modo]
---

# Estado de SonarCloud

!`./scripts/sonar.sh ${1:+--issues}`

## Cómo interpretarlo

La primera línea es el **quality gate**. Si está en `ERROR`, debajo salen las
condiciones concretas que lo rompen, con el valor actual y el umbral — que es
justo lo que la web no te dice de un vistazo.

Las métricas de abajo son del proyecto completo. Las condiciones del gate, en
cambio, suelen medirse sobre **código nuevo**, así que es normal que el
proyecto esté bien y el gate en rojo: significa que lo añadido últimamente
baja el listón.

Para ver los problemas abiertos ordenados por gravedad, pasar cualquier
argumento: `/sonar issues`.

## Token

**No hace falta.** El proyecto es público y la API responde sin autenticar; el
script lo dice al final cuando va en modo anónimo.

Si en algún momento se necesita —proyecto privado, o límite de peticiones— el
script lo busca en dos sitios, en este orden:

1. **`$SONAR_TOKEN` del entorno**, lo que permite:
   `infisical run --env=dev -- ./scripts/sonar.sh`
2. **Llavero de macOS**, servicio `sadday-sonar-token`:
   ```bash
   security add-generic-password -a "$USER" -s sadday-sonar-token -w
   ```
   Pide el valor por teclado, sin eco, y no lo deja en ningún fichero.

Nunca se lee de un `.env` ni se imprime. Un token de lectura basta: no
necesita el permiso de análisis que usa la CI.

## Por qué existe

El job de SonarCloud estuvo fallando desde mayo sin que nadie se enterara —era
el token caducado, un 403— porque mirar el estado implicaba abrir la web.

Y hay una diferencia de fondo con los demás escáneres: Snyk, Trivy y OWASP
miran dependencias. Sonar mira **tu código**: cobertura, complejidad,
duplicación. Es el único que responde a "¿esto está bien escrito?", y por eso
merece una forma barata de consultarlo.

## Salidas

| Código | Significado |
|---|---|
| 0 | quality gate en OK |
| 1 | quality gate en ERROR — las condiciones que fallan salen listadas |
| 2 | no se pudo consultar: proyecto inexistente, red caída o token inválido |
