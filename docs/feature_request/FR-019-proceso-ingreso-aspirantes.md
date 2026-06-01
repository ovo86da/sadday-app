# FR-019: Proceso Digital de Ingreso de Aspirantes

**Fecha:** 2026-06-01
**Estado:** Pendiente de diseño
**Módulo:** Backend + Mobile + Frontend — Socios / Ingreso
**Prioridad:** Media-Alta
**Rama Git:** `docs/FR-019-proceso-ingreso-aspirantes`

---

## 1. Resumen Ejecutivo

El club maneja actualmente 4 documentos físicos para registrar el ingreso de un nuevo aspirante:

| # | Documento | Código | Propósito |
|---|-----------|--------|-----------|
| 1 | Ficha de Socios Aspirantes y Activos | F-02A / F-02B | Perfil personal completo + historial institucional (secretaría) |
| 2 | Formulario de Descargo — Adultos | RD-DA-2022-09 | Exoneración de responsabilidad. Firmada por el propio aspirante mayor de edad |
| 3 | Formulario de Descargo — Pre Juvenil / Juvenil | RD-DJ-2022-09 | Misma exoneración, firmada por el representante legal del menor |
| 4 | Solicitud de Ingreso como Aspirante | RD-SI-2022-09 | Petición formal de entrar por 3 meses + firma de aceptación del Directorio |

El objetivo de este FR es **digitalizar y automatizar todo este flujo dentro de la app**, de modo que no sea necesario ningún documento físico. El proceso debe poder completarse 100% desde la app móvil (aspirante) y la web/móvil (secretaría/directorio).

---

## 2. Contexto — Documentos de Referencia

Los PDFs originales están en `docs/ingreso_docs/`. A continuación se describe el contenido relevante de cada uno.

### 2.1 Solicitud de Ingreso (Doc 4)

Carta simple donde el aspirante:
- Declara su nombre y cédula/pasaporte
- Expresa su deseo de ingresar como **ASPIRANTE por 3 meses**
- Se compromete a cumplir estatutos y reglamentos
- El Directorio (secretaría) anota la aceptación y la fecha

**Digitalización:** formulario de solicitud en la app + flujo de aprobación por secretaría/directorio.

### 2.2 Ficha de Socios (Doc 1)

Formulario en dos partes:

**Parte A — Datos personales (llena el aspirante):**
- Nombre, apellido, nacionalidad, cédula/pasaporte
- Lugar de nacimiento (país, provincia, cantón)
- Fecha de nacimiento, estado civil, profesión/ocupación
- Estatura (cm), peso (kg), tipo de sangre
- Alergias (sí/no + detalle), lesiones articulares (sí/no), intervenciones quirúrgicas (sí/no)
- Dirección de domicilio + referencias, teléfono convencional, móvil
- Dirección de trabajo + teléfonos, correo electrónico
- Contactos de emergencia: **3 familiares** (nombre, parentesco, teléfonos)
- Otros estudios/habilidades (4 campos libres)

**Parte B — Datos referenciales para la agrupación (llena el aspirante):**
- Fecha de ingreso a la agrupación
- Motivos/razones/intereses por los que solicitó el ingreso
- Nombre del socio/a saddaísta que lo invitó/recomendó
- Pregunta 1: ¿Ha pertenecido a otra agrupación de montaña?
- Pregunta 2: ¿Qué conocimientos tiene de montañismo, ciclismo, escalada?
- Pregunta 3: ¿Qué conocimientos está dispuesto a compartir?

**Sección de secretaría (solo Secretaría):**
- Historial de categorías con fecha de inicio y fecha de transición:
  `Aspirante → Socio Pre-Juvenil → Socio Juvenil → Socio Activo → Socio Vitalicio`
- Dignidades ocupadas (Jefatura General, Subjefatura, Tesorería, Secretaría, Prosecretaría, Jefatura de Montaña/Verano/Juveniles, Tribunal Electoral) con período de ejercicio
- Licencias (causa, fecha inicio, fecha renovación, fecha terminación)
- Observaciones

