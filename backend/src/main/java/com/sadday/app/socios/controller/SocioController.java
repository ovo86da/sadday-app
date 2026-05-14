package com.sadday.app.socios.controller;

import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import com.sadday.app.auth.service.EmailVerificationService;
import com.sadday.app.socios.dto.*;
import com.sadday.app.socios.service.CsvHabilitacionService;
import com.sadday.app.socios.service.CsvSocioImportService;
import com.sadday.app.socios.service.SocioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
 * Controlador del módulo Socios.
 *
 * <p>Autorización (dos capas: controlador + servicio):
 * <ul>
 *   <li>GET    /lookups          — autenticado</li>
 *   <li>GET    /me               — autenticado (perfil propio)</li>
 *   <li>GET    /                 — Admin / Secretaria / Directivo</li>
 *   <li>POST   /                 — Admin / Secretaria</li>
 *   <li>GET    /{id}             — Admin / Secretaria / Directivo (para datos personales completos)
 *                                  Los socios regulares deben usar /me para su propio perfil.</li>
 *   <li>PUT    /{id}             — Admin / Secretaria</li>
 *   <li>PATCH  /{id}/habilitar   — Admin / Secretaria / Directivo</li>
 *   <li>PATCH  /{id}/inhabilitar — Admin / Secretaria / Directivo</li>
 *   <li>PATCH  /{id}/nivel-tecnico — Admin / Secretaria / Directivo</li>
 *   <li>PATCH  /{id}/rol         — Admin</li>
 *   <li>DELETE /{id}             — Admin</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping(ApiPaths.SOCIOS)
@RequiredArgsConstructor
@Tag(name = "Socios", description = "Gestión de socios del club")
public class SocioController {

    private final SocioService             socioService;
    private final CsvHabilitacionService   csvHabilitacionService;
    private final CsvSocioImportService    csvSocioImportService;
    private final EmailVerificationService emailVerificationService;
    private final com.sadday.app.auth.service.AuthService authService;

    // =========================================================================
    // Endpoints de solo lectura
    // =========================================================================

    @GetMapping("/lookups")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener tablas de referencia (tipo socio, estado, rol, clasificación técnica)")
    public ResponseEntity<ApiResponse<LookupsResponse>> lookups() {
        return ResponseEntity.ok(ApiResponse.ok(socioService.obtenerLookups()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener el perfil del socio autenticado")
    public ResponseEntity<ApiResponse<SocioResponse>> miPerfil(Authentication authentication) {
        UUID socioId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(socioService.obtener(socioId)));
    }

    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Actualizar la información de contacto propia",
               description = "El socio puede actualizar: correo, teléfono, dirección, tipo de sangre " +
                             "y contactos de emergencia. Los datos de identidad (nombre, cédula) " +
                             "solo pueden ser modificados por Admin/Secretaria.")
    public ResponseEntity<ApiResponse<SocioResponse>> actualizarMiPerfil(
            @Valid @RequestBody UpdateMiPerfilRequest request,
            Authentication authentication) {
        UUID socioId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(socioService.actualizarMiPerfil(socioId, request)));
    }

