import '../../../../core/api/paged_response.dart';
import '../domain/models/socio_model.dart';
import 'socios_remote_data_source.dart';

class SociosRepository {
  const SociosRepository(this._ds);
  final SociosRemoteDataSource _ds;

  Future<PagedResponse<Socio>> getSocios({
    int page = 0,
    String? q,
    int? rolId,
    int? estadoId,
    int? tipoId,
  }) =>
      _ds.getSocios(
          page: page, q: q, rolId: rolId, estadoId: estadoId, tipoId: tipoId);

  Future<SocioDetalle> getSocioDetail(String id) => _ds.getSocioDetail(id);

  Future<SociosLookups> getLookups() => _ds.getLookups();

  Future<List<HabilitacionLogEntry>> getHabilitacionLog(String id) =>
      _ds.getHabilitacionLog(id);

  Future<List<Cuota>> getCuotas(String id) => _ds.getCuotas(id);

  Future<List<Invitacion>> getInvitaciones() => _ds.getInvitaciones();

  Future<void> crearSocio(Map<String, dynamic> data) => _ds.crearSocio(data);

  Future<void> editarSocio(String id, Map<String, dynamic> data) =>
      _ds.editarSocio(id, data);

  Future<void> habilitar(String id) => _ds.habilitar(id);

  Future<void> inhabilitar(String id) => _ds.inhabilitar(id);

  Future<void> cambiarRol(String id, int rolSistemaId) =>
      _ds.cambiarRol(id, rolSistemaId);

  Future<void> cambiarNivel(String id, String? nivelTecnicoId) =>
      _ds.cambiarNivel(id, nivelTecnicoId);

  Future<void> setJefeMontana(String id, bool valor) =>
      _ds.setJefeMontana(id, valor);

  Future<void> setPresidenta(String id, bool valor) =>
      _ds.setPresidenta(id, valor);

  Future<void> reenviarInvitacion(String id) => _ds.reenviarInvitacion(id);

  Future<void> emergencyReset(String id) => _ds.emergencyReset(id);

  Future<void> eliminarSocio(String id) => _ds.eliminarSocio(id);

  Future<List<int>> exportarCsv({
    required List<String> fields,
    int? tipoId,
    int? estadoId,
    bool excludeAdmin = true,
    String? q,
  }) async {
    final res = await _ds.exportarCsv(
        fields: fields,
        tipoId: tipoId,
        estadoId: estadoId,
        excludeAdmin: excludeAdmin,
        q: q);
    return res.data ?? const <int>[];
  }

  Future<List<int>> exportarPdf({
    bool firmas = false,
    List<String> fields = const [],
    int? tipoId,
    int? estadoId,
    bool excludeAdmin = true,
    String? q,
  }) async {
    final res = await _ds.exportarPdf(
        firmas: firmas,
        fields: fields,
        tipoId: tipoId,
        estadoId: estadoId,
        excludeAdmin: excludeAdmin,
        q: q);
    return res.data ?? const <int>[];
  }
}