**Digitalización:** extender el modelo `Socio` + nuevas entidades para historial de categorías, dignidades y licencias.

### 2.3 Descargo de Responsabilidad (Docs 2 y 3)

Declaración legal de que el aspirante (o su representante) conoce los riesgos del montañismo y exonera al club de responsabilidad.

- **Adultos:** firma el propio aspirante (nombre + cédula + fecha)
- **Pre-Juvenil / Juvenil:** firma el representante legal (nombre + cédula del representante, nombre + fecha de nacimiento del menor)

**Digitalización:** aceptación digital dentro del flujo de solicitud (checkbox con texto legal completo o firma digital simple). Ver sección 4.2 sobre consideraciones legales.

---

## 3. Estado Actual del Modelo de Datos

El modelo `Socio` ya cubre varios campos de la ficha:

| Campo ficha | Estado en la app |
|-------------|-----------------|
| nombre, apellido | ✅ existe |
| cedula | ✅ existe |
| correo, telefono | ✅ existe |
| direccion | ✅ existe |
| fechaNacimiento, fechaIngreso | ✅ existe |
| tipoSangre | ✅ existe |
| tipoSocio (Aspirante/Juvenil/Activo/Vitalicio) | ✅ existe |
| estadoHabilitacion | ✅ existe |
| Contacto emergencia 1 y 2 (nombre, teléfono, dirección) | ✅ existe |
| nivelTecnico | ✅ existe |

---

## 4. Brechas — Qué Falta Modelar

### 4.1 Campos faltantes en entidad `Socio`

Todos son **nullable** — no rompen socios existentes.

| Campo | Tipo sugerido | Notas |
|-------|---------------|-------|
| `nacionalidad` | VARCHAR(100) | |
| `lugarNacimiento` | VARCHAR(200) | país + provincia + cantón libre |
| `estadoCivil` | VARCHAR(50) | enum o texto libre |

| `estatura` | DECIMAL(5,2) | cm |
| `peso` | DECIMAL(5,2) | kg |
| `alergias` | TEXT | descripción libre |
| `lesionesArticulares` | BOOLEAN | |
| `intervencionesQuirurgicas` | BOOLEAN | |

| `contactoEmergencia3Nombre` | VARCHAR(200) | actualmente solo hay 2 |
| `contactoEmergencia3Telefono` | VARCHAR(20) | |
| `contactoEmergencia3Direccion` | TEXT | |
| `contactoEmergencia1Parentesco` | VARCHAR(100) | los 3 necesitan parentesco |
| `contactoEmergencia2Parentesco` | VARCHAR(100) | |
| `contactoEmergencia3Parentesco` | VARCHAR(100) | |
| `otrosEstudios` | TEXT | JSON array o texto libre (4 items) |
| `motivosIngreso` | TEXT | por qué quiso entrar |
| `recomendadoPor` | VARCHAR(200) | nombre del socio que lo invitó |
| `experienciaMontanismo` | TEXT | respuesta a las 3 preguntas del Doc 1 |
| `observacionesSecretaria` | TEXT | solo editable por secretaría |

**Migración:** nueva `V8__ingreso_campos_socio.sql`

### 4.2 Nueva tabla: `solicitudes_ingreso`

Representa el Doc 4. Permite que alguien que **aún no es socio** solicite entrar, antes de que exista su registro en `socios`.

```sql
CREATE TABLE solicitudes_ingreso (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre      VARCHAR(100) NOT NULL,
    apellido    VARCHAR(100) NOT NULL,
    cedula      VARCHAR(20),
    correo      VARCHAR(255) NOT NULL,
    telefono    VARCHAR(20),
    estado      VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
      -- PENDIENTE | ACEPTADA | RECHAZADA
    notas       TEXT,
    respondida_por_id UUID REFERENCES socios(id),
    respondida_en     TIMESTAMP,
    creada_en         TIMESTAMP NOT NULL DEFAULT now(),
    -- Al aceptar, se crea el socio y se guarda la referencia
    socio_creado_id   UUID REFERENCES socios(id)
);
```

