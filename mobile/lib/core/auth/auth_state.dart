import 'user_model.dart';

sealed class AuthState {
  const AuthState();
}

class AuthUnauthenticated extends AuthState {
  const AuthUnauthenticated();
}

// Access token limpiado por inactividad; refresh token sigue en SecureStorage.
class AuthLocked extends AuthState {
  const AuthLocked();
}

// Esperando código MFA (TOTP)
class AuthPendingMfa extends AuthState {
  const AuthPendingMfa({required this.challengeToken});
  final String challengeToken;
}

// Esperando country challenge
class AuthPendingCountryChallenge extends AuthState {
  const AuthPendingCountryChallenge({required this.token});
  final String token;
}

class AuthAuthenticated extends AuthState {
  const AuthAuthenticated({required this.accessToken, required this.user});
  final String    accessToken; // Solo en memoria — nunca a disco
  final UserModel user;
}
