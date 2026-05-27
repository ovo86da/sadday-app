import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/estadisticas_remote_data_source.dart';
import '../../data/estadisticas_repository.dart';
import '../../domain/models/estadisticas_models.dart';

final estadisticasRepositoryProvider = Provider<EstadisticasRepository>((ref) {
  return EstadisticasRepository(
      EstadisticasRemoteDataSource(ref.watch(dioClientProvider)));
});

final estadisticasClubProvider =
    FutureProvider.autoDispose.family<EstadisticasClub, int>((ref, meses) {
  return ref.watch(estadisticasRepositoryProvider).getClub(meses: meses);
});

final rankingsProvider =
    FutureProvider.autoDispose.family<List<RankingItem>, int>((ref, top) {
  return ref.watch(estadisticasRepositoryProvider).getRankings(top: top);
});

final montanaRankingProvider =
    FutureProvider.autoDispose<List<MontanaRankingItem>>((ref) {
  return ref.watch(estadisticasRepositoryProvider).getMontanaRanking();
});

/// Historial de participación del socio actual (vista "Mis Salidas" / Kipu).
final historialSocioProvider =
    FutureProvider.autoDispose.family<SocioHistorial, String>((ref, socioId) {
  return ref.watch(estadisticasRepositoryProvider).getHistorialSocio(socioId);
});
