package com.sadday.app.actas.controller;

import com.sadday.app.actas.dto.*;
import com.sadday.app.actas.entity.TipoActa;
import com.sadday.app.actas.service.ActaService;
import com.sadday.app.actas.service.ActaMdFileValidator;
import com.sadday.app.actas.service.PdfActaService;
import com.sadday.app.security.audit.AuditService;
import com.sadday.app.shared.entity.Documento;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controlador del módulo Actas de Reunión.
 *
 * <p>Autorización en dos capas (controlador + servicio):
 * <ul>
 *   <li>GET  /                    — autenticado (Socios ven solo tipo SOCIOS; Directivos/Admin/Secretaria ven todas)</li>
 *   <li>POST /                    — Admin / Secretaria</li>
 *   <li>GET  /{id}                — autenticado (Socios bloqueados en actas DIRECTIVA)</li>
 *   <li>PUT  /{id}                — Admin / Secretaria</li>
 *   <li>DELETE /{id}              — Admin / Secretaria</li>
 *   <li>POST /{id}/asistentes     — Admin / Secretaria</li>
 *   <li>DELETE /{id}/asistentes   — Admin / Secretaria</li>
 *   <li>POST /{id}/informes       — Admin / Secretaria</li>
 *   <li>DELETE /{id}/informes     — Admin / Secretaria</li>
 *   <li>POST /{id}/pdf            — Admin / Secretaria (generar)</li>
 *   <li>GET  /{id}/pdf            — Admin / Secretaria (descargar)</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping(ApiPaths.ACTAS)
@RequiredArgsConstructor
@Tag(name = "Actas", description = "Actas de reunión, asistentes e informes vinculados")
public class ActaController {

    private final ActaService         actaService;
    private final PdfActaService      pdfActaService;
    private final ActaMdFileValidator mdFileValidator;
    private final AuditService        auditService;

    // =========================================================================
    // Actas CRUD
    // =========================================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar actas. ?q= activa FTS, ?tipo= filtra por tipo (DIRECTIVA|SOCIOS)")
    public ResponseEntity<ApiResponse<Page<ActaSummaryResponse>>> listar(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) TipoActa tipo,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(actaService.listar(q, tipo, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Crear acta de reunión (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<ActaResponse>> crear(
            @Valid @RequestBody CreateActaRequest request,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(actaService.crear(request, currentUserId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Detalle de un acta con asistentes e informes vinculados")
    public ResponseEntity<ApiResponse<ActaResponse>> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(actaService.obtener(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Actualizar acta (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<ActaResponse>> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateActaRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(actaService.actualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Eliminar acta (Admin/Secretaria)")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        actaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Asistentes
    // =========================================================================

    @PostMapping("/{id}/asistentes")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Agregar asistente al acta (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<AsistenteResponse>> agregarAsistente(
            @PathVariable UUID id,
            @Valid @RequestBody AgregarAsistenteRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(actaService.agregarAsistente(id, request)));
    }

    @DeleteMapping("/{id}/asistentes/{asistenteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Quitar asistente del acta (Admin/Secretaria)")
    public ResponseEntity<Void> eliminarAsistente(
            @PathVariable UUID id,
            @PathVariable Long asistenteId) {

        actaService.eliminarAsistente(id, asistenteId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Informes vinculados
    // =========================================================================

    @PostMapping("/{id}/informes")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Vincular informe de salida al acta (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<InformeLinkResponse>> agregarInforme(
            @PathVariable UUID id,
            @Valid @RequestBody AgregarInformeActaRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(actaService.agregarInforme(id, request)));
    }

    @DeleteMapping("/{id}/informes/{linkId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Desvincular informe del acta (Admin/Secretaria)")
    public ResponseEntity<Void> eliminarInforme(
            @PathVariable UUID id,
            @PathVariable Long linkId) {

        actaService.eliminarInforme(id, linkId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // PDF
    // =========================================================================

    @PostMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Generar PDF del acta y subirlo a S3 (Admin/Secretaria). " +
                         "Si ya existe un PDF previo se regenera.")
    public ResponseEntity<ApiResponse<Void>> generarPdf(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Documento doc = pdfActaService.generarPdf(id);
        auditService.registrar(authentication.getName(), "GENERAR_PDF_ACTA", "actas",
                id, null, docAuditJson(doc), obtenerIp(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT), "SUCCESS", "PDF del acta de reunión generado");
        return ResponseEntity.ok(ApiResponse.ok("PDF generado correctamente."));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Descargar PDF del acta. Socios: todos los perfiles. Directiva: solo Secretaria/Directivo/Admin.")
    public ResponseEntity<byte[]> descargarPdf(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        byte[] pdf      = pdfActaService.descargarPdf(id);
        Documento doc   = pdfActaService.getDocumento(id).orElse(null);
        auditService.registrar(authentication.getName(), "DESCARGAR_PDF_ACTA", "actas",
                id, null, docAuditJson(doc), obtenerIp(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT), "SUCCESS", "PDF del acta de reunión descargado");
        String filename = doc != null ? doc.getFilename() : pdfActaService.getFilename(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    private static String docAuditJson(Documento doc) {
        if (doc == null) return null;
        String etag = doc.getChecksumMd5() != null
                ? ",\"etag\":\"" + doc.getChecksumMd5() + "\""
                : "";
        return "{\"archivo\":\"" + doc.getFilename()
                + "\",\"sha256\":\"" + doc.getChecksumSha256()
                + "\",\"bytes\":" + doc.getSizeBytes()
                + etag + "}";
    }

    private String obtenerIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        int comma = ip.indexOf(',');
        return comma > 0 ? ip.substring(0, comma).trim() : ip;
    }

    // =========================================================================
    // Importación desde .md
    // =========================================================================

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SECRETARIA')")
    @Operation(summary = "Preview de importación de acta desde archivo .md. No persiste nada. " +
                         "Devuelve los datos parseados con los nombres resueltos o con candidatos para revisión.")
    public ResponseEntity<ApiResponse<ActaImportPreviewResponse>> previewImportar(
            @RequestParam("file") MultipartFile file) {

        String contenido = mdFileValidator.validarYLeer(file);
        return ResponseEntity.ok(ApiResponse.ok(actaService.previewImportacion(contenido)));
    }

    @PostMapping("/importar/confirmar")
    @PreAuthorize("hasRole('SECRETARIA')")
    @Operation(summary = "Confirmar y persistir el acta importada desde .md tras revisión del preview.")
    public ResponseEntity<ApiResponse<ActaResponse>> confirmarImportar(
            @Valid @RequestBody ActaImportConfirmRequest request,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(actaService.confirmarImportacion(request, currentUserId)));
    }

    // =========================================================================

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }
}
