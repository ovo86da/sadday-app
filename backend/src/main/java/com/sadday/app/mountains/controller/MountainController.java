package com.sadday.app.mountains.controller;

import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.service.MountainService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controlador del módulo Montañas.
 *
 * <p>Autorización en dos capas (controlador + servicio):
 * <ul>
 *   <li>GET /lookups                      — autenticado</li>
 *   <li>GET /acceso-por-nivel             — autenticado</li>
 *   <li>PUT /acceso-por-nivel/{nivelId}   — Admin / Secretaria / Directivo</li>
 *   <li>GET /                             — autenticado</li>
 *   <li>POST /                            — Admin / Secretaria / Directivo</li>
 *   <li>GET /{id}                         — autenticado</li>
 *   <li>PUT /{id}                         — Admin / Secretaria / Directivo</li>
 *   <li>DELETE /{id}                      — Admin</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping(ApiPaths.MOUNTAINS)
@RequiredArgsConstructor
@Tag(name = "Montañas", description = "Gestión de montañas y niveles de acceso")
public class MountainController {

    private final MountainService mountainService;

    // =========================================================================
    // Lookups y acceso por nivel (rutas fijas primero)
    // =========================================================================

    @GetMapping("/lookups")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tablas de referencia: escalas, dificultades, compromisos, Yosemite, Sadday")
    public ResponseEntity<ApiResponse<MountainLookupsResponse>> lookups() {
        return ResponseEntity.ok(ApiResponse.ok(mountainService.obtenerLookups()));
    }

    @GetMapping("/acceso-por-nivel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Umbrales máximos de dificultad permitidos por nivel de socio")
    public ResponseEntity<ApiResponse<List<AccesoNivelResponse>>> accesoPorNivel() {
        return ResponseEntity.ok(ApiResponse.ok(mountainService.obtenerAccesoPorNivel()));
    }

    @PutMapping("/acceso-por-nivel/{nivelSocioId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Actualizar umbrales de acceso para un nivel de socio (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<AccesoNivelResponse>> actualizarAcceso(
            @PathVariable @NotBlank @Size(max = 50) String nivelSocioId,
            @Valid @RequestBody UpdateAccesoNivelRequest request,
            Authentication authentication) {

        UUID updatedById = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                mountainService.actualizarAccesoPorNivel(nivelSocioId, request, updatedById)));
    }

    // =========================================================================
    // Mountains CRUD
    // =========================================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar montañas (paginado, filtro opcional por q y region)")
    public ResponseEntity<ApiResponse<Page<MountainSummaryResponse>>> listar(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 100) String region,
            @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(mountainService.listar(q, region, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Crear una nueva montaña (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<MountainResponse>> crear(
            @Valid @RequestBody CreateMountainRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(mountainService.crear(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener detalle de una montaña")
    public ResponseEntity<ApiResponse<MountainResponse>> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(mountainService.obtener(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Actualizar datos de una montaña (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<MountainResponse>> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateMountainRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(mountainService.actualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Eliminar una montaña (Admin/Secretaria/Directivo). Falla si tiene rutas asociadas.")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        mountainService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }
}
