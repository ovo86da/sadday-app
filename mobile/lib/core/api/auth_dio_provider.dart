import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/app_config.dart';

/// Dio para endpoints públicos y de auth (login, refresh, logout, etc.).
/// No incluye interceptores de autenticación — los usa antes de tener tokens.
/// El header [X-Sadday-Client: mobile] activa el flujo nativo de refresh token
/// en el backend (body JSON en lugar de cookie HttpOnly).
final authDioProvider = Provider<Dio>((ref) {
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
  ref.onDispose(dio.close);
  return dio;
});
