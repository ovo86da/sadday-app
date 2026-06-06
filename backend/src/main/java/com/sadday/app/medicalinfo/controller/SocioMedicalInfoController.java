package com.sadday.app.medicalinfo.controller;

import com.sadday.app.medicalinfo.dto.MedicalInfoResponse;
import com.sadday.app.medicalinfo.dto.MedicalSummaryResponse;
import com.sadday.app.medicalinfo.dto.UpdateMedicalInfoRequest;
import com.sadday.app.medicalinfo.service.SocioMedicalInfoService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SocioMedicalInfoController {

    private final SocioMedicalInfoService service;

    // =========================================================================
    // Socio propio — /me/medical-info
    // =========================================================================

    @GetMapping(ApiPaths.ME + "/medical-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MedicalInfoResponse>> getMiInfo(Authentication authentication) {
        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        return ResponseEntity.ok(ApiResponse.ok(service.getMiInfo(socioId)));
    }

    @PutMapping(ApiPaths.ME + "/medical-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MedicalInfoResponse>> updateMiInfo(
            Authentication authentication,
            @Valid @RequestBody UpdateMedicalInfoRequest request) {
        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        return ResponseEntity.ok(ApiResponse.ok(service.updateMiInfo(socioId, request)));
    }

    // =========================================================================
    // Admin / Secretaria — /admin/socios/{id}/medical-info
    // =========================================================================

    @GetMapping(ApiPaths.ADMIN + "/socios/{socioId}/medical-info")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ResponseEntity<ApiResponse<MedicalInfoResponse>> getBySocioId(@PathVariable UUID socioId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getBySocioId(socioId)));
    }

    @PutMapping(ApiPaths.ADMIN + "/socios/{socioId}/medical-info")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ResponseEntity<ApiResponse<MedicalInfoResponse>> updateBySocioId(
            @PathVariable UUID socioId,
            @Valid @RequestBody UpdateMedicalInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateBySocioId(socioId, request)));
    }

    // =========================================================================
    // Resumen para Jefe de Salida — /admin/socios/{id}/medical-summary
    // =========================================================================

    @GetMapping(ApiPaths.ADMIN + "/socios/{socioId}/medical-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    public ResponseEntity<ApiResponse<MedicalSummaryResponse>> getMedicalSummary(@PathVariable UUID socioId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getMedicalSummary(socioId)));
    }
}
