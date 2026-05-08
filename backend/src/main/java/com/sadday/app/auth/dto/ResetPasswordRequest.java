package com.sadday.app.auth.dto;

import com.sadday.app.shared.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "El token es obligatorio")
        String token,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 12, max = 200, message = "La contraseña debe tener al menos 12 caracteres")
        @StrongPassword
        String nuevaPassword,

        @NotBlank(message = "La confirmación de contraseña es obligatoria")
        @Size(min = 12, max = 200, message = "La contraseña debe tener al menos 12 caracteres")
        String confirmPassword
) {}
