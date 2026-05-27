import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/auth_dio_provider.dart';
import '../../../core/auth/auth_provider.dart';
import '../../../core/auth/user_model.dart';
import '../domain/models/auth_models.dart';
import 'auth_remote_data_source.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    dataSource: AuthRemoteDataSource(ref.watch(authDioProvider)),
    authNotifier: ref.read(authNotifierProvider.notifier),
  );
});

class AuthRepository {
  const AuthRepository({
    required this.dataSource,
    required this.authNotifier,
  });

  final AuthRemoteDataSource dataSource;
  final AuthNotifier authNotifier;

  Future<void> login(String username, String password) async {
    final response = await dataSource.login(username, password);
    _applyResponse(response);
  }

  Future<void> verifyMfa(String challengeToken, String code) async {
    final response = await dataSource.verifyMfa(challengeToken, code);
    _applyResponse(response);
  }

  Future<void> verifyCountryChallenge(String token, String country) async {
    final response = await dataSource.verifyCountryChallenge(token, country);
    _applyResponse(response);
  }

  Future<void> forgotPassword(String email) =>
      dataSource.forgotPassword(email);

  Future<void> resetPassword({
    required String token,
    required String password,
    required String passwordConfirmation,
  }) =>
      dataSource.resetPassword(
        token: token,
        password: password,
        passwordConfirmation: passwordConfirmation,
      );

  Future<void> completeRegistration({
    required String invitationToken,
    required String nombre,
    required String apellido,
    required String password,
    required String passwordConfirmation,
  }) async {
    final response = await dataSource.completeRegistration(
      invitationToken: invitationToken,
      nombre: nombre,
      apellido: apellido,
      password: password,
      passwordConfirmation: passwordConfirmation,
    );
    _applyResponse(response);
  }

  void _applyResponse(LoginApiResponse response) {
    switch (response) {
      case LoginSuccess(:final accessToken, :final userJson):
        authNotifier.setAuthenticated(accessToken, UserModel.fromJson(userJson));
      case LoginMfaRequired(:final challengeToken):
        authNotifier.setPendingMfa(challengeToken);
      case LoginCountryChallengeRequired(:final token):
        authNotifier.setPendingCountryChallenge(token);
    }
  }
}
