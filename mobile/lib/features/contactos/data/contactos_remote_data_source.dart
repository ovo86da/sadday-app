import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/contacto_model.dart';

class ContactosRemoteDataSource {
  const ContactosRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PagedResponse<Contacto>> getContactos({
    int page = 0,
    int size = 30,
    String? q,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/contactos',
        queryParameters: {
          'page': page,
          'size': size,
          if (q != null && q.isNotEmpty) 'q': q,
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Contacto.fromJson);
  }

  Future<void> crear(Map<String, dynamic> data) =>
      _dio.post<void>('/v1/contactos', data: data);

  Future<void> editar(int id, Map<String, dynamic> data) =>
      _dio.put<void>('/v1/contactos/$id', data: data);

  Future<void> eliminar(int id) =>
      _dio.delete<void>('/v1/contactos/$id');
}
