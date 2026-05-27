import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/perfil_remote_data_source.dart';
import '../../data/perfil_repository.dart';
import '../../domain/models/perfil_model.dart';

final perfilRepositoryProvider = Provider<PerfilRepository>((ref) {
  return PerfilRepository(PerfilRemoteDataSource(ref.watch(dioClientProvider)));
});

class PerfilNotifier extends AsyncNotifier<PerfilSocio> {
  @override
  Future<PerfilSocio> build() =>
      ref.watch(perfilRepositoryProvider).getMiPerfil();

  Future<void> actualizar(Map<String, dynamic> data) async {
    final repo = ref.read(perfilRepositoryProvider);
    final updated = await repo.actualizarPerfil(data);
    state = AsyncData(updated);
  }
}

final perfilNotifierProvider =
    AsyncNotifierProvider<PerfilNotifier, PerfilSocio>(PerfilNotifier.new);

final sesionesProvider = FutureProvider.autoDispose<List<SesionActiva>>((ref) {
  return ref.watch(perfilRepositoryProvider).getSesiones();
});

final mfaStatusProvider = FutureProvider.autoDispose<bool>((ref) {
  return ref.watch(perfilRepositoryProvider).getMfaStatus();
});
