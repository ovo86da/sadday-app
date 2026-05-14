# FR-015: Exportación de Lista de Socios (CSV, PDF y Hoja de Firmas)

**Fecha:** 2026-05-14
**Estado:** En progreso
**Módulo:** Backend + Frontend — Socios
**Prioridad:** Media
**Rama Git:** `feature/socio-export`

---

## 1. Resumen Ejecutivo

Agregar la capacidad de exportar la lista de socios en tres formatos: CSV, PDF (lista tabulada) y PDF de hoja de firmas (para que los socios firmen presencialmente). La exportación es configurable: el usuario elige los campos a incluir y su orden. Incluye filtros por tipo de socio y estado de habilitación, con la opción de excluir al Admin marcada por defecto.

---

## 2. Roles con Acceso

Solo pueden exportar: **ADMIN, SECRETARIA, DIRECTIVO**.
Los socios regulares (rol SOCIO) no tienen acceso al botón ni a los endpoints.

---

## 3. Formatos de Exportación

### 3.1 Lista CSV
- Archivo `.csv` descargable directamente.
- Campos y orden configurables por el usuario.
- Sin límite de columnas.

### 3.2 Lista PDF
- Documento A4 con tabla de socios.
- Máximo 6 campos para que quepan en el ancho del A4.
- Incluye header con imagen del club (activable cuando se tenga la imagen).

### 3.3 Hoja de Firmas PDF
- Documento A4 en formato de tabla para firmas presenciales.
- Columnas fijas: **Cédula | Apellido | Nombre | Firma (vacío)**.
- Alto de cada fila: **2 cm** para que haya espacio para la firma manuscrita.
- Incluye header con imagen del club.
- No tiene selector de campos — formato fijo.

---

## 4. Campos Exportables

Los siguientes campos pueden seleccionarse para CSV y Lista PDF. El usuario define el orden con un número en la UI.

| Campo interno | Etiqueta visible | Default activo |
|---|---|---|
| `apellido` | Apellido | ✅ |
| `nombre` | Nombre | ✅ |
| `cedula` | Cédula | ✅ |
| `correo` | Correo electrónico | ✅ |
| `telefono` | Teléfono | — |
| `fechaNacimiento` | Fecha de nacimiento | — |
| `edad` | Edad (años) | — |
| `fechaIngreso` | Fecha de ingreso | — |
| `antiguedadAnios` | Antigüedad (años) | — |
| `fechaSalida` | Fecha de salida | — |
| `direccion` | Dirección | — |
| `tipoSangre` | Tipo de sangre | — |
| `tipoSocio` | Tipo de socio | ✅ |
| `nivelTecnico` | Nivel técnico | — |
| `estadoHabilitacion` | Estado habilitación | ✅ |
| `estadoAcceso` | Estado de acceso | — |
| `emergencyContactName` | Contacto emergencia 1 — nombre | — |
| `emergencyContactPhone` | Contacto emergencia 1 — teléfono | — |
| `emergencyContactName2` | Contacto emergencia 2 — nombre | — |
| `emergencyContactPhone2` | Contacto emergencia 2 — teléfono | — |

**Excluidos explícitamente (no exportables):**
- `username` / `password` (credenciales de acceso)
- `rolSistema` (rol del sistema)
- `esJefeMontana` (cargo interno)
- `id` (UUID interno)
- `createdAt` / `updatedAt` (timestamps internos)

---

## 5. Filtros de Exportación

Los filtros se precargan con los valores activos de la página de Socios al abrir el dialog:

| Filtro | Tipo | Default |
|---|---|---|
| Tipo de socio | Selector desplegable | (todos) |
| Estado habilitación | Selector desplegable | (todos) |
| Excluir Admin | Checkbox | ✅ marcado por defecto |
| Búsqueda texto | Input opcional | (vacío) |

---

## 6. UI — Dialog de Exportación

El botón **"Exportar"** (icono Download) aparece en el header de la página Socios junto a "Importar socios". Solo visible para ADMIN, SECRETARIA, DIRECTIVO.

Al hacer clic abre un `Dialog` con tres secciones:

**Sección 1 — Tipo de exportación** (radio buttons):
- Lista CSV
- Lista PDF
- Hoja de Firmas PDF

**Sección 2 — Campos a exportar** (solo para CSV y Lista PDF):
- Lista de campos con checkbox + input numérico de orden a la derecha.
- Los seleccionados con orden válido se envían al backend ordenados.
- Para Hoja de Firmas: sección reemplazada por un preview estático de las columnas fijas.

