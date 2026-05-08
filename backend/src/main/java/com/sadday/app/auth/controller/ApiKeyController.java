package com.sadday.app.auth.controller;

import com.sadday.app.auth.dto.ApiKeyResponse;
import com.sadday.app.auth.dto.CreateApiKeyRequest;
import com.sadday.app.auth.dto.CreateApiKeyResponse;
import com.sadday.app.auth.service.ApiKeyService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.PROFILE + "/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "Gestión de API Keys para el asistente MCP")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @PreAuthorize("isAuthenticated() and not hasAuthority('SCOPE_readonly')")
    @Operation(summary = "Genera una nueva API Key — el raw se muestra una sola vez")
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> create(
            @Valid @RequestBody CreateApiKeyRequest request,
            Authentication authentication
    ) {
        UUID socioId = extractSocioId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(apiKeyService.create(socioId, request)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated() and not hasAuthority('SCOPE_readonly')")
    @Operation(summary = "Lista las API Keys activas del usuario autenticado")
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> list(Authentication authentication) {
        UUID socioId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(apiKeyService.listActive(socioId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated() and not hasAuthority('SCOPE_readonly')")
    @Operation(summary = "Revoca una API Key")
    public ResponseEntity<ApiResponse<Object>> revoke(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID socioId = extractSocioId(authentication);
        apiKeyService.revoke(id, socioId);
        return ResponseEntity.ok(ApiResponse.ok("API Key revocada correctamente."));
    }

    // -------------------------------------------------------------------------

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }
}
