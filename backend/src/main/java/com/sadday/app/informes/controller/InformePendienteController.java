package com.sadday.app.informes.controller;

import com.sadday.app.informes.dto.InformePendienteResponse;
import com.sadday.app.informes.service.InformeService;
import com.sadday.app.security.jwt.SaddayAuthDetails;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint para que el jefe de salida sepa si tiene informes pendientes de completar.
 * Ruta dedicada para evitar conflicto con el /{salidaId} de InformeController.
 */
@RestController
@RequestMapping(ApiPaths.INFORMES)
@RequiredArgsConstructor
@Tag(name = "Informes", description = "Informes de salida y reconocimientos")
public class InformePendienteController {

    private final InformeService informeService;

    @GetMapping("/pendientes-jefe")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Salidas donde el usuario autenticado es Jefe de Salida y aún no tiene informe.")
    public ResponseEntity<ApiResponse<List<InformePendienteResponse>>> pendientesJefe(
            Authentication authentication) {
        UUID socioId = ((SaddayAuthDetails) authentication.getDetails()).socioId();
        return ResponseEntity.ok(ApiResponse.ok(informeService.obtenerPendientesJefe(socioId)));
    }
}
