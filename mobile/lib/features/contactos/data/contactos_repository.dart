import '../../../../core/api/paged_response.dart';
import '../domain/models/contacto_model.dart';
import 'contactos_remote_data_source.dart';

class ContactosRepository {
  const ContactosRepository(this._ds);
  final ContactosRemoteDataSource _ds;

  Future<PagedResponse<Contacto>> getContactos({int page = 0, String? q}) =>
      _ds.getContactos(page: page, q: q);

  Future<void> crear(Map<String, dynamic> data) => _ds.crear(data);

  Future<void> editar(int id, Map<String, dynamic> data) =>
      _ds.editar(id, data);

  Future<void> eliminar(int id) => _ds.eliminar(id);
}
