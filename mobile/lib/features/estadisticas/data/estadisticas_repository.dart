import '../domain/models/estadisticas_models.dart';
import 'estadisticas_remote_data_source.dart';

class EstadisticasRepository {
  const EstadisticasRepository(this._ds);
  final EstadisticasRemoteDataSource _ds;

  Future<EstadisticasClub> getClub({int meses = 12}) =>
      _ds.getClub(meses: meses);

  Future<List<RankingItem>> getRankings({int top = 10}) =>
      _ds.getRankings(top: top);

  Future<List<MontanaRankingItem>> getMontanaRanking() =>
      _ds.getMontanaRanking();

  Future<SocioHistorial> getHistorialSocio(String socioId) =>
      _ds.getHistorialSocio(socioId);
}
