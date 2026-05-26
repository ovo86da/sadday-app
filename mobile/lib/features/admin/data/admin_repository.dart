import '../../../../core/api/paged_response.dart';
import '../domain/models/admin_models.dart';
import 'admin_remote_data_source.dart';

class AdminRepository {
  const AdminRepository(this._ds);
  final AdminRemoteDataSource _ds;

  Future<List<AdminConfig>> getConfig() => _ds.getConfig();

  Future<void> patchConfig(String clave, String valor) =>
      _ds.patchConfig(clave, valor);

  Future<PagedResponse<AuditoriaEntry>> getAuditoria({
    int page = 0,
    String? entidad,
    String? actor,
  }) =>
      _ds.getAuditoria(
          page: page, entidadAfectada: entidad, actorUsername: actor);

  Future<PagedResponse<SecurityEvent>> getSecurityEvents({int page = 0}) =>
      _ds.getSecurityEvents(page: page);

  Future<List<UsuarioAuth>> getUsuariosAuth() => _ds.getUsuariosAuth();

  Future<void> cambiarEstadoAcceso(String socioId, String estado) =>
      _ds.cambiarEstadoAcceso(socioId, estado);

  Future<void> desbloquear(String socioId) => _ds.desbloquear(socioId);

  Future<void> cerrarSesion(String socioId) => _ds.cerrarSesion(socioId);

  Future<void> emergencyReset(String socioId) => _ds.emergencyReset(socioId);
}
