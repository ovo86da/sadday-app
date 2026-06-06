package com.sadday.app.profile.controller;

import com.sadday.app.profile.dto.ProfileCompletionStatusResponse;
import com.sadday.app.profile.service.ProfileCompletionService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProfileCompletionController {

    private final ProfileCompletionService service;

    @GetMapping(ApiPaths.ME + "/profile-completion-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProfileCompletionStatusResponse>> getMiStatus(
            Authentication authentication) {
        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        return ResponseEntity.ok(ApiResponse.ok(service.getMiStatus(socioId)));
    }

    @GetMapping(ApiPaths.ADMIN + "/socios/{socioId}/profile-completion-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ResponseEntity<ApiResponse<ProfileCompletionStatusResponse>> getStatusBySocioId(
            @PathVariable UUID socioId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getStatusBySocioId(socioId)));
    }
}
