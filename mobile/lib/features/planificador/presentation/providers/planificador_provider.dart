import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../../rutas/domain/models/ruta_model.dart';
import '../../../rutas/presentation/providers/rutas_provider.dart';
import '../../data/planificador_remote_data_source.dart';
import '../../data/planificador_repository.dart';
import '../../domain/models/recomendacion_model.dart';

final planificadorRepositoryProvider =
    Provider<PlanificadorRepository>((ref) {
  return PlanificadorRepository(
      PlanificadorRemoteDataSource(ref.watch(dioClientProvider)));
});

/// Lista completa de rutas para el selector del planificador.
/// Recorre todas las páginas del backend (no solo la primera).
final rutasParaPlanificadorProvider =
    FutureProvider.autoDispose<List<Ruta>>((ref) async {
  final repo = ref.watch(rutasRepositoryProvider);
  final todas = <Ruta>[];
  var page = 0;
  while (true) {
    final paged = await repo.getRutas(page: page);
    todas.addAll(paged.items);
    if (paged.isLast || paged.items.isEmpty) break;
    page++;
  }
  return todas;
});

final recomendacionProvider =
    FutureProvider.autoDispose.family<Recomendacion, int>((ref, rutaId) {
  return ref.watch(planificadorRepositoryProvider).getRecomendacion(rutaId);
});
