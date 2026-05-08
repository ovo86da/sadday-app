package com.sadday.app.salidas.controller;

import com.sadday.app.salidas.dto.*;
import com.sadday.app.salidas.entity.EstadoSalida;
import com.sadday.app.salidas.service.SalidaService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controlador del módulo Salidas.
 *
 * <p>Autorización en dos capas (controlador + servicio):
 * <ul>
 *   <li>GET  /lookups                                 — autenticado</li>
 *   <li>GET  /                                        — autenticado</li>
 *   <li>POST /                                        — Admin / Secretaria / Directivo</li>
 *   <li>GET  /{id}                                    — autenticado</li>
 *   <li>PUT  /{id}                                    — Admin / Secretaria / Directivo</li>
 *   <li>PATCH /{id}/estado                            — Admin / Directivo</li>
 *   <li>DELETE /{id}                                  — Admin</li>
 *   <li>POST /{id}/inscripciones                      — autenticado (sin privilegios: solo a sí mismo)</li>
 *   <li>DELETE /{id}/inscripciones/{pid}              — autenticado (sin privilegios: solo la suya)</li>
 *   <li>PATCH /{id}/inscripciones/{pid}/estado            — Admin / Secretaria / Directivo / Jefe</li>
 *   <li>PATCH /{id}/inscripciones/{pid}/aprobacion-riesgo — Admin / Directivo / Jefe de Salida</li>
 *   <li>PATCH /{id}/inscripciones/{pid}/jefe              — Admin / Directivo</li>
 *   <li>POST /{id}/inscripciones/{pid}/dignidades         — Admin / Secretaria / Directivo</li>
 *   <li>DELETE /{id}/inscripciones/{pid}/dignidades/{did} — Admin / Secretaria / Directivo</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping(ApiPaths.SALIDAS)
@RequiredArgsConstructor
@Tag(name = "Salidas", description = "Planificación de salidas, inscripciones y dignidades")
public class SalidaController {

    private final SalidaService salidaService;

    // =========================================================================
    // Lookups
    // =========================================================================

    @GetMapping("/lookups")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Catálogos del módulo: tipos de salida, dignidades, estados")
    public ResponseEntity<ApiResponse<SalidaLookupsResponse>> lookups() {
        return ResponseEntity.ok(ApiResponse.ok(salidaService.obtenerLookups()));
    }

