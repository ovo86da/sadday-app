# Configuración de Seguridad — S3 Bucket de PDFs

> Aplica a: AWS S3 o Lightsail Object Storage (compatible S3).
> Propósito: almacenamiento de PDFs generados (informes de salida, actas de reunión).

---

## 1. Bloqueo Total de Acceso Público

**Habilitar las 4 opciones de Block Public Access** en el bucket:

| Opción | Estado |
|--------|--------|
| Block public access to buckets and objects granted through new access control lists (ACLs) | ✅ ON |
| Block public access to buckets and objects granted through any access control lists (ACLs) | ✅ ON |
| Block public access to buckets and objects granted through new public bucket or access point policies | ✅ ON |
| Block public and cross-account access to buckets and objects through any public bucket or access point policies | ✅ ON |

> Los PDFs **nunca son accesibles directamente** desde el browser. Todo acceso pasa por el backend de Spring Boot, que verifica autenticación y autorización antes de descargar el archivo de S3.

---

## 2. IAM — Política de Mínimos Privilegios

Crear un usuario IAM (o IAM Role en producción) con **solo** los permisos necesarios:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SaddayPdfOperations",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::sadday-pdfs-prod/*"
    },
    {
      "Sid": "SaddayBucketList",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::sadday-pdfs-prod"
    }
  ]
}
```

**Prohibiciones explícitas:**
- No `s3:*` (wildcard).
- No acceso a otros buckets.
- No permisos de administración (`s3:DeleteBucket`, `s3:PutBucketPolicy`, etc.).

**En producción (AWS):** usar IAM Role asignado a la instancia EC2 / ECS Task — **nunca access keys hardcodeadas**. Las variables `S3_ACCESS_KEY` / `S3_SECRET_KEY` son solo para dev local (MinIO o Lightsail).

---

## 3. Bucket Policy — Denegación por Defecto

Añadir una bucket policy que deniegue cualquier acceso que no sea HTTPS y que no provenga del principal autorizado:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DenyHTTP",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": [
        "arn:aws:s3:::sadday-pdfs-prod",
        "arn:aws:s3:::sadday-pdfs-prod/*"
      ],
      "Condition": {
        "Bool": { "aws:SecureTransport": "false" }
      }
    }
  ]
}
```

---

## 4. Cifrado en Reposo

- **SSE-S3 (AES-256)**: habilitado por defecto al hacer `PutObject` con `serverSideEncryption: AES256` (ya configurado en `S3StorageService`).
- **Alternativa más segura**: SSE-KMS con una CMK (Customer Managed Key) en AWS KMS — recomendado para cumplimiento con normativas.

Añadir regla de bucket que rechace objetos sin cifrado:
```json
{
  "Sid": "DenyUnencryptedObjectUploads",
  "Effect": "Deny",
  "Principal": "*",
  "Action": "s3:PutObject",
  "Resource": "arn:aws:s3:::sadday-pdfs-prod/*",
  "Condition": {
    "StringNotEquals": {
      "s3:x-amz-server-side-encryption": "AES256"
    }
  }
}
```

---

## 5. Versionado

- **Habilitar versionado** en el bucket.
- Permite recuperar versiones anteriores de un PDF si se regenera accidentalmente.
- Los object keys incluyen el `salidaId`/`actaId` + fecha de generación, así cada regeneración crea un objeto nuevo (no sobrescribe).

---

## 6. Logging de Acceso

- Habilitar **Server Access Logging** hacia un bucket separado (`sadday-logs-prod`).
- Retención de logs: mínimo 90 días.
- Permite auditar quién descargó qué PDF y cuándo.

---

## 7. Lifecycle Rules

```
Regla 1 — Archivos en producción:
  Prefijo: informes/ | actas/
  Transición a S3 Glacier Instant Retrieval: después de 365 días
  Expiración de versiones no actuales: después de 90 días

Regla 2 — Logs:
  Prefijo: (bucket de logs)
  Expiración: 90 días
```

---

## 8. Estructura de Object Keys

```
informes/
  {salidaId}/
    informe-salida-{salidaId}-{yyyy-MM-dd}.pdf

actas/
  {actaId}/
    acta-reunion-{actaId}-{yyyy-MM-dd}.pdf
```

- Agrupar por entidad permite aplicar lifecycle rules por prefijo.
- La fecha en el nombre permite identificar la versión sin consultar metadatos.

---

## 9. Variables de Entorno

| Variable | Descripción | Dev local | Producción |
|----------|-------------|-----------|------------|
| `S3_BUCKET` | Nombre del bucket | `sadday-dev` | `sadday-pdfs-prod` |
| `S3_REGION` | Región AWS | `us-east-1` | Región de Lightsail/EC2 |
| `S3_ACCESS_KEY` | Access key IAM | Key local/MinIO | **vacío** (usar IAM Role) |
| `S3_SECRET_KEY` | Secret key IAM | Secret local/MinIO | **vacío** (usar IAM Role) |

---

## 10. Dev Local con MinIO

Para desarrollo sin cuenta AWS, usar [MinIO](https://min.io/) (S3-compatible):

```yaml
# docker-compose.yml — servicio MinIO para dev
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  ports:
    - "9000:9000"   # S3 API
    - "9001:9001"   # Console web
  environment:
    MINIO_ROOT_USER:     minioadmin
    MINIO_ROOT_PASSWORD: minioadmin123
  volumes:
    - minio-data:/data
```

Configurar el `S3Client` para apuntar a MinIO:
```properties
S3_BUCKET=sadday-dev
S3_REGION=us-east-1
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin123
```

Y añadir en `S3Config.java` el endpoint override para dev local:
```java
// Solo para MinIO en local — usar S3_ENDPOINT variable de entorno
if (!endpoint.isBlank()) {
    builder.endpointOverride(URI.create(endpoint));
}
```
