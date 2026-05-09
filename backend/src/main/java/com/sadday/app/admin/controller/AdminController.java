package com.sadday.app.admin.controller;

import com.sadday.app.admin.dto.AuditoriaEntryResponse;
import com.sadday.app.admin.dto.AuditoriaFiltroRequest;
import com.sadday.app.admin.dto.ConfiguracionSistemaResponse;
import com.sadday.app.admin.dto.SecurityEventResponse;
import com.sadday.app.admin.dto.UpdateConfigRequest;
import com.sadday.app.admin.dto.UsuarioAuthSummaryResponse;
import com.sadday.app.admin.service.AdminService;
import com.sadday.app.admin.service.ConfiguracionSistemaService;
import com.sadday.app.scheduler.SchedulerService;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controlador del módulo Admin.
 *
 * <p>Todos los endpoints requieren rol ADMIN o SECRETARIA (aplicado en {@code SecurityConfig}
 * a nivel de ruta). Las operaciones de escritura (desbloqueo) usan {@code @PreAuthorize}
 * adicional para restringir solo a ADMIN.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/v1/admin/auditoria          — log de auditoría (paginado, filtrable)</li>
 *   <li>GET  /api/v1/admin/usuarios-auth       — lista de cuentas de auth</li>
 *   <li>POST /api/v1/admin/usuarios-auth/{socioId}/desbloquear — desbloquear cuenta</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping(ApiPaths.ADMIN)
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Gestión de cuentas y log de auditoría (Admin/Secretaria)")
public class AdminController {

    private final AdminService adminService;
    private final ConfiguracionSistemaService configService;
    private final SchedulerService schedulerService;

    // =========================================================================
    // Log de auditoría
    // =========================================================================

    @GetMapping("/auditoria")
    @Operation(summary = "Consultar log de auditoría (paginado)")
    public ResponseEntity<ApiResponse<Page<AuditoriaEntryResponse>>> getAuditoria(
            @RequestParam(required = false) String actorUsername,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @Size(max = 20) List<String> omitirAccion,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String entidadAfectada,
            @RequestParam(required = false) String entidadId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @PageableDefault(size = 30, sort = "createdAt") Pageable pageable
    ) {
        Page<AuditoriaEntryResponse> page = adminService.getAuditoria(
                new AuditoriaFiltroRequest(actorUsername, accion, omitirAccion, resultado,
                        entidadAfectada, entidadId, fechaDesde, fechaHasta),
                pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // =========================================================================
    // Eventos de seguridad
    // =========================================================================

    @GetMapping("/security-events")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Consultar eventos de seguridad (paginado, solo Admin/Secretaria)")
    public ResponseEntity<ApiResponse<Page<SecurityEventResponse>>> getSecurityEvents(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @PageableDefault(size = 30, sort = "createdAt") Pageable pageable
    ) {
        Page<SecurityEventResponse> page = adminService.getSecurityEvents(
                username, eventType, ipAddress, fechaDesde, fechaHasta, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // =========================================================================
    // Gestión de usuarios de autenticación
    // =========================================================================

    @GetMapping("/usuarios-auth")
    @Operation(summary = "Listar cuentas de autenticación (sin datos sensibles)")
    public ResponseEntity<ApiResponse<List<UsuarioAuthSummaryResponse>>> getUsuariosAuth() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getUsuariosAuth()));
    }

    @GetMapping("/usuarios-auth/{socioId}")
    @Operation(summary = "Estado de la cuenta de autenticación de un socio específico")
    public ResponseEntity<ApiResponse<UsuarioAuthSummaryResponse>> getUsuarioAuthBySocio(
            @PathVariable UUID socioId) {
        return adminService.getUsuarioAuthBySocio(socioId)
                .map(dto -> ResponseEntity.ok(ApiResponse.ok(dto)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(null)));
    }

    @PostMapping("/usuarios-auth/{socioId}/desbloquear")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desbloquear cuenta de un socio (solo ADMIN)")
    public ResponseEntity<ApiResponse<Void>> desbloquearUsuario(
            @PathVariable UUID socioId,
            Authentication authentication
    ) {
        adminService.desbloquearUsuario(socioId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Cuenta desbloqueada correctamente"));
    }

    @PatchMapping("/usuarios-auth/{socioId}/estado-acceso")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Cambiar estado de acceso al sistema de un socio (Admin/Secretaria). " +
                         "Valores: ACTIVE, BLOCKED, EX_MEMBER, PENDING_REGISTER, DISABLED. " +
                         "Si el nuevo estado no es ACTIVE, se revocan todas las sesiones del socio.")
    public ResponseEntity<ApiResponse<Void>> cambiarEstadoAcceso(
            @PathVariable UUID socioId,
            @RequestParam String codigo,
            Authentication authentication
    ) {
        adminService.cambiarEstadoAcceso(socioId, codigo, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Estado de acceso actualizado correctamente"));
    }

    @PostMapping("/usuarios-auth/{socioId}/cerrar-sesion")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Forzar cierre de sesión de un socio — revoca todos sus refresh tokens (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<Void>> forzarCierreSesion(
            @PathVariable UUID socioId,
            Authentication authentication
    ) {
        adminService.forzarCierreSesion(socioId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada correctamente"));
    }

    // =========================================================================
    // Configuración del sistema
    // =========================================================================

    @GetMapping("/config")
    @Operation(summary = "Listar toda la configuración del sistema (Admin/Secretaria)")
    public ResponseEntity<ApiResponse<List<ConfiguracionSistemaResponse>>> listarConfig() {
        return ResponseEntity.ok(ApiResponse.ok(configService.listar()));
    }

    @GetMapping("/config/{clave}")
    @Operation(summary = "Obtener un parámetro de configuración por clave")
    public ResponseEntity<ApiResponse<ConfiguracionSistemaResponse>> obtenerConfig(
            @PathVariable String clave) {
        return ResponseEntity.ok(ApiResponse.ok(configService.obtener(clave)));
    }

    // =========================================================================
    // Diagnóstico / checks manuales
    // =========================================================================

    @PostMapping("/diagnostico/geoip")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Forzar check de frescura GeoIP (Admin) — útil en dev para probar alertas")
    public ResponseEntity<ApiResponse<String>> checkGeoIp() {
        schedulerService.ejecutarCheckGeoIpAhora();
        return ResponseEntity.ok(ApiResponse.ok(
                "Check ejecutado. Si ADMIN_ALERT_EMAIL está configurado, revisa el email (o Mailpit en :8025)."));
    }

    @PatchMapping("/config/{clave}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    @Operation(summary = "Actualizar un parámetro de configuración (Admin/Secretaria) — auditado")
    public ResponseEntity<ApiResponse<ConfiguracionSistemaResponse>> actualizarConfig(
            @PathVariable String clave,
            @RequestBody @Valid UpdateConfigRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                configService.actualizar(clave, request, authentication)));
    }
}
