# Diagrama 11 — Documentos y Generación de PDF (Informes y Actas)

## Visión General — Tipos de Documentos

```mermaid
flowchart LR
    subgraph INFORME["📄 Informe de Salida"]
        I1["Vinculado a una Salida\ncon estado REALIZADA o EN_CURSO"]
        I2["Contiene: crónica, observaciones,\ncostos, participantes, reconocimientos"]
        I3["Puede validarse (firmar) por Directivo/Admin\nbloquea edición posterior"]
        I1 --- I2 --- I3
    end

    subgraph ACTA["📋 Acta de Reunión"]
        A1["Independiente de salidas\n(reuniones del club)"]
        A2["Contiene: actividades realizadas,\npendientes, asistentes"]
        A3["Puede importarse desde archivo .md"]
        A4["Puede vincular informes de salida"]
        A1 --- A2 --- A3 --- A4
    end

    subgraph PDF["📦 Almacenamiento PDF"]
        P1["MinIO (S3-compatible)\nUUID.pdf — nombre no predecible"]
        P2["Pre-signed URL\nexpira en 15 minutos (solo GET)"]
        P3["SHA-256 del PDF\nalmacenado en DB para integridad"]
        P1 --- P2 --- P3
    end

    INFORME -->|"POST /informes/{salidaId}/pdf"| PDF
    ACTA    -->|"POST /actas/{id}/pdf"| PDF
```

---

## Flujo Completo: Informe de Salida (Crear → Editar → Validar → PDF)

```mermaid
sequenceDiagram
    actor JEFE as Jefe de Salida / Directivo / Admin / Secretaria
    actor DIR as Directivo / Admin
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant MINIO as MinIO

    Note over JEFE: La salida debe estar EN_CURSO o REALIZADA

    JEFE->>API: POST /api/v1/informes/{salidaId}\n{cronica, observaciones, costos, ...}
    Note over API: Verifica: Jefe de esta salida,\no Admin/Directivo/Secretaria
    API->>DB: INSERT informe_salida\n(salidaId, validado=false, pdfUrl=null)
    API-->>JEFE: 201 InformeResponse

    JEFE->>API: PUT /api/v1/informes/{salidaId}\n{cronica actualizada, ...}
    Note over API: Verifica mismo acceso + validado=false\n(si ya validado → 409 no permitido)
    API->>DB: UPDATE informe_salida SET ...
    API-->>JEFE: 200 InformeResponse

    JEFE->>API: POST /api/v1/informes/{salidaId}/reconocimientos\n{tipo, descripcion, socioId}
    Note over API: Reconocimientos y amonestaciones
    API->>DB: INSERT informe_salida_reconocimientos
    API-->>JEFE: 201

    Note over DIR: Directivo revisa y valida (firma digital implícita)
    DIR->>API: PATCH /api/v1/informes/{salidaId}/validar
    Note over API: Solo Admin / Directivo\nSi ya validado → idempotente
    API->>DB: UPDATE informe_salida SET\nvalidado=true,\nvalidadoPorId=dirId,\nvalidadoEn=NOW()
    API->>DB: INSERT INTO auditoria (VALIDAR_INFORME)
    API-->>DIR: 200 {validado: true, validadoEn: "..."}
    Note over DIR,API: A partir de aquí el informe NO se puede editar

    Note over DIR: Genera el PDF del informe validado
    DIR->>API: POST /api/v1/informes/{salidaId}/pdf
    Note over API: Solo Admin / Directivo / Secretaria
    API->>DB: SELECT datos completos del informe + participantes + reconocimientos
    API->>API: Thymeleaf.process("informe-salida", variables)\n→ XHTML string
    API->>API: DocumentBuilder hardened (XXE off)\n→ DOM Document
    API->>API: ITextRenderer.setDocument(dom)\n→ Flying Saucer + OpenPDF → bytes PDF
    API->>API: filename = UUID.randomUUID() + ".pdf"
    API->>API: pdf_hash = SHA256(pdf_bytes)
    API->>MINIO: PUT informes/{filename} (Content-Type: application/pdf)
    API->>DB: UPDATE informe_salida SET pdf_url=key, pdf_hash=hash
    API->>MINIO: Generar pre-signed GET URL (expira 15 min)
    API-->>DIR: 200 {presignedUrl: "https://minio/...?X-Amz-Expires=900"}

    Note over DIR: Descarga directa desde la pre-signed URL (sin pasar por la API)
```

---

## Flujo: Descarga de PDF ya Generado

