import 'package:dio/dio.dart';
import '../domain/models/auth_models.dart';

class AuthRemoteDataSource {
  const AuthRemoteDataSource(this._dio);
  final Dio _dio;

  Future<LoginApiResponse> login(String username, String password) async {
    final res = await _dio.post('/v1/auth/login', data: {
      'username': username,
      'password': password,
    });
    final inner = (res.data as Map<String, dynamic>)['data'] as Map<String, dynamic>;

    if (res.statusCode == 200) {
      return LoginSuccess(
        accessToken: inner['accessToken'] as String,
        userJson: inner,
      );
    }
    // 202 — desafíos intermedios
    if (inner.containsKey('challengeToken') && !inner.containsKey('countryChallengeToken')) {
      return LoginMfaRequired(challengeToken: inner['challengeToken'] as String);
    }
    return LoginCountryChallengeRequired(
      token: inner['countryChallengeToken'] as String,
    );
  }

  Future<LoginApiResponse> verifyMfa(String challengeToken, String code) async {
    final res = await _dio.post('/v1/auth/mfa/login', data: {
      'challengeToken': challengeToken,
      'mfaCode': code,
    });
    final inner = (res.data as Map<String, dynamic>)['data'] as Map<String, dynamic>;
    return LoginSuccess(
      accessToken: inner['accessToken'] as String,
      userJson: inner,
    );
  }

  Future<LoginApiResponse> verifyCountryChallenge(
    String token,
    String code,
  ) async {
    final res = await _dio.post('/v1/auth/country-challenge/verify', data: {
      'challengeToken': token,
      'code': code,
    });
    final inner = (res.data as Map<String, dynamic>)['data'] as Map<String, dynamic>;
    return LoginSuccess(
      accessToken: inner['accessToken'] as String,
      userJson: inner,
    );
  }

  Future<void> forgotPassword(String email) async {
    await _dio.post('/v1/auth/forgot-password', data: {'correo': email});
  }

  Future<void> resetPassword({
    required String token,
    required String password,
    required String passwordConfirmation,
  }) async {
    await _dio.post('/v1/auth/reset-password', data: {
      'token': token,
      'nuevaPassword': password,
      'confirmPassword': passwordConfirmation,
    });
  }

  Future<LoginApiResponse> completeRegistration({
    required String invitationToken,
    required String username,
    required String nombre,
    required String apellido,
    required String password,
    required String passwordConfirmation,
  }) async {
    final res = await _dio.post('/v1/registro/completar', data: {
      'token': invitationToken,
      'username': username,
      'nombre': nombre,
      'apellido': apellido,
      'password': password,
      'confirmPassword': passwordConfirmation,
    });
    final inner = (res.data as Map<String, dynamic>)['data'] as Map<String, dynamic>;
    return LoginSuccess(
      accessToken: inner['accessToken'] as String,
      userJson: inner,
    );
  }
}
