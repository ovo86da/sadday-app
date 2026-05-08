# Diagrama 01 — Arquitectura del Sistema y Límites de Confianza

## Arquitectura General

```mermaid
flowchart TD
    subgraph INTERNET["🌐 Zona No Confiable — Internet"]
        USER["👤 Usuario\nNavegador Web"]
        ATTACKER["💀 Atacante"]
    end

    subgraph EDGE["☁️ Edge — Cloudflare"]
        CF_WAF["WAF\nFiltrado de peticiones maliciosas\nSQLi / XSS / RFI"]
        CF_DDOS["Mitigación DDoS\nRate Limiting Global"]
        CF_CDN["CDN\nCaché de assets estáticos"]
    end

    subgraph VPS["🖥️ VPS — AWS Lightsail Ubuntu 24.04"]
        FW["🔥 Firewall UFW/iptables\nSolo IPs de Cloudflare en :80/:443\nBloqueo total desde internet directo"]

        NGINX["🔀 Nginx — Reverse Proxy\n(host, fuera de Docker)\nTLS Termination · CSP headers\nRate Limiting interno\n• /api/* → API :8080\n• /* → Frontend :3000"]

        subgraph DOCKER["Red interna Docker — docker-compose"]
            API["⚙️ Spring Boot API\n:8080\nJava 21 / Spring Boot 3.x\nVirtual Threads"]
            FRONTEND["🌐 React + Vite\n:3000 (nginx:alpine)\nSPA servida como assets estáticos"]
            PG[("🗄️ PostgreSQL\n:5432\nFlyway migrations")]
            MINIO[("📦 MinIO\n:9000 (S3-compatible)\nPDFs cifrados en reposo\nAcceso solo con pre-signed URLs")]
        end
    end

    subgraph CICD["🔧 CI/CD — GitHub Actions"]
        GHA["GitHub Actions Runner\nBuild · Trivy · cosign · SBOM"]
        GHCR["📦 GHCR\nghcr.io (registro de imágenes)\nImágenes firmadas con cosign keyless"]
    end

    subgraph EXTERNAL["☁️ Servicios Externos — HTTPS"]
        EMAIL["📧 Servicio de Email\nSMTP/TLS\nInvitaciones, reset de password"]
    end

    USER -->|"HTTPS :443"| CF_WAF
    ATTACKER -->|"HTTPS :443 — filtrado"| CF_WAF
    ATTACKER -. "Acceso directo bloqueado\npor firewall" .-> FW

    CF_WAF --> CF_DDOS
    CF_DDOS --> CF_CDN
    CF_CDN -->|"HTTPS — solo IPs Cloudflare"| FW
    FW --> NGINX
    NGINX -->|"HTTP — red interna\nCF-Connecting-IP validado"| API
    NGINX -->|"HTTP — red interna"| FRONTEND
    API -->|"JDBC — red Docker interna"| PG
    API -->|"S3 API — red Docker interna"| MINIO
    API -->|"SMTP/TLS"| EMAIL

    GHA -->|"docker push + cosign sign"| GHCR
    GHCR -->|"cosign verify + docker pull"| VPS

    style INTERNET fill:#ffcccc,stroke:#cc0000
    style EDGE fill:#fff3cc,stroke:#cc9900
    style VPS fill:#cce5ff,stroke:#0066cc
    style DOCKER fill:#ccffcc,stroke:#006600
    style CICD fill:#e6e6ff,stroke:#3333cc
    style EXTERNAL fill:#f0e6ff,stroke:#6600cc
```

---

## Límites de Confianza (Trust Boundaries)

```mermaid
flowchart LR
    subgraph TB1["Límite 1: Internet ↔ Cloudflare"]
        direction TB
        L1A["Tráfico no autenticado\nSin confianza"]
        L1B["Cloudflare valida:\nHTTPS, headers, WAF rules"]
        L1A -->|"HTTPS"| L1B
    end

    subgraph TB2["Límite 2: Cloudflare ↔ Firewall VPS"]
        direction TB
        L2A["IPs de Cloudflare\nConfianza parcial"]
        L2B["Firewall: whitelist\nde IPs Cloudflare\nSolo :80/:443"]
        L2A --> L2B
    end

    subgraph TB3["Límite 3: Nginx ↔ Spring Boot / Frontend"]
        direction TB
        L3A["Petición filtrada\nConfianza media"]
        L3B["Spring Boot valida:\nJWT RS256, roles, inputs\nFrontend: assets estáticos con CSP"]
        L3A -->|"localhost/red Docker"| L3B
    end

    subgraph TB4["Límite 4: Spring Boot ↔ PostgreSQL / MinIO"]
        direction TB
        L4A["Consultas de aplicación\nConfianza alta"]
        L4B["Postgres: usuario con\npermisos mínimos\nMinIO: credenciales internas\nSin acceso externo"]
        L4A -->|"red Docker interna"| L4B
    end

    subgraph TB5["Límite 5: CI/CD ↔ GHCR ↔ Producción"]
        direction TB
        L5A["GitHub Actions Runner\ncosign sign (OIDC keyless)"]
        L5B["Deploy: cosign verify\nantes de docker pull\nPull por digest @sha256:..."]
        L5A -->|"ghcr.io"| L5B
    end

    TB1 --> TB2 --> TB3 --> TB4
    TB5
```

---

## Principio de Mínimo Privilegio — Usuarios de Base de Datos

```mermaid
flowchart TD
    subgraph PGUSERS["Usuarios PostgreSQL"]
        APP_USER["sadday_app\n• SELECT, INSERT, UPDATE\n• DELETE solo en tablas permitidas\n• NO DELETE en auditoria\n• NO DROP, TRUNCATE"]
        MIGRATION_USER["sadday_migrations\n• Flyway migrations\n• DDL completo\n• Solo usado en despliegue"]
        BACKUP_USER["sadday_backup\n• SELECT only\n• Para backups automáticos"]
    end

    API["Spring Boot API"] -->|"conexión normal"| APP_USER
    FLYWAY["Flyway\nal iniciar"] -->|"solo en startup"| MIGRATION_USER
    BACKUP["Script\nbackup diario"] -->|"read only"| BACKUP_USER
```
