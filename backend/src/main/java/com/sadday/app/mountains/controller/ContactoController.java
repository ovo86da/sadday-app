package com.sadday.app.mountains.controller;

import com.sadday.app.mountains.dto.*;
import com.sadday.app.mountains.service.ContactoService;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContactoController {

    private final ContactoService contactoService;

    // ── Búsqueda de contactos (para formulario de informe) ───────────────────
    @GetMapping(ApiPaths.CONTACTOS + "/buscar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<GlobalContactoResponse>>> buscar(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(contactoService.buscarSugerencias(q)));
    }

    // ── CRUD contactos globales ──────────────────────────────────────────────
    @GetMapping(ApiPaths.CONTACTOS)
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ResponseEntity<ApiResponse<Page<GlobalContactoResponse>>> listar(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(contactoService.listar(q, pageable)));
    }

    @GetMapping(ApiPaths.CONTACTOS + "/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GlobalContactoResponse>> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(contactoService.obtener(id)));
    }

    @PostMapping(ApiPaths.CONTACTOS)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GlobalContactoResponse>> crear(
            @Valid @RequestBody CreateGlobalContactoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(contactoService.crear(request)));
    }

    @PutMapping(ApiPaths.CONTACTOS + "/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA')")
    public ResponseEntity<ApiResponse<GlobalContactoResponse>> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateGlobalContactoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(contactoService.actualizar(id, request)));
    }

    @DeleteMapping(ApiPaths.CONTACTOS + "/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        contactoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
