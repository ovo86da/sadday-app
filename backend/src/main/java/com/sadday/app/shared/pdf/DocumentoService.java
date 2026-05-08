package com.sadday.app.shared.pdf;

import com.sadday.app.shared.entity.Documento;
import com.sadday.app.shared.entity.StorageProvider;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.repository.DocumentoRepository;
import com.sadday.app.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Orquesta la subida de documentos al storage y la persistencia de sus metadatos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentoService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx");

    private final StorageService      storageService;
    private final DocumentoRepository documentoRepository;

    /**
     * Sube el PDF a S3, calcula su checksum y persiste los metadatos en BD.
     *
     * @param pdfBytes  contenido del PDF
     * @param objectKey clave en S3 (ej: {@code "informes/uuid/2026-03-08-informe.pdf"})
     * @param filename  nombre para el header {@code Content-Disposition}
     * @return entidad {@link Documento} persistida
     */
    /**
     * Sube el PDF a S3 y persiste los metadatos.
     * Si el storage no está disponible (dev sin MinIO) retorna {@code null}
     * sin lanzar excepción — así no envenena la transacción del llamador.
     */
    @Transactional
    public Documento guardar(byte[] pdfBytes, String objectKey, String filename) {
        String etag;
        try {
            etag = storageService.upload(pdfBytes, objectKey, "application/pdf");
        } catch (Exception e) {
            log.warn("Storage no disponible, PDF no persistido en S3: {}", e.getMessage(), e);
            return null;
        }

        Documento doc = Documento.builder()
                .storageProvider(StorageProvider.S3)
                .objectKey(objectKey)
                .filename(filename)
                .contentType("application/pdf")
                .sizeBytes((long) pdfBytes.length)
                .checksumSha256(sha256Hex(pdfBytes))
                .checksumMd5(etag)
                .build();

        return documentoRepository.save(doc);
    }

    /**
     * Sube un archivo (PDF, Word, Excel) validando tipo y tamaño.
     * A diferencia de {@link #guardar}, acepta cualquier tipo permitido y lee el
     * {@link MultipartFile} directamente en lugar de bytes pre-generados.
     */
    @Transactional
    public Documento guardarArchivo(MultipartFile file, String objectKey) {
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "documento";
        String ext = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error al leer el archivo");
        }

        String etag;
        try {
            etag = storageService.upload(bytes, objectKey, contentType);
        } catch (Exception e) {
            log.warn("Storage no disponible al subir archivo: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error al subir el archivo al storage");
        }

        Documento doc = Documento.builder()
                .storageProvider(StorageProvider.S3)
                .objectKey(objectKey)
                .filename(originalFilename)
                .contentType(contentType)
                .sizeBytes((long) bytes.length)
                .checksumSha256(sha256Hex(bytes))
                .checksumMd5(etag)
                .build();

        return documentoRepository.save(doc);
    }

    /** Descarga los bytes del documento desde el storage. */
    public byte[] descargar(Documento documento) {
        return storageService.download(documento.getObjectKey());
    }

    /** Elimina el archivo del storage. Falla silenciosamente si el storage no está disponible. */
    public void eliminarDelStorage(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (Exception e) {
            log.warn("No se pudo eliminar del storage: key={}, error={}", objectKey, e.getMessage(), e);
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
