package com.sadday.app.activityrisk.controller;

import com.sadday.app.activityrisk.dto.ActivityRiskAcceptanceResponse;
import com.sadday.app.activityrisk.dto.ActivityRiskDocumentResponse;
import com.sadday.app.activityrisk.dto.CreateActivityRiskDocumentRequest;
import com.sadday.app.activityrisk.service.ActivityRiskAcceptanceService;
import com.sadday.app.activityrisk.service.ActivityRiskDocumentService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ActivityRiskController {

    private final ActivityRiskDocumentService  docService;
    private final ActivityRiskAcceptanceService acceptanceService;

    // =========================================================================
    // Socios autenticados
    // =========================================================================

    @GetMapping(ApiPaths.SALIDAS + "/{id}/risk-document")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ActivityRiskDocumentResponse>> getActivo(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(docService.getActivoParaSalida(id)));
    }

    @PostMapping(ApiPaths.SALIDAS + "/{id}/risk-document/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ActivityRiskAcceptanceResponse>> aceptar(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request) {

        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        String ip = resolveClientIp(request);
        String ua = request.getHeader("User-Agent");

        ActivityRiskAcceptanceResponse result = acceptanceService.aceptar(id, socioId, ip, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Documento de riesgos aceptado", result));
    }

    // =========================================================================
    // Administración — ADMIN + DIRECTIVO
    // =========================================================================

    @PostMapping(ApiPaths.ADMIN + "/salidas/{id}/risk-document")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    public ResponseEntity<ApiResponse<ActivityRiskDocumentResponse>> crear(
            @PathVariable UUID id,
            @Valid @RequestBody CreateActivityRiskDocumentRequest request,
            Authentication authentication) {

        UUID actorId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        ActivityRiskDocumentResponse result = docService.crear(id, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Documento de riesgos creado", result));
    }

    // =========================================================================

    private String resolveClientIp(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(xff -> xff.split(",")[0].trim())
                .orElse(request.getRemoteAddr());
    }
}
