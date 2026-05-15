package com.sadday.app.mountains.controller;

import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.service.ContactoService;
import com.sadday.app.mountains.service.RutaDocumentoService;
import com.sadday.app.mountains.service.RutaService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Controlador del módulo Rutas.
 *
 * <p>Autorización en dos capas (controlador + servicio):
 * <ul>
 *   <li>GET    /                              — autenticado</li>
 *   <li>POST   /                              — autenticado (servicio valida rol)</li>
 *   <li>GET    /{id}                          — autenticado</li>
 *   <li>PUT    /{id}                          — Admin / Secretaria / Directivo</li>
 *   <li>PATCH  /{id}/aprobar                  — Admin / Directivo</li>
 *   <li>DELETE /{id}                          — Admin</li>
 *   <li>GET    /{id}/contactos               — autenticado</li>
 *   <li>POST   /{id}/contactos               — Admin / Secretaria / Directivo</li>
 *   <li>DELETE /{id}/contactos/{cid}         — Admin / Secretaria / Directivo</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping(ApiPaths.RUTAS)
@RequiredArgsConstructor
@Tag(name = "Rutas", description = "Gestión de rutas y sus contactos de apoyo")
public class RutaController {

    private final RutaService            rutaService;
    private final ContactoService        contactoService;
    private final RutaDocumentoService   rutaDocumentoService;

    // =========================================================================
    // Rutas
    // =========================================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar rutas con filtros opcionales")
    public ResponseEntity<ApiResponse<Page<RutaSummaryResponse>>> listar(
            @RequestParam(required = false) Integer mountainId,
            @RequestParam(required = false) Boolean aprobada,
            @RequestParam(required = false) String tipoActividad,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) String nivelMinimoSocioId,
            @RequestParam(required = false) Boolean requierePermisos,
            @RequestParam(required = false) Boolean tieneTrack,
            @RequestParam(required = false) @DecimalMin("0") @DecimalMax("9999") Double longitudKmMin,
            @RequestParam(required = false) @DecimalMin("0") @DecimalMax("9999") Double longitudKmMax,
            @RequestParam(required = false) @Min(0) @Max(9999) Integer desnivelMin,
            @RequestParam(required = false) @Min(0) @Max(9999) Integer desnivelMax,
            @RequestParam(required = false) @Min(1) @Max(365) Integer duracionDiasMin,
            @RequestParam(required = false) @Min(1) @Max(365) Integer duracionDiasMax,
            @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(rutaService.listar(
                mountainId, aprobada, tipoActividad, q,
                nivelMinimoSocioId, requierePermisos, tieneTrack,
                longitudKmMin, longitudKmMax, desnivelMin, desnivelMax,
                duracionDiasMin, duracionDiasMax, pageable)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Proponer una nueva ruta (cualquier socio autenticado)")
    public ResponseEntity<ApiResponse<RutaResponse>> crear(
            @Valid @RequestBody CreateRutaRequest request,
            Authentication authentication) {

        UUID propuestaPorId = extractSocioId(authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Ruta propuesta correctamente. Pendiente de aprobación.", rutaService.crear(request, propuestaPorId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener detalle de una ruta con sus contactos")
    public ResponseEntity<ApiResponse<RutaResponse>> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(rutaService.obtener(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Actualizar datos de una ruta (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<RutaResponse>> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRutaRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(rutaService.actualizar(id, request)));
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    @Operation(summary = "Aprobar una ruta para uso en salidas (Admin/Directivo)")
    public ResponseEntity<ApiResponse<Void>> aprobar(
            @PathVariable Integer id,
            Authentication authentication) {

        UUID aprobadaPorId = extractSocioId(authentication);
        rutaService.aprobar(id, aprobadaPorId);
        return ResponseEntity.ok(ApiResponse.ok("Ruta aprobada correctamente."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Eliminar una ruta (Admin/Secretaria/Directivo). Falla si tiene salidas asociadas.")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        rutaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Lookups
    // =========================================================================

    @GetMapping("/equipos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar todos los tipos de equipo de montaña")
    public ResponseEntity<ApiResponse<java.util.List<com.sadday.app.mountains.entity.EquipoMontana>>> listarEquipos() {
        return ResponseEntity.ok(ApiResponse.ok(rutaService.listarEquipos()));
    }

    // =========================================================================
    // Documentos de permiso
    // =========================================================================

    @GetMapping("/{id}/documentos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar documentos de permiso de una ruta (todos los autenticados)")
    public ResponseEntity<ApiResponse<List<RutaDocumentoResponse>>> listarDocumentos(
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(rutaDocumentoService.listar(id)));
    }

    @PostMapping(value = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Subir un documento de permiso a una ruta (Admin/Secretaria/Directivo)",
               description = "Acepta PDF, Word (doc/docx) y Excel (xls/xlsx). Tamaño máximo: 10 MB.")
    public ResponseEntity<ApiResponse<RutaDocumentoResponse>> subirDocumento(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        UUID subidoPorId = extractSocioId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(rutaDocumentoService.subir(id, file, subidoPorId)));
    }

    @DeleteMapping("/{id}/documentos/{docId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Eliminar un documento de permiso de una ruta (Admin/Secretaria/Directivo)")
    public ResponseEntity<Void> eliminarDocumento(
            @PathVariable Integer id,
            @PathVariable UUID docId) {
        rutaDocumentoService.eliminar(id, docId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/documentos/{docId}/descargar")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Descargar un documento de permiso (todos los autenticados)")
    public ResponseEntity<byte[]> descargarDocumento(
            @PathVariable Integer id,
            @PathVariable UUID docId) {
        RutaDocumentoService.DescargaDocumento descarga = rutaDocumentoService.descargar(id, docId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + descarga.filename() + "\"")
                .contentType(MediaType.parseMediaType(descarga.contentType()))
                .body(descarga.bytes());
    }

    // =========================================================================
    // Contactos de la ruta (vinculación/desvinculación de contactos globales)
    // =========================================================================

    @GetMapping("/{id}/contactos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar contactos activos de una ruta")
    public ResponseEntity<ApiResponse<List<ContactoRutaResponse>>> listarContactos(
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(contactoService.listarContactosRuta(id)));
    }

    @PostMapping("/{id}/contactos")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Vincular un contacto global a una ruta (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<ContactoRutaResponse>> vincularContacto(
            @PathVariable Integer id,
            @Valid @RequestBody VincularContactoRutaRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(contactoService.vincular(id, request)));
    }

    @DeleteMapping("/{id}/contactos/{contactoRutaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Desvincular un contacto de una ruta (soft delete)")
    public ResponseEntity<Void> desvincularContacto(
            @PathVariable Integer id,
            @PathVariable Integer contactoRutaId) {

        contactoService.desvincular(id, contactoRutaId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }
}
