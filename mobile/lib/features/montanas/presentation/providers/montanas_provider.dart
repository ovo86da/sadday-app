import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../domain/models/cambio_umbral_model.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/montanas_remote_data_source.dart';
import '../../data/montanas_repository.dart';
import '../../domain/models/montana_model.dart';
import '../../domain/models/mountain_lookups_model.dart';

final montanasRepositoryProvider = Provider<MontanasRepository>((ref) {
  return MontanasRepository(
      MontanasRemoteDataSource(ref.watch(dioClientProvider)));
});

final montanaDetailProvider =
    FutureProvider.autoDispose.family<Montana, int>((ref, id) {
  return ref.watch(montanasRepositoryProvider).getMontanaDetail(id);
});

final mountainLookupsProvider = FutureProvider.autoDispose<MountainLookups>((ref) {
  return ref.watch(montanasRepositoryProvider).getLookups();
});

/// Cambios de umbral esperando aprobación (FR-026).
///
/// `autoDispose` a propósito: es una lista que cambia cuando otro directivo
/// resuelve algo, y mostrarla cacheada sería enseñar un botón de aprobar sobre
/// un cambio que ya no existe.
final cambiosUmbralProvider =
    FutureProvider.autoDispose<List<CambioUmbral>>((ref) {
  return ref.watch(montanasRepositoryProvider).getCambiosUmbralPendientes();
});
