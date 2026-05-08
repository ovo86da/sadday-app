# Diagrama 05 — Flujo de Datos (DFD) y Clasificación de Datos Sensibles

## DFD Nivel 0 — Vista General del Sistema

```mermaid
flowchart LR
    subgraph ACTORS["Actores externos"]
        UA["👤 Usuario\n(todos los roles)"]
        ADM_SEC["🔐 Admin / Secretaria"]
        DIR["📋 Directivo"]
        JEFE_S["⛰️ Jefe de Salida"]
    end

    subgraph SYSTEM["Sistema Sadday"]
        API(["⚙️ Sadday API\nSpring Boot 3.x / Java 21"])
    end

    subgraph STORES["Almacenes de datos (red Docker interna)"]
        DB[("🗄️ PostgreSQL")]
        MINIO[("📦 MinIO\nS3-compatible\nautohospedado")]
        EMAIL_SVC["📧 Email Service\n(SMTP externo)"]
    end

    UA -->|"Credenciales, datos personales,\ninscripciones"| API
    ADM_SEC -->|"Gestión de socios, actas, invitaciones"| API
    DIR -->|"Aprobaciones, salidas, rutas"| API
    JEFE_S -->|"Informes, dignidades, reconocimientos"| API

    API -->|"PII cifrado en tránsito (TLS/red interna)"| DB
    API -->|"PDFs — pre-signed URL (15 min)"| MINIO
    API -->|"Emails transaccionales (TLS)"| EMAIL_SVC

    DB -->|"Datos consultados"| API
    MINIO -->|"PDFs descargados"| API
    API -->|"Respuestas filtradas por rol (DTOs)"| UA
```

---

## DFD Nivel 1 — Flujo de Datos Sensibles (PII y Credenciales)

```mermaid
flowchart TD
    subgraph INPUT["Entrada de datos sensibles"]
        CRED["🔑 Credenciales\nusername + password"]
        PII["👤 PII\ncédula, correo,\nfecha_nac, contactos emergencia"]
        HEALTH["🏥 Datos de salud\ntipo_sangre"]
        TOTP["🔐 TOTP Secret\nsecreto 2FA"]
        PDF_IN["📄 PDF generado\ninforme / acta"]
        TOKENS["🎟️ Tokens\nrefresh, reset password,\ninvitación email"]
    end

    subgraph PROCESS["Procesamiento (Spring Boot)"]
        HASH_PWD["Argon2id hash\n(nunca almacenar en claro)"]
        ENC_TOTP["Cifrado AES-256-GCM\n(cifrar antes de persistir)"]
        HASH_TOKEN["SHA-256\n(tokens de reset/refresh/invitación)"]
        HASH_PDF["SHA-256\n(integridad del PDF)"]
        FILTER_ROLE["Filtro por rol\n(DTOs específicos por rol)"]
        PDF_GEN["Flying Saucer + OpenPDF\nThymeleaf templates\n(DocumentBuilder hardened contra XXE)"]
    end

    subgraph STORAGE["Almacenamiento"]
        DB_AUTH[("usuarios_auth\npassword_hash (Argon2id)\ntotp_secret (AES-256-GCM cifrado)")]
        DB_TOKENS[("refresh_tokens\npassword_reset_tokens\nemail_verification_tokens\nSOLO hashes SHA-256")]
        DB_PII[("socios\ncédula, correo, PII\ntipo_sangre")]
        MINIO_PDF[("MinIO\nPDFs con UUID aleatorio\nNo accesibles sin pre-signed URL (15 min)")]
        DB_PDF_HASH[("informe_salida / actas_reunion\npdf_url + pdf_hash (SHA-256)")]
    end

    CRED --> HASH_PWD --> DB_AUTH
    TOTP --> ENC_TOTP --> DB_AUTH
    TOKENS --> HASH_TOKEN --> DB_TOKENS
    PII --> DB_PII
    HEALTH --> DB_PII
    PDF_IN --> PDF_GEN
    PDF_GEN --> HASH_PDF --> DB_PDF_HASH
    PDF_GEN --> MINIO_PDF

    DB_PII --> FILTER_ROLE
    FILTER_ROLE -->|"Solo campos permitidos\nsegún rol del solicitante"| OUTPUT["📤 Respuesta API"]
```

