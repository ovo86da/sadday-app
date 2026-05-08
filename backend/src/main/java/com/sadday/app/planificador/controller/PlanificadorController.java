package com.sadday.app.planificador.controller;

import com.sadday.app.planificador.dto.RecomendacionResponse;
import com.sadday.app.planificador.service.PlanificadorService;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint del Planificador de Salidas.
 *
 * <p>Accesible por todos los socios autenticados.
 * No expone datos personales — solo estadísticas agregadas anónimas.
 */
@RestController
@RequestMapping(ApiPaths.PLANIFICADOR)
@RequiredArgsConstructor
@Tag(name = "Planificador", description = "Recomendaciones para planificar salidas")
public class PlanificadorController {

    private final PlanificadorService planificadorService;

    @GetMapping("/ruta/{rutaId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener recomendaciones para planificar una salida a una ruta")
    public ResponseEntity<ApiResponse<RecomendacionResponse>> recomendar(@PathVariable Integer rutaId) {
        return ResponseEntity.ok(ApiResponse.ok(planificadorService.recomendar(rutaId)));
    }
}
