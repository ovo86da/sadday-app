package com.sadday.app.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Valida que una contraseña cumpla requisitos mínimos de complejidad.
 *
 * <p>Reglas (además del {@code @Size(min=12)} que ya existe en los DTOs):
 * <ul>
 *   <li>Al menos una letra minúscula</li>
 *   <li>Al menos una letra mayúscula</li>
 *   <li>Al menos un dígito</li>
 *   <li>Al menos un símbolo (cualquier carácter imprimible que no sea letra ni dígito)</li>
 * </ul>
 *
 * <p>Estas reglas complementan la longitud mínima de 12 caracteres ya validada
 * por {@code @Size}. Juntas proporcionan un balance razonable entre seguridad
 * y usabilidad, alineado con las directrices OWASP 2026.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "La contraseña debe contener al menos una mayúscula, una minúscula, un dígito y un símbolo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
