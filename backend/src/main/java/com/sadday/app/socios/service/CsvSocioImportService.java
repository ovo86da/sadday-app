package com.sadday.app.socios.service;

import com.sadday.app.auth.service.EmailVerificationService;
import com.sadday.app.shared.exception.BusinessException;
import com.sadday.app.shared.exception.ErrorCode;
import com.sadday.app.socios.dto.CsvSocioImportPreviewResponse;
import com.sadday.app.socios.dto.CsvSocioImportPreviewResponse.FilaValida;
import com.sadday.app.socios.dto.CsvSocioImportPreviewResponse.FilaError;
import com.sadday.app.socios.dto.CsvSocioImportResultResponse;
import com.sadday.app.socios.repository.SocioRepository;
import com.sadday.app.socios.repository.TipoSocioClubRepository;
import com.sadday.app.socios.repository.ClasificacionSocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvSocioImportService {

    private static final long   MAX_SIZE_BYTES = 500 * 1024L;
    private static final int    MAX_ROWS       = 500;
    private static final Set<String> MIME_PERMITIDOS = Set.of(
            "text/csv", "application/vnd.ms-excel", "text/plain", "application/octet-stream");

    // Columnas esperadas en el CSV (case-insensitive)
    private static final String COL_CEDULA        = "cedula";
    private static final String COL_NOMBRE        = "nombre";
    private static final String COL_APELLIDO      = "apellido";
    private static final String COL_CORREO        = "correo";
    private static final String COL_TELEFONO      = "telefono";
    private static final String COL_TIPO_SOCIO    = "tiposocio";
    private static final String COL_NIVEL_TECNICO = "niveltecnico";

    private final SocioRepository            socioRepository;
    private final TipoSocioClubRepository    tipoSocioRepo;
    private final ClasificacionSocioRepository clasifSocioRepo;
    private final EmailVerificationService   emailVerificationService;

    // =========================================================================
    // Preview: parsea y valida sin enviar emails
    // =========================================================================

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Transactional(readOnly = true)
    public CsvSocioImportPreviewResponse preview(MultipartFile file) {
        validarArchivo(file);
        String contenido = decodificarUtf8(leerBytes(file));
        String[] lineas = contenido.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        if (lineas.length < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El CSV debe tener al menos una fila de encabezado y una fila de datos");
        }

        int[] colIdx = resolverColumnas(lineas[0]);

        // Valores válidos de BD para validar tipoSocio y nivelTecnico
        Set<String> tiposValidos   = new HashSet<>();
        tipoSocioRepo.findAll().forEach(t -> tiposValidos.add(t.getNombre().toLowerCase()));
        Set<String> nivelesValidos = new HashSet<>();
        clasifSocioRepo.findAll().forEach(c -> nivelesValidos.add(c.getNombre().toLowerCase()));

        List<FilaValida> validas = new ArrayList<>();
        List<FilaError>  errores = new ArrayList<>();

        // Rastrear duplicados dentro del mismo CSV
        Set<String> cedulasEnCsv = new HashSet<>();
        Set<String> correosEnCsv = new HashSet<>();

        for (int i = 1; i < lineas.length && (validas.size() + errores.size()) < MAX_ROWS; i++) {
            String linea = lineas[i].trim();
            if (linea.isBlank()) continue;

            String[] cols = parsearFila(linea);
            int fila = i + 1;

            String cedula       = col(cols, colIdx[0]);
            String nombre       = col(cols, colIdx[1]);
            String apellido     = col(cols, colIdx[2]);
            String correo       = col(cols, colIdx[3]);
            String telefono     = col(cols, colIdx[4]);
            String tipoSocio    = col(cols, colIdx[5]);
            String nivelTecnico = col(cols, colIdx[6]);

            String error = validarFila(fila, cedula, nombre, apellido, correo,
                    tipoSocio, nivelTecnico, tiposValidos, nivelesValidos,
                    cedulasEnCsv, correosEnCsv);

            if (error != null) {
                errores.add(new FilaError(fila, cedula, correo, error));
            } else {
                cedulasEnCsv.add(cedula.toLowerCase());
                correosEnCsv.add(correo.toLowerCase());
                validas.add(new FilaValida(fila, cedula, nombre, apellido, correo,
                        telefono, tipoSocio, nivelTecnico));
            }
        }

        log.info("Preview CSV socios: validas={}, errores={}", validas.size(), errores.size());
        return new CsvSocioImportPreviewResponse(validas.size() + errores.size(), validas, errores);
    }

    // =========================================================================
    // Confirmar: crea tokens y envía correos para las filas válidas
    // =========================================================================

    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Transactional
    public CsvSocioImportResultResponse confirmar(List<FilaValida> filas) {
        if (filas == null || filas.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No hay filas para importar");
        }

        int importados = 0;
        List<FilaError> errores = new ArrayList<>();

        for (FilaValida fila : filas) {
            try {
                emailVerificationService.sendCsvImportInvitation(
                        fila.cedula(), fila.correo(), fila.telefono(),
                        fila.nombre(), fila.apellido(),
                        fila.tipoSocio(), fila.nivelTecnico());
                importados++;
            } catch (BusinessException e) {
                errores.add(new FilaError(fila.fila(), fila.cedula(), fila.correo(), e.getMessage()));
                log.warn("Error al importar fila {}: {}", fila.fila(), e.getMessage(), e);
            }
        }

        log.info("Importación CSV socios: importados={}, errores={}", importados, errores.size());
        return new CsvSocioImportResultResponse(importados, errores.size(), errores);
    }

    // =========================================================================
    // Validación de fila
    // =========================================================================

    private String validarFila(int fila, String cedula, String nombre, String apellido,
                                String correo, String tipoSocio, String nivelTecnico,
                                Set<String> tiposValidos, Set<String> nivelesValidos,
                                Set<String> cedulasEnCsv, Set<String> correosEnCsv) {
        if (cedula.isBlank())   return "La cédula es obligatoria";
        if (nombre.isBlank())   return "El nombre es obligatorio";
        if (apellido.isBlank()) return "El apellido es obligatorio";
        if (correo.isBlank())   return "El correo es obligatorio";
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return "Correo inválido: " + correo;

        if (cedulasEnCsv.contains(cedula.toLowerCase()))
            return "Cédula duplicada en el CSV: " + cedula;
        if (correosEnCsv.contains(correo.toLowerCase()))
            return "Correo duplicado en el CSV: " + correo;

        if (socioRepository.existsByCedula(cedula))
            return "Cédula ya registrada en el sistema: " + cedula;
        if (socioRepository.existsByCorreo(correo))
            return "Correo ya registrado en el sistema: " + correo;

        if (!tipoSocio.isBlank() && !tiposValidos.contains(tipoSocio.toLowerCase()))
            return "Tipo de socio no reconocido: '" + tipoSocio + "'";

        if (!nivelTecnico.isBlank() && !nivelesValidos.contains(nivelTecnico.toLowerCase()))
            return "Nivel técnico no reconocido: '" + nivelTecnico + "'";

        return null;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Resuelve índices de columna desde el encabezado (case-insensitive, sin espacios). */
    private int[] resolverColumnas(String headerLine) {
        String[] headers = parsearFila(headerLine);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            idx.put(headers[i].toLowerCase().replaceAll("[^a-záéíóú]", ""), i);
        }

        String[] requeridas = { COL_CEDULA, COL_NOMBRE, COL_APELLIDO, COL_CORREO };
        for (String col : requeridas) {
            if (!idx.containsKey(col)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "El CSV debe tener la columna '" + col + "'");
            }
        }

        return new int[]{
            idx.getOrDefault(COL_CEDULA,        -1),
            idx.getOrDefault(COL_NOMBRE,         -1),
            idx.getOrDefault(COL_APELLIDO,       -1),
            idx.getOrDefault(COL_CORREO,         -1),
            idx.getOrDefault(COL_TELEFONO,       -1),
            idx.getOrDefault(COL_TIPO_SOCIO,     -1),
            idx.getOrDefault(COL_NIVEL_TECNICO,  -1),
        };
    }

    private String col(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return "";
        return cols[idx].trim();
    }

    /** Parser CSV simple: soporta campos entre comillas dobles. */
    private String[] parsearFila(String linea) {
        List<String> campos = new ArrayList<>();
        boolean enComillas  = false;
        StringBuilder actual = new StringBuilder();

        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (enComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    enComillas = !enComillas;
                }
            } else if (c == ',' && !enComillas) {
                campos.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString().trim());
        return campos.toArray(new String[0]);
    }

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El archivo está vacío");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El archivo supera el límite de 500 KB");
        }
        String ct = file.getContentType();
        if (ct != null && !MIME_PERMITIDOS.contains(ct.toLowerCase().split(";")[0].trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Tipo de archivo no permitido: " + ct);
        }
        String fn = file.getOriginalFilename();
        if (fn == null || !fn.toLowerCase().endsWith(".csv")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El archivo debe tener extensión .csv");
        }
    }

    private byte[] leerBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No se pudo leer el archivo", e);
        }
    }

    private String decodificarUtf8(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
            String s = buffer.toString();
            return s.startsWith("\uFEFF") ? s.substring(1) : s; // strip BOM
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El archivo no es texto UTF-8 válido", e);
        }
    }
}
