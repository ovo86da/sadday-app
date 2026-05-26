import '../../../../core/api/paged_response.dart';
import '../domain/models/montana_model.dart';
import 'montanas_remote_data_source.dart';

class MontanasRepository {
  const MontanasRepository(this._ds);
  final MontanasRemoteDataSource _ds;

  Future<PagedResponse<Montana>> getMontanas({int page = 0, String? q}) =>
      _ds.getMontanas(page: page, q: q);

  Future<Montana> getMontanaDetail(int id) => _ds.getMontanaDetail(id);

  Future<List<Montana>> getAllMontanas() => _ds.getAllMontanas();

  Future<void> crearMontana(Map<String, dynamic> data) => _ds.crearMontana(data);
}
