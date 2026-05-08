package com.sadday.app.auth.controller;

import com.sadday.app.auth.dto.CompleteRegistroRequest;
import com.sadday.app.auth.dto.TokenInfoResponse;
import com.sadday.app.auth.service.EmailVerificationService;
import com.sadday.app.shared.dto.ApiResponse;
import com.sadday.app.shared.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;

/**
 * Controlador para el registro inicial de nuevos socios.
 *
 * <p>Estos endpoints son públicos (no requieren JWT). El acceso se controla mediante
 * el token de invitación enviado por email, que expira en 72h y es de un solo uso.
 *
 * <p>Flujo nuevo (pre-registro): Secretaria inicia invitación con cédula/correo →
 * sistema envía email → socio abre el link, consulta {@code GET /token-info} para
 * conocer qué campos mostrar, llena sus datos personales y credenciales, y hace
 * {@code POST /complete}.
 */
@Validated
@RestController
@RequestMapping(ApiPaths.REGISTRO)
@RequiredArgsConstructor
@Tag(name = "Registro", description = "Activación de cuenta para nuevos socios")
public class RegistroController {

    private final EmailVerificationService emailVerificationService;

    @GetMapping("/token-info")
    @Operation(
            summary = "Consultar tipo de token antes de mostrar el formulario",
            description = "Valida que el token sea vigente y devuelve el tipo de formulario a mostrar. " +
                          "Si fromCsvImport=true, el formulario muestra nombre/apellido como solo lectura " +
                          "y exige los campos faltantes (fecha nacimiento, dirección, contacto emergencia).")
    public ResponseEntity<ApiResponse<TokenInfoResponse>> tokenInfo(
            @RequestParam @NotBlank String token) {

        return ResponseEntity.ok(ApiResponse.ok(emailVerificationService.getTokenInfo(token)));
    }

    @PostMapping("/complete")
    @Operation(
            summary = "Completar registro inicial",
            description = "Valida el token de invitación enviado por email y establece " +
                          "las credenciales (username y contraseña) del socio. " +
                          "En pre-registro también crea el socio con sus datos personales. " +
                          "El token es de un solo uso y expira en 72 horas.")
    public ResponseEntity<ApiResponse<Void>> completeRegistro(
            @Valid @RequestBody CompleteRegistroRequest request) {

        emailVerificationService.complete(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "Cuenta activada correctamente. Ya puedes iniciar sesión."));
    }
}
