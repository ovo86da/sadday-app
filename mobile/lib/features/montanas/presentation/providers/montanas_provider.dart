import 'package:flutter_riverpod/flutter_riverpod.dart';
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
