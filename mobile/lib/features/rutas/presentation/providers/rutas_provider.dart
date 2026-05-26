import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/rutas_remote_data_source.dart';
import '../../data/rutas_repository.dart';
import '../../domain/models/ruta_model.dart';

final rutasRepositoryProvider = Provider<RutasRepository>((ref) {
  return RutasRepository(RutasRemoteDataSource(ref.watch(dioClientProvider)));
});

final rutaDetailProvider =
    FutureProvider.autoDispose.family<Ruta, int>((ref, id) {
  return ref.watch(rutasRepositoryProvider).getRutaDetail(id);
});
