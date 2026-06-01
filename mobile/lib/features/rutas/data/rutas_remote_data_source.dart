import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/ruta_model.dart';

class RutasRemoteDataSource {
  const RutasRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PagedResponse<Ruta>> getRutas({
    int page = 0,
    String? q,
    String? tipoActividad,
    String? nivelMinimoSocioId,
    int? mountainId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/rutas',
        queryParameters: {
          'page': page,
          'size': 20,
          if (q != null && q.isNotEmpty) 'q': q,
          'tipoActividad': ?tipoActividad,
          'nivelMinimoSocioId': ?nivelMinimoSocioId,
          'mountainId': ?mountainId,
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Ruta.fromJson);
  }

  Future<Ruta> getRutaDetail(int id) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/rutas/$id');
    return Ruta.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  /// Rutas aprobadas de una montaña — para selección en salidas de Alpinismo.
  Future<List<Ruta>> getRutasByMountain(int mountainId) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/rutas',
        queryParameters: {
          'mountainId': mountainId,
          'estado': 'APROBADA',
          'size': 500,
          'sort': 'nombre,asc',
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Ruta.fromJson).items;
  }

  Future<void> crearRuta(Map<String, dynamic> data) =>
      _dio.post<void>('/v1/rutas', data: data);

  Future<void> aprobarRuta(int id) => _dio.patch<void>('/v1/rutas/$id/aprobar');

  Future<void> rechazarRuta(int id, String motivo) =>
      _dio.patch<void>('/v1/rutas/$id/rechazar', data: {'motivo': motivo});

  /// Rutas aprobadas filtradas por tipo de actividad
  /// (CICLISMO, ESCALADA, TREKKING).
  Future<List<Ruta>> getRutasByActividad(String tipoActividad) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/rutas',
        queryParameters: {
          'tipoActividad': tipoActividad,
          'estado': 'APROBADA',
          'size': 500,
          'sort': 'nombre,asc',
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Ruta.fromJson).items;
  }
}
