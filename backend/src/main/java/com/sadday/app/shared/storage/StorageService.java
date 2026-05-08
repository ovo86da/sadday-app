package com.sadday.app.shared.storage;

/**
 * Abstracción de almacenamiento de objetos.
 *
 * <p>Implementaciones: {@link S3StorageService} (producción), {@code LocalStorageService} (opcional para dev).
 */
public interface StorageService {

    /**
     * Sube {@code data} al storage bajo la clave {@code objectKey}.
     *
     * @return ETag retornado por el servidor (MD5 hex sin comillas), o {@code null} si no está disponible
     */
    String upload(byte[] data, String objectKey, String contentType);

    /** Descarga el contenido de {@code objectKey}. */
    byte[] download(String objectKey);

    /** Elimina el objeto {@code objectKey} del storage. */
    void delete(String objectKey);
}
