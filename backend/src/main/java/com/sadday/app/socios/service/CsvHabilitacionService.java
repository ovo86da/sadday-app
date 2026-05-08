package com.sadday.app.socios.service;

import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.shared.storage.StorageService;
import com.sadday.app.socios.dto.CsvHabilitacionResult;
import com.sadday.app.socios.dto.CsvHabilitacionResult.FilaError;
import com.sadday.app.socios.entity.EstadoHabilitacion;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.entity.SocioHabilitacionLog;
import com.sadday.app.socios.repository.EstadoHabilitacionRepository;
import com.sadday.app.socios.repository.SocioHabilitacionLogRepository;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Procesa cargas masivas de CSV para habilitar/inhabilitar socios.
 *
 * <p>Formato esperado del CSV (acción por fila):
 * <pre>
 * Nombre,Cedula,Estado
 * Juan Pérez,0102030405,Habilitado
 * María López,0506070809,Habilitado
 * Ana Torres,1709876543,Deshabilitado
 * </pre>
 *
 * <p>Valores aceptados en la columna Estado (sin distinción de mayúsculas):
 * {@code Habilitado} / {@code Deshabilitado}.
 *
 * <p>Restricciones de seguridad:
 * <ul>
 *   <li>Solo archivos .csv con Content-Type text/csv o application/vnd.ms-excel</li>
 *   <li>Máximo 500 KB</li>
 *   <li>Máximo 1 000 filas de datos</li>
 *   <li>Socios Vitalicios son intocables</li>
 *   <li>Celdas con fórmulas Excel (=, +, -, @) son rechazadas</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsvHabilitacionService {

    private static final long   MAX_SIZE_BYTES = 500 * 1024L; // 500 KB
    private static final int    MAX_ROWS       = 1_000;
    private static final Set<String> MIME_PERMITIDOS = Set.of(
            "text/csv", "application/vnd.ms-excel",
            "text/plain",          // algunos navegadores envían esto para .csv
            "application/octet-stream"  // fallback genérico de algunos OS
    );

    private static final String ESTADO_HABILITADO   = "Habilitado";
    private static final String ESTADO_INHABILITADO = "Inhabilitado";
    private static final String ESTADO_VITALICIO    = "Socio Vitalicio";

    private final SocioRepository              socioRepository;
    private final EstadoHabilitacionRepository estadoHabRepo;
    private final SocioHabilitacionLogRepository logRepository;
    private final StorageService               storageService;

    @Transactional
    public CsvHabilitacionResult procesarCsv(MultipartFile file, UUID realizadoPorId) {
        // ── 1. Validar y leer archivo ──────────────────────────────────────────
        validarArchivo(file);
        byte[] bytes = leerBytes(file);
        validarSinBytesNulos(bytes);
        String contenido = stripBom(decodificarUtf8Estricto(bytes));

        // ── 2. Parsear CSV ─────────────────────────────────────────────────────
        List<String[]> rows = parsearCsv(contenido);

        // ── 3. Subir a S3 (no crítico — fallo es ignorado) ────────────────────
        String csvKey = null;
        try {
            csvKey = buildObjectKey(file.getOriginalFilename());
            storageService.upload(bytes, csvKey, "text/csv");
            log.info("CSV de habilitación subido a S3: {}", csvKey);
        } catch (Exception e) {
            log.warn("No se pudo subir el CSV a S3 (continúa sin clave): {}", e.getMessage(), e);
            csvKey = null;
        }

        // ── 4. Procesar en transacción DB ──────────────────────────────────────
        return procesarEnTransaccion(rows, realizadoPorId, csvKey, file.getOriginalFilename());
    }

    private CsvHabilitacionResult procesarEnTransaccion(
            List<String[]> rows, UUID realizadoPorId,
            String csvKey, String nombreArchivo) {


        EstadoHabilitacion estadoVitalicio    = findEstado(ESTADO_VITALICIO);
        Socio realizadoPor = socioRepository.findById(realizadoPorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Socio ejecutor no encontrado"));

        int habilitados = 0, deshabilitados = 0, sinCambio = 0;
        List<FilaError> errores = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int fila = i + 2; // +2: fila 1 es encabezado, índice es 0-based
            String[] row = rows.get(i);

            // El CSV tiene: Nombre (col 0), Cédula (col 1), Estado (col 2)
            String nombre = row.length > 0 ? limpiarCelda(row[0]) : "";
            if (row.length < 3) {
                errores.add(new FilaError(fila, nombre, "", "Fila con formato incorrecto (esperado: Nombre,Cedula,Estado)"));
                continue;
            }

            String cedula = limpiarCelda(row[1]);
            if (cedula.isBlank()) {
                errores.add(new FilaError(fila, nombre, cedula, "Cédula vacía"));
                continue;
            }

            if (esFórmulaCsv(cedula)) {
                errores.add(new FilaError(fila, nombre, cedula, "Valor de cédula no permitido"));
                continue;
            }

            // Resolver estado destino desde la columna Estado
            String estadoColumna = limpiarCelda(row[2]);
            EstadoHabilitacion estadoTarget = resolverEstadoDestino(estadoColumna);
            if (estadoTarget == null) {
                errores.add(new FilaError(fila, nombre, cedula,
                        "Estado inválido: '" + estadoColumna + "'. Use 'Habilitado' o 'Deshabilitado'"));
                continue;
            }

            Optional<Socio> socioOpt = socioRepository.findByCedula(cedula);
            if (socioOpt.isEmpty()) {
                errores.add(new FilaError(fila, nombre, cedula, "Cédula no encontrada en el sistema"));
                continue;
            }

            Socio socio = socioOpt.get();

            if (socio.getEstadoHabilitacion().getId().equals(estadoVitalicio.getId())) {
                errores.add(new FilaError(fila, nombre, cedula,
                        socio.getNombre() + " " + socio.getApellido() + " es Socio Vitalicio y no puede ser modificado"));
                continue;
            }

            String rolNombre = socio.getRolSistema().getNombre().toUpperCase();
            if (estadoTarget.getNombre().equals(ESTADO_INHABILITADO) &&
                    (rolNombre.equals("ADMIN") || rolNombre.equals("SECRETARIA"))) {
                errores.add(new FilaError(fila, nombre, cedula,
                        socio.getNombre() + " " + socio.getApellido()
                        + " tiene rol " + socio.getRolSistema().getNombre() + " y no puede ser inhabilitado"));
                continue;
            }

            if (socio.getEstadoHabilitacion().getId().equals(estadoTarget.getId())) {
                sinCambio++;
                continue;
            }

            EstadoHabilitacion estadoAnterior = socio.getEstadoHabilitacion();
            socio.setEstadoHabilitacion(estadoTarget);
            socioRepository.save(socio);

            String notas = "Fila " + fila + " — " + (nombreArchivo != null ? nombreArchivo : "CSV");
            logRepository.save(SocioHabilitacionLog.builder()
                    .socio(socio)
                    .estadoAnterior(estadoAnterior)
                    .estadoNuevo(estadoTarget)
                    .cambiadoPor(realizadoPor)
                    .cambiadoEn(OffsetDateTime.now(ZoneOffset.UTC))
                    .fuente("CSV")
                    .csvKey(csvKey)
                    .notas(notas)
                    .build());

            if (estadoTarget.getNombre().equals(ESTADO_HABILITADO)) habilitados++;
            else deshabilitados++;
        }

        int procesados = habilitados + deshabilitados + sinCambio;
        log.info("CSV habilitación: procesados={}, habilitados={}, deshabilitados={}, sinCambio={}, errores={}",
                procesados, habilitados, deshabilitados, sinCambio, errores.size());

        return new CsvHabilitacionResult(procesados, habilitados, deshabilitados, sinCambio, errores, csvKey);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El archivo CSV no puede estar vacío");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El archivo debe tener extensión .csv");
        }
        // Protección contra path traversal en el nombre del archivo
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nombre de archivo no válido");
        }

        String contentType = file.getContentType();
        if (contentType != null && !MIME_PERMITIDOS.contains(contentType.toLowerCase().split(";")[0].trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Tipo de contenido no permitido: " + contentType + ". Use un archivo CSV.");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El archivo supera el tamaño máximo permitido (500 KB)");
        }
    }

    private byte[] leerBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "No se pudo leer el archivo CSV", e);
        }
    }

    /** Rechaza archivos con bytes nulos (0x00), que indican contenido binario disfrazado de CSV. */
    private void validarSinBytesNulos(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0x00) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "El archivo contiene contenido binario y no puede procesarse");
            }
        }
    }

    /**
     * Decodifica el contenido con UTF-8 estricto (sin reemplazar secuencias inválidas).
     * Un archivo binario renombrado a .csv casi siempre contiene secuencias UTF-8 inválidas.
     */
    private String decodificarUtf8Estricto(byte[] bytes) {
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

    /** Elimina el BOM UTF-8 (EF BB BF) que Excel agrega al guardar como CSV. */
    private String stripBom(String s) {
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }

    /**
     * Parsea el CSV: salta encabezado, normaliza CRLF, devuelve filas de datos.
     * Soporta campos entre comillas dobles.
     */
    private List<String[]> parsearCsv(String contenido) {
        String[] lines = contenido.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        if (lines.length < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El CSV debe tener al menos una fila de encabezado y una fila de datos");
        }

        // Validar encabezado (flexible: tolera variaciones de mayúsculas y espacios)
        String header = lines[0].toLowerCase().replace(" ", "");
        if (!header.contains("cedula") && !header.contains("cédula")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El CSV debe tener una columna 'Cedula' en la primera fila");
        }
        if (!header.contains("estado")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El CSV debe tener una columna 'Estado' con los valores 'Habilitado' o 'Deshabilitado'");
        }

        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;                    // ignorar líneas vacías

            if (rows.size() >= MAX_ROWS) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "El CSV supera el límite de " + MAX_ROWS + " filas de datos");
            }

            rows.add(splitCsvLine(line));
        }

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El CSV no contiene filas de datos");
        }

        return rows;
    }

    /**
     * Split simple de una línea CSV respetando campos entre comillas dobles.
     * No soporta comillas escapadas (suficiente para nombre + cédula).
     */
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields.toArray(new String[0]);
    }

    /** Limpia espacios extra y comillas residuales de una celda. */
    private String limpiarCelda(String valor) {
        if (valor == null) return "";
        String limpio = valor.trim();
        if (limpio.startsWith("\"") && limpio.endsWith("\"") && limpio.length() >= 2) {
            limpio = limpio.substring(1, limpio.length() - 1).trim();
        }
        return limpio;
    }

    /**
     * Mapea el valor de la columna Estado al EstadoHabilitacion correspondiente.
     * Acepta "Habilitado"/"habilitado" y "Deshabilitado"/"deshabilitado".
     * Devuelve null si el valor no es reconocido.
     */
    private EstadoHabilitacion resolverEstadoDestino(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String v = valor.trim().toLowerCase();
        if (v.equals("habilitado"))    return findEstado(ESTADO_HABILITADO);
        if (v.equals("deshabilitado")) return findEstado(ESTADO_INHABILITADO);
        return null;
    }

    /** Detecta intentos de CSV injection (fórmulas Excel). */
    private boolean esFórmulaCsv(String valor) {
        if (valor.isEmpty()) return false;
        char first = valor.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }

    private EstadoHabilitacion findEstado(String nombre) {
        return estadoHabRepo.findByNombre(nombre)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Estado de habilitación no encontrado: " + nombre));
    }

    private String buildObjectKey(String originalFilename) {
        LocalDate hoy = LocalDate.now();
        String ts = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombre = (originalFilename != null)
                ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_")
                : "habilitacion.csv";
        return String.format("habilitacion-csv/%04d/%02d/%s_%s",
                hoy.getYear(), hoy.getMonthValue(), ts, nombre);
    }
}
