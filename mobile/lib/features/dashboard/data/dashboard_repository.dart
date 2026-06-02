import 'dashboard_remote_data_source.dart';
import '../domain/models/dashboard_models.dart';

class DashboardRepository {
  const DashboardRepository(this._ds);
  final DashboardRemoteDataSource _ds;

  Future<DashboardStats> getDashboard({int meses = 12, String? socioId}) =>
      _ds.getDashboard(meses: meses, socioId: socioId);

  Future<JefeAlertasData> getJefeAlertas({String? socioId}) =>
      _ds.getJefeAlertas(socioId: socioId);
}
