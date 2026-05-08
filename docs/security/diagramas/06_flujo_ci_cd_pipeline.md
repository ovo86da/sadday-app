# Diagrama 06 — Pipeline CI/CD de Seguridad

## Visión General del Pipeline

```mermaid
flowchart TD
    subgraph TRIGGER["Disparadores"]
        T1["Push a main\n→ Deploy producción"]
        T2["Push a develop\n→ Deploy staging"]
        T3["workflow_dispatch\n(manual, elige ambiente)"]
    end

    subgraph CI["GitHub Actions — Jobs paralelos"]
        subgraph BB["build-backend"]
            BB1["Checkout"]
            BB2["Build imagen local\n(sin push)"]
            BB3["Trivy scan\nCRITICAL/HIGH\n→ bloquea si hay CVEs"]
            BB4["Push a GHCR\nghcr.io/org/repo:sha"]
            BB5["cosign sign\n(keyless OIDC)"]
            BB6["SBOM CycloneDX\n(anchore/sbom-action)"]
            BB7["cosign attest SBOM"]
        end

        subgraph BF["build-frontend"]
            BF1["Checkout"]
            BF2["Build imagen local\n(sin push)"]
            BF3["Trivy scan\nCRITICAL/HIGH\n→ bloquea si hay CVEs"]
            BF4["Push a GHCR\nghcr.io/org/repo-frontend:sha"]
            BF5["cosign sign\n(keyless OIDC)"]
            BF6["SBOM CycloneDX"]
            BF7["cosign attest SBOM"]
        end
    end

    subgraph DEPLOY_STAGING["deploy-staging (develop → Proxmox VM)"]
        DQ1["cosign verify\nbackend + frontend\n→ bloquea si firma inválida"]
        DQ2["SCP docker-compose al servidor"]
        DQ3["SSH: docker login GHCR"]
        DQ4["SSH: docker pull\npor digest @sha256:...\n(no por tag)"]
        DQ5["docker compose up\n--pull never"]
        DQ6["Health check\nGET /actuator/health\n(8 intentos, 10s entre c/u)"]
    end

    subgraph DEPLOY_PROD["deploy (main → Lightsail)"]
        DP1["cosign verify\nbackend + frontend\n→ bloquea si firma inválida"]
        DP2["SCP docker-compose al servidor"]
        DP3["SSH: docker login GHCR"]
        DP4["SSH: docker pull\npor digest @sha256:...\n(no por tag)"]
        DP5["docker compose up\n--pull never api frontend"]
        DP6["Health check\nGET /actuator/health\n(10 intentos, 15s entre c/u)"]
    end

    T1 & T2 & T3 --> BB & BF
    BB & BF -->|"needs: ambos builds OK"| DEPLOY_STAGING
    BB & BF -->|"needs: ambos builds OK"| DEPLOY_PROD

    BB1 --> BB2 --> BB3 --> BB4 --> BB5 --> BB6 --> BB7
    BF1 --> BF2 --> BF3 --> BF4 --> BF5 --> BF6 --> BF7
    DQ1 --> DQ2 --> DQ3 --> DQ4 --> DQ5 --> DQ6
    DP1 --> DP2 --> DP3 --> DP4 --> DP5 --> DP6

    style BB3 fill:#ffcccc,stroke:#cc0000
    style BF3 fill:#ffcccc,stroke:#cc0000
    style DQ1 fill:#ccffcc,stroke:#006600
    style DP1 fill:#ccffcc,stroke:#006600
```

---

## Flujo de Firma y Verificación cosign (Cadena de Confianza)

```mermaid
sequenceDiagram
    participant GHA as GitHub Actions Runner
    participant GHCR as GHCR (ghcr.io)
    participant REKOR as Rekor (Sigstore Transparency Log)
    participant SERVER as Servidor (Lightsail / Proxmox)

    Note over GHA: Build job — después de push exitoso

    GHA->>GHA: cosign sign --yes IMAGE@DIGEST
    Note over GHA: OIDC token del runner: identidad =<br/>workflow/.github/workflows/deploy.yml@refs/heads/main

    GHA->>REKOR: Registrar entrada de firma\n(certificado x509 + digest + timestamp)
    REKOR-->>GHA: Confirmation + entry UUID
    GHA->>GHCR: Adjuntar firma como OCI artifact\n(sha256:... → sig artifact)

    GHA->>GHA: cosign attest --predicate sbom.cdx.json
    GHA->>GHCR: Adjuntar attestation SBOM\ncomo OCI artifact

    Note over SERVER: Deploy job — antes de docker pull

    GHA->>GHA: cosign verify\n--certificate-identity=WORKFLOW_URL\n--certificate-oidc-issuer=token.actions.githubusercontent.com\nIMAGE@DIGEST

    GHA->>GHCR: Descargar firma OCI del digest
    GHA->>REKOR: Verificar entrada en transparency log
    REKOR-->>GHA: Firma válida / inválida

    alt Firma inválida o no encontrada
        GHA-->>GHA: ERROR — Deploy abortado
        Note over GHA: El servidor NUNCA recibe la imagen
    end

    GHA->>SERVER: SSH: docker pull IMAGE@sha256:DIGEST
    Note over SERVER: Pull por digest — imposible swap entre<br/>verificación y descarga
    SERVER->>GHCR: docker pull IMAGE@sha256:DIGEST
    GHCR-->>SERVER: Imagen exacta verificada
```

---

## Flujo de Escaneo Trivy (Bloqueo por Vulnerabilidades)

```mermaid
flowchart TD
    A["Build imagen local\n(no push aún)"] --> B["trivy image\n--severity CRITICAL,HIGH\n--exit-code 1"]

    B --> C{¿CVEs encontrados?}

    C -->|"CRITICAL o HIGH\nsin excepción"| D["❌ Exit code 1\nJob falla\nDeploy bloqueado"]
    C -->|"Sin CVEs o solo MEDIUM/LOW"| E["✅ Continúa\nUpload SARIF → GitHub Code Scanning"]

    E --> F["Push imagen a GHCR"]
    D --> G["SARIF subido igualmente\n(if: always())\nVisible en Security tab de GitHub"]

    style D fill:#ffcccc,stroke:#cc0000
    style E fill:#ccffcc,stroke:#006600
```

---

## Concurrencia y Rollback

```mermaid
flowchart LR
    subgraph CONCURRENCY["Grupo de concurrencia"]
        CG["group: deploy-{github.ref_name}\ncancel-in-progress: false"]
        NOTE["Si un deploy está en curso,\nel siguiente espera en cola.\nNO cancela el deploy activo."]
    end

    subgraph ROLLBACK["Rollback manual"]
        R1["workflow_dispatch"]
        R2["Ingresar image_tag anterior\n(SHA del commit previo)"]
        R3["Pipeline re-verifica la firma\ndel tag anterior"]
        R4["Deploy del tag verificado"]
        R1 --> R2 --> R3 --> R4
    end
```
