package com.sadday.app.retirement.controller;

import com.sadday.app.retirement.dto.RetireSocioRequest;
import com.sadday.app.retirement.dto.RetireSocioResponse;
import com.sadday.app.retirement.service.SocioDataRetentionService;
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
public class SocioRetirementController {

    private final SocioDataRetentionService retentionService;

    @PostMapping(ApiPaths.ADMIN + "/socios/{id}/retire")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ResponseEntity<ApiResponse<RetireSocioResponse>> retire(
            @PathVariable UUID id,
            @Valid @RequestBody RetireSocioRequest request,
            Authentication authentication) {

        UUID actorId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        RetireSocioResponse result = retentionService.retireSocio(id, request.reason(), actorId);
        return ResponseEntity.ok(ApiResponse.ok("Socio dado de baja correctamente", result));
    }
}
