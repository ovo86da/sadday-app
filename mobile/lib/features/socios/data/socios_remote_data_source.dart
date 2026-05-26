import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/socio_model.dart';

class SociosRemoteDataSource {
  const SociosRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PagedResponse<Socio>> getSocios({
    int page = 0,
    int size = 20,
    String? q,
    int? rolId,
    int? estadoId,
    int? tipoId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/socios',
        queryParameters: {
          'page': page,
          'size': size,
          if (q != null && q.isNotEmpty) 'q': q,
          'rolId': ?rolId,
          'estadoId': ?estadoId,
          'tipoId': ?tipoId,
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Socio.fromJson);
  }

  Future<SocioDetalle> getSocioDetail(String id) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/socios/$id');
    return SocioDetalle.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<SociosLookups> getLookups() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/socios/lookups');
    return SociosLookups.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<List<HabilitacionLogEntry>> getHabilitacionLog(String id) async {
    final res = await _dio
        .get<Map<String, dynamic>>('/v1/socios/$id/habilitacion-log');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => HabilitacionLogEntry.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<Cuota>> getCuotas(String id) async {
    final res =
        await _dio.get<Map<String, dynamic>>('/v1/socios/$id/cuotas');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => Cuota.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<Invitacion>> getInvitaciones() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/socios/invitaciones');
    final data = res.data!['data'];
    if (data is List) {
      return data.map((e) => Invitacion.fromJson(e as Map<String, dynamic>)).toList();
    }
    final content = (data as Map<String, dynamic>)['content'] as List<dynamic>? ?? [];
    return content.map((e) => Invitacion.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> crearSocio(Map<String, dynamic> data) =>
      _dio.post<void>('/v1/socios', data: data);

  Future<void> editarSocio(String id, Map<String, dynamic> data) =>
      _dio.put<void>('/v1/socios/$id', data: data);

  Future<void> habilitar(String id) =>
      _dio.patch<void>('/v1/socios/$id/habilitar');

  Future<void> inhabilitar(String id) =>
      _dio.patch<void>('/v1/socios/$id/inhabilitar');

  Future<void> cambiarRol(String id, int rolSistemaId) =>
      _dio.patch<void>('/v1/socios/$id/rol', data: {'rolSistemaId': rolSistemaId});

  Future<void> cambiarNivel(String id, String? nivelTecnicoId) =>
      _dio.patch<void>('/v1/socios/$id/nivel-tecnico',
          data: {'nivelTecnicoId': nivelTecnicoId});

  Future<void> setJefeMontana(String id, bool valor) =>
      _dio.patch<void>('/v1/socios/$id/jefe-montana',
          queryParameters: {'valor': valor});

  Future<void> reenviarInvitacion(String id) =>
      _dio.post<void>('/v1/socios/$id/reenviar-invitacion');

  Future<void> emergencyReset(String id) =>
      _dio.post<void>('/v1/socios/$id/emergency-reset');

  Future<void> eliminarSocio(String id) =>
      _dio.delete<void>('/v1/socios/$id');

  Future<Response<List<int>>> exportarCsv({
    required List<String> fields,
    int? tipoId,
    int? estadoId,
    bool excludeAdmin = true,
    String? q,
  }) =>
      _dio.get<List<int>>(
        '/v1/socios/exportar/csv',
        queryParameters: {
          'fields': fields,
          'tipoId': ?tipoId,
          'estadoId': ?estadoId,
          'excludeAdmin': excludeAdmin,
          if (q != null && q.isNotEmpty) 'q': q,
        },
        options: Options(
          responseType: ResponseType.bytes,
          listFormat: ListFormat.multi,
        ),
      );

  Future<Response<List<int>>> exportarPdf({
    bool firmas = false,
    List<String> fields = const [],
    int? tipoId,
    int? estadoId,
    bool excludeAdmin = true,
    String? q,
  }) =>
      _dio.get<List<int>>(
        firmas ? '/v1/socios/exportar/pdf/firmas' : '/v1/socios/exportar/pdf',
        queryParameters: {
          if (!firmas) 'fields': fields,
          'tipoId': ?tipoId,
          'estadoId': ?estadoId,
          'excludeAdmin': excludeAdmin,
          if (q != null && q.isNotEmpty) 'q': q,
        },
        options: Options(
          responseType: ResponseType.bytes,
          listFormat: ListFormat.multi,
        ),
      );
}
