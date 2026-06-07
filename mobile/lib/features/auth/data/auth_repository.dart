import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/auth_dio_provider.dart';
import '../../../core/auth/auth_provider.dart';
import '../../../core/auth/user_model.dart';
import '../../../core/storage/secure_storage_service.dart';
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
    await _applyResponse(response);
  }

  Future<void> verifyMfa(String challengeToken, String code) async {
    final response = await dataSource.verifyMfa(challengeToken, code);
    await _applyResponse(response);
  }

  Future<void> verifyCountryChallenge(String token, String country) async {
    final response = await dataSource.verifyCountryChallenge(token, country);
    await _applyResponse(response);
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
    required String username,
    required String nombre,
    required String apellido,
    required String password,
    required String passwordConfirmation,
    String? fechaNacimiento,
    String? direccion,
    List<String>? documentIdsToAccept,
    List<Map<String, dynamic>>? contactosEmergencia,
    Map<String, dynamic>? informacionMedica,
  }) =>
      dataSource.completeRegistration(
        invitationToken: invitationToken,
        username: username,
        nombre: nombre,
        apellido: apellido,
        password: password,
        passwordConfirmation: passwordConfirmation,
        fechaNacimiento: fechaNacimiento,
        direccion: direccion,
        documentIdsToAccept: documentIdsToAccept,
        contactosEmergencia: contactosEmergencia,
        informacionMedica: informacionMedica,
      );

  Future<Map<String, dynamic>> getTokenInfo(String token) =>
      dataSource.getTokenInfo(token);

  Future<void> _applyResponse(LoginApiResponse response) async {
    switch (response) {
      case LoginSuccess(:final accessToken, :final refreshToken, :final userJson):
        // Persistir el refresh token en Keychain/Keystore antes de notificar
        // el estado autenticado para evitar un cold start sin token si la app
        // se cierra inmediatamente después del login.
        await SecureStorageService.instance.saveRefreshToken(refreshToken);
        authNotifier.setAuthenticated(accessToken, UserModel.fromJson(userJson));
      case LoginMfaRequired(:final challengeToken):
        authNotifier.setPendingMfa(challengeToken);
      case LoginCountryChallengeRequired(:final token):
        authNotifier.setPendingCountryChallenge(token);
    }
  }
}
