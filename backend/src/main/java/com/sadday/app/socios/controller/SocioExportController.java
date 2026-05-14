package com.sadday.app.socios.controller;

import com.sadday.app.security.audit.AuditService;
import com.sadday.app.socios.service.SocioExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/socios/exportar")
@RequiredArgsConstructor
@Tag(name = "Socios — Exportación")
public class SocioExportController {

    private final SocioExportService exportService;
    private final AuditService       auditService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Exportar lista de socios en CSV")
    public ResponseEntity<byte[]> csv(
            @RequestParam(required = false) List<String> fields,
            @RequestParam(required = false) Short tipoId,
            @RequestParam(required = false) Short estadoId,
            @RequestParam(defaultValue = "true") boolean excludeAdmin,
            @RequestParam(required = false) String q,
            Authentication auth,
            HttpServletRequest request) {

        byte[] data     = exportService.exportarCsv(fields, tipoId, estadoId, excludeAdmin, q);
        String filename = "socios-" + LocalDate.now().format(DATE_FMT) + ".csv";

        auditService.registrar(auth.getName(), "EXPORTAR_SOCIOS_CSV", "socios",
                null, null, buildDetalle(fields, tipoId, estadoId, excludeAdmin),
                obtenerIp(request), request.getHeader(HttpHeaders.USER_AGENT),
                "SUCCESS", filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(data);
    }

    @GetMapping("/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Exportar lista de socios en PDF (máx. 6 columnas)")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(required = false) List<String> fields,
            @RequestParam(required = false) Short tipoId,
            @RequestParam(required = false) Short estadoId,
            @RequestParam(defaultValue = "true") boolean excludeAdmin,
            @RequestParam(required = false) String q,
            Authentication auth,
            HttpServletRequest request) {

        byte[] data     = exportService.exportarPdfLista(fields, tipoId, estadoId, excludeAdmin, q);
        String filename = "socios-" + LocalDate.now().format(DATE_FMT) + ".pdf";

        auditService.registrar(auth.getName(), "EXPORTAR_SOCIOS_PDF", "socios",
                null, null, buildDetalle(fields, tipoId, estadoId, excludeAdmin),
                obtenerIp(request), request.getHeader(HttpHeaders.USER_AGENT),
                "SUCCESS", filename);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(data);
    }

    @GetMapping("/pdf/firmas")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Exportar hoja de firmas de socios en PDF")
    public ResponseEntity<byte[]> pdfFirmas(
            @RequestParam(required = false) Short tipoId,
            @RequestParam(required = false) Short estadoId,
            @RequestParam(defaultValue = "true") boolean excludeAdmin,
            @RequestParam(required = false) String q,
            Authentication auth,
            HttpServletRequest request) {

        byte[] data     = exportService.exportarPdfFirmas(tipoId, estadoId, excludeAdmin, q);
        String filename = "socios-firmas-" + LocalDate.now().format(DATE_FMT) + ".pdf";

        auditService.registrar(auth.getName(), "EXPORTAR_SOCIOS_FIRMAS", "socios",
                null, null, buildDetalle(null, tipoId, estadoId, excludeAdmin),
                obtenerIp(request), request.getHeader(HttpHeaders.USER_AGENT),
                "SUCCESS", filename);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(data);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static String buildDetalle(List<String> fields, Short tipoId, Short estadoId, boolean excludeAdmin) {
        return String.format("{\"fields\":%s,\"tipoId\":%s,\"estadoId\":%s,\"excludeAdmin\":%b}",
                fields != null ? "\"" + String.join(",", fields) + "\"" : "null",
                tipoId   != null ? tipoId   : "null",
                estadoId != null ? estadoId : "null",
                excludeAdmin);
    }

    private static String obtenerIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
