import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/admin_models.dart';

class AdminRemoteDataSource {
  const AdminRemoteDataSource(this._dio);
  final Dio _dio;

  Future<List<AdminConfig>> getConfig() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/admin/config');
    final data = res.data!['data'];
    if (data is List) {
      return data
          .map((e) => AdminConfig.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    final map = data as Map<String, dynamic>;
    return map.entries
        .map((e) => AdminConfig(clave: e.key, valor: e.value?.toString() ?? ''))
        .toList();
  }

  Future<void> patchConfig(String clave, String valor) =>
      _dio.patch<void>('/v1/admin/config/$clave', data: {'valor': valor});

  Future<PagedResponse<AuditoriaEntry>> getAuditoria({
    int page = 0,
    int size = 30,
    String? entidadAfectada,
    String? actorUsername,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/admin/auditoria',
        queryParameters: {
          'page': page,
          'size': size,
          'entidadAfectada': ?entidadAfectada,
          'actorUsername': ?actorUsername,
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, AuditoriaEntry.fromJson);
  }

  Future<PagedResponse<SecurityEvent>> getSecurityEvents({
    int page = 0,
    int size = 30,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/admin/security-events',
        queryParameters: {'page': page, 'size': size});
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, SecurityEvent.fromJson);
  }

  Future<List<UsuarioAuth>> getUsuariosAuth() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/admin/usuarios-auth');
    final data = res.data!['data'];
    if (data is List) {
      return data
          .map((e) => UsuarioAuth.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    final content =
        (data as Map<String, dynamic>)['content'] as List<dynamic>? ?? [];
    return content
        .map((e) => UsuarioAuth.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> cambiarEstadoAcceso(String socioId, String codigo) =>
      _dio.patch<void>('/v1/admin/usuarios-auth/$socioId/estado-acceso',
          queryParameters: {'codigo': codigo});

  Future<void> desbloquear(String socioId) =>
      _dio.post<void>('/v1/admin/usuarios-auth/$socioId/desbloquear');

  Future<void> cerrarSesion(String socioId) =>
      _dio.post<void>('/v1/admin/usuarios-auth/$socioId/cerrar-sesion');

  Future<void> emergencyReset(String socioId) =>
      _dio.post<void>('/v1/socios/$socioId/emergency-reset');
}
