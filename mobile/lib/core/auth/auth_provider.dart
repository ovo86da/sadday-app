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

    final hasToken = await SecureStorageService.instance.getRefreshToken() != null;
    if (!hasToken) return const AuthUnauthenticated();

    // Bugs 1 y 5: distinguir error de red (no borrar token) de token inválido.
    try {
      final ok = await _doRefresh();
      if (!ok) {
        await SecureStorageService.instance.deleteRefreshToken();
        return const AuthUnauthenticated();
      }
      return state.requireValue;
    } on DioException catch (e) {
      // Error de red transitorio — el token sigue siendo válido; no borrarlo.
      if (e.type == DioExceptionType.connectionError ||
          e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.sendTimeout ||
          e.type == DioExceptionType.receiveTimeout) {
        return const AuthUnauthenticated();
      }
      await SecureStorageService.instance.deleteRefreshToken();
      return const AuthUnauthenticated();
    } on AppException catch (e, s) {
      AppLogger.e('auth build failed', e, s);
      await SecureStorageService.instance.deleteRefreshToken();
      return const AuthUnauthenticated();
    }
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
  void onInactivityTimeout() => state = const AsyncData(AuthLocked());

  Future<void> logout() async {
    // Bug 3: si estamos en AuthLocked no hay access token en memoria.
    // Refrescar primero para obtener uno válido y autenticar el logout en el backend.
    String? currentAccessToken = switch (state.value) {
      AuthAuthenticated(:final accessToken) => accessToken,
      _ => null,
    };

    if (currentAccessToken == null) {
      try {
        final refreshed = await _doRefresh();
        if (refreshed) {
          currentAccessToken = switch (state.value) {
            AuthAuthenticated(:final accessToken) => accessToken,
            _ => null,
          };
        }
      } catch (_) {
        // Si el refresh falla, procedemos sin header — el backend puede rechazar
        // la revocación pero el token local se elimina de todas formas.
      }
    }

    final refreshToken = await SecureStorageService.instance.getRefreshToken();

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
  /// Lanza [DioException] de red para que [build] no borre el token (bug 1).
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

      await SecureStorageService.instance.saveRefreshToken(newRefreshToken);
      final user = UserModel.fromJson(inner!);
      state = AsyncData(AuthAuthenticated(accessToken: newAccessToken, user: user));
      return true;
    } on AppException {
      rethrow;
    } on DioException catch (e) {
      // Propagar errores de red para que build() no borre el token (bug 1).
      if (e.type == DioExceptionType.connectionError ||
          e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.sendTimeout ||
          e.type == DioExceptionType.receiveTimeout) {
        rethrow;
      }
      AppLogger.e('refresh failed', e);
      return false;
    } catch (e, s) {
      AppLogger.e('refresh failed', e, s);
      return false;
    }
  }
}
