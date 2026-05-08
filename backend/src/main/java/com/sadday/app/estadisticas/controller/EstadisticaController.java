package com.sadday.app.estadisticas.controller;

import com.sadday.app.estadisticas.dto.*;
import com.sadday.app.estadisticas.service.EstadisticaService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Controlador de Estadísticas e Historial.
 *
 * <p>Endpoints (solo lectura):
 * <ul>
 *   <li>GET /api/v1/estadisticas/socios/{socioId}     — historial y estadísticas de un socio</li>
 *   <li>GET /api/v1/estadisticas/mountains/{id}        — estadísticas de salidas a una montaña</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping(ApiPaths.ESTADISTICAS)
@RequiredArgsConstructor
@Tag(name = "Estadísticas", description = "Historial de socios y estadísticas de montañas")
public class EstadisticaController {

    private final EstadisticaService estadisticaService;

    @GetMapping("/socios/{socioId}")
    @Operation(summary = "Historial completo de un socio: salidas, cumbres y dignidades. " +
            "Cada socio solo puede ver el suyo; Admin/Directivo/Secretaria pueden ver cualquiera.")
    public ResponseEntity<ApiResponse<SocioHistorialResponse>> historialSocio(
            @PathVariable UUID socioId,
            Authentication authentication) {

        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.obtenerHistorialSocio(socioId, currentUserId)));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Estadísticas de dashboard: totales globales de salidas y desglose mensual " +
            "para los últimos N meses (1–60, default 12). Accesible a todos los usuarios autenticados.")
    public ResponseEntity<ApiResponse<DashboardEstadisticasResponse>> dashboard(
            @RequestParam(defaultValue = "12") int meses) {

        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.obtenerDashboardEstadisticas(meses)));
    }

    @GetMapping("/mountains/{mountainId}")
    @Operation(summary = "Estadísticas de salidas hacia una montaña: totales, realizadas y rutas.")
    public ResponseEntity<ApiResponse<MountainEstadisticaResponse>> estadisticasMountain(
            @PathVariable int mountainId) {

        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.obtenerEstadisticasMountain(mountainId)));
    }

    @GetMapping("/club")
    @Operation(summary = "Estadísticas del club: distribución de socios por nivel técnico y tipo, " +
            "y dignidades más frecuentes. Solo Admin / Directivo / Secretaria.")
    public ResponseEntity<ApiResponse<ClubEstadisticasResponse>> clubEstadisticas() {
        return ResponseEntity.ok(ApiResponse.ok(estadisticaService.obtenerClubEstadisticas()));
    }

    @GetMapping("/rankings")
    @Operation(summary = "Rankings del club: top jefes de salida, top participantes y top por dignidad.")
    public ResponseEntity<ApiResponse<ClubRankingsResponse>> rankings(
            @RequestParam(defaultValue = "10") int top) {
        return ResponseEntity.ok(ApiResponse.ok(estadisticaService.obtenerRankings(top)));
    }

    @GetMapping("/participantes")
    @Operation(summary = "Búsqueda avanzada de participantes con filtros combinables: " +
            "montaña, ruta, dignidad, nivel técnico y nombre.")
    public ResponseEntity<ApiResponse<List<ParticipanteFiltradoItem>>> participantes(
            @RequestParam(required = false) Integer mountainId,
            @RequestParam(required = false) Integer rutaId,
            @RequestParam(required = false) Integer dignidadId,
            @RequestParam(required = false) String  nivelTecnicoId,
            @RequestParam(required = false) String  tipoActividad,
            @RequestParam(required = false) String  q) {
        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.buscarParticipantes(
                        mountainId, rutaId, dignidadId, nivelTecnicoId, tipoActividad, q)));
    }

    @GetMapping("/reuniones/rankings")
    @Operation(summary = "Rankings y métricas de asistencia a reuniones: top asistentes, " +
            "socios menos presentes y asistencia por mes. Solo Admin / Directivo / Secretaria.")
    public ResponseEntity<ApiResponse<ReunionesRankingResponse>> rankingReuniones(
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "12") int meses) {
        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.obtenerRankingReuniones(top, meses)));
    }

    @GetMapping("/ranking-montana-ruta")
    @Operation(summary = "Ranking de montañas y rutas por salidas y participantes. " +
            "Solo Admin / Directivo / Secretaria.")
    public ResponseEntity<ApiResponse<RankingMontanaRutaResponse>> rankingMontanaRuta(
            @RequestParam(defaultValue = "10") int top) {
        return ResponseEntity.ok(ApiResponse.ok(estadisticaService.obtenerRankingMontanaRuta(top)));
    }

    @GetMapping("/montana-ruta/buscar")
    @Operation(summary = "Búsqueda de montañas o rutas con conteo de salidas y participantes. " +
            "Solo Admin / Directivo / Secretaria.")
    public ResponseEntity<ApiResponse<List<MontanaRutaBusquedaItem>>> buscarMontanaRuta(
            @RequestParam(defaultValue = "ambos") String tipo,
            @RequestParam(required = false)        String q,
            @RequestParam(defaultValue = "false")  boolean sinSalidas) {
        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.buscarMontanaRuta(tipo, q, sinSalidas)));
    }

    @GetMapping("/socios/{socioId}/actividad-total")
    @Operation(summary = "Actividad combinada de un socio: reuniones asistidas + salidas participadas. " +
            "El propio socio puede ver sus datos; Admin/Directivo/Secretaria pueden ver cualquiera.")
    public ResponseEntity<ApiResponse<ActividadTotalSocioResponse>> actividadTotal(
            @PathVariable UUID socioId,
            Authentication authentication) {
        UUID currentUserId = extractSocioId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.obtenerActividadTotalSocio(socioId, currentUserId)));
    }

    @GetMapping("/periodo/salidas")
    @Operation(summary = "Lista de salidas realizadas en un período de fechas, opcionalmente filtradas por tipo de actividad.")
    public ResponseEntity<ApiResponse<List<SalidaPeriodoItem>>> salidasEnPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String tipoActividad) {
        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.buscarSalidasEnPeriodo(fechaDesde, fechaHasta, tipoActividad)));
    }

    @GetMapping("/periodo/montanas")
    @Operation(summary = "Montañas con salidas en un período de fechas, con conteo de salidas y participantes.")
    public ResponseEntity<ApiResponse<List<MontanaPeriodoItem>>> montanasEnPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String tipoActividad) {
        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.buscarMontanasEnPeriodo(fechaDesde, fechaHasta, tipoActividad)));
    }

    @GetMapping("/periodo/rutas")
    @Operation(summary = "Rutas con salidas en un período de fechas, con conteo de salidas y participantes.")
    public ResponseEntity<ApiResponse<List<RutaPeriodoItem>>> rutasEnPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String tipoActividad) {
        return ResponseEntity.ok(ApiResponse.ok(
                estadisticaService.buscarRutasEnPeriodo(fechaDesde, fechaHasta, tipoActividad)));
    }

    private UUID extractSocioId(Authentication authentication) {
        return ((SaddayAuthDetails) authentication.getDetails()).socioId();
    }
}
