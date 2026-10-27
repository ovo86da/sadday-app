import 'package:dio/dio.dart';
import '../domain/models/cambio_umbral_model.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/montana_model.dart';
import '../domain/models/mountain_lookups_model.dart';

class MontanasRemoteDataSource {
  const MontanasRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PagedResponse<Montana>> getMontanas({int page = 0, String? q}) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/mountains',
        queryParameters: {
          'page': page,
          'size': 20,
          if (q != null && q.isNotEmpty) 'q': q,
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Montana.fromJson);
  }

  Future<Montana> getMontanaDetail(int id) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/mountains/$id');
    return Montana.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<void> crearMontana(Map<String, dynamic> data) =>
      _dio.post<void>('/v1/mountains', data: data);

  /// Lista completa de montañas, ordenada por nombre — para selectores.
  Future<List<Montana>> getAllMontanas() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/mountains',
        queryParameters: {'size': 500, 'sort': 'nombre,asc'});
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Montana.fromJson).items;
  }

  Future<MountainLookups> getLookups() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/mountains/lookups');
    return MountainLookups.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  // ── Cambios de umbral pendientes (FR-026) ────────────────────────────────

  Future<List<CambioUmbral>> getCambiosUmbralPendientes() async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/mountains/acceso-por-nivel/cambios');
    return (res.data!['data'] as List<dynamic>)
        .map((e) => CambioUmbral.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> aprobarCambioUmbral(String id) =>
      _dio.post<void>('/v1/mountains/acceso-por-nivel/cambios/$id/aprobar');

  Future<void> rechazarCambioUmbral(String id, String motivo) =>
      _dio.post<void>('/v1/mountains/acceso-por-nivel/cambios/$id/rechazar',
          data: {'motivo': motivo});
}
