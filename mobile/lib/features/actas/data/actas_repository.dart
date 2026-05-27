import '../../../../core/api/paged_response.dart';
import '../domain/models/acta_model.dart';
import 'actas_remote_data_source.dart';

class ActasRepository {
  const ActasRepository(this._ds);
  final ActasRemoteDataSource _ds;

  Future<PagedResponse<Acta>> getActas({int page = 0, String? tipo, String? q}) =>
      _ds.getActas(page: page, tipo: tipo, q: q);

  Future<ActaDetalle> getActaDetail(String id) => _ds.getActaDetail(id);

  Future<void> createActa(CreateActaRequest req) => _ds.createActa(req);

  Future<void> updateActa(String id, CreateActaRequest req) =>
      _ds.updateActa(id, req);

  Future<void> deleteActa(String id) => _ds.deleteActa(id);

  Future<List<int>> generarPdf(String id) => _ds.generarPdf(id);

  Future<List<int>> descargarPdf(String id) => _ds.descargarPdf(id);

  Future<Map<String, dynamic>> importarPreview(String filePath) =>
      _ds.importarPreview(filePath);

  Future<void> importarConfirmar(Map<String, dynamic> previewData) =>
      _ds.importarConfirmar(previewData);
}
