import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../auth/auth_state.dart';
import '../app_exception.dart';
import '../refresh_lock.dart';
import '../../auth/auth_provider.dart';

class ErrorInterceptor extends Interceptor {
  const ErrorInterceptor({required this.dio, required this.ref});
  final Dio dio;
  final Ref ref;

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    // Sin conexión
    if (err.type == DioExceptionType.connectionError ||
        err.type == DioExceptionType.connectionTimeout ||
        err.type == DioExceptionType.sendTimeout ||
        err.type == DioExceptionType.receiveTimeout) {
      return handler.reject(_wrap(err, const NetworkException()));
    }

    final status = err.response?.statusCode;

    if (status == 401) {
      final refreshed = await withRefreshLock(
        () => ref.read(authNotifierProvider.notifier).refresh(),
      );
      if (refreshed) {
        final auth = ref.read(authNotifierProvider).asData?.value;
        if (auth is AuthAuthenticated) {
          final opts = err.requestOptions;
          opts.headers['Authorization'] = 'Bearer ${auth.accessToken}';
          try {
            return handler.resolve(await dio.fetch(opts));
          } on DioException catch (e) {
            return handler.reject(e);
          }
        }
      }
      await ref.read(authNotifierProvider.notifier).logout();
      return handler.reject(_wrap(err, const UnauthorizedException()));
    }

    if (status == 403) return handler.reject(_wrap(err, const ForbiddenException()));

    if (status != null && status >= 400 && status < 500) {
      final msg = _message(err.response) ?? 'Error en la solicitud.';
      return handler.reject(_wrap(err, BusinessException(msg)));
    }

    if (status != null && status >= 500) {
      return handler.reject(_wrap(err, const ServerException()));
    }

    handler.next(err);
  }

  DioException _wrap(DioException e, AppException cause) =>
      e.copyWith(error: cause);

  String? _message(Response? r) {
    try {
      final d = r?.data;
      if (d is Map) return d['message'] as String? ?? d['error'] as String?;
    } catch (_) {}
    return null;
  }
}
