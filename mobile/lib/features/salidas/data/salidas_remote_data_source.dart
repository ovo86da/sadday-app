import 'package:dio/dio.dart';
import '../../../../core/api/paged_response.dart';
import '../domain/models/salida_model.dart';

class SalidasRemoteDataSource {
  const SalidasRemoteDataSource(this._dio);
  final Dio _dio;

  Future<PagedResponse<Salida>> getSalidas({
    int page = 0,
    int size = 20,
    String? estado,
    String? q,
    String? tipoActividad,
    String? nivelMinimoId,
    int? montanaId,
    int? rutaId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/salidas',
        queryParameters: {
          'page': page,
          'size': size,
          'estado': ?estado,
          if (q != null && q.isNotEmpty) 'q': q,
          'tipoActividad': ?tipoActividad,
          'nivelMinimoSocioId': ?nivelMinimoId,
          'montanaId': ?montanaId,
          'rutaId': ?rutaId,
        });
    final data = res.data!['data'] as Map<String, dynamic>;
    return PagedResponse.fromJson(data, Salida.fromJson);
  }

  Future<SalidaDetalle> getSalidaDetail(String id) async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/salidas/$id');
    return SalidaDetalle.fromJson(
        res.data!['data'] as Map<String, dynamic>);
  }

  Future<void> inscribirse(String salidaId, {String? socioId}) =>
      _dio.post<void>('/v1/salidas/$salidaId/inscripciones',
          data: socioId == null ? null : {'socioId': socioId});

  Future<void> cancelarInscripcion(String salidaId, int inscripcionId) =>
      _dio.delete<void>(
          '/v1/salidas/$salidaId/inscripciones/$inscripcionId');

  Future<void> cambiarEstado(String salidaId, String estado) =>
      _dio.patch<void>('/v1/salidas/$salidaId/estado',
          data: {'estado': estado});

  Future<SalidaLookups> getLookups() async {
    final res = await _dio.get<Map<String, dynamic>>('/v1/salidas/lookups');
    return SalidaLookups.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  Future<List<Solapamiento>> verificarSolapamiento({
    required String fechaInicio,
    required String fechaFin,
    String? excludeId,
  }) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/v1/salidas/solapamiento',
      queryParameters: {
        'fechaInicio': fechaInicio,
        'fechaFin': fechaFin,
        'excludeId': ?excludeId,
      },
    );
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => Solapamiento.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> crearSalida(Map<String, dynamic> data) =>
      _dio.post<void>('/v1/salidas', data: data);

  Future<void> editarSalida(String id, Map<String, dynamic> data) =>
      _dio.put<void>('/v1/salidas/$id', data: data);

  Future<void> cancelarSalida(String id, String motivo) =>
      _dio.patch<void>('/v1/salidas/$id/cancelar', data: {'motivo': motivo});

  Future<void> eliminarSalida(String id, String motivo) =>
      _dio.delete<void>('/v1/salidas/$id', data: {'motivo': motivo});

  Future<void> decidirRiesgo(
          String salidaId, int participanteId, bool aprobar, String motivo) =>
      _dio.patch<void>(
        '/v1/salidas/$salidaId/inscripciones/$participanteId/aprobacion-riesgo',
        data: {'aprobar': aprobar, 'motivo': motivo},
      );

  Future<void> revocarAprobacion(String salidaId, int participanteId) =>
      _dio.delete<void>(
        '/v1/salidas/$salidaId/inscripciones/$participanteId/aprobacion-riesgo',
      );

  Future<bool> toggleCerrarInscripciones(String salidaId) async {
    final res = await _dio.patch<Map<String, dynamic>>(
      '/v1/salidas/$salidaId/cerrar-inscripciones',
    );
    return res.data!['data'] as bool;
  }

  Future<void> designarJefeSalida(String salidaId, int participanteId) =>
      _dio.patch<void>(
          '/v1/salidas/$salidaId/inscripciones/$participanteId/jefe');

  Future<void> agregarDignidad(
          String salidaId, int participanteId, int dignidadId) =>
      _dio.post<void>(
          '/v1/salidas/$salidaId/inscripciones/$participanteId/dignidades',
          data: {'dignidadId': dignidadId});

  Future<void> eliminarDignidad(
          String salidaId, int participanteId, int asignadaId) =>
      _dio.delete<void>(
          '/v1/salidas/$salidaId/inscripciones/$participanteId/dignidades/$asignadaId');
}
