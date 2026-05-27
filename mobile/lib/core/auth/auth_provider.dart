import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../api/auth_dio_provider.dart';
import '../api/app_exception.dart';
import '../api/cookie_jar_provider.dart';
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

    // El refresh token vive en el PersistCookieJar (cookie HttpOnly del backend).
    // Intentamos renovarlo directamente: si no hay cookie o está expirada el
    // servidor retorna 401 y _doRefresh() devuelve false → no autenticado.
    final ok = await _doRefresh();
    if (!ok) return const AuthUnauthenticated();
    return state.requireValue;
  }

  // Llamado por los interceptores ante un 401.
  Future<bool> refresh() => _doRefresh();

  // Llamado desde AuthRepository tras login exitoso (Phase 3).
  void setAuthenticated(String accessToken, UserModel user) {
    state = AsyncData(AuthAuthenticated(accessToken: accessToken, user: user));
  }

  void setPendingMfa(String challengeToken) =>
      state = AsyncData(AuthPendingMfa(challengeToken: challengeToken));

  void setPendingCountryChallenge(String token) =>
      state = AsyncData(AuthPendingCountryChallenge(token: token));

  // Inactividad: limpia access token en memoria, refresh token queda en SecureStorage.
  void onInactivityTimeout() => state = const AsyncData(AuthLocked());

  Future<void> logout() async {
    try {
      await _dio.post('/v1/auth/logout');
    } catch (e) {
      AppLogger.w('logout endpoint error', e);
    }
    // Eliminar la cookie del refresh token del jar persistente (cubre el
    // caso de que la request al servidor haya fallado por red).
    await ref.read(cookieJarProvider).deleteAll();
    await SecureStorageService.instance.setBiometricEnabled(false);
    state = const AsyncData(AuthUnauthenticated());
  }

  Future<bool> _doRefresh() async {
    try {
      final res = await _dio.post('/v1/auth/refresh');
      final inner = (res.data as Map<String, dynamic>)['data'] as Map<String, dynamic>?;
      final token = inner?['accessToken'] as String?;
      if (token == null) return false;
      final user = UserModel.fromJson(inner!);
      state = AsyncData(AuthAuthenticated(accessToken: token, user: user));
      return true;
    } on AppException {
      rethrow;
    } catch (e, s) {
      AppLogger.e('refresh failed', e, s);
      return false;
    }
  }
}
