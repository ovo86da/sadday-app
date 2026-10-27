package com.sadday.app.mountains.controller;

import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.service.CambioUmbralService;
import com.sadday.app.mountains.dto.RechazarCambioRequest;
import com.sadday.app.mountains.dto.CambioUmbralResponse;
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
    private final CambioUmbralService cambioUmbralService;

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

    // ── Cambios de umbral: se piden, no se aplican (FR-026) ──────────────────
    //
    // El verbo sigue siendo PUT y la ruta la misma, pero ya no aplica nada:
    // deja el cambio esperando a que lo apruebe otro directivo. Se mantuvo la
    // ruta porque es la misma intencion —cambiar los umbrales de un nivel— y
    // partirla en dos obligaria a que el cliente supiera de antemano si el
    // cambio va a necesitar aprobacion, cosa que no decide el.
    //
    // `SECRETARIA` sale de la anotacion: el servicio exige ADMIN o DIRECTIVO
    // desde antes de este cambio, asi que para una secretaria esto ya devolvia
    // 403. La anotacion solo prometia algo que no pasaba.

    @PutMapping("/acceso-por-nivel/{nivelSocioId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    @Operation(summary = "Pedir un cambio de umbrales para un nivel (queda pendiente de aprobación)",
               description = "NO aplica el cambio. Lo deja esperando a que otro directivo lo apruebe; "
                           + "mientras tanto el umbral vigente sigue siendo el anterior.")
    public ResponseEntity<ApiResponse<CambioUmbralResponse>> solicitarCambioAcceso(
            @PathVariable @NotBlank @Size(max = 50) String nivelSocioId,
            @Valid @RequestBody UpdateAccesoNivelRequest request,
            Authentication authentication) {

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(
                cambioUmbralService.solicitar(nivelSocioId, request, extractSocioId(authentication))));
    }

    @GetMapping("/acceso-por-nivel/cambios")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    @Operation(summary = "Cambios de umbral esperando aprobación, del más antiguo al más reciente")
    public ResponseEntity<ApiResponse<List<CambioUmbralResponse>>> cambiosPendientes(
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                cambioUmbralService.listarPendientes(extractSocioId(authentication))));
    }

    @PostMapping("/acceso-por-nivel/cambios/{cambioId}/aprobar")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    @Operation(summary = "Aprobar un cambio de umbral y aplicarlo",
               description = "Quien lo solicitó no puede aprobarlo.")
    public ResponseEntity<ApiResponse<CambioUmbralResponse>> aprobarCambio(
            @PathVariable UUID cambioId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                cambioUmbralService.aprobar(cambioId, extractSocioId(authentication))));
    }

    @PostMapping("/acceso-por-nivel/cambios/{cambioId}/rechazar")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    @Operation(summary = "Rechazar un cambio de umbral, indicando el motivo")
    public ResponseEntity<ApiResponse<CambioUmbralResponse>> rechazarCambio(
            @PathVariable UUID cambioId,
            @Valid @RequestBody RechazarCambioRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(cambioUmbralService.rechazar(
                cambioId, extractSocioId(authentication), request.motivo())));
    }

    @DeleteMapping("/acceso-por-nivel/cambios/{cambioId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    @Operation(summary = "Cancelar un cambio propio que sigue pendiente")
    public ResponseEntity<ApiResponse<CambioUmbralResponse>> cancelarCambio(
            @PathVariable UUID cambioId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                cambioUmbralService.cancelar(cambioId, extractSocioId(authentication))));
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