---

## Clasificación de Datos y Controles Requeridos

```mermaid
flowchart LR
    subgraph CRITICO["🔴 CRÍTICO"]
        C1["password_hash"]
        C2["totp_secret"]
        C3["token_hash (refresh/reset/invitación)"]
        C4["Tabla auditoria"]
    end

    subgraph ALTO["🟠 ALTO (PII Sensible)"]
        A1["cédula"]
        A2["fecha_nacimiento"]
        A3["contactos de emergencia"]
        A4["tipo_sangre"]
        A5["dirección"]
    end

    subgraph MEDIO["🟡 MEDIO (PII Básica)"]
        M1["nombre + apellido"]
        M2["correo electrónico"]
        M3["teléfono"]
        M4["nivel_tecnico_id"]
    end

    subgraph BAJO["🟢 BAJO (Datos operativos)"]
        B1["Salidas y fechas"]
        B2["Montañas y rutas aprobadas"]
        B3["Dignidades asignadas"]
        B4["Clasificaciones IFAS/UIAA"]
    end

    subgraph CONTROLES_C["Controles — CRÍTICO"]
        CC1["✅ Hash irreversible (Argon2id)"]
        CC2["✅ Cifrado simétrico AES-256-GCM"]
        CC3["✅ Solo hashes SHA-256 para tokens"]
        CC4["✅ Append-only auditoria (sin DELETE grant)"]
    end

    subgraph CONTROLES_A["Controles — ALTO"]
        CA1["✅ TLS en tránsito siempre"]
        CA2["✅ Solo visible a roles autorizados"]
        CA3["✅ Sin exposición en logs"]
        CA4["✅ DTOs que excluyen campos sensibles"]
    end

    CRITICO --> CONTROLES_C
    ALTO --> CONTROLES_A
```

---

## Flujo de Generación y Acceso a PDF (Informes y Actas)

```mermaid
sequenceDiagram
    actor JEFE as Jefe de Salida
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant MINIO as MinIO (S3-compatible)

    JEFE->>API: POST /api/v1/informes/{id}/pdf\n(o /api/v1/actas/{id}/pdf)
    Note over API: Verificar: es_jefe_salida = true<br/>O rol Directivo / Admin

    API->>DB: SELECT datos del informe/acta WHERE id = ?
    API->>API: Thymeleaf.process(template, variables)\n→ XHTML string
    API->>API: DocumentBuilder hardened (XXE deshabilitado)\n→ DOM Document
    API->>API: ITextRenderer.setDocument(dom)\n→ Flying Saucer + OpenPDF\n→ bytes PDF

    API->>API: filename = UUID.randomUUID() + ".pdf"
    API->>API: pdf_hash = SHA256(pdf_bytes)

    API->>MINIO: PUT s3://bucket/informes/{filename}\nContent-Type: application/pdf

    API->>DB: UPDATE informe_salida\nSET pdf_url = minio_key, pdf_hash = hash

    API->>MINIO: Generar pre-signed URL GET (expira 15 min)
    API-->>JEFE: 200 {presignedUrl}
    Note over JEFE: Descarga el PDF con la URL temporal

    actor DIR as Directivo
    DIR->>API: GET /api/v1/informes/{id}/pdf
    Note over API: Verificar: rol Directivo o Admin<br/>O es_jefe_salida = true para esta salida

    API->>DB: SELECT pdf_url, pdf_hash FROM informe_salida
    API->>MINIO: Generar nueva pre-signed URL GET (15 min)
    API-->>DIR: 200 {presignedUrl}
```
