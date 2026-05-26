import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/app_config.dart';
import 'cookie_jar_provider.dart';

// Dio sin interceptores de auth — para endpoints públicos y de auth
// (login, refresh, logout, forgot-password, etc.).
final authDioProvider = Provider<Dio>((ref) {
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
  dio.interceptors.add(CookieManager(jar));
  ref.onDispose(dio.close);
  return dio;
});
