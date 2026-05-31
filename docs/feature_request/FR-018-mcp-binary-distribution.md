# FR-018: Distribución del MCP como Binario Ejecutable

**Fecha:** 2026-05-31
**Estado:** En progreso
**Módulo:** MCP Server + CI/CD
**Prioridad:** Media
**Rama Git:** `feat/mcp-binary-distribution`
**Relacionado con:** [Flujo 14 — Asistente IA MCP](../flujos/14-asistente-ia-mcp.md) · [Flujo 15 — Pipelines CI/CD](../flujos/15-pipelines-ci-cd.md)

---

## 1. Resumen Ejecutivo

Actualmente, usar el MCP server requiere que el usuario clone el repositorio, instale Node.js 20+, instale dependencias y compile TypeScript manualmente. Esta fricción hace inviable que socios sin perfil técnico lo usen.

El objetivo es generar binarios ejecutables multiplataforma distribuidos como assets de GitHub Releases. El usuario descarga un solo archivo y lo configura en su cliente IA — sin clonar repos, sin npm, sin compilar.

---

## 2. Motivación

### Flujo actual (problemático)

```
Usuario quiere usar el MCP
  │
  ├── 1. Clonar el repositorio completo (~100 MB)
  ├── 2. Instalar Node.js 20+ (si no lo tiene)
  ├── 3. cd mcp/ && npm ci --ignore-scripts
  ├── 4. npm run build
  └── 5. Configurar cliente con ruta absoluta a dist/index.js
```

Requiere conocimientos técnicos, acceso al repositorio, y herramientas instaladas.

### Flujo objetivo

```
Usuario quiere usar el MCP
  │
  ├── 1. Ir a github.com/…/releases
  ├── 2. Descargar el binario de su plataforma (~85 MB)
  └── 3. Configurar cliente con la ruta al binario descargado
```

Sin Node.js, sin npm, sin compilar.

---

## 3. Decisiones de diseño

### ¿Por qué `@yao-pkg/pkg` y no Node.js SEA?

| Criterio | `@yao-pkg/pkg` | Node.js SEA |
|---|---|---|
| Madurez | Fork activo del `pkg` original de Vercel, años en producción | Experimental en Node 20, estable en Node 22 LTS |
| Complejidad de pipeline | Una línea de CLI | Proceso de varios pasos (blob → postject) |
| Soporte multiplataforma | Cross-compile desde Linux a macOS/Windows | Requiere compilar en cada plataforma |
| ESM | Requiere bundle previo a CJS | Soporte nativo pero limitado |

Migrar a SEA cuando sea estable es trivial — solo cambia el paso del pipeline.

### ¿Por qué esbuild + pkg y no pkg directo?

El proyecto usa `"type": "module"` con `module: Node16` en TypeScript, que emite ESM. `@yao-pkg/pkg` maneja ESM pero con limitaciones (extensiones `.js` explícitas en imports, dynamic imports). El patrón más robusto es:

```
TypeScript src/ → tsc → dist/ (ESM, type check)
TypeScript src/ → esbuild → dist/bundle.cjs (CJS, un solo archivo)
dist/bundle.cjs → @yao-pkg/pkg → binarios ejecutables
```

`esbuild` bundlea todas las dependencias (axios, zod, @modelcontextprotocol/sdk) en un único archivo CJS, eliminando los problemas de resolución de módulos en runtime.

### ¿Por qué GitHub Releases y no un endpoint del backend?

- El control de acceso ya lo provee la API Key — el binario sin key no puede hacer nada
- GitHub Releases es la solución estándar para distribución de herramientas CLI/ejecutables
- Cero cambios en el backend ni en la infraestructura
- Los assets se versionan junto al código

---

## 4. Cambios implementados

### `mcp/package.json`

- Nuevas devDependencies: `@yao-pkg/pkg`, `esbuild`
- Nuevo script `bundle`: bundlea ESM → CJS con esbuild
- Nuevo script `package`: bundle + genera binarios en `mcp/bin/`

### `.github/workflows/release-mcp.yml`

Nuevo workflow que se activa manualmente (`workflow_dispatch`) con un input `version`. Pasos:

1. `npm ci --ignore-scripts` — instalación sin ejecutar scripts de terceros
2. `npm audit --audit-level=high` — bloquea si hay CVEs high/critical
3. `npm run build` — type check con tsc
4. Bundle con esbuild
5. Package con `@yao-pkg/pkg` para cuatro targets
6. Crea GitHub Release con los binarios como assets

### Targets de compilación

| Archivo | Plataforma |
|---|---|
| `sadday-mcp-macos-arm64` | macOS Apple Silicon (M1/M2/M3) |
| `sadday-mcp-macos-x64` | macOS Intel |
| `sadday-mcp-win-x64.exe` | Windows 64-bit |
| `sadday-mcp-linux-x64` | Linux 64-bit |

### Documentación actualizada

- `mcp/README.md` — nueva sección "Distribución como binario"
- `docs/flujos/14-asistente-ia-mcp.md` — Paso 2 actualizado con opción binario (recomendada) y opción manual
- `docs/flujos/15-pipelines-ci-cd.md` — nuevo workflow `release-mcp.yml` documentado

---

## 5. Impacto en seguridad

- Los binarios no cambian la superficie de ataque — el mismo código que corre vía `node dist/index.js`
- `npm audit` en el workflow bloquea releases si hay CVEs nuevos
- `@yao-pkg/pkg` incluye el runtime de Node.js — se debe monitorear versiones de Node empaquetadas (Dependabot actualiza la devDependency que controla esto)
- Los binarios de macOS deberán ser **firmados y notarizados** por Apple para que GateKeeper no los bloquee. Esto requiere una cuenta de Apple Developer ($99/año). Como paso intermedio, el README documenta cómo omitir GateKeeper localmente (`xattr -c` / clic derecho → Abrir).

---

## 6. Limitaciones conocidas

- **Peso del binario:** ~80–90 MB por plataforma (incluye el runtime de Node.js 20)
- **macOS GateKeeper:** sin firma de Apple, el usuario debe autorizar manualmente la primera ejecución
- **Actualizaciones:** el usuario debe descargar manualmente cada nueva versión (no hay auto-update)
- **Firma de código Windows:** similar a macOS, SmartScreen puede alertar en el primer uso
