import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/actas_remote_data_source.dart';
import '../../data/actas_repository.dart';
import '../../domain/models/acta_model.dart';

final actasRepositoryProvider = Provider<ActasRepository>((ref) {
  return ActasRepository(ActasRemoteDataSource(ref.watch(dioClientProvider)));
});

final actaDetailProvider =
    FutureProvider.autoDispose.family<ActaDetalle, String>((ref, id) {
  return ref.watch(actasRepositoryProvider).getActaDetail(id);
});
