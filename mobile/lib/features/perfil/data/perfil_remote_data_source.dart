import 'package:dio/dio.dart';
import '../domain/models/perfil_model.dart';

class PerfilRemoteDataSource {
  const PerfilRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PerfilSocio> getMiPerfil() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/socios/me');
    return PerfilSocio.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<PerfilSocio> actualizarPerfil(Map<String, dynamic> data) async {
    final res = await _dio.patch<Map<String, dynamic>>('/v1/socios/me',
        data: data);
    return PerfilSocio.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<List<SesionActiva>> getSesiones() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/auth/sessions');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((s) => SesionActiva.fromJson(s as Map<String, dynamic>))
        .toList();
  }

  Future<void> cerrarSesion(String sessionId) =>
      _dio.delete<void>('/v1/auth/sessions/$sessionId');

  Future<void> cerrarOtrasSesiones() =>
      _dio.delete<void>('/v1/auth/sessions/others');

  Future<bool> getMfaStatus() async {
    final res =
        await _dio.get<Map<String, dynamic>>('/v1/auth/mfa/status');
    final data = res.data!['data'] as Map<String, dynamic>? ?? {};
    return data['totpEnabled'] as bool? ?? false;
  }

  Future<({String otpAuthUri, String base32Secret})> setupMfa() async {
    final res = await _dio.post<Map<String, dynamic>>('/v1/auth/mfa/setup');
    final data = res.data!['data'] as Map<String, dynamic>;
    return (
      otpAuthUri: data['otpAuthUri'] as String? ?? '',
      base32Secret: data['base32Secret'] as String? ?? '',
    );
  }

  Future<void> confirmMfa(String code) => _dio.post<void>(
        '/v1/auth/mfa/confirm',
        data: {'code': code},
      );

  Future<void> disableMfa(String code) =>
      _dio.delete<void>('/v1/auth/mfa', data: {'code': code});
}
