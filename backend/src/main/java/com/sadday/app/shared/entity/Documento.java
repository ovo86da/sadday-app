package com.sadday.app.shared.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metadatos de un archivo generado (PDF) almacenado en S3 u otro proveedor.
 *
 * <p>No se almacena la URL completa sino sus componentes ({@code storageProvider}
 * + {@code objectKey}). El backend recompone el acceso al archivo bajo demanda,
 * lo que permite migrar de proveedor sin modificar registros de negocio.
 */
@Entity
@Table(name = "documentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 20)
    private StorageProvider storageProvider;

    /** Clave del objeto en el storage (ej: {@code informes/uuid/2026-03-08-informe.pdf}). */
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    /** Nombre original del archivo para el encabezado Content-Disposition. */
    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** SHA-256 del contenido en hexadecimal (64 chars). */
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    /** ETag retornado por S3/MinIO al subir (MD5, 32 hex chars). Null en registros previos. */
    @Column(name = "checksum_md5", length = 32)
    private String checksumMd5;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
