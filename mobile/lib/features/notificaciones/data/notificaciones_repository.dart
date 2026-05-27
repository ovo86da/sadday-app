import '../domain/models/notificacion_models.dart';
import 'notificaciones_remote_data_source.dart';

class NotificacionesRepository {
  const NotificacionesRepository(this._ds);
  final NotificacionesRemoteDataSource _ds;

  Future<List<AprobacionPendiente>> getAprobacionesPendientes() =>
      _ds.getAprobacionesPendientes();

  Future<List<AlertaSinJefe>> getAlertasSinJefe() => _ds.getAlertasSinJefe();

  Future<void> decidirRiesgo({
    required String salidaId,
    required int participanteId,
    required bool aprobar,
    required String motivo,
  }) =>
      _ds.decidirRiesgo(
        salidaId: salidaId,
        participanteId: participanteId,
        aprobar: aprobar,
        motivo: motivo,
      );
}
