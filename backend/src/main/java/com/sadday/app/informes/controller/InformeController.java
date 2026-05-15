package com.sadday.app.informes.controller;

import com.sadday.app.informes.dto.*;
import com.sadday.app.informes.service.InformeService;
import com.sadday.app.informes.service.PdfInformeService;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.entity.Documento;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controlador del módulo Informes de Salida.
 *
 * <p>Autorización en dos capas (controlador + servicio):
 * <ul>
 *   <li>GET    — autenticado</li>
 *   <li>POST   — Jefe de Salida / Admin / Directivo / Secretaria (validado en servicio)</li>
 *   <li>PUT    — Jefe de Salida / Admin / Directivo / Secretaria (validado en servicio)</li>
 *   <li>PATCH /validar            — Admin / Directivo</li>
 *   <li>POST /reconocimientos     — Jefe de Salida / Admin / Directivo / Secretaria</li>
 *   <li>DELETE /reconocimientos   — Jefe de Salida / Admin / Directivo / Secretaria</li>
 * </ul>
 */
@RestController
@RequestMapping(ApiPaths.INFORMES + "/{salidaId}")
@RequiredArgsConstructor
@Tag(name = "Informes", description = "Informes de salida y reconocimientos")
public class InformeController {

    private final InformeService    informeService;
    private final PdfInformeService pdfInformeService;
    private final AuditService      auditService;

    // =========================================================================
    // Informe CRUD
    // =========================================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener informe de una salida (autenticado). Devuelve data:null si aún no fue creado.")
    public ResponseEntity<ApiResponse<InformeResponse>> obtener(@PathVariable UUID salidaId) {
        return informeService.obtener(salidaId)
                .map(informe -> ResponseEntity.ok(ApiResponse.ok(informe)))
                .orElse(ResponseEntity.ok(ApiResponse.ok("Aún no se ha creado el informe para esta salida", null)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Crear informe de salida. Requiere ser Jefe de Salida, Admin, Directivo o Secretaria.")
    public ResponseEntity<ApiResponse<InformeResponse>> crear(
            @PathVariable UUID salidaId,
            @Valid @RequestBody CreateInformeRequest request,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(informeService.crear(salidaId, request, currentUserId)));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Actualizar informe. Requiere ser Jefe de Salida, Admin, Directivo o Secretaria. No permitido si ya validado.")
    public ResponseEntity<ApiResponse<InformeResponse>> actualizar(
            @PathVariable UUID salidaId,
            @Valid @RequestBody UpdateInformeRequest request,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(informeService.actualizar(salidaId, request, currentUserId)));
    }

    @PatchMapping("/validar")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    @Operation(summary = "Validar (firmar) el informe — Admin o Directivo")
    public ResponseEntity<ApiResponse<Void>> validar(
            @PathVariable UUID salidaId,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        informeService.validar(salidaId, currentUserId);
        return ResponseEntity.ok(ApiResponse.ok("Informe validado correctamente."));
    }

    // =========================================================================
    // Reconocimientos
    // =========================================================================

    @PostMapping("/reconocimientos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Agregar reconocimiento/amonestación. Requiere ser Jefe de Salida, Admin, Directivo o Secretaria.")
    public ResponseEntity<ApiResponse<ReconocimientoResponse>> agregarReconocimiento(
            @PathVariable UUID salidaId,
            @Valid @RequestBody AgregarReconocimientoRequest request,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(informeService.agregarReconocimiento(salidaId, request, currentUserId)));
    }

    @DeleteMapping("/reconocimientos/{reconocimientoId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Eliminar reconocimiento. Requiere ser Jefe de Salida, Admin, Directivo o Secretaria.")
    public ResponseEntity<Void> eliminarReconocimiento(
            @PathVariable UUID salidaId,
            @PathVariable Long reconocimientoId,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        informeService.eliminarReconocimiento(salidaId, reconocimientoId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // PDF
    // =========================================================================

    @PostMapping("/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO', 'SECRETARIA')")
    @Operation(summary = "Generar PDF del informe, subirlo a S3 y descargarlo (Admin/Directivo/Secretaria). " +
                         "Si ya existe un PDF previo se regenera.")
    public ResponseEntity<byte[]> generarPdf(
            @PathVariable UUID salidaId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String username = authentication.getName();
        String ip       = obtenerIp(httpRequest);
        String ua       = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        byte[] pdf        = pdfInformeService.generarYDescargarPdf(salidaId);
        Documento doc     = pdfInformeService.getDocumento(salidaId);
        String filename   = doc != null ? doc.getFilename() : pdfInformeService.getFilename(salidaId);

        auditService.registrar(username, "GENERAR_PDF_INFORME", "informe_salida",
                salidaId, null, docAuditJson(doc, filename),
                ip, ua, "SUCCESS", "PDF del informe de salida generado: " + filename);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    @GetMapping("/pdf")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Descargar PDF del informe ya generado.")
    public ResponseEntity<byte[]> descargarPdf(
            @PathVariable UUID salidaId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String username = authentication.getName();
        String ip       = obtenerIp(httpRequest);
        String ua       = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        byte[] pdf      = pdfInformeService.descargarPdf(salidaId);
        Documento doc   = pdfInformeService.getDocumento(salidaId);
        String filename = doc != null ? doc.getFilename() : pdfInformeService.getFilename(salidaId);

        auditService.registrar(username, "DESCARGAR_PDF_INFORME", "informe_salida",
                salidaId, null, docAuditJson(doc, filename),
                ip, ua, "SUCCESS", "PDF del informe de salida descargado: " + filename);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    // =========================================================================

    private static String docAuditJson(Documento doc, String fallbackFilename) {
        if (doc == null) return "{\"archivo\":\"" + fallbackFilename + "\"}";
        String etag = doc.getChecksumMd5() != null
                ? ",\"etag\":\"" + doc.getChecksumMd5() + "\""
                : "";
        return "{\"archivo\":\"" + doc.getFilename()
                + "\",\"sha256\":\"" + doc.getChecksumSha256()
                + "\",\"bytes\":" + doc.getSizeBytes()
                + etag + "}";
    }

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }

    private String obtenerIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        // X-Forwarded-For puede contener lista: "ip1, ip2, ..." — tomar la primera
        int comma = ip.indexOf(',');
        return comma > 0 ? ip.substring(0, comma).trim() : ip;
    }
}
