// Respuesta de POST /v1/auth/login — tres escenarios posibles.
sealed class LoginApiResponse {
  const LoginApiResponse();
}

class LoginSuccess extends LoginApiResponse {
  const LoginSuccess({required this.accessToken, required this.userJson});
  final String accessToken;
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
