import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/dio_client.dart';
import '../../data/admin_remote_data_source.dart';
import '../../data/admin_repository.dart';
import '../../domain/models/admin_models.dart';

final adminRepositoryProvider = Provider<AdminRepository>((ref) {
  return AdminRepository(AdminRemoteDataSource(ref.watch(dioClientProvider)));
});

final adminConfigProvider = FutureProvider.autoDispose<List<AdminConfig>>((ref) {
  return ref.watch(adminRepositoryProvider).getConfig();
});

final adminUsuariosProvider =
    FutureProvider.autoDispose<List<UsuarioAuth>>((ref) {
  return ref.watch(adminRepositoryProvider).getUsuariosAuth();
});