**Flujo:**
1. Aspirante (sin cuenta) llena el formulario en la app (o secretaría lo crea manualmente)
2. Secretaría/Directorio recibe notificación y revisa
3. Si acepta → se crea el `Socio` con `tipoSocio = ASPIRANTE` y se vincula
4. Si rechaza → se registra el motivo

### 4.3 Nueva tabla: `descargos_responsabilidad`

Registro digital del Doc 2 (adultos) y Doc 3 (juveniles). Un socio puede tener varios (uno por ingreso, renovables).

```sql
CREATE TABLE descargos_responsabilidad (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    socio_id                UUID NOT NULL REFERENCES socios(id),
    tipo                    VARCHAR(20) NOT NULL,
      -- ADULTO | PRE_JUVENIL | JUVENIL
    firmante_nombre         VARCHAR(200) NOT NULL,
    firmante_cedula         VARCHAR(20) NOT NULL,
    -- Solo para tipo PRE_JUVENIL/JUVENIL:
    representado_nombre     VARCHAR(200),
    representado_fecha_nacimiento DATE,
    fecha_firma             DATE NOT NULL,
    aceptado_digitalmente   BOOLEAN NOT NULL DEFAULT true,
    -- Hash del texto legal aceptado (para auditoría)
    texto_legal_hash        VARCHAR(64),
    creado_en               TIMESTAMP NOT NULL DEFAULT now()
);
```

> **Consideración legal:** La firma digital simple (checkbox de aceptación + timestamp + IP) puede ser suficiente para el contexto del club. Evaluar con la directiva si se requiere firma electrónica cualificada. Por ahora se registra nombre + cédula + fecha + confirmación digital.

### 4.4 Nueva tabla: `historial_categoria_socio`

Reemplaza el rastreo manual de la sección de categorías del Doc 1.

```sql
CREATE TABLE historial_categoria_socio (
    id              SERIAL PRIMARY KEY,
    socio_id        UUID NOT NULL REFERENCES socios(id),
    categoria       VARCHAR(50) NOT NULL,
      -- ASPIRANTE | PRE_JUVENIL | JUVENIL | ACTIVO | VITALICIO
    fecha_inicio    DATE NOT NULL,
    fecha_fin       DATE,
    registrado_por  UUID REFERENCES socios(id),
    creado_en       TIMESTAMP NOT NULL DEFAULT now()
);
```

> El campo `tipoSocio` en `socios` refleja la categoría **actual**. Esta tabla es el historial completo de transiciones.

### 4.5 Nueva tabla: `dignidades_socio`

Para la sección de cargos ocupados del Doc 1.

```sql
CREATE TABLE dignidades_socio (
    id              SERIAL PRIMARY KEY,
    socio_id        UUID NOT NULL REFERENCES socios(id),
    cargo           VARCHAR(100) NOT NULL,
      -- JEFATURA_GENERAL | SUBJEFATURA | TESORERIA | SECRETARIA |
      -- PROSECRETARIA | JEFATURA_MONTANA | JEFATURA_VERANO |
      -- JEFATURA_JUVENILES | TRIBUNAL_ELECTORAL | COMISION_EXTRAORDINARIA
    periodo_inicio  SMALLINT NOT NULL,  -- año
    periodo_fin     SMALLINT,           -- año; NULL = cargo activo
    creado_en       TIMESTAMP NOT NULL DEFAULT now()
);
```

### 4.6 Nueva tabla: `licencias_socio`

Para las licencias/ausencias del Doc 1 (ya existe conceptualmente en FR-016 como estado, aquí se agrega el detalle histórico).

