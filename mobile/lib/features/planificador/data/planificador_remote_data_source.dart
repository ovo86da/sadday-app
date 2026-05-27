import 'package:dio/dio.dart';
import '../domain/models/recomendacion_model.dart';

class PlanificadorRemoteDataSource {
  const PlanificadorRemoteDataSource(this._dio);
  final Dio _dio;

  Future<Recomendacion> getRecomendacion(int rutaId) async {
    final res = await _dio
        .get<Map<String, dynamic>>('/v1/planificador/ruta/$rutaId');
    return Recomendacion.fromJson(res.data!['data'] as Map<String, dynamic>);
  }
}
