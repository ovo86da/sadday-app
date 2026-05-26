import 'package:dio/dio.dart';

sealed class AppException implements Exception {
  const AppException();
}

/// Desenvuelve la AppException que el ErrorInterceptor embute dentro de
/// DioException.error. Si no puede mapearlo, retorna ServerException.
AppException unwrapDio(Object e) {
  if (e is AppException) return e;
  if (e is DioException) {
    final inner = e.error;
    if (inner is AppException) return inner;
  }
  return const ServerException();
}

class NetworkException extends AppException {
  const NetworkException();
  @override
  String toString() => 'Sin conexión a internet.';
}

class UnauthorizedException extends AppException {
  const UnauthorizedException();
  @override
  String toString() => 'Sesión expirada. Por favor inicia sesión nuevamente.';
}

class ForbiddenException extends AppException {
  const ForbiddenException();
  @override
  String toString() => 'No tienes permisos para realizar esta acción.';
}

class BusinessException extends AppException {
  const BusinessException(this.message);
  final String message;
  @override
  String toString() => message;
}

class ServerException extends AppException {
  const ServerException();
  @override
  String toString() => 'Error del servidor. Intenta más tarde.';
}
