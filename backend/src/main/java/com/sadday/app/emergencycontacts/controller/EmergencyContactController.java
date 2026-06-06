package com.sadday.app.emergencycontacts.controller;

import com.sadday.app.emergencycontacts.dto.EmergencyContactResponse;
import com.sadday.app.emergencycontacts.dto.UpsertEmergencyContactsRequest;
import com.sadday.app.emergencycontacts.service.EmergencyContactService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controlador de contactos de emergencia.
 *
 * <p>Autorización:
 * <ul>
 *   <li>GET/PUT /me/emergency-contacts          — autenticado (socio propio)</li>
 *   <li>GET/PUT /admin/socios/{id}/emergency-contacts — ADMIN, SECRETARIA</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Contactos de Emergencia", description = "Gestión de contactos de emergencia de socios")
public class EmergencyContactController {

    private final EmergencyContactService service;

    // =========================================================================
    // Propio socio (/me)
    // =========================================================================

    @GetMapping(ApiPaths.ME + "/emergency-contacts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mis contactos de emergencia")
    public ResponseEntity<ApiResponse<List<EmergencyContactResponse>>> getMisContactos(
            Authentication authentication) {
        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        return ResponseEntity.ok(ApiResponse.ok(service.getMisPropios(socioId)));
    }

    @PutMapping(ApiPaths.ME + "/emergency-contacts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Crear/actualizar mis contactos de emergencia (máx 2, reemplaza los anteriores)")
    public ResponseEntity<ApiResponse<List<EmergencyContactResponse>>> upsertMisContactos(
            @Valid @RequestBody UpsertEmergencyContactsRequest req,
            Authentication authentication) {
        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        return ResponseEntity.ok(ApiResponse.ok("Contactos actualizados", service.upsertMisPropios(socioId, req)));
    }

    // =========================================================================
    // Administración
    // =========================================================================

    @GetMapping(ApiPaths.ADMIN + "/socios/{socioId}/emergency-contacts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Contactos de emergencia de un socio")
    public ResponseEntity<ApiResponse<List<EmergencyContactResponse>>> getContactos(
            @PathVariable UUID socioId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getBySocioId(socioId)));
    }

    @PutMapping(ApiPaths.ADMIN + "/socios/{socioId}/emergency-contacts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Crear/actualizar contactos de emergencia de un socio (máx 2, reemplaza los anteriores)")
    public ResponseEntity<ApiResponse<List<EmergencyContactResponse>>> upsertContactos(
            @PathVariable UUID socioId,
            @Valid @RequestBody UpsertEmergencyContactsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Contactos actualizados", service.upsertBySocioId(socioId, req)));
    }
}
