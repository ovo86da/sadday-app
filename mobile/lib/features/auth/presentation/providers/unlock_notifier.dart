import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:local_auth/local_auth.dart';
import '../../../../core/auth/auth_provider.dart';
import '../../../../core/config/app_logger.dart';

class UnlockState {
  const UnlockState({this.loading = false, this.failedAttempts = 0, this.error});
  final bool loading;
  final int failedAttempts;
  final String? error;

  static const maxAttempts = 3;
  bool get isLocked => failedAttempts >= maxAttempts;

  UnlockState copyWith({bool? loading, int? failedAttempts, String? error}) =>
      UnlockState(
        loading: loading ?? this.loading,
        failedAttempts: failedAttempts ?? this.failedAttempts,
        error: error,
      );
}

final unlockNotifierProvider =
    NotifierProvider<UnlockNotifier, UnlockState>(UnlockNotifier.new);

class UnlockNotifier extends Notifier<UnlockState> {
  final _localAuth = LocalAuthentication();

  @override
  UnlockState build() => const UnlockState();

  Future<void> unlock() async {
    if (state.isLocked) return;
    state = state.copyWith(loading: true, error: null);
    try {
      final authenticated = await _localAuth.authenticate(
        localizedReason: 'Usa tu huella o Face ID para acceder a Sadday',
      );
      if (!authenticated) {
        _registerFailure('Autenticación biométrica cancelada.');
        return;
      }
      final refreshed =
          await ref.read(authNotifierProvider.notifier).refresh();
      if (!refreshed) {
        _registerFailure('No se pudo verificar tu sesión.');
      } else {
        state = const UnlockState();
      }
    } catch (e, s) {
      AppLogger.e('biometric unlock error', e, s);
      _registerFailure('Error al autenticar.');
    }
  }

  Future<void> logout() =>
      ref.read(authNotifierProvider.notifier).logout();

  void _registerFailure(String message) {
    final attempts = state.failedAttempts + 1;
    if (attempts >= UnlockState.maxAttempts) {
      logout();
    } else {
      state = state.copyWith(
        loading: false,
        failedAttempts: attempts,
        error: message,
      );
    }
  }
}
