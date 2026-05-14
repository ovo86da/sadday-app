package com.sadday.app.socios.service;

import com.sadday.app.shared.pdf.PdfRenderService;
import com.sadday.app.socios.entity.Socio;
import com.sadday.app.socios.repository.SocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocioExportService {

    private final SocioRepository  socioRepository;
    private final PdfRenderService pdfRenderService;

    private static final DateTimeFormatter DATE_FMT      = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FMT_FILE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // =========================================================================
    // Definición de campos exportables
    // =========================================================================

    private static final Map<String, Function<Socio, String>> EXTRACTORS = Map.ofEntries(
            Map.entry("apellido",               s -> nvl(s.getApellido())),
            Map.entry("nombre",                 s -> nvl(s.getNombre())),
            Map.entry("cedula",                 s -> nvl(s.getCedula())),
            Map.entry("correo",                 s -> nvl(s.getCorreo())),
            Map.entry("telefono",               s -> nvl(s.getTelefono())),
            Map.entry("fechaNacimiento",         s -> s.getFechaNacimiento() != null ? s.getFechaNacimiento().format(DATE_FMT) : ""),
            Map.entry("edad",                   s -> String.valueOf(s.calcularEdad())),
            Map.entry("fechaIngreso",            s -> s.getFechaIngreso() != null ? s.getFechaIngreso().format(DATE_FMT) : ""),
            Map.entry("antiguedadAnios",         s -> String.valueOf(s.calcularAntiguedad())),
            Map.entry("fechaSalida",             s -> s.getFechaSalida() != null ? s.getFechaSalida().format(DATE_FMT) : ""),
            Map.entry("direccion",               s -> nvl(s.getDireccion())),
            Map.entry("tipoSangre",              s -> nvl(s.getTipoSangre())),
            Map.entry("tipoSocio",               s -> s.getTipoSocio().getNombre()),
            Map.entry("nivelTecnico",            s -> s.getNivelTecnico() != null ? s.getNivelTecnico().getNombre() : ""),
            Map.entry("estadoHabilitacion",      s -> s.getEstadoHabilitacion().getNombre()),
            Map.entry("estadoAcceso",            s -> s.getEstadoAcceso().getCodigo()),
            Map.entry("emergencyContactName",    s -> nvl(s.getEmergencyContactName())),
            Map.entry("emergencyContactPhone",   s -> nvl(s.getEmergencyContactPhone())),
            Map.entry("emergencyContactName2",   s -> nvl(s.getEmergencyContactName2())),
            Map.entry("emergencyContactPhone2",  s -> nvl(s.getEmergencyContactPhone2()))
    );

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("apellido",               "Apellido"),
            Map.entry("nombre",                 "Nombre"),
            Map.entry("cedula",                 "Cédula"),
            Map.entry("correo",                 "Correo electrónico"),
            Map.entry("telefono",               "Teléfono"),
            Map.entry("fechaNacimiento",         "Fecha de nacimiento"),
            Map.entry("edad",                   "Edad (años)"),
            Map.entry("fechaIngreso",            "Fecha de ingreso"),
            Map.entry("antiguedadAnios",         "Antigüedad (años)"),
            Map.entry("fechaSalida",             "Fecha de salida"),
            Map.entry("direccion",               "Dirección"),
            Map.entry("tipoSangre",              "Tipo de sangre"),
            Map.entry("tipoSocio",               "Tipo de socio"),
            Map.entry("nivelTecnico",            "Nivel técnico"),
            Map.entry("estadoHabilitacion",      "Estado habilitación"),
            Map.entry("estadoAcceso",            "Estado de acceso"),
            Map.entry("emergencyContactName",    "Contacto emergencia 1 — nombre"),
            Map.entry("emergencyContactPhone",   "Contacto emergencia 1 — teléfono"),
            Map.entry("emergencyContactName2",   "Contacto emergencia 2 — nombre"),
            Map.entry("emergencyContactPhone2",  "Contacto emergencia 2 — teléfono")
    );

    // =========================================================================
    // Exportar CSV
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public byte[] exportarCsv(List<String> fields, Short tipoId, Short estadoId,
                               boolean excludeAdmin, String q) {
        List<String> validFields = resolveFields(fields);
        List<Socio>  socios      = fetchSocios(tipoId, estadoId, excludeAdmin, q);

        StringBuilder sb = new StringBuilder();
        sb.append(validFields.stream()
                .map(f -> csvEscape(LABELS.getOrDefault(f, f)))
                .collect(Collectors.joining(",")));
        sb.append("\n");
        for (Socio s : socios) {
            sb.append(validFields.stream()
                    .map(f -> csvEscape(EXTRACTORS.getOrDefault(f, x -> "").apply(s)))
                    .collect(Collectors.joining(",")));
            sb.append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // =========================================================================
    // Exportar PDF lista
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public byte[] exportarPdfLista(List<String> fields, Short tipoId, Short estadoId,
                                    boolean excludeAdmin, String q) {
        List<String> validFields = resolveFields(fields);
        if (validFields.size() > 6) {
            validFields = validFields.subList(0, 6);
        }
        List<Socio> socios = fetchSocios(tipoId, estadoId, excludeAdmin, q);

        List<String> headers = validFields.stream()
                .map(f -> LABELS.getOrDefault(f, f))
                .toList();

        final List<String> cols = validFields;
        List<List<String>> rows = socios.stream()
                .map(s -> cols.stream()
                        .map(f -> EXTRACTORS.getOrDefault(f, x -> "").apply(s))
                        .toList())
                .toList();

        Map<String, Object> vars = new HashMap<>();
        vars.put("headers",          headers);
        vars.put("rows",             rows);
        vars.put("generadoEn",       LocalDate.now().format(DATE_FMT));
        vars.put("totalSocios",      socios.size());
        vars.put("headerImageBase64", null);

        return pdfRenderService.render("socios-lista", vars);
    }

    // =========================================================================
    // Exportar PDF hoja de firmas
    // =========================================================================

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public byte[] exportarPdfFirmas(Short tipoId, Short estadoId,
                                     boolean excludeAdmin, String q) {
        List<Socio> socios = fetchSocios(tipoId, estadoId, excludeAdmin, q);

        List<Map<String, String>> filas = socios.stream()
                .map(s -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("cedula",   s.getCedula());
                    row.put("apellido", s.getApellido());
                    row.put("nombre",   s.getNombre());
                    return row;
                })
                .toList();

        Map<String, Object> vars = new HashMap<>();
        vars.put("socios",            filas);
        vars.put("generadoEn",        LocalDate.now().format(DATE_FMT));
        vars.put("totalSocios",       socios.size());
        vars.put("headerImageBase64", null);

        return pdfRenderService.render("socios-firmas", vars);
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    private List<Socio> fetchSocios(Short tipoId, Short estadoId,
                                     boolean excludeAdmin, String q) {
        Specification<Socio> spec = buildSpec(tipoId, estadoId, excludeAdmin, q);
        return socioRepository.findAll(spec, Sort.by("apellido", "nombre"));
    }

    private Specification<Socio> buildSpec(Short tipoId, Short estadoId,
                                            boolean excludeAdmin, String q) {
        Specification<Socio> spec = (root, query, cb) -> cb.conjunction();

        if (estadoId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("estadoHabilitacion").get("id"), estadoId));
        }
        if (tipoId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("tipoSocio").get("id"), tipoId));
        }
        if (excludeAdmin) {
            spec = spec.and((root, query, cb) ->
                    cb.notEqual(cb.lower(root.get("rolSistema").get("nombre")), "admin"));
        }
        if (q != null && !q.isBlank()) {
            String escaped = q.replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombre")), pattern),
                    cb.like(cb.lower(root.get("apellido")), pattern),
                    cb.like(cb.lower(root.get("cedula")), pattern),
                    cb.like(cb.lower(root.get("correo")), pattern)
            ));
        }
        return spec;
    }

    private List<String> resolveFields(List<String> fields) {
        List<String> defaults = List.of("apellido", "nombre", "cedula", "correo", "tipoSocio", "estadoHabilitacion");
        if (fields == null || fields.isEmpty()) {
            return defaults;
        }
        List<String> valid = fields.stream().filter(EXTRACTORS::containsKey).toList();
        return valid.isEmpty() ? defaults : valid;
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        // Neutralize formula injection: Excel/Sheets interpret cells starting with
        // =, +, -, @ as formulas. Prefix with a tab so the cell is read as text.
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "\t" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\t")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
