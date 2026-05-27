import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/app_config.dart';
import 'interceptors/auth_interceptor.dart';
import 'interceptors/error_interceptor.dart';

/// Dio principal — todas las requests autenticadas de los features.
/// Incluye [AuthInterceptor] (añade el Bearer token) y [ErrorInterceptor]
/// (maneja el refresh automático ante un 401).
final dioClientProvider = Provider<Dio>((ref) {
  final dio = Dio(
    BaseOptions(
      baseUrl: AppConfig.apiBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 30),
      headers: const {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'X-Sadday-Client': 'mobile',
      },
    ),
  );
  dio.interceptors.addAll([
    AuthInterceptor(ref: ref),
    ErrorInterceptor(dio: dio, ref: ref),
  ]);
  ref.onDispose(dio.close);
  return dio;
});