    @GetMapping("/solapamiento")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Verificar solapamiento de fechas con salidas activas (PLANIFICADA/EN_CURSO). " +
                         "Devuelve la lista de salidas que se solapan. excludeId opcional para excluir la salida en edición.")
    public ResponseEntity<ApiResponse<List<SolapamientoResponse>>> verificarSolapamiento(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) UUID excludeId) {

        return ResponseEntity.ok(ApiResponse.ok(
                salidaService.verificarSolapamiento(fechaInicio, fechaFin, excludeId)));
    }

    // =========================================================================
    // Salidas CRUD
    // =========================================================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar salidas (filtros: estado, fechaInicio, q, rutaId)")
    public ResponseEntity<ApiResponse<Page<SalidaSummaryResponse>>> listar(
            @RequestParam(required = false) EstadoSalida estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Long rutaId,
            @PageableDefault(size = 20, sort = "fechaInicio") Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
                salidaService.listar(estado, fechaInicio, q, rutaId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Crear una nueva salida (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<SalidaResponse>> crear(
            @Valid @RequestBody CreateSalidaRequest request,
            Authentication authentication) {

        UUID creadoPorId = extractSocioId(authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(salidaService.crear(request, creadoPorId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una salida con participantes (autenticado)")
    public ResponseEntity<ApiResponse<SalidaResponse>> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(salidaService.obtener(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Actualizar una salida PLANIFICADA (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<SalidaResponse>> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSalidaRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(salidaService.actualizar(id, request)));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTIVO')")
    @Operation(summary = "Cambiar estado de una salida (Admin/Directivo)")
    public ResponseEntity<ApiResponse<Void>> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody CambiarEstadoSalidaRequest request) {

        salidaService.cambiarEstado(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado correctamente."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Eliminar (soft) una salida — requiere motivo. La salida deja de ser visible (Admin/Secretaria/Directivo)")
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID id,
            @Valid @RequestBody EliminarSalidaRequest request,
            Authentication authentication) {

        UUID actorId = extractSocioId(authentication);
        salidaService.eliminar(id, request, actorId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Cancelar una salida — requiere motivo. La salida queda visible como CANCELADA (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<SalidaResponse>> cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody CancelarSalidaRequest request,
            Authentication authentication) {

        UUID actorId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(salidaService.cancelar(id, request, actorId)));
    }

    @DeleteMapping("/{id}/inscripciones/{participanteId}/aprobacion-riesgo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Revocar aprobación de riesgo (Directivo/Admin revoca su slot; Jefe de Salida revoca el suyo). " +
                         "Si la inscripción estaba INSCRITO, vuelve a PENDIENTE_APROBACION.")
    public ResponseEntity<ApiResponse<ParticipanteResponse>> revocarAprobacion(
            @PathVariable UUID id,
            @PathVariable Long participanteId) {

        return ResponseEntity.ok(ApiResponse.ok(salidaService.revocarAprobacion(id, participanteId)));
    }

    // =========================================================================
    // Inscripciones
    // =========================================================================

    @PostMapping("/{id}/inscripciones")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Inscribir un socio a la salida. Sin privilegios solo puedes inscribirte a ti mismo.")
    public ResponseEntity<ApiResponse<ParticipanteResponse>> inscribir(
            @PathVariable UUID id,
            @Valid @RequestBody InscribirRequest request,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(salidaService.inscribir(id, request, currentUserId)));
    }

    @DeleteMapping("/{id}/inscripciones/{participanteId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancelar inscripción. Sin privilegios solo puedes cancelar la tuya.")
    public ResponseEntity<Void> cancelarInscripcion(
            @PathVariable UUID id,
            @PathVariable Long participanteId,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        salidaService.cancelarInscripcion(id, participanteId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/inscripciones/{participanteId}/estado")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cambiar estado de inscripción (Admin/Secretaria/Directivo/Jefe de Salida)")
    public ResponseEntity<ApiResponse<ParticipanteResponse>> cambiarEstadoInscripcion(
            @PathVariable UUID id,
            @PathVariable Long participanteId,
            @Valid @RequestBody CambiarEstadoInscripcionRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                salidaService.cambiarEstadoInscripcion(id, participanteId, request)));
    }

    @PatchMapping("/{id}/inscripciones/{participanteId}/aprobacion-riesgo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Aprobar o negar riesgo de inscripción con nivel insuficiente (Directivo/Admin o Jefe de Salida). " +
                         "Negación de cualquiera → NEGADO inmediato. Ambas aprobaciones → INSCRITO.")
    public ResponseEntity<ApiResponse<ParticipanteResponse>> decidirRiesgo(
            @PathVariable UUID id,
            @PathVariable Long participanteId,
            @Valid @RequestBody DecidirRiesgoRequest request,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                salidaService.decidirRiesgo(id, participanteId, currentUserId, request)));
    }

    @GetMapping("/aprobaciones-pendientes")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista de inscripciones pendientes de aprobación de riesgo para el usuario autenticado " +
                         "(Directivo/Admin ven todas las pendientes; Jefe de Salida ve solo las de sus salidas).")
    public ResponseEntity<ApiResponse<List<AprobacionPendienteResponse>>> aprobacionesPendientes(
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                salidaService.obtenerAprobacionesPendientes(currentUserId)));
    }

    @GetMapping("/alertas-sin-jefe")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Salidas en las que el Jefe de Salida se retiró y aún no hay uno nuevo asignado.")
    public ResponseEntity<ApiResponse<List<AlertaSinJefeResponse>>> alertasSinJefe() {
        return ResponseEntity.ok(ApiResponse.ok(salidaService.obtenerAlertasSinJefe()));
    }

    @PatchMapping("/{id}/cerrar-inscripciones")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Abrir/cerrar inscripciones (Jefe de Salida / Admin / Directivo). " +
                         "Cuando está cerrado no se admiten nuevas inscripciones ni cancelaciones de socios.")
    public ResponseEntity<ApiResponse<Boolean>> toggleCerrarInscripciones(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        boolean cerradas = salidaService.toggleInscripcionesCerradas(id, currentUserId);
        return ResponseEntity.ok(ApiResponse.ok(cerradas));
    }

    @PatchMapping("/{id}/inscripciones/{participanteId}/jefe")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Designar jefe de salida (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<ParticipanteResponse>> designarJefeSalida(
            @PathVariable UUID id,
            @PathVariable Long participanteId) {

        return ResponseEntity.ok(ApiResponse.ok(salidaService.designarJefeSalida(id, participanteId)));
    }

    // =========================================================================
    // Dignidades
    // =========================================================================

    @PostMapping("/{id}/inscripciones/{participanteId}/dignidades")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Asignar dignidad a un participante (Admin/Secretaria/Directivo)")
    public ResponseEntity<ApiResponse<ParticipanteResponse>> agregarDignidad(
            @PathVariable UUID id,
            @PathVariable Long participanteId,
            @Valid @RequestBody AgregarDignidadRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(salidaService.agregarDignidad(id, participanteId, request)));
    }

    @DeleteMapping("/{id}/inscripciones/{participanteId}/dignidades/{dignidadAsignadaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'DIRECTIVO')")
    @Operation(summary = "Quitar dignidad de un participante (Admin/Secretaria/Directivo)")
    public ResponseEntity<Void> eliminarDignidad(
            @PathVariable UUID id,
            @PathVariable Long participanteId,
            @PathVariable Long dignidadAsignadaId) {

        salidaService.eliminarDignidad(id, participanteId, dignidadAsignadaId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }
}
