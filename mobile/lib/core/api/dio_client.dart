import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/app_config.dart';
import 'cookie_jar_provider.dart';
import 'interceptors/auth_interceptor.dart';
import 'interceptors/error_interceptor.dart';

export 'cookie_jar_provider.dart';

// Dio principal — todas las requests autenticadas de los features.
final dioClientProvider = Provider<Dio>((ref) {
  final jar = ref.watch(cookieJarProvider);
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
    CookieManager(jar),
    AuthInterceptor(ref: ref),
    ErrorInterceptor(dio: dio, ref: ref),
  ]);
  ref.onDispose(dio.close);
  return dio;
});
