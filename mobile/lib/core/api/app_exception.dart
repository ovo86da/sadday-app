import 'package:dio/dio.dart';

sealed class AppException implements Exception {
  const AppException();
}

/// Convierte cualquier error en una [AppException] tipada.
///
/// Cubre dos caminos:
///  - Requests del Dio principal: el ErrorInterceptor ya embutió la
///    AppException dentro de [DioException.error] → se desenvuelve.
///  - Requests del authDio (login, refresh, etc.), que no tiene
///    ErrorInterceptor: el DioException llega crudo y se clasifica aquí
///    por tipo de error y status code.
AppException unwrapDio(Object e) {
  if (e is AppException) return e;
  if (e is DioException) {
    final inner = e.error;
    if (inner is AppException) return inner;
    return _classifyDio(e);
  }
  return const ServerException();
}

/// True si el DioException es un fallo de transporte (sin conexión o servidor
/// inalcanzable), no una respuesta HTTP de error.
///
/// unknown sin response cubre SocketException a nivel OS (ej: servidor apagado),
/// que Dio no clasifica como connectionError en todas las plataformas. Fuente
/// única de verdad: la usan el ErrorInterceptor, [_classifyDio] y el flujo de
/// refresh para no borrar el token ante un corte de red transitorio.
bool isConnectionError(DioException e) =>
    e.type == DioExceptionType.connectionError ||
    e.type == DioExceptionType.connectionTimeout ||
    e.type == DioExceptionType.sendTimeout ||
    e.type == DioExceptionType.receiveTimeout ||
    (e.type == DioExceptionType.unknown && e.response == null);

/// Clasifica un DioException crudo (sin AppException envuelta) por tipo de
/// error de transporte y, si hubo respuesta, por status code.
AppException _classifyDio(DioException e) {
  if (isConnectionError(e)) return const NetworkException();

  final status = e.response?.statusCode;
  if (status == 401) return const UnauthorizedException();
  if (status == 403) return const ForbiddenException();
  if (status != null && status >= 400 && status < 500) {
    return BusinessException(_serverMessage(e.response) ?? 'Error en la solicitud.');
  }
  return const ServerException();
}

String? _serverMessage(Response<dynamic>? r) {
  final d = r?.data;
  if (d is Map) return d['message'] as String? ?? d['error'] as String?;
  return null;
}

class NetworkException extends AppException {
  const NetworkException();
  @override
  String toString() =>
      'Estamos teniendo problemas para conectarnos al servidor.\n'
      'Verifica tu conexión a internet o intenta nuevamente más tarde.';
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
  String toString() =>
      'El servidor encontró un problema inesperado.\n'
      'Intenta nuevamente más tarde.';
}
