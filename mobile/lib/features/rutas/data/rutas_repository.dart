import '../../../../core/api/paged_response.dart';
import '../domain/models/ruta_model.dart';
import 'rutas_remote_data_source.dart';

class RutasRepository {
  const RutasRepository(this._ds);
  final RutasRemoteDataSource _ds;

  Future<PagedResponse<Ruta>> getRutas({int page = 0, String? q}) =>
      _ds.getRutas(page: page, q: q);

  Future<Ruta> getRutaDetail(int id) => _ds.getRutaDetail(id);

  Future<List<Ruta>> getRutasByMountain(int mountainId) =>
      _ds.getRutasByMountain(mountainId);

  Future<List<Ruta>> getRutasByActividad(String tipoActividad) =>
      _ds.getRutasByActividad(tipoActividad);
}
