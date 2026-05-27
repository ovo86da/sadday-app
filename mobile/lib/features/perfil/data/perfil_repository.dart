import '../domain/models/perfil_model.dart';
import 'perfil_remote_data_source.dart';

class PerfilRepository {
  const PerfilRepository(this._ds);
  final PerfilRemoteDataSource _ds;

  Future<PerfilSocio> getMiPerfil() => _ds.getMiPerfil();
  Future<PerfilSocio> actualizarPerfil(Map<String, dynamic> data) =>
      _ds.actualizarPerfil(data);
  Future<List<SesionActiva>> getSesiones() => _ds.getSesiones();
  Future<void> cerrarSesion(String sessionId) => _ds.cerrarSesion(sessionId);
  Future<void> cerrarOtrasSesiones() => _ds.cerrarOtrasSesiones();
  Future<bool> getMfaStatus() => _ds.getMfaStatus();
  Future<({String otpAuthUri, String base32Secret})> setupMfa() => _ds.setupMfa();
  Future<void> confirmMfa(String code) => _ds.confirmMfa(code);
  Future<void> disableMfa(String code) => _ds.disableMfa(code);
}
