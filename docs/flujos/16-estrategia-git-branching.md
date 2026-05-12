# Flujo 16 — Estrategia de ramas Git (Branching Strategy)

## ¿Para qué sirven las ramas?

Una rama es una copia independiente del código donde puedes trabajar sin afectar lo que tienen los demás. Cuando terminas, haces un **merge** para integrar tus cambios.

Este proyecto usa una estrategia de dos ramas permanentes inspirada en **Gitflow simplificado**:

```
main ──────────────────────────────────────────────▶  PRODUCCIÓN
  │
  └── develop ────────────────────────────────────▶  Staging / Staging
          │
          ├── feature/nombre-de-la-feature
          ├── fix/nombre-del-bug
          └── chore/nombre-de-la-tarea
```

---

## Las dos ramas permanentes

### `main` — Producción

- Contiene **solo código verificado y listo para producción**.
- Nunca se trabaja directamente en esta rama (excepto hotfixes críticos).
- Cada push a `main` dispara automáticamente el deploy a **Lightsail (producción)**.
- Los commits en `main` representan versiones entregadas al usuario final.

### `develop` — Staging / Staging

- Es la rama de integración. Aquí se acumula el trabajo en progreso.
- Cada push a `develop` dispara automáticamente el deploy a la **VM Proxmox (Staging)**.
- Permite probar en un entorno real antes de ir a producción.
- Es la rama base desde donde se crean las feature branches.

---

## El flujo diario de trabajo

### 1. Crear una rama para tu tarea

Siempre parte desde `develop`:

```bash
git checkout develop
git pull origin develop          # asegurarte de tener lo último
git checkout -b feature/mi-tarea
```

**Convención de nombres:**

| Prefijo | Cuándo usarlo | Ejemplo |
|---|---|---|
| `feature/` | Nueva funcionalidad | `feature/exportar-pdf-socios` |
| `fix/` | Corrección de bug | `fix/error-login-timeout` |
| `chore/` | Configuración, CI, docs, dependencias | `chore/actualizar-alpine-3.23.5` |
| `hotfix/` | Bug crítico en producción (parte desde `main`) | `hotfix/vulnerabilidad-auth` |

### 2. Trabajar y hacer commits

```bash
# hacer cambios...
git add archivo-modificado.java
git commit -m "feat: agregar exportación PDF de lista de socios"
```

Convención de mensajes (Conventional Commits):

| Prefijo | Significado |
|---|---|
| `feat:` | Nueva funcionalidad |
| `fix:` | Corrección de bug |
| `chore:` | Mantenimiento (sin impacto funcional) |
| `docs:` | Solo documentación |
| `refactor:` | Refactorización sin cambio de comportamiento |
| `test:` | Agregar o corregir tests |

### 3. Subir la rama y abrir un Pull Request hacia `develop`

```bash
git push origin feature/mi-tarea
```

Luego en GitHub: **New Pull Request** → base: `develop` ← compare: `feature/mi-tarea`

Esto activa `ci.yml` automáticamente (compilación + tests + Sonar + Semgrep).

### 4. Merge a `develop` → deploy automático a Staging

Una vez aprobado el PR y pasado el CI, merge a `develop`. El pipeline despliega a Staging automáticamente. Puedes verificar que todo funciona en el ambiente de pruebas.

### 5. Merge a `main` → deploy automático a Producción

Cuando el conjunto de cambios está probado en Staging y listo para producción, se abre un PR de `develop` → `main`.

```
develop ──── PR ────▶ main ──── deploy automático ────▶ Lightsail (producción)
```

---

## Diagrama completo

```
develop         feature/exportar-pdf
   │                    │
   │◀── git checkout ───┘  (crear rama)
   │                    │
   │                 commits...
   │                    │
   │◀──── PR + merge ───┘  (terminar feature)
   │
   │  [deploy automático a Staging]
   │
   │   (probar en Staging, todo OK)
   │
main◀─── PR + merge ────┘  (llevar a producción)
   │
   │  [deploy automático a Lightsail]
```

---

## Caso especial: Hotfix (bug crítico en producción)

Si hay un bug grave en producción que no puede esperar al ciclo normal:

```bash
git checkout main
git pull origin main
git checkout -b hotfix/descripcion-del-bug

# ... corregir el bug ...

git push origin hotfix/descripcion-del-bug
```

Abrir PR hacia `main` directamente. Una vez mergeado:
- Merge también hacia `develop` para que el fix quede en la rama de desarrollo.

```bash
git checkout develop
git merge main
git push origin develop
```

---

## Crear la rama `develop` por primera vez

Si aún no existe en el repositorio:

```bash
git checkout main
git checkout -b develop
git push origin develop
```

Esto crea `develop` a partir del estado actual de `main`. A partir de ahí, todo el desarrollo nuevo parte desde `develop`.

---

## Resumen: ¿qué pasa automáticamente?

| Acción | Pipeline que corre | Resultado |
|---|---|---|
| Push a cualquier rama / PR | `ci.yml` | Tests + análisis de calidad |
| Push a `develop` | `deploy.yml` | Build Docker + deploy a Staging |
| Push a `main` | `deploy.yml` | Build Docker + deploy a Producción |
| Cada lunes | `security.yml` + Dependabot | Auditoría de seguridad + PRs de updates |

---

## Buenas prácticas

- **Ramas cortas:** idealmente una feature = un PR que se mergea en el mismo día o semana. Las ramas longevas causan conflictos difíciles.
- **Un PR por feature:** no acumular 10 features en un solo PR gigante.
- **No commitear directamente a `main`:** siempre via PR, aunque seas el único desarrollador. El historial queda limpio y el CI corre antes del merge.
- **Borrar ramas mergeadas:** después de hacer merge, borrar la feature branch. GitHub lo puede hacer automáticamente.
- **Actualizar `develop` antes de crear una feature branch:** evita conflictos al mergear después.
