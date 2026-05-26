import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/informes_remote_data_source.dart';
import '../../data/informes_repository.dart';
import '../../domain/models/informe_model.dart';

final informesRepositoryProvider = Provider<InformesRepository>((ref) {
  return InformesRepository(
      InformesRemoteDataSource(ref.watch(dioClientProvider)));
});

final informeDetailProvider =
    FutureProvider.autoDispose.family<Informe?, String>((ref, salidaId) {
  return ref.watch(informesRepositoryProvider).getInforme(salidaId);
});

final pendientesJefeProvider =
    FutureProvider.autoDispose<List<InformeResumen>>((ref) {
  return ref.watch(informesRepositoryProvider).getPendientesJefe();
});
