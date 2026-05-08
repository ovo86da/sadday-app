package com.sadday.app.auth.dto;

/**
 * Resultado del primer paso de login.
 * <ul>
 *   <li>{@link Completed} — sin 2FA ni país desconocido: login completado.</li>
 *   <li>{@link MfaRequired} — usuario tiene 2FA activo: pendiente código TOTP.</li>
 *   <li>{@link CountryRequired} — país desconocido sin 2FA: pendiente código por email.</li>
 * </ul>
 */
public sealed interface LoginStepResult
        permits LoginStepResult.Completed, LoginStepResult.MfaRequired, LoginStepResult.CountryRequired {

    record Completed(LoginResult result) implements LoginStepResult {}

    record MfaRequired(MfaChallengeResponse challenge) implements LoginStepResult {}

    record CountryRequired(CountryChallengeResponse challenge) implements LoginStepResult {}
}