**Sección 3 — Filtros:**
- Selector tipo de socio.
- Selector estado habilitación.
- Checkbox "Excluir Admin" (checked por defecto).

---

## 7. Mecanismo de Descarga

Igual que la descarga de actas (`useGenerarPdfActa`):
- Llamada con `responseType: "blob"`.
- Filename extraído del header `content-disposition`.
- Descarga disparada vía `URL.createObjectURL(blob)`.
- Filename por defecto: `socios-YYYY-MM-DD.csv` / `socios-YYYY-MM-DD.pdf` / `socios-firmas-YYYY-MM-DD.pdf`.

---

## 8. Backend — Endpoints

```
GET /api/v1/socios/exportar/csv
GET /api/v1/socios/exportar/pdf
GET /api/v1/socios/exportar/pdf/firmas
```

**Autorización:** `@PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")`

**Query params comunes:**
```
fields=apellido,nombre,cedula   ← orden = posición en la lista
tipoId=2                        ← opcional
estadoId=1                      ← opcional
excludeAdmin=true               ← default true
q=texto                         ← opcional
```

**Respuesta:** `ResponseEntity<byte[]>` con `Content-Disposition: attachment; filename="..."`.

---

## 9. Campo `antiguedadAnios` — Cambios Necesarios

El campo `antiguedadAnios` (años desde `fechaIngreso`) sigue el mismo patrón que `edad`:

| Archivo | Cambio |
|---|---|
| `Socio.java` | Agregar método `calcularAntiguedad()` (2 líneas, mismo algoritmo que `calcularEdad()` con `fechaIngreso`) |
| `SocioSummaryResponse.java` | Agregar campo `int antiguedadAnios` |
| `SocioResponse.java` | Agregar campo `int antiguedadAnios` |
| `SocioService.java` | Incluir `calcularAntiguedad()` en el mapeo de respuestas |

**Sin cambio en base de datos.** El campo es aditivo y backwards-compatible — el endpoint existente `/v1/socios` simplemente incluirá el campo nuevo en su respuesta JSON.

---

## 10. Header de Imagen en PDFs

Todos los templates PDF (`socios-lista.html`, `socios-firmas.html`, `acta-reunion.html`, `informe-salida.html`) tendrán un bloque:

```html
<img th:if="${headerImageBase64 != null}"
     th:src="'data:image/png;base64,' + ${headerImageBase64}"
     style="width: 100%; height: auto;" />
```

**Estado actual:** `headerImageBase64` es `null` → el bloque es invisible.

**Para activar (cuando se tenga la imagen):**
1. Colocar el PNG en `src/main/resources/images/header.png`.
2. En `PdfRenderService`, agregar la carga de la imagen como Base64 al mapa de variables.
3. El header aparece en todos los PDFs automáticamente.

**Requisitos de la imagen:** PNG, fondo blanco o transparente, ~1536×300 px, < 500 KB, color RGB.

---

## 11. Archivos Afectados

### Backend (nuevos)
- `SocioExportController.java`
- `SocioExportService.java`
- `templates/pdf/socios-lista.html`
- `templates/pdf/socios-firmas.html`

### Backend (modificados)
- `Socio.java` — método `calcularAntiguedad()`
- `SocioSummaryResponse.java` — campo `antiguedadAnios`
- `SocioResponse.java` — campo `antiguedadAnios`
- `SocioService.java` — mapeo del nuevo campo

### Frontend (nuevos)
- `pages/socios/export-socios-dialog.tsx`

### Frontend (modificados)
- `hooks/use-socios.ts` — 3 hooks de exportación
- `pages/socios/socios-page.tsx` — botón Exportar + dialog

---

## 12. Impacto y Riesgos

| Aspecto | Impacto | Mitigación |
|---|---|---|
| PDF con muchos campos no cabe en A4 | Medio | Límite de 6 campos para Lista PDF; sin límite para CSV |
| Exportar muchos socios (rendimiento) | Bajo | Query sin paginación pero con los mismos filtros de la lista; socios de un club son pocos cientos |
| Campo `antiguedadAnios` en API existente | Mínimo | Cambio aditivo, no rompe clientes existentes |
| Nada en producción se rompe | Ninguno | Endpoints y componentes completamente nuevos |
| Datos sensibles (sin acceso para SOCIO) | Ninguno | `@PreAuthorize` en todos los endpoints de exportación |
