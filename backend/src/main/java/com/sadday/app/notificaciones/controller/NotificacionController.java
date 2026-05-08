package com.sadday.app.notificaciones.controller;

import com.sadday.app.notificaciones.dto.CumpleanosResponse;
import com.sadday.app.notificaciones.service.NotificacionService;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de Notificaciones.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/v1/notificaciones/cumpleanos — socios con cumpleaños hoy</li>
 * </ul>
 */
@RestController
@RequestMapping(ApiPaths.NOTIFICACIONES)
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Alertas y notificaciones del club")
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping("/cumpleanos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Socios con cumpleaños hoy. El frontend muestra un cartel de celebración.")
    public ResponseEntity<ApiResponse<CumpleanosResponse>> cumpleanosHoy() {
        return ResponseEntity.ok(ApiResponse.ok(notificacionService.cumpleanosHoy()));
    }
}
