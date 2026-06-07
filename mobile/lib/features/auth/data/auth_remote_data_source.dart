import 'package:dio/dio.dart';
import '../../../../core/api/app_exception.dart';
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
        accessToken: _requireString(inner, 'accessToken'),
        refreshToken: _requireString(inner, 'refreshToken'),
        userJson: inner,
      );
    }
    // 202 — desafíos intermedios
    if (inner.containsKey('challengeToken') && !inner.containsKey('countryChallengeToken')) {
      return LoginMfaRequired(challengeToken: _requireString(inner, 'challengeToken'));
    }
    return LoginCountryChallengeRequired(
      token: _requireString(inner, 'countryChallengeToken'),
    );
  }

  Future<LoginApiResponse> verifyMfa(String challengeToken, String code) async {
    final res = await _dio.post('/v1/auth/mfa/login', data: {
      'challengeToken': challengeToken,
      'mfaCode': code,
    });
    final inner = (res.data as Map<String, dynamic>)['data'] as Map<String, dynamic>;
    return LoginSuccess(
      accessToken: _requireString(inner, 'accessToken'),
      refreshToken: _requireString(inner, 'refreshToken'),
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
      accessToken: _requireString(inner, 'accessToken'),
      refreshToken: _requireString(inner, 'refreshToken'),
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

  /// Completa el registro con todos los datos del wizard de 6 pasos.
  /// El backend devuelve 200 OK con mensaje (no tokens). El socio debe
  /// iniciar sesión por separado tras completar el registro.
  Future<void> completeRegistration({
    required String invitationToken,
    required String username,
    required String nombre,
    required String apellido,
    required String password,
    required String passwordConfirmation,
    String? fechaNacimiento,
    String? direccion,
    List<String>? documentIdsToAccept,
    List<Map<String, dynamic>>? contactosEmergencia,
    Map<String, dynamic>? informacionMedica,
  }) async {
    await _dio.post('/v1/registro/complete', data: {
      'token': invitationToken,
      'username': username,
      'nombre': nombre,
      'apellido': apellido,
      'password': password,
      'confirmPassword': passwordConfirmation,
      // ignore: use_null_aware_elements
      if (fechaNacimiento != null) 'fechaNacimiento': fechaNacimiento,
      if (direccion != null && direccion.isNotEmpty) 'direccion': direccion,
      if (documentIdsToAccept != null && documentIdsToAccept.isNotEmpty)
        'documentIdsToAccept': documentIdsToAccept,
      if (contactosEmergencia != null && contactosEmergencia.isNotEmpty)
        'contactosEmergencia': contactosEmergencia,
      // ignore: use_null_aware_elements
      if (informacionMedica != null) 'informacionMedica': informacionMedica,
    });
  }

  Future<Map<String, dynamic>> getTokenInfo(String token) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/v1/registro/token-info',
      queryParameters: {'token': token},
    );
    return res.data!['data'] as Map<String, dynamic>;
  }

  /// Extrae un campo String requerido; lanza [ServerException] si es nulo o ausente.
  static String _requireString(Map<String, dynamic> map, String key) {
    final value = map[key] as String?;
    if (value == null) throw const ServerException();
    return value;
  }
}
