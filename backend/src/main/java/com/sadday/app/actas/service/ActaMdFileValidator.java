package com.sadday.app.actas.service;

import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Set;

/**
 * Validador de seguridad para archivos .md subidos para importar actas.
 *
 * <p>Capas de defensa aplicadas:
 * <ol>
 *   <li><b>Extensión</b>: el nombre original debe terminar en {@code .md} (case-insensitive).</li>
 *   <li><b>Tamaño</b>: máx. 256 KB (un acta normal tiene pocos KB; el límite también está en
 *       {@code application.yml} como primera barrera en Tomcat/Spring).</li>
 *   <li><b>Content-Type</b>: se admiten los MIME types legítimos de texto plano/markdown.
 *       Aunque el cliente puede manipularlo, combinarlo con las otras capas eleva el coste
 *       del ataque.</li>
 *   <li><b>UTF-8 estricto</b>: el contenido debe ser UTF-8 válido sin secuencias inválidas.
 *       Esto rechaza archivos binarios disfrazados como texto.</li>
 *   <li><b>Bytes nulos</b>: la presencia de bytes {@code 0x00} indica contenido binario
 *       (ejecutables, archivos ofimáticos, etc.).</li>
 *   <li><b>Longitud máxima del texto</b>: el string resultante no puede superar 50 000
 *       caracteres para evitar que el parser sea víctima de un DoS con texto artificialmente
 *       extenso.</li>
 * </ol>
 */
@Component
public class ActaMdFileValidator {

    private static final long   MAX_BYTES    = 256 * 1024L;      // 256 KB
    private static final int    MAX_CHARS    = 50_000;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "application/octet-stream"  // algunos navegadores usan este para .md
    );

    /**
     * Valida el archivo y devuelve su contenido como String UTF-8.
     *
     * @throws BusinessException con {@link ErrorCode#VALIDATION_ERROR} si el archivo
     *         no pasa alguna de las verificaciones.
     */
    public String validarYLeer(MultipartFile file) {
        validarNoVacio(file);
        validarExtension(file);
        validarTamanio(file);
        validarContentType(file);

        byte[] bytes = leerBytes(file);

        validarSinBytesNulos(bytes);
        String contenido = validarUtf8Estricto(bytes);
        validarLongitudTexto(contenido);

        return contenido;
    }

    // =========================================================================
    // Validaciones individuales
    // =========================================================================

    private void validarNoVacio(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El archivo no puede estar vacío");
        }
    }

    private void validarExtension(MultipartFile file) {
        String nombre = file.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".md")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Solo se aceptan archivos con extensión .md");
        }
        // Protección contra path traversal en el nombre del archivo
        if (nombre.contains("..") || nombre.contains("/") || nombre.contains("\\")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Nombre de archivo no válido");
        }
    }

    private void validarTamanio(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El archivo supera el tamaño máximo permitido (256 KB)");
        }
    }

    private void validarContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct == null) return; // si no viene, no bloqueamos (otras capas protegen)
        // Tomar solo la parte base del MIME type (ignorar parámetros como charset)
        String base = ct.split(";")[0].trim().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(base)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Tipo de archivo no permitido: " + base);
        }
    }

    private byte[] leerBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se pudo leer el archivo", e);
        }
    }

    private void validarSinBytesNulos(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0x00) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "El archivo contiene contenido binario y no puede importarse");
            }
        }
    }

    /**
     * Decodifica el contenido con UTF-8 estricto (sin reemplazar secuencias inválidas).
     * Un archivo binario renombrado a .md casi siempre contiene secuencias UTF-8 inválidas.
     */
    private String validarUtf8Estricto(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
            return buffer.toString();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El archivo no es texto UTF-8 válido", e);
        }
    }

    private void validarLongitudTexto(String contenido) {
        if (contenido.length() > MAX_CHARS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El archivo supera los 50 000 caracteres permitidos");
        }
    }
}
