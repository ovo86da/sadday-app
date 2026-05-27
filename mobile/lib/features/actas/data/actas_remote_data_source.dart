import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/acta_model.dart';

class ActasRemoteDataSource {
  const ActasRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PagedResponse<Acta>> getActas({int page = 0, String? tipo, String? q}) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/actas',
        queryParameters: {
          'page': page,
          'size': 20,
          'tipo': ?tipo,
          if (q != null && q.isNotEmpty) 'q': q,
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Acta.fromJson);
  }

  Future<ActaDetalle> getActaDetail(String id) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/actas/$id');
    return ActaDetalle.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<void> createActa(CreateActaRequest req) =>
      _dio.post<void>('/v1/actas', data: req.toJson());

  Future<void> updateActa(String id, CreateActaRequest req) =>
      _dio.put<void>('/v1/actas/$id', data: req.toJson());

  Future<void> deleteActa(String id) =>
      _dio.delete<void>('/v1/actas/$id');

  Future<List<int>> generarPdf(String id) async {
    final res = await _dio.post<List<int>>(
      '/v1/actas/$id/pdf',
      options: Options(responseType: ResponseType.bytes),
    );
    return res.data ?? [];
  }

  Future<List<int>> descargarPdf(String id) async {
    final res = await _dio.get<List<int>>(
      '/v1/actas/$id/pdf',
      options: Options(responseType: ResponseType.bytes),
    );
    return res.data ?? [];
  }

  Future<Map<String, dynamic>> importarPreview(String filePath) async {
    final formData = FormData.fromMap({
      'file': await MultipartFile.fromFile(filePath),
    });
    final res = await _dio.post<Map<String, dynamic>>(
        '/v1/actas/importar', data: formData);
    return res.data!['data'] as Map<String, dynamic>;
  }

  Future<void> importarConfirmar(Map<String, dynamic> previewData) =>
      _dio.post<void>('/v1/actas/importar/confirmar', data: previewData);
}
