import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/montana_model.dart';

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
}
