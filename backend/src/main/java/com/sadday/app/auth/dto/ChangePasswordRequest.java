package com.sadday.app.auth.dto;

import com.sadday.app.shared.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 12, max = 200, message = "La contraseña debe tener al menos 12 caracteres")
        @StrongPassword
        String newPassword,

        @NotBlank(message = "La confirmación de contraseña es obligatoria")
        @Size(min = 12, max = 200, message = "La contraseña debe tener al menos 12 caracteres")
        String confirmPassword,

        /** Código TOTP requerido solo si el usuario tiene 2FA activo. */
        String totpCode
) {}
