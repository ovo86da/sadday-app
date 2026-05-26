import 'package:dio/dio.dart';
import '../domain/models/estadisticas_models.dart';

class EstadisticasRemoteDataSource {
  const EstadisticasRemoteDataSource(this._dio);
  final Dio _dio;

  Future<EstadisticasClub> getClub({int meses = 12}) async {
    final results = await Future.wait([
      _dio.get<Map<String, dynamic>>('/v1/estadisticas/dashboard',
          queryParameters: {'meses': meses}),
      _dio.get<Map<String, dynamic>>('/v1/estadisticas/club'),
    ]);
    final dash = (results[0].data!['data'] as Map<String, dynamic>?) ?? {};
    final club = (results[1].data!['data'] as Map<String, dynamic>?) ?? {};
    return EstadisticasClub.fromBackend(dash: dash, club: club);
  }

  /// /v1/estadisticas/rankings devuelve ClubRankingsResponse (objeto con
  /// varias listas). Mostramos `topParticipaciones` por defecto.
  Future<List<RankingItem>> getRankings({int top = 10}) async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/estadisticas/rankings',
        queryParameters: {'top': top});
    final wrapper =
        (res.data!['data'] as Map<String, dynamic>?) ?? const {};
    final raw = (wrapper['topParticipaciones'] as List<dynamic>?) ??
        (wrapper['topJefesSalida'] as List<dynamic>?) ??
        const <dynamic>[];
    return raw
        .asMap()
        .entries
        .map((e) =>
            RankingItem.fromJson(e.value as Map<String, dynamic>, e.key))
        .toList();
  }

  /// Historial de participación de un socio (vista Kipu).
  Future<SocioHistorial> getHistorialSocio(String socioId) async {
    final res = await _dio
        .get<Map<String, dynamic>>('/v1/estadisticas/socios/$socioId');
    return SocioHistorial.fromJson(res.data!['data'] as Map<String, dynamic>);
  }

  /// /v1/estadisticas/ranking-montana-ruta devuelve RankingMontanaRutaResponse
  /// (objeto con varias listas). Tomamos `topMontanasMasSalidas`.
  Future<List<MontanaRankingItem>> getMontanaRanking() async {
    final res = await _dio.get<Map<String, dynamic>>(
        '/v1/estadisticas/ranking-montana-ruta');
    final wrapper =
        (res.data!['data'] as Map<String, dynamic>?) ?? const {};
    final raw =
        (wrapper['topMontanasMasSalidas'] as List<dynamic>?) ?? const <dynamic>[];
    return raw
        .map((e) => MontanaRankingItem.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}