```sql
CREATE TABLE licencias_socio (
    id                  SERIAL PRIMARY KEY,
    socio_id            UUID NOT NULL REFERENCES socios(id),
    causa               TEXT,
    fecha_inicio        DATE NOT NULL,
    fecha_renovacion    DATE,
    fecha_fin           DATE,
    aprobada_por        UUID REFERENCES socios(id),
    creado_en           TIMESTAMP NOT NULL DEFAULT now()
);
```

---

## 5. Documentos Físicos — Cuáles se Eliminan

La directiva confirmó que los siguientes documentos del Doc 1 **ya no se exigen**:

| Documento | Motivo de eliminación |
|-----------|-----------------------|
| Papeleta de votación | No aplica al contexto deportivo actual |
| Carné de tipo de sangre | El dato ya se registra digitalmente en el perfil |
| 2 fotografías tamaño carné | Se remplaza por foto de perfil digital (a definir si se implementa) |
| Examen deportológico | Ya no se solicita |

Los documentos que **sí se mantienen** (aunque digitalizados):
- **Cédula de ciudadanía / pasaporte** — se exige el número; la foto del documento es opcional
- **Solicitud de ingreso** → reemplazada por el flujo digital (sección 4.2)
- **Descargo de responsabilidad** → reemplazado por aceptación digital (sección 4.3)

---

## 6. Flujo Digital Propuesto

```
[Aspirante]
    |
    ▼
Abre la app (sin cuenta) ──► Pantalla "Quiero unirme"
    |
    ▼
Paso 1: Datos básicos (nombre, apellido, cédula, correo, teléfono)
    |
    ▼
Paso 2: Datos personales completos (ficha — parte A y B)
    |
    ▼
Paso 3: Descargo de responsabilidad
         ├─ Mayor de edad → acepta con nombre + cédula + checkbox
         └─ Menor de edad → representante legal llena sus datos + acepta
    |
    ▼
Se crea SolicitudIngreso (estado=PENDIENTE)
Notificación a Secretaría/Directorio
    |
    ▼
[Secretaría/Directorio]
    |
    ▼
Revisa solicitud en la app/web
    ├─ Acepta → se crea Socio (tipoSocio=ASPIRANTE, estadoHabilitacion=HABILITADO)
    │           se registra DescargoDiResponsabilidad
    │           se registra historial_categoria_socio (inicio ASPIRANTE)
    │           se envía email de bienvenida con credenciales temporales
    └─ Rechaza → notificación al aspirante con motivo
```

---

## 7. Preguntas Abiertas / Decisiones Pendientes

Antes de implementar, definir con la directiva:

| # | Pregunta | Opciones |
|---|----------|---------|
| 1 | ¿El aspirante puede iniciar el flujo sin tener cuenta? (público) ¿O lo crea la secretaría manualmente? | Auto-servicio vs. solo admin |
| 2 | ¿Se requiere que el aspirante adjunte foto de su cédula/pasaporte? | Sí / No |
| 3 | ¿El descargo digital (checkbox) tiene validez legal suficiente para el club? | Confirmar con directiva |
| 4 | ¿Los 3 meses de período aspirante se controlan automáticamente? (alerta/auto-transición) | Auto vs. manual |
| 5 | ¿Las dignidades se cargan retroactivamente para socios existentes? | Sí (carga manual) / No (solo nuevos) |
| 6 | ¿La foto de perfil del socio reemplaza las 2 fotos carné? | A definir |

---

## 8. Impacto por Capa

### 8.1 Backend (Spring Boot)

| Área | Cambio |
|------|--------|
| BD | Migración V8: ~20 columnas nuevas en `socios` + 5 tablas nuevas |
| `SolicitudIngresoController` | CRUD + endpoint de aceptar/rechazar |
| `SolicitudIngresoService` | Lógica de validación + creación de `Socio` al aceptar |
| `DescargoDiResponsabilidadController` | Crear / listar por socio |
| `HistorialCategoriaController` | CRUD — acceso solo secretaría |
| `DignidadSocioController` | CRUD — acceso solo secretaría |
| `LicenciaSocioController` | CRUD — acceso solo secretaría |
| `SocioController / SocioService` | Extender `CreateSocioRequest` y `UpdateSocioRequest` con nuevos campos |
| Notificaciones | Email de bienvenida al aceptar solicitud |

