import '../domain/models/recomendacion_model.dart';
import 'planificador_remote_data_source.dart';

class PlanificadorRepository {
  const PlanificadorRepository(this._ds);
  final PlanificadorRemoteDataSource _ds;

  Future<Recomendacion> getRecomendacion(int rutaId) =>
      _ds.getRecomendacion(rutaId);
}
