import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../api/auth_dio_provider.dart';
import '../api/app_exception.dart';
import '../config/app_logger.dart';
import '../storage/secure_storage_service.dart';
import 'auth_state.dart';
import 'user_model.dart';

final authNotifierProvider =
    AsyncNotifierProvider<AuthNotifier, AuthState>(AuthNotifier.new);

class AuthNotifier extends AsyncNotifier<AuthState> {
  late Dio _dio;

  @override
  Future<AuthState> build() async {
    _dio = ref.watch(authDioProvider);

    // El refresh token vive en el Keychain/Keystore (SecureStorage).
    // Si no existe → no hay sesión activa.
    final hasToken = await SecureStorageService.instance.getRefreshToken() != null;
    if (!hasToken) return const AuthUnauthenticated();

    // Intentar renovar la sesión con el token almacenado.
    final ok = await _doRefresh();
    if (!ok) {
      // Token inválido o expirado → limpiar y pedir login.
      await SecureStorageService.instance.deleteRefreshToken();
      return const AuthUnauthenticated();
    }
    return state.requireValue;
  }

  /// Llamado por los interceptores ante un 401.
  Future<bool> refresh() => _doRefresh();

  /// Llamado desde AuthRepository tras login exitoso.
  void setAuthenticated(String accessToken, UserModel user) {
    state = AsyncData(AuthAuthenticated(accessToken: accessToken, user: user));
  }

  void setPendingMfa(String challengeToken) =>
      state = AsyncData(AuthPendingMfa(challengeToken: challengeToken));

  void setPendingCountryChallenge(String token) =>
      state = AsyncData(AuthPendingCountryChallenge(token: token));

  /// Inactividad: bloquea la pantalla sin eliminar la sesión.
  /// El refresh token permanece en SecureStorage para restaurarla.
  void onInactivityTimeout() => state = const AsyncData(AuthLocked());

  Future<void> logout() async {
    final refreshToken = await SecureStorageService.instance.getRefreshToken();

    // Incluir el access token actual en el header para que el backend
    // pueda verificar la identidad antes de revocar el refresh token.
    final currentAccessToken = switch (state.value) {
      AuthAuthenticated(:final accessToken) => accessToken,
      _ => null,
    };

    try {
      await _dio.post(
        '/v1/auth/logout',
        data: refreshToken != null ? {'refreshToken': refreshToken} : null,
        options: currentAccessToken != null
            ? Options(headers: {'Authorization': 'Bearer $currentAccessToken'})
            : null,
      );
    } catch (e) {
      AppLogger.w('logout endpoint error', e);
    }

    await SecureStorageService.instance.deleteRefreshToken();
    await SecureStorageService.instance.setBiometricEnabled(false);
    state = const AsyncData(AuthUnauthenticated());
  }

  /// Renueva el access token enviando el refresh token almacenado en el body.
  /// Rota el refresh token y guarda el nuevo en SecureStorage.
  Future<bool> _doRefresh() async {
    final refreshToken = await SecureStorageService.instance.getRefreshToken();
    if (refreshToken == null) return false;

    try {
      final res = await _dio.post(
        '/v1/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      final inner =
          (res.data as Map<String, dynamic>)['data'] as Map<String, dynamic>?;
      final newAccessToken  = inner?['accessToken']  as String?;
      final newRefreshToken = inner?['refreshToken'] as String?;
      if (newAccessToken == null || newRefreshToken == null) return false;

      // Guardar el nuevo refresh token ANTES de actualizar el estado.
      await SecureStorageService.instance.saveRefreshToken(newRefreshToken);
      final user = UserModel.fromJson(inner!);
      state = AsyncData(AuthAuthenticated(accessToken: newAccessToken, user: user));
      return true;
    } on AppException {
      rethrow;
    } catch (e, s) {
      AppLogger.e('refresh failed', e, s);
      return false;
    }
  }
}