### 8.2 Mobile (Flutter)

| Feature | Pantallas nuevas |
|---------|-----------------|
| `ingreso/` (nuevo feature) | `ingreso_screen.dart` — pantalla pública; flujo multi-step (3 pasos) |
| `socios/` | Extender `socio_detail_screen.dart` con pestañas: Dignidades, Licencias, Historial Categorías |
| `perfil/` | Extender `perfil_screen.dart` con campos nuevos (datos médicos, contacto emergencia 3, preguntas de motivación) |
| `admin/` | Panel de solicitudes pendientes con acciones Aceptar/Rechazar |

### 8.3 Frontend Web

| Área | Cambio |
|------|--------|
| Ficha del socio | Nueva sección secretaría: Historial de categorías, Dignidades, Licencias |
| Solicitudes de ingreso | Nueva página de gestión de solicitudes pendientes |
| Formulario crear socio | Nuevos campos del Doc 1 |

---

## 9. Archivos a Crear / Modificar (referencia)

### Backend
```
db/migration/V8__ingreso_campos_socio.sql        -- nueva migración
socios/entity/Socio.java                         -- +20 campos nullable
socios/dto/CreateSocioRequest.java               -- extender
socios/dto/UpdateSocioRequest.java               -- extender
socios/dto/SocioResponse.java                    -- extender
ingreso/                                         -- nuevo package
  entity/SolicitudIngreso.java
  entity/DescargoDiResponsabilidad.java
  entity/HistorialCategoriaSocio.java
  entity/DignidadSocio.java
  entity/LicenciaSocio.java
  controller/SolicitudIngresoController.java
  controller/DescargoDiResponsabilidadController.java
  controller/HistorialCategoriaController.java
  controller/DignidadSocioController.java
  controller/LicenciaSocioController.java
  service/SolicitudIngresoService.java
  dto/ (requests y responses para cada entidad)
```

### Mobile
```
mobile/lib/features/ingreso/              -- nuevo feature
  domain/models/solicitud_ingreso_model.dart
  domain/models/descargo_model.dart
  data/ingreso_remote_data_source.dart
  data/ingreso_repository.dart
  presentation/providers/ingreso_provider.dart
  presentation/screens/ingreso_screen.dart         -- flow público multi-step
  presentation/screens/solicitudes_admin_screen.dart
  presentation/widgets/paso_datos_basicos.dart
  presentation/widgets/paso_datos_personales.dart
  presentation/widgets/paso_descargo.dart

mobile/lib/features/socios/
  presentation/screens/socio_detail_screen.dart    -- + pestañas dignidades/licencias
mobile/lib/features/perfil/
  presentation/screens/perfil_screen.dart          -- + campos nuevos
mobile/lib/features/admin/
  presentation/screens/admin_screen.dart           -- + panel solicitudes
```

### Frontend
```
frontend/src/pages/socios/               -- sección secretaría expandida
frontend/src/pages/solicitudes-ingreso/  -- nueva página
```

---

## 10. Estimación de Complejidad

| Fase | Descripción | Complejidad |
|------|-------------|-------------|
| **Fase 1** | Migración BD + campos nuevos en `Socio` + extender perfil móvil/web | Media |
| **Fase 2** | `SolicitudIngreso` + flujo aprobación (el flujo principal del FR) | Grande |
| **Fase 3** | `DescargoDiResponsabilidad` (digital) + email bienvenida | Media |
| **Fase 4** | `DignidadSocio` + `LicenciaSocio` + `HistorialCategoria` (secretaría) | Media |

Se recomienda implementar en este orden. Fase 1 desbloquea todas las demás.
