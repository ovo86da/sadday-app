# Diagrama 16 — Aceptación de Documentos Legales y Cadena de Evidencia

## Flujo de Aceptación (socio ya registrado — nueva versión publicada)

```mermaid
sequenceDiagram
    actor SOC as Socio
    actor ADM as Admin
    actor SEC as Secretaria
    participant FE as Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL

    Note over ADM,SEC: Admin publica nueva versión del LIABILITY_WAIVER

    ADM->>API: POST /api/v1/admin/legal-documents/{id}/new-version\n{ content: "...nuevo texto..." }
    Note over API: El frontend envía SOLO el texto
    API->>API: SHA256 = sha256(content)
    API->>DB: INSERT legal_documents\n(code='LIABILITY_WAIVER', version=4, active=false, content_hash=SHA256)
    API->>DB: INSERT audit_log (LEGAL_DOCUMENT_VERSION_CREATED)
    API-->>ADM: 201 — versión 4 creada (inactiva)

    ADM->>API: PATCH /api/v1/admin/legal-documents/{nueva_id}/activate
    API->>DB: UPDATE legal_documents SET active=false\nWHERE code='LIABILITY_WAIVER' AND active=true
    API->>DB: UPDATE legal_documents SET active=true WHERE id=nueva_id
    API-->>ADM: 200 — versión 4 activa

    Note over DB: Socios con requires_reacceptance=true\naparecen como "pendientes" en el sistema

    Note over SOC: Próximo login del socio

    SOC->>FE: Inicia sesión
    FE->>API: GET /api/v1/me/profile-completion-status
    API->>DB: ¿Tiene LIABILITY_WAIVER v4 aceptado?\nSELECT COUNT(*) FROM legal_document_acceptances\nWHERE socio_id=? AND document_code='LIABILITY_WAIVER' AND document_version=4
    DB-->>API: 0 — no aceptado
    API-->>FE: { canEnrollActivities: false, pendingDocuments: ["LIABILITY_WAIVER"] }

    FE-->>SOC: Banner: "Hay documentos actualizados que requieren tu aceptación"

    SOC->>FE: Presiona banner o navega a Mis Documentos
    FE->>API: GET /api/v1/legal-documents/LIABILITY_WAIVER/active
    API->>DB: SELECT * FROM legal_documents\nWHERE code='LIABILITY_WAIVER' AND active=true
    DB-->>API: { id, content, version: 4, content_hash: "abc..." }
    API-->>FE: Documento con contenido Markdown

    FE-->>SOC: Muestra el documento con checkbox de aceptación

    SOC->>FE: Lee el documento y marca el checkbox
    FE->>API: POST /api/v1/legal-documents/{id}/accept\n{ documentId: "uuid", documentVersion: 4 }

    Note over API: El backend calcula todo — el frontend no envía hash, IP ni timestamp
    API->>DB: SELECT content FROM legal_documents WHERE id=?
    API->>API: contentHash = sha256(content) — recalculado desde BD
    API->>API: ip = X-Forwarded-For header
    API->>API: userAgent = User-Agent header
    API->>API: acceptedAt = Instant.now() — reloj del servidor

    API->>DB: INSERT legal_document_acceptances\n(\n  socio_id = jwt.sub,\n  legal_document_id = ?,\n  document_code = 'LIABILITY_WAIVER',\n  document_version = 4,\n  content_hash = sha256(content),\n  accepted_at = NOW(),\n  ip_address = '...',\n  user_agent = '...'\n)
    API->>DB: INSERT audit_log\n(LEGAL_DOCUMENT_ACCEPTED, actor=socio_id, resource=legal_document_id)
    API-->>FE: 200 — aceptación registrada

    FE-->>SOC: "Documento aceptado ✓"
    Note over SOC: canEnrollActivities = true si era el único pendiente
```

---

## Cómo verificar una aceptación retrospectivamente

```mermaid
flowchart TD
    A["¿El socio aceptó el documento?"] --> B["Consultar legal_document_acceptances\nWHERE socio_id=? AND document_code=? AND document_version=?"]
    B --> C{"¿Existe la fila?"}
    C -->|No| D["El socio NO aceptó esa versión"]
    C -->|Sí| E["Obtener: accepted_at, ip_address, user_agent, content_hash"]
    E --> F["Obtener contenido del documento\nSELECT content FROM legal_documents\nWHERE code=? AND version=?"]
    F --> G["Calcular SHA-256(content)"]
    G --> H{"¿SHA-256 calculado\n= content_hash guardado?"}
    H -->|Sí| I["✅ El hash coincide — el socio aceptó\nexactamente este texto el {accepted_at}\ndesde IP {ip_address}"]
    H -->|No| J["⚠️ El contenido fue modificado después —\nrevisar historial de la tabla"]
```

---

## Estructura de una aceptación en BD (evidencia legal)

```
legal_document_acceptances
─────────────────────────────────────────────────────────────────
id               | f3a2b1c0-...  (UUID inmutable)
socio_id         | 9e1a3d45-...  (quién aceptó)
legal_document_id| 7c8d2f11-...  (qué documento)
document_code    | LIABILITY_WAIVER
document_version | 4             (qué versión exacta)
content_hash     | a3f2e1...     (SHA-256 del texto exacto)
accepted_at      | 2026-06-07 14:23:11.834+00  (timestamp servidor)
ip_address       | 190.152.xx.xx (del request, no del frontend)
user_agent       | Mozilla/5.0 (iPhone; CPU iPhone OS 18_0...)
accepted         | true
created_at       | 2026-06-07 14:23:11.834+00
─────────────────────────────────────────────────────────────────
NOTA: Esta fila nunca se modifica ni elimina — es append-only.
```
