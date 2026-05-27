import '../../../../core/api/paged_response.dart';
import '../domain/models/informe_model.dart';
import 'informes_remote_data_source.dart';

class InformesRepository {
  const InformesRepository(this._ds);
  final InformesRemoteDataSource _ds;

  Future<PagedResponse<InformeResumen>> getInformes({int page = 0}) =>
      _ds.getInformes(page: page);

  Future<List<InformeResumen>> getPendientesJefe() => _ds.getPendientesJefe();

  Future<Informe?> getInforme(String salidaId) => _ds.getInforme(salidaId);

  Future<void> createInforme(String salidaId, CreateInformeRequest req) =>
      _ds.createInforme(salidaId, req);

  Future<void> updateInforme(String salidaId, CreateInformeRequest req) =>
      _ds.updateInforme(salidaId, req);

  Future<void> validarInforme(String salidaId) => _ds.validarInforme(salidaId);

  Future<List<int>> downloadPdf(String salidaId) => _ds.downloadPdf(salidaId);

  Future<List<int>> generarPdf(String salidaId) => _ds.generarPdf(salidaId);
}