```mermaid
sequenceDiagram
    actor U as Cualquier autenticado (informe)\no Admin/Secretaria (acta)
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant MINIO as MinIO

    U->>API: GET /api/v1/informes/{salidaId}/pdf\n(o GET /api/v1/actas/{id}/pdf)

    API->>DB: SELECT pdf_url, pdf_hash WHERE id = ?
    alt PDF no generado aún (pdf_url = null)
        API-->>U: 404 "El PDF no ha sido generado todavía"
    end

    API->>MINIO: GeneratePresignedUrl(key=pdf_url, expires=15min, method=GET)
    MINIO-->>API: presignedUrl

    API-->>U: 200 {presignedUrl}
    Note over U,MINIO: El cliente descarga directamente de MinIO.\nLa URL expira en 15 min.\nNo pasa por la API.
```

---

## Flujo: Acta de Reunión (con importación desde .md)

```mermaid
sequenceDiagram
    actor SEC as Admin / Secretaria
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant MINIO as MinIO

    alt Crear manualmente
        SEC->>API: POST /api/v1/actas\n{titulo, fecha, tipo, actividades, ...}
        Note over API: Solo Admin / Secretaria
        API->>DB: INSERT acta_reunion
        API-->>SEC: 201 ActaResponse
    end

    alt Importar desde archivo .md
        SEC->>API: POST /api/v1/actas/importar\n(multipart: archivo .md)
        Note over API: Solo preview — no persiste nada
        API->>API: Parsear .md → estructura de acta
        API-->>SEC: 200 ActaImportPreviewResponse\n{titulo, fecha, actividades, asistentes, ...}

        SEC->>API: POST /api/v1/actas/importar/confirmar\n{datos del preview confirmados}
        API->>DB: INSERT acta_reunion + asistentes
        API-->>SEC: 201 ActaResponse
    end

    Note over SEC: Gestión de asistentes
    SEC->>API: POST /api/v1/actas/{id}/asistentes\n{socioId}
    API->>DB: INSERT asistente_reunion
    API-->>SEC: 201

    Note over SEC: Vincular informes de salida al acta
    SEC->>API: POST /api/v1/actas/{id}/informes\n{informeSalidaId}
    API->>DB: INSERT acta_informe_salida (link)
    API-->>SEC: 201

    Note over SEC: Generar PDF del acta
    SEC->>API: POST /api/v1/actas/{id}/pdf
    Note over API: Solo Admin / Secretaria
    API->>API: Thymeleaf.process("acta-reunion", variables)\n→ Flying Saucer + OpenPDF → bytes PDF
    API->>API: pdf_hash = SHA256(pdf_bytes)
    API->>MINIO: PUT actas/{UUID}.pdf
    API->>DB: UPDATE acta_reunion SET pdf_url, pdf_hash
    API->>MINIO: Generar pre-signed GET URL (15 min)
    API-->>SEC: 200 {presignedUrl}
```

---

## Control de Acceso por Rol — Documentos

```mermaid
flowchart TD
    subgraph INFORME_ACC["Informe de Salida"]
        IA1["GET /informes/{salidaId}\nVer informe"]
        IA2["POST / PUT /informes/{salidaId}\nCrear / Editar"]
        IA3["PATCH /informes/{salidaId}/validar\nValidar (firma)"]
        IA4["POST /informes/{salidaId}/pdf\nGenerar PDF"]
        IA5["GET /informes/{salidaId}/pdf\nDescargar PDF"]
    end

    subgraph ACTA_ACC["Acta de Reunión"]
        AA1["GET /actas, GET /actas/{id}\nVer actas"]
        AA2["POST / PUT / DELETE /actas\nGestionar actas"]
        AA3["POST /actas/{id}/pdf\nGenerar PDF"]
        AA4["GET /actas/{id}/pdf\nDescargar PDF"]
    end

    TODOS["Cualquier autenticado"] -->|"✅"| IA1 & IA5 & AA1
    JSD["Jefe de Salida\n+ Admin/Dir/Sec"] -->|"✅"| IA2
    ADMIN_DIR["Admin / Directivo"] -->|"✅"| IA3 & IA4
    ADMIN_SEC["Admin / Secretaria"] -->|"✅"| AA2 & AA3 & AA4

    SOCIO_SIMPLE["Socio sin rol especial"] -->|"❌ 403"| IA2 & IA3 & IA4 & AA2 & AA3 & AA4

    style IA3 fill:#fff3cc,stroke:#cc9900
    style IA4 fill:#fff3cc,stroke:#cc9900
```

---

## Integridad del PDF — Verificación de Hash

```mermaid
flowchart LR
    GENERATE["POST .../pdf\n→ pdf_bytes generados"]
    HASH["SHA256(pdf_bytes)\n→ pdf_hash"]
    STORE["MinIO: almacena pdf_bytes\nDB: almacena pdf_hash"]

    GENERATE --> HASH --> STORE

    VERIFY["Verificación futura (manual / auditoría):\nDescargar PDF desde MinIO\nSHA256(bytes_descargados)\n== pdf_hash en DB?\n→ ✅ íntegro / ❌ manipulado"]

    STORE -.->|"en cualquier momento"| VERIFY

    style VERIFY fill:#e6f3ff,stroke:#0066cc
```
