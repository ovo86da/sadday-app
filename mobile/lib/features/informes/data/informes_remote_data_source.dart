import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/informe_model.dart';

class InformesRemoteDataSource {
  const InformesRemoteDataSource(this._dio);
  final Dio _dio;

  /// El backend no expone GET /v1/informes. Como aproximación, listamos
  /// salidas con `tieneInforme=true` para alimentar la pestaña "Todos".
  Future<PagedResponse<InformeResumen>> getInformes({int page = 0}) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/salidas',
        queryParameters: {
          'page': page,
          'size': 20,
          'estado': 'REALIZADA',
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    final items = (data['content'] as List<dynamic>? ?? [])
        .where((e) => (e as Map)['tieneInforme'] == true)
        .map((e) {
          final s = e as Map<String, dynamic>;
          return InformeResumen(
            salidaId: s['id']?.toString() ?? '',
            salidaNombre: s['nombre'] as String? ?? '',
            fechaSalida: s['fechaInicio'] != null
                ? DateTime.tryParse(s['fechaInicio'] as String)
                : null,
            estado: 'COMPLETADO',
            tieneInforme: true,
          );
        })
        .toList();
    return PagedResponse(
      items: items,
      totalElements: (data['totalElements'] as num?)?.toInt() ?? items.length,
      totalPages: (data['totalPages'] as num?)?.toInt() ?? 1,
      currentPage: (data['number'] as num?)?.toInt() ?? 0,
    );
  }

  Future<List<InformeResumen>> getPendientesJefe() async {
    final res = await _dio
        .get<Map<String, dynamic>>('/v1/informes/pendientes-jefe');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => InformeResumen.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<Informe?> getInforme(String salidaId) async {
    final res = await _dio
        .get<Map<String, dynamic>>('/v1/informes/$salidaId');
    final data = res.data!['data'];
    if (data == null) return null;
    return Informe.fromJson(data as Map<String, dynamic>);
  }

  Future<void> createInforme(String salidaId, CreateInformeRequest req) =>
      _dio.post<void>('/v1/informes/$salidaId', data: req.toJson());

  Future<void> updateInforme(String salidaId, CreateInformeRequest req) =>
      _dio.put<void>('/v1/informes/$salidaId', data: req.toJson());

  Future<void> validarInforme(String salidaId) =>
      _dio.patch<void>('/v1/informes/$salidaId/validar');

  Future<List<int>> downloadPdf(String salidaId) async {
    final res = await _dio.get<List<int>>(
      '/v1/informes/$salidaId/pdf',
      options: Options(responseType: ResponseType.bytes),
    );
    return res.data ?? [];
  }

  Future<List<int>> generarPdf(String salidaId) async {
    final res = await _dio.post<List<int>>(
      '/v1/informes/$salidaId/pdf',
      options: Options(responseType: ResponseType.bytes),
    );
    return res.data ?? [];
  }
}