    /**
     * Búsqueda mínima (id, nombre, apellido) accesible a cualquier autenticado.
     * Usada por el Jefe de Salida para agregar participantes al informe.
     * No expone datos personales sensibles (IDOR-safe).
     */
    @GetMapping("/buscar")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Búsqueda rápida de socios por nombre/apellido (solo id+nombre). Todos los autenticados.")
    public ResponseEntity<ApiResponse<List<SocioMinimalResponse>>> buscar(
            @RequestParam @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "10") int size
    ) {
        int maxSize = Math.min(size, 20);
        return ResponseEntity.ok(ApiResponse.ok(socioService.buscarMinimal(q, maxSize)));
    }

    /**
     * Lista paginada con datos personales. Restringida a roles privilegiados
     * para evitar que un socio pueda enumerar datos personales de otros socios (IDOR).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Listar socios (paginado, con filtros opcionales)",
               description = "Filtros disponibles: rolId, estadoId, tipoId, q (búsqueda en nombre/apellido/cédula/correo). Solo Admin, Secretaria y Directivo.")
    public ResponseEntity<ApiResponse<Page<SocioSummaryResponse>>> listar(
            @RequestParam(required = false) Short  rolId,
            @RequestParam(required = false) Short  estadoId,
            @RequestParam(required = false) Short  tipoId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @PageableDefault(size = 20, sort = "apellido") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                socioService.listar(rolId, estadoId, tipoId, q, pageable)));
    }

    /**
     * Detalle completo con datos personales (dirección, contactos emergencia, etc.).
     * Restringido a roles privilegiados para evitar IDOR.
     * Los socios regulares acceden a su propio perfil mediante GET /socios/me.
     */
    @GetMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Obtener detalle de un socio por ID (Admin/Secretaria/Directivo). Para el perfil propio usar /me.")
    public ResponseEntity<ApiResponse<SocioResponse>> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(socioService.obtener(id)));
    }

    // =========================================================================
    // Escritura
    // =========================================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Iniciar el registro de un nuevo socio",
               description = "Envía una invitación por email al socio para que complete sus datos personales " +
                             "y credenciales. El registro en BD se crea cuando el socio activa su cuenta.")
    public ResponseEntity<ApiResponse<Void>> crear(
            @Valid @RequestBody CreateSocioRequest request) {

        socioService.crear(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Invitación de registro enviada correctamente al correo del socio."));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Actualizar datos personales de un socio (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<SocioResponse>> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSocioRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(socioService.actualizar(id, request)));
    }

    @PatchMapping("/{id}/habilitar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Habilitar a un socio inhabilitado (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<Void>> habilitar(
            @PathVariable UUID id, Authentication authentication) {
        socioService.habilitar(id, extractSocioId(authentication));
        return ResponseEntity.ok(ApiResponse.ok("Socio habilitado correctamente."));
    }

    @PatchMapping("/{id}/inhabilitar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Inhabilitar a un socio (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<Void>> inhabilitar(
            @PathVariable UUID id, Authentication authentication) {
        socioService.inhabilitar(id, extractSocioId(authentication));
        return ResponseEntity.ok(ApiResponse.ok("Socio inhabilitado correctamente."));
    }

    @GetMapping("/{id}/habilitacion-log")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Historial de cambios de habilitación de un socio (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<List<HabilitacionLogResponse>>> habilitacionLog(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(socioService.listarHabilitacionLog(id)));
    }

    @PostMapping(value = "/importar/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Vista previa de importación masiva de socios desde CSV",
               description = "Valida el CSV y devuelve filas válidas y errores sin enviar emails. " +
                             "Columnas: cedula, nombre, apellido, correo, telefono (opt), tipoSocio (opt), nivelTecnico (opt).")
    public ResponseEntity<ApiResponse<CsvSocioImportPreviewResponse>> previewImportarSocios(
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(ApiResponse.ok(csvSocioImportService.preview(file)));
    }

    @PostMapping("/importar/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Confirmar importación masiva de socios",
               description = "Recibe las filas válidas del preview y envía correos de invitación a cada socio.")
    public ResponseEntity<ApiResponse<CsvSocioImportResultResponse>> confirmarImportarSocios(
            @RequestBody List<CsvSocioImportPreviewResponse.FilaValida> filas) {

        return ResponseEntity.ok(ApiResponse.ok(csvSocioImportService.confirmar(filas)));
    }

    @PostMapping(value = "/habilitacion/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Carga masiva de habilitación/deshabilitación de socios desde CSV (Admin/Secretaria/Directivo)",
               description = "El CSV debe tener encabezado 'Nombre,Cedula,Estado'. " +
                             "La columna Estado acepta 'Habilitado' o 'Deshabilitado' por fila.")
    public ResponseEntity<ApiResponse<CsvHabilitacionResult>> procesarCsvHabilitacion(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        UUID realizadoPorId = extractSocioId(authentication);
        CsvHabilitacionResult result = csvHabilitacionService.procesarCsv(file, realizadoPorId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PatchMapping("/{id}/nivel-tecnico")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Actualizar el nivel técnico de un socio (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<SocioResponse>> actualizarNivelTecnico(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNivelTecnicoRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(socioService.actualizarNivelTecnico(id, request)));
    }

    @PatchMapping("/{id}/jefe-montana")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Activar o desactivar el flag Jefe de Montaña (Admin/Secretaria). " +
                         "Solo aplica a socios con rol DIRECTIVO.")
    public ResponseEntity<ApiResponse<SocioResponse>> setJefeMontana(
            @PathVariable UUID id,
            @RequestParam boolean valor) {

        return ResponseEntity.ok(ApiResponse.ok(socioService.setJefeMontana(id, valor)));
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Cambiar el rol del sistema de un socio (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<Void>> cambiarRol(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRolRequest request) {

        socioService.cambiarRol(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Rol actualizado correctamente."));
    }

    @PostMapping("/{id}/reenviar-invitacion")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Reenviar invitación de activación de cuenta (Admin/Secretaria). " +
                         "Falla si el socio ya activó su cuenta.")
    public ResponseEntity<ApiResponse<Void>> reenviarInvitacion(@PathVariable UUID id) {
        socioService.reenviarInvitacion(id);
        return ResponseEntity.ok(ApiResponse.ok("Invitación reenviada correctamente."));
    }

    @GetMapping("/invitaciones")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Listar invitaciones de pre-registro pendientes (Admin/Secretaria)",
               description = "Devuelve los tokens de invitación cuyo socio aún no completó el registro.")
    public ResponseEntity<ApiResponse<List<InvitacionPendienteResponse>>> listarInvitaciones() {
        return ResponseEntity.ok(ApiResponse.ok(emailVerificationService.listarInvitacionesPendientes()));
    }

    @PostMapping("/invitaciones/{tokenId}/reenviar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Reenviar una invitación de pre-registro por ID de token (Admin/Secretaria)",
               description = "Invalida el token anterior y envía un nuevo correo con un token fresco.")
    public ResponseEntity<ApiResponse<Void>> reenviarInvitacionPreRegistro(@PathVariable UUID tokenId) {
        emailVerificationService.reenviarInvitacionPreRegistro(tokenId);
        return ResponseEntity.ok(ApiResponse.ok("Invitación reenviada correctamente."));
    }

    @DeleteMapping("/invitaciones/{tokenId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Eliminar una invitación pendiente (Admin/Secretaria)",
               description = "Elimina permanentemente una invitación que aún no fue aceptada.")
    public ResponseEntity<Void> eliminarInvitacion(@PathVariable UUID tokenId) {
        emailVerificationService.eliminarInvitacion(tokenId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{socioId}/emergency-reset")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Reset de emergencia por pérdida de teléfono (Admin/Secretaria)",
               description = "Desactiva el 2FA, revoca todas las sesiones y envía email de reset de contraseña. " +
                             "Solo aplicable a socios con 2FA activo.")
    public ResponseEntity<ApiResponse<Void>> emergencyReset(
            @PathVariable UUID socioId,
            Authentication authentication) {
        authService.emergencyReset(socioId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Reset de emergencia ejecutado. Se envió el email de restablecimiento."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un socio (Admin — hard delete)")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        socioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Cuotas
    // =========================================================================

    @GetMapping("/{id}/cuotas")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Historial de cuotas de un socio (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<List<CuotaResponse>>> listarCuotas(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(socioService.listarCuotas(id)));
    }

    @PostMapping("/{id}/cuotas")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Registrar un pago o cuota pendiente para un socio (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<CuotaResponse>> registrarCuota(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCuotaRequest request,
            Authentication authentication) {

        UUID registradoPorId = extractSocioId(authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(socioService.registrarCuota(id, request, registradoPorId)));
    }

    @DeleteMapping("/{id}/cuotas/{cuotaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Eliminar un registro de cuota (Admin/Secretaria)")
    public ResponseEntity<Void> eliminarCuota(
            @PathVariable UUID id,
            @PathVariable Long cuotaId) {

        socioService.eliminarCuota(id, cuotaId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }
}
