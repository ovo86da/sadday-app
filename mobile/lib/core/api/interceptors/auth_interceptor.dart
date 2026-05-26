import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../auth/auth_state.dart';
import '../../auth/jwt_utils.dart';
import '../../auth/auth_provider.dart';

// Agrega Bearer token y dispara refresh proactivo si el JWT está a punto de expirar.
class AuthInterceptor extends Interceptor {
  const AuthInterceptor({required this.ref});
  final Ref ref;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final auth = ref.read(authNotifierProvider).asData?.value;
    if (auth is AuthAuthenticated) {
      if (JwtUtils.isExpiredWithBuffer(auth.accessToken)) {
        await ref.read(authNotifierProvider.notifier).refresh();
        final refreshed = ref.read(authNotifierProvider).asData?.value;
        if (refreshed is AuthAuthenticated) {
          options.headers['Authorization'] = 'Bearer ${refreshed.accessToken}';
        }
      } else {
        options.headers['Authorization'] = 'Bearer ${auth.accessToken}';
      }
    }
    handler.next(options);
  }
}
