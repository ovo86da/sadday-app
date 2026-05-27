// Respuesta de POST /v1/auth/login — tres escenarios posibles.
sealed class LoginApiResponse {
  const LoginApiResponse();
}

/// Login completado: acceso concedido.
/// [refreshToken] viene en el body JSON (flujo mobile nativo).
/// [userJson] es el mapa completo del body para construir [UserModel].
class LoginSuccess extends LoginApiResponse {
  const LoginSuccess({
    required this.accessToken,
    required this.refreshToken,
    required this.userJson,
  });
  final String accessToken;
  final String refreshToken;
  final Map<String, dynamic> userJson;
}

class LoginMfaRequired extends LoginApiResponse {
  const LoginMfaRequired({required this.challengeToken});
  final String challengeToken;
}

class LoginCountryChallengeRequired extends LoginApiResponse {
  const LoginCountryChallengeRequired({required this.token});
  final String token;
}
