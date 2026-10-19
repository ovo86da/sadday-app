package com.sadday.app.admin.controller;

import com.sadday.app.admin.service.ConfiguracionSistemaService;
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

import java.util.Map;

/**
 * Configuración que el frontend y mobile necesitan sin ser administradores.
 *
 * <p>Existe porque los endpoints de configuración viven bajo
 * {@code /api/v1/admin/**}, restringidos a ADMIN y SECRETARIA en
 * {@code SecurityConfig}. Un socio normal no puede leerlos, y sin embargo
 * necesita, por ejemplo, la lista de apps recomendadas al descargar un track.
 *
 * <p>Devuelve solo las claves de una lista blanca definida en el servicio, no
 * la configuración completa.
 */
@RestController
@RequestMapping(ApiPaths.CONFIG)
@RequiredArgsConstructor
@Tag(name = "Configuración pública", description = "Parámetros legibles por cualquier socio autenticado")
public class ConfiguracionPublicaController {

    private final ConfiguracionSistemaService configService;

    @GetMapping("/publicas")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Configuración pública",
               description = "Claves de la lista blanca, como APPS_RECOMENDADAS_GPX. "
                           + "Una clave ausente se omite en lugar de dar error.")
    public ResponseEntity<ApiResponse<Map<String, String>>> publicas() {
        return ResponseEntity.ok(ApiResponse.ok(configService.publicas()));
    }
}
