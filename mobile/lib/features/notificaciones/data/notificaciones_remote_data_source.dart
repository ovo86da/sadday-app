import 'package:dio/dio.dart';
import '../domain/models/notificacion_models.dart';

class NotificacionesRemoteDataSource {
  const NotificacionesRemoteDataSource(this._dio);
  final Dio _dio;

  Future<List<AprobacionPendiente>> getAprobacionesPendientes() async {
    final res = await _dio
        .get<Map<String, dynamic>>('/v1/salidas/aprobaciones-pendientes');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => AprobacionPendiente.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<AlertaSinJefe>> getAlertasSinJefe() async {
    final res =
        await _dio.get<Map<String, dynamic>>('/v1/salidas/alertas-sin-jefe');
    final data = res.data!['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => AlertaSinJefe.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> decidirRiesgo({
    required String salidaId,
    required int participanteId,
    required bool aprobar,
    required String motivo,
  }) =>
      _dio.patch<void>(
        '/v1/salidas/$salidaId/inscripciones/$participanteId/aprobacion-riesgo',
        data: {'aprobar': aprobar, 'motivo': motivo},
      );
}
