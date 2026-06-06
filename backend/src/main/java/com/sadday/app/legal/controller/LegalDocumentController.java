package com.sadday.app.legal.controller;

import com.sadday.app.legal.dto.*;
import com.sadday.app.legal.service.LegalDocumentAcceptanceService;
import com.sadday.app.legal.service.LegalDocumentService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Controlador del módulo de Documentos Legales.
 *
 * <p>Autorización por endpoint:
 * <ul>
 *   <li>GET  /legal-documents/active              — público (pre-auth)</li>
 *   <li>GET  /legal-documents/{code}/active       — público (pre-auth)</li>
 *   <li>GET  /legal-documents/{id}                — autenticado</li>
 *   <li>POST /legal-documents/{id}/accept         — autenticado</li>
 *   <li>GET  /me/legal-acceptances                — autenticado (socio)</li>
 *   <li>GET  /admin/legal-documents               — ADMIN, SECRETARIA</li>
 *   <li>POST /admin/legal-documents               — ADMIN, SECRETARIA</li>
 *   <li>POST /admin/legal-documents/{id}/new-version   — ADMIN, SECRETARIA</li>
 *   <li>PATCH /admin/legal-documents/{id}/activate     — ADMIN</li>
 *   <li>GET  /admin/legal-documents/{id}/acceptances   — ADMIN, SECRETARIA</li>
 *   <li>GET  /admin/legal-documents/pending-acceptances — ADMIN, SECRETARIA</li>
 *   <li>GET  /admin/socios/{id}/legal-status      — ADMIN, SECRETARIA</li>
 *   <li>GET  /admin/socios/blocked-for-activities — ADMIN, SECRETARIA</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Documentos Legales", description = "Gestión de documentos legales versionados y aceptaciones electrónicas")
public class LegalDocumentController {

    private final LegalDocumentService           legalDocumentService;
    private final LegalDocumentAcceptanceService acceptanceService;

    // =========================================================================
    // Públicos (pre-auth)
    // =========================================================================

    @GetMapping(ApiPaths.LEGAL_DOCUMENTS + "/active")
    @Operation(summary = "Documentos activos, opcionalmente filtrados por etapa (REGISTRATION, PROFILE_COMPLETION, ACTIVITY_ENROLLMENT)")
    public ResponseEntity<ApiResponse<List<LegalDocumentResponse>>> getActiveDocuments(
            @RequestParam(required = false) String stage) {
        return ResponseEntity.ok(ApiResponse.ok(legalDocumentService.getActiveDocuments(stage)));
    }

    @GetMapping(ApiPaths.LEGAL_DOCUMENTS + "/{code}/active")
    @Operation(summary = "Documento activo por código")
    public ResponseEntity<ApiResponse<LegalDocumentResponse>> getActiveDocument(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(legalDocumentService.getActiveDocument(code)));
    }

    // =========================================================================
    // Autenticado — socio
    // =========================================================================

    @GetMapping(ApiPaths.LEGAL_DOCUMENTS + "/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Documento legal por ID")
    public ResponseEntity<ApiResponse<LegalDocumentResponse>> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(legalDocumentService.getDocument(id)));
    }

    @PostMapping(ApiPaths.LEGAL_DOCUMENTS + "/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Registrar aceptación de un documento legal. IP y user-agent se capturan en servidor.")
    public ResponseEntity<ApiResponse<LegalDocumentAcceptanceResponse>> acceptDocument(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request) {

        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        String ip = resolveClientIp(request);
        String ua = request.getHeader("User-Agent");

        LegalDocumentAcceptanceResponse result = acceptanceService.acceptDocument(socioId, id, ip, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Documento aceptado", result));
    }

    @GetMapping(ApiPaths.ME + "/legal-acceptances")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mis aceptaciones de documentos legales")
    public ResponseEntity<ApiResponse<List<LegalDocumentAcceptanceResponse>>> getMyAcceptances(
            Authentication authentication) {
        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        return ResponseEntity.ok(ApiResponse.ok(acceptanceService.getMyAcceptances(socioId)));
    }

    // =========================================================================
    // Administración — ADMIN + SECRETARIA
    // =========================================================================

    @GetMapping(ApiPaths.ADMIN + "/legal-documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Listar todos los documentos legales con todas sus versiones")
    public ResponseEntity<ApiResponse<List<LegalDocumentSummaryResponse>>> getAllDocuments() {
        return ResponseEntity.ok(ApiResponse.ok(legalDocumentService.getAllDocumentsAdmin()));
    }

    @PostMapping(ApiPaths.ADMIN + "/legal-documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Crear nuevo documento legal (versión 1, inactivo)")
    public ResponseEntity<ApiResponse<LegalDocumentResponse>> createDocument(
            @Valid @RequestBody CreateLegalDocumentRequest req,
            Authentication authentication) {
        UUID actorId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        LegalDocumentResponse result = legalDocumentService.createDocument(req, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Documento creado", result));
    }

    @PostMapping(ApiPaths.ADMIN + "/legal-documents/{id}/new-version")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Crear nueva versión de un documento existente (queda inactiva hasta que un ADMIN la active)")
    public ResponseEntity<ApiResponse<LegalDocumentResponse>> createNewVersion(
            @PathVariable UUID id,
            @Valid @RequestBody NewVersionRequest req,
            Authentication authentication) {
        UUID actorId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        LegalDocumentResponse result = legalDocumentService.createNewVersion(id, req, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Nueva versión creada", result));
    }

    @PatchMapping(ApiPaths.ADMIN + "/legal-documents/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar versión de un documento (solo ADMIN). Desactiva automáticamente las versiones anteriores.")
    public ResponseEntity<ApiResponse<LegalDocumentResponse>> activateVersion(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID actorId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        return ResponseEntity.ok(ApiResponse.ok("Versión activada", legalDocumentService.activateVersion(id, actorId)));
    }

    @GetMapping(ApiPaths.ADMIN + "/legal-documents/{id}/acceptances")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Aceptaciones de un documento específico")
    public ResponseEntity<ApiResponse<List<LegalDocumentAcceptanceAdminResponse>>> getAcceptances(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(legalDocumentService.getAcceptancesByDocument(id)));
    }

    @GetMapping(ApiPaths.ADMIN + "/legal-documents/pending-acceptances")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Por cada documento activo requerido, lista los socios que aún no lo han aceptado")
    public ResponseEntity<ApiResponse<List<PendingAcceptancesResponse>>> getPendingAcceptances() {
        return ResponseEntity.ok(ApiResponse.ok(legalDocumentService.getPendingAcceptances()));
    }

    @GetMapping(ApiPaths.ADMIN + "/socios/{socioId}/legal-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Estado legal de un socio: qué documentos aceptó y cuáles le faltan")
    public ResponseEntity<ApiResponse<SocioLegalStatusResponse>> getSocioLegalStatus(
            @PathVariable UUID socioId) {
        return ResponseEntity.ok(ApiResponse.ok(legalDocumentService.getSocioLegalStatus(socioId)));
    }

    @GetMapping(ApiPaths.ADMIN + "/socios/blocked-for-activities")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Socios bloqueados para inscripción por documentos legales pendientes")
    public ResponseEntity<ApiResponse<List<PendingAcceptancesResponse.SocioPendienteDto>>> getSociosBlockedForActivities() {
        return ResponseEntity.ok(ApiResponse.ok(legalDocumentService.getSociosBlockedByDocuments()));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String resolveClientIp(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(xff -> xff.split(",")[0].trim())
                .orElse(request.getRemoteAddr());
    }
}
