import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/app_exception.dart';
import '../../data/auth_repository.dart';

sealed class LoginUiState {
  const LoginUiState();
}

class LoginIdle extends LoginUiState {
  const LoginIdle();
}

class LoginLoading extends LoginUiState {
  const LoginLoading();
}

class LoginError extends LoginUiState {
  const LoginError(this.message);
  final String message;
}

final loginNotifierProvider =
    NotifierProvider<LoginNotifier, LoginUiState>(LoginNotifier.new);

class LoginNotifier extends Notifier<LoginUiState> {
  @override
  LoginUiState build() => const LoginIdle();

  Future<void> login(String username, String password) =>
      _run(() => ref.read(authRepositoryProvider).login(username, password));

  Future<void> verifyMfa(String challengeToken, String code) =>
      _run(() => ref.read(authRepositoryProvider).verifyMfa(challengeToken, code));

  Future<void> verifyCountryChallenge(String token, String country) =>
      _run(() => ref
          .read(authRepositoryProvider)
          .verifyCountryChallenge(token, country));

  Future<void> forgotPassword(String email) =>
      _run(() => ref.read(authRepositoryProvider).forgotPassword(email));

  Future<void> resetPassword({
    required String token,
    required String password,
    required String passwordConfirmation,
  }) =>
      _run(() => ref.read(authRepositoryProvider).resetPassword(
            token: token,
            password: password,
            passwordConfirmation: passwordConfirmation,
          ));

  Future<void> completeRegistration({
    required String invitationToken,
    required String nombre,
    required String apellido,
    required String password,
    required String passwordConfirmation,
  }) =>
      _run(() => ref.read(authRepositoryProvider).completeRegistration(
            invitationToken: invitationToken,
            nombre: nombre,
            apellido: apellido,
            password: password,
            passwordConfirmation: passwordConfirmation,
          ));

  void clearError() => state = const LoginIdle();

  Future<void> _run(Future<void> Function() action) async {
    state = const LoginLoading();
    try {
      await action();
      state = const LoginIdle();
    } catch (e) {
      switch (unwrapDio(e)) {
        case BusinessException(:final message):
          state = LoginError(message);
        case NetworkException():
          state = const LoginError('Sin conexión a internet.');
        case UnauthorizedException():
          state = const LoginError('Credenciales incorrectas.');
        case ForbiddenException():
        case ServerException():
          state = const LoginError('Error del servidor. Intenta más tarde.');
      }
    }
  }
}
