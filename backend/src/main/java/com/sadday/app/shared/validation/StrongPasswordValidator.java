package com.sadday.app.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador para {@link StrongPassword}.
 *
 * <p>Verifica que la contraseña contenga al menos:
 * <ul>
 *   <li>Una letra minúscula (a–z)</li>
 *   <li>Una letra mayúscula (A–Z)</li>
 *   <li>Un dígito (0–9)</li>
 *   <li>Un símbolo (cualquier carácter imprimible que no sea letra, dígito ni espacio)</li>
 * </ul>
 *
 * <p>La validación de longitud mínima la maneja {@code @Size(min=12)} en el DTO.
 * Este validador no rechaza valores {@code null} ni vacíos — eso lo maneja {@code @NotBlank}.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        boolean hasLower  = false;
        boolean hasUpper  = false;
        boolean hasDigit  = false;
        boolean hasSymbol = false;

        for (char c : value.toCharArray()) {
            if      (Character.isLowerCase(c))  hasLower  = true;
            else if (Character.isUpperCase(c))  hasUpper  = true;
            else if (Character.isDigit(c))      hasDigit  = true;
            else if (!Character.isWhitespace(c)) hasSymbol = true;

            if (hasLower && hasUpper && hasDigit && hasSymbol) return true;
        }

        return false;
    }
}
